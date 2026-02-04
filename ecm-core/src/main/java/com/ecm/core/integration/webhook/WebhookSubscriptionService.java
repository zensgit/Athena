package com.ecm.core.integration.webhook;

import com.ecm.core.entity.WebhookSubscription;
import com.ecm.core.repository.WebhookSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class WebhookSubscriptionService {

    private final WebhookSubscriptionRepository repository;
    private final WebhookNotificationService notificationService;

    @Transactional(readOnly = true)
    public List<WebhookSubscription> list() {
        return repository.findAll();
    }

    public WebhookSubscription create(WebhookSubscription subscription) {
        if (subscription.getEventTypes() == null) {
            subscription.setEventTypes(Set.of());
        }
        return repository.save(subscription);
    }

    public WebhookSubscription update(UUID id, WebhookSubscription updates) {
        WebhookSubscription existing = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));
        if (updates.getName() != null) {
            existing.setName(updates.getName());
        }
        if (updates.getUrl() != null) {
            existing.setUrl(updates.getUrl());
        }
        if (updates.getSecret() != null) {
            existing.setSecret(updates.getSecret());
        }
        existing.setEnabled(updates.isEnabled());
        if (updates.getEventTypes() != null) {
            existing.setEventTypes(updates.getEventTypes());
        }
        return repository.save(existing);
    }

    public void delete(UUID id) {
        repository.deleteById(id);
    }

    public WebhookSubscription toggle(UUID id, boolean enabled) {
        WebhookSubscription existing = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));
        existing.setEnabled(enabled);
        return repository.save(existing);
    }

    public void test(UUID id) {
        WebhookSubscription subscription = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));
        notificationService.sendTestEvent(subscription);
    }
}
