package com.ecm.core.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * Ensures the Elasticsearch index exists.
 *
 * Elasticsearch is an acceleration layer and can be rebuilt from PostgreSQL, so this initializer
 * is best-effort and must not prevent the application from starting when ES is unavailable.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchIndexInitializer implements ApplicationRunner {

    private final ElasticsearchOperations elasticsearchOperations;

    @Value("${ecm.search.enabled:true}")
    private boolean searchEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!searchEnabled) {
            return;
        }

        try {
            IndexOperations indexOps = elasticsearchOperations.indexOps(NodeDocument.class);
            if (!indexOps.exists()) {
                indexOps.create();
                indexOps.putMapping();
            }
        } catch (LinkageError e) {
            log.warn("Elasticsearch index init skipped due to missing/invalid client dependency: {}", e.toString());
        } catch (Exception e) {
            log.warn("Elasticsearch index init failed (continuing without blocking startup): {}", e.getMessage());
        }
    }
}
