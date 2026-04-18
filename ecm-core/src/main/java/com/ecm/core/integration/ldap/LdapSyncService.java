package com.ecm.core.integration.ldap;

import com.ecm.core.entity.Group;
import com.ecm.core.entity.User;
import com.ecm.core.repository.GroupRepository;
import com.ecm.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ecm.identity.provider", havingValue = "ldap")
public class LdapSyncService {

    static final String DIRECTORY_SOURCE = "ldap";
    private static final String EMAIL_FALLBACK_DOMAIN = "ldap.local";

    private final LdapDirectoryClient directoryClient;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;

    @Transactional(readOnly = true)
    public LdapConnectionStatus testConnection() {
        return directoryClient.testConnection();
    }

    @Transactional
    @CacheEvict(value = "authorities", allEntries = true)
    public LdapSyncResult syncNow() {
        return syncInternal("manual");
    }

    @Transactional
    @CacheEvict(value = "authorities", allEntries = true)
    public LdapSyncResult runScheduledSync() {
        return syncInternal("scheduled");
    }

    private LdapSyncResult syncInternal(String trigger) {
        LocalDateTime syncedAt = LocalDateTime.now();
        List<String> warnings = new ArrayList<>();
        LdapDirectorySnapshot snapshot = directoryClient.fetchSnapshot();

        Map<String, User> managedUsers = mapManagedUsers();
        Map<String, Group> managedGroups = mapManagedGroups();
        Map<String, User> syncedUsers = new LinkedHashMap<>();
        Map<String, Group> syncedGroups = new LinkedHashMap<>();

        int usersCreated = 0;
        int usersUpdated = 0;
        int usersDisabled = 0;
        int usersSkipped = 0;
        int groupsCreated = 0;
        int groupsUpdated = 0;
        int groupsDisabled = 0;
        int groupsSkipped = 0;

        for (LdapDirectoryUser entry : snapshot.users()) {
            if (!StringUtils.hasText(entry.externalId()) || !StringUtils.hasText(entry.username())) {
                usersSkipped++;
                warnings.add("Skipped LDAP user with missing external ID or username");
                continue;
            }

            User user = managedUsers.get(entry.externalId());
            boolean created = false;
            if (user == null) {
                if (userRepository.findByUsername(entry.username()).isPresent()) {
                    usersSkipped++;
                    warnings.add("Skipped LDAP user " + entry.username() + " because the username already exists locally");
                    continue;
                }
                user = new User();
                user.setUsername(entry.username());
                user.setPassword(directoryPlaceholderPassword(entry.externalId()));
                created = true;
            } else if (!entry.username().equals(user.getUsername())) {
                warnings.add("Preserved local username " + user.getUsername() + " for LDAP identity " + entry.externalId());
            }

            String email = resolveUserEmail(user, entry, warnings);
            if (email == null) {
                usersSkipped++;
                warnings.add("Skipped LDAP user " + entry.username() + " because no unique email could be assigned");
                continue;
            }

            user.setEmail(email);
            user.setFirstName(entry.firstName());
            user.setLastName(entry.lastName());
            user.setDisplayName(entry.displayName());
            user.setDepartment(entry.department());
            user.setJobTitle(entry.jobTitle());
            user.setEnabled(entry.enabled());
            user.setDirectoryManaged(true);
            user.setDirectorySource(DIRECTORY_SOURCE);
            user.setDirectoryExternalId(entry.externalId());
            user.setDirectoryDn(normalizeDn(entry.dn()));
            user.setDirectoryLastSyncedAt(syncedAt);
            userRepository.save(user);

            syncedUsers.put(entry.externalId(), user);
            if (created) {
                usersCreated++;
            } else {
                usersUpdated++;
            }
        }

        for (LdapDirectoryGroup entry : snapshot.groups()) {
            if (!StringUtils.hasText(entry.externalId()) || !StringUtils.hasText(entry.name())) {
                groupsSkipped++;
                warnings.add("Skipped LDAP group with missing external ID or name");
                continue;
            }

            Group group = managedGroups.get(entry.externalId());
            boolean created = false;
            if (group == null) {
                if (groupRepository.findByName(entry.name()).isPresent()) {
                    groupsSkipped++;
                    warnings.add("Skipped LDAP group " + entry.name() + " because the name already exists locally");
                    continue;
                }
                group = new Group();
                group.setName(entry.name());
                created = true;
            } else if (!entry.name().equals(group.getName())) {
                warnings.add("Preserved local group name " + group.getName() + " for LDAP identity " + entry.externalId());
            }

            group.setDisplayName(firstNonBlank(entry.displayName(), group.getName()));
            group.setDescription(entry.description());
            group.setEmail(entry.email());
            group.setEnabled(entry.enabled());
            group.setDirectoryManaged(true);
            group.setDirectorySource(DIRECTORY_SOURCE);
            group.setDirectoryExternalId(entry.externalId());
            group.setDirectoryDn(normalizeDn(entry.dn()));
            group.setDirectoryLastSyncedAt(syncedAt);
            groupRepository.save(group);

            syncedGroups.put(entry.externalId(), group);
            if (created) {
                groupsCreated++;
            } else {
                groupsUpdated++;
            }
        }

        Set<User> membershipTouchedUsers = new HashSet<>();
        int membershipsChanged = syncMemberships(snapshot, syncedUsers, syncedGroups, warnings, membershipTouchedUsers);
        if (!membershipTouchedUsers.isEmpty()) {
            userRepository.saveAll(membershipTouchedUsers);
        }

        for (User user : managedUsers.values()) {
            if (!syncedUsers.containsKey(user.getDirectoryExternalId())) {
                usersDisabled += disableMissingUser(user, syncedAt);
            }
        }

        for (Group group : managedGroups.values()) {
            if (!syncedGroups.containsKey(group.getDirectoryExternalId())) {
                groupsDisabled += disableMissingGroup(group, syncedAt);
            }
        }

        int unresolvedMembers = countUnresolvedMembers(snapshot, syncedUsers);

        List<String> sortedWarnings = warnings.stream()
            .filter(StringUtils::hasText)
            .distinct()
            .sorted(Comparator.naturalOrder())
            .toList();

        log.info(
            "LDAP {} sync completed: users +{}/~{}/-{}, groups +{}/~{}/-{}, memberships {}, warnings {}",
            trigger,
            usersCreated,
            usersUpdated,
            usersDisabled,
            groupsCreated,
            groupsUpdated,
            groupsDisabled,
            membershipsChanged,
            sortedWarnings.size()
        );

        return new LdapSyncResult(
            trigger,
            syncedAt,
            usersCreated,
            usersUpdated,
            usersDisabled,
            usersSkipped,
            groupsCreated,
            groupsUpdated,
            groupsDisabled,
            groupsSkipped,
            membershipsChanged,
            unresolvedMembers,
            sortedWarnings
        );
    }

