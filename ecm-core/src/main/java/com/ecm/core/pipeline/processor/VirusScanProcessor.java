package com.ecm.core.pipeline.processor;

import com.ecm.core.integration.antivirus.AntivirusService;
import com.ecm.core.integration.antivirus.AntivirusService.AntivirusException;
import com.ecm.core.integration.antivirus.AntivirusService.VirusDetectedException;
import com.ecm.core.integration.antivirus.AntivirusService.VirusScanResult;
import com.ecm.core.pipeline.DocumentContext;
import com.ecm.core.pipeline.DocumentProcessor;
import com.ecm.core.pipeline.ProcessingResult;
import com.ecm.core.service.ContentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * Virus Scan Processor (Order: 150)
 *
 * Scans uploaded content for viruses using ClamAV.
 * Runs after ContentStorageProcessor (100) so content is already stored.
 * If virus is detected, deletes the stored content and terminates pipeline.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VirusScanProcessor implements DocumentProcessor {

    private final AntivirusService antivirusService;
    private final ContentService contentService;

    @Override
    public int getOrder() {
        return 150;
    }

    @Override
    public ProcessingResult process(DocumentContext context) {
        // Skip if antivirus is disabled
        if (!antivirusService.isEnabled()) {
            log.debug("Antivirus scanning is disabled, skipping for: {}", context.getOriginalFilename());
            return ProcessingResult.builder()
                    .status(ProcessingResult.Status.SKIPPED)
                    .message("Antivirus scanning is disabled")
                    .build();
        }

        // Ensure content has been stored
        String contentId = context.getContentId();
        if (contentId == null || contentId.isEmpty()) {
            log.warn("No contentId in context, cannot scan for viruses");
            return ProcessingResult.builder()
                    .status(ProcessingResult.Status.SKIPPED)
                    .message("No content to scan")
                    .build();
        }

        long startTime = System.currentTimeMillis();
        String filename = context.getOriginalFilename();

        try {
            // Get content stream for scanning
            try (InputStream contentStream = contentService.getContent(contentId)) {
                VirusScanResult result = antivirusService.scan(contentStream, filename, null);

                long processingTime = System.currentTimeMillis() - startTime;

                if (result.isClean()) {
                    log.info("Virus scan CLEAN for '{}' (contentId: {}) in {}ms",
                            filename, contentId, processingTime);

                    return ProcessingResult.builder()
                            .status(ProcessingResult.Status.SUCCESS)
                            .processingTimeMs(processingTime)
                            .message("Virus scan passed - no threats detected")
                            .build();

                } else if (result.wasSkipped()) {
                    return ProcessingResult.builder()
                            .status(ProcessingResult.Status.SKIPPED)
                            .message(result.message())
                            .build();

                } else if (result.isInfected()) {
                    // This shouldn't happen in reject mode (exception thrown), but handle quarantine mode
                    log.warn("Virus detected in '{}' (contentId: {}): {}",
                            filename, contentId, result.threatName());

                    // Delete the infected content
                    deleteInfectedContent(contentId, filename);

                    context.addError(getName(), "Virus detected: " + result.threatName());
                    context.stopProcessing();

                    return ProcessingResult.fatal(
                            String.format("Virus detected: %s. File rejected.", result.threatName()));
                }

                // Shouldn't reach here
                return ProcessingResult.builder()
                        .status(ProcessingResult.Status.SUCCESS)
                        .processingTimeMs(processingTime)
                        .build();
            }

        } catch (VirusDetectedException e) {
            // Virus detected in reject mode - delete content and terminate
            log.error("VIRUS REJECTED: '{}' contains {} - deleting content",
                    e.getFilename(), e.getThreatName());

            deleteInfectedContent(contentId, filename);

            context.addError(getName(), e.getMessage());
            context.stopProcessing();

            return ProcessingResult.fatal(
                    String.format("Upload rejected: virus detected (%s)", e.getThreatName()));

        } catch (AntivirusException e) {
            // Scan error - log but don't block upload (fail-open policy)
            // Change to fail-closed by returning fatal() instead
            log.warn("Virus scan unavailable for '{}': {} (upload allowed)", filename, e.getMessage());

            // Fail-open: allow upload to continue (do not mark pipeline as failed)
            return ProcessingResult.builder()
                    .status(ProcessingResult.Status.SKIPPED)
                    .message("Virus scan unavailable: " + e.getMessage())
                    .build();

        } catch (IOException e) {
            log.warn("Failed to read content for virus scan: {} (upload allowed)", e.getMessage());

            // Fail-open: allow upload to continue (do not mark pipeline as failed)
            return ProcessingResult.builder()
                    .status(ProcessingResult.Status.SKIPPED)
                    .message("Could not scan content: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Delete infected content from storage.
     */
    private void deleteInfectedContent(String contentId, String filename) {
        try {
            contentService.deleteContent(contentId);
            log.info("Deleted infected content: {} (filename: {})", contentId, filename);
        } catch (IOException e) {
            log.error("Failed to delete infected content {}: {}", contentId, e.getMessage());
        }
    }
}
