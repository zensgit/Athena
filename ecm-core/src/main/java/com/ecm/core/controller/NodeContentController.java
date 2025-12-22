package com.ecm.core.controller;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Node;
import com.ecm.core.service.ContentService;
import com.ecm.core.service.NodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping({"/api/nodes", "/api/v1/nodes"})
@RequiredArgsConstructor
@Tag(name = "Node Content", description = "Download node content (documents)")
public class NodeContentController {

    private final NodeService nodeService;
    private final ContentService contentService;

    @GetMapping("/{nodeId}/content")
    @Operation(summary = "Download node content", description = "Download the current content of a document node")
    public ResponseEntity<InputStreamResource> downloadContent(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId) throws IOException {

        Node node = nodeService.getNode(nodeId);
        if (!(node instanceof Document document)) {
            throw new ResponseStatusException(BAD_REQUEST, "Node is not a document: " + nodeId);
        }

        InputStream content = contentService.getContent(document.getContentId());

        HttpHeaders headers = new HttpHeaders();
        if (document.getMimeType() != null && !document.getMimeType().isBlank()) {
            headers.setContentType(MediaType.parseMediaType(document.getMimeType()));
        } else {
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        }
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename(document.getName(), StandardCharsets.UTF_8)
            .build());
        if (document.getFileSize() != null) {
            headers.setContentLength(document.getFileSize());
        }

        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(content));
    }
}
