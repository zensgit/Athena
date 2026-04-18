package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Node;
import com.ecm.core.exception.IllegalOperationException;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.FolderRepository;
import com.ecm.core.repository.NodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckOutCheckInServiceRecordDeclarationTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private NodeRepository nodeRepository;
    @Mock private SecurityService securityService;
    @Mock private TenantWorkspaceScopeService tenantWorkspaceScopeService;
    @Mock private ContentReferenceService contentReferenceService;
    @Mock private ContentService contentService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private RecordsManagementService recordsManagementService;

    private CheckOutCheckInService service;

    @BeforeEach
    void setUp() {
        service = new CheckOutCheckInService(
            documentRepository,
            folderRepository,
            nodeRepository,
            securityService,
            tenantWorkspaceScopeService,
            contentReferenceService,
            contentService,
            eventPublisher
        );
        ReflectionTestUtils.setField(service, "recordsManagementService", recordsManagementService);
    }

    @Test
    @DisplayName("checkout rejects declared record")
    void checkoutRejectsDeclaredRecord() {
        UUID documentId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setName("report.pdf");
        document.setPath("/Sites/Finance/report.pdf");
        document.setArchiveStatus(Node.ArchiveStatus.LIVE);

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);
        doThrow(new IllegalOperationException("record")).when(recordsManagementService)
            .assertDirectMutationAllowed(document, "check out");

        assertThrows(IllegalOperationException.class, () -> service.checkout(documentId));

        verify(documentRepository, never()).save(document);
    }
}
