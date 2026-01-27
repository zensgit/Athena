package com.ecm.core.integration.email;

import com.ecm.core.entity.Document;
import com.ecm.core.service.ContentService;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.TagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.mail.RFC822Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for ingesting and processing email files (EML, MSG).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailIngestionService {

    private final NodeService nodeService;
    private final ContentService contentService;
    private final TagService tagService;

    @Transactional
    public Document ingestEmail(MultipartFile file, UUID parentFolderId) {
        return ingestEmail(file, parentFolderId, Map.of());
    }

    @Transactional
    public Document ingestEmail(MultipartFile file, UUID parentFolderId, Map<String, Object> mailProperties) {
        try {
            // 1. Store content
            String contentId = contentService.storeContent(file);

            // 2. Parse Email Metadata using Tika
            Metadata metadata = new Metadata();
            BodyContentHandler handler = new BodyContentHandler(-1); // No limit
            RFC822Parser parser = new RFC822Parser();
            
            try (InputStream stream = file.getInputStream()) {
                parser.parse(stream, handler, metadata, new ParseContext());
            }

            // 3. Create Document
            Document doc = new Document();
            String subject = metadata.get("subject");
            doc.setName(subject != null && !subject.isBlank() ? subject + ".eml" : file.getOriginalFilename());
            doc.setMimeType("message/rfc822");
            doc.setFileSize(file.getSize());
            doc.setContentId(contentId);

            // 4. Map Metadata
            Map<String, Object> docMeta = new HashMap<>();
            docMeta.put("email:from", metadata.get("Author")); // Tika maps From -> Author
            docMeta.put("email:to", metadata.get("Message-To"));
            docMeta.put("email:cc", metadata.get("Message-Cc"));
            docMeta.put("email:subject", metadata.get("subject"));
            docMeta.put("email:sentDate", metadata.get("Creation-Date"));
            
            doc.setMetadata(docMeta);
            if (mailProperties != null && !mailProperties.isEmpty()) {
                doc.getProperties().putAll(mailProperties);
            }

            Document savedDoc = (Document) nodeService.createNode(doc, parentFolderId);

            // 5. Add 'Email' tag
            tagService.addTagToNode(savedDoc.getId().toString(), "Email");

            log.info("Ingested email: {} (From: {})", savedDoc.getName(), docMeta.get("email:from"));
            return savedDoc;

        } catch (Exception e) {
            log.error("Failed to ingest email", e);
            throw new RuntimeException("Email ingestion failed", e);
        }
    }
}
