package com.ecm.core.service.transfer;

import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.TransferReceiverRegistration;
import com.ecm.core.pipeline.PipelineResult;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.TransferReceiverRegistrationRepository;
import com.ecm.core.service.DocumentUploadService;
import com.ecm.core.service.FolderService;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferReceiverService {

    private static final String TRANSFER_RECEIVER_PRINCIPAL = "transfer-receiver";

    private final TransferReceiverRegistrationRepository receiverRepository;
    private final NodeRepository nodeRepository;
    private final FolderService folderService;
    private final DocumentUploadService documentUploadService;

    @Transactional
    public VerifyFolderResponse verifyFolder(UUID folderId, String authUsername, String authSecret) {
        AuthorizedFolder authorized = resolveAuthorizedFolder(folderId, authUsername, authSecret);
        recordAccessSuccess(authorized.receiver(), "Verified receiver folder access: " + authorized.folder().getName());
        Folder folder = authorized.folder();
        return new VerifyFolderResponse(folder.getId(), folder.getName());
    }

    @Transactional
    public CreateFolderResponse createFolder(CreateFolderRequest request, String authUsername, String authSecret) {
        if (request == null) {
            throw new IllegalArgumentException("Transfer receiver folder request is required");
        }
        String requestedName = normalizeRequired(request.name(), "Folder name is required");
        AuthorizedFolder authorized = resolveAuthorizedFolder(request.parentFolderId(), authUsername, authSecret);
        Folder parent = authorized.folder();
        String resolvedName = resolveReplicaName(parent.getId(), requestedName);

        SecurityContext previous = pushTransferAuthentication();
        try {
            Folder created = folderService.createFolder(new FolderService.CreateFolderRequest(
                resolvedName,
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
            recordAccessSuccess(authorized.receiver(), "Created receiver folder: " + created.getName());
            return new CreateFolderResponse(created.getId(), created.getName());
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
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A non-empty file is required");
        }

        AuthorizedFolder authorized = resolveAuthorizedFolder(parentFolderId, authUsername, authSecret);
        Folder parent = authorized.folder();
        String filename = normalizeRequired(file.getOriginalFilename(), "File name is required");
        String resolvedName = resolveReplicaName(parent.getId(), filename);
        MultipartFile effectiveFile = Objects.equals(resolvedName, filename)
            ? file
            : renamedFile(file, resolvedName);

        Map<String, Object> properties = null;
        String normalizedDescription = normalizeOptional(description);
        if (normalizedDescription != null) {
            properties = Map.of("description", normalizedDescription);
        }

        SecurityContext previous = pushTransferAuthentication();
        try {
            PipelineResult result = documentUploadService.uploadDocument(effectiveFile, parent.getId(), properties);
            if (!result.isSuccess() || result.getDocumentId() == null) {
                throw new IllegalStateException("Transfer receiver document upload failed");
            }
            recordAccessSuccess(authorized.receiver(), "Uploaded receiver document: " + effectiveFile.getOriginalFilename());
            return new UploadDocumentResponse(result.getDocumentId(), effectiveFile.getOriginalFilename());
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

    private String resolveReplicaName(UUID parentFolderId, String requestedName) {
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

    public record VerifyFolderResponse(UUID folderId, String folderName) {}

    public record CreateFolderRequest(UUID parentFolderId, String name, String description) {}

    public record CreateFolderResponse(UUID folderId, String folderName) {}

    public record UploadDocumentResponse(UUID documentId, String documentName) {}

    private record AuthorizedFolder(TransferReceiverRegistration receiver, Folder folder) {}
}
