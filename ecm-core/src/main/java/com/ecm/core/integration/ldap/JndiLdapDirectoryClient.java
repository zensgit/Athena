package com.ecm.core.integration.ldap;

import com.ecm.core.exception.IllegalOperationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ecm.identity.provider", havingValue = "ldap")
public class JndiLdapDirectoryClient implements LdapDirectoryClient {

    private final LdapSyncProperties properties;

    @Override
    public LdapConnectionStatus testConnection() {
        return withContext(context -> {
            verifyBase(context, properties.getUserBaseDn(), "userBaseDn");
            verifyBase(context, properties.getGroupBaseDn(), "groupBaseDn");
            return new LdapConnectionStatus(
                true,
                properties.getUserBaseDn(),
                properties.getGroupBaseDn(),
                "LDAP connection successful"
            );
        }, "Failed to connect to LDAP");
    }

    @Override
    public LdapDirectorySnapshot fetchSnapshot() {
        return withContext(context -> {
            List<LdapDirectoryUser> users = searchUsers(context);
            List<LdapDirectoryGroup> groups = searchGroups(context);
            log.info("Fetched LDAP snapshot with {} users and {} groups", users.size(), groups.size());
            return new LdapDirectorySnapshot(users, groups);
        }, "Failed to fetch LDAP snapshot");
    }

    private List<LdapDirectoryUser> searchUsers(DirContext context) throws NamingException {
        String[] attributes = uniqueAttributes(
            properties.getUserUsernameAttribute(),
            properties.getUserEmailAttribute(),
            properties.getUserFirstNameAttribute(),
            properties.getUserLastNameAttribute(),
            properties.getUserDisplayNameAttribute(),
            properties.getUserDepartmentAttribute(),
            properties.getUserJobTitleAttribute(),
            properties.getUserExternalIdAttribute(),
            properties.getUserEnabledAttribute()
        );

        List<SearchResult> results = search(
            context,
            properties.getUserBaseDn(),
            properties.getUserFilter(),
            attributes
        );

        List<LdapDirectoryUser> users = new ArrayList<>(results.size());
        for (SearchResult result : results) {
            Attributes attrs = result.getAttributes();
            String dn = normalizeDn(resolveDn(result, properties.getUserBaseDn()));
            String externalId = defaultIfBlank(stringAttribute(attrs, properties.getUserExternalIdAttribute()), dn);
            String username = defaultIfBlank(stringAttribute(attrs, properties.getUserUsernameAttribute()), extractRdnValue(dn));
            if (!StringUtils.hasText(username)) {
                log.warn("Skipping LDAP user with missing username attribute at DN {}", dn);
                continue;
            }
            String firstName = stringAttribute(attrs, properties.getUserFirstNameAttribute());
            String lastName = stringAttribute(attrs, properties.getUserLastNameAttribute());
            String displayName = firstNonBlank(
                stringAttribute(attrs, properties.getUserDisplayNameAttribute()),
                joinName(firstName, lastName),
                username
            );
            users.add(new LdapDirectoryUser(
                externalId,
                username,
                stringAttribute(attrs, properties.getUserEmailAttribute()),
                firstName,
                lastName,
                displayName,
                stringAttribute(attrs, properties.getUserDepartmentAttribute()),
                stringAttribute(attrs, properties.getUserJobTitleAttribute()),
                parseEnabled(attrs),
                dn
            ));
        }
        return users;
    }

    private List<LdapDirectoryGroup> searchGroups(DirContext context) throws NamingException {
        String[] attributes = uniqueAttributes(
            properties.getGroupNameAttribute(),
            properties.getGroupDisplayNameAttribute(),
            properties.getGroupDescriptionAttribute(),
            properties.getGroupEmailAttribute(),
            properties.getGroupExternalIdAttribute(),
            properties.getGroupMemberAttribute()
        );

        List<SearchResult> results = search(
            context,
            properties.getGroupBaseDn(),
            properties.getGroupFilter(),
            attributes
        );

        List<LdapDirectoryGroup> groups = new ArrayList<>(results.size());
        for (SearchResult result : results) {
            Attributes attrs = result.getAttributes();
            String dn = normalizeDn(resolveDn(result, properties.getGroupBaseDn()));
            String externalId = defaultIfBlank(stringAttribute(attrs, properties.getGroupExternalIdAttribute()), dn);
            String groupName = defaultIfBlank(stringAttribute(attrs, properties.getGroupNameAttribute()), extractRdnValue(dn));
            if (!StringUtils.hasText(groupName)) {
                log.warn("Skipping LDAP group with missing name attribute at DN {}", dn);
                continue;
            }
            groups.add(new LdapDirectoryGroup(
                externalId,
                groupName,
                firstNonBlank(stringAttribute(attrs, properties.getGroupDisplayNameAttribute()), groupName),
                stringAttribute(attrs, properties.getGroupDescriptionAttribute()),
                stringAttribute(attrs, properties.getGroupEmailAttribute()),
                true,
                dn,
                stringAttributes(attrs, properties.getGroupMemberAttribute())
            ));
        }
        return groups;
    }

