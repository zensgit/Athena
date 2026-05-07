package com.ecm.core.integration.oauth;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
public class OAuthCredentialService {

    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final String GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String MICROSOFT_AUTH_URL_TEMPLATE = "https://login.microsoftonline.com/%s/oauth2/v2.0/authorize";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_REVOKE_URL = "https://oauth2.googleapis.com/revoke";
    private static final String MICROSOFT_TOKEN_URL_TEMPLATE = "https://login.microsoftonline.com/%s/oauth2/v2.0/token";
    private static final String GOOGLE_SCOPE_DEFAULT = "https://mail.google.com/";
    private static final String MICROSOFT_SCOPE_DEFAULT = "offline_access https://outlook.office.com/IMAP.AccessAsUser.All";

    private final Environment environment;
    private final RestTemplate restTemplate;
    private final List<OAuthCredentialOwnerAdapter> adapters;
    private final ConcurrentMap<String, OAuthState> stateStore = new ConcurrentHashMap<>();
    private final ConcurrentMap<OwnerKey, OAuthSession> sessionStore = new ConcurrentHashMap<>();

    public OAuthAuthorizeResponse buildAuthorizeUrl(
        String ownerType,
        UUID ownerId,
        String callbackUrl,
        String redirectUrl
    ) {
        AdapterContext context = loadContext(ownerType, ownerId);
        OAuthCredentialOwner owner = context.owner();
        if (owner.provider() == null || owner.provider() == OAuthProviderType.CUSTOM) {
            throw new IllegalArgumentException("OAuth provider must be GOOGLE or MICROSOFT for connect flow");
        }

        String state = generateState();
        pruneExpiredStates();
        stateStore.put(state, new OAuthState(ownerType, ownerId, callbackUrl, redirectUrl, Instant.now()));

        String authUrl = switch (owner.provider()) {
            case GOOGLE -> buildGoogleAuthorizeUrl(context, state, callbackUrl);
            case MICROSOFT -> buildMicrosoftAuthorizeUrl(context, state, callbackUrl);
            case CUSTOM -> throw new IllegalArgumentException("Custom OAuth providers require manual credentials");
        };
        return new OAuthAuthorizeResponse(authUrl, state);
    }

