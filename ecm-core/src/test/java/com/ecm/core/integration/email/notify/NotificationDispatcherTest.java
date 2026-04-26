package com.ecm.core.integration.email.notify;

import com.ecm.core.entity.Activity;
import com.ecm.core.service.NotificationInboxService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Test
    @DisplayName("dispatch: routes to each requested channel by id")
    void routesToRequestedChannels() {
        NotificationInboxService inboxService = mock(NotificationInboxService.class);
        EmailNotificationService emailService = mock(EmailNotificationService.class);

        InboxChannel inbox = new InboxChannel(inboxService);
        EmailChannel email = new EmailChannel(emailService);
        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(inbox, email));

        Activity activity = new Activity();
        NotificationPayload payload = NotificationPayload.builder()
            .type("rm.report_preset.delivery.succeeded")
            .recipientUserId("alice")
            .recipientEmail("alice@example.com")
            .preferredLocale("default")
            .activity(activity)
            .templateVars(Map.of("k", "v"))
            .build();

        dispatcher.dispatch(payload, Set.of("inbox", "email"));

        verify(inboxService, times(1)).createDirectNotification(eq("alice"), eq(activity));
        verify(emailService, times(1)).send(
            eq("rm.report_preset.delivery.succeeded"),
            eq("alice@example.com"),
            eq("default"),
            any()
        );
    }

    @Test
    @DisplayName("dispatch: skips unknown channelId and continues with the rest")
    void skipsUnknownChannel() {
        NotificationInboxService inboxService = mock(NotificationInboxService.class);
        EmailNotificationService emailService = mock(EmailNotificationService.class);

        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(
            new InboxChannel(inboxService),
            new EmailChannel(emailService)
        ));

        NotificationPayload payload = NotificationPayload.builder()
            .type("k")
            .recipientUserId("u")
            .recipientEmail("u@example.com")
            .activity(new Activity())
            .build();

        dispatcher.dispatch(payload, List.of("inbox", "sms-not-implemented", "email"));

        verify(inboxService, times(1)).createDirectNotification(any(), any());
        verify(emailService, times(1)).send(any(), any(), any(), any());
    }

    @Test
    @DisplayName("dispatch: a failing channel does not abort the remaining dispatches")
    void singleChannelFailureDoesNotAbortOthers() {
        NotificationInboxService inboxService = mock(NotificationInboxService.class);
        EmailNotificationService emailService = mock(EmailNotificationService.class);

        org.mockito.Mockito.doThrow(new RuntimeException("inbox boom"))
            .when(inboxService).createDirectNotification(any(), any());

        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(
            new InboxChannel(inboxService),
            new EmailChannel(emailService)
        ));

        NotificationPayload payload = NotificationPayload.builder()
            .type("k")
            .recipientUserId("u")
            .recipientEmail("u@example.com")
            .activity(new Activity())
            .build();

        // Must not throw — the InboxChannel itself swallows the inboxService throw,
        // and the dispatcher swallows any further channel-level exception.
        dispatcher.dispatch(payload, List.of("inbox", "email"));

        verify(emailService, times(1)).send(any(), any(), any(), any());
    }

    @Test
    @DisplayName("dispatch: empty channel set is a no-op")
    void emptyChannelSetIsNoOp() {
        NotificationInboxService inboxService = mock(NotificationInboxService.class);
        EmailNotificationService emailService = mock(EmailNotificationService.class);

        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(
            new InboxChannel(inboxService),
            new EmailChannel(emailService)
        ));

        NotificationPayload payload = NotificationPayload.builder()
            .type("k")
            .recipientUserId("u")
            .activity(new Activity())
            .build();

        dispatcher.dispatch(payload, Set.of());

        verify(inboxService, never()).createDirectNotification(any(), any());
        verify(emailService, never()).send(any(), any(), any(), any());
    }

    @Test
    @DisplayName("EmailChannel: skips when recipientEmail is blank (debug log only)")
    void emailChannel_skipsBlankEmail() {
        EmailNotificationService emailService = mock(EmailNotificationService.class);
        EmailChannel email = new EmailChannel(emailService);

        NotificationPayload payload = NotificationPayload.builder()
            .type("k")
            .recipientUserId("u")
            .recipientEmail(null)
            .build();

        email.dispatch(payload);

        verify(emailService, never()).send(any(), any(), any(), any());
    }

    @Test
    @DisplayName("InboxChannel: skips when activity reference is missing")
    void inboxChannel_skipsMissingActivity() {
        NotificationInboxService inboxService = mock(NotificationInboxService.class);
        InboxChannel inbox = new InboxChannel(inboxService);

        NotificationPayload payload = NotificationPayload.builder()
            .type("k")
            .recipientUserId("u")
            .activity(null)
            .build();

        inbox.dispatch(payload);

        verify(inboxService, never()).createDirectNotification(any(), any());
    }
}
