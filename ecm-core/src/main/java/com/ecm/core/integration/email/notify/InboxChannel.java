package com.ecm.core.integration.email.notify;

import com.ecm.core.service.NotificationInboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InboxChannel implements NotificationChannel {

    private final NotificationInboxService notificationInboxService;

    @Override
    public String getId() {
        return INBOX;
    }

    @Override
    public void dispatch(NotificationPayload payload) {
        if (payload == null) {
            return;
        }
        if (payload.getRecipientUserId() == null || payload.getRecipientUserId().isBlank()) {
            log.warn("dispatch[inbox]: missing recipientUserId for type={}", payload.getType());
            return;
        }
        if (payload.getActivity() == null) {
            log.warn(
                "dispatch[inbox]: missing activity reference for type={} recipient={}",
                payload.getType(),
                payload.getRecipientUserId()
            );
            return;
        }
        try {
            notificationInboxService.createDirectNotification(
                payload.getRecipientUserId(),
                payload.getActivity()
            );
        } catch (Exception ex) {
            log.warn(
                "dispatch[inbox]: failed type={} recipient={} cause={}",
                payload.getType(),
                payload.getRecipientUserId(),
                ex.getMessage()
            );
        }
    }
}
