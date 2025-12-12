package com.ecm.core.service;

import com.ecm.core.entity.Group;
import com.ecm.core.entity.User;
import com.ecm.core.repository.GroupRepository;
import com.ecm.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserGroupService {

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;

    // === User Management ===

    @Transactional(readOnly = true)
    public Page<User> searchUsers(String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            return userRepository.findAll(pageable);
        }
        // Assuming UserRepository has a search method or we use JPA naming
        return userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(query, query, pageable);
    }

    @Transactional(readOnly = true)
    public User getUser(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
    }

    @Transactional
    public User createUser(User user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }
        return userRepository.save(user);
    }

    @Transactional
    public User updateUser(String username, User updates) {
        User user = getUser(username);
        if (updates.getEmail() != null) user.setEmail(updates.getEmail());
        if (updates.getFirstName() != null) user.setFirstName(updates.getFirstName());
        if (updates.getLastName() != null) user.setLastName(updates.getLastName());
        if (updates.isEnabled() != user.isEnabled()) user.setEnabled(updates.isEnabled());
        return userRepository.save(user);
    }

    // === Group Management ===

    @Transactional(readOnly = true)
    public Page<Group> getGroups(Pageable pageable) {
        return groupRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Group getGroup(String name) {
        return groupRepository.findByName(name)
            .orElseThrow(() -> new IllegalArgumentException("Group not found: " + name));
    }

    @Transactional
    public Group createGroup(String name, String displayName) {
        if (groupRepository.findByName(name).isPresent()) {
            throw new IllegalArgumentException("Group already exists: " + name);
        }
        Group group = new Group();
        group.setName(name);
        group.setDisplayName(displayName);
        return groupRepository.save(group);
    }

    @Transactional
    public void deleteGroup(String name) {
        Group group = getGroup(name);
        // Remove all members first logic if needed, usually Cascade takes care or DB constraints
        groupRepository.delete(group);
    }

    @Transactional
    public void addUserToGroup(String username, String groupName) {
        User user = getUser(username);
        Group group = getGroup(groupName);
        
        if (!group.getUsers().contains(user)) {
            group.getUsers().add(user);
            // user.getGroups().add(group); // If bidirectional managed correctly
            groupRepository.save(group);
            log.info("Added user {} to group {}", username, groupName);
        }
    }

    @Transactional
    public void removeUserFromGroup(String username, String groupName) {
        User user = getUser(username);
        Group group = getGroup(groupName);
        
        if (group.getUsers().remove(user)) {
            groupRepository.save(group);
            log.info("Removed user {} from group {}", username, groupName);
        }
    }
}
