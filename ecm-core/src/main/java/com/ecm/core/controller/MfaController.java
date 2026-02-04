package com.ecm.core.controller;

import com.ecm.core.security.mfa.MfaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/mfa")
@RequiredArgsConstructor
@Tag(name = "MFA", description = "Multi-factor authentication management")
public class MfaController {

    private final MfaService mfaService;

    @GetMapping("/status")
    @Operation(summary = "MFA Status", description = "Get the current user's MFA status")
    public ResponseEntity<MfaService.MfaStatus> getStatus() {
        return ResponseEntity.ok(mfaService.getStatus());
    }

    @PostMapping("/enroll")
    @Operation(summary = "Enroll MFA", description = "Generate a new TOTP secret and recovery codes")
    public ResponseEntity<MfaService.MfaEnrollment> enroll() {
        return ResponseEntity.ok(mfaService.enroll());
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify MFA", description = "Verify a TOTP code to enable MFA")
    public ResponseEntity<MfaService.MfaVerification> verify(@RequestBody MfaVerifyRequest request) {
        return ResponseEntity.ok(mfaService.verify(request.code()));
    }

    @PostMapping("/disable")
    @Operation(summary = "Disable MFA", description = "Disable MFA using a TOTP or recovery code")
    public ResponseEntity<MfaService.MfaDisableResult> disable(@RequestBody MfaDisableRequest request) {
        return ResponseEntity.ok(mfaService.disable(request.code()));
    }

    @PostMapping("/recovery-codes")
    @Operation(summary = "Regenerate recovery codes", description = "Regenerate recovery codes using a valid TOTP code")
    public ResponseEntity<MfaService.RecoveryCodes> regenerateRecoveryCodes(@RequestBody MfaVerifyRequest request) {
        return ResponseEntity.ok(mfaService.regenerateRecoveryCodes(request.code()));
    }

    public record MfaVerifyRequest(String code) {}

    public record MfaDisableRequest(String code) {}
}
