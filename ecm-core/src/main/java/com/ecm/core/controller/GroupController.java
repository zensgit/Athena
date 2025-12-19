package com.ecm.core.controller;

import com.ecm.core.dto.CreateGroupRequest;
import com.ecm.core.dto.GroupDto;
import com.ecm.core.service.UserGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
@Tag(name = "Group Management", description = "Manage user groups")
public class GroupController {

    private final UserGroupService userGroupService;

    @GetMapping
    @Operation(summary = "List groups", description = "Get all groups")
    public ResponseEntity<Page<GroupDto>> getGroups(Pageable pageable) {
        return ResponseEntity.ok(userGroupService.getGroups(pageable));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create group", description = "Create a new group")
    public ResponseEntity<GroupDto> createGroup(@RequestBody CreateGroupRequest request) {
        return ResponseEntity.ok(userGroupService.createGroup(request));
    }

    @DeleteMapping("/{name}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete group", description = "Delete a group")
    public ResponseEntity<Void> deleteGroup(@PathVariable String name) {
        userGroupService.deleteGroup(name);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupName}/members/{username}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Add member", description = "Add a user to a group")
    public ResponseEntity<Void> addMember(
            @PathVariable String groupName,
            @PathVariable String username) {
        userGroupService.addUserToGroup(username, groupName);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{groupName}/members/{username}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Remove member", description = "Remove a user from a group")
    public ResponseEntity<Void> removeMember(
            @PathVariable String groupName,
            @PathVariable String username) {
        userGroupService.removeUserFromGroup(username, groupName);
        return ResponseEntity.noContent().build();
    }
}
