package com.ecm.core.service;

import com.ecm.core.entity.RmReportPreset;
import com.ecm.core.entity.RmReportPreset.Kind;
import com.ecm.core.repository.RmReportPresetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * P5 PR-83: Saved RM report preset service.
 *
 * <p>Backend-only CRUD around {@link RmReportPreset}. Does NOT execute reports
 * — it only persists the user's saved report kind + params so they can be
 * recalled later, and so a future scheduling slice can drive recurring
 * exports from the same preset.
 *
 * <p>Authority model: every preset is scoped to an {@code owner} (the current
 * Athena username). The service enforces that writes always target the caller's
 * own owner; admins bypass via dedicated admin paths only when actually needed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RmReportPresetService {

    private final RmReportPresetRepository repository;
    private final SecurityService securityService;

    /**
     * List all saved presets for the current user, ordered by name.
     */
    @Transactional(readOnly = true)
    public List<RmReportPreset> listForCurrentUser() {
        String owner = securityService.getCurrentUser();
        if (owner == null || owner.isBlank()) {
            return List.of();
        }
        return repository.findByOwnerAndDeletedFalseOrderByName(owner);
    }

    /**
     * Retrieve a preset by id. Rejects if the preset belongs to a different
     * owner (admins included — cross-user reads go through a dedicated path).
     */
    @Transactional(readOnly = true)
    public RmReportPreset getOwned(UUID id) {
        RmReportPreset preset = repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NoSuchElementException("Report preset not found: " + id));
        assertOwner(preset);
        return preset;
    }

    /**
     * Create a new preset for the current user. Duplicate (owner, name) is rejected.
     */
    public RmReportPreset create(String name, String description, Kind kind, Map<String, Object> params) {
        String owner = requireOwner();
        String normalizedName = requireNonBlank(name, "Preset name is required");
        if (repository.existsByOwnerAndNameAndDeletedFalse(owner, normalizedName)) {
            throw new IllegalArgumentException("A report preset named \"" + normalizedName + "\" already exists");
        }
        if (kind == null) {
            throw new IllegalArgumentException("Preset kind is required");
        }
        RmReportPreset preset = RmReportPreset.builder()
                .owner(owner)
                .name(normalizedName)
                .description(description == null ? null : description.trim())
                .kind(kind)
                .params(params != null ? params : Map.of())
                .build();
        return repository.save(preset);
    }

    /**
     * Update name/description/params on an existing owned preset. Kind is
     * deliberately immutable — callers who need a different kind should
     * create a new preset.
     */
    public RmReportPreset update(UUID id, String name, String description, Map<String, Object> params) {
        RmReportPreset preset = getOwned(id);
        if (name != null) {
            String normalizedName = requireNonBlank(name, "Preset name is required");
            if (!normalizedName.equals(preset.getName())
                    && repository.existsByOwnerAndNameAndDeletedFalse(preset.getOwner(), normalizedName)) {
                throw new IllegalArgumentException("A report preset named \"" + normalizedName + "\" already exists");
            }
            preset.setName(normalizedName);
        }
        if (description != null) {
            preset.setDescription(description.trim());
        }
        if (params != null) {
            preset.setParams(params);
        }
        return repository.save(preset);
    }

    /**
     * Soft-delete an owned preset.
     */
    public void delete(UUID id) {
        RmReportPreset preset = getOwned(id);
        preset.setDeleted(true);
        preset.setDeletedAt(LocalDateTime.now());
        preset.setDeletedBy(securityService.getCurrentUser());
        repository.save(preset);
    }

    // ------------------------------------------------------------------

    private String requireOwner() {
        String owner = securityService.getCurrentUser();
        if (owner == null || owner.isBlank()) {
            throw new SecurityException("No authenticated user for report preset operation");
        }
        return owner;
    }

    private void assertOwner(RmReportPreset preset) {
        String owner = requireOwner();
        if (!owner.equals(preset.getOwner())) {
            throw new SecurityException("Report preset belongs to a different owner");
        }
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }
}
