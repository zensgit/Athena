package com.ecm.core.service;

import com.ecm.core.entity.LegalHold;
import com.ecm.core.entity.LegalHoldItem;
import com.ecm.core.entity.Node;
import com.ecm.core.exception.IllegalOperationException;
import com.ecm.core.repository.LegalHoldItemRepository;
import com.ecm.core.repository.LegalHoldRepository;
import com.ecm.core.repository.NodeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegalHoldServiceTest {

    @Mock
    private LegalHoldRepository legalHoldRepository;

    @Mock
    private LegalHoldItemRepository legalHoldItemRepository;

    @Mock
    private NodeRepository nodeRepository;

    @Mock
    private SecurityService securityService;

    @Mock
    private TenantWorkspaceScopeService tenantWorkspaceScopeService;

    @InjectMocks
    private LegalHoldService legalHoldService;

    @Test
    @DisplayName("assertOperationAllowed blocks descendants of held folder")
    void assertOperationAllowedBlocksDescendantsOfHeldFolder() {
        UUID holdId = UUID.randomUUID();
        Node heldFolder = node(UUID.randomUUID(), "Finance", "/Sites/Finance", Node.NodeType.FOLDER);
        Node targetDocument = node(UUID.randomUUID(), "invoice.pdf", "/Sites/Finance/2026/invoice.pdf", Node.NodeType.DOCUMENT);

        LegalHold hold = new LegalHold();
        hold.setId(holdId);
        hold.setName("Quarter Close");
        hold.setStatus(LegalHold.HoldStatus.ACTIVE);

        LegalHoldItem item = new LegalHoldItem();
        item.setHold(hold);
        item.setNode(heldFolder);
        item.setNodeType(Node.NodeType.FOLDER);
        item.setNodePath(heldFolder.getPath());

        when(legalHoldItemRepository.findActiveItems(LegalHold.HoldStatus.ACTIVE)).thenReturn(List.of(item));

        IllegalOperationException ex = assertThrows(
            IllegalOperationException.class,
            () -> legalHoldService.assertOperationAllowed(targetDocument, "delete")
        );

        assertEquals(
            "Cannot delete because node 'invoice.pdf' is under active legal hold(s): Quarter Close",
            ex.getMessage()
        );
    }

    @Test
    @DisplayName("releaseHold marks hold released and returns updated DTO")
    void releaseHoldMarksHoldReleased() {
        UUID holdId = UUID.randomUUID();
        LegalHold hold = new LegalHold();
        hold.setId(holdId);
        hold.setName("Litigation 2026");
        hold.setStatus(LegalHold.HoldStatus.ACTIVE);
        hold.setCreatedBy("admin");
        hold.setCreatedDate(LocalDateTime.of(2026, 4, 14, 10, 0));

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(legalHoldRepository.findById(holdId)).thenReturn(Optional.of(hold));
        when(legalHoldRepository.save(hold)).thenReturn(hold);
        when(legalHoldItemRepository.findByHoldIdWithNode(holdId)).thenReturn(List.of());

        LegalHoldService.LegalHoldDto dto = legalHoldService.releaseHold(
            holdId,
            new LegalHoldService.ReleaseLegalHoldRequest("Matter closed")
        );

        assertEquals(LegalHold.HoldStatus.RELEASED, hold.getStatus());
        assertEquals("admin", hold.getReleasedBy());
        assertEquals("Matter closed", hold.getReleaseComment());
        assertEquals(LegalHold.HoldStatus.RELEASED, dto.status());
        assertEquals("Matter closed", dto.releaseComment());
    }

    private Node node(UUID id, String name, String path, Node.NodeType nodeType) {
        Node node = nodeType == Node.NodeType.FOLDER ? new com.ecm.core.entity.Folder() : new com.ecm.core.entity.Document();
        node.setId(id);
        node.setName(name);
        node.setPath(path);
        node.setArchiveStatus(Node.ArchiveStatus.LIVE);
        node.setStatus(Node.NodeStatus.ACTIVE);
        return node;
    }
}
