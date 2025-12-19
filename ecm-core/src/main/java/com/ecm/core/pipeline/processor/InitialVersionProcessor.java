package com.ecm.core.pipeline.processor;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Version;
import com.ecm.core.event.VersionCreatedEvent;
import com.ecm.core.pipeline.DocumentContext;
import com.ecm.core.pipeline.DocumentProcessor;
import com.ecm.core.pipeline.ProcessingResult;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.VersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Initial Version Processor (Order: 420)
 *
 * Ensures a newly uploaded document has an initial Version entry.
 * Pipeline uploads persist Document metadata but historically skipped creating Version rows,
 * resulting in empty version history. This processor creates version 1 referencing the
 * already-stored contentId (no duplicate content storage).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InitialVersionProcessor implements DocumentProcessor {

    private final VersionRepository versionRepository;
    private final DocumentRepository documentRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public int getOrder() {
        return 420;
    }

    @Override
    public boolean supports(DocumentContext context) {
        Document document = context.getDocument();
        return document != null
            && document.isVersioned()
            && document.getCurrentVersion() == null;
    }

    @Override
    public ProcessingResult process(DocumentContext context) {
        Document document = context.getDocument();
        if (document == null) {
            return ProcessingResult.skipped("Document not persisted yet");
        }

        if (document.getContentId() == null) {
            return ProcessingResult.fatal("Cannot create initial version without content");
        }

        Integer maxVersionNumber = versionRepository.findMaxVersionNumber(document.getId());
        if (maxVersionNumber != null && maxVersionNumber > 0) {
            if (document.getCurrentVersion() == null) {
                versionRepository.findByDocumentIdAndVersionNumber(document.getId(), maxVersionNumber)
                    .ifPresent(existing -> {
                        document.setCurrentVersion(existing);
                        document.setVersionLabel(existing.getVersionLabel());
                        documentRepository.save(document);
                    });
            }
            return ProcessingResult.skipped("Version history already exists");
        }

        long startTime = System.currentTimeMillis();

        try {
            int major = document.getMajorVersion() != null ? document.getMajorVersion() : 1;
            int minor = document.getMinorVersion() != null ? document.getMinorVersion() : 0;
            String versionLabel = major + "." + minor;

            Version version = new Version();
            version.setDocument(document);
            version.setVersionNumber(1);
            version.setMajorVersion(major);
            version.setMinorVersion(minor);
            version.setVersionLabel(versionLabel);
            version.setMajorVersionFlag(true);
            version.setContentId(document.getContentId());
            version.setMimeType(document.getMimeType());
            version.setFileSize(document.getFileSize() != null ? document.getFileSize() : 0L);
            version.setContentHash(document.getContentHash());
            version.setComment("Initial upload");

            Version saved = versionRepository.save(version);

            document.setCurrentVersion(saved);
            document.setVersionLabel(saved.getVersionLabel());
            documentRepository.save(document);

            context.setVersionLabel(saved.getVersionLabel());
            eventPublisher.publishEvent(new VersionCreatedEvent(saved, context.getUserId()));

            long processingTime = System.currentTimeMillis() - startTime;

            log.info("Created initial version {} for document {} in {}ms",
                saved.getVersionLabel(), document.getId(), processingTime);

            return ProcessingResult.builder()
                .status(ProcessingResult.Status.SUCCESS)
                .processingTimeMs(processingTime)
                .message("Created initial version: " + saved.getVersionLabel())
                .build()
                .withData("versionId", saved.getId())
                .withData("versionLabel", saved.getVersionLabel())
                .withData("versionNumber", saved.getVersionNumber());
        } catch (Exception e) {
            log.error("Failed to create initial version for document {}: {}", document.getId(), e.getMessage(), e);
            context.addError(getName(), e.getMessage());
            return ProcessingResult.fatal("Initial version creation failed: " + e.getMessage());
        }
    }
}

