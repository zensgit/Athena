package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.repository.NodeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BulkOperationServiceAuditTest {

    @Mock
    private NodeService nodeService;

    @Mock
    private NodeRepository nodeRepository;

    @Mock
    private TrashService trashService;

    @Mock
    private AuditService auditService;

    @Mock
    private SecurityService securityService;

    @InjectMocks
    private BulkOperationService bulkOperationService;

    @Test
    @DisplayName("Bulk delete emits completed audit event on full success")
    void bulkDeleteShouldAuditCompletedEvent() {
        UUID id = UUID.randomUUID();
        when(nodeRepository.findById(id)).thenReturn(Optional.of(documentNamed("doc-a")));
        when(securityService.getCurrentUser()).thenReturn("admin");

        BulkOperationService.BulkOperationResult result = bulkOperationService.bulkDelete(List.of(id));

        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailureCount()).isZero();
        verify(auditService).logEvent(
            eq("BULK_DELETE_COMPLETED"),
            eq(null),
            eq("BULK_OPERATIONS"),
            eq("admin"),
            eq("Bulk DELETE requested=1 success=1 failed=0")
        );
    }

    @Test
    @DisplayName("Bulk delete emits partial audit event when failures exist")
    void bulkDeleteShouldAuditPartialEvent() {
        UUID okId = UUID.randomUUID();
        UUID failedId = UUID.randomUUID();
        when(nodeRepository.findById(okId)).thenReturn(Optional.of(documentNamed("doc-ok")));
        when(nodeRepository.findById(failedId)).thenReturn(Optional.of(documentNamed("doc-failed")));
        doAnswer(invocation -> {
            UUID currentId = invocation.getArgument(0);
            if (failedId.equals(currentId)) {
                throw new IllegalStateException("trash unavailable");
            }
            return null;
        }).when(trashService).moveToTrash(any(UUID.class));
        when(securityService.getCurrentUser()).thenReturn("editor");

        BulkOperationService.BulkOperationResult result = bulkOperationService.bulkDelete(List.of(okId, failedId));

        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailureCount()).isEqualTo(1);

        ArgumentCaptor<String> detailsCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).logEvent(
            eq("BULK_DELETE_PARTIAL"),
            eq(null),
            eq("BULK_OPERATIONS"),
            eq("editor"),
            detailsCaptor.capture()
        );
        assertThat(detailsCaptor.getValue())
            .contains("requested=2")
            .contains("success=1")
            .contains("failed=1")
            .contains(failedId.toString());
    }

    private Document documentNamed(String name) {
        Document document = new Document();
        document.setName(name);
        return document;
    }
}
