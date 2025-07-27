package com.ecm.core.controller;

import com.ecm.core.search.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Search APIs")
public class SearchController {
    
    private final SearchIndexService searchIndexService;
    
    @PostMapping
    @Operation(summary = "Search documents", description = "Search for documents using Elasticsearch")
    public ResponseEntity<List<NodeDocument>> search(@RequestBody SearchRequest request) {
        List<NodeDocument> results = searchIndexService.search(request.getQuery(), request);
        return ResponseEntity.ok(results);
    }
    
    @GetMapping("/quick")
    @Operation(summary = "Quick search", description = "Quick search with simple query string")
    public ResponseEntity<List<NodeDocument>> quickSearch(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        SearchRequest request = new SearchRequest();
        request.setQuery(q);
        request.setPageable(PageRequest.of(page, size));
        
        List<NodeDocument> results = searchIndexService.search(q, request);
        return ResponseEntity.ok(results);
    }
}