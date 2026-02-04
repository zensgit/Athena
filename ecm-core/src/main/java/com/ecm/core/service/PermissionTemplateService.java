package com.ecm.core.service;

import com.ecm.core.entity.Node;
import com.ecm.core.entity.PermissionTemplate;
import com.ecm.core.repository.PermissionTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PermissionTemplateService {

    private final PermissionTemplateRepository repository;
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
        return repository.save(template);
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

        return repository.save(existing);
    }

    public void delete(UUID id) {
        repository.deleteById(id);
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
}
