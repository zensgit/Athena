package com.ecm.core.controller;

import com.ecm.core.entity.Group;
import com.ecm.core.entity.User;
import com.ecm.core.service.UserGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "Manage users")
public class UserController {

    private final UserGroupService userGroupService;

    @GetMapping
    @Operation(summary = "Search users", description = "Search users by username or email")
    public ResponseEntity<Page<User>> searchUsers(
            @RequestParam(required = false) String query,
            Pageable pageable) {
        return ResponseEntity.ok(userGroupService.searchUsers(query, pageable));
    }

    @GetMapping("/{username}")
    @Operation(summary = "Get user", description = "Get user details")
    public ResponseEntity<User> getUser(@PathVariable String username) {
        return ResponseEntity.ok(userGroupService.getUser(username));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create user", description = "Create a new local user")
    public ResponseEntity<User> createUser(@RequestBody User user) {
        return ResponseEntity.ok(userGroupService.createUser(user));
    }

    @PutMapping("/{username}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update user", description = "Update user details")
    public ResponseEntity<User> updateUser(@PathVariable String username, @RequestBody User user) {
        return ResponseEntity.ok(userGroupService.updateUser(username, user));
    }
}
