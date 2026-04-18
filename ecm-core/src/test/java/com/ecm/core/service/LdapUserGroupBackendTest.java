package com.ecm.core.service;

import com.ecm.core.dto.CreateUserRequest;
import com.ecm.core.entity.User;
import com.ecm.core.exception.IllegalOperationException;
import com.ecm.core.repository.GroupRepository;
import com.ecm.core.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class LdapUserGroupBackendTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroupRepository groupRepository;

    @InjectMocks
    private LdapUserGroupBackend backend;

    @Test
    @DisplayName("LDAP backend reads mirrored users from local repositories")
    void ldapBackendReadsMirroredUsersFromLocalRepositories() {
        User alice = new User();
        alice.setUsername("alice");
        alice.setEmail("alice@example.com");
        alice.setEnabled(true);

        Mockito.when(userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                "ali",
                "ali",
                PageRequest.of(0, 10)
            ))
            .thenReturn(new PageImpl<>(List.of(alice), PageRequest.of(0, 10), 1));

        var page = backend.searchUsers("ali", PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals("alice", page.getContent().get(0).username());
    }

    @Test
    @DisplayName("LDAP backend rejects direct user creation")
    void ldapBackendRejectsDirectUserCreation() {
        assertThrows(
            IllegalOperationException.class,
            () -> backend.createUser(new CreateUserRequest("alice", "alice@example.com", "pw", "Alice", "Lee", true))
        );
    }
}
