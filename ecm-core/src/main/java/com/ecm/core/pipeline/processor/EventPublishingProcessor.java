package com.ecm.core.pipeline.processor;

import com.ecm.core.entity.Document;
import com.ecm.core.event.message.DocumentCreatedMessage;
import com.ecm.core.pipeline.DocumentContext;
import com.ecm.core.pipeline.DocumentProcessor;
import com.ecm.core.pipeline.ProcessingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Event Publishing Processor
 *
 * Publishes integration events to the message broker (RabbitMQ)
 * when a document is successfully processed.
 *
 * Execution Order: 600 (Last step)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublishingProcessor implements DocumentProcessor {

    private final RabbitTemplate rabbitTemplate;

    @Value("${ecm.events.exchange:athena.events}")
    private String eventsExchange;

    @Value("${ecm.events.routing-key.created:document.created}")
    private String createdRoutingKey;

    @Override
    public ProcessingResult process(DocumentContext context) {
        Document document = context.getDocument();
        if (document == null) {
            return ProcessingResult.skipped("Document not available for event publishing");
        }

        try {
            Map<String, String> metadata = new HashMap<>();
            context.getExtractedMetadata().forEach((k, v) -> metadata.put(k, v != null ? v.toString() : null));

            DocumentCreatedMessage message = DocumentCreatedMessage.builder()
                .documentId(document.getId())
                .name(document.getName())
                .mimeType(document.getMimeType())
                .contentId(document.getContentId())
                .size(document.getSize())
                .createdBy(document.getCreatedBy())
                .createdAt(document.getCreatedDate())
                .parentId(document.getParent() != null ? document.getParent().getId() : null)
                .metadata(metadata)
                .build();

            rabbitTemplate.convertAndSend(eventsExchange, createdRoutingKey, message);

            log.info("Published document.created event for document {}", document.getId());

            return ProcessingResult.success()
                .withData("eventPublished", true)
                .withData("exchange", eventsExchange)
                .withData("routingKey", createdRoutingKey);

        } catch (Exception e) {
            log.error("Failed to publish event for document {}: {}", document.getId(), e.getMessage());
            // Event publishing failure should not fail the pipeline, but we note it
            return ProcessingResult.success()
                .withData("eventPublished", false)
                .withData("error", e.getMessage());
        }
    }

    @Override
    public int getOrder() {
        return 600;
    }

    @Override
    public String getName() {
        return "EventPublishingProcessor";
    }
}
