package com.ecm.core.sanity.check;

import com.ecm.core.entity.Document;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.sanity.SanityCheckReport;
import com.ecm.core.sanity.SanityChecker;
import com.ecm.core.service.ContentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Checks for database records that point to non-existent files in storage.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MissingFileChecker implements SanityChecker {

    private final NodeRepository nodeRepository;
    private final ContentService contentService;

    @Override
    public SanityCheckReport check(boolean fix) {
        SanityCheckReport report = SanityCheckReport.builder()
            .checkName(getName())
            .startTime(LocalDateTime.now())
            .status(SanityCheckReport.Status.SUCCESS)
            .build();

        List<Document> documents = nodeRepository.findAllDocuments(); // Need to add this query
        report.setItemsChecked(documents.size());

        for (Document doc : documents) {
            try {
                if (!contentService.exists(doc.getContentId())) {
                    String issue = String.format("Missing content for document %s (%s). Content ID: %s", 
                        doc.getId(), doc.getName(), doc.getContentId());
                    report.addIssue(issue);
                    log.warn(issue);

                    if (fix) {
                        // Fix strategy: Mark document as 'Corrupted' or move to special Lost&Found folder?
                        // For now, just logging. Deleting data is dangerous.
                        report.addFix("Manual intervention required: Restore from backup");
                    }
                }
            } catch (Exception e) {
                report.addIssue("Error checking document " + doc.getId() + ": " + e.getMessage());
            }
        }

        report.setEndTime(LocalDateTime.now());
        return report;
    }

    @Override
    public String getName() {
        return "Missing File Checker";
    }
}
