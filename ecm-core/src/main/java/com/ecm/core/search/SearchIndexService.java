package com.ecm.core.search;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Node;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.NodeRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchIndexService {
    
    private final DocumentRepository documentRepository;
    private final NodeRepository nodeRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private static final String INDEX_NAME = "ecm_documents";

    public boolean isDocumentIndexed(String documentId) {
        try {
            NodeDocument document = elasticsearchOperations.get(
                documentId,
                NodeDocument.class,
                IndexCoordinates.of(INDEX_NAME)
            );
            return document != null;
        } catch (Exception e) {
            log.warn("Failed to check index status for {}: {}", documentId, e.getMessage());
            return false;
        }
    }

    /**
     * Re-index a single document by id.
     */
    public void indexDocument(String documentId) {
        try {
            UUID id = UUID.fromString(documentId);
            Document document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
            updateDocument(document);
        } catch (IllegalArgumentException ex) {
            log.error("Failed to index document {}: {}", documentId, ex.getMessage());
            throw ex;
        }
    }

    /**
     * Delete a document from the index by id.
     */
    public void deleteDocument(String documentId) {
        try {
            UUID id = UUID.fromString(documentId);
            deleteNode(id);
        } catch (IllegalArgumentException ex) {
            log.error("Failed to delete document {} from index: {}", documentId, ex.getMessage());
            throw ex;
        }
    }
    
    @Transactional(readOnly = true)
    public void indexNode(Node node) {
        try {
            Node hydrated = fetchNode(node.getId());
            if (hydrated == null) {
                log.warn("Skipping index; node not found: {}", node.getId());
                return;
            }
            NodeDocument nodeDoc = NodeDocument.fromNode(hydrated);
            elasticsearchOperations.save(nodeDoc, IndexCoordinates.of(INDEX_NAME));
            log.debug("Indexed node: {}", hydrated.getId());
        } catch (Exception e) {
            log.error("Failed to index node: {}", node.getId(), e);
        }
    }
    
    @Transactional(readOnly = true)
    public void updateNode(Node node) {
        try {
            Node hydrated = fetchNode(node.getId());
            if (hydrated == null) {
                log.warn("Skipping index update; node not found: {}", node.getId());
                return;
            }
            NodeDocument nodeDoc = NodeDocument.fromNode(hydrated);
            elasticsearchOperations.save(nodeDoc, IndexCoordinates.of(INDEX_NAME));
            log.debug("Updated node in index: {}", hydrated.getId());
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
    
    @Transactional(readOnly = true)
    public void updateDocument(Document document) {
        try {
            Document hydrated = fetchDocument(document.getId());
            if (hydrated == null) {
                log.warn("Skipping document index update; document not found: {}", document.getId());
                return;
            }
            NodeDocument nodeDoc = NodeDocument.fromNode(hydrated);
            nodeDoc.setMimeType(hydrated.getMimeType());
            nodeDoc.setFileSize(hydrated.getFileSize());
            nodeDoc.setTextContent(hydrated.getTextContent());
            nodeDoc.setVersionLabel(hydrated.getVersionLabel());
            
            elasticsearchOperations.save(nodeDoc, IndexCoordinates.of(INDEX_NAME));
            log.debug("Updated document in index: {}", hydrated.getId());
        } catch (Exception e) {
            log.error("Failed to update document in index: {}", document.getId(), e);
        }
    }
    
    public void updateNodeChildren(Node parentNode) {
        try {
            if (parentNode == null || parentNode.getPath() == null) {
                return;
            }

            String pathPrefix = parentNode.getPath() + "/";
            Criteria criteria = new Criteria("path").startsWith(pathPrefix);
            Query query = new CriteriaQuery(criteria);

            SearchHits<NodeDocument> searchHits = elasticsearchOperations.search(
                query, NodeDocument.class, IndexCoordinates.of(INDEX_NAME));

            int updated = 0;
            int deleted = 0;

            for (SearchHit<NodeDocument> hit : searchHits) {
                NodeDocument childDoc = hit.getContent();
                if (childDoc == null || childDoc.getId() == null) {
                    continue;
                }

                UUID childId;
                try {
                    childId = UUID.fromString(childDoc.getId());
                } catch (IllegalArgumentException ex) {
                    log.warn("Invalid node id in search index: {}", childDoc.getId());
                    elasticsearchOperations.delete(childDoc.getId(), IndexCoordinates.of(INDEX_NAME));
                    deleted++;
                    continue;
                }

                Node child = nodeRepository.findByIdIncludeDeleted(childId).orElse(null);
                if (child == null) {
                    elasticsearchOperations.delete(childDoc.getId(), IndexCoordinates.of(INDEX_NAME));
                    deleted++;
                    continue;
                }

                NodeDocument refreshed = NodeDocument.fromNode(child);
                elasticsearchOperations.save(refreshed, IndexCoordinates.of(INDEX_NAME));
                updated++;
            }

            log.debug("Synced {} children of node {} (updated={}, deleted={})",
                searchHits.getTotalHits(), parentNode.getId(), updated, deleted);
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
                new Criteria("name").matches(queryText)
                    .or("description").matches(queryText)
                    .or("textContent").matches(queryText)
                    .or("extractedText").matches(queryText)
                    .or("metadata.extractedText").matches(queryText)
            );
        }
        
        // Add filters
        if (request.getFilters() != null) {
            if (request.getFilters().getMimeTypes() != null && !request.getFilters().getMimeTypes().isEmpty()) {
                query.addCriteria(
                    new Criteria("mimeType.keyword").in(request.getFilters().getMimeTypes())
                        .or(new Criteria("mimeType").in(request.getFilters().getMimeTypes()))
                );
            }
            
            if (request.getFilters().getNodeTypes() != null && !request.getFilters().getNodeTypes().isEmpty()) {
                query.addCriteria(
                    new Criteria("nodeType.keyword").in(request.getFilters().getNodeTypes())
                        .or(new Criteria("nodeType").in(request.getFilters().getNodeTypes()))
                );
            }
            
            if (request.getFilters().getCreatedBy() != null) {
                query.addCriteria(
                    new Criteria("createdBy.keyword").is(request.getFilters().getCreatedBy())
                        .or(new Criteria("createdBy").is(request.getFilters().getCreatedBy()))
                );
            }
            
            if (request.getFilters().getDateFrom() != null) {
                query.addCriteria(new Criteria("createdDate").greaterThanEqual(request.getFilters().getDateFrom()));
            }
            
            if (request.getFilters().getDateTo() != null) {
                query.addCriteria(new Criteria("createdDate").lessThanEqual(request.getFilters().getDateTo()));
            }

            if (request.getFilters().getModifiedFrom() != null) {
                query.addCriteria(new Criteria("lastModifiedDate").greaterThanEqual(request.getFilters().getModifiedFrom()));
            }

            if (request.getFilters().getModifiedTo() != null) {
                query.addCriteria(new Criteria("lastModifiedDate").lessThanEqual(request.getFilters().getModifiedTo()));
            }
            
            if (request.getFilters().getMinSize() != null) {
                query.addCriteria(new Criteria("fileSize").greaterThanEqual(request.getFilters().getMinSize()));
            }
            
            if (request.getFilters().getMaxSize() != null) {
                query.addCriteria(new Criteria("fileSize").lessThanEqual(request.getFilters().getMaxSize()));
            }

            if (request.getFilters().getCorrespondents() != null && !request.getFilters().getCorrespondents().isEmpty()) {
                query.addCriteria(
                    new Criteria("correspondent.keyword").in(request.getFilters().getCorrespondents())
                        .or(new Criteria("correspondent").in(request.getFilters().getCorrespondents()))
                );
            }
        }
        
        // Exclude deleted items
        query.addCriteria(new Criteria("deleted").is(false));
        
        // Add pagination
        if (request.getPageable() != null) {
            query.setPageable(request.getPageable().toPageable());
        }
        
        return query;
    }

    private Node fetchNode(UUID id) {
        return nodeRepository.findById(id).orElse(null);
    }

    private Document fetchDocument(UUID id) {
        return documentRepository.findById(id).orElse(null);
    }
}
