package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.AuditLog;
import com.ecm.core.entity.DispositionSchedule;
import com.ecm.core.entity.ImportJob;
import com.ecm.core.entity.ReplicationJob;
import com.ecm.core.entity.TransferTarget;
import com.ecm.core.event.NodesReindexRequestedEvent;
import com.ecm.core.event.NodeUpdatedEvent;
import com.ecm.core.exception.IllegalOperationException;
import com.ecm.core.model.Category;
import com.ecm.core.repository.AuditLogRepository;
import com.ecm.core.repository.ArchivePolicyRepository;
import com.ecm.core.repository.CategoryRepository;
import com.ecm.core.repository.DispositionScheduleRepository;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.FolderRepository;
import com.ecm.core.repository.ImportJobRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.ReplicationJobRepository;
import com.ecm.core.repository.TransferTargetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordsManagementServiceTest {

    @Mock private NodeRepository nodeRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private ArchivePolicyRepository archivePolicyRepository;
    @Mock private DispositionScheduleRepository dispositionScheduleRepository;
    @Mock private ImportJobRepository importJobRepository;
    @Mock private ReplicationJobRepository replicationJobRepository;
    @Mock private TransferTargetRepository transferTargetRepository;
    @Mock private SecurityService securityService;
    @Mock private AuditService auditService;
    @Mock private LegalHoldService legalHoldService;
    @Mock private FolderService folderService;
    @Mock private NodeService nodeService;
    @Mock private TenantWorkspaceScopeService tenantWorkspaceScopeService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PlatformTransactionManager transactionManager;

    private RecordsManagementService service;

    @BeforeEach
    void setUp() {
        service = new RecordsManagementService(
            nodeRepository,
            documentRepository,
            folderRepository,
            categoryRepository,
            auditLogRepository,
            archivePolicyRepository,
            dispositionScheduleRepository,
            importJobRepository,
            replicationJobRepository,
            transferTargetRepository,
            securityService,
            auditService,
            legalHoldService,
            folderService,
            tenantWorkspaceScopeService,
            eventPublisher,
            transactionManager
        );
        ReflectionTestUtils.setField(service, "nodeService", nodeService);
        // Lenient stub: only the bulk-declare path engages the TransactionTemplate. Per-method
        // tests that do not invoke bulk declare still build the service via this setUp and
        // would otherwise fail under Mockito's strict-stub mode with UnnecessaryStubbingException.
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
            .thenReturn(new SimpleTransactionStatus());
    }

    @Test
    @DisplayName("declareRecord adds rm:record aspect and declaration metadata")
    void declareRecordAddsAspectAndMetadata() {
        UUID nodeId = UUID.randomUUID();
        Document document = document(nodeId, "/Sites/Finance/report.pdf");
        document.setVersionLabel("1.2");

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(documentRepository.findById(nodeId)).thenReturn(Optional.of(document));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RecordsManagementService.RecordDeclarationDto dto = service.declareRecord(
            nodeId,
            new RecordsManagementService.DeclareRecordRequest("Quarter close record", null)
        );

        assertEquals("admin", dto.declaredBy());
        assertEquals("1.2", dto.declaredVersionLabel());
        assertEquals("Quarter close record", dto.declarationComment());
        assertNotNull(dto.declaredAt());
        assertEquals("admin", document.getProperties().get(RecordsManagementService.DECLARED_BY_PROPERTY));
        verify(eventPublisher).publishEvent(any(NodeUpdatedEvent.class));
    }

    @Test
    @DisplayName("assertHierarchyMutationAllowed blocks folder containing declared record")
    void assertHierarchyMutationAllowedBlocksAncestorFolder() {
        Folder folder = new Folder();
        folder.setId(UUID.randomUUID());
        folder.setName("Finance");
        folder.setPath("/Sites/Finance");
        folder.setArchiveStatus(Node.ArchiveStatus.LIVE);

        Document record = document(UUID.randomUUID(), "/Sites/Finance/report.pdf");
        record.addAspect(RecordsManagementService.RECORD_ASPECT);

        when(nodeRepository.findByAspectNameAndDeletedFalse(RecordsManagementService.RECORD_ASPECT))
            .thenReturn(List.of(record));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);

        IllegalOperationException ex = assertThrows(
            IllegalOperationException.class,
            () -> service.assertHierarchyMutationAllowed(folder, "delete")
        );

        assertEquals(
            "Cannot delete because node 'Finance' contains declared record(s): report.pdf",
            ex.getMessage()
        );
    }

    @Test
    @DisplayName("declareRecord rejects checked out documents")
    void declareRecordRejectsCheckedOutDocuments() {
        UUID nodeId = UUID.randomUUID();
        Document document = document(nodeId, "/Sites/Finance/report.pdf");
        document.checkout("alice");

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(documentRepository.findById(nodeId)).thenReturn(Optional.of(document));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);

        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> service.declareRecord(nodeId, new RecordsManagementService.DeclareRecordRequest(null, null))
        );

        assertEquals("Checked out documents cannot be declared as records", ex.getMessage());
    }

    // =================================================================================
    // Bulk record declaration tests
    //
    // Brief: docs/BULK_RECORD_DECLARATION_ADJUDICATION_AND_DESIGN_20260524.md
    //
    // Locks the orchestration pattern (§4 + §6): per-row REQUIRES_NEW TransactionTemplate
    // for failure isolation; SKIPPED_ALREADY_DECLARED carries a non-null declaration DTO
    // (Finding 1 / v3 Blocker); already-declared + non-null categoryId is still SKIPPED
    // with no category mutation (Finding 2 lock); errorCategory closed set is
    // {NODE_NOT_FOUND, NODE_NOT_VISIBLE, INTERNAL_ERROR} (Finding 3); INTERNAL_ERROR
    // never echoes ex.getMessage() to the response, never passes raw Throwable to SLF4J
    // (feedback_sanitize_throwable_cause_for_log_emission).
    // =================================================================================

    @Test
    @DisplayName("declareRecordsBulk declares every distinct visible node and engages a per-row REQUIRES_NEW TransactionTemplate")
    void declareRecordsBulkAllDeclared() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        Document docA = document(a, "/Sites/Finance/a.pdf");
        Document docB = document(b, "/Sites/Finance/b.pdf");
        Document docC = document(c, "/Sites/Finance/c.pdf");

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(documentRepository.findById(a)).thenReturn(Optional.of(docA));
        when(documentRepository.findById(b)).thenReturn(Optional.of(docB));
        when(documentRepository.findById(c)).thenReturn(Optional.of(docC));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RecordsManagementService.BulkDeclareResponse response = service.declareRecordsBulk(
            new RecordsManagementService.BulkDeclareRequest(List.of(a, b, c), null, "  batch declaration  ")
        );

        List<RecordsManagementService.BulkDeclareResult> rows = response.bulkDeclareResults().rows();
        assertEquals(3, rows.size());
        for (RecordsManagementService.BulkDeclareResult row : rows) {
            assertEquals(RecordsManagementService.BulkDeclareStatus.DECLARED, row.status());
            assertNotNull(row.declaration());
            assertNull(row.errorCategory());
            assertNull(row.errorMessage());
        }
        verify(documentRepository, times(3)).save(any(Document.class));
        verify(transactionManager, times(3)).getTransaction(any(TransactionDefinition.class));
    }

    @Test
    @DisplayName("declareRecordsBulk treats already-declared rows as SKIPPED with non-null declaration and skips save")
    void declareRecordsBulkAlreadyDeclaredSkipped() {
        UUID nodeId = UUID.randomUUID();
        Document document = document(nodeId, "/Sites/Finance/already.pdf");
        document.addAspect(RecordsManagementService.RECORD_ASPECT);
        document.getProperties().put(RecordsManagementService.DECLARED_AT_PROPERTY, "2026-01-01T00:00:00");
        document.getProperties().put(RecordsManagementService.DECLARED_BY_PROPERTY, "older-admin");

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(documentRepository.findById(nodeId)).thenReturn(Optional.of(document));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);

        RecordsManagementService.BulkDeclareResponse response = service.declareRecordsBulk(
            new RecordsManagementService.BulkDeclareRequest(List.of(nodeId), null, null)
        );

        List<RecordsManagementService.BulkDeclareResult> rows = response.bulkDeclareResults().rows();
        assertEquals(1, rows.size());
        RecordsManagementService.BulkDeclareResult row = rows.get(0);
        assertEquals(RecordsManagementService.BulkDeclareStatus.SKIPPED_ALREADY_DECLARED, row.status());
        assertNotNull(row.declaration(), "SKIPPED_ALREADY_DECLARED carries a non-null declaration DTO (mirrors single-row :511 toDto(document))");
        assertEquals(nodeId, row.declaration().nodeId());
        assertNull(row.errorCategory(), "SKIPPED is not an error; errorCategory must be null per Finding 3");
        assertNull(row.errorMessage());
        verify(documentRepository, never()).save(any(Document.class));
    }

    @Test
    @DisplayName("declareRecordsBulk skips category assignment when an already-declared row carries a non-null request categoryId (Finding 2)")
    void declareRecordsBulkAlreadyDeclaredWithCategoryRequestStillSkipsCategoryAssignment() {
        UUID nodeId = UUID.randomUUID();
        UUID requestCategoryId = UUID.randomUUID();
        Document document = document(nodeId, "/Sites/Finance/already.pdf");
        document.addAspect(RecordsManagementService.RECORD_ASPECT);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(documentRepository.findById(nodeId)).thenReturn(Optional.of(document));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);

        RecordsManagementService.BulkDeclareResponse response = service.declareRecordsBulk(
            new RecordsManagementService.BulkDeclareRequest(List.of(nodeId), requestCategoryId, "ignored comment")
        );

        RecordsManagementService.BulkDeclareResult row = response.bulkDeclareResults().rows().get(0);
        assertEquals(RecordsManagementService.BulkDeclareStatus.SKIPPED_ALREADY_DECLARED, row.status());
        // No category lookup must occur: applyRecordCategory(...) is only reachable through
        // loadRecordCategory(...) which reads from categoryRepository. Verify that path was
        // never engaged for the requested categoryId.
        verify(categoryRepository, never()).findById(requestCategoryId);
        verify(documentRepository, never()).save(any(Document.class));
        // No RM_RECORD_CATEGORY_ASSIGNED audit event must be emitted: the bulk-declare flow
        // only emits RM_RECORD_DECLARED on the DECLARED branch, and the SKIPPED branch
        // emits nothing.
        verify(auditService, never()).logEvent(eq("RM_RECORD_CATEGORY_ASSIGNED"), any(), any(), any(), any());
        verify(auditService, never()).logEvent(eq("RM_RECORD_DECLARED"), any(), any(), any(), any());
    }

    @Test
    @DisplayName("declareRecordsBulk maps a missing node to NODE_NOT_FOUND and keeps processing later rows")
    void declareRecordsBulkMissingNodeFailedPartial() {
        UUID ok1 = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        UUID ok2 = UUID.randomUUID();
        Document doc1 = document(ok1, "/Sites/Finance/a.pdf");
        Document doc2 = document(ok2, "/Sites/Finance/b.pdf");

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(documentRepository.findById(ok1)).thenReturn(Optional.of(doc1));
        when(documentRepository.findById(missing)).thenReturn(Optional.empty());
        when(documentRepository.findById(ok2)).thenReturn(Optional.of(doc2));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RecordsManagementService.BulkDeclareResponse response = service.declareRecordsBulk(
            new RecordsManagementService.BulkDeclareRequest(List.of(ok1, missing, ok2), null, null)
        );

        List<RecordsManagementService.BulkDeclareResult> rows = response.bulkDeclareResults().rows();
        assertEquals(3, rows.size());
        assertEquals(RecordsManagementService.BulkDeclareStatus.DECLARED, rows.get(0).status());
        assertEquals(RecordsManagementService.BulkDeclareStatus.FAILED, rows.get(1).status());
        assertEquals(RecordsManagementService.BulkDeclareErrorCategory.NODE_NOT_FOUND, rows.get(1).errorCategory());
        assertNull(rows.get(1).declaration());
        assertEquals(RecordsManagementService.BulkDeclareStatus.DECLARED, rows.get(2).status());
        verify(documentRepository, times(2)).save(any(Document.class));
    }

    @Test
    @DisplayName("declareRecordsBulk classifies an invisible node as NODE_NOT_VISIBLE")
    void declareRecordsBulkInvisibleNodeReportsCategory() {
        UUID nodeId = UUID.randomUUID();
        Document document = document(nodeId, "/Tenants/other/secret.pdf");
        // Make the document load throw ResourceNotFoundException via the tenant-scope filter.
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(documentRepository.findById(nodeId)).thenReturn(Optional.of(document));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(true);
        when(tenantWorkspaceScopeService.isPathVisible("/Tenants/other/secret.pdf")).thenReturn(false);

        RecordsManagementService.BulkDeclareResponse response = service.declareRecordsBulk(
            new RecordsManagementService.BulkDeclareRequest(List.of(nodeId), null, null)
        );

        RecordsManagementService.BulkDeclareResult row = response.bulkDeclareResults().rows().get(0);
        assertEquals(RecordsManagementService.BulkDeclareStatus.FAILED, row.status());
        assertEquals(RecordsManagementService.BulkDeclareErrorCategory.NODE_NOT_VISIBLE, row.errorCategory());
        assertNull(row.declaration());
    }

    @Test
    @DisplayName("declareRecordsBulk sanitises internal-error messages: never echoes ex.getMessage(), always carries the class simple name")
    void declareRecordsBulkInternalErrorSanitisedMessage() {
        UUID nodeId = UUID.randomUUID();
        Document document = document(nodeId, "/Sites/Finance/probe.pdf");
        String probe = "BULK_DECLARE_USER_PII_PROBE_d8c4a2f0";

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(documentRepository.findById(nodeId)).thenReturn(Optional.of(document));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);
        when(documentRepository.save(any(Document.class))).thenThrow(new RuntimeException(probe));

        RecordsManagementService.BulkDeclareResponse response = service.declareRecordsBulk(
            new RecordsManagementService.BulkDeclareRequest(List.of(nodeId), null, null)
        );

        RecordsManagementService.BulkDeclareResult row = response.bulkDeclareResults().rows().get(0);
        assertEquals(RecordsManagementService.BulkDeclareStatus.FAILED, row.status());
        assertEquals(RecordsManagementService.BulkDeclareErrorCategory.INTERNAL_ERROR, row.errorCategory());
        assertNotNull(row.errorMessage());
        assertFalse(row.errorMessage().contains(probe), "errorMessage must not echo ex.getMessage() — see feedback_sanitize_throwable_cause_for_log_emission");
        assertTrue(row.errorMessage().contains("RuntimeException"), "errorMessage carries the exception class simple name for operator triage");
    }

    @Test
    @DisplayName("declareRecordsBulk continues after a failed row, preserves input order, and only saves DECLARED rows")
    void declareRecordsBulkContinuesAfterFailedRowAndPreservesOrder() {
        UUID ok1 = UUID.randomUUID();
        UUID fail2 = UUID.randomUUID();
        UUID ok3 = UUID.randomUUID();
        Document doc1 = document(ok1, "/Sites/Finance/a.pdf");
        Document doc3 = document(ok3, "/Sites/Finance/c.pdf");

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(documentRepository.findById(ok1)).thenReturn(Optional.of(doc1));
        when(documentRepository.findById(fail2)).thenReturn(Optional.empty()); // → NODE_NOT_FOUND
        when(documentRepository.findById(ok3)).thenReturn(Optional.of(doc3));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RecordsManagementService.BulkDeclareResponse response = service.declareRecordsBulk(
            new RecordsManagementService.BulkDeclareRequest(List.of(ok1, fail2, ok3), null, null)
        );

        List<RecordsManagementService.BulkDeclareResult> rows = response.bulkDeclareResults().rows();

        // Lock 1: per-row REQUIRES_NEW TransactionTemplate engagement.
        // The orchestrator must drive one transaction boundary per deduped input row, not
        // wrap the loop in a single transaction. There is no parent entity here (bulk-declare
        // mutates pre-existing Documents), so this verify is the load-bearing assertion that
        // failure-isolation propagation kicks in row-by-row.
        verify(transactionManager, times(3)).getTransaction(any(TransactionDefinition.class));

        // Lock 2: middle failure does not abort the run; input order preserved.
        assertEquals(3, rows.size());
        assertEquals(ok1, rows.get(0).nodeId());
        assertEquals(RecordsManagementService.BulkDeclareStatus.DECLARED, rows.get(0).status());
        assertEquals(fail2, rows.get(1).nodeId());
        assertEquals(RecordsManagementService.BulkDeclareStatus.FAILED, rows.get(1).status());
        assertEquals(ok3, rows.get(2).nodeId());
        assertEquals(RecordsManagementService.BulkDeclareStatus.DECLARED, rows.get(2).status());

        // Lock 3: documentRepository.save fires only on DECLARED rows. The failed row's
        // declareOneRow exits at loadLiveDocument's throw before ever reaching the save site.
        verify(documentRepository, times(2)).save(any(Document.class));
    }

    @Test
    @DisplayName("declareRecordsBulk rejects an empty nodeIds list before any per-row work")
    void declareRecordsBulkEmptyNodeIdsReturns400() {
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);

        assertThrows(
            IllegalArgumentException.class,
            () -> service.declareRecordsBulk(
                new RecordsManagementService.BulkDeclareRequest(List.of(), null, null)
            )
        );
        verify(transactionManager, never()).getTransaction(any(TransactionDefinition.class));
        verify(documentRepository, never()).save(any(Document.class));
    }

    @Test
    @DisplayName("declareRecordsBulk rejects a null-only nodeIds list AFTER dedupe (v3.1 boundary guard)")
    void declareRecordsBulkNullOnlyNodeIdsReturns400() {
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);

        java.util.ArrayList<UUID> nullOnly = new java.util.ArrayList<>();
        nullOnly.add(null);
        nullOnly.add(null);

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.declareRecordsBulk(
                new RecordsManagementService.BulkDeclareRequest(nullOnly, null, null)
            )
        );
        // The post-dedupe guard message is distinct from the pre-dedupe one so external
        // callers can tell which path tripped the validation.
        assertTrue(ex.getMessage().contains("non-null"), "post-dedupe guard must say 'non-null'; got: " + ex.getMessage());
        verify(transactionManager, never()).getTransaction(any(TransactionDefinition.class));
        verify(documentRepository, never()).save(any(Document.class));
    }

    @Test
    @DisplayName("declareRecordsBulk rejects non-admin callers via requireAdmin before any per-row work")
    void declareRecordsBulkRejectsNonAdmin() {
        UUID nodeId = UUID.randomUUID();
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);

        assertThrows(
            SecurityException.class,
            () -> service.declareRecordsBulk(
                new RecordsManagementService.BulkDeclareRequest(List.of(nodeId), null, null)
            )
        );
        verify(transactionManager, never()).getTransaction(any(TransactionDefinition.class));
        verify(documentRepository, never()).findById(any(UUID.class));
        verify(documentRepository, never()).save(any(Document.class));
    }

    @Test
    @DisplayName("declareRecordsBulk dedupes the input list so duplicate UUIDs are emitted once")
    void declareRecordsBulkDedupesDuplicateNodeIdsInRequest() {
        // Mirrors the prior gate observation (legal-hold slice) that LinkedHashSet de-dup
        // upstream of per-row work is consistent across slice patterns. Verifies that even
        // when the same UUID appears twice in the input, it produces a single result row
        // and a single TransactionTemplate boundary.
        UUID nodeId = UUID.randomUUID();
        Document document = document(nodeId, "/Sites/Finance/a.pdf");

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(documentRepository.findById(nodeId)).thenReturn(Optional.of(document));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RecordsManagementService.BulkDeclareResponse response = service.declareRecordsBulk(
            new RecordsManagementService.BulkDeclareRequest(List.of(nodeId, nodeId, nodeId), null, null)
        );

        assertEquals(1, response.bulkDeclareResults().rows().size());
        verify(transactionManager, times(1)).getTransaction(any(TransactionDefinition.class));
        verify(documentRepository, times(1)).save(any(Document.class));
    }

    @Test
    @DisplayName("undeclareRecord removes rm:record aspect and RM metadata")
    void undeclareRecordRemovesAspectAndMetadata() {
        UUID nodeId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Document document = document(nodeId, "/Sites/Finance/report.pdf");
        document.addAspect(RecordsManagementService.RECORD_ASPECT);
        document.setVersionLabel("1.2");
        document.getProperties().put(RecordsManagementService.DECLARED_AT_PROPERTY, "2026-04-14T20:30:00");
        document.getProperties().put(RecordsManagementService.DECLARED_BY_PROPERTY, "admin");
        document.getProperties().put(RecordsManagementService.DECLARATION_COMMENT_PROPERTY, "Quarter close record");
        document.getProperties().put(RecordsManagementService.DECLARED_VERSION_LABEL_PROPERTY, "1.2");
        document.getProperties().put(RecordsManagementService.RECORD_CATEGORY_ID_PROPERTY, categoryId.toString());
        document.getProperties().put(RecordsManagementService.RECORD_CATEGORY_NAME_PROPERTY, "Contracts");
        document.getProperties().put(RecordsManagementService.RECORD_CATEGORY_PATH_PROPERTY, "/Records Management/Contracts");
        Category category = recordCategory(categoryId, "/Records Management/Contracts", "Contracts", null);
        document.getCategories().add(category);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(documentRepository.findById(nodeId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);
        when(folderRepository.findActiveFoldersByType(Folder.FolderType.FILE_PLAN)).thenReturn(List.of());

        service.undeclareRecord(nodeId, new RecordsManagementService.UndeclareRecordRequest("  Administrative correction  "));

        org.junit.jupiter.api.Assertions.assertFalse(document.hasAspect(RecordsManagementService.RECORD_ASPECT));
        assertEquals(0, document.getCategories().size());
        assertEquals(null, document.getProperties().get(RecordsManagementService.DECLARED_AT_PROPERTY));
        assertEquals(null, document.getProperties().get(RecordsManagementService.DECLARED_BY_PROPERTY));
        assertEquals(null, document.getProperties().get(RecordsManagementService.DECLARATION_COMMENT_PROPERTY));
        assertEquals(null, document.getProperties().get(RecordsManagementService.DECLARED_VERSION_LABEL_PROPERTY));
        assertEquals(null, document.getProperties().get(RecordsManagementService.RECORD_CATEGORY_ID_PROPERTY));
        assertEquals(null, document.getProperties().get(RecordsManagementService.RECORD_CATEGORY_NAME_PROPERTY));
        assertEquals(null, document.getProperties().get(RecordsManagementService.RECORD_CATEGORY_PATH_PROPERTY));
        verify(eventPublisher).publishEvent(any(NodeUpdatedEvent.class));
        verify(auditService).logEvent(
            org.mockito.ArgumentMatchers.eq("RM_RECORD_UNDECLARED"),
            org.mockito.ArgumentMatchers.eq(nodeId),
            org.mockito.ArgumentMatchers.eq("report.pdf"),
            org.mockito.ArgumentMatchers.eq("admin"),
            org.mockito.ArgumentMatchers.eq("Undeclared document as record. Reason: Administrative correction")
        );
    }

    @Test
    @DisplayName("undeclareRecord requires a non-empty reason")
    void undeclareRecordRequiresReason() {
        UUID nodeId = UUID.randomUUID();
        Document document = document(nodeId, "/Sites/Finance/report.pdf");
        document.addAspect(RecordsManagementService.RECORD_ASPECT);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(documentRepository.findById(nodeId)).thenReturn(Optional.of(document));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.undeclareRecord(nodeId, new RecordsManagementService.UndeclareRecordRequest("   "))
        );

        assertEquals("Undeclare reason is required", ex.getMessage());
    }

    @Test
    @DisplayName("undeclareRecord blocks nodes under active legal hold")
    void undeclareRecordBlocksLegalHold() {
        UUID nodeId = UUID.randomUUID();
        Document document = document(nodeId, "/Sites/Finance/report.pdf");
        document.addAspect(RecordsManagementService.RECORD_ASPECT);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(documentRepository.findById(nodeId)).thenReturn(Optional.of(document));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);
        org.mockito.Mockito.doThrow(new IllegalOperationException(
            "Cannot undeclare record because node 'report.pdf' is under active legal hold(s): Hold A"
        )).when(legalHoldService).assertOperationAllowed(document, "undeclare record");

        IllegalOperationException ex = assertThrows(
            IllegalOperationException.class,
            () -> service.undeclareRecord(nodeId, new RecordsManagementService.UndeclareRecordRequest("Administrative correction"))
        );

        assertEquals("Cannot undeclare record because node 'report.pdf' is under active legal hold(s): Hold A", ex.getMessage());
        verify(auditService).logEvent(
            org.mockito.ArgumentMatchers.eq("RM_RECORD_UNDECLARE_BLOCKED"),
            org.mockito.ArgumentMatchers.eq(nodeId),
            org.mockito.ArgumentMatchers.eq("report.pdf"),
            org.mockito.ArgumentMatchers.eq("admin"),
            org.mockito.ArgumentMatchers.contains("active legal hold(s): Hold A")
        );
    }

    @Test
    @DisplayName("getActivityTimeline groups RM audit events by day and event family")
    void getActivityTimelineGroupsAuditEvents() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRecordsManagementEventsByDaySince(any(LocalDateTime.class))).thenReturn(List.of(
            new Object[]{yesterday, "RM_RECORD_DECLARED", 2L},
            new Object[]{yesterday, "RM_RECORD_CATEGORY_ASSIGNED", 1L},
            new Object[]{today, "RM_RECORD_UNDECLARED", 1L},
            new Object[]{today, "RM_FILE_PLAN_MOVED", 3L},
            new Object[]{today, "RM_RECORD_CATEGORY_UPDATED", 1L},
            new Object[]{today, "RM_RECORD_UNDECLARE_BLOCKED", 9L}
        ));

        RecordsManagementService.RecordsActivityTimelineDto timeline = service.getActivityTimeline(3);

        assertEquals(3, timeline.days());
        assertEquals(3, timeline.points().size());

        RecordsManagementService.RecordsActivityPointDto yesterdayPoint = timeline.points().stream()
            .filter(point -> point.day().equals(yesterday.toString()))
            .findFirst()
            .orElseThrow();
        assertEquals(2, yesterdayPoint.declaredCount());
        assertEquals(1, yesterdayPoint.categoryAssignedCount());
        assertEquals(0, yesterdayPoint.governanceChangeCount());
        assertEquals(3, yesterdayPoint.totalCount());

        RecordsManagementService.RecordsActivityPointDto todayPoint = timeline.points().stream()
            .filter(point -> point.day().equals(today.toString()))
            .findFirst()
            .orElseThrow();
        assertEquals(1, todayPoint.undeclaredCount());
        assertEquals(4, todayPoint.governanceChangeCount());
        assertEquals(0, todayPoint.categoryAssignedCount());
        assertEquals(5, todayPoint.totalCount());
    }

    @Test
    @DisplayName("getActivityHighlights summarizes current and previous RM windows")
    void getActivityHighlightsSummarizesWindows() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate twoDaysAgo = today.minusDays(2);
        LocalDate threeDaysAgo = today.minusDays(3);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRecordsManagementEventsByDaySince(any(LocalDateTime.class))).thenReturn(List.of(
            new Object[]{threeDaysAgo, "RM_RECORD_UNDECLARED", 2L},
            new Object[]{twoDaysAgo, "RM_RECORD_CATEGORY_ASSIGNED", 1L},
            new Object[]{twoDaysAgo, "RM_RECORD_CATEGORY_UPDATED", 1L},
            new Object[]{yesterday, "RM_FILE_PLAN_MOVED", 3L},
            new Object[]{today, "RM_RECORD_DECLARED", 1L},
            new Object[]{today, "RM_RECORD_CATEGORY_ASSIGNED", 2L},
            new Object[]{today, "RM_FILE_PLAN_UPDATED", 1L},
            new Object[]{today, "RM_RECORD_UNDECLARE_BLOCKED", 9L}
        ));

        RecordsManagementService.RecordsActivityHighlightsDto highlights = service.getActivityHighlights(2);

        assertEquals(2, highlights.windowDays());
        assertEquals(yesterday.toString(), highlights.currentWindow().fromDay());
        assertEquals(today.toString(), highlights.currentWindow().toDay());
        assertEquals(2, highlights.currentWindow().activeDayCount());
        assertEquals(1, highlights.currentWindow().declaredCount());
        assertEquals(0, highlights.currentWindow().undeclaredCount());
        assertEquals(2, highlights.currentWindow().categoryAssignedCount());
        assertEquals(4, highlights.currentWindow().governanceChangeCount());
        assertEquals(7, highlights.currentWindow().totalCount());

        assertEquals(threeDaysAgo.toString(), highlights.previousWindow().fromDay());
        assertEquals(twoDaysAgo.toString(), highlights.previousWindow().toDay());
        assertEquals(2, highlights.previousWindow().activeDayCount());
        assertEquals(0, highlights.previousWindow().declaredCount());
        assertEquals(2, highlights.previousWindow().undeclaredCount());
        assertEquals(1, highlights.previousWindow().categoryAssignedCount());
        assertEquals(1, highlights.previousWindow().governanceChangeCount());
        assertEquals(4, highlights.previousWindow().totalCount());

        assertEquals(today.toString(), highlights.busiestDay().day());
        assertEquals(4, highlights.busiestDay().totalCount());
    }

    @Test
    @DisplayName("getActivityFamilyTrend groups RM activity families into contiguous buckets")
    void getActivityFamilyTrendGroupsFamiliesIntoBuckets() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate twoDaysAgo = today.minusDays(2);
        LocalDate threeDaysAgo = today.minusDays(3);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRecordsManagementEventsByDaySince(any(LocalDateTime.class))).thenReturn(List.of(
            new Object[]{threeDaysAgo, "RM_RECORD_DECLARED", 2L},
            new Object[]{twoDaysAgo, "RM_RECORD_CATEGORY_ASSIGNED", 1L},
            new Object[]{yesterday, "RM_FILE_PLAN_MOVED", 3L},
            new Object[]{today, "RM_RECORD_UNDECLARE_BLOCKED", 1L}
        ));

        RecordsManagementService.ActivityFamilyTrendDto trend = service.getActivityFamilyTrend(7, 2);

        assertEquals(7, trend.days());
        assertEquals(2, trend.bucketDays());
        assertEquals(4, trend.buckets().size());

        RecordsManagementService.ActivityFamilyTrendBucketDto latestBucket = trend.buckets().get(3);
        assertEquals(yesterday.toString() + " to " + today.toString(), latestBucket.label());
        assertEquals(2, latestBucket.activeDayCount());
        assertEquals(4, latestBucket.totalCount());
        assertEquals("GOVERNANCE_CHANGE", latestBucket.familyCounts().get(0).family());
        assertEquals(3, latestBucket.familyCounts().get(0).count());
        assertEquals("OTHER", latestBucket.familyCounts().get(1).family());
        assertEquals(1, latestBucket.familyCounts().get(1).count());
    }

    @Test
    @DisplayName("getActivityFamilyTrend clamps days and bucket size to valid ranges")
    void getActivityFamilyTrendClampsRanges() {
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRecordsManagementEventsByDaySince(any(LocalDateTime.class))).thenReturn(List.of());

        RecordsManagementService.ActivityFamilyTrendDto trend = service.getActivityFamilyTrend(3, 99);

        assertEquals(7, trend.days());
        assertEquals(7, trend.bucketDays());
    }

    @Test
    @DisplayName("getActivityFamilyReport aggregates mixed-family RM activity with current details")
    void getActivityFamilyReportAggregatesFamilies() {
        LocalDateTime currentFrom = LocalDateTime.of(2026, 4, 1, 0, 0);
        LocalDateTime currentTo = LocalDateTime.of(2026, 4, 15, 23, 59, 59);
        LocalDateTime previousFrom = LocalDateTime.of(2026, 3, 17, 0, 0);
        LocalDateTime previousTo = LocalDateTime.of(2026, 3, 31, 23, 59, 59);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByTypeBetween(currentFrom, currentTo)).thenReturn(List.of(
            new Object[]{"RM_RECORD_DECLARED", 5L, LocalDateTime.of(2026, 4, 15, 10, 0)},
            new Object[]{"RM_FILE_PLAN_MOVED", 2L, LocalDateTime.of(2026, 4, 12, 9, 0)},
            new Object[]{"RM_RECORD_UNDECLARE_BLOCKED", 1L, LocalDateTime.of(2026, 4, 10, 8, 0)}
        ));
        when(auditLogRepository.countRmEventsByTypeBetween(previousFrom, previousTo)).thenReturn(List.of(
            new Object[]{"RM_RECORD_DECLARED", 2L, LocalDateTime.of(2026, 3, 30, 18, 0)},
            new Object[]{"RM_RECORD_CATEGORY_ASSIGNED", 3L, LocalDateTime.of(2026, 3, 28, 17, 0)}
        ));
        when(auditLogRepository.countRmEventsByUsernameAndTypeBetween(currentFrom, currentTo)).thenReturn(List.of(
            new Object[]{"admin", "RM_RECORD_DECLARED", 4L, LocalDateTime.of(2026, 4, 15, 10, 0)},
            new Object[]{"alice", "RM_RECORD_DECLARED", 1L, LocalDateTime.of(2026, 4, 14, 11, 0)},
            new Object[]{"admin", "RM_FILE_PLAN_MOVED", 2L, LocalDateTime.of(2026, 4, 12, 9, 0)},
            new Object[]{null, "RM_RECORD_UNDECLARE_BLOCKED", 1L, LocalDateTime.of(2026, 4, 10, 8, 0)}
        ));

        RecordsManagementService.ActivityFamilyReportDto result = service.getActivityFamilyReport(
            currentFrom,
            currentTo,
            2,
            2
        );

        assertEquals("2026-04-01T00:00", result.currentWindow().from());
        assertEquals("2026-04-15T23:59:59", result.currentWindow().to());
        assertEquals("2026-03-17T00:00", result.previousWindow().from());
        assertEquals("2026-03-31T23:59:59", result.previousWindow().to());
        assertEquals(2, result.eventTypeLimit());
        assertEquals(2, result.contributorLimit());
        assertEquals(8, result.currentTotalCount());
        assertEquals(5, result.previousTotalCount());
        assertEquals(4, result.families().size());

        RecordsManagementService.ActivityFamilyReportEntryDto declared = result.families().stream()
            .filter(entry -> "DECLARED".equals(entry.family()))
            .findFirst()
            .orElseThrow();
        assertEquals(5, declared.currentCount());
        assertEquals(2, declared.previousCount());
        assertEquals(3, declared.delta());
        assertEquals(1, declared.topEventTypes().size());
        assertEquals("RM_RECORD_DECLARED", declared.topEventTypes().get(0).eventType());
        assertEquals(2, declared.topContributors().size());
        assertEquals("admin", declared.topContributors().get(0).username());
        assertEquals(4, declared.topContributors().get(0).count());

        RecordsManagementService.ActivityFamilyReportEntryDto other = result.families().stream()
            .filter(entry -> "OTHER".equals(entry.family()))
            .findFirst()
            .orElseThrow();
        assertEquals(1, other.currentCount());
        assertEquals(0, other.previousCount());
        assertEquals("(System)", other.topContributors().get(0).label());
    }

    @Test
    @DisplayName("getActivityFamilyReport uses default closed range when params are omitted")
    void getActivityFamilyReportUsesDefaults() {
        LocalDate today = LocalDate.now();
        LocalDate startDay = today.minusDays(27);
        LocalDateTime expectedFrom = startDay.atStartOfDay();
        LocalDateTime expectedTo = today.atTime(23, 59, 59);
        long spanSeconds = ChronoUnit.SECONDS.between(expectedFrom, expectedTo);
        LocalDateTime expectedPreviousTo = expectedFrom.minusSeconds(1);
        LocalDateTime expectedPreviousFrom = expectedPreviousTo.minusSeconds(spanSeconds);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByTypeBetween(expectedFrom, expectedTo)).thenReturn(List.of());
        when(auditLogRepository.countRmEventsByTypeBetween(expectedPreviousFrom, expectedPreviousTo)).thenReturn(List.of());
        when(auditLogRepository.countRmEventsByUsernameAndTypeBetween(expectedFrom, expectedTo)).thenReturn(List.of());

        RecordsManagementService.ActivityFamilyReportDto result = service.getActivityFamilyReport(null, null, null, null);

        assertEquals(expectedFrom.toString(), result.currentWindow().from());
        assertEquals(expectedTo.toString(), result.currentWindow().to());
        assertEquals(expectedPreviousFrom.toString(), result.previousWindow().from());
        assertEquals(expectedPreviousTo.toString(), result.previousWindow().to());
        assertEquals(3, result.eventTypeLimit());
        assertEquals(3, result.contributorLimit());
        assertTrue(result.families().isEmpty());
    }

    @Test
    @DisplayName("getActivityFamilyReport rejects partial custom range")
    void getActivityFamilyReportRejectsPartialRange() {
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.getActivityFamilyReport(LocalDateTime.of(2026, 4, 1, 0, 0), null, 3, 3)
        );

        assertEquals("Both from and to are required when specifying a custom range", ex.getMessage());
    }

    @Test
    @DisplayName("getActivityFamilyReport rejects ranges longer than ninety days")
    void getActivityFamilyReportRejectsRangeOverMaxDays() {
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.getActivityFamilyReport(
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 4, 15, 23, 59, 59),
                3,
                3
            )
        );

        assertEquals("Range exceeds maximum of 90 days", ex.getMessage());
    }

    @Test
    @DisplayName("getActivityBreakdown groups RM activity into contiguous buckets")
    void getActivityBreakdownGroupsBuckets() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate twoDaysAgo = today.minusDays(2);
        LocalDate threeDaysAgo = today.minusDays(3);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRecordsManagementEventsByDaySince(any(LocalDateTime.class))).thenReturn(List.of(
            new Object[]{threeDaysAgo, "RM_RECORD_DECLARED", 1L},
            new Object[]{twoDaysAgo, "RM_RECORD_CATEGORY_ASSIGNED", 2L},
            new Object[]{yesterday, "RM_FILE_PLAN_MOVED", 3L},
            new Object[]{today, "RM_RECORD_UNDECLARED", 1L}
        ));

        RecordsManagementService.RecordsActivityBreakdownDto breakdown = service.getActivityBreakdown(7, 2);

        assertEquals(7, breakdown.days());
        assertEquals(2, breakdown.bucketDays());
        assertEquals(4, breakdown.buckets().size());

        RecordsManagementService.RecordsActivityBucketDto latestBucket = breakdown.buckets().get(3);
        assertEquals(yesterday.toString() + " to " + today.toString(), latestBucket.label());
        assertEquals(2, latestBucket.activeDayCount());
        assertEquals(0, latestBucket.declaredCount());
        assertEquals(1, latestBucket.undeclaredCount());
        assertEquals(0, latestBucket.categoryAssignedCount());
        assertEquals(3, latestBucket.governanceChangeCount());
        assertEquals(4, latestBucket.totalCount());
    }

    @Test
    @DisplayName("getActivityContributors aggregates RM events by user and event family")
    void getActivityContributorsAggregatesByUser() {
        LocalDateTime now = LocalDateTime.now();

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByUsernameAndTypeBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(List.of(
                new Object[]{"admin", "RM_RECORD_DECLARED", 5L, now.minusHours(1)},
                new Object[]{"admin", "RM_RECORD_UNDECLARED", 2L, now.minusHours(2)},
                new Object[]{"admin", "RM_FILE_PLAN_CREATED", 1L, now.minusHours(3)},
                new Object[]{"alice", "RM_RECORD_DECLARED", 3L, now},
                new Object[]{"alice", "RM_RECORD_CATEGORY_ASSIGNED", 4L, now.minusMinutes(30)},
                new Object[]{null, "RM_RECORD_CATEGORY_UPDATED", 2L, now.minusHours(5)},
                new Object[]{"bob", "RM_RECORD_UNDECLARE_BLOCKED", 9L, now.minusHours(1)}
            ));

        RecordsManagementService.ActivityContributorsDto result = service.getActivityContributors(28, 5);

        assertEquals(28, result.days());
        assertEquals(5, result.limit());
        assertEquals(3, result.contributors().size());

        RecordsManagementService.ActivityContributorDto first = result.contributors().get(0);
        assertEquals("admin", first.username());
        assertEquals("admin", first.label());
        assertEquals(5, first.declaredCount());
        assertEquals(2, first.undeclaredCount());
        assertEquals(0, first.categoryAssignedCount());
        assertEquals(1, first.governanceChangeCount());
        assertEquals(8, first.totalCount());
        assertEquals(now.minusHours(1), first.lastEventTime());

        RecordsManagementService.ActivityContributorDto second = result.contributors().get(1);
        assertEquals("alice", second.username());
        assertEquals("alice", second.label());
        assertEquals(3, second.declaredCount());
        assertEquals(0, second.undeclaredCount());
        assertEquals(4, second.categoryAssignedCount());
        assertEquals(0, second.governanceChangeCount());
        assertEquals(7, second.totalCount());
        assertEquals(now, second.lastEventTime());

        RecordsManagementService.ActivityContributorDto third = result.contributors().get(2);
        assertEquals(null, third.username());
        assertEquals("(System)", third.label());
        assertEquals(0, third.declaredCount());
        assertEquals(0, third.undeclaredCount());
        assertEquals(0, third.categoryAssignedCount());
        assertEquals(2, third.governanceChangeCount());
        assertEquals(2, third.totalCount());
    }

    @Test
    @DisplayName("getActivityContributors respects limit parameter")
    void getActivityContributorsRespectsLimit() {
        LocalDateTime now = LocalDateTime.now();

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByUsernameAndTypeBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(List.of(
                new Object[]{"admin", "RM_RECORD_DECLARED", 5L, now},
                new Object[]{"alice", "RM_RECORD_DECLARED", 3L, now},
                new Object[]{"bob", "RM_RECORD_DECLARED", 1L, now}
            ));

        RecordsManagementService.ActivityContributorsDto result = service.getActivityContributors(28, 2);

        assertEquals(2, result.contributors().size());
        assertEquals("admin", result.contributors().get(0).username());
        assertEquals("alice", result.contributors().get(1).username());
    }

    @Test
    @DisplayName("getActivityContributors returns empty list when no RM events")
    void getActivityContributorsReturnsEmptyWhenNoEvents() {
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByUsernameAndTypeBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(List.of());

        RecordsManagementService.ActivityContributorsDto result = service.getActivityContributors(null, null);

        assertEquals(28, result.days());
        assertEquals(5, result.limit());
        assertEquals(0, result.contributors().size());
    }

    @Test
    @DisplayName("getActivityContributors clamps days and limit to valid ranges")
    void getActivityContributorsClampsDaysAndLimit() {
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByUsernameAndTypeBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(List.of());

        RecordsManagementService.ActivityContributorsDto result = service.getActivityContributors(1, 100);

        assertEquals(7, result.days());
        assertEquals(50, result.limit());
    }

    @Test
    @DisplayName("getActivityContributorHighlights compares current and previous contributor windows")
    void getActivityContributorHighlightsComparesWindows() {
        LocalDate today = LocalDate.now();
        LocalDate currentStartDay = today.minusDays(6);
        LocalDate previousEndDay = currentStartDay.minusDays(1);
        LocalDate previousStartDay = previousEndDay.minusDays(6);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByUsernameAndTypeBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(List.of(
                new Object[]{"admin", "RM_RECORD_DECLARED", 5L, LocalDateTime.of(2026, 4, 15, 10, 0)},
                new Object[]{"admin", "RM_FILE_PLAN_MOVED", 3L, LocalDateTime.of(2026, 4, 14, 8, 0)},
                new Object[]{null, "RM_RECORD_UNDECLARE_BLOCKED", 1L, LocalDateTime.of(2026, 4, 13, 7, 0)}
            ), List.of(
                new Object[]{"admin", "RM_RECORD_DECLARED", 2L, LocalDateTime.of(2026, 4, 7, 18, 0)},
                new Object[]{"bob", "RM_FILE_PLAN_MOVED", 4L, LocalDateTime.of(2026, 4, 6, 11, 0)}
            ));

        RecordsManagementService.ActivityContributorHighlightsDto result =
            service.getActivityContributorHighlights(7, 5);

        assertEquals(7, result.windowDays());
        assertEquals(5, result.limit());
        assertEquals(currentStartDay.toString(), result.currentWindow().fromDay());
        assertEquals(today.toString(), result.currentWindow().toDay());
        assertEquals(previousStartDay.toString(), result.previousWindow().fromDay());
        assertEquals(previousEndDay.toString(), result.previousWindow().toDay());
        assertEquals(3, result.contributors().size());

        RecordsManagementService.ActivityContributorHighlightDto first = result.contributors().get(0);
        assertEquals("admin", first.username());
        assertEquals(8, first.currentCount());
        assertEquals(2, first.previousCount());
        assertEquals(6, first.delta());

        RecordsManagementService.ActivityContributorHighlightDto second = result.contributors().get(1);
        assertEquals("bob", second.username());
        assertEquals(0, second.currentCount());
        assertEquals(4, second.previousCount());

        RecordsManagementService.ActivityContributorHighlightDto third = result.contributors().get(2);
        assertNull(third.username());
        assertEquals("(System)", third.label());
        assertEquals(1, third.currentCount());
    }

    @Test
    @DisplayName("getActivityContributorHighlights uses defaults and clamps limit")
    void getActivityContributorHighlightsUsesDefaultsAndClampsLimit() {
        LocalDate today = LocalDate.now();
        LocalDate currentStartDay = today.minusDays(6);
        LocalDate previousEndDay = currentStartDay.minusDays(1);
        LocalDate previousStartDay = previousEndDay.minusDays(6);
        LocalDateTime currentFrom = currentStartDay.atStartOfDay();
        LocalDateTime currentTo = today.atTime(23, 59, 59);
        LocalDateTime previousFrom = previousStartDay.atStartOfDay();
        LocalDateTime previousTo = previousEndDay.atTime(23, 59, 59);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByUsernameAndTypeBetween(currentFrom, currentTo)).thenReturn(List.of());
        when(auditLogRepository.countRmEventsByUsernameAndTypeBetween(previousFrom, previousTo)).thenReturn(List.of());

        RecordsManagementService.ActivityContributorHighlightsDto result =
            service.getActivityContributorHighlights(null, 99);

        assertEquals(7, result.windowDays());
        assertEquals(50, result.limit());
        assertTrue(result.contributors().isEmpty());
    }

    @Test
    @DisplayName("getActivityContributorEventTypeHighlights compares current and previous contributor event types")
    void getActivityContributorEventTypeHighlightsComparesWindows() {
        LocalDate today = LocalDate.now();
        LocalDate currentStartDay = today.minusDays(6);
        LocalDate previousEndDay = currentStartDay.minusDays(1);
        LocalDate previousStartDay = previousEndDay.minusDays(6);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByUsernameAndTypeBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(List.of(
                new Object[]{"admin", "RM_RECORD_DECLARED", 5L, LocalDateTime.of(2026, 4, 15, 10, 0)},
                new Object[]{"admin", "RM_FILE_PLAN_MOVED", 3L, LocalDateTime.of(2026, 4, 14, 8, 0)},
                new Object[]{null, "RM_RECORD_UNDECLARE_BLOCKED", 1L, LocalDateTime.of(2026, 4, 13, 7, 0)}
            ), List.of(
                new Object[]{"admin", "RM_RECORD_DECLARED", 2L, LocalDateTime.of(2026, 4, 7, 18, 0)},
                new Object[]{"bob", "RM_RECORD_CATEGORY_ASSIGNED", 4L, LocalDateTime.of(2026, 4, 6, 11, 0)}
            ));

        RecordsManagementService.ActivityContributorEventTypeHighlightsDto result =
            service.getActivityContributorEventTypeHighlights(7, 5, 3);

        assertEquals(7, result.windowDays());
        assertEquals(5, result.limit());
        assertEquals(3, result.eventTypeLimit());
        assertEquals(currentStartDay.toString(), result.currentWindow().fromDay());
        assertEquals(today.toString(), result.currentWindow().toDay());
        assertEquals(previousStartDay.toString(), result.previousWindow().fromDay());
        assertEquals(previousEndDay.toString(), result.previousWindow().toDay());
        assertEquals(3, result.contributors().size());

        RecordsManagementService.ActivityContributorEventTypeReportEntryDto first = result.contributors().get(0);
        assertEquals("admin", first.username());
        assertEquals(8, first.currentCount());
        assertEquals(2, first.previousCount());
        assertEquals(2, first.eventTypes().size());
        assertEquals("RM_RECORD_DECLARED", first.eventTypes().get(0).eventType());
        assertEquals("DECLARED", first.eventTypes().get(0).family());
        assertEquals(5, first.eventTypes().get(0).currentCount());
        assertEquals(2, first.eventTypes().get(0).previousCount());
        assertEquals("RM_FILE_PLAN_MOVED", first.eventTypes().get(1).eventType());
        assertEquals("GOVERNANCE_CHANGE", first.eventTypes().get(1).family());

        RecordsManagementService.ActivityContributorEventTypeReportEntryDto second = result.contributors().get(1);
        assertEquals("bob", second.username());
        assertEquals(0, second.currentCount());
        assertEquals(4, second.previousCount());
        assertEquals("RM_RECORD_CATEGORY_ASSIGNED", second.eventTypes().get(0).eventType());

        RecordsManagementService.ActivityContributorEventTypeReportEntryDto third = result.contributors().get(2);
        assertNull(third.username());
        assertEquals("(System)", third.label());
        assertEquals("RM_RECORD_UNDECLARE_BLOCKED", third.eventTypes().get(0).eventType());
        assertEquals("OTHER", third.eventTypes().get(0).family());
    }

    @Test
    @DisplayName("getActivityContributorEventTypeHighlights uses defaults and clamps limits")
    void getActivityContributorEventTypeHighlightsUsesDefaultsAndClampsLimits() {
        LocalDate today = LocalDate.now();
        LocalDate currentStartDay = today.minusDays(6);
        LocalDate previousEndDay = currentStartDay.minusDays(1);
        LocalDate previousStartDay = previousEndDay.minusDays(6);
        LocalDateTime currentFrom = currentStartDay.atStartOfDay();
        LocalDateTime currentTo = today.atTime(23, 59, 59);
        LocalDateTime previousFrom = previousStartDay.atStartOfDay();
        LocalDateTime previousTo = previousEndDay.atTime(23, 59, 59);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByUsernameAndTypeBetween(currentFrom, currentTo)).thenReturn(List.of());
        when(auditLogRepository.countRmEventsByUsernameAndTypeBetween(previousFrom, previousTo)).thenReturn(List.of());

        RecordsManagementService.ActivityContributorEventTypeHighlightsDto result =
            service.getActivityContributorEventTypeHighlights(null, 99, 99);

        assertEquals(7, result.windowDays());
        assertEquals(50, result.limit());
        assertEquals(10, result.eventTypeLimit());
        assertTrue(result.contributors().isEmpty());
    }

    @Test
    @DisplayName("getActivityContributorFamilyHighlights compares current and previous contributor families")
    void getActivityContributorFamilyHighlightsComparesWindows() {
        LocalDate today = LocalDate.now();
        LocalDate currentStartDay = today.minusDays(6);
        LocalDate previousEndDay = currentStartDay.minusDays(1);
        LocalDate previousStartDay = previousEndDay.minusDays(6);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByUsernameAndTypeBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(List.of(
                new Object[]{"admin", "RM_RECORD_DECLARED", 5L, LocalDateTime.of(2026, 4, 15, 10, 0)},
                new Object[]{"admin", "RM_FILE_PLAN_MOVED", 3L, LocalDateTime.of(2026, 4, 14, 8, 0)},
                new Object[]{null, "RM_RECORD_UNDECLARE_BLOCKED", 1L, LocalDateTime.of(2026, 4, 13, 7, 0)}
            ), List.of(
                new Object[]{"admin", "RM_RECORD_DECLARED", 2L, LocalDateTime.of(2026, 4, 7, 18, 0)},
                new Object[]{"bob", "RM_RECORD_CATEGORY_ASSIGNED", 4L, LocalDateTime.of(2026, 4, 6, 11, 0)}
            ));

        RecordsManagementService.ActivityContributorFamilyHighlightsDto result =
            service.getActivityContributorFamilyHighlights(7, 5);

        assertEquals(7, result.windowDays());
        assertEquals(5, result.limit());
        assertEquals(currentStartDay.toString(), result.currentWindow().fromDay());
        assertEquals(today.toString(), result.currentWindow().toDay());
        assertEquals(previousStartDay.toString(), result.previousWindow().fromDay());
        assertEquals(previousEndDay.toString(), result.previousWindow().toDay());
        assertEquals(3, result.contributors().size());

        RecordsManagementService.ActivityContributorFamilyHighlightsEntryDto first = result.contributors().get(0);
        assertEquals("admin", first.username());
        assertEquals(8, first.currentCount());
        assertEquals(2, first.previousCount());
        assertEquals(2, first.families().size());
        assertEquals("DECLARED", first.families().get(0).family());
        assertEquals(5, first.families().get(0).currentCount());
        assertEquals(2, first.families().get(0).previousCount());
        assertEquals("GOVERNANCE_CHANGE", first.families().get(1).family());
        assertEquals(3, first.families().get(1).currentCount());

        RecordsManagementService.ActivityContributorFamilyHighlightsEntryDto second = result.contributors().get(1);
        assertEquals("bob", second.username());
        assertEquals(0, second.currentCount());
        assertEquals(4, second.previousCount());
        assertEquals(1, second.families().size());
        assertEquals("CATEGORY_ASSIGNED", second.families().get(0).family());

        RecordsManagementService.ActivityContributorFamilyHighlightsEntryDto third = result.contributors().get(2);
        assertNull(third.username());
        assertEquals("(System)", third.label());
        assertEquals(1, third.currentCount());
        assertEquals("OTHER", third.families().get(0).family());
    }

    @Test
    @DisplayName("getActivityContributorFamilyHighlights uses defaults and clamps limit")
    void getActivityContributorFamilyHighlightsUsesDefaultsAndClampsLimit() {
        LocalDate today = LocalDate.now();
        LocalDate currentStartDay = today.minusDays(6);
        LocalDate previousEndDay = currentStartDay.minusDays(1);
        LocalDate previousStartDay = previousEndDay.minusDays(6);
        LocalDateTime currentFrom = currentStartDay.atStartOfDay();
        LocalDateTime currentTo = today.atTime(23, 59, 59);
        LocalDateTime previousFrom = previousStartDay.atStartOfDay();
        LocalDateTime previousTo = previousEndDay.atTime(23, 59, 59);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByUsernameAndTypeBetween(currentFrom, currentTo)).thenReturn(List.of());
        when(auditLogRepository.countRmEventsByUsernameAndTypeBetween(previousFrom, previousTo)).thenReturn(List.of());

        RecordsManagementService.ActivityContributorFamilyHighlightsDto result =
            service.getActivityContributorFamilyHighlights(null, 99);

        assertEquals(7, result.windowDays());
        assertEquals(50, result.limit());
        assertTrue(result.contributors().isEmpty());
    }

    @Test
    @DisplayName("getActivityEventTypeTrend buckets tracked RM event types and preserves otherCount")
    void getActivityEventTypeTrendBucketsTrackedEventTypes() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate twoDaysAgo = today.minusDays(2);
        LocalDate threeDaysAgo = today.minusDays(3);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByTypeBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(List.of(
                new Object[]{"RM_RECORD_DECLARED", 5L, LocalDateTime.of(2026, 4, 15, 10, 0)},
                new Object[]{"RM_FILE_PLAN_MOVED", 4L, LocalDateTime.of(2026, 4, 14, 8, 0)},
                new Object[]{"RM_RECORD_UNDECLARE_BLOCKED", 2L, LocalDateTime.of(2026, 4, 13, 7, 0)}
            ));
        when(auditLogRepository.countRecordsManagementEventsByDaySince(any(LocalDateTime.class))).thenReturn(List.of(
            new Object[]{threeDaysAgo, "RM_RECORD_DECLARED", 2L},
            new Object[]{twoDaysAgo, "RM_FILE_PLAN_MOVED", 1L},
            new Object[]{yesterday, "RM_FILE_PLAN_MOVED", 3L},
            new Object[]{today, "RM_RECORD_DECLARED", 3L},
            new Object[]{today, "RM_RECORD_UNDECLARE_BLOCKED", 2L}
        ));

        RecordsManagementService.ActivityEventTypeTrendDto result = service.getActivityEventTypeTrend(7, 2, 2);

        assertEquals(7, result.days());
        assertEquals(2, result.bucketDays());
        assertEquals(2, result.limit());
        assertEquals(2, result.trackedEventTypes().size());
        assertEquals("RM_RECORD_DECLARED", result.trackedEventTypes().get(0).eventType());
        assertEquals("RM_FILE_PLAN_MOVED", result.trackedEventTypes().get(1).eventType());

        RecordsManagementService.ActivityEventTypeTrendBucketDto latestBucket = result.buckets().get(3);
        assertEquals(yesterday.toString() + " to " + today.toString(), latestBucket.label());
        assertEquals(2, latestBucket.activeDayCount());
        assertEquals(8, latestBucket.totalCount());
        assertEquals(2, latestBucket.otherCount());
        assertEquals(2, latestBucket.eventTypeCounts().size());
        assertEquals("RM_RECORD_DECLARED", latestBucket.eventTypeCounts().get(0).eventType());
        assertEquals(3, latestBucket.eventTypeCounts().get(0).count());
        assertEquals("RM_FILE_PLAN_MOVED", latestBucket.eventTypeCounts().get(1).eventType());
        assertEquals(3, latestBucket.eventTypeCounts().get(1).count());
    }

    @Test
    @DisplayName("getActivityEventTypeTrend clamps days bucket size and limit")
    void getActivityEventTypeTrendClampsInputs() {
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByTypeBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(List.of());
        when(auditLogRepository.countRecordsManagementEventsByDaySince(any(LocalDateTime.class))).thenReturn(List.of());

        RecordsManagementService.ActivityEventTypeTrendDto result = service.getActivityEventTypeTrend(1, 99, 99);

        assertEquals(7, result.days());
        assertEquals(7, result.bucketDays());
        assertEquals(20, result.limit());
        assertTrue(result.trackedEventTypes().isEmpty());
        assertEquals(1, result.buckets().size());
    }

    @Test
    @DisplayName("undeclareRecord blocks nodes governed by file plan")
    void undeclareRecordBlocksFilePlanGovernance() {
        UUID nodeId = UUID.randomUUID();
        Document document = document(nodeId, "/Corporate File Plan/Contracts/report.pdf");
        document.addAspect(RecordsManagementService.RECORD_ASPECT);

        Folder filePlan = new Folder();
        filePlan.setId(UUID.randomUUID());
        filePlan.setName("Corporate File Plan");
        filePlan.setPath("/Corporate File Plan");
        filePlan.setFolderType(Folder.FolderType.FILE_PLAN);
        filePlan.setDeleted(false);
        filePlan.setArchiveStatus(Node.ArchiveStatus.LIVE);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(documentRepository.findById(nodeId)).thenReturn(Optional.of(document));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);
        when(folderRepository.findActiveFoldersByType(Folder.FolderType.FILE_PLAN)).thenReturn(List.of(filePlan));
        when(tenantWorkspaceScopeService.isPathVisible("/Corporate File Plan")).thenReturn(true);

        IllegalOperationException ex = assertThrows(
            IllegalOperationException.class,
            () -> service.undeclareRecord(nodeId, new RecordsManagementService.UndeclareRecordRequest("Administrative correction"))
        );

        assertEquals(
            "Cannot undeclare because node 'report.pdf' is governed by file plan 'Corporate File Plan'",
            ex.getMessage()
        );
        verify(auditService).logEvent(
            org.mockito.ArgumentMatchers.eq("RM_RECORD_UNDECLARE_BLOCKED"),
            org.mockito.ArgumentMatchers.eq(nodeId),
            org.mockito.ArgumentMatchers.eq("report.pdf"),
            org.mockito.ArgumentMatchers.eq("admin"),
            org.mockito.ArgumentMatchers.contains("governed by file plan 'Corporate File Plan'")
        );
    }

    @Test
    @DisplayName("assignRecordCategory binds a record category to declared record metadata")
    void assignRecordCategoryBindsMetadata() {
        UUID nodeId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Document document = document(nodeId, "/Sites/Finance/report.pdf");
        document.addAspect(RecordsManagementService.RECORD_ASPECT);
        Category root = recordCategory(UUID.randomUUID(), RecordsManagementService.RECORD_CATEGORY_ROOT_PATH, "Records Management", null);
        Category category = recordCategory(categoryId, "/Records Management/Contracts", "Contracts", root);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(documentRepository.findById(nodeId)).thenReturn(Optional.of(document));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.findFirstByPathAndActiveTrue(RecordsManagementService.RECORD_CATEGORY_ROOT_PATH))
            .thenReturn(Optional.of(root));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);

        RecordsManagementService.RecordDeclarationDto dto = service.assignRecordCategory(nodeId, categoryId);

        assertEquals(categoryId, dto.recordCategoryId());
        assertEquals("Contracts", dto.recordCategoryName());
        assertEquals("/Records Management/Contracts", dto.recordCategoryPath());
        assertEquals(Set.of(category), document.getCategories());
        assertEquals(categoryId.toString(), document.getProperties().get(RecordsManagementService.RECORD_CATEGORY_ID_PROPERTY));
    }

    @Test
    @DisplayName("createFilePlan delegates folder creation as FILE_PLAN")
    void createFilePlanDelegatesFolderCreationAsFilePlan() {
        UUID folderId = UUID.randomUUID();
        Folder folder = new Folder();
        folder.setId(folderId);
        folder.setName("Corporate File Plan");
        folder.setFolderType(Folder.FolderType.FILE_PLAN);
        folder.setPath("/Corporate File Plan");

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(folderService.createFolder(any(FolderService.CreateFolderRequest.class))).thenReturn(folder);

        RecordsManagementService.FilePlanDto dto = service.createFilePlan(
            new RecordsManagementService.CreateFilePlanRequest("Corporate File Plan", "RM root", null)
        );

        assertEquals(folderId, dto.folderId());
        assertEquals("/Corporate File Plan", dto.path());
        verify(folderService).createFolder(any(FolderService.CreateFolderRequest.class));
    }

    @Test
    @DisplayName("updateFilePlan updates description without renaming the file plan")
    void updateFilePlanUpdatesDescription() {
        UUID folderId = UUID.randomUUID();
        Folder filePlan = new Folder();
        filePlan.setId(folderId);
        filePlan.setName("Corporate File Plan");
        filePlan.setDescription("Old description");
        filePlan.setPath("/Corporate File Plan");
        filePlan.setFolderType(Folder.FolderType.FILE_PLAN);
        filePlan.setArchiveStatus(Node.ArchiveStatus.LIVE);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(folderService.getFolder(folderId)).thenReturn(filePlan);
        when(folderRepository.save(any(Folder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RecordsManagementService.FilePlanDto dto = service.updateFilePlan(
            folderId,
            new RecordsManagementService.UpdateFilePlanRequest("  Updated description  ")
        );

        assertEquals("Corporate File Plan", dto.name());
        assertEquals("Updated description", dto.description());
        verify(eventPublisher).publishEvent(any(NodeUpdatedEvent.class));
        verify(auditService).logEvent(
            org.mockito.ArgumentMatchers.eq("RM_FILE_PLAN_UPDATED"),
            org.mockito.ArgumentMatchers.eq(folderId),
            org.mockito.ArgumentMatchers.eq("Corporate File Plan"),
            org.mockito.ArgumentMatchers.eq("admin"),
            org.mockito.ArgumentMatchers.eq("Updated file plan description")
        );
    }

    @Test
    @DisplayName("renameFilePlan repairs descendant paths and requests subtree reindex")
    void renameFilePlanRepairsDescendantPaths() {
        UUID folderId = UUID.randomUUID();
        Folder filePlan = new Folder();
        filePlan.setId(folderId);
        filePlan.setName("Corporate File Plan");
        filePlan.setDescription("RM root");
        filePlan.setPath("/Corporate File Plan");
        filePlan.setFolderType(Folder.FolderType.FILE_PLAN);
        filePlan.setArchiveStatus(Node.ArchiveStatus.LIVE);

        Folder renamed = new Folder();
        renamed.setId(folderId);
        renamed.setName("HR File Plan");
        renamed.setDescription("RM root");
        renamed.setPath("/HR File Plan");
        renamed.setFolderType(Folder.FolderType.FILE_PLAN);
        renamed.setArchiveStatus(Node.ArchiveStatus.LIVE);

        Folder childFolder = new Folder();
        childFolder.setId(UUID.randomUUID());
        childFolder.setName("Contracts");
        childFolder.setParent(renamed);
        childFolder.setPath("/Corporate File Plan/Contracts");
        childFolder.setFolderType(Folder.FolderType.GENERAL);
        childFolder.setArchiveStatus(Node.ArchiveStatus.LIVE);

        Document grandchild = document(UUID.randomUUID(), "/Corporate File Plan/Contracts/report.pdf");
        grandchild.setName("report.pdf");
        grandchild.setParent(childFolder);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(folderService.getFolder(folderId)).thenReturn(filePlan);
        when(folderService.updateFolder(org.mockito.ArgumentMatchers.eq(folderId), any(FolderService.UpdateFolderRequest.class)))
            .thenReturn(renamed);
        when(nodeRepository.findByParentIdAndDeletedFalse(folderId)).thenReturn(List.of(childFolder));
        when(nodeRepository.findByParentIdAndDeletedFalse(childFolder.getId())).thenReturn(List.of(grandchild));
        when(nodeRepository.save(any(Node.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RecordsManagementService.FilePlanDto dto = service.renameFilePlan(
            folderId,
            new RecordsManagementService.RenameFilePlanRequest("HR File Plan")
        );

        assertEquals("HR File Plan", dto.name());
        assertEquals("/HR File Plan/Contracts", childFolder.getPath());
        assertEquals("/HR File Plan/Contracts/report.pdf", grandchild.getPath());
        verify(eventPublisher).publishEvent(any(com.ecm.core.event.NodeSubtreeReindexRequestedEvent.class));
        verify(auditService).logEvent(
            org.mockito.ArgumentMatchers.eq("RM_FILE_PLAN_RENAMED"),
            org.mockito.ArgumentMatchers.eq(folderId),
            org.mockito.ArgumentMatchers.eq("HR File Plan"),
            org.mockito.ArgumentMatchers.eq("admin"),
            org.mockito.ArgumentMatchers.contains("Renamed file plan from 'Corporate File Plan'")
        );
    }

    @Test
    @DisplayName("moveFilePlan validates target parent and delegates to node move semantics")
    void moveFilePlanDelegatesToNodeMove() {
        UUID folderId = UUID.randomUUID();
        UUID targetParentId = UUID.randomUUID();

        Folder filePlan = new Folder();
        filePlan.setId(folderId);
        filePlan.setName("Corporate File Plan");
        filePlan.setPath("/Corporate File Plan");
        filePlan.setFolderType(Folder.FolderType.FILE_PLAN);
        filePlan.setArchiveStatus(Node.ArchiveStatus.LIVE);

        Folder targetParent = new Folder();
        targetParent.setId(targetParentId);
        targetParent.setName("Company Home");
        targetParent.setPath("/Company Home");
        targetParent.setFolderType(Folder.FolderType.WORKSPACE);
        targetParent.setArchiveStatus(Node.ArchiveStatus.LIVE);

        Folder moved = new Folder();
        moved.setId(folderId);
        moved.setName("Corporate File Plan");
        moved.setPath("/Company Home/Corporate File Plan");
        moved.setParent(targetParent);
        moved.setFolderType(Folder.FolderType.FILE_PLAN);
        moved.setArchiveStatus(Node.ArchiveStatus.LIVE);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(folderService.getFolder(folderId)).thenReturn(filePlan);
        when(folderService.getFolder(targetParentId)).thenReturn(targetParent);
        when(nodeService.moveNode(folderId, targetParentId)).thenReturn(moved);

        RecordsManagementService.FilePlanDto dto = service.moveFilePlan(
            folderId,
            new RecordsManagementService.MoveFilePlanRequest(targetParentId)
        );

        assertEquals("/Company Home/Corporate File Plan", dto.path());
        verify(nodeService).moveNode(folderId, targetParentId);
        verify(auditService).logEvent(
            org.mockito.ArgumentMatchers.eq("RM_FILE_PLAN_MOVED"),
            org.mockito.ArgumentMatchers.eq(folderId),
            org.mockito.ArgumentMatchers.eq("Corporate File Plan"),
            org.mockito.ArgumentMatchers.eq("admin"),
            org.mockito.ArgumentMatchers.eq("Moved file plan from /Corporate File Plan to /Company Home/Corporate File Plan")
        );
    }

    @Test
    @DisplayName("moveFilePlan rejects target parents outside RM placement rules")
    void moveFilePlanRejectsInvalidParent() {
        UUID folderId = UUID.randomUUID();
        UUID targetParentId = UUID.randomUUID();

        Folder filePlan = new Folder();
        filePlan.setId(folderId);
        filePlan.setName("Corporate File Plan");
        filePlan.setPath("/Corporate File Plan");
        filePlan.setFolderType(Folder.FolderType.FILE_PLAN);
        filePlan.setArchiveStatus(Node.ArchiveStatus.LIVE);

        Folder invalidParent = new Folder();
        invalidParent.setId(targetParentId);
        invalidParent.setName("Contracts");
        invalidParent.setPath("/Sites/contracts");
        invalidParent.setFolderType(Folder.FolderType.GENERAL);
        invalidParent.setArchiveStatus(Node.ArchiveStatus.LIVE);
        Folder invalidAncestor = new Folder();
        invalidAncestor.setId(UUID.randomUUID());
        invalidAncestor.setName("Sites");
        invalidAncestor.setPath("/Sites");
        invalidAncestor.setFolderType(Folder.FolderType.GENERAL);
        invalidAncestor.setArchiveStatus(Node.ArchiveStatus.LIVE);
        invalidParent.setParent(invalidAncestor);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(folderService.getFolder(folderId)).thenReturn(filePlan);
        when(folderService.getFolder(targetParentId)).thenReturn(invalidParent);

        IllegalOperationException ex = assertThrows(
            IllegalOperationException.class,
            () -> service.moveFilePlan(folderId, new RecordsManagementService.MoveFilePlanRequest(targetParentId))
        );

        assertEquals(
            "File plans can only be created at the root, under workspace/system roots, or inside another file plan",
            ex.getMessage()
        );
    }

    @Test
    @DisplayName("deleteFilePlan rejects non-empty file plans")
    void deleteFilePlanRejectsNonEmptyFilePlan() {
        UUID folderId = UUID.randomUUID();
        Folder filePlan = new Folder();
        filePlan.setId(folderId);
        filePlan.setName("Corporate File Plan");
        filePlan.setPath("/Corporate File Plan");
        filePlan.setFolderType(Folder.FolderType.FILE_PLAN);
        filePlan.setArchiveStatus(Node.ArchiveStatus.LIVE);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(folderService.getFolder(folderId)).thenReturn(filePlan);
        when(folderRepository.countChildren(folderId)).thenReturn(1L);

        IllegalOperationException ex = assertThrows(
            IllegalOperationException.class,
            () -> service.deleteFilePlan(folderId)
        );

        assertEquals("Cannot delete file plan 'Corporate File Plan' because it is not empty", ex.getMessage());
    }

    @Test
    @DisplayName("deleteFilePlan rejects file plans with disposition schedules")
    void deleteFilePlanRejectsDispositionSchedule() {
        UUID folderId = UUID.randomUUID();
        Folder filePlan = new Folder();
        filePlan.setId(folderId);
        filePlan.setName("Corporate File Plan");
        filePlan.setPath("/Corporate File Plan");
        filePlan.setFolderType(Folder.FolderType.FILE_PLAN);
        filePlan.setArchiveStatus(Node.ArchiveStatus.LIVE);
        DispositionSchedule schedule = new DispositionSchedule();
        schedule.setFolder(filePlan);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(folderService.getFolder(folderId)).thenReturn(filePlan);
        when(folderRepository.countChildren(folderId)).thenReturn(0L);
        when(dispositionScheduleRepository.findByFolderId(folderId)).thenReturn(Optional.of(schedule));

        IllegalOperationException ex = assertThrows(
            IllegalOperationException.class,
            () -> service.deleteFilePlan(folderId)
        );

        assertEquals(
            "Cannot delete file plan 'Corporate File Plan' because it has a disposition schedule",
            ex.getMessage()
        );
    }

    @Test
    @DisplayName("deleteFilePlan deletes empty unmanaged file plans")
    void deleteFilePlanDeletesEmptyFilePlan() {
        UUID folderId = UUID.randomUUID();
        Folder filePlan = new Folder();
        filePlan.setId(folderId);
        filePlan.setName("Corporate File Plan");
        filePlan.setPath("/Corporate File Plan");
        filePlan.setFolderType(Folder.FolderType.FILE_PLAN);
        filePlan.setArchiveStatus(Node.ArchiveStatus.LIVE);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(folderService.getFolder(folderId)).thenReturn(filePlan);
        when(folderRepository.countChildren(folderId)).thenReturn(0L);
        when(dispositionScheduleRepository.findByFolderId(folderId)).thenReturn(Optional.empty());
        when(archivePolicyRepository.findByFolderId(folderId)).thenReturn(Optional.empty());

        service.deleteFilePlan(folderId);

        verify(folderService).deleteFolder(folderId, false, false);
        verify(auditService).logEvent(
            org.mockito.ArgumentMatchers.eq("RM_FILE_PLAN_DELETED"),
            org.mockito.ArgumentMatchers.eq(folderId),
            org.mockito.ArgumentMatchers.eq("Corporate File Plan"),
            org.mockito.ArgumentMatchers.eq("admin"),
            org.mockito.ArgumentMatchers.eq("Deleted empty file plan")
        );
    }

    @Test
    @DisplayName("updateRecordCategory updates description for non-root categories")
    void updateRecordCategoryUpdatesDescription() {
        UUID categoryId = UUID.randomUUID();
        Category root = recordCategory(UUID.randomUUID(), RecordsManagementService.RECORD_CATEGORY_ROOT_PATH, "Records Management", null);
        Category category = recordCategory(categoryId, "/Records Management/Contracts", "Contracts", root);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.findFirstByPathAndActiveTrue(RecordsManagementService.RECORD_CATEGORY_ROOT_PATH))
            .thenReturn(Optional.of(root));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RecordsManagementService.RecordCategoryDto dto = service.updateRecordCategory(
            categoryId,
            new RecordsManagementService.UpdateRecordCategoryRequest("  Updated description  ")
        );

        assertEquals("Updated description", dto.description());
        verify(auditService).logEvent(
            org.mockito.ArgumentMatchers.eq("RM_RECORD_CATEGORY_UPDATED"),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.eq("Contracts"),
            org.mockito.ArgumentMatchers.eq("admin"),
            org.mockito.ArgumentMatchers.eq("Updated record category at path /Records Management/Contracts")
        );
    }

    @Test
    @DisplayName("updateRecordCategory rejects the RM root category")
    void updateRecordCategoryRejectsRootCategory() {
        UUID categoryId = UUID.randomUUID();
        Category root = recordCategory(categoryId, RecordsManagementService.RECORD_CATEGORY_ROOT_PATH, "Records Management", null);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(root));
        when(categoryRepository.findFirstByPathAndActiveTrue(RecordsManagementService.RECORD_CATEGORY_ROOT_PATH))
            .thenReturn(Optional.of(root));

        IllegalOperationException ex = assertThrows(
            IllegalOperationException.class,
            () -> service.updateRecordCategory(categoryId, new RecordsManagementService.UpdateRecordCategoryRequest("Updated"))
        );

        assertEquals("The record category root cannot be modified", ex.getMessage());
    }

    @Test
    @DisplayName("deleteRecordCategory rejects categories with child categories")
    void deleteRecordCategoryRejectsNonLeaf() {
        UUID categoryId = UUID.randomUUID();
        Category root = recordCategory(UUID.randomUUID(), RecordsManagementService.RECORD_CATEGORY_ROOT_PATH, "Records Management", null);
        Category category = recordCategory(categoryId, "/Records Management/Contracts", "Contracts", root);
        Category child = recordCategory(UUID.randomUUID(), "/Records Management/Contracts/Executed", "Executed", category);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.findFirstByPathAndActiveTrue(RecordsManagementService.RECORD_CATEGORY_ROOT_PATH))
            .thenReturn(Optional.of(root));
        when(categoryRepository.findByParentAndActiveTrue(category)).thenReturn(List.of(child));

        IllegalOperationException ex = assertThrows(
            IllegalOperationException.class,
            () -> service.deleteRecordCategory(categoryId)
        );

        assertEquals(
            "Cannot delete record category 'Contracts' because it has child categories",
            ex.getMessage()
        );
    }

    @Test
    @DisplayName("deleteRecordCategory rejects categories assigned to nodes")
    void deleteRecordCategoryRejectsAssignedNodes() {
        UUID categoryId = UUID.randomUUID();
        Category root = recordCategory(UUID.randomUUID(), RecordsManagementService.RECORD_CATEGORY_ROOT_PATH, "Records Management", null);
        Category category = recordCategory(categoryId, "/Records Management/Contracts", "Contracts", root);
        Document assigned = document(UUID.randomUUID(), "/Sites/Finance/report.pdf");

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.findFirstByPathAndActiveTrue(RecordsManagementService.RECORD_CATEGORY_ROOT_PATH))
            .thenReturn(Optional.of(root));
        when(categoryRepository.findByParentAndActiveTrue(category)).thenReturn(List.of());
        when(nodeRepository.findByCategoriesInAndDeletedFalse(Set.of(category))).thenReturn(List.of(assigned));

        IllegalOperationException ex = assertThrows(
            IllegalOperationException.class,
            () -> service.deleteRecordCategory(categoryId)
        );

        assertEquals(
            "Cannot delete record category 'Contracts' because it is assigned to node(s)",
            ex.getMessage()
        );
    }

    @Test
    @DisplayName("deleteRecordCategory deletes unused leaf categories")
    void deleteRecordCategoryDeletesUnusedLeaf() {
        UUID categoryId = UUID.randomUUID();
        Category root = recordCategory(UUID.randomUUID(), RecordsManagementService.RECORD_CATEGORY_ROOT_PATH, "Records Management", null);
        Category category = recordCategory(categoryId, "/Records Management/Contracts", "Contracts", root);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.findFirstByPathAndActiveTrue(RecordsManagementService.RECORD_CATEGORY_ROOT_PATH))
            .thenReturn(Optional.of(root));
        when(categoryRepository.findByParentAndActiveTrue(category)).thenReturn(List.of());
        when(nodeRepository.findByCategoriesInAndDeletedFalse(Set.of(category))).thenReturn(List.of());

        service.deleteRecordCategory(categoryId);

        verify(categoryRepository).delete(category);
        verify(auditService).logEvent(
            org.mockito.ArgumentMatchers.eq("RM_RECORD_CATEGORY_DELETED"),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.eq("Contracts"),
            org.mockito.ArgumentMatchers.eq("admin"),
            org.mockito.ArgumentMatchers.eq("Deleted record category at path /Records Management/Contracts")
        );
    }

    @Test
    @DisplayName("renameRecordCategory repairs descendant paths and declared-record metadata")
    void renameRecordCategoryRepairsDescendantsAndRecordMetadata() {
        UUID categoryId = UUID.randomUUID();
        UUID childCategoryId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();

        Category root = recordCategory(rootId, RecordsManagementService.RECORD_CATEGORY_ROOT_PATH, "Records Management", null);
        Category category = recordCategory(categoryId, "/Records Management/Contracts", "Contracts", root);
        Category child = recordCategory(childCategoryId, "/Records Management/Contracts/Executed", "Executed", category);

        Document assignedToRoot = document(UUID.randomUUID(), "/Sites/Finance/root.pdf");
        assignedToRoot.addAspect(RecordsManagementService.RECORD_ASPECT);
        assignedToRoot.getCategories().add(category);
        assignedToRoot.getProperties().put(RecordsManagementService.RECORD_CATEGORY_ID_PROPERTY, categoryId.toString());
        assignedToRoot.getProperties().put(RecordsManagementService.RECORD_CATEGORY_NAME_PROPERTY, "Contracts");
        assignedToRoot.getProperties().put(RecordsManagementService.RECORD_CATEGORY_PATH_PROPERTY, "/Records Management/Contracts");

        Document assignedToChild = document(UUID.randomUUID(), "/Sites/Finance/child.pdf");
        assignedToChild.addAspect(RecordsManagementService.RECORD_ASPECT);
        assignedToChild.getCategories().add(child);
        assignedToChild.getProperties().put(RecordsManagementService.RECORD_CATEGORY_ID_PROPERTY, childCategoryId.toString());
        assignedToChild.getProperties().put(RecordsManagementService.RECORD_CATEGORY_NAME_PROPERTY, "Executed");
        assignedToChild.getProperties().put(RecordsManagementService.RECORD_CATEGORY_PATH_PROPERTY, "/Records Management/Contracts/Executed");

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.findFirstByPathAndActiveTrue(RecordsManagementService.RECORD_CATEGORY_ROOT_PATH))
            .thenReturn(Optional.of(root));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryRepository.findByParentAndActiveTrue(category)).thenReturn(List.of(child));
        when(categoryRepository.findByParentAndActiveTrue(child)).thenReturn(List.of());
        when(nodeRepository.findByCategoryIds(org.mockito.ArgumentMatchers.anyList()))
            .thenReturn(List.of(assignedToRoot, assignedToChild));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RecordsManagementService.RecordCategoryDto dto = service.renameRecordCategory(
            categoryId,
            new RecordsManagementService.RenameRecordCategoryRequest("Agreements")
        );

        assertEquals("Agreements", dto.name());
        assertEquals("/Records Management/Agreements", dto.path());
        assertEquals("/Records Management/Agreements/Executed", child.getPath());
        assertEquals("Agreements", assignedToRoot.getProperties().get(RecordsManagementService.RECORD_CATEGORY_NAME_PROPERTY));
        assertEquals("/Records Management/Agreements", assignedToRoot.getProperties().get(RecordsManagementService.RECORD_CATEGORY_PATH_PROPERTY));
        assertEquals("/Records Management/Agreements/Executed", assignedToChild.getProperties().get(RecordsManagementService.RECORD_CATEGORY_PATH_PROPERTY));
        verify(eventPublisher).publishEvent(any(NodesReindexRequestedEvent.class));
        verify(auditService).logEvent(
            org.mockito.ArgumentMatchers.eq("RM_RECORD_CATEGORY_RENAMED"),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.eq("Agreements"),
            org.mockito.ArgumentMatchers.eq("admin"),
            org.mockito.ArgumentMatchers.contains("repaired 2 declared record(s)")
        );
    }

    @Test
    @DisplayName("moveRecordCategory repairs subtree paths and metadata under new parent")
    void moveRecordCategoryRepairsSubtreeAndMetadata() {
        UUID categoryId = UUID.randomUUID();
        UUID childCategoryId = UUID.randomUUID();
        UUID targetParentId = UUID.randomUUID();

        Category root = recordCategory(UUID.randomUUID(), RecordsManagementService.RECORD_CATEGORY_ROOT_PATH, "Records Management", null);
        Category category = recordCategory(categoryId, "/Records Management/Contracts", "Contracts", root);
        Category child = recordCategory(childCategoryId, "/Records Management/Contracts/Executed", "Executed", category);
        Category targetParent = recordCategory(targetParentId, "/Records Management/Finance", "Finance", root);

        Document assignedToChild = document(UUID.randomUUID(), "/Sites/Finance/child.pdf");
        assignedToChild.addAspect(RecordsManagementService.RECORD_ASPECT);
        assignedToChild.getCategories().add(child);
        assignedToChild.getProperties().put(RecordsManagementService.RECORD_CATEGORY_ID_PROPERTY, childCategoryId.toString());
        assignedToChild.getProperties().put(RecordsManagementService.RECORD_CATEGORY_NAME_PROPERTY, "Executed");
        assignedToChild.getProperties().put(RecordsManagementService.RECORD_CATEGORY_PATH_PROPERTY, "/Records Management/Contracts/Executed");

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.findById(targetParentId)).thenReturn(Optional.of(targetParent));
        when(categoryRepository.findFirstByPathAndActiveTrue(RecordsManagementService.RECORD_CATEGORY_ROOT_PATH))
            .thenReturn(Optional.of(root));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryRepository.findByParentAndActiveTrue(category)).thenReturn(List.of(child));
        when(categoryRepository.findByParentAndActiveTrue(child)).thenReturn(List.of());
        when(nodeRepository.findByCategoryIds(org.mockito.ArgumentMatchers.anyList()))
            .thenReturn(List.of(assignedToChild));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RecordsManagementService.RecordCategoryDto dto = service.moveRecordCategory(
            categoryId,
            new RecordsManagementService.MoveRecordCategoryRequest(targetParentId)
        );

        assertEquals("/Records Management/Finance/Contracts", dto.path());
        assertEquals("/Records Management/Finance/Contracts/Executed", child.getPath());
        assertEquals("/Records Management/Finance/Contracts/Executed",
            assignedToChild.getProperties().get(RecordsManagementService.RECORD_CATEGORY_PATH_PROPERTY));
        verify(eventPublisher).publishEvent(any(NodesReindexRequestedEvent.class));
        verify(auditService).logEvent(
            org.mockito.ArgumentMatchers.eq("RM_RECORD_CATEGORY_MOVED"),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.eq("Contracts"),
            org.mockito.ArgumentMatchers.eq("admin"),
            org.mockito.ArgumentMatchers.contains("repaired 1 declared record(s)")
        );
    }

    @Test
    @DisplayName("moveRecordCategory rejects moves under the category subtree")
    void moveRecordCategoryRejectsCycles() {
        UUID categoryId = UUID.randomUUID();
        UUID childCategoryId = UUID.randomUUID();

        Category root = recordCategory(UUID.randomUUID(), RecordsManagementService.RECORD_CATEGORY_ROOT_PATH, "Records Management", null);
        Category category = recordCategory(categoryId, "/Records Management/Contracts", "Contracts", root);
        Category child = recordCategory(childCategoryId, "/Records Management/Contracts/Executed", "Executed", category);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.findById(childCategoryId)).thenReturn(Optional.of(child));
        when(categoryRepository.findFirstByPathAndActiveTrue(RecordsManagementService.RECORD_CATEGORY_ROOT_PATH))
            .thenReturn(Optional.of(root));

        IllegalOperationException ex = assertThrows(
            IllegalOperationException.class,
            () -> service.moveRecordCategory(categoryId, new RecordsManagementService.MoveRecordCategoryRequest(childCategoryId))
        );

        assertEquals("Cannot move record category 'Contracts' under its own subtree", ex.getMessage());
    }

    @Test
    @DisplayName("getSummary reports uncategorized and outside-file-plan counts")
    void getSummaryReportsBreakdown() {
        Document categorized = document(UUID.randomUUID(), "/Corporate File Plan/Contracts/report.pdf");
        categorized.addAspect(RecordsManagementService.RECORD_ASPECT);
        Category category = recordCategory(UUID.randomUUID(), "/Records Management/Contracts", "Contracts", null);
        categorized.getCategories().add(category);
        categorized.getProperties().put(RecordsManagementService.RECORD_CATEGORY_PATH_PROPERTY, category.getPath());

        Document uncategorized = document(UUID.randomUUID(), "/Loose/report-2.pdf");
        uncategorized.addAspect(RecordsManagementService.RECORD_ASPECT);

        Folder filePlan = new Folder();
        filePlan.setId(UUID.randomUUID());
        filePlan.setName("Corporate File Plan");
        filePlan.setPath("/Corporate File Plan");
        filePlan.setFolderType(Folder.FolderType.FILE_PLAN);
        filePlan.setDeleted(false);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(nodeRepository.findByAspectNameAndDeletedFalse(RecordsManagementService.RECORD_ASPECT))
            .thenReturn(List.of(categorized, uncategorized));
        when(folderRepository.findActiveFoldersByType(Folder.FolderType.FILE_PLAN)).thenReturn(List.of(filePlan));
        when(categoryRepository.findByPurposeAndActiveTrueOrderByPathAsc(Category.Purpose.RECORD))
            .thenReturn(List.of(category));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);
        when(tenantWorkspaceScopeService.isPathVisible("/Corporate File Plan")).thenReturn(true);

        RecordsManagementService.RecordsSummaryDto summary = service.getSummary();

        assertEquals(2, summary.declaredRecordCount());
        assertEquals(1, summary.filePlanCount());
        assertEquals(1, summary.recordCategoryCount());
        assertEquals(1, summary.uncategorizedRecordCount());
        assertEquals(1, summary.outsideFilePlanRecordCount());
        org.junit.jupiter.api.Assertions.assertTrue(
            summary.categoryBreakdown().stream().anyMatch(bucket -> "/Records Management/Contracts".equals(bucket.key()) && bucket.count() == 1)
        );
    }

    @Test
    @DisplayName("listAudit returns RM audit entries")
    void listAuditReturnsEntries() {
        UUID auditId = UUID.randomUUID();
        AuditLog auditLog = AuditLog.builder()
            .id(auditId)
            .eventType("RM_RECORD_DECLARED")
            .nodeId(UUID.randomUUID())
            .nodeName("report.pdf")
            .username("admin")
            .eventTime(LocalDateTime.of(2026, 4, 14, 20, 0))
            .details("Declared document as record")
            .build();

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.findRecordsManagementTimeline("RM_RECORD_DECLARED", "admin", null, null, PageRequest.of(0, 20)))
            .thenReturn(new PageImpl<>(List.of(auditLog), PageRequest.of(0, 20), 1));

        var page = service.listAudit("record_declared", "admin", null, null, null, PageRequest.of(0, 20));

        assertEquals(1, page.getTotalElements());
        assertEquals(auditId, page.getContent().get(0).auditLogId());
        assertEquals("RM_RECORD_DECLARED", page.getContent().get(0).eventType());
    }

    @Test
    @DisplayName("listAudit passes to parameter through to repository")
    void listAuditPassesToParameter() {
        LocalDateTime from = LocalDateTime.of(2026, 4, 10, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 4, 14, 23, 59, 59);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.findRecordsManagementTimeline(null, null, from, to, PageRequest.of(0, 20)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        var page = service.listAudit(null, null, from, to, null, PageRequest.of(0, 20));

        assertEquals(0, page.getTotalElements());
        verify(auditLogRepository).findRecordsManagementTimeline(null, null, from, to, PageRequest.of(0, 20));
    }

    @Test
    @DisplayName("listAudit with from and to forms a closed interval")
    void listAuditClosedInterval() {
        LocalDateTime from = LocalDateTime.of(2026, 4, 14, 10, 0);
        LocalDateTime to = LocalDateTime.of(2026, 4, 14, 10, 0);
        UUID auditId = UUID.randomUUID();
        AuditLog auditLog = AuditLog.builder()
            .id(auditId)
            .eventType("RM_RECORD_DECLARED")
            .nodeId(UUID.randomUUID())
            .nodeName("exact.pdf")
            .username("admin")
            .eventTime(from)
            .details("Exact boundary")
            .build();

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.findRecordsManagementTimeline(null, null, from, to, PageRequest.of(0, 20)))
            .thenReturn(new PageImpl<>(List.of(auditLog), PageRequest.of(0, 20), 1));

        var page = service.listAudit(null, null, from, to, null, PageRequest.of(0, 20));

        assertEquals(1, page.getTotalElements());
        assertEquals(auditId, page.getContent().get(0).auditLogId());
    }

    @Test
    @DisplayName("listAudit with family=DECLARED filters to declared events only")
    void listAuditWithFamilyDeclared() {
        UUID auditId = UUID.randomUUID();
        AuditLog auditLog = AuditLog.builder()
            .id(auditId)
            .eventType("RM_RECORD_DECLARED")
            .nodeId(UUID.randomUUID())
            .nodeName("report.pdf")
            .username("admin")
            .eventTime(LocalDateTime.of(2026, 4, 15, 10, 0))
            .details("Declared document as record")
            .build();

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.findByEventTypesAndFilters(
            List.of("RM_RECORD_DECLARED"), null, null, null, PageRequest.of(0, 20)))
            .thenReturn(new PageImpl<>(List.of(auditLog), PageRequest.of(0, 20), 1));

        var page = service.listAudit(null, null, null, null,
            RecordsManagementService.RmEventFamily.DECLARED, PageRequest.of(0, 20));

        assertEquals(1, page.getTotalElements());
        assertEquals("RM_RECORD_DECLARED", page.getContent().get(0).eventType());
    }

    @Test
    @DisplayName("listAudit with family=GOVERNANCE_CHANGE filters to governance events")
    void listAuditWithFamilyGovernanceChange() {
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.findByEventTypesAndFilters(any(), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        var page = service.listAudit(null, null, null, null,
            RecordsManagementService.RmEventFamily.GOVERNANCE_CHANGE, PageRequest.of(0, 20));

        verify(auditLogRepository).findByEventTypesAndFilters(
            org.mockito.ArgumentMatchers.argThat(types ->
                types.size() == 10
                && types.contains("RM_FILE_PLAN_CREATED")
                && types.contains("RM_RECORD_CATEGORY_DELETED")),
            any(), any(), any(), any());
        assertEquals(0, page.getTotalElements());
    }

    @Test
    @DisplayName("listAudit with family=OTHER filters to complementary RM events")
    void listAuditWithFamilyOther() {
        UUID auditId = UUID.randomUUID();
        AuditLog auditLog = AuditLog.builder()
            .id(auditId)
            .eventType("RM_RECORD_UNDECLARE_BLOCKED")
            .nodeId(UUID.randomUUID())
            .nodeName("report.pdf")
            .username("admin")
            .eventTime(LocalDateTime.of(2026, 4, 15, 12, 0))
            .details("Blocked undeclare")
            .build();

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.findOtherRecordsManagementTimeline(
            any(),
            org.mockito.ArgumentMatchers.eq(null),
            org.mockito.ArgumentMatchers.eq(null),
            org.mockito.ArgumentMatchers.eq(null),
            org.mockito.ArgumentMatchers.eq(null),
            any()
        )).thenReturn(new PageImpl<>(List.of(auditLog), PageRequest.of(0, 20), 1));

        var page = service.listAudit(null, null, null, null,
            RecordsManagementService.RmEventFamily.OTHER, PageRequest.of(0, 20));

        verify(auditLogRepository).findOtherRecordsManagementTimeline(
            org.mockito.ArgumentMatchers.argThat(types ->
                types.contains("RM_RECORD_DECLARED")
                    && types.contains("RM_RECORD_UNDECLARED")
                    && types.contains("RM_RECORD_CATEGORY_ASSIGNED")
                    && types.contains("RM_FILE_PLAN_CREATED")),
            org.mockito.ArgumentMatchers.eq(null),
            org.mockito.ArgumentMatchers.eq(null),
            org.mockito.ArgumentMatchers.eq(null),
            org.mockito.ArgumentMatchers.eq(null),
            org.mockito.ArgumentMatchers.eq(PageRequest.of(0, 20))
        );
        assertEquals(1, page.getTotalElements());
        assertEquals("RM_RECORD_UNDECLARE_BLOCKED", page.getContent().get(0).eventType());
    }

    @Test
    @DisplayName("listAudit with family and conflicting eventType returns empty page")
    void listAuditWithFamilyAndConflictingEventType() {
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);

        var page = service.listAudit("RM_FILE_PLAN_CREATED", null, null, null,
            RecordsManagementService.RmEventFamily.DECLARED, PageRequest.of(0, 20));

        assertEquals(0, page.getTotalElements());
    }

    @Test
    @DisplayName("listAudit with family and matching eventType narrows to that event type")
    void listAuditWithFamilyAndMatchingEventType() {
        UUID auditId = UUID.randomUUID();
        AuditLog auditLog = AuditLog.builder()
            .id(auditId)
            .eventType("RM_FILE_PLAN_CREATED")
            .nodeId(UUID.randomUUID())
            .nodeName("Corporate File Plan")
            .username("admin")
            .eventTime(LocalDateTime.of(2026, 4, 15, 11, 0))
            .details("Created file plan")
            .build();

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.findByEventTypesAndFilters(
            List.of("RM_FILE_PLAN_CREATED"), null, null, null, PageRequest.of(0, 20)))
            .thenReturn(new PageImpl<>(List.of(auditLog), PageRequest.of(0, 20), 1));

        var page = service.listAudit("FILE_PLAN_CREATED", null, null, null,
            RecordsManagementService.RmEventFamily.GOVERNANCE_CHANGE, PageRequest.of(0, 20));

        assertEquals(1, page.getTotalElements());
        assertEquals("RM_FILE_PLAN_CREATED", page.getContent().get(0).eventType());
    }

    @Test
    @DisplayName("listAudit with family=null preserves existing behavior")
    void listAuditWithNullFamilyPreservesExistingBehavior() {
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.findRecordsManagementTimeline(null, null, null, null, PageRequest.of(0, 20)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.listAudit(null, null, null, null, null, PageRequest.of(0, 20));

        verify(auditLogRepository).findRecordsManagementTimeline(null, null, null, null, PageRequest.of(0, 20));
    }

    @Test
    @DisplayName("getActivityEventTypeReport compares current and previous RM event types")
    void getActivityEventTypeReportComparesWindows() {
        LocalDateTime currentFrom = LocalDateTime.of(2026, 4, 1, 0, 0);
        LocalDateTime currentTo = LocalDateTime.of(2026, 4, 15, 23, 59, 59);
        LocalDateTime previousFrom = LocalDateTime.of(2026, 3, 17, 0, 0);
        LocalDateTime previousTo = LocalDateTime.of(2026, 3, 31, 23, 59, 59);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByTypeBetween(currentFrom, currentTo))
            .thenReturn(List.of(
                new Object[]{"RM_RECORD_DECLARED", 5L, LocalDateTime.of(2026, 4, 15, 10, 0)},
                new Object[]{"RM_FILE_PLAN_MOVED", 4L, LocalDateTime.of(2026, 4, 14, 8, 0)},
                new Object[]{"RM_RECORD_UNDECLARE_BLOCKED", 1L, LocalDateTime.of(2026, 4, 13, 7, 0)}
            ));
        when(auditLogRepository.countRmEventsByTypeBetween(previousFrom, previousTo))
            .thenReturn(List.of(
                new Object[]{"RM_RECORD_DECLARED", 2L, LocalDateTime.of(2026, 3, 30, 18, 0)},
                new Object[]{"RM_FILE_PLAN_MOVED", 2L, LocalDateTime.of(2026, 3, 28, 11, 0)}
            ));

        RecordsManagementService.ActivityEventTypeReportDto result = service.getActivityEventTypeReport(
            currentFrom,
            currentTo,
            8
        );

        assertEquals("2026-04-01T00:00", result.currentWindow().from());
        assertEquals("2026-03-17T00:00", result.previousWindow().from());
        assertEquals(8, result.limit());
        assertEquals(10, result.currentTotalCount());
        assertEquals(4, result.previousTotalCount());
        assertEquals(3, result.eventTypes().size());

        RecordsManagementService.ActivityEventTypeReportEntryDto first = result.eventTypes().get(0);
        assertEquals("RM_RECORD_DECLARED", first.eventType());
        assertEquals("DECLARED", first.family());
        assertEquals(5, first.currentCount());
        assertEquals(2, first.previousCount());
        assertEquals(3, first.delta());

        RecordsManagementService.ActivityEventTypeReportEntryDto second = result.eventTypes().get(1);
        assertEquals("RM_FILE_PLAN_MOVED", second.eventType());
        assertEquals("GOVERNANCE_CHANGE", second.family());
        assertEquals(4, second.currentCount());
        assertEquals(2, second.previousCount());

        RecordsManagementService.ActivityEventTypeReportEntryDto third = result.eventTypes().get(2);
        assertEquals("RM_RECORD_UNDECLARE_BLOCKED", third.eventType());
        assertEquals("OTHER", third.family());
        assertEquals(1, third.currentCount());
        assertEquals(0, third.previousCount());
    }

    @Test
    @DisplayName("getActivityEventTypeReport uses default closed range and clamps limit")
    void getActivityEventTypeReportUsesDefaults() {
        LocalDate today = LocalDate.now();
        LocalDate startDay = today.minusDays(27);
        LocalDateTime expectedFrom = startDay.atStartOfDay();
        LocalDateTime expectedTo = today.atTime(23, 59, 59);
        long spanSeconds = ChronoUnit.SECONDS.between(expectedFrom, expectedTo);
        LocalDateTime expectedPreviousTo = expectedFrom.minusSeconds(1);
        LocalDateTime expectedPreviousFrom = expectedPreviousTo.minusSeconds(spanSeconds);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByTypeBetween(expectedFrom, expectedTo)).thenReturn(List.of());
        when(auditLogRepository.countRmEventsByTypeBetween(expectedPreviousFrom, expectedPreviousTo)).thenReturn(List.of());

        RecordsManagementService.ActivityEventTypeReportDto result = service.getActivityEventTypeReport(null, null, 99);

        assertEquals(expectedFrom.toString(), result.currentWindow().from());
        assertEquals(expectedPreviousFrom.toString(), result.previousWindow().from());
        assertEquals(20, result.limit());
        assertTrue(result.eventTypes().isEmpty());
    }

    @Test
    @DisplayName("getActivityContributorReport compares current and previous RM contributors")
    void getActivityContributorReportComparesWindows() {
        LocalDateTime currentFrom = LocalDateTime.of(2026, 4, 1, 0, 0);
        LocalDateTime currentTo = LocalDateTime.of(2026, 4, 15, 23, 59, 59);
        LocalDateTime previousFrom = LocalDateTime.of(2026, 3, 17, 0, 0);
        LocalDateTime previousTo = LocalDateTime.of(2026, 3, 31, 23, 59, 59);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByUsernameAndTypeBetween(currentFrom, currentTo))
            .thenReturn(List.of(
                new Object[]{"admin", "RM_RECORD_DECLARED", 5L, LocalDateTime.of(2026, 4, 15, 10, 0)},
                new Object[]{"admin", "RM_FILE_PLAN_MOVED", 3L, LocalDateTime.of(2026, 4, 14, 8, 0)},
                new Object[]{null, "RM_RECORD_UNDECLARE_BLOCKED", 1L, LocalDateTime.of(2026, 4, 13, 7, 0)}
            ));
        when(auditLogRepository.countRmEventsByUsernameAndTypeBetween(previousFrom, previousTo))
            .thenReturn(List.of(
                new Object[]{"admin", "RM_RECORD_DECLARED", 2L, LocalDateTime.of(2026, 3, 30, 18, 0)},
                new Object[]{"bob", "RM_FILE_PLAN_MOVED", 4L, LocalDateTime.of(2026, 3, 28, 11, 0)}
            ));

        RecordsManagementService.ActivityContributorReportDto result = service.getActivityContributorReport(
            currentFrom,
            currentTo,
            5,
            3
        );

        assertEquals("2026-04-01T00:00", result.currentWindow().from());
        assertEquals("2026-03-17T00:00", result.previousWindow().from());
        assertEquals(5, result.limit());
        assertEquals(3, result.eventTypeLimit());
        assertEquals(9, result.currentTotalCount());
        assertEquals(6, result.previousTotalCount());
        assertEquals(3, result.contributors().size());

        RecordsManagementService.ActivityContributorReportEntryDto first = result.contributors().get(0);
        assertEquals("admin", first.username());
        assertEquals("admin", first.label());
        assertEquals(8, first.currentCount());
        assertEquals(2, first.previousCount());
        assertEquals(6, first.delta());
        assertEquals(2, first.currentTopEventTypes().size());
        assertEquals("RM_RECORD_DECLARED", first.currentTopEventTypes().get(0).eventType());
        assertEquals("DECLARED", first.currentTopEventTypes().get(0).family());

        RecordsManagementService.ActivityContributorReportEntryDto second = result.contributors().get(1);
        assertEquals("bob", second.username());
        assertEquals(0, second.currentCount());
        assertEquals(4, second.previousCount());
        assertTrue(second.currentTopEventTypes().isEmpty());

        RecordsManagementService.ActivityContributorReportEntryDto third = result.contributors().get(2);
        assertNull(third.username());
        assertEquals("(System)", third.label());
        assertEquals(1, third.currentCount());
        assertEquals(0, third.previousCount());
        assertEquals("RM_RECORD_UNDECLARE_BLOCKED", third.currentTopEventTypes().get(0).eventType());
    }

    @Test
    @DisplayName("getActivityContributorReport uses default closed range and clamps limits")
    void getActivityContributorReportUsesDefaults() {
        LocalDate today = LocalDate.now();
        LocalDate startDay = today.minusDays(27);
        LocalDateTime expectedFrom = startDay.atStartOfDay();
        LocalDateTime expectedTo = today.atTime(23, 59, 59);
        long spanSeconds = ChronoUnit.SECONDS.between(expectedFrom, expectedTo);
        LocalDateTime expectedPreviousTo = expectedFrom.minusSeconds(1);
        LocalDateTime expectedPreviousFrom = expectedPreviousTo.minusSeconds(spanSeconds);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByUsernameAndTypeBetween(expectedFrom, expectedTo)).thenReturn(List.of());
        when(auditLogRepository.countRmEventsByUsernameAndTypeBetween(expectedPreviousFrom, expectedPreviousTo))
            .thenReturn(List.of());

        RecordsManagementService.ActivityContributorReportDto result =
            service.getActivityContributorReport(null, null, 99, 99);

        assertEquals(expectedFrom.toString(), result.currentWindow().from());
        assertEquals(expectedPreviousFrom.toString(), result.previousWindow().from());
        assertEquals(50, result.limit());
        assertEquals(10, result.eventTypeLimit());
        assertTrue(result.contributors().isEmpty());
    }

    @Test
    @DisplayName("getActivityContributorEventTypeReport compares current and previous contributor event types")
    void getActivityContributorEventTypeReportComparesWindows() {
        LocalDateTime currentFrom = LocalDateTime.of(2026, 4, 1, 0, 0);
        LocalDateTime currentTo = LocalDateTime.of(2026, 4, 15, 23, 59, 59);
        LocalDateTime previousFrom = LocalDateTime.of(2026, 3, 17, 0, 0);
        LocalDateTime previousTo = LocalDateTime.of(2026, 3, 31, 23, 59, 59);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByUsernameAndTypeBetween(currentFrom, currentTo))
            .thenReturn(List.of(
                new Object[]{"admin", "RM_RECORD_DECLARED", 5L, LocalDateTime.of(2026, 4, 15, 10, 0)},
                new Object[]{"admin", "RM_FILE_PLAN_MOVED", 3L, LocalDateTime.of(2026, 4, 14, 8, 0)},
                new Object[]{null, "RM_RECORD_UNDECLARE_BLOCKED", 1L, LocalDateTime.of(2026, 4, 13, 7, 0)}
            ));
        when(auditLogRepository.countRmEventsByUsernameAndTypeBetween(previousFrom, previousTo))
            .thenReturn(List.of(
                new Object[]{"admin", "RM_RECORD_DECLARED", 2L, LocalDateTime.of(2026, 3, 30, 18, 0)},
                new Object[]{"bob", "RM_RECORD_CATEGORY_ASSIGNED", 4L, LocalDateTime.of(2026, 3, 28, 11, 0)}
            ));

        RecordsManagementService.ActivityContributorEventTypeReportDto result =
            service.getActivityContributorEventTypeReport(currentFrom, currentTo, 5, 3);

        assertEquals("2026-04-01T00:00", result.currentWindow().from());
        assertEquals("2026-03-17T00:00", result.previousWindow().from());
        assertEquals(5, result.limit());
        assertEquals(3, result.eventTypeLimit());
        assertEquals(9, result.currentTotalCount());
        assertEquals(6, result.previousTotalCount());
        assertEquals(3, result.contributors().size());

        RecordsManagementService.ActivityContributorEventTypeReportEntryDto first = result.contributors().get(0);
        assertEquals("admin", first.username());
        assertEquals(8, first.currentCount());
        assertEquals(2, first.previousCount());
        assertEquals(2, first.eventTypes().size());
        assertEquals("RM_RECORD_DECLARED", first.eventTypes().get(0).eventType());
        assertEquals("DECLARED", first.eventTypes().get(0).family());
        assertEquals(5, first.eventTypes().get(0).currentCount());
        assertEquals(2, first.eventTypes().get(0).previousCount());
        assertEquals("RM_FILE_PLAN_MOVED", first.eventTypes().get(1).eventType());
        assertEquals("GOVERNANCE_CHANGE", first.eventTypes().get(1).family());

        RecordsManagementService.ActivityContributorEventTypeReportEntryDto second = result.contributors().get(1);
        assertEquals("bob", second.username());
        assertEquals(0, second.currentCount());
        assertEquals(4, second.previousCount());
        assertEquals("RM_RECORD_CATEGORY_ASSIGNED", second.eventTypes().get(0).eventType());

        RecordsManagementService.ActivityContributorEventTypeReportEntryDto third = result.contributors().get(2);
        assertNull(third.username());
        assertEquals("(System)", third.label());
        assertEquals("RM_RECORD_UNDECLARE_BLOCKED", third.eventTypes().get(0).eventType());
        assertEquals("OTHER", third.eventTypes().get(0).family());
    }

    @Test
    @DisplayName("getActivityContributorEventTypeReport uses default closed range and clamps limits")
    void getActivityContributorEventTypeReportUsesDefaults() {
        LocalDate today = LocalDate.now();
        LocalDate startDay = today.minusDays(27);
        LocalDateTime expectedFrom = startDay.atStartOfDay();
        LocalDateTime expectedTo = today.atTime(23, 59, 59);
        long spanSeconds = ChronoUnit.SECONDS.between(expectedFrom, expectedTo);
        LocalDateTime expectedPreviousTo = expectedFrom.minusSeconds(1);
        LocalDateTime expectedPreviousFrom = expectedPreviousTo.minusSeconds(spanSeconds);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByUsernameAndTypeBetween(expectedFrom, expectedTo)).thenReturn(List.of());
        when(auditLogRepository.countRmEventsByUsernameAndTypeBetween(expectedPreviousFrom, expectedPreviousTo))
            .thenReturn(List.of());

        RecordsManagementService.ActivityContributorEventTypeReportDto result =
            service.getActivityContributorEventTypeReport(null, null, 99, 99);

        assertEquals(expectedFrom.toString(), result.currentWindow().from());
        assertEquals(expectedPreviousFrom.toString(), result.previousWindow().from());
        assertEquals(50, result.limit());
        assertEquals(10, result.eventTypeLimit());
        assertTrue(result.contributors().isEmpty());
    }

    @Test
    @DisplayName("getActivityContributorFamilyReport compares current and previous RM contributor families")
    void getActivityContributorFamilyReportComparesWindows() {
        LocalDateTime currentFrom = LocalDateTime.of(2026, 4, 1, 0, 0);
        LocalDateTime currentTo = LocalDateTime.of(2026, 4, 15, 23, 59, 59);
        LocalDateTime previousFrom = LocalDateTime.of(2026, 3, 17, 0, 0);
        LocalDateTime previousTo = LocalDateTime.of(2026, 3, 31, 23, 59, 59);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByUsernameAndTypeBetween(currentFrom, currentTo))
            .thenReturn(List.of(
                new Object[]{"admin", "RM_RECORD_DECLARED", 5L, LocalDateTime.of(2026, 4, 15, 10, 0)},
                new Object[]{"admin", "RM_FILE_PLAN_MOVED", 3L, LocalDateTime.of(2026, 4, 14, 8, 0)},
                new Object[]{null, "RM_RECORD_UNDECLARE_BLOCKED", 1L, LocalDateTime.of(2026, 4, 13, 7, 0)}
            ));
        when(auditLogRepository.countRmEventsByUsernameAndTypeBetween(previousFrom, previousTo))
            .thenReturn(List.of(
                new Object[]{"admin", "RM_RECORD_DECLARED", 2L, LocalDateTime.of(2026, 3, 30, 18, 0)},
                new Object[]{"bob", "RM_FILE_PLAN_MOVED", 4L, LocalDateTime.of(2026, 3, 28, 11, 0)}
            ));

        RecordsManagementService.ActivityContributorFamilyReportDto result =
            service.getActivityContributorFamilyReport(currentFrom, currentTo, 5);

        assertEquals("2026-04-01T00:00", result.currentWindow().from());
        assertEquals("2026-03-17T00:00", result.previousWindow().from());
        assertEquals(5, result.limit());
        assertEquals(9, result.currentTotalCount());
        assertEquals(6, result.previousTotalCount());
        assertEquals(3, result.contributors().size());

        RecordsManagementService.ActivityContributorFamilyReportEntryDto first = result.contributors().get(0);
        assertEquals("admin", first.username());
        assertEquals(8, first.currentCount());
        assertEquals(2, first.previousCount());
        assertEquals(6, first.delta());
        assertEquals(2, first.families().size());
        assertEquals("DECLARED", first.families().get(0).family());
        assertEquals(5, first.families().get(0).currentCount());
        assertEquals(2, first.families().get(0).previousCount());

        RecordsManagementService.ActivityContributorFamilyReportEntryDto second = result.contributors().get(1);
        assertEquals("bob", second.username());
        assertEquals(0, second.currentCount());
        assertEquals(4, second.previousCount());
        assertEquals("GOVERNANCE_CHANGE", second.families().get(0).family());

        RecordsManagementService.ActivityContributorFamilyReportEntryDto third = result.contributors().get(2);
        assertNull(third.username());
        assertEquals("(System)", third.label());
        assertEquals("OTHER", third.families().get(0).family());
    }

    @Test
    @DisplayName("getActivityContributorFamilyReport uses default closed range and clamps limit")
    void getActivityContributorFamilyReportUsesDefaults() {
        LocalDate today = LocalDate.now();
        LocalDate startDay = today.minusDays(27);
        LocalDateTime expectedFrom = startDay.atStartOfDay();
        LocalDateTime expectedTo = today.atTime(23, 59, 59);
        long spanSeconds = ChronoUnit.SECONDS.between(expectedFrom, expectedTo);
        LocalDateTime expectedPreviousTo = expectedFrom.minusSeconds(1);
        LocalDateTime expectedPreviousFrom = expectedPreviousTo.minusSeconds(spanSeconds);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByUsernameAndTypeBetween(expectedFrom, expectedTo)).thenReturn(List.of());
        when(auditLogRepository.countRmEventsByUsernameAndTypeBetween(expectedPreviousFrom, expectedPreviousTo))
            .thenReturn(List.of());

        RecordsManagementService.ActivityContributorFamilyReportDto result =
            service.getActivityContributorFamilyReport(null, null, 99);

        assertEquals(expectedFrom.toString(), result.currentWindow().from());
        assertEquals(expectedPreviousFrom.toString(), result.previousWindow().from());
        assertEquals(50, result.limit());
        assertTrue(result.contributors().isEmpty());
    }

    @Test
    @DisplayName("getActivityContributorTrend aggregates tracked contributors into buckets")
    void getActivityContributorTrendAggregatesBuckets() {
        LocalDate today = LocalDate.now();
        LocalDate startDay = today.minusDays(27);
        LocalDateTime from = startDay.atStartOfDay();
        LocalDateTime to = today.atTime(23, 59, 59);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByUsernameAndTypeBetween(from, to))
            .thenReturn(List.of(
                new Object[]{"admin", "RM_RECORD_DECLARED", 5L, LocalDateTime.of(2026, 4, 15, 10, 0)},
                new Object[]{"bob", "RM_FILE_PLAN_MOVED", 4L, LocalDateTime.of(2026, 4, 14, 8, 0)},
                new Object[]{null, "RM_RECORD_UNDECLARE_BLOCKED", 2L, LocalDateTime.of(2026, 4, 13, 7, 0)}
            ));
        when(auditLogRepository.countRmEventsByDayUsernameAndTypeSince(from))
            .thenReturn(List.of(
                new Object[]{today.minusDays(1), "admin", "RM_RECORD_DECLARED", 3L},
                new Object[]{today.minusDays(1), "bob", "RM_FILE_PLAN_MOVED", 2L},
                new Object[]{today.minusDays(1), null, "RM_RECORD_UNDECLARE_BLOCKED", 1L},
                new Object[]{today, "admin", "RM_RECORD_DECLARED", 2L},
                new Object[]{today, "bob", "RM_FILE_PLAN_MOVED", 2L},
                new Object[]{today, "carol", "RM_RECORD_DECLARED", 4L}
            ));

        RecordsManagementService.ActivityContributorTrendDto result = service.getActivityContributorTrend(28, 7, 2);

        assertEquals(28, result.days());
        assertEquals(7, result.bucketDays());
        assertEquals(2, result.limit());
        assertEquals(2, result.trackedContributors().size());
        assertEquals("admin", result.trackedContributors().get(0).username());
        assertEquals("bob", result.trackedContributors().get(1).username());
        assertFalse(result.buckets().isEmpty());

        RecordsManagementService.ActivityContributorTrendBucketDto lastBucket =
            result.buckets().get(result.buckets().size() - 1);
        assertEquals(2, lastBucket.activeDayCount());
        assertEquals(14, lastBucket.totalCount());
        assertEquals(5, lastBucket.otherCount());
        assertEquals(2, lastBucket.contributorCounts().size());
        assertEquals("admin", lastBucket.contributorCounts().get(0).username());
        assertEquals(5, lastBucket.contributorCounts().get(0).count());
        assertEquals("bob", lastBucket.contributorCounts().get(1).username());
        assertEquals(4, lastBucket.contributorCounts().get(1).count());
    }

    @Test
    @DisplayName("getActivityContributorTrend uses defaults and clamps params")
    void getActivityContributorTrendUsesDefaultsAndClamps() {
        LocalDate today = LocalDate.now();
        LocalDate startDay = today.minusDays(6);
        LocalDateTime from = startDay.atStartOfDay();
        LocalDateTime to = today.atTime(23, 59, 59);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByUsernameAndTypeBetween(from, to)).thenReturn(List.of());
        when(auditLogRepository.countRmEventsByDayUsernameAndTypeSince(from)).thenReturn(List.of());

        RecordsManagementService.ActivityContributorTrendDto result = service.getActivityContributorTrend(1, 99, 99);

        assertEquals(7, result.days());
        assertEquals(7, result.bucketDays());
        assertEquals(20, result.limit());
        assertTrue(result.trackedContributors().isEmpty());
        assertEquals(1, result.buckets().size());
    }

    @Test
    @DisplayName("getActivityContributorEventTypeTrend aggregates tracked contributors and event types into buckets")
    void getActivityContributorEventTypeTrendAggregatesBuckets() {
        LocalDate today = LocalDate.now();
        LocalDate startDay = today.minusDays(27);
        LocalDateTime from = startDay.atStartOfDay();
        LocalDateTime to = today.atTime(23, 59, 59);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByUsernameAndTypeBetween(from, to))
            .thenReturn(List.of(
                new Object[]{"admin", "RM_RECORD_DECLARED", 5L, LocalDateTime.of(2026, 4, 15, 10, 0)},
                new Object[]{"admin", "RM_RECORD_UNDECLARED", 3L, LocalDateTime.of(2026, 4, 14, 9, 0)},
                new Object[]{"bob", "RM_FILE_PLAN_MOVED", 4L, LocalDateTime.of(2026, 4, 14, 8, 0)},
                new Object[]{null, "RM_RECORD_UNDECLARE_BLOCKED", 2L, LocalDateTime.of(2026, 4, 13, 7, 0)}
            ));
        when(auditLogRepository.countRmEventsByDayUsernameAndTypeSince(from))
            .thenReturn(List.of(
                new Object[]{today.minusDays(1), "admin", "RM_RECORD_DECLARED", 3L},
                new Object[]{today.minusDays(1), "admin", "RM_RECORD_UNDECLARED", 1L},
                new Object[]{today.minusDays(1), "bob", "RM_FILE_PLAN_MOVED", 2L},
                new Object[]{today.minusDays(1), null, "RM_RECORD_UNDECLARE_BLOCKED", 1L},
                new Object[]{today, "admin", "RM_RECORD_DECLARED", 2L},
                new Object[]{today, "bob", "RM_FILE_PLAN_MOVED", 2L},
                new Object[]{today, "carol", "RM_RECORD_DECLARED", 4L}
            ));

        RecordsManagementService.ActivityContributorEventTypeTrendDto result =
            service.getActivityContributorEventTypeTrend(28, 7, 2, 2);

        assertEquals(28, result.days());
        assertEquals(7, result.bucketDays());
        assertEquals(2, result.limit());
        assertEquals(2, result.eventTypeLimit());
        assertEquals(2, result.trackedContributors().size());
        assertEquals("admin", result.trackedContributors().get(0).username());
        assertEquals("bob", result.trackedContributors().get(1).username());
        assertFalse(result.buckets().isEmpty());

        RecordsManagementService.ActivityContributorEventTypeTrendBucketDto lastBucket =
            result.buckets().get(result.buckets().size() - 1);
        assertEquals(2, lastBucket.activeDayCount());
        assertEquals(15, lastBucket.totalCount());
        assertEquals(5, lastBucket.otherCount());
        assertEquals(2, lastBucket.contributorCounts().size());
        assertEquals("admin", lastBucket.contributorCounts().get(0).username());
        assertEquals(6, lastBucket.contributorCounts().get(0).count());
        assertEquals(2, lastBucket.contributorCounts().get(0).eventTypes().size());
        assertEquals("RM_RECORD_DECLARED", lastBucket.contributorCounts().get(0).eventTypes().get(0).eventType());
        assertEquals(5, lastBucket.contributorCounts().get(0).eventTypes().get(0).count());
        assertEquals("RM_RECORD_UNDECLARED", lastBucket.contributorCounts().get(0).eventTypes().get(1).eventType());
        assertEquals(1, lastBucket.contributorCounts().get(0).eventTypes().get(1).count());
        assertEquals("bob", lastBucket.contributorCounts().get(1).username());
        assertEquals(4, lastBucket.contributorCounts().get(1).count());
        assertEquals("RM_FILE_PLAN_MOVED", lastBucket.contributorCounts().get(1).eventTypes().get(0).eventType());
        assertEquals(4, lastBucket.contributorCounts().get(1).eventTypes().get(0).count());
    }

    @Test
    @DisplayName("getActivityContributorEventTypeTrend uses defaults and clamps params")
    void getActivityContributorEventTypeTrendUsesDefaultsAndClamps() {
        LocalDate today = LocalDate.now();
        LocalDate startDay = today.minusDays(6);
        LocalDateTime from = startDay.atStartOfDay();
        LocalDateTime to = today.atTime(23, 59, 59);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByUsernameAndTypeBetween(from, to)).thenReturn(List.of());
        when(auditLogRepository.countRmEventsByDayUsernameAndTypeSince(from)).thenReturn(List.of());

        RecordsManagementService.ActivityContributorEventTypeTrendDto result =
            service.getActivityContributorEventTypeTrend(1, 99, 99, 99);

        assertEquals(7, result.days());
        assertEquals(7, result.bucketDays());
        assertEquals(20, result.limit());
        assertEquals(10, result.eventTypeLimit());
        assertTrue(result.trackedContributors().isEmpty());
        assertEquals(1, result.buckets().size());
        assertTrue(result.buckets().get(0).contributorCounts().isEmpty());
    }

    @Test
    @DisplayName("getActivityContributorFamilyTrend aggregates tracked contributors and families into buckets")
    void getActivityContributorFamilyTrendAggregatesBuckets() {
        LocalDate today = LocalDate.now();
        LocalDate startDay = today.minusDays(27);
        LocalDateTime from = startDay.atStartOfDay();
        LocalDateTime to = today.atTime(23, 59, 59);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByUsernameAndTypeBetween(from, to))
            .thenReturn(List.of(
                new Object[]{"admin", "RM_RECORD_DECLARED", 5L, LocalDateTime.of(2026, 4, 15, 10, 0)},
                new Object[]{"admin", "RM_RECORD_UNDECLARED", 3L, LocalDateTime.of(2026, 4, 14, 9, 0)},
                new Object[]{"bob", "RM_FILE_PLAN_MOVED", 4L, LocalDateTime.of(2026, 4, 14, 8, 0)},
                new Object[]{null, "RM_RECORD_UNDECLARE_BLOCKED", 2L, LocalDateTime.of(2026, 4, 13, 7, 0)}
            ));
        when(auditLogRepository.countRmEventsByDayUsernameAndTypeSince(from))
            .thenReturn(List.of(
                new Object[]{today.minusDays(1), "admin", "RM_RECORD_DECLARED", 3L},
                new Object[]{today.minusDays(1), "admin", "RM_RECORD_UNDECLARED", 1L},
                new Object[]{today.minusDays(1), "bob", "RM_FILE_PLAN_MOVED", 2L},
                new Object[]{today.minusDays(1), null, "RM_RECORD_UNDECLARE_BLOCKED", 1L},
                new Object[]{today, "admin", "RM_RECORD_DECLARED", 2L},
                new Object[]{today, "bob", "RM_FILE_PLAN_MOVED", 2L},
                new Object[]{today, "carol", "RM_RECORD_DECLARED", 4L}
            ));

        RecordsManagementService.ActivityContributorFamilyTrendDto result =
            service.getActivityContributorFamilyTrend(28, 7, 2);

        assertEquals(28, result.days());
        assertEquals(7, result.bucketDays());
        assertEquals(2, result.limit());
        assertEquals(2, result.trackedContributors().size());
        assertEquals("admin", result.trackedContributors().get(0).username());
        assertEquals("bob", result.trackedContributors().get(1).username());
        assertFalse(result.buckets().isEmpty());

        RecordsManagementService.ActivityContributorFamilyTrendBucketDto lastBucket =
            result.buckets().get(result.buckets().size() - 1);
        assertEquals(2, lastBucket.activeDayCount());
        assertEquals(15, lastBucket.totalCount());
        assertEquals(5, lastBucket.otherCount());
        assertEquals(2, lastBucket.contributorCounts().size());
        assertEquals("admin", lastBucket.contributorCounts().get(0).username());
        assertEquals(6, lastBucket.contributorCounts().get(0).count());
        assertEquals(2, lastBucket.contributorCounts().get(0).families().size());
        assertEquals("DECLARED", lastBucket.contributorCounts().get(0).families().get(0).family());
        assertEquals(5, lastBucket.contributorCounts().get(0).families().get(0).count());
        assertEquals("UNDECLARED", lastBucket.contributorCounts().get(0).families().get(1).family());
        assertEquals(1, lastBucket.contributorCounts().get(0).families().get(1).count());
        assertEquals("bob", lastBucket.contributorCounts().get(1).username());
        assertEquals(4, lastBucket.contributorCounts().get(1).count());
        assertEquals("GOVERNANCE_CHANGE", lastBucket.contributorCounts().get(1).families().get(0).family());
        assertEquals(4, lastBucket.contributorCounts().get(1).families().get(0).count());
    }

    @Test
    @DisplayName("getActivityContributorFamilyTrend uses defaults and clamps params")
    void getActivityContributorFamilyTrendUsesDefaultsAndClamps() {
        LocalDate today = LocalDate.now();
        LocalDate startDay = today.minusDays(6);
        LocalDateTime from = startDay.atStartOfDay();
        LocalDateTime to = today.atTime(23, 59, 59);

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByUsernameAndTypeBetween(from, to)).thenReturn(List.of());
        when(auditLogRepository.countRmEventsByDayUsernameAndTypeSince(from)).thenReturn(List.of());

        RecordsManagementService.ActivityContributorFamilyTrendDto result =
            service.getActivityContributorFamilyTrend(1, 99, 99);

        assertEquals(7, result.days());
        assertEquals(7, result.bucketDays());
        assertEquals(20, result.limit());
        assertTrue(result.trackedContributors().isEmpty());
        assertEquals(1, result.buckets().size());
        assertTrue(result.buckets().get(0).contributorCounts().isEmpty());
    }

    @Test
    @DisplayName("getActivityEventTypes aggregates RM events by exact event type")
    void getActivityEventTypesAggregatesByType() {
        LocalDateTime now = LocalDateTime.now();

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByTypeBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(List.of(
                new Object[]{"RM_RECORD_DECLARED", 5L, now},
                new Object[]{"RM_FILE_PLAN_CREATED", 3L, now.minusHours(1)},
                new Object[]{"RM_RECORD_UNDECLARE_BLOCKED", 2L, now.minusHours(2)}
            ));

        RecordsManagementService.ActivityEventTypesDto result = service.getActivityEventTypes(28, 8);

        assertEquals(28, result.days());
        assertEquals(8, result.limit());
        assertEquals(3, result.eventTypes().size());

        RecordsManagementService.ActivityEventTypeDto first = result.eventTypes().get(0);
        assertEquals("RM_RECORD_DECLARED", first.eventType());
        assertEquals("DECLARED", first.family());
        assertEquals(5, first.count());
        assertEquals(now, first.lastEventTime());

        RecordsManagementService.ActivityEventTypeDto second = result.eventTypes().get(1);
        assertEquals("RM_FILE_PLAN_CREATED", second.eventType());
        assertEquals("GOVERNANCE_CHANGE", second.family());
        assertEquals(3, second.count());

        RecordsManagementService.ActivityEventTypeDto third = result.eventTypes().get(2);
        assertEquals("RM_RECORD_UNDECLARE_BLOCKED", third.eventType());
        assertEquals("OTHER", third.family());
        assertEquals(2, third.count());
    }

    @Test
    @DisplayName("getActivityEventTypes respects limit parameter")
    void getActivityEventTypesRespectsLimit() {
        LocalDateTime now = LocalDateTime.now();

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByTypeBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(List.of(
                new Object[]{"RM_RECORD_DECLARED", 5L, now},
                new Object[]{"RM_RECORD_UNDECLARED", 4L, now.minusHours(1)},
                new Object[]{"RM_RECORD_CATEGORY_ASSIGNED", 3L, now.minusHours(2)}
            ));

        RecordsManagementService.ActivityEventTypesDto result = service.getActivityEventTypes(28, 2);

        assertEquals(2, result.eventTypes().size());
        assertEquals("RM_RECORD_DECLARED", result.eventTypes().get(0).eventType());
        assertEquals("RM_RECORD_UNDECLARED", result.eventTypes().get(1).eventType());
    }

    @Test
    @DisplayName("getActivityEventTypes returns empty list when no RM events")
    void getActivityEventTypesReturnsEmptyWhenNoEvents() {
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByTypeBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(List.of());

        RecordsManagementService.ActivityEventTypesDto result = service.getActivityEventTypes(null, null);

        assertEquals(28, result.days());
        assertEquals(8, result.limit());
        assertEquals(0, result.eventTypes().size());
    }

    @Test
    @DisplayName("getActivityEventTypes clamps days and limit")
    void getActivityEventTypesClampsDaysAndLimit() {
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByTypeBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(List.of());

        RecordsManagementService.ActivityEventTypesDto result = service.getActivityEventTypes(1, 99);

        assertEquals(7, result.days());
        assertEquals(20, result.limit());
    }

    @Test
    @DisplayName("getActivityFamilies aggregates RM events by family")
    void getActivityFamiliesAggregatesByFamily() {
        LocalDateTime now = LocalDateTime.now();

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByTypeBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(List.of(
                new Object[]{"RM_RECORD_DECLARED", 5L, now.minusHours(3)},
                new Object[]{"RM_RECORD_UNDECLARED", 2L, now.minusHours(2)},
                new Object[]{"RM_FILE_PLAN_CREATED", 3L, now.minusHours(1)},
                new Object[]{"RM_RECORD_UNDECLARE_BLOCKED", 4L, now}
            ));

        RecordsManagementService.ActivityFamiliesDto result = service.getActivityFamilies(28);

        assertEquals(28, result.days());
        assertEquals(14L, result.totalCount());
        assertEquals(4, result.families().size());
        assertEquals("DECLARED", result.families().get(0).family());
        assertEquals(5L, result.families().get(0).count());
        assertEquals("OTHER", result.families().get(1).family());
        assertEquals(4L, result.families().get(1).count());
        assertEquals("GOVERNANCE_CHANGE", result.families().get(2).family());
        assertEquals("UNDECLARED", result.families().get(3).family());
    }

    @Test
    @DisplayName("getActivityFamilies returns empty list when no RM events")
    void getActivityFamiliesReturnsEmptyWhenNoEvents() {
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByTypeBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(List.of());

        RecordsManagementService.ActivityFamiliesDto result = service.getActivityFamilies(null);

        assertEquals(28, result.days());
        assertEquals(0L, result.totalCount());
        assertEquals(0, result.families().size());
    }

    @Test
    @DisplayName("getActivityFamilies clamps days to supported range")
    void getActivityFamiliesClampsDays() {
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByTypeBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(List.of());

        RecordsManagementService.ActivityFamiliesDto result = service.getActivityFamilies(1);
        RecordsManagementService.ActivityFamiliesDto maxResult = service.getActivityFamilies(365);

        assertEquals(7, result.days());
        assertEquals(90, maxResult.days());
    }

    @Test
    @DisplayName("getActivityFamilyHighlights compares current and previous windows by family")
    void getActivityFamilyHighlightsComparesWindows() {
        LocalDateTime now = LocalDateTime.now();

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByTypeBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(
                List.of(
                    new Object[]{"RM_RECORD_DECLARED", 5L, now.minusHours(1)},
                    new Object[]{"RM_RECORD_CATEGORY_ASSIGNED", 2L, now.minusHours(2)},
                    new Object[]{"RM_RECORD_UNDECLARE_BLOCKED", 1L, now}
                ),
                List.of(
                    new Object[]{"RM_RECORD_DECLARED", 2L, now.minusDays(7)},
                    new Object[]{"RM_RECORD_UNDECLARED", 3L, now.minusDays(8)},
                    new Object[]{"RM_RECORD_UNDECLARE_BLOCKED", 4L, now.minusDays(6)}
                )
            );

        RecordsManagementService.ActivityFamilyHighlightsDto result = service.getActivityFamilyHighlights(7);

        assertEquals(7, result.windowDays());
        assertEquals(4, result.families().size());
        assertEquals("DECLARED", result.families().get(0).family());
        assertEquals(5L, result.families().get(0).currentCount());
        assertEquals(2L, result.families().get(0).previousCount());
        assertEquals(3L, result.families().get(0).delta());
        assertEquals("OTHER", result.families().get(1).family());
        assertEquals(1L, result.families().get(1).currentCount());
        assertEquals(4L, result.families().get(1).previousCount());
        assertEquals(-3L, result.families().get(1).delta());
        assertEquals("UNDECLARED", result.families().get(2).family());
        assertEquals(0L, result.families().get(2).currentCount());
        assertEquals(3L, result.families().get(2).previousCount());
    }

    @Test
    @DisplayName("getActivityFamilyHighlights returns empty families when no RM activity exists")
    void getActivityFamilyHighlightsReturnsEmptyWhenNoEvents() {
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByTypeBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(List.of(), List.of());

        RecordsManagementService.ActivityFamilyHighlightsDto result = service.getActivityFamilyHighlights(null);

        assertEquals(7, result.windowDays());
        assertEquals(0, result.families().size());
        assertNotNull(result.currentWindow());
        assertNotNull(result.previousWindow());
    }

    @Test
    @DisplayName("getActivityFamilyHighlights clamps window days")
    void getActivityFamilyHighlightsClampsWindowDays() {
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(auditLogRepository.countRmEventsByTypeBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(List.of(), List.of(), List.of(), List.of());

        RecordsManagementService.ActivityFamilyHighlightsDto minResult = service.getActivityFamilyHighlights(1);
        RecordsManagementService.ActivityFamilyHighlightsDto maxResult = service.getActivityFamilyHighlights(365);

        assertEquals(2, minResult.windowDays());
        assertEquals(30, maxResult.windowDays());
    }

    @Test
    @DisplayName("assertArchiveMutationAllowed blocks nodes inside file plans")
    void assertArchiveMutationAllowedBlocksFilePlanNodes() {
        Folder filePlan = new Folder();
        filePlan.setId(UUID.randomUUID());
        filePlan.setName("Corporate File Plan");
        filePlan.setPath("/Corporate File Plan");
        filePlan.setFolderType(Folder.FolderType.FILE_PLAN);
        filePlan.setDeleted(false);

        Document document = document(UUID.randomUUID(), "/Corporate File Plan/Contracts/report.pdf");

        when(folderRepository.findActiveFoldersByType(Folder.FolderType.FILE_PLAN)).thenReturn(List.of(filePlan));
        when(tenantWorkspaceScopeService.isPathVisible("/Corporate File Plan")).thenReturn(true);

        IllegalOperationException ex = assertThrows(
            IllegalOperationException.class,
            () -> service.assertArchiveMutationAllowed(document, "archive")
        );

        assertEquals(
            "Cannot archive because node 'report.pdf' is governed by file plan 'Corporate File Plan'",
            ex.getMessage()
        );
    }

    @Test
    @DisplayName("assertRestoreAllowed blocks nodes governed by file plan")
    void assertRestoreAllowedBlocksNodesGovernedByFilePlan() {
        Folder filePlan = new Folder();
        filePlan.setId(UUID.randomUUID());
        filePlan.setName("Corporate File Plan");
        filePlan.setPath("/Corporate File Plan");
        filePlan.setFolderType(Folder.FolderType.FILE_PLAN);
        filePlan.setDeleted(false);

        Folder trashedChild = new Folder();
        trashedChild.setId(UUID.randomUUID());
        trashedChild.setName("Closed Cases");
        trashedChild.setPath("/Corporate File Plan/Closed Cases");
        trashedChild.setArchiveStatus(Node.ArchiveStatus.LIVE);

        when(folderRepository.findActiveFoldersByType(Folder.FolderType.FILE_PLAN)).thenReturn(List.of(filePlan));
        when(tenantWorkspaceScopeService.isPathVisible("/Corporate File Plan")).thenReturn(true);

        IllegalOperationException ex = assertThrows(
            IllegalOperationException.class,
            () -> service.assertRestoreAllowed(trashedChild, "restore from trash")
        );

        assertEquals(
            "Cannot restore from trash because node 'Closed Cases' is governed by file plan 'Corporate File Plan'",
            ex.getMessage()
        );
    }

    @Test
    @DisplayName("assertRestoreScopeAllowed blocks archived folder containing declared records")
    void assertRestoreScopeAllowedBlocksFolderContainingDeclaredRecords() {
        Folder archivedFolder = new Folder();
        archivedFolder.setId(UUID.randomUUID());
        archivedFolder.setName("Archive Batch");
        archivedFolder.setPath("/Archive Batch");
        archivedFolder.setArchiveStatus(Node.ArchiveStatus.ARCHIVED);

        Document record = document(UUID.randomUUID(), "/Archive Batch/report.pdf");
        record.addAspect(RecordsManagementService.RECORD_ASPECT);
        record.setArchiveStatus(Node.ArchiveStatus.ARCHIVED);

        when(nodeRepository.findByAspectNameAndDeletedFalse(RecordsManagementService.RECORD_ASPECT))
            .thenReturn(List.of(record));
        when(folderRepository.findActiveFoldersByType(Folder.FolderType.FILE_PLAN)).thenReturn(List.of());

        IllegalOperationException ex = assertThrows(
            IllegalOperationException.class,
            () -> service.assertRestoreScopeAllowed(archivedFolder, List.of(archivedFolder, record), "restore")
        );

        assertEquals(
            "Cannot restore because node 'Archive Batch' contains declared record(s): report.pdf",
            ex.getMessage()
        );
    }

    @Test
    @DisplayName("assertRestoreScopeAllowed blocks archived folder containing file plan subtree")
    void assertRestoreScopeAllowedBlocksFolderContainingFilePlanSubtree() {
        Folder archivedFolder = new Folder();
        archivedFolder.setId(UUID.randomUUID());
        archivedFolder.setName("Archive Batch");
        archivedFolder.setPath("/Archive Batch");
        archivedFolder.setArchiveStatus(Node.ArchiveStatus.ARCHIVED);

        Folder filePlan = new Folder();
        filePlan.setId(UUID.randomUUID());
        filePlan.setName("Corporate File Plan");
        filePlan.setPath("/Archive Batch/Corporate File Plan");
        filePlan.setFolderType(Folder.FolderType.FILE_PLAN);
        filePlan.setArchiveStatus(Node.ArchiveStatus.ARCHIVED);
        filePlan.setDeleted(false);

        when(nodeRepository.findByAspectNameAndDeletedFalse(RecordsManagementService.RECORD_ASPECT))
            .thenReturn(List.of());
        when(folderRepository.findActiveFoldersByType(Folder.FolderType.FILE_PLAN)).thenReturn(List.of(filePlan));
        when(tenantWorkspaceScopeService.isPathVisible("/Archive Batch/Corporate File Plan")).thenReturn(true);

        IllegalOperationException ex = assertThrows(
            IllegalOperationException.class,
            () -> service.assertRestoreScopeAllowed(archivedFolder, List.of(archivedFolder, filePlan), "restore")
        );

        assertEquals(
            "Cannot restore because node 'Archive Batch' contains file plan 'Corporate File Plan'",
            ex.getMessage()
        );
    }

    @Test
    @DisplayName("assertCreateInFolderAllowed blocks file plan root targets")
    void assertCreateInFolderAllowedBlocksFilePlanRootTargets() {
        Folder filePlan = new Folder();
        filePlan.setId(UUID.randomUUID());
        filePlan.setName("Corporate File Plan");
        filePlan.setPath("/Corporate File Plan");
        filePlan.setFolderType(Folder.FolderType.FILE_PLAN);
        filePlan.setArchiveStatus(Node.ArchiveStatus.LIVE);

        when(folderRepository.findActiveFoldersByType(Folder.FolderType.FILE_PLAN)).thenReturn(List.of(filePlan));
        when(tenantWorkspaceScopeService.isPathVisible("/Corporate File Plan")).thenReturn(true);

        IllegalOperationException ex = assertThrows(
            IllegalOperationException.class,
            () -> service.assertCreateInFolderAllowed(filePlan, "bulk import into target folder")
        );

        assertEquals(
            "Cannot bulk import into target folder because target folder 'Corporate File Plan' is a file plan",
            ex.getMessage()
        );
    }

    @Test
    @DisplayName("assertCreateInFolderAllowed blocks folders inside file plan scope")
    void assertCreateInFolderAllowedBlocksFoldersInsideFilePlanScope() {
        Folder filePlan = new Folder();
        filePlan.setId(UUID.randomUUID());
        filePlan.setName("Corporate File Plan");
        filePlan.setPath("/Corporate File Plan");
        filePlan.setFolderType(Folder.FolderType.FILE_PLAN);
        filePlan.setArchiveStatus(Node.ArchiveStatus.LIVE);

        Folder targetFolder = new Folder();
        targetFolder.setId(UUID.randomUUID());
        targetFolder.setName("Closed Cases");
        targetFolder.setPath("/Corporate File Plan/Closed Cases");
        targetFolder.setArchiveStatus(Node.ArchiveStatus.LIVE);

        when(folderRepository.findActiveFoldersByType(Folder.FolderType.FILE_PLAN)).thenReturn(List.of(filePlan));
        when(tenantWorkspaceScopeService.isPathVisible("/Corporate File Plan")).thenReturn(true);

        IllegalOperationException ex = assertThrows(
            IllegalOperationException.class,
            () -> service.assertCreateInFolderAllowed(targetFolder, "replicate document into target folder")
        );

        assertEquals(
            "Cannot replicate document into target folder because target folder 'Closed Cases' is governed by file plan 'Corporate File Plan'",
            ex.getMessage()
        );
    }

    @Test
    @DisplayName("getOperationsTelemetry summarizes RM-governed import and transfer jobs")
    void getOperationsTelemetrySummarizesGovernedJobs() {
        UUID targetFolderId = UUID.randomUUID();
        Folder filePlan = new Folder();
        filePlan.setId(targetFolderId);
        filePlan.setName("Corporate File Plan");
        filePlan.setPath("/Corporate File Plan");
        filePlan.setFolderType(Folder.FolderType.FILE_PLAN);
        filePlan.setArchiveStatus(Node.ArchiveStatus.LIVE);

        UUID sourceNodeId = UUID.randomUUID();
        Document sourceRecord = document(sourceNodeId, "/Corporate File Plan/Contracts/report.pdf");
        sourceRecord.addAspect(RecordsManagementService.RECORD_ASPECT);

        ImportJob importJob = new ImportJob();
        importJob.setId(UUID.randomUUID());
        importJob.setTargetFolderId(targetFolderId);
        importJob.setStatus(ImportJob.ImportJobStatus.RUNNING);
        importJob.setConflictPolicy(ImportJob.ConflictPolicy.OVERWRITE);
        importJob.setTotalFiles(4);
        importJob.setImportedFiles(2);
        importJob.setSkippedFiles(1);
        importJob.setFailedFiles(1);
        importJob.setCreatedAt(LocalDateTime.of(2026, 4, 14, 21, 0));
        importJob.setLastMessage("Bulk import started");

        TransferTarget transferTarget = new TransferTarget();
        transferTarget.setId(UUID.randomUUID());
        transferTarget.setTargetFolderId(targetFolderId);

        ReplicationJob transferJob = new ReplicationJob();
        transferJob.setId(UUID.randomUUID());
        transferJob.setDefinitionId(UUID.randomUUID());
        transferJob.setTransferTargetId(transferTarget.getId());
        transferJob.setSourceNodeId(sourceNodeId);
        transferJob.setStatus(ReplicationJob.ReplicationJobStatus.COMPLETED);
        transferJob.setTransportStatus(ReplicationJob.TransportStatus.SUCCESS);
        transferJob.setCreatedAt(LocalDateTime.of(2026, 4, 14, 22, 0));
        transferJob.setLastMessage("Replication completed");
        transferJob.setTransportMessage("Transferred");

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(importJobRepository.findAll()).thenReturn(List.of(importJob));
        when(importJobRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 5)))
            .thenReturn(new PageImpl<>(List.of(importJob), PageRequest.of(0, 5), 1));
        when(replicationJobRepository.findAll()).thenReturn(List.of(transferJob));
        when(replicationJobRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 5)))
            .thenReturn(new PageImpl<>(List.of(transferJob), PageRequest.of(0, 5), 1));
        when(nodeRepository.findById(targetFolderId)).thenReturn(Optional.of(filePlan));
        when(nodeRepository.findById(sourceNodeId)).thenReturn(Optional.of(sourceRecord));
        when(transferTargetRepository.findById(transferTarget.getId())).thenReturn(Optional.of(transferTarget));
        when(folderRepository.findActiveFoldersByType(Folder.FolderType.FILE_PLAN)).thenReturn(List.of(filePlan));
        when(tenantWorkspaceScopeService.isPathVisible("/Corporate File Plan")).thenReturn(true);
        when(tenantWorkspaceScopeService.isPathVisible("/Corporate File Plan/Contracts/report.pdf")).thenReturn(true);

        RecordsManagementService.RecordsOperationsTelemetryDto telemetry = service.getOperationsTelemetry(5);

        assertEquals(1, telemetry.governedImportJobCount());
        assertEquals(1, telemetry.activeGovernedImportJobCount());
        assertEquals(0, telemetry.failedGovernedImportJobCount());
        assertEquals(1, telemetry.governedTransferJobCount());
        assertEquals(0, telemetry.activeGovernedTransferJobCount());
        assertEquals(0, telemetry.failedGovernedTransferJobCount());
        assertEquals("TARGET_FILE_PLAN", telemetry.recentImportJobs().get(0).governanceReasons().get(0));
        assertEquals("TARGET_FILE_PLAN", telemetry.importGovernanceReasonBreakdown().get(0).key());
        org.junit.jupiter.api.Assertions.assertTrue(
            telemetry.recentTransferJobs().get(0).governanceReasons().contains("SOURCE_DECLARED_RECORD")
        );
        org.junit.jupiter.api.Assertions.assertTrue(
            telemetry.recentTransferJobs().get(0).governanceReasons().contains("TARGET_FILE_PLAN")
        );
        org.junit.jupiter.api.Assertions.assertTrue(
            telemetry.transferGovernanceReasonBreakdown().stream()
                .anyMatch(bucket -> "SOURCE_DECLARED_RECORD".equals(bucket.key()) && bucket.count() == 1)
        );
    }

    private Document document(UUID id, String path) {
        Document document = new Document();
        document.setId(id);
        document.setName("report.pdf");
        document.setPath(path);
        document.setArchiveStatus(Node.ArchiveStatus.LIVE);
        document.setStatus(Node.NodeStatus.ACTIVE);
        return document;
    }

    private Category recordCategory(UUID id, String path, String name, Category parent) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setPurpose(Category.Purpose.RECORD);
        category.setActive(true);
        if (parent != null) {
            category.setParent(parent);
        }
        category.setDescription(name);
        ReflectionTestUtils.setField(category, "path", path);
        return category;
    }
}
