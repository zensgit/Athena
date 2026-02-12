package com.ecm.core.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * ML Service Client
 *
 * HTTP client for calling the external ML microservice (FastAPI).
 * The ML service provides document classification capabilities.
 *
 * Architecture:
 * ECM Core (Java) --HTTP--> ML Service (FastAPI/Python)
 *
 * This approach avoids ProcessBuilder issues with Python environments
 * and provides better scalability and maintainability.
 */
@Slf4j
@Service
public class MLServiceClient {

    @Value("${ecm.ml.service.url:http://ml-service:8080}")
    private String mlServiceUrl;

    @Value("${ecm.ml.enabled:true}")
    private boolean mlEnabled;

    @Value("${ecm.ml.timeout:30000}")
    private int timeout;

    private final RestTemplate restTemplate;

    public MLServiceClient() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Check if ML service is available
     */
    public boolean isAvailable() {
        if (!mlEnabled) {
            return false;
        }

        try {
            ResponseEntity<HealthResponse> response = restTemplate.getForEntity(
                mlServiceUrl + "/health",
                HealthResponse.class
            );

            return response.getStatusCode() == HttpStatus.OK &&
                   response.getBody() != null &&
                   "healthy".equals(response.getBody().getStatus());

        } catch (Exception e) {
            log.debug("ML service health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if ML model is loaded and ready
     */
    public boolean isModelLoaded() {
        if (!isAvailable()) {
            return false;
        }

        try {
            ResponseEntity<HealthResponse> response = restTemplate.getForEntity(
                mlServiceUrl + "/health",
                HealthResponse.class
            );

            return response.getBody() != null &&
                   Boolean.TRUE.equals(response.getBody().getModelLoaded());

        } catch (Exception e) {
            log.debug("ML model check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Classify document content
     *
     * @param text The extracted text content of the document
     * @return Classification result with category suggestion and confidence
     */
    public ClassificationResult classify(String text) {
        if (!mlEnabled) {
            log.debug("ML service is disabled");
            return ClassificationResult.empty();
        }

        if (text == null || text.length() < 50) {
            log.debug("Text too short for classification: {} chars", text != null ? text.length() : 0);
            return ClassificationResult.empty();
        }

        try {
            ClassifyRequest request = new ClassifyRequest(text, null);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<ClassifyRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<ClassifyResponse> response = restTemplate.postForEntity(
                mlServiceUrl + "/api/ml/classify",
                entity,
                ClassifyResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                ClassifyResponse body = response.getBody();
                return ClassificationResult.builder()
                    .suggestedCategory(body.getPrediction())
                    .confidence(body.getConfidence())
                    .alternatives(body.getAlternatives())
                    .success(true)
                    .build();
            }

            return ClassificationResult.empty();

        } catch (RestClientException e) {
            log.warn("ML classification request failed: {}", e.getMessage());
            return ClassificationResult.failed(e.getMessage());
        }
    }

    /**
     * Classify document with candidate categories
     *
     * @param text The extracted text content
     * @param candidates List of possible category names to choose from
     * @return Classification result
     */
    public ClassificationResult classifyWithCandidates(String text, List<String> candidates) {
        if (!mlEnabled) {
            return ClassificationResult.empty();
        }

        try {
            ClassifyRequest request = new ClassifyRequest(text, candidates);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<ClassifyRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<ClassifyResponse> response = restTemplate.postForEntity(
                mlServiceUrl + "/api/ml/classify",
                entity,
                ClassifyResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                ClassifyResponse body = response.getBody();
                return ClassificationResult.builder()
                    .suggestedCategory(body.getPrediction())
                    .confidence(body.getConfidence())
                    .alternatives(body.getAlternatives())
                    .success(true)
                    .build();
            }

            return ClassificationResult.empty();

        } catch (RestClientException e) {
            log.warn("ML classification request failed: {}", e.getMessage());
            return ClassificationResult.failed(e.getMessage());
        }
    }

    /**
     * Suggest tags based on document content
     *
     * @param text The extracted text content
     * @param maxTags Maximum number of tags to suggest
     * @return List of suggested tag names
     */
    public List<String> suggestTags(String text, int maxTags) {
        if (!mlEnabled || text == null || text.length() < 50) {
            return Collections.emptyList();
        }

        try {
            TagSuggestRequest request = new TagSuggestRequest(text, maxTags);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<TagSuggestRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<TagSuggestResponse> response = restTemplate.postForEntity(
                mlServiceUrl + "/api/ml/suggest-tags",
                entity,
                TagSuggestResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody().getTags();
            }

            return Collections.emptyList();

        } catch (RestClientException e) {
            log.warn("ML tag suggestion request failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Extract text content via OCR (Tesseract behind the ML service).
     *
     * This is optional and should typically be invoked by a background queue/job,
     * since OCR can be slow for multi-page PDFs.
     */
    public OcrResult ocr(byte[] fileBytes, String filename, String contentType, String language, int maxPages, int maxChars) {
        if (!mlEnabled) {
            return OcrResult.empty();
        }

        if (fileBytes == null || fileBytes.length == 0) {
            return OcrResult.failed("Empty file content");
        }

        if (!isAvailable()) {
            return OcrResult.failed("ML service unavailable");
        }

        String safeLanguage = (language == null || language.isBlank()) ? "eng" : language.trim();
        int safeMaxPages = Math.max(1, Math.min(maxPages > 0 ? maxPages : 3, 20));
        int safeMaxChars = Math.max(1000, Math.min(maxChars > 0 ? maxChars : 200000, 2000000));

        try {
            String url = UriComponentsBuilder
                .fromHttpUrl(mlServiceUrl + "/api/ml/ocr")
                .queryParam("language", safeLanguage)
                .queryParam("maxPages", safeMaxPages)
                .queryParam("maxChars", safeMaxChars)
                .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            ByteArrayResource resource = new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return (filename == null || filename.isBlank()) ? "document" : filename;
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", resource);

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<OcrResponse> response = restTemplate.postForEntity(
                url,
                entity,
                OcrResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                OcrResponse ocr = response.getBody();
                return OcrResult.builder()
                    .success(true)
                    .text(ocr.getText() != null ? ocr.getText() : "")
                    .pages(ocr.getPages())
                    .language(ocr.getLanguage())
                    .truncated(Boolean.TRUE.equals(ocr.getTruncated()))
                    .build();
            }

            return OcrResult.failed("Unexpected OCR response: " + response.getStatusCode());

        } catch (RestClientException e) {
            log.warn("ML OCR request failed: {}", e.getMessage());
            return OcrResult.failed(e.getMessage());
        }
    }

    /**
     * Trigger model training with labeled documents
     *
     * @param documents List of training documents with text and categories
     * @return Training result
     */
    public TrainingResult trainModel(List<TrainingDocument> documents) {
        if (!mlEnabled) {
            return TrainingResult.failed("ML service is disabled");
        }

        if (documents == null || documents.size() < 10) {
            return TrainingResult.failed("Need at least 10 documents for training");
        }

        try {
            TrainRequest request = new TrainRequest(documents);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<TrainRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                mlServiceUrl + "/api/ml/train",
                entity,
                Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map body = response.getBody();
                return TrainingResult.builder()
                    .success(true)
                    .status((String) body.get("status"))
                    .samplesUsed((Integer) body.get("samples"))
                    .build();
            }

            return TrainingResult.failed("Training failed");

        } catch (RestClientException e) {
            log.error("ML training request failed", e);
            return TrainingResult.failed(e.getMessage());
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OcrResponse {
        private String text;
        private Integer pages;
        private String language;
        private Boolean truncated;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OcrResult {
        private boolean success;
        private String text;
        private Integer pages;
        private String language;
        private boolean truncated;
        private String errorMessage;

        public static OcrResult empty() {
            return OcrResult.builder()
                .success(false)
                .text("")
                .errorMessage("OCR disabled")
                .build();
        }

        public static OcrResult failed(String message) {
            return OcrResult.builder()
                .success(false)
                .text("")
                .errorMessage(message)
                .build();
        }
    }

    /**
     * Get model information
     */
    public ModelInfo getModelInfo() {
        if (!mlEnabled) {
            return ModelInfo.empty();
        }

        try {
            ResponseEntity<HealthResponse> response = restTemplate.getForEntity(
                mlServiceUrl + "/health",
                HealthResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                HealthResponse body = response.getBody();
                return ModelInfo.builder()
                    .available(true)
                    .modelLoaded(Boolean.TRUE.equals(body.getModelLoaded()))
                    .modelVersion(body.getModelVersion())
                    .build();
            }

            return ModelInfo.empty();

        } catch (RestClientException e) {
            log.debug("Failed to get ML model info: {}", e.getMessage());
            return ModelInfo.empty();
        }
    }

    // ==================== Request/Response DTOs ====================

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ClassifyRequest {
        private String text;
        private List<String> candidates;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClassifyResponse {
        private String prediction;
        private Double confidence;
        private List<AlternativeCategory> alternatives;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlternativeCategory {
        private String category;
        private Double confidence;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TagSuggestRequest {
        private String text;
        private int maxTags;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TagSuggestResponse {
        private List<String> tags;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TrainRequest {
        private List<TrainingDocument> documents;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrainingDocument {
        private String text;
        private String category;
        private List<String> tags;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HealthResponse {
        private String status;
        private Boolean modelLoaded;
        private String modelVersion;
    }

    // ==================== Result Classes ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClassificationResult {
        private String suggestedCategory;
        private Double confidence;
        private List<AlternativeCategory> alternatives;
        private boolean success;
        private String errorMessage;

        public static ClassificationResult empty() {
            return ClassificationResult.builder()
                .success(false)
                .build();
        }

        public static ClassificationResult failed(String error) {
            return ClassificationResult.builder()
                .success(false)
                .errorMessage(error)
                .build();
        }

        public boolean isHighConfidence() {
            return confidence != null && confidence >= 0.85;
        }

        public boolean isMediumConfidence() {
            return confidence != null && confidence >= 0.7 && confidence < 0.85;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrainingResult {
        private boolean success;
        private String status;
        private Integer samplesUsed;
        private String errorMessage;

        public static TrainingResult failed(String error) {
            return TrainingResult.builder()
                .success(false)
                .errorMessage(error)
                .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelInfo {
        private boolean available;
        private boolean modelLoaded;
        private String modelVersion;

        public static ModelInfo empty() {
            return ModelInfo.builder()
                .available(false)
                .modelLoaded(false)
                .build();
        }
    }
}
