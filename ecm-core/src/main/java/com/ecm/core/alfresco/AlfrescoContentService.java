package com.ecm.core.alfresco;

import com.ecm.core.entity.Document;
import com.ecm.core.service.ContentService;
import com.ecm.core.service.NodeService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.UUID;

/**
 * Alfresco-compatible ContentService implementation
 */
@Slf4j
@Service("alfrescoContentService")
@RequiredArgsConstructor
public class AlfrescoContentService {
    
    private final ContentService contentService;
    private final NodeService nodeService;
    
    /**
     * Get content reader (Alfresco-compatible method)
     */
    public ContentReader getReader(NodeRef nodeRef, QName propertyQName) {
        try {
            Document document = (Document) nodeService.getNode(UUID.fromString(nodeRef.getId()));
            
            return new ContentReader() {
                @Override
                public InputStream getContentInputStream() {
                    try {
                        return contentService.getContent(document.getContentId());
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to get content", e);
                    }
                }
                
                @Override
                public String getMimetype() {
                    return document.getMimeType();
                }
                
                @Override
                public long getSize() {
                    return document.getFileSize();
                }
                
                @Override
                public String getEncoding() {
                    return document.getEncoding();
                }
                
                @Override
                public boolean exists() {
                    return document.getContentId() != null;
                }
            };
        } catch (Exception e) {
            log.error("Failed to get reader for node: {}", nodeRef, e);
            return null;
        }
    }
    
    /**
     * Get content writer (Alfresco-compatible method)
     */
    public ContentWriter getWriter(NodeRef nodeRef, QName propertyQName, boolean update) {
        Document document = (Document) nodeService.getNode(UUID.fromString(nodeRef.getId()));
        
        return new ContentWriter() {
            private String mimetype;
            private String encoding;
            
            @Override
            public void putContent(InputStream content) {
                try {
                    String contentId = contentService.storeContent(content, document.getName());
                    document.setContentId(contentId);
                    
                    if (mimetype != null) {
                        document.setMimeType(mimetype);
                    }
                    if (encoding != null) {
                        document.setEncoding(encoding);
                    }
                    
                    // Update document would happen here
                } catch (IOException e) {
                    throw new RuntimeException("Failed to write content", e);
                }
            }
            
            @Override
            public OutputStream getContentOutputStream() {
                // This would return an output stream that writes to content service
                throw new UnsupportedOperationException("Direct output stream not supported");
            }
            
            @Override
            public void setMimetype(String mimetype) {
                this.mimetype = mimetype;
            }
            
            @Override
            public void setEncoding(String encoding) {
                this.encoding = encoding;
            }
        };
    }
    
    /**
     * Transform content (Alfresco-compatible method)
     */
    public void transform(ContentReader reader, ContentWriter writer, TransformationOptions options) {
        log.info("Transform requested from {} to {}", reader.getMimetype(), options.getTargetMimetype());
        
        // This would integrate with the conversion service
        throw new UnsupportedOperationException("Transformation not yet implemented");
    }
}

interface ContentReader {
    InputStream getContentInputStream();
    String getMimetype();
    long getSize();
    String getEncoding();
    boolean exists();
}

interface ContentWriter {
    void putContent(InputStream content);
    OutputStream getContentOutputStream();
    void setMimetype(String mimetype);
    void setEncoding(String encoding);
}

@Data
class TransformationOptions {
    private String sourceMimetype;
    private String targetMimetype;
    private Map<String, Object> options;
}
