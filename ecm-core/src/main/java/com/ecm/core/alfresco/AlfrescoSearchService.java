package com.ecm.core.alfresco;

import com.ecm.core.search.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Alfresco-compatible SearchService implementation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlfrescoSearchService {
    
    private final SearchIndexService searchIndexService;
    
    /**
     * Execute search query (Alfresco-compatible method)
     */
    public ResultSet query(SearchParameters searchParameters) {
        log.debug("Executing Alfresco search query: {}", searchParameters.getQuery());
        
        // Convert Alfresco search parameters to our search request
        SearchRequest request = new SearchRequest();
        request.setQuery(searchParameters.getQuery());
        
        if (searchParameters.getMaxItems() > 0) {
            request.setPageable(org.springframework.data.domain.PageRequest.of(
                0, searchParameters.getMaxItems()));
        }
        
        List<NodeDocument> results = searchIndexService.search(
            searchParameters.getQuery(), request);
        
        // Convert results to Alfresco format
        List<NodeRef> nodeRefs = results.stream()
            .map(doc -> new NodeRef(doc.getId()))
            .collect(Collectors.toList());
        
        return new ResultSet(nodeRefs, results.size());
    }
    
    /**
     * Execute Lucene query (Alfresco-compatible method)
     */
    public ResultSet query(StoreRef store, String language, String query) {
        SearchParameters params = new SearchParameters();
        params.setQuery(query);
        params.setLanguage(SearchLanguage.valueOf(language.toUpperCase()));
        params.addStore(store);
        
        return query(params);
    }
}

@Data
class SearchParameters {
    private String query;
    private SearchLanguage language = SearchLanguage.LUCENE;
    private List<StoreRef> stores = new ArrayList<>();
    private int maxItems = -1;
    private int skipCount = 0;
    private List<SortDefinition> sortDefinitions = new ArrayList<>();
    
    public void addStore(StoreRef store) {
        stores.add(store);
    }
    
    public void addSort(String field, boolean ascending) {
        sortDefinitions.add(new SortDefinition(field, ascending));
    }
}

enum SearchLanguage {
    LUCENE,
    FTS_ALFRESCO,
    XPATH,
    CMIS_STRICT,
    CMIS_ALFRESCO
}

@Data
@AllArgsConstructor
class StoreRef {
    private String protocol;
    private String identifier;
    
    public static StoreRef STORE_REF_WORKSPACE_SPACESSTORE = 
        new StoreRef("workspace", "SpacesStore");
}

@Data
@AllArgsConstructor
class SortDefinition {
    private String field;
    private boolean ascending;
}

@Data
@AllArgsConstructor
class ResultSet {
    private List<NodeRef> nodeRefs;
    private long numberFound;
    
    public List<NodeRef> getNodeRefs() {
        return nodeRefs;
    }
    
    public boolean hasMore() {
        return false; // Simplified
    }
}