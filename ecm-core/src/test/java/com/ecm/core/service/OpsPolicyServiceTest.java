package com.ecm.core.service;

import com.ecm.core.preview.PreviewFailurePolicyRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpsPolicyServiceTest {

    private OpsPolicyService opsPolicyService;

    @BeforeEach
    void setUp() {
        opsPolicyService = new OpsPolicyService(new PreviewFailurePolicyRegistry());
        opsPolicyService.initialize();
    }

    @Test
    @DisplayName("Initial state has bootstrap snapshot version")
    void initialStateHasBootstrapVersion() {
        OpsPolicyService.DomainPolicyState state = opsPolicyService.getState("PREVIEW");

        assertThat(state.domain()).isEqualTo("PREVIEW");
        assertThat(state.currentVersion()).isGreaterThan(0);
        assertThat(state.actor()).isEqualTo("system");
        assertThat(state.reason()).isEqualTo("bootstrap");
        assertThat(state.policies()).isNotEmpty();
    }

    @Test
    @DisplayName("updatePolicy increments version and mutates target profile values")
    void updatePolicyIncrementsVersionAndMutatesTargetProfileValues() {
        long versionBeforeUpdate = opsPolicyService.getState("PREVIEW").currentVersion();
        PreviewFailurePolicyRegistry.PreviewFailurePolicyUpdate update =
            new PreviewFailurePolicyRegistry.PreviewFailurePolicyUpdate(7, 120000L, 2.5d, 3000L);

        OpsPolicyService.DomainPolicyUpdateResult result =
            opsPolicyService.updatePolicy("PREVIEW", "pdf", update, "tester", "tune_pdf_policy");

        assertThat(result.currentVersion()).isEqualTo(versionBeforeUpdate + 1);
        assertThat(result.updatedPolicy().key()).isEqualTo("pdf");
        assertThat(result.updatedPolicy().maxAttempts()).isEqualTo(7);
        assertThat(result.updatedPolicy().retryDelayMs()).isEqualTo(120000L);
        assertThat(result.updatedPolicy().backoffMultiplier()).isEqualTo(2.5d);
        assertThat(result.updatedPolicy().quietPeriodMs()).isEqualTo(3000L);

        PreviewFailurePolicyRegistry.PreviewFailurePolicy statePolicy =
            policyForKey(opsPolicyService.getState("PREVIEW").policies(), "pdf");
        assertThat(statePolicy.maxAttempts()).isEqualTo(7);
        assertThat(statePolicy.retryDelayMs()).isEqualTo(120000L);
        assertThat(statePolicy.backoffMultiplier()).isEqualTo(2.5d);
        assertThat(statePolicy.quietPeriodMs()).isEqualTo(3000L);
    }

    @Test
    @DisplayName("rollback without target reverts to previous snapshot and increments currentVersion")
    void rollbackWithoutTargetRevertsToPreviousSnapshotAndIncrementsVersion() {
        OpsPolicyService.DomainPolicyUpdateResult firstUpdate = opsPolicyService.updatePolicy(
            "PREVIEW",
            "pdf",
            new PreviewFailurePolicyRegistry.PreviewFailurePolicyUpdate(8, 80000L, 1.7d, 1000L),
            "tester",
            "first_update"
        );
        opsPolicyService.updatePolicy(
            "PREVIEW",
            "pdf",
            new PreviewFailurePolicyRegistry.PreviewFailurePolicyUpdate(2, 5000L, 1.1d, 0L),
            "tester",
            "second_update"
        );

        long versionBeforeRollback = opsPolicyService.getState("PREVIEW").currentVersion();

        OpsPolicyService.DomainPolicyRollbackResult rollbackResult =
            opsPolicyService.rollback("PREVIEW", null, "tester", null);

        assertThat(rollbackResult.previousVersion()).isEqualTo(versionBeforeRollback);
        assertThat(rollbackResult.rolledBackToVersion()).isEqualTo(firstUpdate.currentVersion());
        assertThat(rollbackResult.currentVersion()).isEqualTo(versionBeforeRollback + 1);

        PreviewFailurePolicyRegistry.PreviewFailurePolicy rolledBackPolicy =
            policyForKey(opsPolicyService.getState("PREVIEW").policies(), "pdf");
        assertThat(rolledBackPolicy.maxAttempts()).isEqualTo(8);
        assertThat(rolledBackPolicy.retryDelayMs()).isEqualTo(80000L);
        assertThat(rolledBackPolicy.backoffMultiplier()).isEqualTo(1.7d);
        assertThat(rolledBackPolicy.quietPeriodMs()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("rollback with invalid target version throws IllegalArgumentException")
    void rollbackWithInvalidTargetVersionThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> opsPolicyService.rollback("PREVIEW", 9999L, "tester", "invalid_target"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Rollback target version not found");
    }

    @Test
    @DisplayName("listHistory returns latest versions first with limit applied")
    void listHistoryReturnsLatestFirstWithLimit() {
        opsPolicyService.updatePolicy(
            "PREVIEW",
            "pdf",
            new PreviewFailurePolicyRegistry.PreviewFailurePolicyUpdate(4, 60000L, 1.8d, 0L),
            "tester",
            "update1"
        );
        opsPolicyService.updatePolicy(
            "PREVIEW",
            "cad",
            new PreviewFailurePolicyRegistry.PreviewFailurePolicyUpdate(6, 90000L, 2.0d, 30000L),
            "tester",
            "update2"
        );

        List<OpsPolicyService.DomainPolicyHistoryEntry> history = opsPolicyService.listHistory("PREVIEW", 2);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).version()).isGreaterThan(history.get(1).version());
        assertThat(history.get(0).reason()).isEqualTo("update2");
    }

    private PreviewFailurePolicyRegistry.PreviewFailurePolicy policyForKey(
        List<PreviewFailurePolicyRegistry.PreviewFailurePolicy> policies,
        String key
    ) {
        return policies.stream()
            .filter(policy -> policy.key().equals(key))
            .findFirst()
            .orElseThrow();
    }
}
