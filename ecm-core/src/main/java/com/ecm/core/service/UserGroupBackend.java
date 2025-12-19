package com.ecm.core.service;

import com.ecm.core.dto.CreateGroupRequest;
import com.ecm.core.dto.CreateUserRequest;
import com.ecm.core.dto.GroupDto;
import com.ecm.core.dto.UpdateUserRequest;
import com.ecm.core.dto.UserDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserGroupBackend {

    Page<UserDto> searchUsers(String query, Pageable pageable);

    UserDto getUser(String username);

    UserDto createUser(CreateUserRequest user);

    UserDto updateUser(String username, UpdateUserRequest updates);

    Page<GroupDto> getGroups(Pageable pageable);

    GroupDto createGroup(CreateGroupRequest request);

    void deleteGroup(String name);

    void addUserToGroup(String username, String groupName);

    void removeUserFromGroup(String username, String groupName);
}

