package com.ecm.core.controller;

import com.ecm.core.sanity.SanityCheckReport;
import com.ecm.core.sanity.SanityCheckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/system/sanity")
@RequiredArgsConstructor
@Tag(name = "System: Sanity Check", description = "System health and consistency checks")
@PreAuthorize("hasRole('ADMIN')")
public class SanityCheckController {

    private final SanityCheckService sanityCheckService;

    @PostMapping("/run")
    @Operation(summary = "Run Sanity Checks", description = "Trigger a full system consistency check")
    public ResponseEntity<List<SanityCheckReport>> runChecks(
            @RequestParam(defaultValue = "false") boolean fix) {
        return ResponseEntity.ok(sanityCheckService.runAllChecks(fix));
    }
}