    private List<SearchResult> search(
        DirContext context,
        String baseDn,
        String filter,
        String[] attributes
    ) throws NamingException {
        verifyRequired(baseDn, "LDAP base DN is required");
        verifyRequired(filter, "LDAP search filter is required");

        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setReturningAttributes(attributes);

        List<SearchResult> results = new ArrayList<>();
        NamingEnumeration<SearchResult> enumeration = context.search(baseDn, filter, controls);
        try {
            while (enumeration.hasMore()) {
                results.add(enumeration.next());
            }
        } finally {
            enumeration.close();
        }
        return results;
    }

    private void verifyBase(DirContext context, String baseDn, String label) throws NamingException {
        verifyRequired(baseDn, "LDAP " + label + " must be configured");
        context.getAttributes(baseDn, new String[] {"objectClass"});
    }

    private boolean parseEnabled(Attributes attrs) throws NamingException {
        String attributeName = properties.getUserEnabledAttribute();
        if (!StringUtils.hasText(attributeName)) {
            return true;
        }

        String raw = stringAttribute(attrs, attributeName);
        if (!StringUtils.hasText(raw)) {
            return true;
        }

        if ("useraccountcontrol".equalsIgnoreCase(attributeName)) {
            try {
                int value = Integer.parseInt(raw.trim());
                return (value & 0x0002) == 0;
            } catch (NumberFormatException ignored) {
                return true;
            }
        }

        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (Set.of("false", "0", "disabled", "inactive", "no").contains(normalized)) {
            return false;
        }
        if (Set.of("true", "1", "enabled", "active", "yes").contains(normalized)) {
            return true;
        }
        return true;
    }

    private String stringAttribute(Attributes attrs, String attributeName) throws NamingException {
        if (!StringUtils.hasText(attributeName)) {
            return null;
        }
        Attribute attribute = attrs.get(attributeName);
        if (attribute == null || attribute.size() == 0) {
            return null;
        }
        Object value = attribute.get();
        return value != null ? value.toString().trim() : null;
    }

    private Set<String> stringAttributes(Attributes attrs, String attributeName) throws NamingException {
        if (!StringUtils.hasText(attributeName)) {
            return Set.of();
        }
        Attribute attribute = attrs.get(attributeName);
        if (attribute == null || attribute.size() == 0) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        NamingEnumeration<?> enumeration = attribute.getAll();
        try {
            while (enumeration.hasMore()) {
                Object value = enumeration.next();
                if (value != null) {
                    values.add(normalizeDn(value.toString()));
                }
            }
        } finally {
            enumeration.close();
        }
        return values;
    }

    private String resolveDn(SearchResult result, String baseDn) throws NamingException {
        try {
            String namespace = result.getNameInNamespace();
            if (StringUtils.hasText(namespace)) {
                return namespace;
            }
        } catch (UnsupportedOperationException ignored) {
            // Fall back to relative name.
        }
        String name = result.getName();
        if (!StringUtils.hasText(name)) {
            return baseDn;
        }
        return name + "," + baseDn;
    }

    private String extractRdnValue(String dn) {
        if (!StringUtils.hasText(dn)) {
            return null;
        }
        String first = dn.split(",", 2)[0];
        int idx = first.indexOf('=');
        if (idx < 0 || idx == first.length() - 1) {
            return first;
        }
        return first.substring(idx + 1);
    }

    private String normalizeDn(String dn) {
        return StringUtils.hasText(dn) ? dn.trim().toLowerCase(Locale.ROOT) : null;
    }

    private String[] uniqueAttributes(String... candidates) {
        Set<String> attributes = new LinkedHashSet<>();
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                attributes.add(candidate);
            }
        }
        return attributes.toArray(String[]::new);
    }

    private String joinName(String firstName, String lastName) {
        if (!StringUtils.hasText(firstName) && !StringUtils.hasText(lastName)) {
            return null;
        }
        if (!StringUtils.hasText(firstName)) {
            return lastName;
        }
        if (!StringUtils.hasText(lastName)) {
            return firstName;
        }
        return firstName + " " + lastName;
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate.trim();
            }
        }
        return null;
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private void verifyRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalOperationException(message);
        }
    }

    private <T> T withContext(LdapCallback<T> callback, String errorMessage) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, properties.getUrl());
        env.put("com.sun.jndi.ldap.connect.timeout", Integer.toString(properties.getConnectTimeoutMs()));
        env.put("com.sun.jndi.ldap.read.timeout", Integer.toString(properties.getReadTimeoutMs()));

        if (StringUtils.hasText(properties.getBindDn())) {
            env.put(Context.SECURITY_AUTHENTICATION, "simple");
            env.put(Context.SECURITY_PRINCIPAL, properties.getBindDn());
            env.put(Context.SECURITY_CREDENTIALS, defaultIfBlank(properties.getBindPassword(), ""));
        }

        DirContext context = null;
        try {
            context = new InitialDirContext(env);
            return callback.run(context);
        } catch (NamingException ex) {
            throw new IllegalOperationException(errorMessage + ": " + ex.getMessage(), ex);
        } finally {
            if (context != null) {
                try {
                    context.close();
                } catch (NamingException closeEx) {
                    log.debug("Failed to close LDAP context cleanly: {}", closeEx.getMessage());
                }
            }
        }
    }

    @FunctionalInterface
    private interface LdapCallback<T> {
        T run(DirContext context) throws NamingException;
    }
}
