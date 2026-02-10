package com.ecm.core.integration.mail.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailOAuthTokenErrorParserTest {

    @Test
    @DisplayName("Parses JSON OAuth token error payloads")
    void parsesJsonErrorPayload() {
        String body = "{\"error\":\"invalid_grant\",\"error_description\":\"Token has been expired or revoked.\"}";

        var parsed = MailOAuthTokenErrorParser.parse(body);

        assertTrue(parsed.isPresent());
        assertEquals("invalid_grant", parsed.get().error());
        assertEquals("Token has been expired or revoked.", parsed.get().errorDescription());
    }

    @Test
    @DisplayName("Falls back to heuristics for non-JSON payloads")
    void parsesNonJsonPayload() {
        String body = "400 Bad Request: invalid_grant";

        var parsed = MailOAuthTokenErrorParser.parse(body);

        assertTrue(parsed.isPresent());
        assertEquals("invalid_grant", parsed.get().error());
        assertEquals(null, parsed.get().errorDescription());
    }

    @Test
    @DisplayName("Unknown payloads yield empty parse result")
    void unknownPayloadIsEmpty() {
        String body = "Unexpected error";

        var parsed = MailOAuthTokenErrorParser.parse(body);

        assertFalse(parsed.isPresent());
    }
}

