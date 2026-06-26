package com.ecm.core.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * §3A.3: jobId is a stable {@code declaringClass#methodName} (no CGLIB / proxy class name), with a
 * deterministic suffix so overloaded / same-named methods stay distinct.
 */
class SchedulerJobIdsTest {

    static class Alpha {
        public void run() {
        }

        public void task(String s) {
        }

        public void task(int i) {
        }
    }

    static class Beta {
        public void run() {
        }
    }

    @Test
    @DisplayName("stable declaringClass#methodName, no CGLIB marker")
    void stableDeclaringClassMethodName() throws Exception {
        String id = SchedulerJobIds.of(Alpha.class, Alpha.class.getMethod("run"));
        assertEquals(Alpha.class.getName() + "#run", id);
        assertFalse(id.contains("$$"), "must not carry a CGLIB/proxy marker");
    }

    @Test
    @DisplayName("overloaded methods get distinct, deterministic ids")
    void overloadsAreDistinctAndDeterministic() throws Exception {
        String byString = SchedulerJobIds.of(Alpha.class, Alpha.class.getMethod("task", String.class));
        String byInt = SchedulerJobIds.of(Alpha.class, Alpha.class.getMethod("task", int.class));
        assertNotEquals(byString, byInt);
        assertEquals(byString, SchedulerJobIds.of(Alpha.class, Alpha.class.getMethod("task", String.class)));
    }

    @Test
    @DisplayName("same method name in different classes is distinct")
    void sameMethodNameDifferentClassesDistinct() throws Exception {
        assertNotEquals(
            SchedulerJobIds.of(Alpha.class, Alpha.class.getMethod("run")),
            SchedulerJobIds.of(Beta.class, Beta.class.getMethod("run")));
    }
}
