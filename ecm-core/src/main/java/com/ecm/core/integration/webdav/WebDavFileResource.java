package com.ecm.core.integration.webdav;

import com.ecm.core.entity.Document;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.SecurityService;
import io.milton.http.Auth;
import io.milton.http.Range;
import io.milton.http.Request;
import io.milton.resource.GetableResource;
import io.milton.resource.ReplaceableResource;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class WebDavFileResource implements GetableResource, ReplaceableResource {

    @Getter
    private final Document document;
    private final NodeService nodeService;
    private final SecurityService securityService;

    @Override
    public void sendContent(OutputStream out, Range range, Map<String, String> params, String contentType) throws IOException {
        // Stream content from ContentService
        // InputStream in = contentService.getContent(document.getContentId());
        // IOUtils.copy(in, out);
        log.info("WebDAV: Send content for {}", document.getName());
    }

    @Override
    public Long getMaxAgeSeconds(Auth auth) {
        return 60L;
    }

    @Override
    public String getContentType(String accepts) {
        return document.getMimeType();
    }

    @Override
    public Long getContentLength() {
        return document.getSize();
    }

    @Override
    public void replaceContent(InputStream in, Long length) {
        // Handle file update (create new version)
        log.info("WebDAV: Update content for {}", document.getName());
    }

    @Override
    public String getUniqueId() {
        return document.getId().toString();
    }

    @Override
    public String getName() {
        return document.getName();
    }

    @Override
    public Object authenticate(String user, String password) {
        return user;
    }

    @Override
    public boolean authorise(Request request, Request.Method method, Auth auth) {
        return auth != null;
    }

    @Override
    public String getRealm() {
        return "AthenaECM";
    }

    @Override
    public Date getModifiedDate() {
        return java.sql.Timestamp.valueOf(document.getLastModifiedDate());
    }

    @Override
    public String checkRedirect(Request request) {
        return null;
    }
}
