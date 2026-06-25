package com.ecm.core.controller;

import com.ecm.core.dto.StorageCapacityStatusDto;
import com.ecm.core.service.StorageCapacityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Storage Admin", description = "Storage capacity and health diagnostics")
public class StorageAdminController {

    private final StorageCapacityService storageCapacityService;

    @GetMapping({"/api/admin/storage/capacity", "/api/v1/admin/storage/capacity"})
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get content-store filesystem capacity")
    public ResponseEntity<StorageCapacityStatusDto> getStorageCapacity() {
        return ResponseEntity.ok(storageCapacityService.getStatus());
    }
}
