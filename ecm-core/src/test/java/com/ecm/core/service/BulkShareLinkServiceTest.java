package com.ecm.core.service;

import com.ecm.core.entity.ShareLink;
import com.ecm.core.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BulkShareLinkServiceTest {

    @Mock private ShareLinkService shareLinkService;

    private BulkShareLinkService service;

    private final ShareLinkService.CreateShareLinkRequest request =
        new ShareLinkService.CreateShareLinkRequest(null, null, null, null, null, null);

    @BeforeEach
    void setUp() {
        service = new BulkShareLinkService(shareLinkService);
    }

    private UUID id(String s) {
        return UUID.fromString(s);
    }

    @Test
    @DisplayName("null/empty nodeIds rejected before any per-row work")
    void emptyNodeIdsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.createShareLinksBulk(null, request));
        assertThrows(IllegalArgumentException.class, () -> service.createShareLinksBulk(List.of(), request));
    }

    @Test
    @DisplayName("null-only nodeIds rejected after dropping nulls")
    void nullOnlyNodeIdsRejected() {
        List<UUID> nodeIds = new ArrayList<>();
        nodeIds.add(null);
        assertThrows(IllegalArgumentException.class, () -> service.createShareLinksBulk(nodeIds, request));
    }

    @Test
    @DisplayName("dedupes duplicate IDs first-seen and creates one link per distinct node")
    void dedupesFirstSeen() {
        UUID a = id("11111111-1111-4111-8111-111111111111");
        UUID b = id("22222222-2222-4222-8222-222222222222");
        when(shareLinkService.createShareLink(eq(a), any())).thenReturn(mock(ShareLink.class));
        when(shareLinkService.createShareLink(eq(b), any())).thenReturn(mock(ShareLink.class));

        List<BulkShareLinkService.RowResult> rows = service.createShareLinksBulk(Arrays.asList(a, b, a), request);

        assertEquals(2, rows.size());
        assertEquals(a, rows.get(0).nodeId());
        assertEquals(b, rows.get(1).nodeId());
        verify(shareLinkService, times(1)).createShareLink(eq(a), any());
        verify(shareLinkService, times(1)).createShareLink(eq(b), any());
    }

    @Test
    @DisplayName("CREATED row carries the share link and null error fields")
    void createdCarriesShareLink() {
        UUID a = id("11111111-1111-4111-8111-111111111111");
        ShareLink link = mock(ShareLink.class);
        when(shareLinkService.createShareLink(eq(a), any())).thenReturn(link);

        BulkShareLinkService.RowResult row = service.createShareLinksBulk(List.of(a), request).get(0);

        assertEquals(BulkShareLinkService.Status.CREATED, row.status());
        assertNotNull(row.shareLink());
        assertNull(row.errorCategory());
        assertNull(row.message());
    }

    @Test
    @DisplayName("missing node (NoSuchElement / ResourceNotFound) maps to NODE_NOT_FOUND and run continues")
    void missingNodeMapsNotFoundAndContinues() {
        UUID a = id("11111111-1111-4111-8111-111111111111");
        UUID b = id("22222222-2222-4222-8222-222222222222");
        when(shareLinkService.createShareLink(eq(a), any())).thenThrow(new NoSuchElementException("Node not found"));
        when(shareLinkService.createShareLink(eq(b), any())).thenReturn(mock(ShareLink.class));

        List<BulkShareLinkService.RowResult> rows = service.createShareLinksBulk(Arrays.asList(a, b), request);

        assertEquals(BulkShareLinkService.Status.FAILED, rows.get(0).status());
        assertEquals(BulkShareLinkService.ErrorCategory.NODE_NOT_FOUND, rows.get(0).errorCategory());
        assertNull(rows.get(0).shareLink());
        // run continued: the second row still created
        assertEquals(BulkShareLinkService.Status.CREATED, rows.get(1).status());
    }

    @Test
    @DisplayName("ResourceNotFoundException also maps to NODE_NOT_FOUND")
    void invisibleNodeMapsNotFound() {
        UUID a = id("11111111-1111-4111-8111-111111111111");
        when(shareLinkService.createShareLink(eq(a), any())).thenThrow(new ResourceNotFoundException("Node not found"));

        BulkShareLinkService.RowResult row = service.createShareLinksBulk(List.of(a), request).get(0);
        assertEquals(BulkShareLinkService.ErrorCategory.NODE_NOT_FOUND, row.errorCategory());
    }

    @Test
    @DisplayName("SecurityException maps to NO_PERMISSION (READ contract preserved)")
    void securityMapsNoPermission() {
        UUID a = id("11111111-1111-4111-8111-111111111111");
        when(shareLinkService.createShareLink(eq(a), any())).thenThrow(new SecurityException("No permission to share this document"));

        BulkShareLinkService.RowResult row = service.createShareLinksBulk(List.of(a), request).get(0);
        assertEquals(BulkShareLinkService.ErrorCategory.NO_PERMISSION, row.errorCategory());
    }

    @Test
    @DisplayName("IllegalArgumentException (e.g. allowedIps) maps to VALIDATION_ERROR")
    void validationMaps() {
        UUID a = id("11111111-1111-4111-8111-111111111111");
        when(shareLinkService.createShareLink(eq(a), any())).thenThrow(new IllegalArgumentException("Invalid allowedIps entry: 999.999.999.999"));

        BulkShareLinkService.RowResult row = service.createShareLinksBulk(List.of(a), request).get(0);
        assertEquals(BulkShareLinkService.ErrorCategory.VALIDATION_ERROR, row.errorCategory());
        // Fixed copy, never echoes the (potentially sensitive) exception message.
        assertFalse(row.message().contains("999.999.999.999"));
    }

    @Test
    @DisplayName("unexpected RuntimeException sanitized to INTERNAL_ERROR (no probe leak)")
    void internalErrorSanitised() {
        UUID a = id("11111111-1111-4111-8111-111111111111");
        when(shareLinkService.createShareLink(eq(a), any()))
            .thenThrow(new RuntimeException("USER_PII_FROM_EXCEPTION_LEAK_PROBE"));

        BulkShareLinkService.RowResult row = service.createShareLinksBulk(List.of(a), request).get(0);
        assertEquals(BulkShareLinkService.ErrorCategory.INTERNAL_ERROR, row.errorCategory());
        assertFalse(row.message().contains("USER_PII_FROM_EXCEPTION_LEAK_PROBE"),
            "internal-error message must not leak the raw exception message");
        assertTrue(row.message().contains("RuntimeException"), "message carries the exception class name only");
    }
}
