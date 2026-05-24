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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegalHoldServiceTest {

    @Mock private LegalHoldRepository legalHoldRepository;
    @Mock private LegalHoldItemRepository legalHoldItemRepository;
    @Mock private NodeRepository nodeRepository;
    @Mock private SecurityService securityService;
    @Mock private TenantWorkspaceScopeService tenantWorkspaceScopeService;
    @Mock private PlatformTransactionManager transactionManager;

    /**
     * Build the service with the bulk-slice constructor signature. Tests that
     * exercise {@code createHold} need {@link TransactionTemplate#execute(...)}
     * to actually invoke the callback — stub the transactionManager leniently
     * so it returns a real {@link SimpleTransactionStatus} (template will then
     * just run the callback). Tests that never hit a template-driven path are
     * unaffected by the lenient stub.
     */
    private LegalHoldService newService() {
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
            .thenReturn(new SimpleTransactionStatus());
        return new LegalHoldService(
            legalHoldRepository,
            legalHoldItemRepository,
            nodeRepository,
            securityService,
            tenantWorkspaceScopeService,
            transactionManager
        );
    }

    // ------------------------------------------------------------------
    // Existing tests, updated for the new constructor + DTO shape
    // ------------------------------------------------------------------

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
            () -> newService().assertOperationAllowed(targetDocument, "delete")
        );

        assertEquals(
            "Cannot delete because node 'invoice.pdf' is under active legal hold(s): Quarter Close",
            ex.getMessage()
        );
    }

    @Test
    @DisplayName("releaseHold marks hold released, stores reason+comment, returns updated DTO")
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

        LegalHoldService.LegalHoldDto dto = newService().releaseHold(
            holdId,
            new LegalHoldService.ReleaseLegalHoldRequest(
                LegalHold.HoldReleaseReason.LITIGATION_ENDED,
                "Matter closed"
            )
        );

        assertEquals(LegalHold.HoldStatus.RELEASED, hold.getStatus());
        assertEquals("admin", hold.getReleasedBy());
        assertEquals("Matter closed", hold.getReleaseComment());
        assertEquals(LegalHold.HoldReleaseReason.LITIGATION_ENDED, hold.getReleaseReason());
        assertEquals(LegalHold.HoldStatus.RELEASED, dto.status());
        assertEquals("Matter closed", dto.releaseComment());
        assertEquals(LegalHold.HoldReleaseReason.LITIGATION_ENDED, dto.releaseReason());
    }

    // ------------------------------------------------------------------
    // Release-reason guard
    // ------------------------------------------------------------------

    @Test
    @DisplayName("releaseHold rejects null releaseReason with IllegalArgumentException (HTTP 400 path)")
    void releaseHoldRequiresReleaseReason() {
        UUID holdId = UUID.randomUUID();
        LegalHold hold = new LegalHold();
        hold.setId(holdId);
        hold.setName("Litigation 2026");
        hold.setStatus(LegalHold.HoldStatus.ACTIVE);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(legalHoldRepository.findById(holdId)).thenReturn(Optional.of(hold));

        LegalHoldService service = newService();
        assertThrows(IllegalArgumentException.class, () -> service.releaseHold(
            holdId,
            new LegalHoldService.ReleaseLegalHoldRequest(null, "missing reason")
        ));
        assertThrows(IllegalArgumentException.class, () -> service.releaseHold(holdId, null));
        // Local state never changed.
        assertEquals(LegalHold.HoldStatus.ACTIVE, hold.getStatus());
        verify(legalHoldRepository, never()).save(any(LegalHold.class));
    }

    @Test
    @DisplayName("getHold of a legacy released row (releaseReason = null) round-trips releaseReason=null without throwing")
    void getHoldLegacyReleasedRowTolerantToNullReason() {
        UUID holdId = UUID.randomUUID();
        LegalHold hold = new LegalHold();
        hold.setId(holdId);
        hold.setName("Old Hold");
        hold.setStatus(LegalHold.HoldStatus.RELEASED);
        hold.setReleasedBy("someone");
        hold.setReleasedAt(LocalDateTime.of(2026, 4, 14, 10, 0));
        hold.setReleaseComment(null);
        hold.setReleaseReason(null); // legacy

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(legalHoldRepository.findById(holdId)).thenReturn(Optional.of(hold));
        when(legalHoldItemRepository.findByHoldIdWithNode(holdId)).thenReturn(List.of());
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);

        LegalHoldService.LegalHoldDto dto = newService().getHold(holdId);

        assertEquals(LegalHold.HoldStatus.RELEASED, dto.status());
        assertNull(dto.releaseReason());
        assertNull(dto.releaseComment());
    }

    // ------------------------------------------------------------------
    // Bulk-apply tests (orchestration pattern)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("createHold without nodeIds: bulkApplyResults is null, behavior identical to v1 single-row create")
    void createHoldWithoutNodeIdsLeavesBulkApplyResultsNull() {
        UUID savedId = UUID.randomUUID();
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(legalHoldRepository.save(any(LegalHold.class))).thenAnswer(invocation -> {
            LegalHold hold = invocation.getArgument(0);
            hold.setId(savedId);
            hold.setCreatedDate(LocalDateTime.now());
            return hold;
        });
        when(legalHoldRepository.findById(savedId)).thenAnswer(invocation -> {
            LegalHold hold = new LegalHold();
            hold.setId(savedId);
            hold.setName("Discovery Q3");
            hold.setStatus(LegalHold.HoldStatus.ACTIVE);
            return Optional.of(hold);
        });
        when(legalHoldItemRepository.findByHoldIdWithNode(savedId)).thenReturn(List.of());
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);

        LegalHoldService.LegalHoldDto dto = newService().createHold(
            new LegalHoldService.CreateLegalHoldRequest("Discovery Q3", null, null)
        );

        assertNull(dto.bulkApplyResults());
        assertEquals(LegalHold.HoldStatus.ACTIVE, dto.status());
    }

    @Test
    @DisplayName("createHold orchestrator: parent hold save commits BEFORE any per-row item save (InOrder lock)")
    void createHoldOrchestratorParentBeforeRowItem() {
        UUID savedHoldId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        Node node = node(nodeId, "doc.pdf", "/Sites/Finance/doc.pdf", Node.NodeType.DOCUMENT);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(legalHoldRepository.save(any(LegalHold.class))).thenAnswer(invocation -> {
            LegalHold hold = invocation.getArgument(0);
            hold.setId(savedHoldId);
            return hold;
        });
        when(legalHoldRepository.findById(savedHoldId)).thenAnswer(invocation -> {
            LegalHold hold = new LegalHold();
            hold.setId(savedHoldId);
            hold.setName("Test");
            hold.setStatus(LegalHold.HoldStatus.ACTIVE);
            return Optional.of(hold);
        });
        when(legalHoldItemRepository.existsByHoldIdAndNodeId(savedHoldId, nodeId)).thenReturn(false);
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(nodeId, Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(node));
        when(tenantWorkspaceScopeService.isPathVisible(node.getPath())).thenReturn(true);
        when(legalHoldItemRepository.save(any(LegalHoldItem.class))).thenAnswer(invocation -> {
            LegalHoldItem item = invocation.getArgument(0);
            item.setAddedAt(LocalDateTime.now());
            return item;
        });
        when(legalHoldItemRepository.findByHoldIdWithNode(savedHoldId)).thenReturn(List.of());
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);

        newService().createHold(
            new LegalHoldService.CreateLegalHoldRequest("Test", null, List.of(nodeId))
        );

        // Order lock: legalHoldRepository.save(LegalHold) MUST be called before
        // legalHoldItemRepository.save(LegalHoldItem). This is the test that
        // catches any future regression where someone adds @Transactional to
        // createHold and accidentally puts parent + items in the same
        // transaction (which would FK-violate against an uncommitted parent
        // under REQUIRES_NEW). See gate review 2026-05-24.
        InOrder inOrder = inOrder(legalHoldRepository, legalHoldItemRepository);
        inOrder.verify(legalHoldRepository).save(any(LegalHold.class));
        inOrder.verify(legalHoldItemRepository).save(any(LegalHoldItem.class));
    }

    @Test
    @DisplayName("createHold with nodeIds: all 3 nodes ADDED, BulkApplyResults populated, hold ACTIVE")
    void createHoldWithNodeIdsAllAdded() {
        UUID savedHoldId = UUID.randomUUID();
        UUID n1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID n2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID n3 = UUID.fromString("33333333-3333-3333-3333-333333333333");

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(legalHoldRepository.save(any(LegalHold.class))).thenAnswer(invocation -> {
            LegalHold hold = invocation.getArgument(0);
            hold.setId(savedHoldId);
            return hold;
        });
        when(legalHoldRepository.findById(savedHoldId)).thenAnswer(invocation -> {
            LegalHold hold = new LegalHold();
            hold.setId(savedHoldId);
            hold.setName("All-Added");
            hold.setStatus(LegalHold.HoldStatus.ACTIVE);
            return Optional.of(hold);
        });
        when(legalHoldItemRepository.existsByHoldIdAndNodeId(any(), any())).thenReturn(false);
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(any(), any()))
            .thenAnswer(invocation -> {
                UUID requested = invocation.getArgument(0);
                return Optional.of(node(requested, "doc-" + requested, "/Sites/X/doc-" + requested, Node.NodeType.DOCUMENT));
            });
        when(tenantWorkspaceScopeService.isPathVisible(any())).thenReturn(true);
        when(legalHoldItemRepository.save(any(LegalHoldItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(legalHoldItemRepository.findByHoldIdWithNode(savedHoldId)).thenReturn(List.of());
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);

        LegalHoldService.LegalHoldDto dto = newService().createHold(
            new LegalHoldService.CreateLegalHoldRequest("All-Added", null, List.of(n1, n2, n3))
        );

        assertNotNull(dto.bulkApplyResults());
        assertThat(dto.bulkApplyResults().rows()).hasSize(3);
        assertThat(dto.bulkApplyResults().rows()).allSatisfy(r -> {
            assertEquals(LegalHoldService.BulkApplyResult.Status.ADDED, r.status());
            assertNotNull(r.item());
            assertNull(r.errorCategory());
            assertNull(r.errorMessage());
        });
    }

    @Test
    @DisplayName("createHold with one missing node: hold + 2 ADDED rows committed; missing node FAILED with NODE_NOT_FOUND")
    void createHoldWithMissingNodePartialFailureDoesNotRollback() {
        UUID savedHoldId = UUID.randomUUID();
        UUID n1 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID nMissing = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID n3 = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(legalHoldRepository.save(any(LegalHold.class))).thenAnswer(invocation -> {
            LegalHold hold = invocation.getArgument(0);
            hold.setId(savedHoldId);
            return hold;
        });
        when(legalHoldRepository.findById(savedHoldId)).thenAnswer(invocation -> {
            LegalHold hold = new LegalHold();
            hold.setId(savedHoldId);
            hold.setName("Partial");
            hold.setStatus(LegalHold.HoldStatus.ACTIVE);
            return Optional.of(hold);
        });
        when(legalHoldItemRepository.existsByHoldIdAndNodeId(any(), any())).thenReturn(false);
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(n1, Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(node(n1, "a.pdf", "/X/a.pdf", Node.NodeType.DOCUMENT)));
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(nMissing, Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.empty());
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(n3, Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(node(n3, "c.pdf", "/X/c.pdf", Node.NodeType.DOCUMENT)));
        when(tenantWorkspaceScopeService.isPathVisible(any())).thenReturn(true);
        when(legalHoldItemRepository.save(any(LegalHoldItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(legalHoldItemRepository.findByHoldIdWithNode(savedHoldId)).thenReturn(List.of());
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);

        LegalHoldService.LegalHoldDto dto = newService().createHold(
            new LegalHoldService.CreateLegalHoldRequest("Partial", null, List.of(n1, nMissing, n3))
        );

        assertNotNull(dto.bulkApplyResults());
        assertThat(dto.bulkApplyResults().rows()).hasSize(3);
        assertEquals(LegalHoldService.BulkApplyResult.Status.ADDED, dto.bulkApplyResults().rows().get(0).status());
        assertEquals(LegalHoldService.BulkApplyResult.Status.FAILED, dto.bulkApplyResults().rows().get(1).status());
        assertEquals(LegalHoldService.BulkApplyErrorCategory.NODE_NOT_FOUND, dto.bulkApplyResults().rows().get(1).errorCategory());
        assertEquals(LegalHoldService.BulkApplyResult.Status.ADDED, dto.bulkApplyResults().rows().get(2).status());

        // Parent hold WAS saved exactly once (Step 1 commit) even though row 2 failed.
        verify(legalHoldRepository).save(any(LegalHold.class));
        // Two item saves committed (rows 1 and 3), one item save attempted+failed (row 2 never reached save).
        verify(legalHoldItemRepository, org.mockito.Mockito.times(2)).save(any(LegalHoldItem.class));
    }

    @Test
    @DisplayName("createHold with already-applied node: row reports SKIPPED_DUPLICATE; other rows succeed")
    void createHoldWithDuplicateSkipped() {
        UUID savedHoldId = UUID.randomUUID();
        UUID nDup = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        UUID nNew = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(legalHoldRepository.save(any(LegalHold.class))).thenAnswer(invocation -> {
            LegalHold hold = invocation.getArgument(0);
            hold.setId(savedHoldId);
            return hold;
        });
        when(legalHoldRepository.findById(savedHoldId)).thenAnswer(invocation -> {
            LegalHold hold = new LegalHold();
            hold.setId(savedHoldId);
            hold.setName("Dup");
            hold.setStatus(LegalHold.HoldStatus.ACTIVE);
            return Optional.of(hold);
        });
        when(legalHoldItemRepository.existsByHoldIdAndNodeId(savedHoldId, nDup)).thenReturn(true);
        when(legalHoldItemRepository.existsByHoldIdAndNodeId(savedHoldId, nNew)).thenReturn(false);
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(nNew, Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(node(nNew, "new.pdf", "/X/new.pdf", Node.NodeType.DOCUMENT)));
        when(tenantWorkspaceScopeService.isPathVisible(any())).thenReturn(true);
        when(legalHoldItemRepository.save(any(LegalHoldItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(legalHoldItemRepository.findByHoldIdWithNode(savedHoldId)).thenReturn(List.of());
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);

        LegalHoldService.LegalHoldDto dto = newService().createHold(
            new LegalHoldService.CreateLegalHoldRequest("Dup", null, List.of(nDup, nNew))
        );

        assertNotNull(dto.bulkApplyResults());
        assertThat(dto.bulkApplyResults().rows()).hasSize(2);
        assertEquals(LegalHoldService.BulkApplyResult.Status.SKIPPED_DUPLICATE, dto.bulkApplyResults().rows().get(0).status());
        assertEquals(LegalHoldService.BulkApplyResult.Status.ADDED, dto.bulkApplyResults().rows().get(1).status());
    }

    @Test
    @DisplayName("createHold with tenant-invisible node: row reports NODE_NOT_VISIBLE")
    void createHoldWithTenantInvisibleNodeReportsCategory() {
        UUID savedHoldId = UUID.randomUUID();
        UUID nInvisible = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        Node invisibleNode = node(nInvisible, "secret.pdf", "/OtherTenant/secret.pdf", Node.NodeType.DOCUMENT);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(legalHoldRepository.save(any(LegalHold.class))).thenAnswer(invocation -> {
            LegalHold hold = invocation.getArgument(0);
            hold.setId(savedHoldId);
            return hold;
        });
        when(legalHoldRepository.findById(savedHoldId)).thenAnswer(invocation -> {
            LegalHold hold = new LegalHold();
            hold.setId(savedHoldId);
            hold.setName("Cross-Tenant");
            hold.setStatus(LegalHold.HoldStatus.ACTIVE);
            return Optional.of(hold);
        });
        when(legalHoldItemRepository.existsByHoldIdAndNodeId(any(), any())).thenReturn(false);
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(nInvisible, Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(invisibleNode));
        when(tenantWorkspaceScopeService.isPathVisible(invisibleNode.getPath())).thenReturn(false);
        when(legalHoldItemRepository.findByHoldIdWithNode(savedHoldId)).thenReturn(List.of());
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);

        LegalHoldService.LegalHoldDto dto = newService().createHold(
            new LegalHoldService.CreateLegalHoldRequest("Cross-Tenant", null, List.of(nInvisible))
        );

        assertNotNull(dto.bulkApplyResults());
        assertThat(dto.bulkApplyResults().rows()).hasSize(1);
        assertEquals(LegalHoldService.BulkApplyResult.Status.FAILED, dto.bulkApplyResults().rows().get(0).status());
        assertEquals(
            LegalHoldService.BulkApplyErrorCategory.NODE_NOT_VISIBLE,
            dto.bulkApplyResults().rows().get(0).errorCategory()
        );
    }

    @Test
    @DisplayName("createHold INTERNAL_ERROR: errorMessage carries class name, NOT raw exception message (Phase 2 logging audit)")
    void createHoldInternalErrorSanitisedMessage() {
        UUID savedHoldId = UUID.randomUUID();
        UUID nodeId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        String probe = "USER_PII_FROM_EXCEPTION_LEAK_PROBE";

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(legalHoldRepository.save(any(LegalHold.class))).thenAnswer(invocation -> {
            LegalHold hold = invocation.getArgument(0);
            hold.setId(savedHoldId);
            return hold;
        });
        when(legalHoldRepository.findById(savedHoldId)).thenAnswer(invocation -> {
            LegalHold hold = new LegalHold();
            hold.setId(savedHoldId);
            hold.setName("Crash");
            hold.setStatus(LegalHold.HoldStatus.ACTIVE);
            return Optional.of(hold);
        });
        when(legalHoldItemRepository.existsByHoldIdAndNodeId(any(), any())).thenReturn(false);
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(nodeId, Node.ArchiveStatus.LIVE))
            .thenThrow(new RuntimeException(probe));
        when(legalHoldItemRepository.findByHoldIdWithNode(savedHoldId)).thenReturn(List.of());
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);

        LegalHoldService.LegalHoldDto dto = newService().createHold(
            new LegalHoldService.CreateLegalHoldRequest("Crash", null, List.of(nodeId))
        );

        assertNotNull(dto.bulkApplyResults());
        assertThat(dto.bulkApplyResults().rows()).hasSize(1);
        LegalHoldService.BulkApplyResult result = dto.bulkApplyResults().rows().get(0);
        assertEquals(LegalHoldService.BulkApplyResult.Status.FAILED, result.status());
        assertEquals(LegalHoldService.BulkApplyErrorCategory.INTERNAL_ERROR, result.errorCategory());
        // Class name preserved for operator triage; raw probe ABSENT.
        assertThat(result.errorMessage()).contains("RuntimeException");
        assertThat(result.errorMessage()).doesNotContain(probe);
    }

    @Test
    @DisplayName("createHold with blank name throws IllegalArgumentException at the request boundary, no DB writes")
    void createHoldBlankNameRejected() {
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        LegalHoldService service = newService();

        assertThrows(IllegalArgumentException.class, () -> service.createHold(
            new LegalHoldService.CreateLegalHoldRequest("   ", null, null)
        ));
        assertThrows(IllegalArgumentException.class, () -> service.createHold(
            new LegalHoldService.CreateLegalHoldRequest(null, "desc", null)
        ));
        verify(legalHoldRepository, never()).save(any(LegalHold.class));
        verify(legalHoldItemRepository, never()).save(any(LegalHoldItem.class));
    }

    @Test
    @DisplayName("createHold without admin role: SecurityException, no DB writes, no transactionManager use")
    void createHoldSecurityGate() {
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);
        LegalHoldService service = newService();

        assertThrows(SecurityException.class, () -> service.createHold(
            new LegalHoldService.CreateLegalHoldRequest("X", null, List.of(UUID.randomUUID()))
        ));
        verify(legalHoldRepository, never()).save(any(LegalHold.class));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

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