    public OAuthCallbackResult handleCallback(String code, String state) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Missing OAuth authorization code");
        }

        OAuthState stored = stateStore.remove(state);
        if (stored == null) {
            throw new IllegalArgumentException("Invalid or expired OAuth state");
        }
        if (Instant.now().isAfter(stored.createdAt().plus(STATE_TTL))) {
            throw new IllegalArgumentException("OAuth state has expired");
        }

        AdapterContext context = loadContext(stored.ownerType(), stored.ownerId());
        OAuthTokenResponse token = exchangeAuthorizationCode(context, code, stored.callbackUrl());
        OAuthCredentialOwner owner = context.owner();
        if ((token.refreshToken() == null || token.refreshToken().isBlank())
            && (owner.refreshToken() == null || owner.refreshToken().isBlank())) {
            throw new IllegalStateException("OAuth provider did not return a refresh token");
        }

        LocalDateTime expiresAt = computeExpiresAt(token.expiresIn());
        OAuthCredentialOwner saved = context.adapter().saveTokens(
            owner.ownerId(),
            token.accessToken(),
            token.refreshToken(),
            expiresAt
        );
        if (token.accessToken() != null && !token.accessToken().isBlank()) {
            sessionStore.put(new OwnerKey(saved.ownerType(), saved.ownerId()), new OAuthSession(token.accessToken(), expiresAt));
        } else {
            sessionStore.remove(new OwnerKey(saved.ownerType(), saved.ownerId()));
        }

        return new OAuthCallbackResult(saved.ownerType(), saved.ownerId(), stored.redirectUrl());
    }

    public OAuthEnvironmentStatus checkEnvironment(String ownerType, UUID ownerId) {
        AdapterContext context = loadContext(ownerType, ownerId);
        return checkEnvironment(context);
    }

    public String resolveAccessToken(String ownerType, UUID ownerId) {
        OwnerKey ownerKey = new OwnerKey(ownerType, ownerId);
        OAuthSession session = sessionStore.get(ownerKey);
        if (session != null && !isExpired(session.expiresAt())) {
            return session.accessToken();
        }

        AdapterContext context = loadContext(ownerType, ownerId);
        OAuthTokenResponse response = refreshAccessToken(context);
        LocalDateTime expiresAt = computeExpiresAt(response.expiresIn());
        context.adapter().saveTokens(ownerId, response.accessToken(), response.refreshToken(), expiresAt);
        sessionStore.put(ownerKey, new OAuthSession(response.accessToken(), expiresAt));
        return response.accessToken();
    }

    public String refreshAccessTokenNow(String ownerType, UUID ownerId) {
        AdapterContext context = loadContext(ownerType, ownerId);
        OAuthTokenResponse response = refreshAccessToken(context);
        LocalDateTime expiresAt = computeExpiresAt(response.expiresIn());
        OAuthCredentialOwner saved = context.adapter().saveTokens(ownerId, response.accessToken(), response.refreshToken(), expiresAt);
        sessionStore.put(new OwnerKey(saved.ownerType(), saved.ownerId()), new OAuthSession(response.accessToken(), expiresAt));
        return response.accessToken();
    }

    public void clearTokens(String ownerType, UUID ownerId) {
        AdapterContext context = loadContext(ownerType, ownerId);
        context.adapter().clearTokens(ownerId);
        evictSession(ownerType, ownerId);
    }

    /**
     * Calls the provider's revoke endpoint for the locally stored OAuth token.
     *
     * <p>v1 supports GOOGLE only. Prefers the stored refresh token; falls back to the access token.
     * On HTTP 200 OR provider-side already-invalid responses (HTTP 4xx with error in
     * {invalid_token, invalid_grant, unsupported_token_type}), local tokens are cleared and the
     * session cache is evicted. On HTTP 5xx, network failures, or any other error, local tokens
     * are preserved and an {@link IllegalStateException} is raised so the operator can retry.
     */
    public void revokeProviderTokens(String ownerType, UUID ownerId) {
        AdapterContext context = loadContext(ownerType, ownerId);
        OAuthCredentialOwner owner = context.owner();

        if (owner.provider() != OAuthProviderType.GOOGLE) {
            throw new IllegalArgumentException(
                "Provider-side revoke is only supported for GOOGLE; this credential is " + owner.provider()
            );
        }

        boolean hasAccessToken = owner.accessToken() != null && !owner.accessToken().isBlank();
        boolean hasRefreshToken = owner.refreshToken() != null && !owner.refreshToken().isBlank();

        if (hasCredentialKey(owner) && !hasAccessToken && !hasRefreshToken) {
            throw new IllegalArgumentException(
                "Provider-side revoke requires a locally stored OAuth token; "
                    + "this credential row only references env-managed secrets"
            );
        }
        if (!hasAccessToken && !hasRefreshToken) {
            throw new IllegalArgumentException("No locally stored OAuth token to revoke");
        }

        String tokenToRevoke = hasRefreshToken ? owner.refreshToken() : owner.accessToken();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", tokenToRevoke);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            restTemplate.postForEntity(
                GOOGLE_REVOKE_URL,
                new HttpEntity<>(form, headers),
                String.class
            );
        } catch (HttpServerErrorException ex) {
            throw new IllegalStateException(
                "OAuth token revoke failed for owner " + owner.ownerName()
                    + ": provider returned " + ex.getStatusCode().value(),
                ex
            );
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().is5xxServerError()) {
                throw new IllegalStateException(
                    "OAuth token revoke failed for owner " + owner.ownerName()
                        + ": provider returned " + ex.getStatusCode().value(),
                    ex
                );
            }
            OAuthTokenErrorParser.OAuthTokenError parsed =
                OAuthTokenErrorParser.parse(ex.getResponseBodyAsString()).orElse(null);
            if (parsed != null && isAlreadyInvalidTokenError(parsed.error())) {
                // Provider already considers the token invalid/revoked. Clearing locally reflects truth.
                context.adapter().clearTokens(owner.ownerId());
                evictSession(owner.ownerType(), owner.ownerId());
                return;
            }
            String message = "OAuth token revoke failed for owner " + owner.ownerName();
            if (parsed != null) {
                message += ": " + parsed.error();
                if (parsed.errorDescription() != null && !parsed.errorDescription().isBlank()) {
                    message += " - " + parsed.errorDescription().trim();
                }
            } else {
                message += ": provider returned " + ex.getStatusCode().value();
            }
            throw new IllegalStateException(message, ex);
        } catch (ResourceAccessException ex) {
            throw new IllegalStateException(
                "OAuth token revoke failed for owner " + owner.ownerName()
                    + ": network error contacting provider",
                ex
            );
        }

        context.adapter().clearTokens(owner.ownerId());
        evictSession(owner.ownerType(), owner.ownerId());
    }

    private boolean isAlreadyInvalidTokenError(String error) {
        if (error == null) {
            return false;
        }
        return "invalid_token".equalsIgnoreCase(error)
            || "invalid_grant".equalsIgnoreCase(error)
            || "unsupported_token_type".equalsIgnoreCase(error);
    }

    public void evictSession(String ownerType, UUID ownerId) {
        sessionStore.remove(new OwnerKey(ownerType, ownerId));
    }

    private OAuthEnvironmentStatus checkEnvironment(AdapterContext context) {
        OAuthCredentialOwner owner = context.owner();
        if (owner.provider() == null && normalizeCredentialKeyNullable(owner.credentialKey()) == null) {
            return new OAuthEnvironmentStatus(false, true, null, List.of());
        }

        String normalizedKey = normalizeCredentialKeyNullable(owner.credentialKey());
        if (normalizedKey != null) {
            List<String> requiredKeys = List.of(
                context.adapter().buildCredentialEnvKey(normalizedKey, "CLIENT_ID"),
                context.adapter().buildCredentialEnvKey(normalizedKey, "REFRESH_TOKEN")
            );
            List<String> missing = requiredKeys.stream()
                .filter(this::isMissingEnvValue)
                .toList();
            return new OAuthEnvironmentStatus(true, missing.isEmpty(), normalizedKey, missing);
        }

        if (owner.provider() == null || owner.provider() == OAuthProviderType.CUSTOM) {
            return new OAuthEnvironmentStatus(true, false, null, List.of("oauthCredentialKey"));
        }

        List<String> requiredKeys = List.of(
            context.adapter().buildProviderEnvKey(owner.provider(), "CLIENT_ID"),
            context.adapter().buildProviderEnvKey(owner.provider(), "CLIENT_SECRET")
        );
        List<String> missing = requiredKeys.stream()
            .filter(this::isMissingEnvValue)
            .toList();
        return new OAuthEnvironmentStatus(true, missing.isEmpty(), null, missing);
    }

    private OAuthTokenResponse refreshAccessToken(AdapterContext context) {
        OAuthCredentialOwner owner = context.owner();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", resolveClientId(context));
        String clientSecret = resolveClientSecret(context);
        if (clientSecret != null && !clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }
        form.add("refresh_token", resolveRefreshToken(context));
        String scope = resolveScope(context);
        if (scope != null && !scope.isBlank()) {
            form.add("scope", scope);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        Map<?, ?> body;
        try {
            body = restTemplate.postForEntity(
                resolveTokenEndpoint(context),
                new HttpEntity<>(form, headers),
                Map.class
            ).getBody();
        } catch (HttpStatusCodeException ex) {
            OAuthTokenErrorParser.OAuthTokenError parsed =
                OAuthTokenErrorParser.parse(ex.getResponseBodyAsString()).orElse(null);
            if (parsed != null) {
                if ("invalid_grant".equalsIgnoreCase(parsed.error())) {
                    context.adapter().clearTokens(owner.ownerId());
                    evictSession(owner.ownerType(), owner.ownerId());
                    throw new OAuthReauthRequiredException(
                        owner.ownerType(),
                        owner.ownerId(),
                        parsed.error(),
                        parsed.errorDescription()
                    );
                }
                String message = "OAuth token refresh failed: " + parsed.error();
                if (parsed.errorDescription() != null && !parsed.errorDescription().isBlank()) {
                    message += " - " + parsed.errorDescription().trim();
                }
                throw new IllegalStateException(message, ex);
            }
            throw ex;
        }

        if (body == null || body.get("access_token") == null) {
            throw new IllegalStateException("OAuth token refresh failed for owner " + owner.ownerName());
        }
        return parseTokenResponse(body);
    }

    private OAuthTokenResponse exchangeAuthorizationCode(AdapterContext context, String code, String callbackUrl) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", callbackUrl);
        form.add("client_id", resolveClientId(context));
        String clientSecret = resolveClientSecret(context);
        if (clientSecret != null && !clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }
        String scope = resolveScope(context);
        if (scope != null && !scope.isBlank()) {
            form.add("scope", scope);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        Map<?, ?> body = restTemplate.postForEntity(
            resolveTokenEndpoint(context),
            new HttpEntity<>(form, headers),
            Map.class
        ).getBody();
        if (body == null || body.get("access_token") == null) {
            throw new IllegalStateException("OAuth token exchange failed for owner " + context.owner().ownerName());
        }
        return parseTokenResponse(body);
    }

    private String buildGoogleAuthorizeUrl(AdapterContext context, String state, String callbackUrl) {
        return UriComponentsBuilder.fromHttpUrl(GOOGLE_AUTH_URL)
            .queryParam("client_id", resolveClientId(context))
            .queryParam("redirect_uri", callbackUrl)
            .queryParam("response_type", "code")
            .queryParam("scope", resolveScope(context))
            .queryParam("access_type", "offline")
            .queryParam("prompt", "consent")
            .queryParam("state", state)
            .build()
            .toUriString();
    }

    private String buildMicrosoftAuthorizeUrl(AdapterContext context, String state, String callbackUrl) {
        String tenantId = context.owner().tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = "common";
        }
        String authUrl = String.format(MICROSOFT_AUTH_URL_TEMPLATE, tenantId);
        return UriComponentsBuilder.fromHttpUrl(authUrl)
            .queryParam("client_id", resolveClientId(context))
            .queryParam("redirect_uri", callbackUrl)
            .queryParam("response_type", "code")
            .queryParam("response_mode", "query")
            .queryParam("scope", resolveScope(context))
            .queryParam("state", state)
            .build()
            .toUriString();
    }

    private String resolveTokenEndpoint(AdapterContext context) {
        OAuthCredentialOwner owner = context.owner();
        String override = hasCredentialKey(owner)
            ? resolveCredentialEnv(context, "TOKEN_ENDPOINT", false)
            : resolveProviderEnv(context, "TOKEN_ENDPOINT", false);
        if (override != null && !override.isBlank()) {
            return override;
        }
        if (owner.tokenEndpoint() != null && !owner.tokenEndpoint().isBlank()) {
            return owner.tokenEndpoint();
        }
        if (owner.provider() == null || owner.provider() == OAuthProviderType.CUSTOM) {
            throw new IllegalStateException("OAuth token endpoint not configured for owner " + owner.ownerName());
        }
        return switch (owner.provider()) {
            case GOOGLE -> GOOGLE_TOKEN_URL;
            case MICROSOFT -> {
                String tenantId = owner.tenantId();
                if (tenantId == null || tenantId.isBlank()) {
                    tenantId = "common";
                }
                yield String.format(MICROSOFT_TOKEN_URL_TEMPLATE, tenantId);
            }
            case CUSTOM -> throw new IllegalStateException(
                "OAuth token endpoint not configured for owner " + owner.ownerName()
            );
        };
    }

    private String resolveClientId(AdapterContext context) {
        if (hasCredentialKey(context.owner())) {
            return resolveCredentialEnv(context, "CLIENT_ID", true);
        }
        return resolveProviderEnv(context, "CLIENT_ID", true);
    }

    private String resolveClientSecret(AdapterContext context) {
        if (hasCredentialKey(context.owner())) {
            return resolveCredentialEnv(context, "CLIENT_SECRET", false);
        }
        return resolveProviderEnv(context, "CLIENT_SECRET", true);
    }

    private String resolveRefreshToken(AdapterContext context) {
        OAuthCredentialOwner owner = context.owner();
        if (hasCredentialKey(owner)) {
            return resolveCredentialEnv(context, "REFRESH_TOKEN", true);
        }
        if (owner.refreshToken() == null || owner.refreshToken().isBlank()) {
            throw new IllegalStateException("Missing OAuth refresh token for owner " + owner.ownerName());
        }
        return owner.refreshToken();
    }

    private String resolveScope(AdapterContext context) {
        OAuthCredentialOwner owner = context.owner();
        String scope = hasCredentialKey(owner)
            ? resolveCredentialEnv(context, "SCOPE", false)
            : resolveProviderEnv(context, "SCOPE", false);
        if (scope != null && !scope.isBlank()) {
            return scope;
        }
        if (owner.scope() != null && !owner.scope().isBlank()) {
            return owner.scope();
        }
        return defaultScope(owner.provider());
    }

    private String defaultScope(OAuthProviderType provider) {
        if (provider == null || provider == OAuthProviderType.CUSTOM) {
            return null;
        }
        return switch (provider) {
            case GOOGLE -> GOOGLE_SCOPE_DEFAULT;
            case MICROSOFT -> MICROSOFT_SCOPE_DEFAULT;
            case CUSTOM -> null;
        };
    }

    private String resolveCredentialEnv(AdapterContext context, String suffix, boolean required) {
        String normalizedKey = normalizeCredentialKey(context.owner().credentialKey(), context.owner().ownerName());
        String envKey = context.adapter().buildCredentialEnvKey(normalizedKey, suffix);
        return resolveEnvValue(envKey, context.owner().ownerName(), required);
    }

    private String resolveProviderEnv(AdapterContext context, String suffix, boolean required) {
        OAuthProviderType provider = context.owner().provider();
        if (provider == null) {
            throw new IllegalStateException("OAuth provider missing for owner " + context.owner().ownerName());
        }
        String envKey = context.adapter().buildProviderEnvKey(provider, suffix);
        return resolveEnvValue(envKey, context.owner().ownerName(), required);
    }

    private String resolveEnvValue(String envKey, String ownerName, boolean required) {
        String value = environment.getProperty(envKey);
        if (required && (value == null || value.isBlank())) {
            throw new IllegalStateException("Missing OAuth env var " + envKey + " for owner " + ownerName);
        }
        return value;
    }

    private boolean hasCredentialKey(OAuthCredentialOwner owner) {
        return normalizeCredentialKeyNullable(owner.credentialKey()) != null;
    }

    private boolean isExpired(LocalDateTime expiresAt) {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now().plusMinutes(1));
    }

    private boolean isMissingEnvValue(String envKey) {
        String value = environment.getProperty(envKey);
        return value == null || value.isBlank();
    }

    private String normalizeCredentialKey(String credentialKey, String ownerName) {
        String normalized = normalizeCredentialKeyNullable(credentialKey);
        if (normalized == null) {
            throw new IllegalStateException("OAuth credential key missing for owner " + ownerName);
        }
        return normalized;
    }

    private String normalizeCredentialKeyNullable(String credentialKey) {
        if (credentialKey == null || credentialKey.isBlank()) {
            return null;
        }
        return credentialKey.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_");
    }

    private OAuthTokenResponse parseTokenResponse(Map<?, ?> body) {
        String accessToken = body.get("access_token").toString();
        String refreshToken = body.get("refresh_token") != null ? body.get("refresh_token").toString() : null;
        Long expiresIn = body.get("expires_in") instanceof Number number ? number.longValue() : null;
        return new OAuthTokenResponse(accessToken, refreshToken, expiresIn);
    }

    private LocalDateTime computeExpiresAt(Long expiresIn) {
        if (expiresIn == null || expiresIn <= 0) {
            return null;
        }
        return LocalDateTime.now().plusSeconds(expiresIn);
    }

    private AdapterContext loadContext(String ownerType, UUID ownerId) {
        OAuthCredentialOwnerAdapter adapter = adapters.stream()
            .filter(candidate -> candidate.ownerType().equals(ownerType))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No OAuth adapter registered for owner type " + ownerType));
        return new AdapterContext(adapter, adapter.loadOwner(ownerId));
    }

    private String generateState() {
        byte[] bytes = new byte[24];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void pruneExpiredStates() {
        Instant now = Instant.now();
        stateStore.entrySet().removeIf(entry -> now.isAfter(entry.getValue().createdAt().plus(STATE_TTL)));
    }

    public record OAuthAuthorizeResponse(String url, String state) {
    }

    public record OAuthCallbackResult(String ownerType, UUID ownerId, String redirectUrl) {
    }

    private record AdapterContext(OAuthCredentialOwnerAdapter adapter, OAuthCredentialOwner owner) {
    }

    private record OAuthTokenResponse(String accessToken, String refreshToken, Long expiresIn) {
    }

    private record OAuthSession(String accessToken, LocalDateTime expiresAt) {
    }

    private record OwnerKey(String ownerType, UUID ownerId) {
    }

    private record OAuthState(String ownerType, UUID ownerId, String callbackUrl, String redirectUrl, Instant createdAt) {
    }
}
