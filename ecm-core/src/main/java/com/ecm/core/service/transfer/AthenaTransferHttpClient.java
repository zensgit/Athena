package com.ecm.core.service.transfer;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.ReplicationDefinition;
import com.ecm.core.entity.TransferTarget;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.service.ContentService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AthenaTransferHttpClient implements TransferClient {

    private final RestTemplate restTemplate;
    private final ContentService contentService;
    private final NodeRepository nodeRepository;

    @Override
    public TransferTarget.TransportType transportType() {
        return TransferTarget.TransportType.ATHENA_HTTP;
    }

    @Override
    public TransferVerificationResult verifyTarget(TransferTarget target) {
        String url = UriComponentsBuilder
            .fromHttpUrl(apiUrl(target, "/transfer/receiver/verify"))
            .queryParam("folderId", target.getTargetFolderId())
            .toUriString();
        ResponseEntity<JsonNode> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            new HttpEntity<>(jsonHeaders(target)),
            JsonNode.class
        );
        JsonNode body = response.getBody();
        if (body == null) {
            throw new IllegalStateException("Remote transfer receiver verification returned an empty response");
        }
        String name = body.path("folderName").asText(target.getTargetFolderId().toString());
        String remoteRepoId = body.has("repositoryId") ? body.path("repositoryId").asText(null) : null;
        return new TransferVerificationResult("Verified remote Athena transfer receiver folder: " + name, remoteRepoId);
    }

    @Override
    public TransferExecutionResult replicate(
        TransferTarget target,
        Node source,
        boolean includeChildren,
        ReplicationDefinition.ConflictPolicy conflictPolicy
    ) {
        UUID remoteId;
        String message;
        List<TransferExecutionEntry> entries = new ArrayList<>();
        if (source instanceof Folder folder) {
            TransferReceiverFolderResult folderResult = createRemoteFolder(
                target,
                target.getTargetFolderId(),
                folder.getName(),
                folder.getDescription(),
                conflictPolicy
            );
            remoteId = folderResult.folderId();
            message = folderResult.message();
            entries.add(entryFor(source, remoteId, folderResult.disposition().name(), folderResult.message()));
            if (includeChildren && folderResult.disposition() != TransferReceiverService.ConflictDisposition.SKIPPED) {
                replicateChildren(target, folder, remoteId, conflictPolicy, entries);
            }
        } else if (source instanceof Document document) {
            TransferReceiverDocumentResult documentResult = uploadRemoteDocument(
                target,
                target.getTargetFolderId(),
                document,
                document.getName(),
                conflictPolicy
            );
            remoteId = documentResult.documentId();
            message = documentResult.message();
            entries.add(entryFor(source, remoteId, documentResult.disposition().name(), documentResult.message()));
        } else {
            throw new IllegalArgumentException("Unsupported node type for remote replication: " + source.getNodeType());
        }
        return new TransferExecutionResult(
            remoteId,
            message != null ? message : "Remote Athena HTTP replication completed",
            entries
        );
    }

    private void replicateChildren(
        TransferTarget target,
        Folder sourceFolder,
        UUID remoteParentId,
        ReplicationDefinition.ConflictPolicy conflictPolicy,
        List<TransferExecutionEntry> entries
    ) {
        List<Node> children = nodeRepository.findByParentIdAndDeletedFalseAndArchiveStatus(
            sourceFolder.getId(),
            Node.ArchiveStatus.LIVE
        ).stream().sorted((left, right) -> String.CASE_INSENSITIVE_ORDER.compare(left.getName(), right.getName())).toList();

        for (Node child : children) {
            if (child instanceof Folder folder) {
                TransferReceiverFolderResult folderResult = createRemoteFolder(
                    target,
                    remoteParentId,
                    folder.getName(),
                    folder.getDescription(),
                    conflictPolicy
                );
                entries.add(entryFor(folder, folderResult.folderId(), folderResult.disposition().name(), folderResult.message()));
                if (folderResult.disposition() != TransferReceiverService.ConflictDisposition.SKIPPED) {
                    replicateChildren(target, folder, folderResult.folderId(), conflictPolicy, entries);
                }
            } else if (child instanceof Document document) {
                TransferReceiverDocumentResult documentResult = uploadRemoteDocument(
                    target,
                    remoteParentId,
                    document,
                    document.getName(),
                    conflictPolicy
                );
                entries.add(entryFor(document, documentResult.documentId(), documentResult.disposition().name(), documentResult.message()));
            }
        }
    }

    private TransferReceiverFolderResult createRemoteFolder(
        TransferTarget target,
        UUID parentId,
        String name,
        String description,
        ReplicationDefinition.ConflictPolicy conflictPolicy
    ) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("parentFolderId", parentId);
        request.put("name", name);
        request.put("description", description);
        request.put("conflictPolicy", conflictPolicy != null ? conflictPolicy.name() : ReplicationDefinition.ConflictPolicy.RENAME.name());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, jsonHeaders(target));
        ResponseEntity<JsonNode> response = restTemplate.exchange(
            apiUrl(target, "/transfer/receiver/folders"),
            HttpMethod.POST,
            entity,
            JsonNode.class
        );
        JsonNode body = response.getBody();
        return new TransferReceiverFolderResult(
            extractUuid(body, "folderId", "Remote folder creation did not return a folderId"),
            body != null && !body.path("folderName").asText().isBlank() ? body.path("folderName").asText() : name,
            parseDisposition(body),
            extractMessage(body, "Remote folder creation completed")
        );
    }

    private TransferReceiverDocumentResult uploadRemoteDocument(
        TransferTarget target,
        UUID remoteFolderId,
        Document document,
        String fileName,
        ReplicationDefinition.ConflictPolicy conflictPolicy
    ) {
        byte[] bytes = readContent(document);
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };

        HttpHeaders partHeaders = new HttpHeaders();
        if (document.getMimeType() != null && !document.getMimeType().isBlank()) {
            partHeaders.setContentType(MediaType.parseMediaType(document.getMimeType()));
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(resource, partHeaders));
        body.add("parentFolderId", remoteFolderId.toString());
        if (document.getDescription() != null && !document.getDescription().isBlank()) {
            body.add("description", document.getDescription());
        }
        body.add("conflictPolicy", (conflictPolicy != null ? conflictPolicy : ReplicationDefinition.ConflictPolicy.RENAME).name());

        HttpHeaders headers = authHeaders(target);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<JsonNode> response = restTemplate.exchange(
            apiUrl(target, "/transfer/receiver/documents"),
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            JsonNode.class
        );
        JsonNode bodyNode = response.getBody();
        return new TransferReceiverDocumentResult(
            extractUuid(bodyNode, "documentId", "Remote upload did not return a documentId"),
            bodyNode != null && !bodyNode.path("documentName").asText().isBlank() ? bodyNode.path("documentName").asText() : fileName,
            parseDisposition(bodyNode),
            extractMessage(bodyNode, "Remote document upload completed")
        );
    }

    private HttpHeaders jsonHeaders(TransferTarget target) {
        HttpHeaders headers = authHeaders(target);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private HttpHeaders authHeaders(TransferTarget target) {
        HttpHeaders headers = new HttpHeaders();
        TransferTarget.AuthType authType = target.getAuthType() != null ? target.getAuthType() : TransferTarget.AuthType.NONE;
        switch (authType) {
            case BASIC -> {
                headers.set(TransferReceiverHeaders.USER_HEADER, safe(target.getAuthUsername(), "authUsername is required for BASIC auth"));
                headers.set(TransferReceiverHeaders.SECRET_HEADER, safe(target.getAuthSecret(), "authSecret is required for BASIC auth"));
            }
            case BEARER -> headers.set(TransferReceiverHeaders.SECRET_HEADER, safe(target.getAuthSecret(), "authSecret is required for BEARER auth"));
            case NONE -> {
            }
        }
        headers.setAcceptCharset(List.of(StandardCharsets.UTF_8));
        return headers;
    }

    private String apiUrl(TransferTarget target, String suffix) {
        String endpointUrl = safe(target.getEndpointUrl(), "endpointUrl is required for ATHENA_HTTP targets");
        String endpointPath = normalizeEndpointPath(target.getEndpointPath());
        String normalizedSuffix = suffix.startsWith("/") ? suffix : "/" + suffix;
        return endpointUrl.replaceAll("/+$", "") + endpointPath + normalizedSuffix;
    }

    private String normalizeEndpointPath(String endpointPath) {
        if (endpointPath == null || endpointPath.isBlank()) {
            return "/api/v1";
        }
        String normalized = endpointPath.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized.replaceAll("/+$", "");
    }

    private String safe(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private byte[] readContent(Document document) {
        if (document.getContentId() == null || document.getContentId().isBlank()) {
            throw new IllegalStateException("Document has no content to transfer: " + document.getId());
        }
        try (InputStream content = contentService.getContent(document.getContentId())) {
            return content.readAllBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read local content for transfer: " + document.getId(), ex);
        }
    }

    private UUID extractUuid(JsonNode body, String fieldName, String errorMessage) {
        if (body == null || body.path(fieldName).isMissingNode() || body.path(fieldName).asText().isBlank()) {
            throw new IllegalStateException(errorMessage);
        }
        return UUID.fromString(body.path(fieldName).asText());
    }

    private TransferReceiverService.ConflictDisposition parseDisposition(JsonNode body) {
        if (body == null || body.path("disposition").asText().isBlank()) {
            return TransferReceiverService.ConflictDisposition.CREATED;
        }
        return TransferReceiverService.ConflictDisposition.valueOf(body.path("disposition").asText());
    }

    private String extractMessage(JsonNode body, String fallback) {
        if (body == null || body.path("message").asText().isBlank()) {
            return fallback;
        }
        return body.path("message").asText();
    }

    private TransferExecutionEntry entryFor(Node source, UUID targetNodeId, String action, String message) {
        LocalDateTime now = LocalDateTime.now();
        return new TransferExecutionEntry(
            source.getId(),
            source.getPath(),
            source.getNodeType() != null ? source.getNodeType().name() : source.getClass().getSimpleName(),
            targetNodeId,
            action,
            message,
            now,
            now
        );
    }

    private record TransferReceiverFolderResult(
        UUID folderId,
        String folderName,
        TransferReceiverService.ConflictDisposition disposition,
        String message
    ) {}

    private record TransferReceiverDocumentResult(
        UUID documentId,
        String documentName,
        TransferReceiverService.ConflictDisposition disposition,
        String message
    ) {}
}
