package com.ecm.core.search;

import com.ecm.core.entity.Node;
import com.ecm.core.entity.Node.NodeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "ecm_documents")
@Setting(replicas = 1, shards = 2)
public class NodeDocument {
    
    @Id
    private String id;
    
    @Field(type = FieldType.Text, analyzer = "standard")
    private String name;
    
    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;
    
    @Field(type = FieldType.Keyword)
    private String path;
    
    @Field(type = FieldType.Keyword)
    private NodeType nodeType;
    
    @Field(type = FieldType.Keyword)
    private String parentId;
    
    @Field(type = FieldType.Keyword)
    private String mimeType;
    
    @Field(type = FieldType.Long)
    private Long fileSize;
    
    @Field(type = FieldType.Text, analyzer = "standard")
    private String textContent;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String content;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String extractedText;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String title;

    @Field(type = FieldType.Keyword)
    private String author;
    
    @Field(type = FieldType.Keyword)
    private String versionLabel;
    
    @Field(type = FieldType.Keyword)
    private Set<String> tags;
    
    @Field(type = FieldType.Keyword)
    private Set<String> categories;
    
    @Field(type = FieldType.Object)
    private Map<String, Object> properties;
    
    @Field(type = FieldType.Object)
    private Map<String, Object> metadata;
    
    @Field(type = FieldType.Keyword)
    private String createdBy;
    
    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis)
    private LocalDateTime createdDate;
    
    @Field(type = FieldType.Keyword)
    private String lastModifiedBy;
    
    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis)
    private LocalDateTime lastModifiedDate;
    
    @Field(type = FieldType.Boolean)
    private boolean locked;
    
    @Field(type = FieldType.Keyword)
    private String lockedBy;
    
    @Field(type = FieldType.Boolean)
    private boolean deleted;
    
    @Field(type = FieldType.Keyword)
    private String status;
    
    @Field(type = FieldType.Keyword)
    private Set<String> permissions;
    
    public static NodeDocument fromNode(Node node) {
        NodeDocument doc = new NodeDocument();
        doc.setId(node.getId().toString());
        doc.setName(node.getName());
        doc.setDescription(node.getDescription());
        doc.setPath(node.getPath());
        doc.setNodeType(node.getNodeType());
        doc.setParentId(node.getParent() != null ? node.getParent().getId().toString() : null);
        doc.setProperties(node.getProperties());
        doc.setMetadata(node.getMetadata());
        doc.setCreatedBy(node.getCreatedBy());
        doc.setCreatedDate(node.getCreatedDate());
        doc.setLastModifiedBy(node.getLastModifiedBy());
        doc.setLastModifiedDate(node.getLastModifiedDate());
        doc.setLocked(node.isLocked());
        doc.setLockedBy(node.getLockedBy());
        doc.setDeleted(node.isDeleted());
        doc.setStatus(node.getStatus().toString());

        if (node instanceof com.ecm.core.entity.Document document) {
            doc.setMimeType(document.getMimeType());
            doc.setFileSize(document.getFileSize());
            doc.setVersionLabel(document.getVersionLabel());
            // Store extracted text in both textContent and content for search fields
            doc.setTextContent(document.getTextContent());
            doc.setContent(document.getTextContent());
            doc.setExtractedText(document.getTextContent());
        }
        
        // Set tags and categories
        if (node.getTags() != null) {
            doc.setTags(node.getTags().stream()
                .map(tag -> tag.getName())
                .collect(Collectors.toSet()));
        }
        
        if (node.getCategories() != null) {
            doc.setCategories(node.getCategories().stream()
                .map(cat -> cat.getName())
                .collect(Collectors.toSet()));
        }
        
        return doc;
    }
}
