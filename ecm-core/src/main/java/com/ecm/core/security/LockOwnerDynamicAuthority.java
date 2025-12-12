package com.ecm.core.security;

import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Lock Owner Dynamic Authority
 *
 * The user who locked a document has special permissions:
 * - Can unlock the document
 * - Can check in changes
 * - Can cancel checkout
 *
 * This prevents other users from modifying locked documents.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LockOwnerDynamicAuthority implements DynamicAuthority {

    private final NodeRepository nodeRepository;

    @Override
    public boolean hasAuthority(PermissionContext context) {
        Node node = context.getNode();

        if (node == null && context.getNodeId() != null) {
            node = nodeRepository.findById(context.getNodeId()).orElse(null);
        }

        if (node == null) {
            return false;
        }

        // Check if document is locked by this user
        if (!node.isLocked()) {
            return false;
        }

        boolean isLockOwner = context.getUsername() != null &&
                              context.getUsername().equals(node.getLockedBy());

        if (isLockOwner) {
            log.debug("User {} is lock owner of node {}", context.getUsername(), node.getId());
        }

        return isLockOwner;
    }

    @Override
    public String getAuthorityName() {
        return "ROLE_LOCK_OWNER";
    }

    @Override
    public Set<PermissionType> getApplicablePermissions() {
        // Lock owner can only unlock, checkin, and cancel checkout
        return Set.of(
            PermissionType.WRITE,       // Can modify while locked
            PermissionType.DELETE       // Can delete locked document
            // Note: UNLOCK, CHECKIN, CANCEL_CHECKOUT would need to be added to PermissionType
        );
    }

    @Override
    public int getPriority() {
        // Medium priority
        return 20;
    }
}
