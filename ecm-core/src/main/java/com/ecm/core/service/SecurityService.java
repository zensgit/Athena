package com.ecm.core.service;

import com.ecm.core.entity.*;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.entity.PermissionSet;
import com.ecm.core.entity.Permission.AuthorityType;
import com.ecm.core.entity.Role.Privilege;
import com.ecm.core.entity.Group.GroupType;
import com.ecm.core.repository.*;
import com.ecm.core.security.DynamicAuthority;
import com.ecm.core.security.PermissionContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.core.GrantedAuthority;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SecurityService {

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final NodeRepository nodeRepository;
    private final List<DynamicAuthority> dynamicAuthorities;

    @PostConstruct
    public void init() {
        // Sort dynamic authorities by priority (lower value = higher priority)
        dynamicAuthorities.sort(Comparator.comparingInt(DynamicAuthority::getPriority));
        log.info("SecurityService initialized with {} dynamic authorities: {}",
            dynamicAuthorities.size(),
            dynamicAuthorities.stream().map(DynamicAuthority::getAuthorityName).toList());
    }
    
    public String getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            Object principal = auth.getPrincipal();
            if (principal instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
                String preferredUsername = jwt.getClaimAsString("preferred_username");
                if (preferredUsername != null && !preferredUsername.isBlank()) {
                    return preferredUsername;
                }
                return jwt.getSubject();
            }
            if (principal instanceof UserDetails userDetails) {
                return userDetails.getUsername();
            }
            return principal.toString();
        }
        return "anonymous";
    }
    
    public User getCurrentUserEntity() {
        String username = getCurrentUser();
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementException("User not found: " + username));
    }
    
    public boolean hasPermission(Node node, PermissionType permissionType) {
        return hasPermission(node, permissionType, getCurrentUser());
    }

    public void checkPermission(Node node, PermissionType permissionType) {
        if (!hasPermission(node, permissionType)) {
            throw new SecurityException("No permission: " + permissionType + " on node: " + node.getName());
        }
    }

    public boolean isAdmin(String username) {
        return hasRole("ROLE_ADMIN", username);
    }
    
    @Cacheable(value = "permissions", key = "#node.id + '_' + #permissionType + '_' + #username")
    public boolean hasPermission(Node node, PermissionType permissionType, String username) {
        // Admin has all permissions
        if (hasAuthority("ROLE_ADMIN") || hasRole("ROLE_ADMIN", username)) {
            return true;
        }

        // Folder/document owner fallback: if the current user created the node, allow all actions
        if (node != null && username != null && username.equals(node.getCreatedBy())) {
            return true;
        }

        // Build permission context for dynamic authority evaluation
        PermissionContext context = PermissionContext.builder()
            .nodeId(node != null ? node.getId() : null)
            .node(node)
            .username(username)
            .requestedPermission(permissionType)
            .build();

        // Check dynamic authorities first (priority order)
        for (DynamicAuthority dynamicAuthority : dynamicAuthorities) {
            Boolean grant = dynamicAuthority.grantPermission(context);
            if (grant != null) {
                log.debug("Dynamic authority {} {} permission {} for user {} on node {}",
                    dynamicAuthority.getAuthorityName(),
                    grant ? "granted" : "denied",
                    permissionType, username,
                    node != null ? node.getId() : "null");
                return grant;
            }
        }

        // Fall back to ACL-based permission check
        Set<String> authorities = getUserAuthorities(username);
        return checkNodePermissions(node, permissionType, authorities);
    }

    public PermissionDecision explainPermission(Node node, PermissionType permissionType, String username) {
        if (node == null) {
            return new PermissionDecision(null, username, permissionType, false, "NODE_MISSING", null, List.of(), List.of());
        }

        if (hasAuthority("ROLE_ADMIN") || hasRole("ROLE_ADMIN", username)) {
            return new PermissionDecision(node.getId(), username, permissionType, true, "ADMIN", null, List.of(), List.of());
        }

        if (username != null && username.equals(node.getCreatedBy())) {
            return new PermissionDecision(node.getId(), username, permissionType, true, "OWNER", null, List.of(), List.of());
        }

        PermissionContext context = PermissionContext.builder()
            .nodeId(node.getId())
            .node(node)
            .username(username)
            .requestedPermission(permissionType)
            .build();

        for (DynamicAuthority dynamicAuthority : dynamicAuthorities) {
            Boolean grant = dynamicAuthority.grantPermission(context);
            if (grant != null) {
                return new PermissionDecision(
                    node.getId(),
                    username,
                    permissionType,
                    grant,
                    "DYNAMIC_AUTHORITY",
                    dynamicAuthority.getAuthorityName(),
                    List.of(),
                    List.of()
                );
            }
        }

        Set<String> authorities = getUserAuthorities(username);
        PermissionAuthorityMatches matches = resolvePermissionAuthorityMatches(node, permissionType, authorities);
        if (!matches.deniedAuthorities().isEmpty()) {
            return new PermissionDecision(
                node.getId(),
                username,
                permissionType,
                false,
                "ACL_DENY",
                null,
                matches.allowedAuthorities(),
                matches.deniedAuthorities()
            );
        }
        if (!matches.allowedAuthorities().isEmpty()) {
            return new PermissionDecision(
                node.getId(),
                username,
                permissionType,
                true,
                "ACL_ALLOW",
                null,
                matches.allowedAuthorities(),
                matches.deniedAuthorities()
            );
        }

        return new PermissionDecision(
            node.getId(),
            username,
            permissionType,
            false,
            "NO_MATCH",
            null,
            List.of(),
            List.of()
        );
    }

    public Map<String, Set<PermissionType>> getPermissionSets() {
        Map<String, Set<PermissionType>> sets = new LinkedHashMap<>();
        for (PermissionSet set : PermissionSet.values()) {
            sets.put(set.name(), EnumSet.copyOf(set.getPermissions()));
        }
        return sets;
    }

    public Optional<PermissionSet> resolvePermissionSet(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        for (PermissionSet set : PermissionSet.values()) {
            if (set.name().equalsIgnoreCase(name)) {
                return Optional.of(set);
            }
        }
        return Optional.empty();
    }
    
    public boolean hasRole(String roleName) {
        return hasRole(roleName, getCurrentUser());
    }
    
    public boolean hasRole(String roleName, String username) {
        if (hasAuthority(roleName)) {
            return true;
        }

        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return false;
        }
        
        // Check direct roles
        boolean hasDirectRole = user.getRoles().stream()
            .anyMatch(role -> role.getName().equals(roleName));
        
        if (hasDirectRole) {
            return true;
        }
        
        // Check group roles
        return user.getGroups().stream()
            .flatMap(group -> group.getRoles().stream())
            .anyMatch(role -> role.getName().equals(roleName));
    }
    
    public boolean hasPrivilege(Privilege privilege) {
        return hasPrivilege(privilege, getCurrentUser());
    }
    
    public boolean hasPrivilege(Privilege privilege, String username) {
        if (hasAuthority("ROLE_ADMIN")) {
            return true;
        }

        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return false;
        }
        
        // Check user roles
        Set<Role> allRoles = new HashSet<>(user.getRoles());
        
        // Add group roles
        user.getGroups().forEach(group -> allRoles.addAll(group.getRoles()));
        
        return allRoles.stream()
            .anyMatch(role -> role.getPrivileges().contains(privilege));
    }

    private boolean hasAuthority(String roleName) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (roleName.equalsIgnoreCase(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
    
    @Transactional
    @CacheEvict(value = "permissions", allEntries = true)
    public void setPermission(Node node, String authority, AuthorityType authorityType,
                              PermissionType permissionType, boolean allowed) {
        // Check if user has permission to change permissions
        if (!hasPermission(node, PermissionType.CHANGE_PERMISSIONS)) {
            throw new SecurityException("No permission to change permissions on node: " + node.getName());
        }
        
        // Find existing permission
        Optional<Permission> existing = permissionRepository.findByNodeIdAndAuthority(node.getId(), authority)
            .stream()
            .filter(p -> p.getPermission() == permissionType)
            .findFirst();
        
        if (existing.isPresent()) {
            // Update existing
            Permission permission = existing.get();
            permission.setAllowed(allowed);
            permissionRepository.save(permission);
        } else {
            // Create new
            Permission permission = new Permission();
            permission.setNode(node);
            permission.setAuthority(authority);
            permission.setAuthorityType(authorityType);
            permission.setPermission(permissionType);
            permission.setAllowed(allowed);
            permissionRepository.save(permission);
        }
    }

    @Transactional
    @CacheEvict(value = "permissions", allEntries = true)
    public void applyPermissionSet(Node node,
                                   String authority,
                                   AuthorityType authorityType,
                                   PermissionSet permissionSet,
                                   boolean replace) {
        if (permissionSet == null) {
            return;
        }

        if (!hasPermission(node, PermissionType.CHANGE_PERMISSIONS)) {
            throw new SecurityException("No permission to change permissions on node: " + node.getName());
        }

        List<Permission> existing = permissionRepository.findByNodeIdAndAuthority(node.getId(), authority);
        if (replace) {
            for (Permission permission : existing) {
                if (!permissionSet.getPermissions().contains(permission.getPermission())) {
                    permissionRepository.delete(permission);
                }
            }
        }

        for (PermissionType permissionType : permissionSet.getPermissions()) {
            setPermission(node, authority, authorityType, permissionType, true);
        }
    }
    
    @Transactional
    @CacheEvict(value = "permissions", key = "#node.id + '*'")
    public void removePermission(Node node, String authority, PermissionType permissionType) {
        // Check if user has permission to change permissions
        if (!hasPermission(node, PermissionType.CHANGE_PERMISSIONS)) {
            throw new SecurityException("No permission to change permissions on node: " + node.getName());
        }
        
        permissionRepository.findByNodeIdAndAuthority(node.getId(), authority).stream()
            .filter(p -> p.getPermission() == permissionType)
            .forEach(permissionRepository::delete);
    }
    
    @Transactional
    @CacheEvict(value = "permissions", allEntries = true)
    public void setInheritPermissions(Node node, boolean inherit) {
        // Check if user has permission to change permissions
        if (!hasPermission(node, PermissionType.CHANGE_PERMISSIONS)) {
            throw new SecurityException("No permission to change permission inheritance");
        }
        
        node.setInheritPermissions(inherit);
        nodeRepository.save(node);
        
        if (!inherit) {
            // Copy parent permissions when breaking inheritance
            if (node.getParent() != null) {
                copyPermissions(node.getParent(), node);
            }
        }
    }
    
    public List<Permission> getNodePermissions(Node node) {
        // Check if user has permission to view permissions
        if (!hasPermission(node, PermissionType.READ)) {
            throw new SecurityException("No permission to view permissions");
        }
        
        List<Permission> permissions = new ArrayList<>();
        
        // Get direct permissions
        permissions.addAll(permissionRepository.findByNodeId(node.getId()));
        
        // Get inherited permissions if applicable
        if (node.isInheritPermissions() && node.getParent() != null) {
            permissions.addAll(getInheritedPermissions(node.getParent()));
        }
        
        return permissions;
    }
    
    public Map<String, Set<PermissionType>> getEffectivePermissions(Node node) {
        List<Permission> allPermissions = getNodePermissions(node);
        return resolveEffectivePermissions(allPermissions);
    }

    public Set<String> resolveReadAuthorities(Node node) {
        if (node == null) {
            return Collections.emptySet();
        }

        List<Permission> allPermissions = collectPermissionsForNode(node);
        Map<String, Set<PermissionType>> effectivePermissions = resolveEffectivePermissions(allPermissions);
        Set<String> readableAuthorities = effectivePermissions.entrySet().stream()
            .filter(entry -> entry.getValue().contains(PermissionType.READ))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());

        if (node.getCreatedBy() != null && !node.getCreatedBy().isBlank()) {
            readableAuthorities.add(node.getCreatedBy());
        }

        return readableAuthorities;
    }

    public Set<String> resolveReadAuthorities(UUID nodeId) {
        if (nodeId == null) {
            return Collections.emptySet();
        }
        return resolveReadAuthorities(nodeRepository.findById(nodeId).orElse(null));
    }

    private List<Permission> collectPermissionsForNode(Node node) {
        if (node == null) {
            return List.of();
        }

        List<Permission> permissions = new ArrayList<>(permissionRepository.findByNodeId(node.getId()));
        if (node.isInheritPermissions() && node.getParent() != null) {
            permissions.addAll(getInheritedPermissions(node.getParent()));
        }
        return permissions;
    }

    private Map<String, Set<PermissionType>> resolveEffectivePermissions(List<Permission> allPermissions) {
        Map<String, Set<PermissionType>> effectivePermissions = new HashMap<>();
        Map<String, Set<PermissionType>> deniedPermissions = new HashMap<>();

        for (Permission permission : allPermissions) {
            if (permission.isExpired()) {
                continue;
            }
            if (permission.isAllowed()) {
                effectivePermissions
                    .computeIfAbsent(permission.getAuthority(), k -> new HashSet<>())
                    .add(permission.getPermission());
            } else {
                deniedPermissions
                    .computeIfAbsent(permission.getAuthority(), k -> new HashSet<>())
                    .add(permission.getPermission());
            }
        }

        // Apply deny precedence
        for (Map.Entry<String, Set<PermissionType>> entry : deniedPermissions.entrySet()) {
            Set<PermissionType> allowed = effectivePermissions.get(entry.getKey());
            if (allowed != null) {
                allowed.removeAll(entry.getValue());
            }
        }

        return effectivePermissions;
    }
    
    public List<Node> filterNodesByPermission(List<Node> nodes, PermissionType permissionType) {
        return nodes.stream()
            .filter(node -> hasPermission(node, permissionType))
            .collect(Collectors.toList());
    }
    
    @Transactional
    public void takeOwnership(Node node) {
        // Check if user has permission to take ownership
        if (!hasPermission(node, PermissionType.TAKE_OWNERSHIP)) {
            throw new SecurityException("No permission to take ownership");
        }
        
        String currentUser = getCurrentUser();
        
        // Remove all existing owner permissions
        permissionRepository.findByNodeIdAndPermissionType(node.getId(), PermissionType.TAKE_OWNERSHIP)
            .forEach(permissionRepository::delete);
        
        // Grant all permissions to new owner
        for (PermissionType permissionType : PermissionType.values()) {
            setPermission(node, currentUser, AuthorityType.USER, permissionType, true);
        }
    }
    
    @Cacheable(value = "authorities", key = "#username")
    public Set<String> getUserAuthorities(String username) {
        Set<String> authorities = new HashSet<>();
        
        // Add username
        authorities.add(username);
        
        // Add EVERYONE
        authorities.add("EVERYONE");

        // Include JWT authorities (realm/client roles) for the currently authenticated user.
        // This is important when the identity provider is Keycloak and users are not mirrored
        // into the local user table, but ACLs still reference ROLE_* authorities.
        if (username != null && username.equals(getCurrentUser())) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getAuthorities() != null) {
                auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(Objects::nonNull)
                    .forEach(authorities::add);
            }
        }
        
        User user = userRepository.findByUsername(username).orElse(null);
        if (user != null) {
            // Add groups
            authorities.addAll(user.getGroups().stream()
                .map(Group::getName)
                .collect(Collectors.toSet()));
            
            // Add roles
            authorities.addAll(user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet()));
            
            // Add group roles
            user.getGroups().stream()
                .flatMap(group -> group.getRoles().stream())
                .map(Role::getName)
                .forEach(authorities::add);
        }
        
        return authorities;
    }
    
    @Transactional
    public void cleanupExpiredPermissions() {
        List<Permission> expiredPermissions = permissionRepository.findExpiredPermissions();
        log.info("Cleaning up {} expired permissions", expiredPermissions.size());
        permissionRepository.deleteAll(expiredPermissions);
    }
    
    private boolean checkNodePermissions(Node node, PermissionType permissionType, 
                                         Set<String> authorities) {
        Node current = node;
        boolean allowed = false;
        boolean denied = false;
        while (current != null) {
            // Check direct permissions on current node
            List<Permission> permissions = permissionRepository.findByNodeId(current.getId());
            
            for (Permission permission : permissions) {
                if (permission.getPermission() == permissionType && 
                    authorities.contains(permission.getAuthority()) &&
                    !permission.isExpired()) {
                    
                    if (permission.isAllowed()) {
                        allowed = true;
                    } else {
                        // Explicit deny (deny takes precedence across inheritance chain)
                        denied = true;
                    }
                }
            }
            
            // Move to parent if inheriting permissions
            if (current.isInheritPermissions() && current.getParent() != null) {
                current = current.getParent();
            } else {
                break;
            }
        }
        if (denied) {
            return false;
        }
        return allowed;
    }

    private PermissionAuthorityMatches resolvePermissionAuthorityMatches(
        Node node,
        PermissionType permissionType,
        Set<String> authorities
    ) {
        if (node == null) {
            return new PermissionAuthorityMatches(List.of(), List.of());
        }
        Set<String> allowed = new LinkedHashSet<>();
        Set<String> denied = new LinkedHashSet<>();
        Node current = node;
        while (current != null) {
            List<Permission> permissions = permissionRepository.findByNodeId(current.getId());
            for (Permission permission : permissions) {
                if (permission.getPermission() == permissionType
                    && authorities.contains(permission.getAuthority())
                    && !permission.isExpired()) {
                    if (permission.isAllowed()) {
                        allowed.add(permission.getAuthority());
                    } else {
                        denied.add(permission.getAuthority());
                    }
                }
            }
            if (current.isInheritPermissions() && current.getParent() != null) {
                current = current.getParent();
            } else {
                break;
            }
        }
        return new PermissionAuthorityMatches(new ArrayList<>(allowed), new ArrayList<>(denied));
    }

    public record PermissionDecision(
        UUID nodeId,
        String username,
        PermissionType permission,
        boolean allowed,
        String reason,
        String dynamicAuthority,
        List<String> allowedAuthorities,
        List<String> deniedAuthorities
    ) {
    }

    private record PermissionAuthorityMatches(
        List<String> allowedAuthorities,
        List<String> deniedAuthorities
    ) {
    }
    
    private List<Permission> getInheritedPermissions(Node node) {
        List<Permission> inherited = new ArrayList<>();
        Node current = node;
        
        while (current != null && current.isInheritPermissions()) {
            List<Permission> nodePermissions = permissionRepository.findByNodeId(current.getId());
            
            // Mark as inherited
            nodePermissions.forEach(p -> p.setInherited(true));
            inherited.addAll(nodePermissions);
            
            current = current.getParent();
        }
        
        return inherited;
    }
    
    private void copyPermissions(Node source, Node target) {
        List<Permission> sourcePermissions = permissionRepository.findByNodeId(source.getId());
        
        for (Permission sourcePerm : sourcePermissions) {
            Permission copy = new Permission();
            copy.setNode(target);
            copy.setAuthority(sourcePerm.getAuthority());
            copy.setAuthorityType(sourcePerm.getAuthorityType());
            copy.setPermission(sourcePerm.getPermission());
            copy.setAllowed(sourcePerm.isAllowed());
            copy.setExpiryDate(sourcePerm.getExpiryDate());
            copy.setNotes(sourcePerm.getNotes());
            permissionRepository.save(copy);
        }
    }
}