    private Map<String, User> mapManagedUsers() {
        Map<String, User> managed = new HashMap<>();
        for (User user : userRepository.findAllByDirectoryManagedTrueAndDirectorySource(DIRECTORY_SOURCE)) {
            if (StringUtils.hasText(user.getDirectoryExternalId())) {
                managed.put(user.getDirectoryExternalId(), user);
            }
        }
        return managed;
    }

    private Map<String, Group> mapManagedGroups() {
        Map<String, Group> managed = new HashMap<>();
        for (Group group : groupRepository.findAllByDirectoryManagedTrueAndDirectorySource(DIRECTORY_SOURCE)) {
            if (StringUtils.hasText(group.getDirectoryExternalId())) {
                managed.put(group.getDirectoryExternalId(), group);
            }
        }
        return managed;
    }

    private String resolveUserEmail(User user, LdapDirectoryUser entry, List<String> warnings) {
        String candidate = normalizeEmail(entry.email());
        if (!StringUtils.hasText(candidate)) {
            candidate = fallbackEmail(entry.username(), entry.externalId());
        }

        User existingEmailOwner = userRepository.findByEmail(candidate).orElse(null);
        if (existingEmailOwner == null || Objects.equals(existingEmailOwner.getId(), user.getId())) {
            return candidate;
        }

        if (StringUtils.hasText(user.getEmail()) && !candidate.equalsIgnoreCase(user.getEmail())) {
            warnings.add("Preserved local email " + user.getEmail() + " for LDAP user " + user.getUsername() + " because " + candidate + " is already in use");
            return user.getEmail();
        }

        String fallback = fallbackEmail(entry.username(), entry.externalId());
        User fallbackOwner = userRepository.findByEmail(fallback).orElse(null);
        if (fallbackOwner == null || Objects.equals(fallbackOwner.getId(), user.getId())) {
            warnings.add("Assigned fallback email " + fallback + " to LDAP user " + entry.username() + " due to duplicate directory email");
            return fallback;
        }

        return null;
    }

