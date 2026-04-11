package com.ecm.core.service;

import com.ecm.core.entity.Tenant;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenantMetricsService {

    private final TenantRepository tenantRepository;
    private final TenantQuotaService tenantQuotaService;
    private final NodeRepository nodeRepository;
    private final DocumentRepository documentRepository;

    public TenantMetrics getMetrics(String tenantDomain) {
        Tenant tenant = tenantRepository.findByTenantDomainIgnoreCaseAndDeletedFalse(tenantDomain.trim())
            .orElseThrow(() -> new NoSuchElementException("Tenant not found: " + tenantDomain));

        long storageUsedBytes = tenantQuotaService.calculateUsedBytes(tenant);
        Long quotaBytes = tenant.getQuotaBytes();

        String rootPath = resolveRootPath(tenant);
        long nodeCount = 0;
        long documentCount = 0;
        long folderCount = 0;

        if (rootPath != null) {
            String pathPattern = rootPath + "/%";
            nodeCount = countNodesUnderPath(pathPattern);
            documentCount = countDocumentsUnderPath(pathPattern);
            folderCount = nodeCount - documentCount;
        }

        return new TenantMetrics(
            tenant.getTenantDomain(),
            tenant.getTenantName(),
            tenant.isEnabled(),
            storageUsedBytes,
            quotaBytes,
            quotaBytes != null ? quotaBytes - storageUsedBytes : null,
            nodeCount,
            documentCount,
            folderCount
        );
    }

    private String resolveRootPath(Tenant tenant) {
        if (tenant.getRootNodeId() == null) {
            return null;
        }
        return nodeRepository.findById(tenant.getRootNodeId())
            .map(node -> node.getPath())
            .orElse(null);
    }

    private long countNodesUnderPath(String pathPattern) {
        return nodeRepository.countByDeletedFalseAndPathLike(pathPattern);
    }

    private long countDocumentsUnderPath(String pathPattern) {
        return documentRepository.countByDeletedFalseAndPathLike(pathPattern);
    }

    public record TenantMetrics(
        String tenantDomain,
        String tenantName,
        boolean enabled,
        long storageUsedBytes,
        Long quotaBytes,
        Long storageAvailableBytes,
        long nodeCount,
        long documentCount,
        long folderCount
    ) {}
}
