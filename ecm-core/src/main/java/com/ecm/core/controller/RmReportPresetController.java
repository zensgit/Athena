package com.ecm.core.controller;

import com.ecm.core.entity.RmReportPreset;
import com.ecm.core.entity.RmReportPreset.Kind;
import com.ecm.core.service.RmReportPresetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * P5 PR-83: RM saved report preset REST API.
 *
 * Admin-only per existing RM surface convention.
 */
@RestController
@RequestMapping("/api/v1/records/report-presets")
@RequiredArgsConstructor
@Tag(name = "Records Management — Report Presets",
     description = "Saved RM report configurations admins can recall or schedule.")
@PreAuthorize("hasRole('ADMIN')")
public class RmReportPresetController {

    private final RmReportPresetService service;

    public record ReportPresetResponse(
            UUID id,
            String owner,
            String name,
            String description,
            Kind kind,
            Map<String, Object> params,
            LocalDateTime createdDate,
            LocalDateTime lastModifiedDate
    ) {
        public static ReportPresetResponse from(RmReportPreset p) {
            return new ReportPresetResponse(
                    p.getId(),
                    p.getOwner(),
                    p.getName(),
                    p.getDescription(),
                    p.getKind(),
                    p.getParams(),
                    p.getCreatedDate(),
                    p.getLastModifiedDate()
            );
        }
    }

    public record CreatePresetRequest(
            @NotBlank String name,
            String description,
            @NotNull Kind kind,
            Map<String, Object> params
    ) {}

    public record UpdatePresetRequest(
            String name,
            String description,
            Map<String, Object> params
    ) {}

    @GetMapping
    @Operation(summary = "List my saved RM report presets")
    public ResponseEntity<List<ReportPresetResponse>> listMine() {
        List<ReportPresetResponse> dto = service.listForCurrentUser().stream()
                .map(ReportPresetResponse::from)
                .toList();
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one of my saved RM report presets by id")
    public ResponseEntity<ReportPresetResponse> getOne(@PathVariable UUID id) {
        return ResponseEntity.ok(ReportPresetResponse.from(service.getOwned(id)));
    }

    @PostMapping
    @Operation(summary = "Save a new RM report preset")
    public ResponseEntity<ReportPresetResponse> create(@RequestBody CreatePresetRequest request) {
        RmReportPreset saved = service.create(
                request.name(),
                request.description(),
                request.kind(),
                request.params()
        );
        return ResponseEntity.ok(ReportPresetResponse.from(saved));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update name/description/params on an owned preset")
    public ResponseEntity<ReportPresetResponse> update(
            @PathVariable UUID id,
            @RequestBody UpdatePresetRequest request) {
        RmReportPreset updated = service.update(id, request.name(), request.description(), request.params());
        return ResponseEntity.ok(ReportPresetResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete an owned preset")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
