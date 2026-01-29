package com.ecm.core.integration.mail.service;

import com.ecm.core.integration.mail.model.MailAccount;
import com.ecm.core.integration.mail.repository.MailAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailOAuthService {

    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final String GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String MICROSOFT_AUTH_URL_TEMPLATE = "https://login.microsoftonline.com/%s/oauth2/v2.0/authorize";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String MICROSOFT_TOKEN_URL_TEMPLATE = "https://login.microsoftonline.com/%s/oauth2/v2.0/token";
    private static final String GOOGLE_SCOPE_DEFAULT = "https://mail.google.com/";
    private static final String MICROSOFT_SCOPE_DEFAULT = "offline_access https://outlook.office.com/IMAP.AccessAsUser.All";

    private final Environment environment;
    private final MailAccountRepository accountRepository;
    private final RestTemplate restTemplate;
    private final ConcurrentMap<String, OAuthState> stateStore = new ConcurrentHashMap<>();

    public OAuthAuthorizeResponse buildAuthorizeUrl(UUID accountId, String callbackUrl, String redirectUrl) {
        MailAccount account = accountRepository.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Mail account not found: " + accountId));
        if (account.getSecurity() != MailAccount.SecurityType.OAUTH2) {
            throw new IllegalArgumentException("Mail account is not configured for OAuth2");
        }
        if (account.getOauthProvider() == null || account.getOauthProvider() == MailAccount.OAuthProvider.CUSTOM) {
            throw new IllegalArgumentException("OAuth provider must be GOOGLE or MICROSOFT for OAuth connect flow");
        }

        String state = generateState();
        pruneExpiredStates();
        stateStore.put(state, new OAuthState(account.getId(), callbackUrl, redirectUrl, Instant.now()));

        String authUrl = switch (account.getOauthProvider()) {
            case GOOGLE -> buildGoogleAuthorizeUrl(state, callbackUrl);
            case MICROSOFT -> buildMicrosoftAuthorizeUrl(account, state, callbackUrl);
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

        MailAccount account = accountRepository.findById(stored.accountId())
            .orElseThrow(() -> new IllegalArgumentException("Mail account not found: " + stored.accountId()));
        OAuthTokenResponse token = exchangeAuthorizationCode(account, code, stored.callbackUrl());

        if (token.refreshToken() == null || token.refreshToken().isBlank()) {
            if (account.getOauthRefreshToken() == null || account.getOauthRefreshToken().isBlank()) {
                throw new IllegalStateException("OAuth provider did not return a refresh token");
            }
        } else {
            account.setOauthRefreshToken(token.refreshToken());
        }

        account.setOauthAccessToken(token.accessToken());
        if (token.expiresIn() != null && token.expiresIn() > 0) {
            account.setOauthTokenExpiresAt(LocalDateTime.now().plusSeconds(token.expiresIn()));
        }
        accountRepository.save(account);

        return new OAuthCallbackResult(account.getId(), stored.redirectUrl());
    }

    private String buildGoogleAuthorizeUrl(String state, String callbackUrl) {
        String clientId = resolveProviderClientId(MailAccount.OAuthProvider.GOOGLE);
        String scope = resolveProviderScope(MailAccount.OAuthProvider.GOOGLE);
        return UriComponentsBuilder.fromHttpUrl(GOOGLE_AUTH_URL)
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", callbackUrl)
            .queryParam("response_type", "code")
            .queryParam("scope", scope)
            .queryParam("access_type", "offline")
            .queryParam("prompt", "consent")
            .queryParam("state", state)
            .build()
            .toUriString();
    }

    private String buildMicrosoftAuthorizeUrl(MailAccount account, String state, String callbackUrl) {
        String tenantId = account.getOauthTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = "common";
        }
        String clientId = resolveProviderClientId(MailAccount.OAuthProvider.MICROSOFT);
        String scope = resolveProviderScope(MailAccount.OAuthProvider.MICROSOFT);
        String authUrl = String.format(MICROSOFT_AUTH_URL_TEMPLATE, tenantId);
        return UriComponentsBuilder.fromHttpUrl(authUrl)
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", callbackUrl)
            .queryParam("response_type", "code")
            .queryParam("response_mode", "query")
            .queryParam("scope", scope)
            .queryParam("state", state)
            .build()
            .toUriString();
    }

    private OAuthTokenResponse exchangeAuthorizationCode(MailAccount account, String code, String callbackUrl) {
        String tokenEndpoint = resolveTokenEndpoint(account);
        String clientId = resolveProviderClientId(account.getOauthProvider());
        String clientSecret = resolveProviderClientSecret(account.getOauthProvider());
        String scope = resolveProviderScope(account.getOauthProvider());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", callbackUrl);
        form.add("client_id", clientId);
        if (clientSecret != null && !clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }
        if (scope != null && !scope.isBlank()) {
            form.add("scope", scope);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        var response = restTemplate.postForEntity(tokenEndpoint, new HttpEntity<>(form, headers), Map.class);
        Map<?, ?> body = response.getBody();
        if (body == null || body.get("access_token") == null) {
            throw new IllegalStateException("OAuth token exchange failed for account " + account.getName());
        }

        String accessToken = body.get("access_token").toString();
        String refreshToken = body.get("refresh_token") != null ? body.get("refresh_token").toString() : null;
        Long expiresIn = body.get("expires_in") instanceof Number number ? number.longValue() : null;
        return new OAuthTokenResponse(accessToken, refreshToken, expiresIn);
    }

    private String resolveTokenEndpoint(MailAccount account) {
        if (account.getOauthTokenEndpoint() != null && !account.getOauthTokenEndpoint().isBlank()) {
            return account.getOauthTokenEndpoint();
        }
        MailAccount.OAuthProvider provider = account.getOauthProvider();
        if (provider == null) {
            throw new IllegalArgumentException("OAuth provider not configured for account " + account.getName());
        }
        return switch (provider) {
            case GOOGLE -> GOOGLE_TOKEN_URL;
            case MICROSOFT -> {
                String tenantId = account.getOauthTenantId();
                if (tenantId == null || tenantId.isBlank()) {
                    tenantId = "common";
                }
                yield String.format(MICROSOFT_TOKEN_URL_TEMPLATE, tenantId);
            }
            case CUSTOM -> throw new IllegalArgumentException("Custom OAuth providers require token endpoint");
        };
    }

    private String resolveProviderScope(MailAccount.OAuthProvider provider) {
        String envKey = switch (provider) {
            case GOOGLE -> "ECM_MAIL_OAUTH_GOOGLE_SCOPE";
            case MICROSOFT -> "ECM_MAIL_OAUTH_MICROSOFT_SCOPE";
            case CUSTOM -> null;
        };
        if (envKey != null) {
            String value = environment.getProperty(envKey);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return switch (provider) {
            case GOOGLE -> GOOGLE_SCOPE_DEFAULT;
            case MICROSOFT -> MICROSOFT_SCOPE_DEFAULT;
            case CUSTOM -> null;
        };
    }

    private String resolveProviderClientId(MailAccount.OAuthProvider provider) {
        String envKey = switch (provider) {
            case GOOGLE -> "ECM_MAIL_OAUTH_GOOGLE_CLIENT_ID";
            case MICROSOFT -> "ECM_MAIL_OAUTH_MICROSOFT_CLIENT_ID";
            case CUSTOM -> null;
        };
        String value = envKey != null ? environment.getProperty(envKey) : null;
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing OAuth client id env var " + envKey);
        }
        return value;
    }

    private String resolveProviderClientSecret(MailAccount.OAuthProvider provider) {
        String envKey = switch (provider) {
            case GOOGLE -> "ECM_MAIL_OAUTH_GOOGLE_CLIENT_SECRET";
            case MICROSOFT -> "ECM_MAIL_OAUTH_MICROSOFT_CLIENT_SECRET";
            case CUSTOM -> null;
        };
        String value = envKey != null ? environment.getProperty(envKey) : null;
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing OAuth client secret env var " + envKey);
        }
        return value;
    }

    private String generateState() {
        byte[] bytes = new byte[24];
        java.security.SecureRandom random = new java.security.SecureRandom();
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void pruneExpiredStates() {
        Instant now = Instant.now();
        stateStore.entrySet().removeIf(entry -> now.isAfter(entry.getValue().createdAt().plus(STATE_TTL)));
    }

    public record OAuthAuthorizeResponse(String url, String state) {}

    public record OAuthCallbackResult(UUID accountId, String redirectUrl) {}

    private record OAuthState(UUID accountId, String callbackUrl, String redirectUrl, Instant createdAt) {}

    private record OAuthTokenResponse(String accessToken, String refreshToken, Long expiresIn) {}
}
