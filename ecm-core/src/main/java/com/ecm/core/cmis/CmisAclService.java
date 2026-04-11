package com.ecm.core.cmis;

import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission;
import com.ecm.core.entity.Permission.AuthorityType;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.SecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CmisAclService {

    private final SecurityService securityService;
    private final NodeService nodeService;

    private static final Map<PermissionType, String> PERMISSION_TO_CMIS;
    private static final Map<String, List<PermissionType>> CMIS_TO_PERMISSIONS;

    static {
        Map<PermissionType, String> forward = new EnumMap<>(PermissionType.class);
        forward.put(PermissionType.READ, "cmis:read");
        forward.put(PermissionType.WRITE, "cmis:write");
        forward.put(PermissionType.CREATE_CHILDREN, "cmis:write");
        forward.put(PermissionType.CHECKOUT, "cmis:write");
        forward.put(PermissionType.CHECKIN, "cmis:write");
        forward.put(PermissionType.CANCEL_CHECKOUT, "cmis:write");
        forward.put(PermissionType.DELETE, "cmis:all");
        forward.put(PermissionType.DELETE_CHILDREN, "cmis:all");
        forward.put(PermissionType.CHANGE_PERMISSIONS, "cmis:all");
        forward.put(PermissionType.TAKE_OWNERSHIP, "cmis:all");
        forward.put(PermissionType.EXECUTE, "cmis:all");
        forward.put(PermissionType.APPROVE, "cmis:all");
        forward.put(PermissionType.REJECT, "cmis:all");
        PERMISSION_TO_CMIS = Collections.unmodifiableMap(forward);

        Map<String, List<PermissionType>> reverse = new LinkedHashMap<>();
        reverse.put("cmis:read", List.of(PermissionType.READ));
        reverse.put("cmis:write", List.of(PermissionType.WRITE, PermissionType.CREATE_CHILDREN));
        reverse.put("cmis:all", List.of(PermissionType.DELETE, PermissionType.CHANGE_PERMISSIONS, PermissionType.EXECUTE));
        CMIS_TO_PERMISSIONS = Collections.unmodifiableMap(reverse);
    }

    public CmisModels.AclResponse getAcl(String objectId) {
        if (objectId == null || objectId.isBlank()) {
            throw new IllegalArgumentException("objectId is required");
        }
        Node node = nodeService.getNode(UUID.fromString(objectId.trim()));
        List<Permission> permissions = securityService.getNodePermissions(node);

        List<CmisModels.AceEntry> aces = buildAceEntries(permissions);
        return new CmisModels.AclResponse(objectId.trim(), aces, true);
    }

    public CmisModels.AclResponse applyAcl(String objectId,
                                            List<CmisModels.AceEntry> addAces,
                                            List<CmisModels.AceEntry> removeAces) {
        if (objectId == null || objectId.isBlank()) {
            throw new IllegalArgumentException("objectId is required");
        }
        Node node = nodeService.getNode(UUID.fromString(objectId.trim()));

        if (removeAces != null) {
            for (CmisModels.AceEntry ace : removeAces) {
                for (String cmisPermission : ace.permissions()) {
                    List<PermissionType> types = CMIS_TO_PERMISSIONS.get(cmisPermission);
                    if (types != null) {
                        for (PermissionType type : types) {
                            securityService.removePermission(node, ace.principalId(), type);
                        }
                    }
                }
            }
        }

        if (addAces != null) {
            for (CmisModels.AceEntry ace : addAces) {
                for (String cmisPermission : ace.permissions()) {
                    List<PermissionType> types = CMIS_TO_PERMISSIONS.get(cmisPermission);
                    if (types != null) {
                        for (PermissionType type : types) {
                            securityService.setPermission(node, ace.principalId(),
                                    AuthorityType.USER, type, true);
                        }
                    }
                }
            }
        }

        // Return the updated ACL
        List<Permission> updatedPermissions = securityService.getNodePermissions(node);
        List<CmisModels.AceEntry> aces = buildAceEntries(updatedPermissions);
        return new CmisModels.AclResponse(objectId.trim(), aces, true);
    }

    private List<CmisModels.AceEntry> buildAceEntries(List<Permission> permissions) {
        // Group by (principal, isDirect) then collect unique CMIS permission strings
        record PrincipalKey(String authority, boolean isDirect) {}

        Map<PrincipalKey, Set<String>> grouped = new LinkedHashMap<>();
        for (Permission permission : permissions) {
            if (!permission.isAllowed() || permission.isExpired()) {
                continue;
            }
            String cmisPermission = PERMISSION_TO_CMIS.get(permission.getPermission());
            if (cmisPermission == null) {
                continue;
            }
            PrincipalKey key = new PrincipalKey(permission.getAuthority(), !permission.isInherited());
            grouped.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(cmisPermission);
        }

        return grouped.entrySet().stream()
                .map(entry -> new CmisModels.AceEntry(
                        entry.getKey().authority(),
                        new ArrayList<>(entry.getValue()),
                        entry.getKey().isDirect()))
                .collect(Collectors.toList());
    }

    static String mapPermissionToCmis(PermissionType type) {
        return PERMISSION_TO_CMIS.get(type);
    }

    static List<PermissionType> mapCmisToPermissions(String cmisPermission) {
        return CMIS_TO_PERMISSIONS.getOrDefault(cmisPermission, List.of());
    }
}
