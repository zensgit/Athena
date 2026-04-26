package com.ecm.core.integration.email.notify;

public interface NotificationChannel {

    String INBOX = "inbox";
    String EMAIL = "email";

    String getId();

    void dispatch(NotificationPayload payload);
}
