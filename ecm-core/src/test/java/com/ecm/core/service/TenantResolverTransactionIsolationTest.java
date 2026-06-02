package com.ecm.core.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mechanism guard for the Q2b transaction-poisoning regression.
 *
 * <p>The bug: {@link TenantContextResolverService} was {@code @Transactional(readOnly = true)} and
 * THREW a business outcome ({@code TargetFolderTenantException}) on reject. Thrown across its proxy it
 * marked the calling scheduled-writer's {@code REQUIRES_NEW} transaction rollback-only, so the
 * writer's clean-FAILED save hit {@link UnexpectedRollbackException} on commit and processedCount went
 * to 0 — the RM-notification E2E gate failure first seen on {@code d1dd45b}. The unit suite stayed
 * green throughout because a Mockito resolver throwing does NOT mark a real transaction rollback-only;
 * only a real proxied transaction reveals it. Hence this {@code @DataJpaTest}.
 *
 * <p>Two tests lock the mechanism in real transactions:
 * <ul>
 *   <li>{@link #throwingAcrossReadOnlyProxyPoisonsRequiresNewCaller_teeth} reproduces the hazard — a
 *       {@code @Transactional(readOnly)} bean that throws poisons a {@code REQUIRES_NEW} caller even
 *       when the caller catches it. These are the teeth: if this stops throwing
 *       {@code UnexpectedRollbackException}, the harness proves nothing.</li>
 *   <li>{@link #returningAcrossReadOnlyProxyLeavesCallerCommittable_fix} is the fixed shape — a
 *       {@code @Transactional(readOnly)} bean that RETURNS leaves the caller committable.</li>
 * </ul>
 *
 * <p>The real resolver's returning-contract is verified by {@code TenantContextResolverServiceTest}
 * (unit) and end-to-end by the RM-notification gate against real Postgres ({@code Node} carries jsonb
 * columns, so a real-resolver slice can't run on H2 here).
 */
@DataJpaTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.liquibase.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:txisolation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@ContextConfiguration(classes = TenantResolverTransactionIsolationTest.JpaTestConfig.class)
@Import({
    TenantResolverTransactionIsolationTest.ThrowingReadOnlyBean.class,
    TenantResolverTransactionIsolationTest.ReturningReadOnlyBean.class,
    TenantResolverTransactionIsolationTest.RequiresNewCaller.class
})
class TenantResolverTransactionIsolationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class JpaTestConfig {
    }

    @Autowired
    private RequiresNewCaller caller;

    @Test
    void throwingAcrossReadOnlyProxyPoisonsRequiresNewCaller_teeth() {
        // Pre-fix behaviour. The caller catches the exception (as the writers' catch blocks do), yet
        // the REQUIRES_NEW transaction is already marked rollback-only, so its commit fails. If this
        // assertion ever stops holding, this harness has no teeth and the fix test below proves nothing.
        assertThrows(UnexpectedRollbackException.class, caller::viaThrowingResolver);
    }

    @Test
    void returningAcrossReadOnlyProxyLeavesCallerCommittable_fix() {
        // Post-fix behaviour: the resolver returns a result instead of throwing, so nothing marks the
        // caller's REQUIRES_NEW transaction rollback-only and the writer's clean-FAILED / scoped write
        // commits normally.
        assertDoesNotThrow(caller::viaReturningResolver);
    }

    /** Mirrors the pre-fix resolver: a {@code @Transactional(readOnly)} bean that throws on reject. */
    @Component
    static class ThrowingReadOnlyBean {
        @Transactional(readOnly = true)
        public void resolve() {
            throw new IllegalStateException("simulated pre-fix resolver reject thrown across a readOnly proxy");
        }
    }

    /** Mirrors the fixed resolver: a {@code @Transactional(readOnly)} bean that returns a result. */
    @Component
    static class ReturningReadOnlyBean {
        @Transactional(readOnly = true)
        public String resolve() {
            return "UNRESOLVED";
        }
    }

    /**
     * Mirrors {@code RmReportPresetDeliveryService.processOneScheduledDelivery}'s {@code REQUIRES_NEW}
     * boundary plus {@code deliverPreset}'s catch that persists a clean FAILED execution.
     */
    @Component
    static class RequiresNewCaller {
        private final ThrowingReadOnlyBean throwing;
        private final ReturningReadOnlyBean returning;

        RequiresNewCaller(ThrowingReadOnlyBean throwing, ReturningReadOnlyBean returning) {
            this.throwing = throwing;
            this.returning = returning;
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void viaThrowingResolver() {
            try {
                throwing.resolve();
            } catch (RuntimeException e) {
                // Caught, exactly as the writers catch a reject — but the transaction is already poisoned.
            }
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void viaReturningResolver() {
            returning.resolve();
        }
    }
}
