package com.ecm.core.service.transfer;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
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
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
@RequiredArgsConstructor
public class AthenaTransferHttpClient implements TransferClient {

    private static final int REMOTE_FOLDER_PAGE_SIZE = 500;

    private final RestTemplate restTemplate;
    private final ContentService contentService;
    private final NodeRepository nodeRepository;

    @Override
    public TransferTarget.TransportType transportType() {
        return TransferTarget.TransportType.ATHENA_HTTP;
    }

    @Override
    public TransferVerificationResult verifyTarget(TransferTarget target) {
        JsonNode folder = getRemoteFolder(target, target.getTargetFolderId());
        String name = folder.path("name").asText(target.getTargetFolderId().toString());
        return new TransferVerificationResult("Verified remote Athena folder: " + name);
    }

    @Override
    public TransferExecutionResult replicate(TransferTarget target, Node source, boolean includeChildren) {
        UUID remoteId;
        if (source instanceof Folder folder) {
            String folderName = resolveReplicaName(target, target.getTargetFolderId(), folder.getName());
            remoteId = createRemoteFolder(target, target.getTargetFolderId(), folderName, folder.getDescription());
            if (includeChildren) {
                replicateChildren(target, folder, remoteId);
            }
        } else if (source instanceof Document document) {
            String fileName = resolveReplicaName(target, target.getTargetFolderId(), document.getName());
            remoteId = uploadRemoteDocument(target, target.getTargetFolderId(), document, fileName);
        } else {
            throw new IllegalArgumentException("Unsupported node type for remote replication: " + source.getNodeType());
        }
        return new TransferExecutionResult(remoteId, "Remote Athena HTTP replication completed");
    }

    private void replicateChildren(TransferTarget target, Folder sourceFolder, UUID remoteParentId) {
        List<Node> children = nodeRepository.findByParentIdAndDeletedFalseAndArchiveStatus(
            sourceFolder.getId(),
            Node.ArchiveStatus.LIVE
        ).stream().sorted(Comparator.comparing(Node::getName, String.CASE_INSENSITIVE_ORDER)).toList();

        for (Node child : children) {
            if (child instanceof Folder folder) {
                String folderName = resolveReplicaName(target, remoteParentId, folder.getName());
                UUID remoteFolderId = createRemoteFolder(target, remoteParentId, folderName, folder.getDescription());
                replicateChildren(target, folder, remoteFolderId);
            } else if (child instanceof Document document) {
                String fileName = resolveReplicaName(target, remoteParentId, document.getName());
                uploadRemoteDocument(target, remoteParentId, document, fileName);
            }
        }
    }

    private JsonNode getRemoteFolder(TransferTarget target, UUID folderId) {
        String url = apiUrl(target, "/folders/{folderId}");
        ResponseEntity<JsonNode> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            new HttpEntity<>(jsonHeaders(target)),
            JsonNode.class,
            folderId
        );
        JsonNode body = response.getBody();
        if (body == null || body.path("id").isMissingNode()) {
            throw new IllegalStateException("Remote folder lookup returned an empty response");
        }
        return body;
    }

    private UUID createRemoteFolder(TransferTarget target, UUID parentId, String name, String description) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", name);
        request.put("description", description);
        request.put("parentId", parentId);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, jsonHeaders(target));
        ResponseEntity<JsonNode> response = restTemplate.exchange(
            apiUrl(target, "/folders"),
            HttpMethod.POST,
            entity,
            JsonNode.class
        );
        return extractUuid(response.getBody(), "id", "Remote folder creation did not return an id");
    }

    private UUID uploadRemoteDocument(TransferTarget target, UUID remoteFolderId, Document document, String fileName) {
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
        body.add("folderId", remoteFolderId.toString());
        if (document.getDescription() != null && !document.getDescription().isBlank()) {
            body.add("description", document.getDescription());
        }

        HttpHeaders headers = authHeaders(target);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<JsonNode> response = restTemplate.exchange(
            apiUrl(target, "/documents/upload"),
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            JsonNode.class
        );
        return extractUuid(response.getBody(), "documentId", "Remote upload did not return a documentId");
    }

    private String resolveReplicaName(TransferTarget target, UUID remoteFolderId, String requestedName) {
        Set<String> existingNames = listRemoteChildNames(target, remoteFolderId);
        String baseName = requestedName;
        String extension = "";
        int dotIndex = requestedName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = requestedName.substring(0, dotIndex);
            extension = requestedName.substring(dotIndex);
        }
        String candidate = requestedName;
        int attempt = 1;
        while (existingNames.contains(candidate)) {
            candidate = baseName + " (Replica " + attempt + ")" + extension;
            attempt++;
        }
        return candidate;
    }

    private Set<String> listRemoteChildNames(TransferTarget target, UUID remoteFolderId) {
        Set<String> names = new HashSet<>();
        int page = 0;
        boolean last = false;
        while (!last) {
            String url = UriComponentsBuilder
                .fromHttpUrl(apiUrl(target, "/folders/" + remoteFolderId + "/contents"))
                .queryParam("page", page)
                .queryParam("size", REMOTE_FOLDER_PAGE_SIZE)
                .toUriString();
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(target)),
                JsonNode.class
            );
            JsonNode body = response.getBody();
            if (body == null) {
                break;
            }
            JsonNode content = body.path("content");
            if (content.isArray()) {
                for (JsonNode entry : content) {
                    String name = entry.path("name").asText(null);
                    if (name != null) {
                        names.add(name);
                    }
                }
            }
            last = body.path("last").asBoolean(true);
            page++;
        }
        return names;
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
            case BASIC -> headers.setBasicAuth(
                safe(target.getAuthUsername(), "authUsername is required for BASIC auth"),
                safe(target.getAuthSecret(), "authSecret is required for BASIC auth"),
                StandardCharsets.UTF_8
            );
            case BEARER -> headers.setBearerAuth(safe(target.getAuthSecret(), "authSecret is required for BEARER auth"));
            case NONE -> {
            }
        }
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
}
