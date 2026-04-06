package com.ecm.core.service;

import com.ecm.core.config.TenantContext;
import com.ecm.core.entity.Tenant;
import com.ecm.core.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private SecurityService securityService;

    private TenantService tenantService;

    @BeforeEach
    void setUp() {
        tenantService = new TenantService(tenantRepository, securityService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("createTenant normalizes domain and saves tenant")
    void createTenantNormalizesDomain() {
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(tenantRepository.existsByTenantDomainIgnoreCaseAndDeletedFalse("acme")).thenReturn(false);
        when(tenantRepository.save(any())).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(UUID.randomUUID());
            return tenant;
        });

        TenantService.TenantDto saved = tenantService.createTenant(
            new TenantService.TenantMutationRequest(" AcMe ", " Acme Corp ", true, null, 1024L)
        );

        assertEquals("acme", saved.tenantDomain());
        assertEquals("Acme Corp", saved.tenantName());
        assertEquals(1024L, saved.quotaBytes());
    }

    @Test
    @DisplayName("listTenants returns sorted tenant registry")
    void listTenantsReturnsRegistry() {
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(tenantRepository.findByDeletedFalseOrderByTenantDomainAsc()).thenReturn(List.of(
            tenant("acme", "Acme", true, false),
            tenant("default", "Default Tenant", true, true)
        ));

        List<TenantService.TenantDto> tenants = tenantService.listTenants();

        assertEquals(2, tenants.size());
        assertEquals("acme", tenants.get(0).tenantDomain());
        assertEquals("default", tenants.get(1).tenantDomain());
    }

    @Test
    @DisplayName("updateTenant rejects disabling default tenant")
    void updateTenantRejectsDisablingDefaultTenant() {
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(tenantRepository.findByTenantDomainIgnoreCaseAndDeletedFalse("default"))
            .thenReturn(Optional.of(tenant("default", "Default Tenant", true, true)));

        assertThrows(IllegalArgumentException.class, () -> tenantService.updateTenant(
            "default",
            new TenantService.TenantMutationRequest("default", "Default Tenant", false, null, null)
        ));
    }

    @Test
    @DisplayName("getCurrentTenant uses thread local fallback")
    void getCurrentTenantUsesThreadLocalFallback() {
        when(tenantRepository.findByTenantDomainIgnoreCaseAndDeletedFalse("default"))
            .thenReturn(Optional.of(tenant("default", "Default Tenant", true, true)));

        TenantService.TenantDto current = tenantService.getCurrentTenant();

        assertEquals("default", current.tenantDomain());
    }

    @Test
    @DisplayName("resolveCurrentTenant rejects disabled tenant")
    void resolveCurrentTenantRejectsDisabledTenant() {
        when(tenantRepository.findByTenantDomainIgnoreCaseAndDeletedFalse("acme"))
            .thenReturn(Optional.of(tenant("acme", "Acme", false, false)));

        assertThrows(SecurityException.class, () -> tenantService.resolveCurrentTenant("acme"));
    }

    private Tenant tenant(String domain, String name, boolean enabled, boolean systemDefault) {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setTenantDomain(domain);
        tenant.setTenantName(name);
        tenant.setEnabled(enabled);
        tenant.setSystemDefault(systemDefault);
        return tenant;
    }
}
