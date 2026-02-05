package com.ecm.core.service;

import com.ecm.core.entity.Node;
import com.ecm.core.entity.PermissionTemplate;
import com.ecm.core.entity.PermissionTemplateVersion;
import com.ecm.core.repository.PermissionTemplateRepository;
import com.ecm.core.repository.PermissionTemplateVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
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
