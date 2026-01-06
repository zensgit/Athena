package com.ecm.core.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RuleActionTest {

    @Test
    @DisplayName("sendNotification sets recipient and message")
    void sendNotificationSetsRecipientAndMessage() {
        RuleAction action = RuleAction.sendNotification("alice", "hello");

        assertEquals(RuleAction.ActionType.SEND_NOTIFICATION, action.getType());
        assertEquals("alice", action.getParam(RuleAction.ParamKeys.RECIPIENT));
        assertEquals("hello", action.getParam(RuleAction.ParamKeys.MESSAGE));
        assertNull(action.getParam(RuleAction.ParamKeys.NOTIFICATION_TYPE));
    }

    @Test
    @DisplayName("sendNotification includes type when provided")
    void sendNotificationIncludesType() {
        RuleAction action = RuleAction.sendNotification("bob", "hi", "email");

        assertEquals("email", action.getParam(RuleAction.ParamKeys.NOTIFICATION_TYPE));
    }
}
