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
 * <p><b>Returns a {@link TenantResolution} result — it never throws a business outcome.</b> This is
 * deliberate and load-bearing: this bean is {@code @Transactional(readOnly = true)}, so throwing a
 * {@code RuntimeException} across its proxy boundary marks the <i>caller's</i> surrounding
 * transaction rollback-only. A scheduled writer that catches the exception to persist a clean FAILED
 * row would then have that write silently rolled back and hit an {@code UnexpectedRollbackException}
 * on commit. Returning a result instead lets the caller branch without poisoning its transaction.
 *
 * <p>Three outcomes, so the caller can decide per writer:
 * <ul>
 *   <li>{@link Status#RESOLVED} — folder is under an enabled tenant root; scope the write to it.</li>
 *   <li>{@link Status#NO_TENANT_SYSTEM} — no enabled tenant exists at all (legacy single-tenant /
 *       no-tenant deployment); the write proceeds untenanted, exactly as before tenancy existed.</li>
 *   <li>{@link Status#UNRESOLVED} — tenants <i>do</i> exist but the folder is under none of them (a
 *       null/missing folder, or a folder outside every tenant root). This is a configuration error:
 *       the caller rejects rather than performing an unattributable write.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenantContextResolverService {

    private final NodeRepository nodeRepository;
    private final TenantRepository tenantRepository;

    public TenantResolution resolveTenantForTargetFolder(UUID folderId) {
        if (folderId != null) {
            Optional<Node> folderOpt = nodeRepository
                .findByIdAndDeletedFalseAndArchiveStatus(folderId, Node.ArchiveStatus.LIVE);
            if (folderOpt.isPresent()) {
                // Walk the parent chain (folder itself first), matching each ancestor id against tenant roots.
                Node current = folderOpt.get();
                while (current != null) {
                    Optional<Tenant> tenant = tenantRepository.findByRootNodeIdAndDeletedFalse(current.getId());
                    if (tenant.isPresent() && tenant.get().isEnabled()) {
                        Tenant t = tenant.get();
                        return TenantResolution.resolved(t.getTenantDomain(), t.getRootNodeId());
                    }
                    Node parentRef = current.getParent();
                    UUID parentId = (parentRef != null) ? parentRef.getId() : null;
                    current = (parentId != null)
                        ? nodeRepository.findByIdAndDeletedFalse(parentId).orElse(null)
                        : null;
                }
            }
        }
        // Unresolved: folder is null / missing / not under any enabled tenant root. Distinguish a
        // misconfigured multi-tenant target (UNRESOLVED -> caller rejects) from a no-tenant
        // deployment (NO_TENANT_SYSTEM -> caller writes untenanted). Never throw — see class javadoc.
        if (tenantRepository.existsByDeletedFalseAndEnabledTrue()) {
            return TenantResolution.unresolved();
        }
        return TenantResolution.noTenantSystem();
    }

    /**
     * Outcome of resolving a target folder's tenant. {@code tenantDomain}/{@code rootNodeId} are
     * populated only for {@link Status#RESOLVED}.
     */
    public record TenantResolution(Status status, String tenantDomain, UUID rootNodeId) {

        public enum Status { RESOLVED, NO_TENANT_SYSTEM, UNRESOLVED }

        public static TenantResolution resolved(String tenantDomain, UUID rootNodeId) {
            return new TenantResolution(Status.RESOLVED, tenantDomain, rootNodeId);
        }

        public static TenantResolution noTenantSystem() {
            return new TenantResolution(Status.NO_TENANT_SYSTEM, null, null);
        }

        public static TenantResolution unresolved() {
            return new TenantResolution(Status.UNRESOLVED, null, null);
        }

        /** Folder is under an enabled tenant root — scope the write to {@link #tenantDomain()}/{@link #rootNodeId()}. */
        public boolean isResolved() {
            return status == Status.RESOLVED;
        }

        /** Tenants exist but the folder is under none — a configuration error the caller must reject. */
        public boolean isReject() {
            return status == Status.UNRESOLVED;
        }

        /** No enabled tenant exists at all — the caller writes untenanted (legacy behaviour). */
        public boolean isNoTenantSystem() {
            return status == Status.NO_TENANT_SYSTEM;
        }
    }
}
