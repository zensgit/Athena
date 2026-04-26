package com.ecm.core.integration.email.notify;

import com.ecm.core.entity.Activity;
import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.Map;

@Value
@Builder
public class NotificationPayload {

    String type;

    String recipientUserId;

    String recipientEmail;

    String preferredLocale;

    Activity activity;

    @Builder.Default
    Map<String, Object> templateVars = Collections.emptyMap();
}
