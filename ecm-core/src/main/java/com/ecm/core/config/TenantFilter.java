package com.ecm.core.config;

import com.ecm.core.service.TenantService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.NoSuchElementException;

@RequiredArgsConstructor
public class TenantFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Tenant-ID";

    private final TenantService tenantService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String requestedTenant = request.getHeader(TENANT_HEADER);
        try {
            TenantService.TenantDto tenant = tenantService.resolveCurrentTenant(requestedTenant);
            TenantContext.setCurrentTenantDomain(tenant.tenantDomain());
            TenantContext.setCurrentTenantRootNodeId(tenant.rootNodeId());
            response.setHeader(TENANT_HEADER, tenant.tenantDomain());
            filterChain.doFilter(request, response);
        } catch (NoSuchElementException ex) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, ex.getMessage());
        } catch (SecurityException ex) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, ex.getMessage());
        } finally {
            TenantContext.clear();
        }
    }
}
