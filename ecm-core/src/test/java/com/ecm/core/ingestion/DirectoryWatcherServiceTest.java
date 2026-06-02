package com.ecm.core.ingestion;

import com.ecm.core.config.TenantContext;
import com.ecm.core.service.DocumentUploadService;
import com.ecm.core.service.FolderService;
import com.ecm.core.service.TenantContextResolverService;
import com.ecm.core.service.TenantContextResolverService.TenantResolution;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectoryWatcherServiceTest {

    @Mock private DocumentUploadService uploadService;
    @Mock private FolderService folderService;
    @Mock private TenantContextResolverService tenantContextResolverService;

    @InjectMocks private DirectoryWatcherService service;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void setsResolvedTenantContextDuringUploadAndClearsAfter() throws Exception {
        UUID folderId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        ReflectionTestUtils.setField(service, "targetFolderId", folderId);
        when(tenantContextResolverService.resolveTenantForTargetFolder(folderId))
            .thenReturn(TenantResolution.resolved("acme", rootId));
        // The tenant must be set AT upload time — assert inside the upload stub.
        when(uploadService.uploadDocument(any(), eq(folderId), isNull())).thenAnswer(inv -> {
            assertEquals("acme", TenantContext.getCurrentTenantDomain());
            assertEquals(rootId, TenantContext.getCurrentTenantRootNodeId());
            return null;
        });

        boolean ok = service.ingestUnderResolvedTenant(file("a.txt"), "a.txt");

        assertTrue(ok);
        verify(uploadService).uploadDocument(any(), eq(folderId), isNull());
        // cleared after the upload — no leak into the next scheduled poll on this thread.
        assertNull(TenantContext.getCurrentTenantDomain());
        assertNull(TenantContext.getCurrentTenantRootNodeId());
    }

    @Test
    void skipsWhenTargetFolderNotConfigured() throws Exception {
        ReflectionTestUtils.setField(service, "targetFolderId", null);

        boolean ok = service.ingestUnderResolvedTenant(file("a.txt"), "a.txt");

        assertFalse(ok);
        verifyNoInteractions(uploadService);
    }

    @Test
    void skipsWhenTargetFolderNotUnderTenant() throws Exception {
        UUID folderId = UUID.randomUUID();
        ReflectionTestUtils.setField(service, "targetFolderId", folderId);
        when(tenantContextResolverService.resolveTenantForTargetFolder(folderId))
            .thenReturn(TenantResolution.unresolved());

        boolean ok = service.ingestUnderResolvedTenant(file("a.txt"), "a.txt");

        assertFalse(ok);
        verifyNoInteractions(uploadService);
        assertNull(TenantContext.getCurrentTenantDomain()); // context not set on reject
    }

    @Test
    void restoresCallerTenantWhenUploadThrows() throws Exception {
        UUID folderId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        ReflectionTestUtils.setField(service, "targetFolderId", folderId);
        // Simulate a manual/request-thread trigger that already has its own tenant.
        TenantContext.setCurrentTenantDomain("caller-tenant");
        when(tenantContextResolverService.resolveTenantForTargetFolder(folderId))
            .thenReturn(TenantResolution.resolved("acme", rootId));
        when(uploadService.uploadDocument(any(), eq(folderId), isNull()))
            .thenThrow(new java.io.IOException("boom"));

        assertThrows(java.io.IOException.class,
            () -> service.ingestUnderResolvedTenant(file("a.txt"), "a.txt"));

        // The caller's original tenant is restored (not cleared, not left as the resolved "acme").
        assertEquals("caller-tenant", TenantContext.getCurrentTenantDomain());
    }

    private MultipartFile file(String name) {
        return new MockMultipartFile(name, name, "text/plain", "hello".getBytes());
    }
}
