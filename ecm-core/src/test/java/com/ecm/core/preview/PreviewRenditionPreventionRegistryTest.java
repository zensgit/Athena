package com.ecm.core.preview;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewRenditionPreventionRegistryTest {

    @Test
    void blocksListAndHitCountersWork() {
        PreviewRenditionPreventionRegistry registry = new PreviewRenditionPreventionRegistry();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        registry.block(first, "permanent failure", PreviewFailureClassifier.CATEGORY_PERMANENT);
        registry.block(second, "unsupported", PreviewFailureClassifier.CATEGORY_UNSUPPORTED);
        registry.markBlockedHit(second);
        registry.markBlockedHit(second);

        assertEquals(2, registry.list(10).size());
        assertEquals(second, registry.list(10).get(0).documentId());
        PreviewRenditionPreventionRegistry.BlockedEntry secondEntry = registry.get(second);
        assertNotNull(secondEntry);
        assertEquals(2L, secondEntry.hitCount());
        assertEquals(2, registry.getBlockedCount());
    }

    @Test
    void trimsOverflowByConfiguredLimit() {
        PreviewRenditionPreventionRegistry registry = new PreviewRenditionPreventionRegistry();
        registry.setMaxBlocked(100);

        UUID first = UUID.randomUUID();
        registry.block(first, "first", PreviewFailureClassifier.CATEGORY_PERMANENT);
        UUID last = first;
        for (int i = 0; i < 100; i++) {
            last = UUID.randomUUID();
            registry.block(last, "item-" + i, PreviewFailureClassifier.CATEGORY_PERMANENT);
        }

        assertNull(registry.get(first));
        assertNotNull(registry.get(last));
        assertEquals(100, registry.list(200).size());
    }

    @Test
    void normalizesAutoBlockCategoriesAndCanDisable() {
        PreviewRenditionPreventionRegistry registry = new PreviewRenditionPreventionRegistry();

        registry.setAutoBlockCategories("unsupported, permanent");
        assertTrue(registry.shouldAutoBlock("UNSUPPORTED"));
        assertTrue(registry.shouldAutoBlock("permanent"));

        registry.setEnabled(false);
        assertEquals(0, registry.list(10).size());
        assertFalse(registry.shouldAutoBlock("UNSUPPORTED"));
    }
}
