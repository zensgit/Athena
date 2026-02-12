package com.ecm.core.service;

import com.ecm.core.dto.PermissionTemplateVersionDiffDto;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.PermissionTemplate;
import com.ecm.core.entity.PermissionTemplateVersion;
import com.ecm.core.repository.PermissionTemplateRepository;
import com.ecm.core.repository.PermissionTemplateVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PermissionTemplateService {

    private final PermissionTemplateRepository repository;
    private final PermissionTemplateVersionRepository versionRepository;
    private final SecurityService securityService;
    private final NodeService nodeService;

    @Transactional(readOnly = true)
    public List<PermissionTemplate> list() {
        return repository.findAll();
    }

    public PermissionTemplate create(PermissionTemplate template) {
        if (template.getName() == null || template.getName().isBlank()) {
            throw new IllegalArgumentException("Template name is required");
        }
        if (repository.existsByNameIgnoreCase(template.getName())) {
            throw new IllegalArgumentException("Template already exists: " + template.getName());
        }
        if (template.getEntries() == null) {
            template.setEntries(List.of());
        }
        PermissionTemplate saved = repository.save(template);
        createVersionSnapshot(saved);
        return saved;
    }

    public PermissionTemplate update(UUID id, PermissionTemplate updates) {
        PermissionTemplate existing = repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Template not found: " + id));

        if (updates.getName() != null && !updates.getName().isBlank()) {
            if (!updates.getName().equalsIgnoreCase(existing.getName())
                && repository.existsByNameIgnoreCase(updates.getName())) {
                throw new IllegalArgumentException("Template already exists: " + updates.getName());
            }
            existing.setName(updates.getName());
        }
        if (updates.getDescription() != null) {
            existing.setDescription(updates.getDescription());
        }
        if (updates.getEntries() != null) {
            existing.setEntries(updates.getEntries());
        }

        PermissionTemplate saved = repository.save(existing);
        createVersionSnapshot(saved);
        return saved;
    }

    public void delete(UUID id) {
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<PermissionTemplateVersion> listVersions(UUID templateId) {
        ensureTemplateExists(templateId);
        return versionRepository.findByTemplateIdOrderByVersionNumberDesc(templateId);
    }

    @Transactional(readOnly = true)
    public PermissionTemplateVersion getVersion(UUID templateId, UUID versionId) {
        ensureTemplateExists(templateId);
        return versionRepository.findById(versionId)
            .filter(version -> templateId.equals(version.getTemplateId()))
            .orElseThrow(() -> new NoSuchElementException("Template version not found: " + versionId));
    }

    @Transactional(readOnly = true)
    public PermissionTemplateVersionDiffDto computeVersionDiff(UUID templateId, UUID fromVersionId, UUID toVersionId) {
        PermissionTemplate template = repository.findById(templateId)
            .orElseThrow(() -> new NoSuchElementException("Template not found: " + templateId));

        PermissionTemplateVersion fromVersion = getVersion(templateId, fromVersionId);
        PermissionTemplateVersion toVersion = getVersion(templateId, toVersionId);

        Map<String, PermissionTemplate.PermissionTemplateEntry> fromByIdentity = indexEntries(fromVersion.getEntries());
        Map<String, PermissionTemplate.PermissionTemplateEntry> toByIdentity = indexEntries(toVersion.getEntries());

        Set<String> identities = new HashSet<>();
        identities.addAll(fromByIdentity.keySet());
        identities.addAll(toByIdentity.keySet());

        List<PermissionTemplateVersionDiffDto.EntryDto> added = new ArrayList<>();
        List<PermissionTemplateVersionDiffDto.EntryDto> removed = new ArrayList<>();
        List<PermissionTemplateVersionDiffDto.ChangeDto> changed = new ArrayList<>();

        for (String identity : identities) {
            PermissionTemplate.PermissionTemplateEntry fromEntry = fromByIdentity.get(identity);
            PermissionTemplate.PermissionTemplateEntry toEntry = toByIdentity.get(identity);

            if (toEntry != null && fromEntry == null) {
                added.add(toEntryDto(toEntry));
                continue;
            }
            if (toEntry == null && fromEntry != null) {
                removed.add(toEntryDto(fromEntry));
                continue;
            }
            if (toEntry != null && fromEntry != null && toEntry.getPermissionSet() != fromEntry.getPermissionSet()) {
                changed.add(PermissionTemplateVersionDiffDto.ChangeDto.builder()
                    .before(toEntryDto(fromEntry))
                    .after(toEntryDto(toEntry))
                    .build());
            }
        }

        Comparator<PermissionTemplateVersionDiffDto.EntryDto> byIdentity = Comparator
            .comparing((PermissionTemplateVersionDiffDto.EntryDto entry) -> entry.getAuthorityType().name())
            .thenComparing(PermissionTemplateVersionDiffDto.EntryDto::getAuthority, String.CASE_INSENSITIVE_ORDER);

        added.sort(byIdentity);
        removed.sort(byIdentity);
        changed.sort(Comparator.comparing(change -> buildIdentityKey(change.getAfter()), String.CASE_INSENSITIVE_ORDER));

        return PermissionTemplateVersionDiffDto.builder()
            .templateId(template.getId())
            .templateName(template.getName())
            .fromVersionId(fromVersion.getId())
            .fromVersionNumber(fromVersion.getVersionNumber())
            .toVersionId(toVersion.getId())
            .toVersionNumber(toVersion.getVersionNumber())
            .added(added)
            .removed(removed)
            .changed(changed)
            .build();
    }

    public String formatVersionDiffCsv(PermissionTemplateVersionDiffDto diff) {
        if (diff == null) {
            return "status,authority,authorityType,previousPermissionSet,currentPermissionSet\n";
        }

        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("status", "authority", "authorityType", "previousPermissionSet", "currentPermissionSet"));

        if (diff.getAdded() != null) {
            diff.getAdded().forEach(entry -> rows.add(List.of(
                "ADDED",
                entry.getAuthority(),
                entry.getAuthorityType().name(),
                "",
                entry.getPermissionSet().name()
            )));
        }

        if (diff.getRemoved() != null) {
            diff.getRemoved().forEach(entry -> rows.add(List.of(
                "REMOVED",
                entry.getAuthority(),
                entry.getAuthorityType().name(),
                entry.getPermissionSet().name(),
                ""
            )));
        }

        if (diff.getChanged() != null) {
            diff.getChanged().forEach(change -> rows.add(List.of(
                "CHANGED",
                change.getAfter().getAuthority(),
                change.getAfter().getAuthorityType().name(),
                change.getBefore().getPermissionSet().name(),
                change.getAfter().getPermissionSet().name()
            )));
        }

        return rows.stream()
            .map(row -> row.stream().map(PermissionTemplateService::escapeCsv).collect(java.util.stream.Collectors.joining(",")))
            .collect(java.util.stream.Collectors.joining("\n")) + "\n";
    }

    public PermissionTemplate rollback(UUID templateId, UUID versionId) {
        PermissionTemplate template = repository.findById(templateId)
            .orElseThrow(() -> new NoSuchElementException("Template not found: " + templateId));
        PermissionTemplateVersion version = getVersion(templateId, versionId);

        if (!template.getName().equalsIgnoreCase(version.getName())
            && repository.existsByNameIgnoreCase(version.getName())) {
            throw new IllegalArgumentException("Template already exists: " + version.getName());
        }

        template.setName(version.getName());
        template.setDescription(version.getDescription());
        template.setEntries(copyEntries(version.getEntries()));
        PermissionTemplate saved = repository.save(template);
        createVersionSnapshot(saved);
        return saved;
    }

    public void apply(UUID templateId, UUID nodeId, boolean replace) {
        PermissionTemplate template = repository.findById(templateId)
            .orElseThrow(() -> new NoSuchElementException("Template not found: " + templateId));
        Node node = nodeService.getNode(nodeId);

        if (template.getEntries() == null || template.getEntries().isEmpty()) {
            return;
        }

        template.getEntries().forEach(entry -> {
            if (entry == null || entry.getAuthority() == null || entry.getAuthority().isBlank()) {
                return;
            }
            if (entry.getPermissionSet() == null || entry.getAuthorityType() == null) {
                return;
            }
            securityService.applyPermissionSet(
                node,
                entry.getAuthority(),
                entry.getAuthorityType(),
                entry.getPermissionSet(),
                replace
            );
        });
    }

    private void ensureTemplateExists(UUID templateId) {
        if (!repository.existsById(templateId)) {
            throw new NoSuchElementException("Template not found: " + templateId);
        }
    }

    private Map<String, PermissionTemplate.PermissionTemplateEntry> indexEntries(
        List<PermissionTemplate.PermissionTemplateEntry> entries) {
        Map<String, PermissionTemplate.PermissionTemplateEntry> indexed = new LinkedHashMap<>();
        if (entries == null || entries.isEmpty()) {
            return indexed;
        }
        for (PermissionTemplate.PermissionTemplateEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            if (entry.getAuthorityType() == null || entry.getAuthority() == null || entry.getAuthority().isBlank()) {
                continue;
            }
            if (entry.getPermissionSet() == null) {
                continue;
            }
            indexed.put(buildIdentityKey(entry), entry);
        }
        return indexed;
    }

    private static String buildIdentityKey(PermissionTemplate.PermissionTemplateEntry entry) {
        return entry.getAuthorityType().name() + ":" + entry.getAuthority();
    }

    private static String buildIdentityKey(PermissionTemplateVersionDiffDto.EntryDto entry) {
        return entry.getAuthorityType().name() + ":" + entry.getAuthority();
    }

    private static PermissionTemplateVersionDiffDto.EntryDto toEntryDto(PermissionTemplate.PermissionTemplateEntry entry) {
        return PermissionTemplateVersionDiffDto.EntryDto.builder()
            .authority(entry.getAuthority())
            .authorityType(entry.getAuthorityType())
            .permissionSet(entry.getPermissionSet())
            .build();
    }

    private static String escapeCsv(String value) {
        String safe = value == null ? "" : value;
        if (safe.contains("\"")) {
            safe = safe.replace("\"", "\"\"");
        }
        if (safe.contains(",") || safe.contains("\n") || safe.contains("\r") || safe.contains("\"")) {
            return "\"" + safe + "\"";
        }
        return safe;
    }

    private void createVersionSnapshot(PermissionTemplate template) {
        int nextVersion = versionRepository.findTopByTemplateIdOrderByVersionNumberDesc(template.getId())
            .map(PermissionTemplateVersion::getVersionNumber)
            .orElse(0) + 1;
        PermissionTemplateVersion version = new PermissionTemplateVersion();
        version.setTemplateId(template.getId());
        version.setVersionNumber(nextVersion);
        version.setName(template.getName());
        version.setDescription(template.getDescription());
        version.setEntries(copyEntries(template.getEntries()));
        versionRepository.save(version);
    }

    private List<PermissionTemplate.PermissionTemplateEntry> copyEntries(
        List<PermissionTemplate.PermissionTemplateEntry> entries) {
        List<PermissionTemplate.PermissionTemplateEntry> snapshot = new ArrayList<>();
        if (entries == null || entries.isEmpty()) {
            return snapshot;
        }
        for (PermissionTemplate.PermissionTemplateEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            PermissionTemplate.PermissionTemplateEntry copy = new PermissionTemplate.PermissionTemplateEntry();
            copy.setAuthority(entry.getAuthority());
            copy.setAuthorityType(entry.getAuthorityType());
            copy.setPermissionSet(entry.getPermissionSet());
            snapshot.add(copy);
        }
        return snapshot;
    }
}
