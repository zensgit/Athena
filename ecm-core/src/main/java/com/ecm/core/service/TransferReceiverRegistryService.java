package com.ecm.core.service;

import com.ecm.core.entity.Folder;
import com.ecm.core.entity.TransferReceiverRegistration;
import com.ecm.core.entity.TransferTarget;
import com.ecm.core.repository.TransferReceiverRegistrationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferReceiverRegistryService {

    private final TransferReceiverRegistrationRepository receiverRepository;
    private final FolderService folderService;
    private final SecurityService securityService;

    public List<TransferReceiverDto> listReceivers() {
        requireAdmin();
        return receiverRepository.findAll().stream()
            .map(this::toDto)
            .toList();
    }

    public TransferReceiverDto getReceiver(UUID receiverId) {
        requireAdmin();
        return toDto(requireReceiver(receiverId));
    }

    public TransferReceiverDto createReceiver(TransferReceiverMutationRequest request) {
        requireAdmin();
        String name = normalizeRequired(request.name(), "Receiver name is required");
        if (receiverRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Transfer receiver already exists: " + name);
        }
        TransferReceiverRegistration receiver = new TransferReceiverRegistration();
        receiver.setName(name);
        receiver.setDescription(normalizeOptional(request.description()));
        applyConfiguration(receiver, request, true);
        return toDto(receiverRepository.save(receiver));
    }

    public TransferReceiverDto updateReceiver(UUID receiverId, TransferReceiverMutationRequest request) {
        requireAdmin();
        TransferReceiverRegistration receiver = requireReceiver(receiverId);
        String name = normalizeRequired(request.name(), "Receiver name is required");
        boolean renamed = !receiver.getName().equalsIgnoreCase(name);
        if (renamed && receiverRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Transfer receiver already exists: " + name);
        }
        receiver.setName(name);
        receiver.setDescription(normalizeOptional(request.description()));
        applyConfiguration(receiver, request, false);
        return toDto(receiverRepository.save(receiver));
    }

    public TransferReceiverDto verifyReceiver(UUID receiverId) {
        requireAdmin();
        TransferReceiverRegistration receiver = requireReceiver(receiverId);
        try {
            Folder folder = folderService.getFolder(receiver.getRootFolderId());
            receiver.setVerificationStatus(TransferTarget.VerificationStatus.VERIFIED);
            receiver.setVerificationMessage("Verified receiver root folder: " + folder.getName());
        } catch (RuntimeException ex) {
            receiver.setVerificationStatus(TransferTarget.VerificationStatus.FAILED);
            receiver.setVerificationMessage(ex.getMessage());
            receiver.setLastVerifiedAt(LocalDateTime.now());
            receiverRepository.save(receiver);
            throw ex;
        }
        receiver.setLastVerifiedAt(LocalDateTime.now());
        return toDto(receiverRepository.save(receiver));
    }

    public void deleteReceiver(UUID receiverId) {
        requireAdmin();
        receiverRepository.delete(requireReceiver(receiverId));
    }

    private TransferReceiverRegistration requireReceiver(UUID receiverId) {
        return receiverRepository.findById(receiverId)
            .orElseThrow(() -> new NoSuchElementException("Transfer receiver not found: " + receiverId));
    }

    private void applyConfiguration(TransferReceiverRegistration receiver, TransferReceiverMutationRequest request, boolean creating) {
        UUID rootFolderId = requiredId(request.rootFolderId(), "rootFolderId");
        TransferTarget.AuthType authType = request.authType() != null
            ? request.authType()
            : TransferTarget.AuthType.NONE;

        UUID previousRootFolderId = receiver.getRootFolderId();
        TransferTarget.AuthType previousAuthType = receiver.getAuthType();
        String previousAuthUsername = receiver.getAuthUsername();
        String previousAuthSecret = receiver.getAuthSecret();

        Folder rootFolder = folderService.getFolder(rootFolderId);
        receiver.setRootFolderId(rootFolder.getId());
        receiver.setEnabled(request.enabled() == null || request.enabled());
        receiver.setAuthType(authType);

        String username = request.authUsername() != null
            ? normalizeOptional(request.authUsername())
            : receiver.getAuthUsername();
        String secret = request.authSecret() != null
            ? normalizeOptional(request.authSecret())
            : receiver.getAuthSecret();

        if (authType == TransferTarget.AuthType.NONE) {
            username = null;
            secret = null;
        } else if (authType == TransferTarget.AuthType.BASIC) {
            if (username == null) {
                throw new IllegalArgumentException("authUsername is required for BASIC auth");
            }
            if (secret == null) {
                throw new IllegalArgumentException("authSecret is required for BASIC auth");
            }
        } else if (secret == null) {
            throw new IllegalArgumentException("authSecret is required for BEARER auth");
        }

        receiver.setAuthUsername(username);
        receiver.setAuthSecret(secret);

        boolean verificationInputsChanged = creating
            || !Objects.equals(previousRootFolderId, receiver.getRootFolderId())
            || !Objects.equals(previousAuthType, receiver.getAuthType())
            || !Objects.equals(previousAuthUsername, receiver.getAuthUsername())
            || !Objects.equals(previousAuthSecret, receiver.getAuthSecret());
        if (verificationInputsChanged) {
            receiver.setVerificationStatus(TransferTarget.VerificationStatus.NEVER_VERIFIED);
            receiver.setVerificationMessage(null);
            receiver.setLastVerifiedAt(null);
        }
    }

    private TransferReceiverDto toDto(TransferReceiverRegistration receiver) {
        String rootFolderName = null;
        try {
            rootFolderName = folderService.getFolder(receiver.getRootFolderId()).getName();
        } catch (RuntimeException ignored) {
        }
        return new TransferReceiverDto(
            receiver.getId(),
            receiver.getName(),
            receiver.getDescription(),
            receiver.getRootFolderId(),
            rootFolderName,
            receiver.getAuthType(),
            receiver.getAuthUsername(),
            receiver.getAuthSecret() != null && !receiver.getAuthSecret().isBlank(),
            receiver.isEnabled(),
            receiver.getVerificationStatus(),
            receiver.getVerificationMessage(),
            receiver.getLastVerifiedAt(),
            receiver.getLastAccessStatus(),
            receiver.getLastAccessMessage(),
            receiver.getLastAccessedAt(),
            receiver.getCreatedAt(),
            receiver.getUpdatedAt()
        );
    }

    private void requireAdmin() {
        if (!securityService.hasRole("ROLE_ADMIN")) {
            throw new SecurityException("Admin access required for transfer receiver registry operations");
        }
    }

    private UUID requiredId(UUID value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
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

    public record TransferReceiverMutationRequest(
        String name,
        String description,
        UUID rootFolderId,
        TransferTarget.AuthType authType,
        String authUsername,
        String authSecret,
        Boolean enabled
    ) {}

    public record TransferReceiverDto(
        UUID id,
        String name,
        String description,
        UUID rootFolderId,
        String rootFolderName,
        TransferTarget.AuthType authType,
        String authUsername,
        boolean authSecretConfigured,
        boolean enabled,
        TransferTarget.VerificationStatus verificationStatus,
        String verificationMessage,
        LocalDateTime lastVerifiedAt,
        TransferReceiverRegistration.AccessStatus lastAccessStatus,
        String lastAccessMessage,
        LocalDateTime lastAccessedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}
}
