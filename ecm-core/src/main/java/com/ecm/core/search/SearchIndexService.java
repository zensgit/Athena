package com.ecm.core.search;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Node;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchIndexService {
    
    private final ElasticsearchOperations elasticsearchOperations;
    private static final String INDEX_NAME = "ecm_documents";
    
    public void indexNode(Node node) {
        try {
            NodeDocument nodeDoc = NodeDocument.fromNode(node);
            elasticsearchOperations.save(nodeDoc, IndexCoordinates.of(INDEX_NAME));
            log.debug("Indexed node: {}", node.getId());
        } catch (Exception e) {
            log.error("Failed to index node: {}", node.getId(), e);
        }
    }
    
    public void updateNode(Node node) {
        try {
            NodeDocument nodeDoc = NodeDocument.fromNode(node);
            elasticsearchOperations.save(nodeDoc, IndexCoordinates.of(INDEX_NAME));
            log.debug("Updated node in index: {}", node.getId());
        } catch (Exception e) {
            log.error("Failed to update node in index: {}", node.getId(), e);
        }
    }
    
    public void deleteNode(UUID nodeId) {
        try {
            elasticsearchOperations.delete(nodeId.toString(), IndexCoordinates.of(INDEX_NAME));
            log.debug("Deleted node from index: {}", nodeId);
        } catch (Exception e) {
            log.error("Failed to delete node from index: {}", nodeId, e);
        }
    }
    
    public void updateDocument(Document document) {
        try {
            NodeDocument nodeDoc = NodeDocument.fromNode(document);
            // Add document-specific fields
            nodeDoc.setMimeType(document.getMimeType());
            nodeDoc.setFileSize(document.getFileSize());
            nodeDoc.setTextContent(document.getTextContent());
            nodeDoc.setVersionLabel(document.getVersionLabel());
            
            elasticsearchOperations.save(nodeDoc, IndexCoordinates.of(INDEX_NAME));
            log.debug("Updated document in index: {}", document.getId());
        } catch (Exception e) {
            log.error("Failed to update document in index: {}", document.getId(), e);
        }
    }
    
    public void updateNodeChildren(Node parentNode) {
        try {
            // Find all children by path prefix
            Criteria criteria = new Criteria("path").startsWith(parentNode.getPath() + "/");
            Query query = new CriteriaQuery(criteria);
            
            SearchHits<NodeDocument> searchHits = elasticsearchOperations.search(
                query, NodeDocument.class, IndexCoordinates.of(INDEX_NAME));
            
            // Update each child's path
            for (SearchHit<NodeDocument> hit : searchHits) {
                NodeDocument child = hit.getContent();
                // Update path would be done here
                elasticsearchOperations.save(child, IndexCoordinates.of(INDEX_NAME));
            }
            
            log.debug("Updated {} children of node: {}", searchHits.getTotalHits(), parentNode.getId());
        } catch (Exception e) {
            log.error("Failed to update children of node: {}", parentNode.getId(), e);
        }
    }
    
    public List<NodeDocument> search(String queryText, SearchRequest searchRequest) {
        try {
            Query query = buildQuery(queryText, searchRequest);
            SearchHits<NodeDocument> searchHits = elasticsearchOperations.search(
                query, NodeDocument.class, IndexCoordinates.of(INDEX_NAME));
            
            return searchHits.stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Search failed for query: {}", queryText, e);
            return List.of();
        }
    }
    
    private Query buildQuery(String queryText, SearchRequest request) {
        CriteriaQuery query = new CriteriaQuery(new Criteria());
        
        // Add text search
        if (queryText != null && !queryText.isEmpty()) {
            query.addCriteria(
                new Criteria("name").contains(queryText)
                    .or("description").contains(queryText)
                    .or("textContent").contains(queryText)
            );
        }
        
        // Add filters
        if (request.getFilters() != null) {
            if (request.getFilters().getMimeTypes() != null && !request.getFilters().getMimeTypes().isEmpty()) {
                query.addCriteria(new Criteria("mimeType").in(request.getFilters().getMimeTypes()));
            }
            
            if (request.getFilters().getNodeTypes() != null && !request.getFilters().getNodeTypes().isEmpty()) {
                query.addCriteria(new Criteria("nodeType").in(request.getFilters().getNodeTypes()));
            }
            
            if (request.getFilters().getCreatedBy() != null) {
                query.addCriteria(new Criteria("createdBy").is(request.getFilters().getCreatedBy()));
            }
            
            if (request.getFilters().getDateFrom() != null) {
                query.addCriteria(new Criteria("createdDate").greaterThanEqual(request.getFilters().getDateFrom()));
            }
            
            if (request.getFilters().getDateTo() != null) {
                query.addCriteria(new Criteria("createdDate").lessThanEqual(request.getFilters().getDateTo()));
            }
            
            if (request.getFilters().getMinSize() != null) {
                query.addCriteria(new Criteria("fileSize").greaterThanEqual(request.getFilters().getMinSize()));
            }
            
            if (request.getFilters().getMaxSize() != null) {
                query.addCriteria(new Criteria("fileSize").lessThanEqual(request.getFilters().getMaxSize()));
            }
        }
        
        // Exclude deleted items
        query.addCriteria(new Criteria("deleted").is(false));
        
        // Add pagination
        if (request.getPageable() != null) {
            query.setPageable(request.getPageable());
        }
        
        return query;
    }
}