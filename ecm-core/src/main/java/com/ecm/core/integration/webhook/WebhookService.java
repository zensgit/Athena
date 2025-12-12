package com.ecm.core.integration.webhook;

import com.ecm.core.entity.Document;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Service to execute Webhook calls triggered by the Rule Engine.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final RestTemplate restTemplate;

    @Value("${ecm.api.base-url:http://localhost:8080}")
    private String apiBaseUrl;

    public void sendWebhook(String url, String method, String bodyTemplate, Map<String, String> headers, Document document) {
        try {
            // 1. Prepare Payload
            String body = resolveTemplate(bodyTemplate, document);

            // 2. Prepare Headers
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
            if (headers != null) {
                headers.forEach(httpHeaders::add);
            }

            // 3. Send Request
            HttpEntity<String> entity = new HttpEntity<>(body, httpHeaders);
            restTemplate.exchange(url, HttpMethod.valueOf(method.toUpperCase()), entity, String.class);

            log.info("Webhook sent to {} for document {}", url, document.getId());

        } catch (Exception e) {
            log.error("Failed to send webhook to {}", url, e);
            throw new RuntimeException("Webhook execution failed", e);
        }
    }

    private String resolveTemplate(String template, Document doc) {
        if (template == null) {
            // Default payload if no template provided
            return String.format("{\"event\": \"document_processed\", \"id\": \"%s\", \"name\": \"%s\"}", 
                doc.getId(), doc.getName());
        }

        String result = template;
        result = result.replace("{documentId}", doc.getId().toString());
        result = result.replace("{documentName}", doc.getName());
        result = result.replace("{mimeType}", doc.getMimeType());
        result = result.replace("{size}", String.valueOf(doc.getSize()));
        result = result.replace("{downloadUrl}", apiBaseUrl + "/api/v1/documents/" + doc.getId() + "/download");
        
        return result;
    }
}
