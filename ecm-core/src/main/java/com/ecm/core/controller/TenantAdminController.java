package com.ecm.core.controller;

import com.ecm.core.service.TenantMetricsService;
import com.ecm.core.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Tenant Admin", description = "Multi-tenant control plane backbone")
public class TenantAdminController {

    private final TenantService tenantService;
    private final TenantMetricsService tenantMetricsService;

    @GetMapping({"/api/admin/tenants", "/api/v1/admin/tenants"})
    @Operation(summary = "List tenants")
    public ResponseEntity<java.util.List<TenantService.TenantDto>> listTenants() {
        return ResponseEntity.ok(tenantService.listTenants());
    }

    @GetMapping({"/api/admin/tenants/current", "/api/v1/admin/tenants/current"})
    @Operation(summary = "Get current request tenant")
    public ResponseEntity<TenantService.TenantDto> getCurrentTenant() {
        return ResponseEntity.ok(tenantService.getCurrentTenant());
    }

    @GetMapping({"/api/admin/tenants/{tenantDomain}", "/api/v1/admin/tenants/{tenantDomain}"})
    @Operation(summary = "Get a tenant by domain")
    public ResponseEntity<TenantService.TenantDto> getTenant(@PathVariable String tenantDomain) {
        return ResponseEntity.ok(tenantService.getTenant(tenantDomain));
    }

    @PostMapping({"/api/admin/tenants", "/api/v1/admin/tenants"})
    @Operation(summary = "Create a tenant")
    public ResponseEntity<TenantService.TenantDto> createTenant(@RequestBody TenantService.TenantMutationRequest request) {
        return ResponseEntity.status(201).body(tenantService.createTenant(request));
    }

    @PutMapping({"/api/admin/tenants/{tenantDomain}", "/api/v1/admin/tenants/{tenantDomain}"})
    @Operation(summary = "Update a tenant")
    public ResponseEntity<TenantService.TenantDto> updateTenant(
        @PathVariable String tenantDomain,
        @RequestBody TenantService.TenantMutationRequest request
    ) {
        return ResponseEntity.ok(tenantService.updateTenant(tenantDomain, request));
    }

    @DeleteMapping({"/api/admin/tenants/{tenantDomain}", "/api/v1/admin/tenants/{tenantDomain}"})
    @Operation(summary = "Delete a tenant")
    public ResponseEntity<Void> deleteTenant(@PathVariable String tenantDomain) {
        tenantService.deleteTenant(tenantDomain);
        return ResponseEntity.noContent().build();
    }

    @GetMapping({"/api/admin/tenants/{tenantDomain}/metrics", "/api/v1/admin/tenants/{tenantDomain}/metrics"})
    @Operation(summary = "Get tenant resource metrics")
    public ResponseEntity<TenantMetricsService.TenantMetrics> getTenantMetrics(@PathVariable String tenantDomain) {
        return ResponseEntity.ok(tenantMetricsService.getMetrics(tenantDomain));
    }
}
