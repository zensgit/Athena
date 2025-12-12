package com.ecm.core.security;

import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Owner Dynamic Authority
 *
 * The document owner (creator) has all permissions on their documents.
 * This is a high-priority dynamic authority that grants full access.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OwnerDynamicAuthority implements DynamicAuthority {

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

        String owner = node.getCreatedBy();
        boolean isOwner = context.getUsername() != null &&
                          context.getUsername().equals(owner);

        if (isOwner) {
            log.debug("User {} is owner of node {}", context.getUsername(), node.getId());
        }

        return isOwner;
    }

    @Override
    public String getAuthorityName() {
        return "ROLE_OWNER";
    }

    @Override
    public Set<PermissionType> getApplicablePermissions() {
        // Owner has ALL permissions
        return null;
    }

    @Override
    public int getPriority() {
        // High priority - owner check should happen early
        return 10;
    }
}
