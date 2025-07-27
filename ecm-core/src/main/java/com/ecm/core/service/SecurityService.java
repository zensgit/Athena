package com.ecm.core.service;

import com.ecm.core.entity.*;
import com.ecm.core.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    
    public String getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            Object principal = auth.getPrincipal();
            if (principal instanceof UserDetails) {
                return ((UserDetails) principal).getUsername();
            } else {
                return principal.toString();
            }
        }
        return "anonymous";
    }
    
    public User getCurrentUserEntity() {
        String username = getCurrentUser();
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementException("User not found: " + username));
    }
    
    @Cacheable(value = "permissions", key = "#node.id + '_' + #permissionType")
    public boolean hasPermission(Node node, PermissionType permissionType) {
        return hasPermission(node, permissionType, getCurrentUser());
    }
    
    public boolean hasPermission(Node node, PermissionType permissionType, String username) {
        // Admin has all permissions
        if (hasRole("ROLE_ADMIN", username)) {
            return true;
        }
        
        // Get user authorities
        Set<String> authorities = getUserAuthorities(username);
        
        // Check permissions on node and ancestors
        return checkNodePermissions(node, permissionType, authorities);
    }
    
    public boolean hasRole(String roleName) {
        return hasRole(roleName, getCurrentUser());
    }
    
    public boolean hasRole(String roleName, String username) {
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
    
    @Transactional
    @CacheEvict(value = "permissions", key = "#node.id + '*'")
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
        
        Map<String, Set<PermissionType>> effectivePermissions = new HashMap<>();
        
        for (Permission permission : allPermissions) {
            if (permission.isAllowed() && !permission.isExpired()) {
                effectivePermissions
                    .computeIfAbsent(permission.getAuthority(), k -> new HashSet<>())
                    .add(permission.getPermission());
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
        
        while (current != null) {
            // Check direct permissions on current node
            List<Permission> permissions = permissionRepository.findByNodeId(current.getId());
            
            for (Permission permission : permissions) {
                if (permission.getPermission() == permissionType && 
                    authorities.contains(permission.getAuthority()) &&
                    !permission.isExpired()) {
                    
                    if (permission.isAllowed()) {
                        return true;
                    } else {
                        // Explicit deny
                        return false;
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
        
        return false;
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