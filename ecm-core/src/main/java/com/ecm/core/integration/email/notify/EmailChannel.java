package com.ecm.core.integration.email.notify;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailChannel implements NotificationChannel {

    private final EmailNotificationService emailNotificationService;

    @Override
    public String getId() {
        return EMAIL;
    }

    @Override
    public void dispatch(NotificationPayload payload) {
        if (payload == null) {
            return;
        }
        if (payload.getRecipientEmail() == null || payload.getRecipientEmail().isBlank()) {
            log.debug(
                "dispatch[email]: no recipientEmail for type={} (skipping email channel)",
                payload.getType()
            );
            return;
        }
        emailNotificationService.send(
            payload.getType(),
            payload.getRecipientEmail(),
            payload.getPreferredLocale(),
            payload.getTemplateVars()
        );
    }
}
