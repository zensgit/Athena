package com.ecm.core.integration.antivirus;

import com.ecm.core.service.AuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * High-level antivirus service for content scanning.
 *
 * Wraps ClamAvClient with Spring configuration and audit logging.
 */
@Slf4j
@Service
public class AntivirusService {

    private final AuditService auditService;

    @Value("${ecm.antivirus.enabled:false}")
    private boolean enabled;

    @Value("${ecm.antivirus.clamd.host:localhost}")
    private String clamdHost;

    @Value("${ecm.antivirus.clamd.port:3310}")
    private int clamdPort;

    @Value("${ecm.antivirus.clamd.timeout:30000}")
    private int clamdTimeout;

    @Value("${ecm.antivirus.on-threat.action:reject}")
    private String onThreatAction;

    private ClamAvClient clamAvClient;

    public AntivirusService(AuditService auditService) {
        this.auditService = auditService;
    }

    @PostConstruct
    public void init() {
        if (enabled) {
            clamAvClient = new ClamAvClient(clamdHost, clamdPort, clamdTimeout);
            log.info("Antivirus service initialized - ClamAV at {}:{}", clamdHost, clamdPort);

            // Check connectivity on startup
            if (clamAvClient.ping()) {
                String version = clamAvClient.getVersion();
                log.info("ClamAV is available - Version: {}", version);
            } else {
                log.warn("ClamAV is not responding at {}:{}. Uploads will fail if scanning is required.",
                        clamdHost, clamdPort);
            }
        } else {
            log.info("Antivirus service is disabled");
        }
    }

    /**
     * Check if antivirus scanning is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Check if ClamAV daemon is available.
     */
    public boolean isAvailable() {
        if (!enabled || clamAvClient == null) {
            return false;
        }
        return clamAvClient.ping();
    }

    /**
     * Get ClamAV version.
     */
    public String getVersion() {
        if (!enabled || clamAvClient == null) {
            return null;
        }
        return clamAvClient.getVersion();
    }

    /**
     * Scan content for viruses.
     *
     * @param inputStream the content to scan
     * @param filename    original filename (for logging)
     * @param nodeId      optional node ID (for audit logging)
     * @return scan result
     * @throws AntivirusException if scanning fails or virus is detected
     */
    public VirusScanResult scan(InputStream inputStream, String filename, UUID nodeId) throws AntivirusException {
        if (!enabled) {
            log.debug("Antivirus scanning is disabled, skipping scan for: {}", filename);
            return VirusScanResult.skipped("Antivirus scanning is disabled");
        }

        if (clamAvClient == null) {
            throw new AntivirusException("Antivirus service not initialized");
        }

        log.debug("Scanning content: {} (nodeId: {})", filename, nodeId);
        long startTime = System.currentTimeMillis();

        try {
            ClamAvClient.ScanResult result = clamAvClient.scan(inputStream);
            long duration = System.currentTimeMillis() - startTime;

            if (result.isClean()) {
                log.info("Virus scan CLEAN for '{}' in {}ms", filename, duration);
                return VirusScanResult.clean(duration);

            } else if (result.isInfected()) {
                log.warn("VIRUS DETECTED in '{}': {} (scan took {}ms)", filename, result.threatName(), duration);

                // Audit log the virus detection
                auditVirusDetection(nodeId, filename, result.threatName());

                // Based on action configuration
                if ("reject".equalsIgnoreCase(onThreatAction)) {
                    throw new VirusDetectedException(filename, result.threatName());
                } else {
                    // quarantine mode - return result but let caller handle
                    return VirusScanResult.infected(result.threatName(), duration);
                }

            } else {
                // Error during scan
                log.error("Virus scan ERROR for '{}': {}", filename, result.errorMessage());
                throw new AntivirusException("Virus scan failed: " + result.errorMessage());
            }

        } catch (IOException e) {
            log.error("Failed to scan content '{}': {}", filename, e.getMessage());
            throw new AntivirusException("Virus scan communication error: " + e.getMessage(), e);
        }
    }

    /**
     * Scan byte array for viruses.
     */
    public VirusScanResult scan(byte[] data, String filename, UUID nodeId) throws AntivirusException {
        try (InputStream bis = new java.io.ByteArrayInputStream(data)) {
            return scan(bis, filename, nodeId);
        } catch (IOException e) {
            throw new AntivirusException("Failed to scan bytes: " + e.getMessage(), e);
        }
    }

    private void auditVirusDetection(UUID nodeId, String filename, String threatName) {
        try {
            String details = String.format(
                    "VIRUS_DETECTED filename=%s threatName=%s scannerAction=%s",
                    filename,
                    threatName,
                    onThreatAction
            );
            auditService.logEvent("SECURITY_EVENT", nodeId, filename, "system", details);
        } catch (Exception e) {
            log.error("Failed to audit virus detection: {}", e.getMessage());
        }
    }

    /**
     * Result of a virus scan operation.
     */
    public record VirusScanResult(
            Status status,
            String threatName,
            String message,
            long scanDurationMs
    ) {
        public enum Status {
            CLEAN,
            INFECTED,
            SKIPPED,
            ERROR
        }

        public static VirusScanResult clean(long durationMs) {
            return new VirusScanResult(Status.CLEAN, null, "No threats detected", durationMs);
        }

        public static VirusScanResult infected(String threatName, long durationMs) {
            return new VirusScanResult(Status.INFECTED, threatName, "Threat detected: " + threatName, durationMs);
        }

        public static VirusScanResult skipped(String reason) {
            return new VirusScanResult(Status.SKIPPED, null, reason, 0);
        }

        public static VirusScanResult error(String message) {
            return new VirusScanResult(Status.ERROR, null, message, 0);
        }

        public boolean isClean() {
            return status == Status.CLEAN;
        }

        public boolean isInfected() {
            return status == Status.INFECTED;
        }

        public boolean wasSkipped() {
            return status == Status.SKIPPED;
        }
    }

    /**
     * Base exception for antivirus operations.
     */
    public static class AntivirusException extends Exception {
        public AntivirusException(String message) {
            super(message);
        }

        public AntivirusException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when a virus is detected.
     */
    public static class VirusDetectedException extends AntivirusException {
        private final String filename;
        private final String threatName;

        public VirusDetectedException(String filename, String threatName) {
            super(String.format("Virus detected in '%s': %s", filename, threatName));
            this.filename = filename;
            this.threatName = threatName;
        }

        public String getFilename() {
            return filename;
        }

        public String getThreatName() {
            return threatName;
        }
    }
}
