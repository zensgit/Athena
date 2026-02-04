package com.ecm.core.integration.webhook;

import com.ecm.core.entity.WebhookSubscription;
import com.ecm.core.repository.WebhookSubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookNotificationService {

    public static final Set<String> SUPPORTED_EVENT_TYPES = Set.of(
        "NODE_CREATED",
        "NODE_DELETED",
        "NODE_LOCKED",
        "NODE_UNLOCKED",
        "VERSION_CREATED",
        "VERSION_REVERTED",
        "USER_NOTIFICATION"
    );

    private static final String SIGNATURE_HEADER = "X-ECM-Signature";
    private static final String EVENT_HEADER = "X-ECM-Event";
    private static final String DELIVERY_HEADER = "X-ECM-Delivery";
    private static final String TIMESTAMP_HEADER = "X-ECM-Timestamp";

    private final WebhookSubscriptionRepository repository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Async
    @Transactional
    public void dispatchNotification(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return;
        }
        String eventType = String.valueOf(payload.getOrDefault("type", "UNKNOWN"));
        List<WebhookSubscription> subscriptions = repository.findByEnabledTrue();
        for (WebhookSubscription subscription : subscriptions) {
            if (!shouldDeliver(subscription, eventType)) {
                continue;
            }
            deliver(subscription, eventType, payload);
        }
    }

    @Async
    @Transactional
    public void sendTestEvent(WebhookSubscription subscription) {
        Map<String, Object> payload = Map.of(
            "type", "TEST",
            "message", "Test webhook delivery",
            "timestamp", Instant.now().toString()
        );
        deliver(subscription, "TEST", payload);
    }

    private boolean shouldDeliver(WebhookSubscription subscription, String eventType) {
        if (!subscription.isEnabled()) {
            return false;
        }
        if (subscription.getEventTypes() == null || subscription.getEventTypes().isEmpty()) {
            return true;
        }
        return subscription.getEventTypes().contains(eventType);
    }

    private void deliver(WebhookSubscription subscription, String eventType, Map<String, Object> payload) {
        try {
            String deliveryId = UUID.randomUUID().toString();
            long timestamp = Instant.now().getEpochSecond();
            String body = objectMapper.writeValueAsString(Map.of(
                "eventType", eventType,
                "deliveryId", deliveryId,
                "timestamp", timestamp,
                "payload", payload
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add(EVENT_HEADER, eventType);
            headers.add(DELIVERY_HEADER, deliveryId);
            headers.add(TIMESTAMP_HEADER, String.valueOf(timestamp));
            if (subscription.getSecret() != null && !subscription.getSecret().isBlank()) {
                headers.add(SIGNATURE_HEADER, signPayload(subscription.getSecret(), body));
            }

            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            var response = restTemplate.postForEntity(subscription.getUrl(), entity, String.class);
            subscription.setLastSuccessAt(LocalDateTime.now());
            subscription.setLastStatusCode(response.getStatusCode().value());
            subscription.setLastErrorMessage(null);
            repository.save(subscription);
        } catch (Exception ex) {
            subscription.setLastFailureAt(LocalDateTime.now());
            subscription.setLastErrorMessage(ex.getMessage());
            repository.save(subscription);
            log.warn("Webhook delivery failed for {}: {}", subscription.getUrl(), ex.getMessage());
        }
    }

    private String signPayload(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign webhook payload", ex);
        }
    }
}
