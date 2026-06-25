package com.ecm.core.service;

import com.ecm.core.dto.StorageCapacityStatusDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StorageCapacityServiceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("reports filesystem capacity for an existing content root")
    void reportsFilesystemCapacity() {
        StorageCapacityService service = new StorageCapacityService(
            tempDir.toString(),
            101,
            101,
            -1
        );

        StorageCapacityStatusDto status = service.getStatus();

        assertThat(status.backendType()).isEqualTo("filesystem");
        assertThat(status.status()).isEqualTo("OK");
        assertThat(status.totalBytes()).isGreaterThan(0L);
        assertThat(status.usableBytes()).isGreaterThan(0L);
        assertThat(status.usedBytes()).isGreaterThanOrEqualTo(0L);
        assertThat(status.usedPercent()).isGreaterThanOrEqualTo(0.0d);
        assertThat(status.rootPath()).isEqualTo(tempDir.toAbsolutePath().normalize().toString());
        assertThat(status.error()).isNull();
    }

    @Test
    @DisplayName("returns UNKNOWN without creating a missing content root")
    void missingRootIsUnknownAndHasNoWriteSideEffect() {
        Path missingRoot = tempDir.resolve("missing-content-root");
        StorageCapacityService service = new StorageCapacityService(
            missingRoot.toString(),
            80,
            95,
            104857600
        );

        StorageCapacityStatusDto status = service.getStatus();

        assertThat(status.status()).isEqualTo("UNKNOWN");
        assertThat(status.error()).isEqualTo("content_root_missing");
        assertThat(status.totalBytes()).isZero();
        assertThat(status.usableBytes()).isZero();
        assertThat(missingRoot).doesNotExist();
    }

    @Test
    @DisplayName("applies WARN threshold without changing quota enforcement")
    void appliesWarnThreshold() {
        StorageCapacityService service = new StorageCapacityService(
            tempDir.toString(),
            0,
            101,
            -1
        );

        assertThat(service.getStatus().status()).isEqualTo("WARN");
    }

    @Test
    @DisplayName("applies CRITICAL threshold ahead of WARN")
    void appliesCriticalThreshold() {
        StorageCapacityService service = new StorageCapacityService(
            tempDir.toString(),
            0,
            0,
            -1
        );

        assertThat(service.getStatus().status()).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("applies BLOCKED threshold as observability status")
    void appliesBlockedThreshold() {
        StorageCapacityService service = new StorageCapacityService(
            tempDir.toString(),
            101,
            101,
            Long.MAX_VALUE
        );

        assertThat(service.getStatus().status()).isEqualTo("BLOCKED");
    }
}
