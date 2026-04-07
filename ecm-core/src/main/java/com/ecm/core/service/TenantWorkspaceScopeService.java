package com.ecm.core.service;

import com.ecm.core.config.TenantContext;
import com.ecm.core.entity.Activity;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Site;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenantWorkspaceScopeService {

    private final NodeRepository nodeRepository;
    private final SiteRepository siteRepository;

    public boolean hasScopedTenantWorkspace() {
        return TenantContext.getCurrentTenantRootNodeId() != null;
    }

    public String resolveCurrentTenantRootPath() {
        UUID tenantRootNodeId = TenantContext.getCurrentTenantRootNodeId();
        if (tenantRootNodeId == null) {
            return null;
        }
        return nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(tenantRootNodeId, Node.ArchiveStatus.LIVE)
            .map(Node::getPath)
            .filter(path -> path != null && !path.isBlank())
            .orElse("");
    }

    public boolean isActivityVisible(Activity activity) {
        String tenantRootPath = resolveCurrentTenantRootPath();
        if (tenantRootPath == null) {
            return true;
        }
        return isActivityVisible(activity, tenantRootPath);
    }

    public boolean isActivityVisible(Activity activity, String tenantRootPath) {
        if (tenantRootPath == null) {
            return true;
        }
        if (tenantRootPath.isBlank() || activity == null) {
            return false;
        }
        if (activity.getNodeId() != null && isNodeVisible(activity.getNodeId(), tenantRootPath)) {
            return true;
        }
        if (activity.getSiteId() != null && isSiteVisible(activity.getSiteId(), tenantRootPath)) {
            return true;
        }
        return false;
    }

    public boolean isNodeVisible(UUID nodeId, String tenantRootPath) {
        if (tenantRootPath == null) {
            return true;
        }
        if (tenantRootPath.isBlank() || nodeId == null) {
            return false;
        }
        return nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(nodeId, Node.ArchiveStatus.LIVE)
            .map(Node::getPath)
            .filter(path -> path != null && !path.isBlank())
            .map(path -> isPathVisible(path, tenantRootPath))
            .orElse(false);
    }

    public boolean isSiteVisible(String siteId, String tenantRootPath) {
        if (tenantRootPath == null) {
            return true;
        }
        if (tenantRootPath.isBlank() || siteId == null || siteId.isBlank()) {
            return false;
        }
        return siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse(siteId.trim().toLowerCase(Locale.ROOT))
            .map(Site::getRootFolder)
            .filter(rootFolder -> rootFolder != null && !rootFolder.isDeleted())
            .filter(rootFolder -> rootFolder.getArchiveStatus() == Node.ArchiveStatus.LIVE)
            .map(Node::getPath)
            .filter(path -> path != null && !path.isBlank())
            .map(path -> isPathVisible(path, tenantRootPath))
            .orElse(false);
    }

    private boolean isPathVisible(String candidatePath, String tenantRootPath) {
        return candidatePath.equals(tenantRootPath) || candidatePath.startsWith(tenantRootPath + "/");
    }
}
