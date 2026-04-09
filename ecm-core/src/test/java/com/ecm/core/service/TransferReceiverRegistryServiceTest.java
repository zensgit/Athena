package com.ecm.core.service;

import com.ecm.core.entity.Folder;
import com.ecm.core.entity.TransferReceiverRegistration;
import com.ecm.core.entity.TransferTarget;
import com.ecm.core.repository.TransferReceiverRegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferReceiverRegistryServiceTest {

    @Mock
    private TransferReceiverRegistrationRepository receiverRepository;

    @Mock
    private FolderService folderService;

    @Mock
    private SecurityService securityService;

    private TransferReceiverRegistryService service;
    private Map<UUID, TransferReceiverRegistration> storedReceivers;

    @BeforeEach
    void setUp() {
        service = new TransferReceiverRegistryService(receiverRepository, folderService, securityService);
        storedReceivers = new LinkedHashMap<>();

        lenient().when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        lenient().when(receiverRepository.save(any(TransferReceiverRegistration.class))).thenAnswer(invocation -> {
            TransferReceiverRegistration receiver = invocation.getArgument(0);
            if (receiver.getId() == null) {
                receiver.setId(UUID.randomUUID());
                receiver.setCreatedAt(LocalDateTime.now());
            }
            receiver.setUpdatedAt(LocalDateTime.now());
            storedReceivers.put(receiver.getId(), receiver);
            return receiver;
        });
        lenient().when(receiverRepository.findById(any(UUID.class))).thenAnswer(invocation ->
            Optional.ofNullable(storedReceivers.get(invocation.getArgument(0)))
        );
        lenient().when(receiverRepository.findAll()).thenAnswer(invocation -> List.copyOf(storedReceivers.values()));
        lenient().when(receiverRepository.existsByNameIgnoreCase(anyString())).thenAnswer(invocation -> {
            String name = invocation.getArgument(0, String.class);
            return storedReceivers.values().stream().anyMatch(receiver -> receiver.getName() != null && receiver.getName().equalsIgnoreCase(name));
        });
    }

    @Test
    @DisplayName("createReceiver persists a bearer-auth receiver registry entry")
    void createReceiverPersistsReceiverRegistryEntry() {
        UUID rootFolderId = UUID.randomUUID();
        when(folderService.getFolder(rootFolderId)).thenReturn(folder(rootFolderId, "Inbound Root"));

        TransferReceiverRegistryService.TransferReceiverDto receiver = service.createReceiver(
            new TransferReceiverRegistryService.TransferReceiverMutationRequest(
                "receiver-east",
                "East receiver",
                rootFolderId,
                TransferTarget.AuthType.BEARER,
                null,
                "shared-secret",
                true
            )
        );

        assertEquals("receiver-east", receiver.name());
        assertEquals(rootFolderId, receiver.rootFolderId());
        assertEquals("Inbound Root", receiver.rootFolderName());
        assertEquals(TransferTarget.AuthType.BEARER, receiver.authType());
        assertTrue(receiver.authSecretConfigured());
        assertEquals(TransferTarget.VerificationStatus.NEVER_VERIFIED, receiver.verificationStatus());
    }

    @Test
    @DisplayName("createReceiver rejects duplicate registry names")
    void createReceiverRejectsDuplicateRegistryNames() {
        UUID rootFolderId = UUID.randomUUID();
        TransferReceiverRegistration existing = new TransferReceiverRegistration();
        existing.setId(UUID.randomUUID());
        existing.setName("receiver-east");
        storedReceivers.put(existing.getId(), existing);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.createReceiver(
            new TransferReceiverRegistryService.TransferReceiverMutationRequest(
                "receiver-east",
                null,
                rootFolderId,
                TransferTarget.AuthType.NONE,
                null,
                null,
                true
            )
        ));

        assertTrue(ex.getMessage().contains("Transfer receiver already exists"));
    }

    @Test
    @DisplayName("verifyReceiver stores verified root-folder diagnostics")
    void verifyReceiverStoresVerifiedRootFolderDiagnostics() {
        UUID receiverId = UUID.randomUUID();
        UUID rootFolderId = UUID.randomUUID();
        TransferReceiverRegistration receiver = new TransferReceiverRegistration();
        receiver.setId(receiverId);
        receiver.setName("receiver-east");
        receiver.setRootFolderId(rootFolderId);
        receiver.setAuthType(TransferTarget.AuthType.BASIC);
        receiver.setAuthUsername("replicator");
        receiver.setAuthSecret("top-secret");
        storedReceivers.put(receiverId, receiver);
        when(folderService.getFolder(rootFolderId)).thenReturn(folder(rootFolderId, "Inbound Root"));

        TransferReceiverRegistryService.TransferReceiverDto verified = service.verifyReceiver(receiverId);

        assertEquals(TransferTarget.VerificationStatus.VERIFIED, verified.verificationStatus());
        assertEquals("Verified receiver root folder: Inbound Root", verified.verificationMessage());
        assertNotNull(verified.lastVerifiedAt());
    }

    @Test
    @DisplayName("updateReceiver resets verification when auth changes")
    void updateReceiverResetsVerificationWhenAuthChanges() {
        UUID receiverId = UUID.randomUUID();
        UUID rootFolderId = UUID.randomUUID();
        TransferReceiverRegistration receiver = new TransferReceiverRegistration();
        receiver.setId(receiverId);
        receiver.setName("receiver-east");
        receiver.setRootFolderId(rootFolderId);
        receiver.setAuthType(TransferTarget.AuthType.BEARER);
        receiver.setAuthSecret("old-secret");
        receiver.setVerificationStatus(TransferTarget.VerificationStatus.VERIFIED);
        receiver.setVerificationMessage("Verified");
        receiver.setLastVerifiedAt(LocalDateTime.now());
        storedReceivers.put(receiverId, receiver);
        when(folderService.getFolder(rootFolderId)).thenReturn(folder(rootFolderId, "Inbound Root"));

        TransferReceiverRegistryService.TransferReceiverDto updated = service.updateReceiver(
            receiverId,
            new TransferReceiverRegistryService.TransferReceiverMutationRequest(
                "receiver-east",
                "Updated",
                rootFolderId,
                TransferTarget.AuthType.BEARER,
                null,
                "new-secret",
                true
            )
        );

        assertEquals(TransferTarget.VerificationStatus.NEVER_VERIFIED, updated.verificationStatus());
        assertEquals("Updated", updated.description());
    }

    private Folder folder(UUID id, String name) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName(name);
        folder.setArchiveStatus(com.ecm.core.entity.Node.ArchiveStatus.LIVE);
        folder.setDeleted(false);
        return folder;
    }
}
