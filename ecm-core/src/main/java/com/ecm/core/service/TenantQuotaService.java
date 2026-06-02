package com.ecm.core.service;

import com.ecm.core.config.TenantContext;
import com.ecm.core.entity.Tenant;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.TenantRepository;
import com.ecm.core.repository.VersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantQuotaService {

    private final TenantRepository tenantRepository;
    private final DocumentRepository documentRepository;
    private final NodeRepository nodeRepository;
    private final VersionRepository versionRepository;

    /**
     * Check whether storing {@code additionalBytes} would exceed the current tenant's quota.
     * Does nothing if the tenant has no quota configured (quotaBytes == null).
     *
     * @throws QuotaExceededException if the tenant's quota would be exceeded
     */
    public void assertQuotaAvailable(long additionalBytes) {
        if (additionalBytes <= 0) {
            return;
        }
        Tenant tenant = resolveCurrentTenant();
        if (tenant == null || tenant.getQuotaBytes() == null) {
            return; // No quota configured
        }
        long used = calculateUsedBytes(tenant);
        long remaining = tenant.getQuotaBytes() - used;
        if (additionalBytes > remaining) {
            throw new QuotaExceededException(
                tenant.getTenantDomain(),
                tenant.getQuotaBytes(),
                used,
                additionalBytes
            );
        }
    }

    /**
     * Best-effort preflight check using declared size (e.g. Content-Length).
     * Returns false if quota would be exceeded, true otherwise (including when no quota is set).
     */
    public boolean hasAvailableQuota(long declaredBytes) {
        if (declaredBytes <= 0) {
            return true;
        }
        Tenant tenant = resolveCurrentTenant();
        if (tenant == null || tenant.getQuotaBytes() == null) {
            return true;
        }
        long used = calculateUsedBytes(tenant);
        return declaredBytes <= (tenant.getQuotaBytes() - used);
    }

    public long calculateUsedBytes(Tenant tenant) {
        if (tenant.getRootNodeId() == null) {
            return 0;
        }
        String rootPath = nodeRepository.findById(tenant.getRootNodeId())
            .map(node -> node.getPath())
            .orElse(null);
        if (rootPath == null) {
            return 0;
        }
        // ADR-002 addendum model: logical current documents + non-current retained versions.
        // The current version's bytes equal the live Document.fileSize, so they are counted once
        // via the documents sum; only non-current retained versions add to usage. No physical
        // blob dedup (ADR-001 global dedup makes per-tenant physical accounting ill-defined).
        String pathPrefix = rootPath + "/%";
        long liveDocuments = documentRepository.sumFileSizeByPathPrefix(pathPrefix);
        long retainedVersions = versionRepository.sumNonCurrentVersionFileSizeByPathPrefix(pathPrefix);
        return liveDocuments + retainedVersions;
    }

    private Tenant resolveCurrentTenant() {
        String domain = TenantContext.getCurrentTenantDomain();
        if (domain == null || domain.isBlank()) {
            return null;
        }
        return tenantRepository.findByTenantDomainIgnoreCaseAndDeletedFalse(domain.trim())
            .orElse(null);
    }

    public static class QuotaExceededException extends IllegalArgumentException {
        private final String tenantDomain;
        private final long quotaBytes;
        private final long usedBytes;
        private final long requestedBytes;

        public QuotaExceededException(String tenantDomain, long quotaBytes, long usedBytes, long requestedBytes) {
            super(String.format(
                "Tenant '%s' storage quota exceeded: quota=%d bytes, used=%d bytes, requested=%d bytes, available=%d bytes",
                tenantDomain, quotaBytes, usedBytes, requestedBytes, Math.max(0L, quotaBytes - usedBytes)
            ));
            this.tenantDomain = tenantDomain;
            this.quotaBytes = quotaBytes;
            this.usedBytes = usedBytes;
            this.requestedBytes = requestedBytes;
        }

        public String getTenantDomain() { return tenantDomain; }
        public long getQuotaBytes() { return quotaBytes; }
        public long getUsedBytes() { return usedBytes; }
        public long getRequestedBytes() { return requestedBytes; }
    }
}
