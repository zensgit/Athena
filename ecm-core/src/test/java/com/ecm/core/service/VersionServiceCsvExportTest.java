package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.entity.Version;
import com.ecm.core.entity.Version.VersionStatus;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.VersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CSV export of version history. Exercises the reused READ-permission gate (export === view),
 * the header/row shape aligned to VersionDto, RFC-4180 escaping, and the majorOnly filter pass-through.
 */
@ExtendWith(MockitoExtension.class)
class VersionServiceCsvExportTest {

    @Mock private VersionRepository versionRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private ContentService contentService;
    @Mock private SecurityService securityService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private VersionLabelService versionLabelService;
    @Mock private ContentReferenceService contentReferenceService;

    private VersionService versionService;

    private final UUID documentId = UUID.fromString("11111111-1111-4111-8111-111111111111");

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
    }

    private Document document() {
        Document document = new Document();
        document.setId(documentId);
        document.setName("report.pdf");
        return document;
    }

    private Version version(Document document, String label, String comment, long size,
                            VersionStatus status, String mime, boolean major) {
        Version version = new Version();
        version.setId(UUID.randomUUID());
        version.setDocument(document);
        version.setVersionLabel(label);
        version.setComment(comment);
        version.setFileSize(size);
        version.setStatus(status);
        version.setMimeType(mime);
        version.setMajorVersionFlag(major);
        version.setCreatedBy("alice");
        version.setCreatedDate(LocalDateTime.of(2026, 5, 25, 9, 30, 0));
        return version;
    }

    @Test
    @DisplayName("export produces a header row, version rows, and RFC-4180 escaping")
    void exportProducesHeaderAndEscapedRows() {
        Document document = document();
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(securityService.hasPermission(document, PermissionType.READ)).thenReturn(true);
        when(versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId)).thenReturn(List.of(
            version(document, "1.1", "Reviewed, approved by \"legal\"", 2048L, VersionStatus.RELEASED, "application/pdf", false),
            version(document, "1.0", "initial", 1024L, VersionStatus.RELEASED, "application/pdf", true)
        ));

        String csv = versionService.exportVersionHistoryCsv(documentId, false);

        assertTrue(csv.startsWith("Version Label,Created Date,Created By,Comment,Size (bytes),Major,MIME Type,Status\n"),
            "header row columns must be in the agreed order");
        // Comment with a comma + embedded quotes must be quoted with doubled quotes (RFC-4180).
        assertTrue(csv.contains("\"Reviewed, approved by \"\"legal\"\"\""),
            "comment with comma/quotes must be CSV-escaped");
        assertTrue(csv.contains("2048"), "size in bytes present");
        assertTrue(csv.contains("application/pdf"), "mime type present");
        assertTrue(csv.contains("RELEASED"), "status present");
        assertTrue(csv.contains(",true,"), "major flag rendered");
    }

    @Test
    @DisplayName("export propagates SecurityException when caller lacks READ (export === view)")
    void exportDeniedWithoutRead() {
        Document document = document();
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(securityService.hasPermission(document, PermissionType.READ)).thenReturn(false);

        assertThrows(SecurityException.class, () -> versionService.exportVersionHistoryCsv(documentId, false));
    }

    @Test
    @DisplayName("export with majorOnly uses the major-versions query")
    void exportMajorOnlyUsesMajorQuery() {
        Document document = document();
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(securityService.hasPermission(document, PermissionType.READ)).thenReturn(true);
        when(versionRepository.findMajorVersions(documentId)).thenReturn(List.of(
            version(document, "1.0", "initial", 1024L, VersionStatus.RELEASED, "application/pdf", true)
        ));

        String csv = versionService.exportVersionHistoryCsv(documentId, true);

        assertTrue(csv.contains("1.0"));
        verify(versionRepository).findMajorVersions(documentId);
    }

    @Test
    @DisplayName("export throws when the document does not exist")
    void exportDocumentNotFound() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> versionService.exportVersionHistoryCsv(documentId, false));
    }
}
