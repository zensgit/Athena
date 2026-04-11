package com.ecm.core.service.transfer;

import com.ecm.core.config.RepositoryIdentityProvider;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.ReplicationDefinition;
import com.ecm.core.entity.TransferReceiverRegistration;
import com.ecm.core.pipeline.PipelineResult;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.TransferReceiverRegistrationRepository;
import com.ecm.core.service.DocumentUploadService;
import com.ecm.core.service.FolderService;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.VersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferReceiverService {

    private static final String TRANSFER_RECEIVER_PRINCIPAL = "transfer-receiver";

    private final TransferReceiverRegistrationRepository receiverRepository;
    private final NodeRepository nodeRepository;
    private final FolderService folderService;
    private final DocumentUploadService documentUploadService;
    private final NodeService nodeService;
    private final VersionService versionService;
    private final RepositoryIdentityProvider repositoryIdentityProvider;

    @Transactional
    public VerifyFolderResponse verifyFolder(UUID folderId, String authUsername, String authSecret) {
        AuthorizedFolder authorized = resolveAuthorizedFolder(folderId, authUsername, authSecret);
        recordAccessSuccess(authorized.receiver(), "Verified receiver folder access: " + authorized.folder().getName());
        Folder folder = authorized.folder();
        return new VerifyFolderResponse(folder.getId(), folder.getName(), repositoryIdentityProvider.getTransferRepositoryId());
    }

    @Transactional
    public CreateFolderResponse createFolder(CreateFolderRequest request, String authUsername, String authSecret) {
        if (request == null) {
            throw new IllegalArgumentException("Transfer receiver folder request is required");
        }
        String requestedName = normalizeRequired(request.name(), "Folder name is required");
        AuthorizedFolder authorized = resolveAuthorizedFolder(request.parentFolderId(), authUsername, authSecret);
        Folder parent = authorized.folder();
        ConflictResolution resolution = resolveFolderConflict(
            parent.getId(),
            requestedName,
            request.conflictPolicy() != null ? request.conflictPolicy() : ReplicationDefinition.ConflictPolicy.RENAME
        );
        if (resolution.disposition() == ConflictDisposition.SKIPPED) {
            Folder existingFolder = requireFolder(resolution.existingNode());
            recordAccessSuccess(authorized.receiver(), "Skipped existing receiver folder: " + existingFolder.getName());
            return new CreateFolderResponse(
                existingFolder.getId(),
                existingFolder.getName(),
                resolution.disposition(),
                "Skipped existing receiver folder"
            );
        }

        SecurityContext previous = pushTransferAuthentication();
        try {
            Folder created = folderService.createFolder(new FolderService.CreateFolderRequest(
                resolution.resolvedName(),
                normalizeOptional(request.description()),
                parent.getId(),
                Folder.FolderType.GENERAL,
                null,
                null,
                false,
                null,
                true,
                false,
                null
            ));
            String message = switch (resolution.disposition()) {
                case CREATED -> "Created receiver folder";
                case RENAMED -> "Created renamed receiver folder";
                case OVERWRITTEN -> "Overwrote receiver folder";
                case SKIPPED -> "Skipped existing receiver folder";
            };
            recordAccessSuccess(authorized.receiver(), message + ": " + created.getName());
            return new CreateFolderResponse(created.getId(), created.getName(), resolution.disposition(), message);
        } finally {
            popTransferAuthentication(previous);
        }
    }

    @Transactional
    public UploadDocumentResponse uploadDocument(
        MultipartFile file,
        UUID parentFolderId,
        String description,
        String authUsername,
        String authSecret
    ) throws IOException {
        return uploadDocument(
            file,
            parentFolderId,
            description,
            ReplicationDefinition.ConflictPolicy.RENAME,
            authUsername,
            authSecret
        );
    }

    @Transactional
    public UploadDocumentResponse uploadDocument(
        MultipartFile file,
        UUID parentFolderId,
        String description,
        ReplicationDefinition.ConflictPolicy conflictPolicy,
        String authUsername,
        String authSecret
    ) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A non-empty file is required");
        }

        AuthorizedFolder authorized = resolveAuthorizedFolder(parentFolderId, authUsername, authSecret);
        Folder parent = authorized.folder();
        String filename = normalizeRequired(file.getOriginalFilename(), "File name is required");
        ConflictResolution resolution = resolveDocumentConflict(
            parent.getId(),
            filename,
            conflictPolicy != null ? conflictPolicy : ReplicationDefinition.ConflictPolicy.RENAME
        );
        if (resolution.disposition() == ConflictDisposition.SKIPPED) {
            com.ecm.core.entity.Document existingDocument = requireDocument(resolution.existingNode());
            recordAccessSuccess(authorized.receiver(), "Skipped existing receiver document: " + existingDocument.getName());
            return new UploadDocumentResponse(
                existingDocument.getId(),
                existingDocument.getName(),
                resolution.disposition(),
                "Skipped existing receiver document"
            );
        }

        MultipartFile effectiveFile = Objects.equals(resolution.resolvedName(), filename)
            ? file
            : renamedFile(file, resolution.resolvedName());

        Map<String, Object> properties = null;
        String normalizedDescription = normalizeOptional(description);
        if (normalizedDescription != null) {
            properties = Map.of("description", normalizedDescription);
        }

        SecurityContext previous = pushTransferAuthentication();
        try {
            if (resolution.disposition() == ConflictDisposition.OVERWRITTEN && resolution.existingNode() instanceof com.ecm.core.entity.Document existingDocument) {
                versionService.createVersion(
                    existingDocument.getId(),
                    effectiveFile,
                    "Replicated overwrite via transfer receiver",
                    false
                );
                if (normalizedDescription != null) {
                    nodeService.updateNode(existingDocument.getId(), Map.of("description", normalizedDescription));
                }
                recordAccessSuccess(authorized.receiver(), "Overwrote receiver document: " + existingDocument.getName());
                return new UploadDocumentResponse(
                    existingDocument.getId(),
                    existingDocument.getName(),
                    resolution.disposition(),
                    "Overwrote receiver document"
                );
            }
            PipelineResult result = documentUploadService.uploadDocument(effectiveFile, parent.getId(), properties);
            if (!result.isSuccess() || result.getDocumentId() == null) {
                throw new IllegalStateException("Transfer receiver document upload failed");
            }
            String message = switch (resolution.disposition()) {
                case CREATED -> "Uploaded receiver document";
                case RENAMED -> "Uploaded renamed receiver document";
                case OVERWRITTEN -> "Overwrote receiver document";
                case SKIPPED -> "Skipped existing receiver document";
            };
            recordAccessSuccess(authorized.receiver(), message + ": " + effectiveFile.getOriginalFilename());
            return new UploadDocumentResponse(
                result.getDocumentId(),
                effectiveFile.getOriginalFilename(),
                resolution.disposition(),
                message
            );
        } finally {
            popTransferAuthentication(previous);
        }
    }

    private AuthorizedFolder resolveAuthorizedFolder(UUID folderId, String authUsername, String authSecret) {
        if (folderId == null) {
            throw new IllegalArgumentException("folderId is required");
        }

        Folder requestedFolder = loadFolder(folderId);
        String normalizedUsername = normalizeOptional(authUsername);
        String normalizedSecret = normalizeOptional(authSecret);
        List<TransferReceiverRegistration> matchingReceivers = receiverRepository.findAll().stream()
            .filter(TransferReceiverRegistration::isEnabled)
            .filter(receiver -> matchesCredentials(receiver, normalizedUsername, normalizedSecret))
            .toList();

        TransferReceiverRegistration wrongScopeMatch = null;
        for (TransferReceiverRegistration receiver : matchingReceivers) {
            Folder rootFolder = tryLoadFolder(receiver.getRootFolderId());
            if (rootFolder != null && isWithinRoot(requestedFolder, rootFolder)) {
                return new AuthorizedFolder(receiver, requestedFolder);
            }
            wrongScopeMatch = receiver;
        }

        if (wrongScopeMatch != null) {
            recordAccessFailure(wrongScopeMatch, "Transfer receiver credentials do not permit folder: " + folderId);
        }
        throw new SecurityException("Transfer receiver credentials do not permit folder: " + folderId);
    }

    private boolean matchesCredentials(TransferReceiverRegistration receiver, String authUsername, String authSecret) {
        com.ecm.core.entity.TransferTarget.AuthType authType = receiver.getAuthType() != null
            ? receiver.getAuthType()
            : com.ecm.core.entity.TransferTarget.AuthType.NONE;
        String targetUsername = normalizeOptional(receiver.getAuthUsername());
        String targetSecret = normalizeOptional(receiver.getAuthSecret());
        return switch (authType) {
            case NONE -> authUsername == null && authSecret == null;
            case BASIC -> Objects.equals(targetUsername, authUsername) && Objects.equals(targetSecret, authSecret);
            case BEARER -> authUsername == null && Objects.equals(targetSecret, authSecret);
        };
    }

    private void recordAccessSuccess(TransferReceiverRegistration receiver, String message) {
        receiver.setLastAccessStatus(TransferReceiverRegistration.AccessStatus.SUCCESS);
        receiver.setLastAccessMessage(message);
        receiver.setLastAccessedAt(java.time.LocalDateTime.now());
        receiverRepository.save(receiver);
    }

    private void recordAccessFailure(TransferReceiverRegistration receiver, String message) {
        receiver.setLastAccessStatus(TransferReceiverRegistration.AccessStatus.FAILED);
        receiver.setLastAccessMessage(message);
        receiver.setLastAccessedAt(java.time.LocalDateTime.now());
        receiverRepository.save(receiver);
    }

    private Folder tryLoadFolder(UUID folderId) {
        try {
            return loadFolder(folderId);
        } catch (NoSuchElementException | IllegalArgumentException ex) {
            return null;
        }
    }

    private Folder loadFolder(UUID folderId) {
        Node folder = nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(folderId, Node.ArchiveStatus.LIVE)
            .orElseThrow(() -> new NoSuchElementException("Folder not found: " + folderId));
        if (!(folder instanceof Folder resolved)) {
            throw new IllegalArgumentException("Node is not a folder: " + folderId);
        }
        return resolved;
    }

    private boolean isWithinRoot(Folder candidate, Folder root) {
        if (candidate.getId().equals(root.getId())) {
            return true;
        }
        String candidatePath = normalizePath(candidate.getPath());
        String rootPath = normalizePath(root.getPath());
        if (candidatePath == null || rootPath == null) {
            return false;
        }
        if ("/".equals(rootPath)) {
            return candidatePath.startsWith("/");
        }
        return candidatePath.startsWith(rootPath + "/");
    }

    private ConflictResolution resolveFolderConflict(
        UUID parentFolderId,
        String requestedName,
        ReplicationDefinition.ConflictPolicy conflictPolicy
    ) {
        Optional<Node> existing = nodeRepository.findByParentIdAndName(parentFolderId, requestedName);
        if (existing.isEmpty()) {
            return new ConflictResolution(requestedName, null, ConflictDisposition.CREATED);
        }
        return switch (conflictPolicy) {
            case SKIP -> {
                if (!(existing.get() instanceof Folder)) {
                    throw new IllegalStateException("Cannot skip folder creation because a non-folder already exists: " + requestedName);
                }
                yield new ConflictResolution(requestedName, existing.get(), ConflictDisposition.SKIPPED);
            }
            case RENAME -> new ConflictResolution(generateReplicaName(parentFolderId, requestedName), existing.get(), ConflictDisposition.RENAMED);
            case OVERWRITE -> {
                deleteExistingConflict(existing.get());
                yield new ConflictResolution(requestedName, existing.get(), ConflictDisposition.OVERWRITTEN);
            }
        };
    }

    private ConflictResolution resolveDocumentConflict(
        UUID parentFolderId,
        String requestedName,
        ReplicationDefinition.ConflictPolicy conflictPolicy
    ) {
        Optional<Node> existing = nodeRepository.findByParentIdAndName(parentFolderId, requestedName);
        if (existing.isEmpty()) {
            return new ConflictResolution(requestedName, null, ConflictDisposition.CREATED);
        }
        return switch (conflictPolicy) {
            case SKIP -> {
                if (!(existing.get() instanceof com.ecm.core.entity.Document)) {
                    throw new IllegalStateException("Cannot skip document upload because a non-document already exists: " + requestedName);
                }
                yield new ConflictResolution(requestedName, existing.get(), ConflictDisposition.SKIPPED);
            }
            case RENAME -> new ConflictResolution(generateReplicaName(parentFolderId, requestedName), existing.get(), ConflictDisposition.RENAMED);
            case OVERWRITE -> {
                if (existing.get() instanceof Folder) {
                    deleteExistingConflict(existing.get());
                }
                yield new ConflictResolution(requestedName, existing.get(), ConflictDisposition.OVERWRITTEN);
            }
        };
    }

    private void deleteExistingConflict(Node existing) {
        nodeService.deleteNode(existing.getId(), false);
    }

    private String generateReplicaName(UUID parentFolderId, String requestedName) {
        String baseName = requestedName;
        String extension = "";
        int dotIndex = requestedName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = requestedName.substring(0, dotIndex);
            extension = requestedName.substring(dotIndex);
        }

        String candidate = requestedName;
        int attempt = 1;
        while (nodeRepository.findByParentIdAndName(parentFolderId, candidate).isPresent()) {
            candidate = baseName + " (Replica " + attempt + ")" + extension;
            attempt++;
        }
        return candidate;
    }

    private Folder requireFolder(Node node) {
        if (!(node instanceof Folder folder)) {
            throw new IllegalStateException("Expected existing folder conflict");
        }
        return folder;
    }

    private com.ecm.core.entity.Document requireDocument(Node node) {
        if (!(node instanceof com.ecm.core.entity.Document document)) {
            throw new IllegalStateException("Expected existing document conflict");
        }
        return document;
    }

    private MultipartFile renamedFile(MultipartFile file, String filename) throws IOException {
        return new MockMultipartFile(
            file.getName(),
            filename,
            file.getContentType(),
            file.getBytes()
        );
    }

    private SecurityContext pushTransferAuthentication() {
        SecurityContext previous = SecurityContextHolder.getContext();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            TRANSFER_RECEIVER_PRINCIPAL,
            "transfer-receiver",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        return previous;
    }

    private void popTransferAuthentication(SecurityContext previous) {
        if (previous != null) {
            SecurityContextHolder.setContext(previous);
        } else {
            SecurityContextHolder.clearContext();
        }
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String trimmed = path.trim();
        if ("/".equals(trimmed)) {
            return "/";
        }
        return trimmed.replaceAll("/+$", "");
    }

    public record VerifyFolderResponse(UUID folderId, String folderName, String repositoryId) {}

    public record CreateFolderRequest(
        UUID parentFolderId,
        String name,
        String description,
        ReplicationDefinition.ConflictPolicy conflictPolicy
    ) {}

    public record CreateFolderResponse(
        UUID folderId,
        String folderName,
        ConflictDisposition disposition,
        String message
    ) {}

    public record UploadDocumentResponse(
        UUID documentId,
        String documentName,
        ConflictDisposition disposition,
        String message
    ) {}

    private record AuthorizedFolder(TransferReceiverRegistration receiver, Folder folder) {}

    private record ConflictResolution(String resolvedName, Node existingNode, ConflictDisposition disposition) {}

    public enum ConflictDisposition {
        CREATED,
        RENAMED,
        SKIPPED,
        OVERWRITTEN
    }
}
