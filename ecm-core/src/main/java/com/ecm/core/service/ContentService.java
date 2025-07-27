package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Version;
import com.ecm.core.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ContentService {
    
    private final DocumentRepository documentRepository;
    
    @Value("${ecm.storage.root-path}")
    private String rootPath;
    
    @Value("${ecm.storage.temp-path}")
    private String tempPath;
    
    private final Tika tika = new Tika();
    
    public String storeContent(MultipartFile file) throws IOException {
        return storeContent(file.getInputStream(), file.getOriginalFilename());
    }
    
    public String storeContent(InputStream inputStream, String filename) throws IOException {
        // Create temp file
        Path tempFile = Files.createTempFile(Paths.get(tempPath), "upload_", "_" + filename);
        
        try {
            // Copy to temp file and calculate hash
            String contentHash;
            try (InputStream is = inputStream;
                 OutputStream os = Files.newOutputStream(tempFile)) {
                contentHash = copyAndHash(is, os);
            }
            
            // Check if content already exists (deduplication)
            String existingContentId = findExistingContent(contentHash);
            if (existingContentId != null) {
                log.debug("Content already exists with hash: {}, reusing content ID: {}", 
                    contentHash, existingContentId);
                Files.deleteIfExists(tempFile);
                return existingContentId;
            }
            
            // Generate content ID and storage path
            String contentId = generateContentId();
            Path storagePath = getStoragePath(contentId);
            
            // Ensure parent directories exist
            Files.createDirectories(storagePath.getParent());
            
            // Move temp file to final location
            Files.move(tempFile, storagePath, StandardCopyOption.ATOMIC_MOVE);
            
            log.info("Stored content: {} at path: {}", contentId, storagePath);
            
            return contentId;
            
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
    
    public InputStream getContent(String contentId) throws IOException {
        Path contentPath = getStoragePath(contentId);
        
        if (!Files.exists(contentPath)) {
            throw new FileNotFoundException("Content not found: " + contentId);
        }
        
        return Files.newInputStream(contentPath);
    }
    
    public void deleteContent(String contentId) throws IOException {
        // Check if content is still referenced
        if (isContentReferenced(contentId)) {
            log.warn("Content {} is still referenced, skipping deletion", contentId);
            return;
        }
        
        Path contentPath = getStoragePath(contentId);
        Files.deleteIfExists(contentPath);
        
        log.info("Deleted content: {}", contentId);
    }
    
    public Map<String, Object> extractMetadata(String contentId) throws IOException {
        Map<String, Object> metadata = new HashMap<>();
        
        try (InputStream is = getContent(contentId)) {
            Metadata tikaMetadata = new Metadata();
            BodyContentHandler handler = new BodyContentHandler(-1);
            Parser parser = new AutoDetectParser();
            ParseContext context = new ParseContext();
            
            try {
                parser.parse(is, handler, tikaMetadata, context);
                
                // Extract metadata
                for (String name : tikaMetadata.names()) {
                    metadata.put(name, tikaMetadata.get(name));
                }
                
                // Extract text content
                String textContent = handler.toString();
                if (textContent != null && !textContent.trim().isEmpty()) {
                    metadata.put("textContent", textContent);
                }
                
            } catch (SAXException | TikaException e) {
                log.error("Error extracting metadata from content: {}", contentId, e);
            }
        }
        
        return metadata;
    }
    
    public String detectMimeType(String contentId) throws IOException {
        try (InputStream is = getContent(contentId)) {
            return tika.detect(is);
        }
    }
    
    public String detectMimeType(InputStream inputStream, String filename) throws IOException {
        Metadata metadata = new Metadata();
        if (filename != null) {
            metadata.set(Metadata.RESOURCE_NAME_KEY, filename);
        }
        return tika.detect(inputStream, metadata);
    }
    
    public long getContentSize(String contentId) throws IOException {
        Path contentPath = getStoragePath(contentId);
        return Files.size(contentPath);
    }
    
    public String copyContent(String sourceContentId) throws IOException {
        try (InputStream is = getContent(sourceContentId)) {
            return storeContent(is, "copy");
        }
    }
    
    public File getTempFile(String contentId) throws IOException {
        Path tempFile = Files.createTempFile(Paths.get(tempPath), "content_", "_" + contentId);
        
        try (InputStream is = getContent(contentId);
             OutputStream os = Files.newOutputStream(tempFile)) {
            is.transferTo(os);
        }
        
        return tempFile.toFile();
    }
    
    public void streamContent(String contentId, OutputStream outputStream) throws IOException {
        try (InputStream is = getContent(contentId)) {
            is.transferTo(outputStream);
        }
    }
    
    public String updateContent(String oldContentId, InputStream newContent, String filename) 
            throws IOException {
        // Store new content
        String newContentId = storeContent(newContent, filename);
        
        // Delete old content if not referenced
        if (!isContentReferenced(oldContentId)) {
            try {
                deleteContent(oldContentId);
            } catch (IOException e) {
                log.warn("Failed to delete old content: {}", oldContentId, e);
            }
        }
        
        return newContentId;
    }
    
    private String copyAndHash(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        
        try (var digestStream = new DigestOutputStream(output, DigestUtils.getSha256Digest())) {
            while ((bytesRead = input.read(buffer)) != -1) {
                digestStream.write(buffer, 0, bytesRead);
            }
            
            return DigestUtils.sha256Hex(digestStream.getMessageDigest().digest());
        }
    }
    
    private String findExistingContent(String contentHash) {
        return documentRepository.findByContentHash(contentHash)
            .map(Document::getContentId)
            .orElse(null);
    }
    
    private boolean isContentReferenced(String contentId) {
        // Check if any documents or versions reference this content
        long docCount = documentRepository.count((root, query, cb) -> 
            cb.equal(root.get("contentId"), contentId));
        
        if (docCount > 0) {
            return true;
        }
        
        // Also check versions
        // This would require a VersionRepository query
        
        return false;
    }
    
    private String generateContentId() {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return timestamp + "_" + uuid;
    }
    
    private Path getStoragePath(String contentId) {
        // Use content ID to create a directory structure
        // e.g., contentId = "20231201120000_abc123" -> /root/2023/12/01/20231201120000_abc123
        String year = contentId.substring(0, 4);
        String month = contentId.substring(4, 6);
        String day = contentId.substring(6, 8);
        
        return Paths.get(rootPath, year, month, day, contentId);
    }
    
    private static class DigestOutputStream extends FilterOutputStream {
        private final java.security.MessageDigest digest;
        
        public DigestOutputStream(OutputStream out, java.security.MessageDigest digest) {
            super(out);
            this.digest = digest;
        }
        
        @Override
        public void write(int b) throws IOException {
            digest.update((byte) b);
            super.write(b);
        }
        
        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            digest.update(b, off, len);
            super.write(b, off, len);
        }
        
        public java.security.MessageDigest getMessageDigest() {
            return digest;
        }
    }
}