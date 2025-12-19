package com.ecm.core.service;

import com.ecm.core.dto.CreateGroupRequest;
import com.ecm.core.dto.CreateUserRequest;
import com.ecm.core.dto.GroupDto;
import com.ecm.core.dto.UpdateUserRequest;
import com.ecm.core.dto.UserDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserGroupService {

    private final UserGroupBackend backend;

    public Page<UserDto> searchUsers(String query, Pageable pageable) {
        return backend.searchUsers(query, pageable);
    }

    public UserDto getUser(String username) {
        return backend.getUser(username);
    }

    public UserDto createUser(CreateUserRequest user) {
        return backend.createUser(user);
    }

    public UserDto updateUser(String username, UpdateUserRequest updates) {
        return backend.updateUser(username, updates);
    }

    public Page<GroupDto> getGroups(Pageable pageable) {
        return backend.getGroups(pageable);
    }

    public GroupDto createGroup(CreateGroupRequest request) {
        return backend.createGroup(request);
    }

    public void deleteGroup(String name) {
        backend.deleteGroup(name);
    }

    public void addUserToGroup(String username, String groupName) {
        backend.addUserToGroup(username, groupName);
    }

    public void removeUserFromGroup(String username, String groupName) {
        backend.removeUserFromGroup(username, groupName);
    }
}
