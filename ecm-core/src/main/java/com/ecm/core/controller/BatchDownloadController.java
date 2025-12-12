package com.ecm.core.controller;

import com.ecm.core.service.BatchDownloadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipOutputStream;

@Slf4j
@RestController
@RequestMapping("/api/v1/nodes/download")
@RequiredArgsConstructor
@Tag(name = "Batch Download", description = "Download multiple files as ZIP")
public class BatchDownloadController {

    private final BatchDownloadService batchDownloadService;

    @GetMapping("/batch")
    @Operation(summary = "Batch Download", description = "Download multiple documents/folders as a single ZIP file")
    public ResponseEntity<StreamingResponseBody> batchDownload(
            @Parameter(description = "List of Node IDs") @RequestParam("ids") List<UUID> ids,
            @Parameter(description = "Name for the ZIP file") @RequestParam(required = false, defaultValue = "archive") String name) {

        StreamingResponseBody stream = outputStream -> {
            try (ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {
                batchDownloadService.streamNodesAsZip(ids, zipOut);
            } catch (Exception e) {
                log.error("Error streaming zip", e);
            }
        };

        String filename = name + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".zip";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(stream);
    }
}
