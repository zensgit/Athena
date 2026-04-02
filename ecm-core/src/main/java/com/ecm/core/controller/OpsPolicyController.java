package com.ecm.core.controller;

import com.ecm.core.preview.PreviewFailurePolicyRegistry;
import com.ecm.core.service.AuditService;
import com.ecm.core.service.OpsPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ops/policies")
@RequiredArgsConstructor
@Tag(name = "Ops Policies", description = "Unified operational policy center")
@PreAuthorize("hasRole('ADMIN')")
public class OpsPolicyController {

    private final OpsPolicyService opsPolicyService;
    private final AuditService auditService;

    @GetMapping
    @Operation(
        summary = "Get policies by domain",
        description = "Returns policy state and current policy version for the requested domain."
    )
    public ResponseEntity<OpsPolicyDomainStateDto> getPolicies(
        @RequestParam(defaultValue = "PREVIEW") String domain
    ) {
        OpsPolicyService.DomainPolicyState state = opsPolicyService.getState(domain);
        return ResponseEntity.ok(toDomainStateDto(state));
    }

    @GetMapping("/{domain}/history")
    @Operation(
        summary = "Get policy version history",
        description = "Returns recent policy version history entries for the requested domain."
    )
    public ResponseEntity<OpsPolicyHistoryResponseDto> getHistory(
        @PathVariable String domain,
        @RequestParam(defaultValue = "20") Integer limit
    ) {
        OpsPolicyService.DomainPolicyState state = opsPolicyService.getState(domain);
        List<OpsPolicyHistoryEntryDto> history = opsPolicyService.listHistory(domain, limit).stream()
            .map(entry -> new OpsPolicyHistoryEntryDto(
                entry.version(),
                entry.updatedAt(),
                entry.actor(),
                entry.reason()
            ))
            .toList();
        return ResponseEntity.ok(new OpsPolicyHistoryResponseDto(
            state.domain(),
            state.currentVersion(),
            history
        ));
    }

