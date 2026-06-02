package com.ecm.core.service;

import com.ecm.core.entity.Node;
import com.ecm.core.entity.Tenant;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the owning tenant of a target folder by walking the node's parent chain up to a tenant
 * root ({@code Tenant.rootNodeId}). Used by scheduled/background content writers (Q2b) that run off
 * a scheduler thread with no request {@code TenantContext}: they resolve the tenant from their
 * target folder, set {@code TenantContext}, write, then clear.
 *
 * <p>Athena tenancy is path-based, but this uses <b>exact parent-chain root-id matching</b> (not a
 * loose path prefix) so a folder whose name merely resembles a tenant path cannot be mis-attributed.
 *
 * <p>It <b>rejects</b> (never silently allows a no-quota system write) in two distinct cases, so the
 * caller can fail/skip the scheduled write with a clear message:
 * <ul>
 *   <li>{@code NOT_FOUND} — target folder is missing / deleted (e.g. a stale configured folder id)</li>
 *   <li>{@code NOT_UNDER_TENANT} — folder exists but no enabled tenant root is found in its chain</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenantContextResolverService {

    private final NodeRepository nodeRepository;
    private final TenantRepository tenantRepository;

    public ResolvedTenant resolveTenantForTargetFolder(UUID folderId) {
        if (folderId == null) {
            throw new TargetFolderTenantException(TargetFolderTenantException.Reason.NOT_FOUND,
                "Target folder id is null");
        }
        Node current = nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(folderId, Node.ArchiveStatus.LIVE)
            .orElseThrow(() -> new TargetFolderTenantException(TargetFolderTenantException.Reason.NOT_FOUND,
                "Target folder not found or deleted: " + folderId));

        // Walk the parent chain (folder itself first), matching each ancestor id against tenant roots.
        while (current != null) {
            Optional<Tenant> tenant = tenantRepository.findByRootNodeIdAndDeletedFalse(current.getId());
            if (tenant.isPresent() && tenant.get().isEnabled()) {
                Tenant t = tenant.get();
                return new ResolvedTenant(t.getTenantDomain(), t.getRootNodeId());
            }
            Node parentRef = current.getParent();
            UUID parentId = (parentRef != null) ? parentRef.getId() : null;
            current = (parentId != null)
                ? nodeRepository.findByIdAndDeletedFalse(parentId).orElse(null)
                : null;
        }
        throw new TargetFolderTenantException(TargetFolderTenantException.Reason.NOT_UNDER_TENANT,
            "Target folder not under any enabled tenant root: " + folderId);
    }

    /** Resolved tenant identity for a target folder. */
    public record ResolvedTenant(String tenantDomain, UUID rootNodeId) {}

    /** Thrown when a target folder cannot be mapped to an enabled tenant; carries a distinct reason. */
    public static class TargetFolderTenantException extends IllegalStateException {
        public enum Reason { NOT_FOUND, NOT_UNDER_TENANT }

        private final Reason reason;

        public TargetFolderTenantException(Reason reason, String message) {
            super(message);
            this.reason = reason;
        }

        public Reason getReason() {
            return reason;
        }
    }
}
