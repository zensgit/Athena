package com.ecm.core.integration.wopi.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.integration.wopi.model.WopiHealthResponse;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.SecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WopiEditorService {

    private final CollaboraDiscoveryService discoveryService;
    private final NodeService nodeService;
    private final SecurityService securityService;
    private final WopiAccessTokenService accessTokenService;

    @Value("${ecm.wopi.enabled:true}")
    private boolean enabled;

    @Value("${ecm.wopi.host-url:http://ecm-core:8080}")
    private String wopiHostUrl;

    @Value("${ecm.wopi.access-token-ttl-seconds:3600}")
    private long accessTokenTtlSeconds;

    public WopiHealthResponse getHealth() {
        return WopiHealthResponse.builder()
            .enabled(enabled)
            .wopiHostUrl(wopiHostUrl)
            .discoveryUrl(discoveryService.getDiscoveryUrl())
            .capabilitiesUrl(discoveryService.getCapabilitiesUrl())
            .publicUrl(discoveryService.getPublicUrl())
            .discovery(discoveryService.getDiscoveryStatus())
            .capabilities(discoveryService.getCapabilities())
            .build();
    }

    public WopiUrlResponse generateEditorUrl(UUID documentId, String permission) {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "WOPI editor is disabled");
        }

        Document document = (Document) nodeService.getNode(documentId);

        boolean wantWrite = "write".equalsIgnoreCase(permission);
        if (wantWrite) {
            securityService.checkPermission(document, PermissionType.WRITE);
        } else {
            securityService.checkPermission(document, PermissionType.READ);
        }

        String ext = getFileExtension(document.getName());
        String urlsrc = null;
        String[] actions = wantWrite
            ? new String[] {"edit", "editnew", "view", "view_comment"}
            : new String[] {"view", "view_comment", "edit"};
        for (String action : actions) {
            urlsrc = discoveryService.findUrlsrc(ext, action);
            if (urlsrc != null) {
                break;
            }
        }
        if (urlsrc == null) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "No WOPI action available for file type: " + ext);
        }

        String userId = securityService.getCurrentUser();
        String userFriendlyName = resolveUserFriendlyName();
        Collection<? extends GrantedAuthority> authorities = resolveAuthorities();

        long ttlSeconds = computeTtlSeconds();
        Instant expiresAt = Instant.now().plusSeconds(ttlSeconds);
        String token = accessTokenService.issue(
            documentId,
            userId,
            userFriendlyName,
            wantWrite,
            authorities,
            Duration.ofSeconds(ttlSeconds)
        );

        String wopiSrc = normalizeBaseUrl(wopiHostUrl) + "/wopi/files/" + documentId;
        String encodedWopiSrc = urlEncode(wopiSrc);
        String encodedToken = urlEncode(token);

        URI publicBase = URI.create(normalizeBaseUrl(discoveryService.getPublicUrl()));
        String editorUrl = UriComponentsBuilder.fromUriString(urlsrc)
            .scheme(publicBase.getScheme())
            .host(publicBase.getHost())
            .port(publicBase.getPort())
            .replaceQueryParam("WOPISrc")
            .replaceQueryParam("access_token")
            .replaceQueryParam("access_token_ttl")
            .queryParam("WOPISrc", encodedWopiSrc)
            .queryParam("access_token", encodedToken)
            // WOPI expects an absolute expiry time (epoch millis), not a duration.
            .queryParam("access_token_ttl", expiresAt.toEpochMilli())
            .build(true)
            .toUriString();

        long expiresAtMs = expiresAt.toEpochMilli();
        return new WopiUrlResponse(editorUrl, expiresAtMs);
    }

    private long computeTtlSeconds() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt && jwt.getExpiresAt() != null) {
            long seconds = jwt.getExpiresAt().getEpochSecond() - Instant.now().getEpochSecond();
            if (seconds > 0) {
                return seconds;
            }
        }
        return accessTokenTtlSeconds;
    }

    private String resolveUserFriendlyName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return securityService.getCurrentUser();
        }
        if (auth.getPrincipal() instanceof Jwt jwt) {
            String preferredUsername = jwt.getClaimAsString("preferred_username");
            if (preferredUsername != null && !preferredUsername.isBlank()) {
                return preferredUsername;
            }
        }
        return securityService.getCurrentUser();
    }

    private Collection<? extends GrantedAuthority> resolveAuthorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return java.util.List.of();
        }
        return auth.getAuthorities();
    }

    private String normalizeBaseUrl(String base) {
        if (base == null) {
            return "";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        // WOPISrc must be URL-encoded (nested URL)
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String getFileExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) {
            return "";
        }
        return filename.substring(idx + 1).toLowerCase();
    }

    public record WopiUrlResponse(String wopiUrl, long expiresAt) {}
}
