package com.ecm.core.integration.wopi.service;

import com.ecm.core.entity.Document;
import com.ecm.core.integration.wopi.model.WopiCheckFileInfoResponse;
import com.ecm.core.service.ContentService;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.SecurityService;
import com.ecm.core.service.VersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * WOPI Service
 * 
 * Implements the WOPI Host operations required by Microsoft Office Online / Collabora.
 * See: https://wopi.readthedocs.io/en/latest/
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WopiService {

    private final NodeService nodeService;
    private final ContentService contentService;
    private final SecurityService securityService;
    private final VersionService versionService;
    private final WopiAccessTokenService accessTokenService;

    @Value("${ecm.wopi.public-app-url:http://localhost:5500}")
    private String publicAppUrl;

    /**
     * CheckFileInfo (GET)
     * Returns information about the file and permissions.
     */
    @Transactional(readOnly = true)
    public WopiCheckFileInfoResponse checkFileInfo(UUID documentId, String accessToken) {
        WopiAccessTokenService.TokenInfo tokenInfo = accessTokenService.validate(documentId, accessToken);
        SecurityContext previous = pushAuthentication(tokenInfo);
        try {
            Document doc = (Document) nodeService.getNode(documentId);
            boolean canWrite = tokenInfo.canWrite();

            return WopiCheckFileInfoResponse.builder()
                .baseFileName(doc.getName())
                .ownerId(doc.getCreatedBy())
                .size(doc.getSize())
                .userId(tokenInfo.userId())
                .userFriendlyName(tokenInfo.userFriendlyName())
                .version(doc.getVersionLabel() != null ? doc.getVersionLabel() : "1.0")
                .userCanWrite(canWrite)
                .readOnly(!canWrite)
                .userCanRename(false) // Simplified
                .supportsLocks(true)
                .supportsUpdate(true)
                .postMessageOrigin(publicAppUrl)
                .breadcrumbBrandName("Athena ECM")
                .breadcrumbDocName(doc.getName())
                .build();
        } finally {
            popAuthentication(previous);
        }
    }

    /**
     * GetFile (GET contents)
     * Returns the raw file content.
     */
    @Transactional(readOnly = true)
    public InputStream getFileContent(UUID documentId, String accessToken) throws IOException {
        WopiAccessTokenService.TokenInfo tokenInfo = accessTokenService.validate(documentId, accessToken);
        SecurityContext previous = pushAuthentication(tokenInfo);
        try {
            Document doc = (Document) nodeService.getNode(documentId);
            return contentService.getContent(doc.getContentId());
        } finally {
            popAuthentication(previous);
        }
    }

    /**
     * PutFile (POST contents)
     * Updates the file content.
     */
    @Transactional
    public void putFile(UUID documentId, String accessToken, InputStream content, long size) throws IOException {
        WopiAccessTokenService.TokenInfo tokenInfo = accessTokenService.validate(documentId, accessToken);
        if (!tokenInfo.canWrite()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "WOPI token is read-only");
        }

        SecurityContext previous = pushAuthentication(tokenInfo);
        try {
            Document doc = (Document) nodeService.getNode(documentId);

            log.info("WOPI PutFile: Updating document {} (size={} bytes)", documentId, size);

            // VersionService will validate WRITE permission, checkout state, store content, and update doc metadata.
            versionService.createVersion(documentId, content, doc.getName(), "Updated via WOPI", false);
        } finally {
            popAuthentication(previous);
        }
    }

    private SecurityContext pushAuthentication(WopiAccessTokenService.TokenInfo tokenInfo) {
        SecurityContext previous = SecurityContextHolder.getContext();

        List<SimpleGrantedAuthority> authorities = tokenInfo.authorities() != null
            ? tokenInfo.authorities().stream().map(SimpleGrantedAuthority::new).toList()
            : List.of();

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            tokenInfo.userId(),
            "wopi",
            authorities
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        return previous;
    }

    private void popAuthentication(SecurityContext previous) {
        if (previous != null) {
            SecurityContextHolder.setContext(previous);
        } else {
            SecurityContextHolder.clearContext();
        }
    }
}