    private int syncMemberships(
        LdapDirectorySnapshot snapshot,
        Map<String, User> syncedUsers,
        Map<String, Group> syncedGroups,
        List<String> warnings,
        Set<User> membershipTouchedUsers
    ) {
        Map<String, User> usersByDn = new HashMap<>();
        for (User user : syncedUsers.values()) {
            if (StringUtils.hasText(user.getDirectoryDn())) {
                usersByDn.put(normalizeDn(user.getDirectoryDn()), user);
            }
        }

        int changes = 0;
        for (LdapDirectoryGroup entry : snapshot.groups()) {
            Group group = syncedGroups.get(entry.externalId());
            if (group == null) {
                continue;
            }

            Set<User> currentMembers = new HashSet<>(group.getUsers());
            Set<User> targetMembers = new HashSet<>();
            for (String memberDn : entry.memberDns()) {
                User user = usersByDn.get(normalizeDn(memberDn));
                if (user == null) {
                    warnings.add("Ignored LDAP group member DN without a mirrored user: " + memberDn);
                    continue;
                }
                targetMembers.add(user);
            }

            for (User existing : currentMembers) {
                if (!targetMembers.contains(existing)) {
                    unlink(group, existing);
                    membershipTouchedUsers.add(existing);
                    changes++;
                }
            }

            for (User target : targetMembers) {
                if (!group.getUsers().contains(target)) {
                    link(group, target);
                    membershipTouchedUsers.add(target);
                    changes++;
                }
            }
        }
        return changes;
    }

    private int disableMissingUser(User user, LocalDateTime syncedAt) {
        boolean changed = false;
        if (user.isEnabled()) {
            user.setEnabled(false);
            changed = true;
        }

        for (Group group : new HashSet<>(user.getGroups())) {
            if (isDirectoryManaged(group)) {
                unlink(group, user);
                changed = true;
            }
        }

        user.setDirectoryLastSyncedAt(syncedAt);
        userRepository.save(user);
        return changed ? 1 : 0;
    }

    private int disableMissingGroup(Group group, LocalDateTime syncedAt) {
        boolean changed = false;
        if (group.isEnabled()) {
            group.setEnabled(false);
            changed = true;
        }

        for (User user : new HashSet<>(group.getUsers())) {
            unlink(group, user);
            userRepository.save(user);
            changed = true;
        }

        group.setDirectoryLastSyncedAt(syncedAt);
        groupRepository.save(group);
        return changed ? 1 : 0;
    }

    private int countUnresolvedMembers(LdapDirectorySnapshot snapshot, Map<String, User> syncedUsers) {
        Set<String> userDns = syncedUsers.values().stream()
            .map(User::getDirectoryDn)
            .filter(StringUtils::hasText)
            .map(this::normalizeDn)
            .collect(java.util.stream.Collectors.toSet());

        int unresolved = 0;
        for (LdapDirectoryGroup group : snapshot.groups()) {
            for (String memberDn : group.memberDns()) {
                if (!userDns.contains(normalizeDn(memberDn))) {
                    unresolved++;
                }
            }
        }
        return unresolved;
    }

    private boolean isDirectoryManaged(Group group) {
        return group != null
            && group.isDirectoryManaged()
            && DIRECTORY_SOURCE.equalsIgnoreCase(group.getDirectorySource());
    }

    private void link(Group group, User user) {
        group.getUsers().add(user);
        user.getGroups().add(group);
    }

    private void unlink(Group group, User user) {
        group.getUsers().removeIf(existing -> sameUser(existing, user));
        user.getGroups().removeIf(existing -> sameGroup(existing, group));
    }

    private boolean sameUser(User left, User right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (left.getId() != null && right.getId() != null) {
            return Objects.equals(left.getId(), right.getId());
        }
        return Objects.equals(left.getUsername(), right.getUsername());
    }

    private boolean sameGroup(Group left, Group right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (left.getId() != null && right.getId() != null) {
            return Objects.equals(left.getId(), right.getId());
        }
        return Objects.equals(left.getName(), right.getName());
    }

    private String fallbackEmail(String username, String externalId) {
        String localPart = username + "+" + externalId.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
        if (localPart.length() > 64) {
            localPart = localPart.substring(0, 64);
        }
        return localPart + "@" + EMAIL_FALLBACK_DOMAIN;
    }

    private String normalizeEmail(String email) {
        return StringUtils.hasText(email) ? email.trim().toLowerCase(Locale.ROOT) : null;
    }

    private String normalizeDn(String dn) {
        return StringUtils.hasText(dn) ? dn.trim().toLowerCase(Locale.ROOT) : null;
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate.trim();
            }
        }
        return null;
    }

    private String directoryPlaceholderPassword(String externalId) {
        return "{ldap-managed}" + UUID.nameUUIDFromBytes(externalId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
