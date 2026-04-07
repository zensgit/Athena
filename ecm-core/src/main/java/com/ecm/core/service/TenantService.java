package com.ecm.core.service;

import com.ecm.core.config.TenantContext;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Folder.FolderType;
import com.ecm.core.entity.Tenant;
import com.ecm.core.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenantService {

    public static final String DEFAULT_TENANT_DOMAIN = "default";

    private final TenantRepository tenantRepository;
    private final SecurityService securityService;
    private final FolderService folderService;

    public List<TenantDto> listTenants() {
        requireAdmin();
        return tenantRepository.findByDeletedFalseOrderByTenantDomainAsc().stream()
            .map(this::toDto)
            .toList();
    }

    public TenantDto getTenant(String tenantDomain) {
        requireAdmin();
        return toDto(getRequiredTenant(tenantDomain));
    }

    @Transactional
    public TenantDto createTenant(TenantMutationRequest request) {
        requireAdmin();
        String tenantDomain = normalizeDomain(request.tenantDomain());
        String tenantName = normalizeName(request.tenantName());
        if (tenantRepository.existsByTenantDomainIgnoreCaseAndDeletedFalse(tenantDomain)) {
            throw new IllegalArgumentException("Tenant already exists: " + tenantDomain);
        }

        Tenant tenant = new Tenant();
        tenant.setTenantDomain(tenantDomain);
        tenant.setTenantName(tenantName);
        tenant.setEnabled(request.enabled() == null || request.enabled());
        tenant.setQuotaBytes(request.quotaBytes());
        tenant.setSystemDefault(false);
        tenant.setRootNodeId(bootstrapRootWorkspace(tenantDomain, tenantName).getId());
        return toDto(tenantRepository.save(tenant));
    }

    @Transactional
    public TenantDto updateTenant(String tenantDomain, TenantMutationRequest request) {
        requireAdmin();
        Tenant tenant = getRequiredTenant(tenantDomain);
        String tenantName = normalizeName(request.tenantName());
        tenant.setTenantName(tenantName);
        if (request.rootNodeId() != null && !Objects.equals(request.rootNodeId(), tenant.getRootNodeId())) {
            throw new IllegalArgumentException("rootNodeId is managed automatically");
        }
        if (request.enabled() != null) {
            if (tenant.isSystemDefault() && !request.enabled()) {
                throw new IllegalArgumentException("Default tenant cannot be disabled");
            }
            if (!request.enabled() && isCurrentRequestTenant(tenant.getTenantDomain())) {
                throw new IllegalArgumentException("Current request tenant cannot be disabled");
            }
            tenant.setEnabled(request.enabled());
        }
        if (tenant.getRootNodeId() == null) {
            tenant.setRootNodeId(bootstrapRootWorkspace(tenant.getTenantDomain(), tenantName).getId());
        }
        tenant.setQuotaBytes(request.quotaBytes());
        return toDto(tenantRepository.save(tenant));
    }

    @Transactional
    public void deleteTenant(String tenantDomain) {
        requireAdmin();
        Tenant tenant = getRequiredTenant(tenantDomain);
        if (tenant.isSystemDefault()) {
            throw new IllegalArgumentException("Default tenant cannot be deleted");
        }
        if (isCurrentRequestTenant(tenant.getTenantDomain())) {
            throw new IllegalArgumentException("Current request tenant cannot be deleted");
        }
        if (tenant.getRootNodeId() != null) {
            throw new IllegalArgumentException("Tenant workspace must be deprovisioned before deletion");
        }
        tenant.setDeleted(true);
        tenant.setDeletedAt(LocalDateTime.now());
        tenant.setDeletedBy(securityService.getCurrentUser());
        tenantRepository.save(tenant);
    }

    public TenantDto getCurrentTenant() {
        return toDto(resolveTenantForRequest(TenantContext.getCurrentTenantDomain()));
    }

    public TenantDto resolveCurrentTenant(String requestedTenantDomain) {
        return toDto(resolveTenantForRequest(requestedTenantDomain));
    }

    public Tenant resolveTenantForRequest(String requestedTenantDomain) {
        String effectiveDomain = normalizeRequestedDomain(requestedTenantDomain);
        Tenant tenant = tenantRepository.findByTenantDomainIgnoreCaseAndDeletedFalse(effectiveDomain)
            .orElseThrow(() -> new NoSuchElementException("Tenant not found: " + effectiveDomain));
        if (!tenant.isEnabled()) {
            throw new SecurityException("Tenant is disabled: " + effectiveDomain);
        }
        return tenant;
    }

    private Tenant getRequiredTenant(String tenantDomain) {
        String normalized = normalizeRequestedDomain(tenantDomain);
        return tenantRepository.findByTenantDomainIgnoreCaseAndDeletedFalse(normalized)
            .orElseThrow(() -> new NoSuchElementException("Tenant not found: " + normalized));
    }

    private void requireAdmin() {
        if (!securityService.hasRole("ROLE_ADMIN")) {
            throw new SecurityException("Admin role required");
        }
    }

    private String normalizeDomain(String tenantDomain) {
        if (tenantDomain == null || tenantDomain.isBlank()) {
            throw new IllegalArgumentException("tenantDomain is required");
        }
        return tenantDomain.trim().toLowerCase();
    }

    private String normalizeRequestedDomain(String tenantDomain) {
        if (tenantDomain == null || tenantDomain.isBlank()) {
            return DEFAULT_TENANT_DOMAIN;
        }
        return tenantDomain.trim().toLowerCase();
    }

    private String normalizeName(String tenantName) {
        if (tenantName == null || tenantName.isBlank()) {
            throw new IllegalArgumentException("tenantName is required");
        }
        return tenantName.trim();
    }

    private Folder bootstrapRootWorkspace(String tenantDomain, String tenantName) {
        return folderService.createFolder(new FolderService.CreateFolderRequest(
            buildWorkspaceName(tenantDomain, tenantName),
            "Tenant workspace for " + tenantName + " (" + tenantDomain + ")",
            null,
            FolderType.WORKSPACE,
            null,
            null,
            null,
            null,
            true,
            false,
            null
        ));
    }

    private String buildWorkspaceName(String tenantDomain, String tenantName) {
        return tenantName + " Workspace [" + tenantDomain + "]";
    }

    private boolean isCurrentRequestTenant(String tenantDomain) {
        return normalizeRequestedDomain(TenantContext.getCurrentTenantDomain()).equals(normalizeRequestedDomain(tenantDomain));
    }

    private TenantDto toDto(Tenant tenant) {
        return new TenantDto(
            tenant.getId(),
            tenant.getTenantDomain(),
            tenant.getTenantName(),
            tenant.isEnabled(),
            tenant.getRootNodeId(),
            tenant.getQuotaBytes(),
            tenant.isSystemDefault(),
            tenant.getCreatedDate(),
            tenant.getLastModifiedDate()
        );
    }

    public record TenantDto(
        UUID id,
        String tenantDomain,
        String tenantName,
        boolean enabled,
        UUID rootNodeId,
        Long quotaBytes,
        boolean systemDefault,
        LocalDateTime createdDate,
        LocalDateTime lastModifiedDate
    ) {
    }

    public record TenantMutationRequest(
        String tenantDomain,
        String tenantName,
        Boolean enabled,
        UUID rootNodeId,
        Long quotaBytes
    ) {
    }
}
