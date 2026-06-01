package com.ecm.core.repository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryNullableDateQueryShapeTest {

    @Test
    @DisplayName("staging-exposed audit timelines avoid nullable timestamp predicates")
    void stagingExposedAuditTimelinesAvoidNullableTimestampPredicates() throws Exception {
        assertUsesCoalesce(AuditLogRepository.class, "countByEventTypePrefixWithFilters",
            String.class, LocalDateTime.class, String.class, String.class);
        assertUsesCoalesce(AuditLogRepository.class, "countByUsernamePrefixWithFilters",
            String.class, LocalDateTime.class, String.class, String.class);
        assertUsesCoalesce(AuditLogRepository.class, "findRuleAuditTimeline",
            String.class, String.class, UUID.class, LocalDateTime.class, LocalDateTime.class, Pageable.class);
        assertUsesCoalesce(AuditLogRepository.class, "findRuleAuditTimelineNoNodeId",
            String.class, String.class, LocalDateTime.class, LocalDateTime.class, Pageable.class);
        assertUsesCoalesce(AuditLogRepository.class, "findRecordsManagementTimeline",
            String.class, String.class, LocalDateTime.class, LocalDateTime.class, Pageable.class);
        assertUsesCoalesce(AuditLogRepository.class, "findByEventTypesAndFilters",
            List.class, String.class, LocalDateTime.class, LocalDateTime.class, Pageable.class);
        assertUsesCoalesce(AuditLogRepository.class, "findOtherRecordsManagementTimeline",
            List.class, String.class, String.class, LocalDateTime.class, LocalDateTime.class, Pageable.class);
    }

    @Test
    @DisplayName("staging-exposed preview diagnostics avoid nullable timestamp predicates")
    void stagingExposedPreviewDiagnosticsAvoidNullableTimestampPredicates() throws Exception {
        assertUsesCoalesce(DocumentRepository.class, "findRecentPreviewFailuresByWindow",
            List.class, LocalDateTime.class, Pageable.class);
        assertUsesCoalesce(DocumentRepository.class, "countPreviewFailuresByWindow",
            List.class, LocalDateTime.class);
        assertUsesCoalesce(DocumentRepository.class, "findPreviewFailuresByReasonAndWindow",
            List.class, LocalDateTime.class, String.class, Pageable.class);
        assertUsesCoalesce(DocumentRepository.class, "countPreviewFailuresByReasonAndWindow",
            List.class, LocalDateTime.class, String.class);
        assertUsesCoalesce(DocumentRepository.class, "findPreviewFailureLedgerEntries",
            LocalDateTime.class, Pageable.class);
        assertUsesCoalesce(DocumentRepository.class, "countPreviewFailureLedgerEntries",
            LocalDateTime.class);
    }

    @Test
    @DisplayName("rule audit has a no-node-id variant for null UUID filters")
    void ruleAuditHasNoNodeIdVariantForNullUuidFilters() throws Exception {
        Method method = AuditLogRepository.class.getDeclaredMethod(
            "findRuleAuditTimelineNoNodeId",
            String.class,
            String.class,
            LocalDateTime.class,
            LocalDateTime.class,
            Pageable.class
        );
        assertTrue(Page.class.isAssignableFrom(method.getReturnType()));
        assertFalse(queryValue(method).contains(":nodeId"));
    }

    private static void assertUsesCoalesce(Class<?> repository, String methodName, Class<?>... parameterTypes)
        throws Exception {
        String query = queryValue(repository.getDeclaredMethod(methodName, parameterTypes));
        assertTrue(query.contains("COALESCE("), methodName + " should use COALESCE for nullable timestamp filters");
        assertFalse(query.contains(":from IS NULL OR"), methodName + " should not use nullable :from predicate");
        assertFalse(query.contains(":to IS NULL OR"), methodName + " should not use nullable :to predicate");
        assertFalse(query.contains(":updatedSince IS NULL OR"), methodName + " should not use nullable :updatedSince predicate");
        assertFalse(query.contains(":failedSince IS NULL OR"), methodName + " should not use nullable :failedSince predicate");
    }

    private static String queryValue(Method method) {
        Query query = method.getAnnotation(Query.class);
        assertTrue(query != null, method.getName() + " must declare @Query");
        return query.value();
    }
}
