package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for PDF manipulation operations (Merge, Split).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfManipulationService {

    private final NodeService nodeService;
    private final ContentService contentService;
    private final NodeRepository nodeRepository;

    /**
     * Merge multiple PDF documents into one new document.
     */
    @Transactional
    public Document mergePdfs(List<UUID> documentIds, String newName, UUID targetFolderId) throws IOException {
        log.info("Merging {} documents into '{}'", documentIds.size(), newName);

        if (documentIds == null || documentIds.size() < 2) {
            throw new IllegalArgumentException("At least 2 documents are required for merge");
        }

        PDFMergerUtility merger = new PDFMergerUtility();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        merger.setDestinationStream(outputStream);

        List<PDDocument> openDocuments = new ArrayList<>();

        try {
            for (UUID docId : documentIds) {
                Document doc = (Document) nodeService.getNode(docId);
                
                if (!"application/pdf".equals(doc.getMimeType())) {
                    throw new IllegalArgumentException("Document " + doc.getName() + " is not a PDF");
                }

                InputStream content = contentService.getContent(doc.getContentId());
                // We need to keep streams open for merger, or load PDDocument
                // Loading PDDocument is safer for manipulation
                PDDocument pdDoc = PDDocument.load(content);
                openDocuments.add(pdDoc);
                merger.addSource(content); // This might need file or stream, PDDocument approach below is manual
            }

            // Using manual append to ensure control
            PDDocument resultDoc = new PDDocument();
            for (PDDocument pdDoc : openDocuments) {
                for (int i = 0; i < pdDoc.getNumberOfPages(); i++) {
                    resultDoc.addPage(pdDoc.getPage(i));
                }
            }
            
            resultDoc.save(outputStream);
            resultDoc.close();

            // Create new document entry
            long size = outputStream.size();
            ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
            
            // Store content
            // Assuming we reuse NodeService/ContentService logic essentially
            // But we need to act as "Upload"
            
            Document newDoc = nodeService.createDocument(
                newName.endsWith(".pdf") ? newName : newName + ".pdf",
                "application/pdf",
                size,
                targetFolderId
            );
            
            String contentId = contentService.store(inputStream);
            newDoc.setContentId(contentId);
            return nodeRepository.save(newDoc);

        } finally {
            for (PDDocument doc : openDocuments) {
                try { doc.close(); } catch (Exception e) { /* ignore */ }
            }
        }
    }

    /**
     * Split a PDF document into separate files (one per page).
     * Returns list of created document IDs.
     */
    @Transactional
    public List<Document> splitPdf(UUID documentId, UUID targetFolderId) throws IOException {
        log.info("Splitting document {}", documentId);

        Document sourceDoc = (Document) nodeService.getNode(documentId);
        if (!"application/pdf".equals(sourceDoc.getMimeType())) {
            throw new IllegalArgumentException("Document is not a PDF");
        }

        InputStream content = contentService.getContent(sourceDoc.getContentId());
        PDDocument pdDoc = PDDocument.load(content);
        
        List<Document> createdDocs = new ArrayList<>();

        try {
            int totalPages = pdDoc.getNumberOfPages();
            String baseName = sourceDoc.getName().replace(".pdf", "");

            for (int i = 0; i < totalPages; i++) {
                PDDocument singlePageDoc = new PDDocument();
                singlePageDoc.addPage(pdDoc.getPage(i));
                
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                singlePageDoc.save(out);
                singlePageDoc.close();
                
                String newName = String.format("%s_page_%d.pdf", baseName, i + 1);
                long size = out.size();
                
                Document newDoc = nodeService.createDocument(
                    newName,
                    "application/pdf",
                    size,
                    targetFolderId
                );
                
                String contentId = contentService.store(new ByteArrayInputStream(out.toByteArray()));
                newDoc.setContentId(contentId);
                createdDocs.add(nodeRepository.save(newDoc));
            }
            
            return createdDocs;

        } finally {
            pdDoc.close();
        }
    }
}