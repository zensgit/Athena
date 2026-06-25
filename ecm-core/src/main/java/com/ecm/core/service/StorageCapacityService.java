package com.ecm.core.service;

import com.ecm.core.dto.StorageCapacityStatusDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Service
public class StorageCapacityService {

    static final String BACKEND_TYPE_FILESYSTEM = "filesystem";

    private final Path rootPath;
    private final int warnPercent;
    private final int criticalPercent;
    private final long blockedMinFreeBytes;

    public StorageCapacityService(
        @Value("${ecm.storage.root-path}") String rootPath,
        @Value("${ecm.storage.capacity.warn-percent:80}") int warnPercent,
        @Value("${ecm.storage.capacity.critical-percent:95}") int criticalPercent,
        @Value("${ecm.storage.capacity.blocked-min-free-bytes:104857600}") long blockedMinFreeBytes
    ) {
        this.rootPath = Path.of(rootPath).toAbsolutePath().normalize();
        this.warnPercent = warnPercent;
        this.criticalPercent = criticalPercent;
        this.blockedMinFreeBytes = blockedMinFreeBytes;
    }

    public StorageCapacityStatusDto getStatus() {
        if (!Files.exists(rootPath)) {
            return unknown("content_root_missing");
        }

        try {
            FileStore fileStore = Files.getFileStore(rootPath);
            long totalBytes = safeSpace(fileStore.getTotalSpace());
            long usableBytes = safeSpace(fileStore.getUsableSpace());
            long usedBytes = Math.max(0L, totalBytes - usableBytes);
            double usedPercent = totalBytes > 0
                ? (usedBytes * 100.0d) / totalBytes
                : 0.0d;

            return new StorageCapacityStatusDto(
                BACKEND_TYPE_FILESYSTEM,
                determineStatus(usableBytes, usedPercent),
                totalBytes,
                usableBytes,
                usedBytes,
                usedPercent,
                warnPercent,
                criticalPercent,
                blockedMinFreeBytes,
                rootPath.toString(),
                null
            );
        } catch (IOException | RuntimeException ex) {
            log.debug("Unable to inspect content store capacity for {}", rootPath, ex);
            return unknown(ex.getClass().getSimpleName());
        }
    }

    private StorageCapacityStatusDto unknown(String error) {
        return new StorageCapacityStatusDto(
            BACKEND_TYPE_FILESYSTEM,
            StorageCapacityStatus.UNKNOWN.name(),
            0L,
            0L,
            0L,
            0.0d,
            warnPercent,
            criticalPercent,
            blockedMinFreeBytes,
            rootPath.toString(),
            error
        );
    }

    private String determineStatus(long usableBytes, double usedPercent) {
        if (blockedMinFreeBytes >= 0 && usableBytes <= blockedMinFreeBytes) {
            return StorageCapacityStatus.BLOCKED.name();
        }
        if (usedPercent >= criticalPercent) {
            return StorageCapacityStatus.CRITICAL.name();
        }
        if (usedPercent >= warnPercent) {
            return StorageCapacityStatus.WARN.name();
        }
        return StorageCapacityStatus.OK.name();
    }

    private long safeSpace(long value) {
        return Math.max(0L, value);
    }

    enum StorageCapacityStatus {
        OK,
        WARN,
        CRITICAL,
        BLOCKED,
        UNKNOWN
    }
}
