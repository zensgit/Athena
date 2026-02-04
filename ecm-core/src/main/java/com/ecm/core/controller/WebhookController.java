package com.ecm.core.controller;

import com.ecm.core.entity.WebhookSubscription;
import com.ecm.core.integration.webhook.WebhookNotificationService;
import com.ecm.core.integration.webhook.WebhookSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Webhook subscriptions")
public class WebhookController {

    private final WebhookSubscriptionService subscriptionService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List webhook subscriptions")
    public ResponseEntity<List<WebhookSubscription>> list() {
        return ResponseEntity.ok(subscriptionService.list());
    }

    @GetMapping("/event-types")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List webhook event types")
    public ResponseEntity<Set<String>> eventTypes() {
        return ResponseEntity.ok(WebhookNotificationService.SUPPORTED_EVENT_TYPES);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create webhook subscription")
    public ResponseEntity<WebhookSubscription> create(@RequestBody WebhookSubscriptionRequest request) {
        WebhookSubscription subscription = WebhookSubscription.builder()
            .name(request.name())
            .url(request.url())
            .secret(request.secret())
            .enabled(request.enabled())
            .eventTypes(request.eventTypes())
            .build();
        return ResponseEntity.ok(subscriptionService.create(subscription));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update webhook subscription")
    public ResponseEntity<WebhookSubscription> update(@PathVariable UUID id, @RequestBody WebhookSubscriptionRequest request) {
        WebhookSubscription updates = WebhookSubscription.builder()
            .name(request.name())
            .url(request.url())
            .secret(request.secret())
            .enabled(request.enabled())
            .eventTypes(request.eventTypes())
            .build();
        return ResponseEntity.ok(subscriptionService.update(id, updates));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete webhook subscription")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        subscriptionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Send test webhook")
    public ResponseEntity<Void> test(@PathVariable UUID id) {
        subscriptionService.test(id);
        return ResponseEntity.accepted().build();
    }

    public record WebhookSubscriptionRequest(
        String name,
        String url,
        String secret,
        boolean enabled,
        Set<String> eventTypes
    ) {}
}
