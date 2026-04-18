package com.ecm.core.service;

import com.ecm.core.dto.CreateGroupRequest;
import com.ecm.core.dto.CreateUserRequest;
import com.ecm.core.dto.GroupDto;
import com.ecm.core.dto.UpdateUserRequest;
import com.ecm.core.dto.UserDto;
import com.ecm.core.entity.Group;
import com.ecm.core.entity.Role;
import com.ecm.core.entity.User;
import com.ecm.core.exception.IllegalOperationException;
import com.ecm.core.repository.GroupRepository;
import com.ecm.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ecm.identity.provider", havingValue = "ldap")
public class LdapUserGroupBackend implements UserGroupBackend {

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<UserDto> searchUsers(String query, Pageable pageable) {
        Page<User> page;
        if (query == null || query.isBlank()) {
            page = userRepository.findAll(pageable);
        } else {
            page = userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(query, query, pageable);
        }
        List<UserDto> content = page.getContent().stream().map(this::toDto).toList();
        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto getUser(String username) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        return toDto(user);
    }

    @Override
    public UserDto createUser(CreateUserRequest user) {
        throw managedByDirectory("User creation");
    }

    @Override
    public UserDto updateUser(String username, UpdateUserRequest updates) {
        throw managedByDirectory("User updates");
    }

    @Override
    @Transactional(readOnly = true)
    public Page<GroupDto> getGroups(Pageable pageable) {
        Page<Group> page = groupRepository.findAll(pageable);
        List<GroupDto> content = page.getContent().stream().map(this::toDto).toList();
        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    @Override
    public GroupDto createGroup(CreateGroupRequest request) {
        throw managedByDirectory("Group creation");
    }

    @Override
    public void deleteGroup(String name) {
        throw managedByDirectory("Group deletion");
    }

    @Override
    public void addUserToGroup(String username, String groupName) {
        throw managedByDirectory("Group membership changes");
    }

    @Override
    public void removeUserFromGroup(String username, String groupName) {
        throw managedByDirectory("Group membership changes");
    }

    private IllegalOperationException managedByDirectory(String action) {
        return new IllegalOperationException(action + " is managed by LDAP sync when ecm.identity.provider=ldap");
    }

    private UserDto toDto(User user) {
        List<String> roles = new ArrayList<>();
        if (user.getRoles() != null) {
            roles = user.getRoles().stream()
                .map(Role::getName)
                .sorted(Comparator.naturalOrder())
                .toList();
        }
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

    private GroupDto toDto(Group group) {
        return new GroupDto(
            group.getId() != null ? group.getId().toString() : null,
            group.getName(),
            group.getDisplayName(),
            group.getDescription(),
            group.getEmail(),
            group.isEnabled(),
            group.getGroupType() != null ? group.getGroupType().name() : null,
            null
        );
    }
}
