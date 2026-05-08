package com.ecm.core.controller;

import com.ecm.core.integration.email.notify.EmailAdminTestService;
import com.ecm.core.integration.email.notify.EmailAdminTestService.EmailTestSmtpResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin diagnostics for SMTP / outbound email configuration.
 *
 * <p>Lives outside {@link com.ecm.core.integration.mail.controller.MailAutomationController}
 * because that controller manages inbound IMAP automation; this one verifies
 * the outbound SMTP path. The endpoint deliberately bypasses
 * {@code ecm.email.enabled} — the operator needs to test SMTP <em>before</em>
 * enabling production notifications.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/email")
@RequiredArgsConstructor
@Tag(name = "Email Admin", description = "Operator diagnostics for SMTP / outbound email configuration")
@PreAuthorize("hasRole('ADMIN')")
public class EmailAdminController {

    private final EmailAdminTestService emailAdminTestService;

    @PostMapping("/test-smtp")
    @Operation(
        summary = "Send a single SMTP test message",
        description = "Synchronously dispatches a single test message via the configured JavaMailSender. "
            + "Returns dispatch outcome plus the active spring.mail.host / spring.mail.port / "
            + "ecm.email.from-address values. Bypasses ecm.email.enabled so operators can verify "
            + "SMTP connectivity before turning notifications on."
    )
    public ResponseEntity<EmailTestSmtpResponse> testSmtp(@RequestBody EmailTestSmtpRequest request) {
        String to = request == null ? null : request.to();
        EmailTestSmtpResult result = emailAdminTestService.sendTestMessage(to);
        return ResponseEntity.ok(EmailTestSmtpResponse.from(result));
    }

    /**
     * Wire-format request body for {@code POST /api/v1/admin/email/test-smtp}.
     */
    public record EmailTestSmtpRequest(String to) {}

    /**
     * Wire-format response body for {@code POST /api/v1/admin/email/test-smtp}.
     *
     * @param ok           true if the message was handed off to JavaMailSender successfully.
     * @param message      operator-facing one-line summary (success or failure category).
     * @param smtpHost     current value of {@code spring.mail.host}, or null if unset.
     * @param smtpPort     current value of {@code spring.mail.port}, or null if unset/unparseable.
     * @param fromAddress  current value of {@code ecm.email.from-address}, or empty if unset.
     * @param diagnostic   null on success; on failure carries the {@link org.springframework.mail.MailException}
     *                     message or {@code <ClassName>: <message>} for other thrown types.
     */
    public record EmailTestSmtpResponse(
        boolean ok,
        String message,
        String smtpHost,
        Integer smtpPort,
        String fromAddress,
        String diagnostic
    ) {
        static EmailTestSmtpResponse from(EmailTestSmtpResult result) {
            return new EmailTestSmtpResponse(
                result.ok(),
                result.message(),
                result.smtpHost(),
                result.smtpPort(),
                result.fromAddress(),
                result.diagnostic()
            );
        }
    }
}
