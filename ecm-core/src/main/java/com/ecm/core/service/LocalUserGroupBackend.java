package com.ecm.core.service;

import com.ecm.core.dto.CreateGroupRequest;
import com.ecm.core.dto.CreateUserRequest;
import com.ecm.core.dto.GroupDto;
import com.ecm.core.dto.UpdateUserRequest;
import com.ecm.core.dto.UserDto;
import com.ecm.core.entity.Group;
import com.ecm.core.entity.Role;
import com.ecm.core.entity.User;
import com.ecm.core.repository.GroupRepository;
import com.ecm.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ecm.identity.provider", havingValue = "local", matchIfMissing = true)
public class LocalUserGroupBackend implements UserGroupBackend {

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
    @Transactional
    public UserDto createUser(CreateUserRequest user) {
        if (userRepository.existsByUsername(user.username())) {
            throw new IllegalArgumentException("Username already exists");
        }
        User entity = new User();
        entity.setUsername(user.username());
        entity.setEmail(user.email());
        entity.setPassword(user.password());
        entity.setFirstName(user.firstName());
        entity.setLastName(user.lastName());
        if (user.enabled() != null) {
            entity.setEnabled(user.enabled());
        }
        return toDto(userRepository.save(entity));
    }

    @Override
    @Transactional
    public UserDto updateUser(String username, UpdateUserRequest updates) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        if (updates.email() != null) {
            user.setEmail(updates.email());
        }
        if (updates.firstName() != null) {
            user.setFirstName(updates.firstName());
        }
        if (updates.lastName() != null) {
            user.setLastName(updates.lastName());
        }
        if (updates.enabled() != null) {
            user.setEnabled(updates.enabled());
        }
        return toDto(userRepository.save(user));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<GroupDto> getGroups(Pageable pageable) {
        Page<Group> page = groupRepository.findAll(pageable);
        List<GroupDto> content = page.getContent().stream().map(this::toDto).toList();
        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    @Override
    @Transactional
    public GroupDto createGroup(CreateGroupRequest request) {
        if (groupRepository.findByName(request.name()).isPresent()) {
            throw new IllegalArgumentException("Group already exists: " + request.name());
        }
        Group group = new Group();
        group.setName(request.name());
        group.setDisplayName(request.displayName());
        return toDto(groupRepository.save(group));
    }

    @Override
    @Transactional
    public void deleteGroup(String name) {
        Group group = groupRepository.findByName(name)
            .orElseThrow(() -> new IllegalArgumentException("Group not found: " + name));
        groupRepository.delete(group);
    }

    @Override
    @Transactional
    public void addUserToGroup(String username, String groupName) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        Group group = groupRepository.findByName(groupName)
            .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupName));
        if (!group.getUsers().contains(user)) {
            group.getUsers().add(user);
            groupRepository.save(group);
            log.info("Added user {} to group {}", username, groupName);
        }
    }

    @Override
    @Transactional
    public void removeUserFromGroup(String username, String groupName) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        Group group = groupRepository.findByName(groupName)
            .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupName));
        if (group.getUsers().remove(user)) {
            groupRepository.save(group);
            log.info("Removed user {} from group {}", username, groupName);
        }
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

