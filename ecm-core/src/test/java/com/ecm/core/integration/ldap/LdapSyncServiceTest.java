package com.ecm.core.integration.ldap;

import com.ecm.core.entity.Group;
import com.ecm.core.entity.User;
import com.ecm.core.repository.GroupRepository;
import com.ecm.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LdapSyncServiceTest {

    @Mock
    private LdapDirectoryClient directoryClient;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroupRepository groupRepository;

    @InjectMocks
    private LdapSyncService ldapSyncService;

    @BeforeEach
    void setUp() {
        Mockito.lenient().when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        Mockito.lenient().when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        Mockito.lenient().when(groupRepository.findByName(anyString())).thenReturn(Optional.empty());
        Mockito.lenient().when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.lenient().when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.lenient().when(userRepository.saveAll(org.mockito.ArgumentMatchers.<Iterable<User>>any()))
            .thenAnswer(invocation -> {
                Iterable<User> users = invocation.getArgument(0);
                return users instanceof List<?> list ? list : java.util.stream.StreamSupport.stream(users.spliterator(), false).toList();
            });
    }

    @Test
    @DisplayName("syncNow creates mirrored users groups and memberships")
    void syncNowCreatesMirroredUsersGroupsAndMemberships() {
        when(directoryClient.fetchSnapshot()).thenReturn(new LdapDirectorySnapshot(
            List.of(
                new LdapDirectoryUser(
                    "user-1",
                    "alice",
                    "alice@example.com",
                    "Alice",
                    "Lee",
                    "Alice Lee",
                    "Engineering",
                    "Developer",
                    true,
                    "uid=alice,ou=people,dc=example,dc=com"
                )
            ),
            List.of(
                new LdapDirectoryGroup(
                    "group-1",
                    "editors",
                    "Editors",
                    "Editing team",
                    "editors@example.com",
                    true,
                    "cn=editors,ou=groups,dc=example,dc=com",
                    Set.of("uid=alice,ou=people,dc=example,dc=com")
                )
            )
        ));
        when(userRepository.findAllByDirectoryManagedTrueAndDirectorySource("ldap")).thenReturn(List.of());
        when(groupRepository.findAllByDirectoryManagedTrueAndDirectorySource("ldap")).thenReturn(List.of());

        LdapSyncResult result = ldapSyncService.syncNow();

        assertEquals(1, result.usersCreated());
        assertEquals(1, result.groupsCreated());
        assertEquals(1, result.membershipsChanged());
        assertEquals(0, result.unresolvedMembers());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User alice = userCaptor.getValue();
        assertTrue(alice.isDirectoryManaged());
        assertEquals("ldap", alice.getDirectorySource());
        assertEquals(1, alice.getGroups().size());
        assertEquals("editors", alice.getGroups().iterator().next().getName());
    }

    @Test
    @DisplayName("syncNow disables missing managed users and groups and clears memberships")
    void syncNowDisablesMissingManagedUsersAndGroups() {
        User alice = new User();
        alice.setUsername("alice");
        alice.setEmail("alice@example.com");
        alice.setDirectoryManaged(true);
        alice.setDirectorySource("ldap");
        alice.setDirectoryExternalId("user-1");
        alice.setEnabled(true);

        Group editors = new Group();
        editors.setName("editors");
        editors.setDirectoryManaged(true);
        editors.setDirectorySource("ldap");
        editors.setDirectoryExternalId("group-1");
        editors.setEnabled(true);
        editors.addUser(alice);

        when(directoryClient.fetchSnapshot()).thenReturn(new LdapDirectorySnapshot(List.of(), List.of()));
        when(userRepository.findAllByDirectoryManagedTrueAndDirectorySource("ldap")).thenReturn(List.of(alice));
        when(groupRepository.findAllByDirectoryManagedTrueAndDirectorySource("ldap")).thenReturn(List.of(editors));

        LdapSyncResult result = ldapSyncService.syncNow();

        assertEquals(1, result.usersDisabled());
        assertEquals(1, result.groupsDisabled());
        assertFalse(alice.isEnabled());
        assertFalse(editors.isEnabled());
        assertTrue(alice.getGroups().isEmpty());
        assertTrue(editors.getUsers().isEmpty());
    }

    @Test
    @DisplayName("syncNow preserves local authority keys when directory usernames change")
    void syncNowPreservesLocalAuthorityKeysWhenDirectoryNamesChange() {
        User existing = new User();
        existing.setUsername("alice");
        existing.setEmail("alice@example.com");
        existing.setDirectoryManaged(true);
        existing.setDirectorySource("ldap");
        existing.setDirectoryExternalId("user-1");
        existing.setEnabled(true);

        when(directoryClient.fetchSnapshot()).thenReturn(new LdapDirectorySnapshot(
            List.of(
                new LdapDirectoryUser(
                    "user-1",
                    "alice.renamed",
                    "alice.renamed@example.com",
                    "Alice",
                    "Lee",
                    "Alice Lee",
                    null,
                    null,
                    true,
                    "uid=alice.renamed,ou=people,dc=example,dc=com"
                )
            ),
            List.of()
        ));
        when(userRepository.findAllByDirectoryManagedTrueAndDirectorySource("ldap")).thenReturn(List.of(existing));
        when(groupRepository.findAllByDirectoryManagedTrueAndDirectorySource("ldap")).thenReturn(List.of());

        LdapSyncResult result = ldapSyncService.syncNow();

        assertEquals("alice", existing.getUsername());
        assertEquals(1, result.usersUpdated());
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("Preserved local username alice")));
    }
}
