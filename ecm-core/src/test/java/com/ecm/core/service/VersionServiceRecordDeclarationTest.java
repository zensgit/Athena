package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.entity.Version;
import com.ecm.core.exception.IllegalOperationException;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.VersionRepository;
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
class VersionServiceRecordDeclarationTest {

    @Mock private VersionRepository versionRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private ContentService contentService;
    @Mock private SecurityService securityService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private VersionLabelService versionLabelService;
    @Mock private ContentReferenceService contentReferenceService;
    @Mock private RecordsManagementService recordsManagementService;

    private VersionService versionService;

    @BeforeEach
    void setUp() {
        versionService = new VersionService(
            versionRepository,
            documentRepository,
            contentService,
            securityService,
            eventPublisher,
            versionLabelService,
            contentReferenceService
        );
        ReflectionTestUtils.setField(versionService, "recordsManagementService", recordsManagementService);
    }

    @Test
    @DisplayName("deleteVersion rejects declared record")
    void deleteVersionRejectsDeclaredRecord() {
        UUID versionId = UUID.randomUUID();

        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName("report.pdf");
        document.setPath("/Sites/Finance/report.pdf");
        document.setArchiveStatus(Node.ArchiveStatus.LIVE);

        Version version = new Version();
        version.setId(versionId);
        version.setDocument(document);
        version.setVersionNumber(1);

        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(securityService.hasPermission(document, PermissionType.READ)).thenReturn(true);
        when(securityService.hasPermission(document, PermissionType.DELETE)).thenReturn(true);
        doThrow(new IllegalOperationException("record")).when(recordsManagementService)
            .assertDirectMutationAllowed(document, "delete version");

        assertThrows(IllegalOperationException.class, () -> versionService.deleteVersion(versionId));

        verify(versionRepository, never()).delete(version);
    }
}
