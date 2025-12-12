package com.ecm.core.controller;

import com.ecm.core.license.LicenseService;
import com.ecm.core.license.LicenseService.LicenseInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system/license")
@RequiredArgsConstructor
@Tag(name = "System: License", description = "License management endpoints")
public class LicenseController {

    private final LicenseService licenseService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get License Info", description = "Get current license details and limits")
    public ResponseEntity<LicenseInfo> getLicenseInfo() {
        return ResponseEntity.ok(licenseService.getLicenseInfo());
    }
}
