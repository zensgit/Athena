package com.ecm.core.service;

import com.ecm.core.exception.IllegalOperationException;
import com.ecm.core.repository.NodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BulkMetadataServiceTest {

    @Mock private TagService tagService;
    @Mock private CategoryService categoryService;
    @Mock private NodeService nodeService;
    @Mock private NodeRepository nodeRepository;
    @Mock private SecurityService securityService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AuditService auditService;

    private BulkMetadataService bulkMetadataService;

    @BeforeEach
    void setUp() {
        bulkMetadataService = new BulkMetadataService(
            tagService,
            categoryService,
            nodeService,
            nodeRepository,
            securityService,
            eventPublisher,
            auditService
        );
    }

    @Test
    @DisplayName("bulk metadata reports category failures when category service blocks a node")
    void bulkMetadataReportsCategoryFailures() {
        UUID nodeId = UUID.randomUUID();
        doThrow(new IllegalOperationException("Use the records management API to add categories on declared record 'doc.txt'"))
            .when(categoryService).addCategoriesToNode(nodeId.toString(), List.of("cat-1"));

        BulkMetadataService.BulkMetadataResult result = bulkMetadataService.applyMetadata(
            new BulkMetadataService.BulkMetadataRequest(
                List.of(nodeId),
                List.of(),
                List.of("cat-1"),
                null,
                false
            )
        );

        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailureCount());
        assertEquals(
            Map.of(nodeId.toString(), "Use the records management API to add categories on declared record 'doc.txt'"),
            result.getFailures()
        );
        verify(auditService).logEvent(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq("BULK_METADATA"), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }
}
