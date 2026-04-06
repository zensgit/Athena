package com.ecm.core.config;

import com.ecm.core.service.TenantService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantFilterTest {

    @Mock private TenantService tenantService;
    @Mock private FilterChain filterChain;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Filter resolves default tenant when header absent")
    void filterResolvesDefaultTenant() throws Exception {
        when(tenantService.resolveCurrentTenant(null)).thenReturn(defaultTenant());

        TenantFilter filter = new TenantFilter(tenantService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/tenants");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(tenantService).resolveCurrentTenant(null);
        assertEquals("default", response.getHeader(TenantFilter.TENANT_HEADER));
        assertNull(TenantContext.getCurrentTenantDomain());
    }

    @Test
    @DisplayName("Filter returns not found for unknown tenant")
    void filterReturnsNotFoundForUnknownTenant() throws Exception {
        when(tenantService.resolveCurrentTenant("missing")).thenThrow(new NoSuchElementException("Tenant not found: missing"));

        TenantFilter filter = new TenantFilter(tenantService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/tenants");
        request.addHeader(TenantFilter.TENANT_HEADER, "missing");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertEquals(404, response.getStatus());
    }

    private TenantService.TenantDto defaultTenant() {
        return new TenantService.TenantDto(
            UUID.randomUUID(),
            "default",
            "Default Tenant",
            true,
            null,
            null,
            true,
            LocalDateTime.of(2026, 4, 6, 9, 0),
            LocalDateTime.of(2026, 4, 6, 9, 0)
        );
    }
}
