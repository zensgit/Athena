package com.ecm.core.controller;

import com.ecm.core.dto.PermissionDto;
import com.ecm.core.dto.PermissionSetDto;
import com.ecm.core.dto.UserDto;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission;
import com.ecm.core.entity.Permission.AuthorityType;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.entity.PermissionSet;
import com.ecm.core.entity.Role;
import com.ecm.core.entity.User;
import com.ecm.core.service.SecurityService;
import com.ecm.core.service.NodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/security")
@RequiredArgsConstructor
@Tag(name = "Security Management", description = "APIs for managing permissions and security")
public class SecurityController {
    
    private final SecurityService securityService;
    private final NodeService nodeService;
    
    @GetMapping("/nodes/{nodeId}/permissions")
    @Operation(summary = "Get node permissions", description = "Get all permissions for a node")
    public ResponseEntity<List<PermissionDto>> getNodePermissions(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId) {
        Node node = nodeService.getNode(nodeId);
        List<Permission> permissions = securityService.getNodePermissions(node);
        return ResponseEntity.ok(permissions.stream().map(PermissionDto::from).toList());
    }

    @GetMapping("/permission-sets")
    @Operation(summary = "Get permission sets", description = "Get predefined permission sets")
    public ResponseEntity<Map<String, Set<PermissionType>>> getPermissionSets() {
        return ResponseEntity.ok(securityService.getPermissionSets());
    }

    @GetMapping("/permission-sets/metadata")
    @Operation(summary = "Get permission set metadata", description = "Get predefined permission sets with labels and descriptions")
    public ResponseEntity<List<PermissionSetDto>> getPermissionSetMetadata() {
        return ResponseEntity.ok(
            java.util.Arrays.stream(PermissionSet.values())
                .sorted(java.util.Comparator.comparingInt(PermissionSet::getOrder))
                .map(PermissionSetDto::from)
                .toList()
        );
    }
    
    @GetMapping("/nodes/{nodeId}/effective-permissions")
    @Operation(summary = "Get effective permissions", description = "Get effective permissions for all authorities")
    public ResponseEntity<Map<String, Set<PermissionType>>> getEffectivePermissions(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId) {
        Node node = nodeService.getNode(nodeId);
        Map<String, Set<PermissionType>> permissions = securityService.getEffectivePermissions(node);
        return ResponseEntity.ok(permissions);
    }
    
    @PostMapping("/nodes/{nodeId}/permissions")
    @Operation(summary = "Set permission", description = "Set a permission on a node")
    public ResponseEntity<Void> setPermission(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId,
            @Parameter(description = "Authority (user/group/role)") @RequestParam String authority,
            @Parameter(description = "Authority type") @RequestParam AuthorityType authorityType,
            @Parameter(description = "Permission type") @RequestParam PermissionType permissionType,
            @Parameter(description = "Allow or deny") @RequestParam boolean allowed) {
        
        Node node = nodeService.getNode(nodeId);
        securityService.setPermission(node, authority, authorityType, permissionType, allowed);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/nodes/{nodeId}/permission-sets")
    @Operation(summary = "Apply permission set", description = "Apply a predefined permission set to a node")
    public ResponseEntity<Void> applyPermissionSet(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId,
            @Parameter(description = "Authority (user/group/role)") @RequestParam String authority,
            @Parameter(description = "Authority type") @RequestParam AuthorityType authorityType,
            @Parameter(description = "Permission set") @RequestParam PermissionSet permissionSet,
            @Parameter(description = "Replace existing permissions") @RequestParam(defaultValue = "false") boolean replace) {

        Node node = nodeService.getNode(nodeId);
        securityService.applyPermissionSet(node, authority, authorityType, permissionSet, replace);
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/nodes/{nodeId}/permissions")
    @Operation(summary = "Remove permission", description = "Remove a permission from a node")
    public ResponseEntity<Void> removePermission(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId,
            @Parameter(description = "Authority") @RequestParam String authority,
            @Parameter(description = "Permission type") @RequestParam PermissionType permissionType) {
        
        Node node = nodeService.getNode(nodeId);
        securityService.removePermission(node, authority, permissionType);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/nodes/{nodeId}/inherit-permissions")
    @Operation(summary = "Set permission inheritance", description = "Enable or disable permission inheritance")
    public ResponseEntity<Void> setInheritPermissions(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId,
            @Parameter(description = "Inherit permissions") @RequestParam boolean inherit) {
        
        Node node = nodeService.getNode(nodeId);
        securityService.setInheritPermissions(node, inherit);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/nodes/{nodeId}/take-ownership")
    @Operation(summary = "Take ownership", description = "Take ownership of a node")
    public ResponseEntity<Void> takeOwnership(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId) {
        
        Node node = nodeService.getNode(nodeId);
        securityService.takeOwnership(node);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/users/current")
    @Operation(summary = "Get current user", description = "Get the current authenticated user")
    public ResponseEntity<UserDto> getCurrentUser() {
        User user = securityService.getCurrentUserEntity();
        return ResponseEntity.ok(toDto(user));
    }
    
    @GetMapping("/users/current/authorities")
    @Operation(summary = "Get user authorities", description = "Get all authorities for the current user")
    public ResponseEntity<Set<String>> getCurrentUserAuthorities() {
        String username = securityService.getCurrentUser();
        Set<String> authorities = securityService.getUserAuthorities(username);
        return ResponseEntity.ok(authorities);
    }
    
    @GetMapping("/nodes/{nodeId}/check-permission")
    @Operation(summary = "Check permission", description = "Check if current user has a specific permission")
    public ResponseEntity<Boolean> checkPermission(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId,
            @Parameter(description = "Permission type") @RequestParam PermissionType permissionType) {
        
        Node node = nodeService.getNode(nodeId);
        boolean hasPermission = securityService.hasPermission(node, permissionType);
        return ResponseEntity.ok(hasPermission);
    }

    @GetMapping("/nodes/{nodeId}/permission-diagnostics")
    @Operation(summary = "Permission diagnostics", description = "Explain why the current user can or cannot perform a permission")
    public ResponseEntity<SecurityService.PermissionDecision> getPermissionDiagnostics(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId,
            @Parameter(description = "Permission type") @RequestParam PermissionType permissionType) {

        Node node = nodeService.getNode(nodeId);
        if (!securityService.hasPermission(node, PermissionType.READ)) {
            throw new SecurityException("No permission to view permissions");
        }
        String username = securityService.getCurrentUser();
        return ResponseEntity.ok(securityService.explainPermission(node, permissionType, username));
    }
    
    @PostMapping("/permissions/cleanup-expired")
    @Operation(summary = "Cleanup expired permissions", description = "Remove all expired permissions")
    public ResponseEntity<Void> cleanupExpiredPermissions() {
        securityService.cleanupExpiredPermissions();
        return ResponseEntity.ok().build();
    }

    private UserDto toDto(User user) {
        if (user == null) {
            return null;
        }
        List<String> roles = user.getRoles() != null
            ? user.getRoles().stream().map(Role::getName).sorted().toList()
            : List.of();
        return new UserDto(
            user.getId() != null ? user.getId().toString() : null,
            user.getUsername(),
            user.getEmail(),
            roles,
            user.getFirstName(),
            user.getLastName(),
            user.isEnabled(),
            user.isLocked()
        );
    }
}
