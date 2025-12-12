package com.ecm.core.security;

import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.entity.User;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Same Department Dynamic Authority
 *
 * Users in the same department as the document owner can read documents.
 * This enables implicit collaboration within teams.
 *
 * This authority is optional and can be disabled via configuration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SameDepartmentDynamicAuthority implements DynamicAuthority {

    private final NodeRepository nodeRepository;
    private final UserRepository userRepository;

    @Value("${ecm.security.same-department-access.enabled:false}")
    private boolean enabled;

    @Override
    public boolean hasAuthority(PermissionContext context) {
        if (!enabled) {
            return false;
        }

        Node node = context.getNode();
        if (node == null && context.getNodeId() != null) {
            node = nodeRepository.findById(context.getNodeId()).orElse(null);
        }

        if (node == null || node.getCreatedBy() == null) {
            return false;
        }

        String ownerDepartment = getDepartment(node.getCreatedBy());
        String userDepartment = getDepartment(context.getUsername());

        if (ownerDepartment == null || userDepartment == null) {
            return false;
        }

        boolean sameDepartment = ownerDepartment.equals(userDepartment);

        if (sameDepartment) {
            log.debug("User {} and owner {} are in same department: {}",
                context.getUsername(), node.getCreatedBy(), userDepartment);
        }

        return sameDepartment;
    }

    private String getDepartment(String username) {
        if (username == null) {
            return null;
        }

        return userRepository.findByUsername(username)
            .map(User::getDepartment)
            .orElse(null);
    }

    @Override
    public String getAuthorityName() {
        return "ROLE_SAME_DEPARTMENT";
    }

    @Override
    public Set<PermissionType> getApplicablePermissions() {
        // Same department only grants read access
        return Set.of(PermissionType.READ);
    }

    @Override
    public int getPriority() {
        // Lower priority - check after owner and lock owner
        return 50;
    }
}