    @PutMapping("/{domain}")
    @Operation(
        summary = "Update domain policy profile",
        description = "Updates one profile in the given policy domain and creates a new policy version snapshot."
    )
    public ResponseEntity<OpsPolicyUpdateResponseDto> updatePolicy(
        @PathVariable String domain,
        @RequestBody OpsPolicyUpdateRequestDto request
    ) {
        try {
            if (request == null || request.profileKey() == null || request.profileKey().isBlank()) {
                throw new IllegalArgumentException("profileKey is required");
            }
            PreviewFailurePolicyRegistry.PreviewFailurePolicyUpdate update = new PreviewFailurePolicyRegistry.PreviewFailurePolicyUpdate(
                request.maxAttempts(),
                request.retryDelayMs(),
                request.backoffMultiplier(),
                request.quietPeriodMs()
            );
            OpsPolicyService.DomainPolicyUpdateResult updated = opsPolicyService.updatePolicy(
                domain,
                request.profileKey(),
                update,
                resolveAuditUsername(),
                request.reason()
            );
            auditService.logEvent(
                "OPS_POLICY_UPDATED",
                null,
                "OPS_POLICY",
                resolveAuditUsername(),
                String.format(
                    "domain=%s profile=%s version=%d reason=%s",
                    updated.domain(),
                    request.profileKey(),
                    updated.currentVersion(),
                    updated.reason()
                )
            );
            return ResponseEntity.ok(new OpsPolicyUpdateResponseDto(
                updated.domain(),
                updated.currentVersion(),
                updated.updatedAt(),
                updated.actor(),
                updated.reason(),
                toPolicyDto(updated.updatedPolicy()),
                updated.policies().stream().map(OpsPolicyController::toPolicyDto).toList(),
                null
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new OpsPolicyUpdateResponseDto(
                "PREVIEW",
                0,
                Instant.now(),
                resolveAuditUsername(),
                "error",
                null,
                List.of(),
                ex.getMessage()
            ));
        }
    }

    @PostMapping("/{domain}/rollback")
    @Operation(
        summary = "Rollback policy domain",
        description = "Rolls back policy domain to target version (or previous version if omitted)."
    )
    public ResponseEntity<OpsPolicyRollbackResponseDto> rollback(
        @PathVariable String domain,
        @RequestBody(required = false) OpsPolicyRollbackRequestDto request
    ) {
        try {
            OpsPolicyService.DomainPolicyRollbackResult result = opsPolicyService.rollback(
                domain,
                request != null ? request.targetVersion() : null,
                resolveAuditUsername(),
                request != null ? request.reason() : null
            );
            auditService.logEvent(
                "OPS_POLICY_ROLLBACK",
                null,
                "OPS_POLICY",
                resolveAuditUsername(),
                String.format(
                    "domain=%s from=%d to=%d current=%d reason=%s",
                    result.domain(),
                    result.previousVersion(),
                    result.rolledBackToVersion(),
                    result.currentVersion(),
                    result.reason()
                )
            );
            return ResponseEntity.ok(new OpsPolicyRollbackResponseDto(
                result.domain(),
                result.previousVersion(),
                result.rolledBackToVersion(),
                result.currentVersion(),
                result.updatedAt(),
                result.actor(),
                result.reason(),
                result.policies().stream().map(OpsPolicyController::toPolicyDto).toList(),
                null
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new OpsPolicyRollbackResponseDto(
                "PREVIEW",
                0,
                0,
                0,
                Instant.now(),
                resolveAuditUsername(),
                "error",
                List.of(),
                ex.getMessage()
            ));
        }
    }

    private static OpsPolicyDomainStateDto toDomainStateDto(OpsPolicyService.DomainPolicyState state) {
        return new OpsPolicyDomainStateDto(
            state.domain(),
            state.currentVersion(),
            state.updatedAt(),
            state.actor(),
            state.reason(),
            state.policies().stream().map(OpsPolicyController::toPolicyDto).toList()
        );
    }

    private static OpsPolicyProfileDto toPolicyDto(PreviewFailurePolicyRegistry.PreviewFailurePolicy policy) {
        return new OpsPolicyProfileDto(
            policy.key(),
            policy.label(),
            policy.maxAttempts(),
            policy.retryDelayMs(),
            policy.backoffMultiplier(),
            policy.quietPeriodMs(),
            policy.builtIn()
        );
    }

    private static String resolveAuditUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "system";
        }
        return authentication.getName();
    }

    public record OpsPolicyDomainStateDto(
        String domain,
        long currentVersion,
        Instant updatedAt,
        String actor,
        String reason,
        List<OpsPolicyProfileDto> policies
    ) {}

    public record OpsPolicyProfileDto(
        String key,
        String label,
        int maxAttempts,
        long retryDelayMs,
        double backoffMultiplier,
        long quietPeriodMs,
        boolean builtIn
    ) {}

    public record OpsPolicyUpdateRequestDto(
        String profileKey,
        Integer maxAttempts,
        Long retryDelayMs,
        Double backoffMultiplier,
        Long quietPeriodMs,
        String reason
    ) {}

    public record OpsPolicyUpdateResponseDto(
        String domain,
        long currentVersion,
        Instant updatedAt,
        String actor,
        String reason,
        OpsPolicyProfileDto updatedPolicy,
        List<OpsPolicyProfileDto> policies,
        String error
    ) {}

    public record OpsPolicyRollbackRequestDto(
        Long targetVersion,
        String reason
    ) {}

    public record OpsPolicyRollbackResponseDto(
        String domain,
        long previousVersion,
        long rolledBackToVersion,
        long currentVersion,
        Instant updatedAt,
        String actor,
        String reason,
        List<OpsPolicyProfileDto> policies,
        String error
    ) {}

    public record OpsPolicyHistoryEntryDto(
        long version,
        Instant updatedAt,
        String actor,
        String reason
    ) {}

    public record OpsPolicyHistoryResponseDto(
        String domain,
        long currentVersion,
        List<OpsPolicyHistoryEntryDto> history
    ) {}
}
