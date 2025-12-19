package com.ecm.core.controller;

import com.ecm.core.dto.SystemStatusDto;
import com.ecm.core.integration.antivirus.AntivirusService;
import com.ecm.core.integration.wopi.model.WopiHealthResponse;
import com.ecm.core.integration.wopi.service.WopiEditorService;
import com.ecm.core.ml.MLServiceClient;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.search.FullTextSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
@Tag(name = "System", description = "System status and diagnostics")
public class SystemStatusController {

    private final DocumentRepository documentRepository;
    private final ConnectionFactory rabbitConnectionFactory;
    private final FullTextSearchService fullTextSearchService;
    private final MLServiceClient mlServiceClient;
    private final WopiEditorService wopiEditorService;
    private final AntivirusService antivirusService;
    private final RestTemplate restTemplate;

    @Value("${ecm.keycloak.url:http://keycloak:8080}")
    private String keycloakUrl;

    @Value("${ecm.keycloak.realm:ecm}")
    private String keycloakRealm;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @GetMapping("/status")
    @Operation(summary = "System status", description = "Aggregate health and integration status for local diagnostics")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SystemStatusDto> status() {
        Map<String, Object> database = checkDatabase();
        Map<String, Object> redis = checkRedis();
        Map<String, Object> rabbitmq = checkRabbitMq();
        Map<String, Object> search = fullTextSearchService.getIndexStats();
        Map<String, Object> ml = checkMl();
        Map<String, Object> keycloak = checkKeycloak();
        WopiHealthResponse wopi = wopiEditorService.getHealth();
        Map<String, Object> antivirus = checkAntivirus();

        SystemStatusDto response = new SystemStatusDto(
            Instant.now().toString(),
            database,
            redis,
            rabbitmq,
            search,
            ml,
            keycloak,
            wopi,
            antivirus
        );
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> checkDatabase() {
        Map<String, Object> status = new HashMap<>();
        try {
            long documentCount = documentRepository.count();
            status.put("reachable", true);
            status.put("documentCount", documentCount);
        } catch (Exception e) {
            status.put("reachable", false);
            status.put("error", e.getMessage());
        }
        return status;
    }

    private Map<String, Object> checkRedis() {
        Map<String, Object> status = new HashMap<>();
        status.put("host", redisHost);
        status.put("port", redisPort);

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(redisHost, redisPort), 1500);
            socket.setSoTimeout(2000);

            try (BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
                 BufferedInputStream in = new BufferedInputStream(socket.getInputStream())) {

                if (redisPassword != null && !redisPassword.isBlank()) {
                    writeRespCommand(out, "AUTH", redisPassword);
                    String authResp = readRespLine(in);
                    if (authResp == null || authResp.startsWith("-")) {
                        status.put("reachable", false);
                        status.put("error", authResp != null ? authResp : "No AUTH response from Redis");
                        return status;
                    }
                    status.put("authenticated", true);
                }

                writeRespCommand(out, "PING");
                String pingResp = readRespLine(in);
                if (pingResp != null && pingResp.startsWith("+")) {
                    status.put("reachable", true);
                    status.put("ping", pingResp.substring(1));
                } else {
                    status.put("reachable", false);
                    status.put("error", pingResp != null ? pingResp : "No PING response from Redis");
                }
            }
        } catch (Exception e) {
            status.put("reachable", false);
            status.put("error", e.getMessage());
        }
        return status;
    }

    private Map<String, Object> checkRabbitMq() {
        Map<String, Object> status = new HashMap<>();
        Connection connection = null;
        try {
            connection = rabbitConnectionFactory.createConnection();
            status.put("reachable", connection != null && connection.isOpen());
        } catch (Exception e) {
            status.put("reachable", false);
            status.put("error", e.getMessage());
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
        return status;
    }

    private Map<String, Object> checkMl() {
        Map<String, Object> status = new HashMap<>();
        try {
            boolean available = mlServiceClient.isAvailable();
            MLServiceClient.ModelInfo modelInfo = mlServiceClient.getModelInfo();
            status.put("available", available);
            status.put("modelLoaded", modelInfo.isModelLoaded());
            status.put("modelVersion", modelInfo.getModelVersion() != null ? modelInfo.getModelVersion() : "N/A");
            status.put("status", available ? "healthy" : "unavailable");
        } catch (Exception e) {
            status.put("available", false);
            status.put("status", "unavailable");
            status.put("error", e.getMessage());
        }
        return status;
    }

    private Map<String, Object> checkKeycloak() {
        Map<String, Object> status = new HashMap<>();
        String wellKnownUrl = keycloakUrl.replaceAll("/+$", "")
            + "/realms/" + keycloakRealm
            + "/.well-known/openid-configuration";

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> config = restTemplate.getForObject(wellKnownUrl, Map.class);
            status.put("reachable", true);
            status.put("url", wellKnownUrl);
            if (config != null) {
                status.put("issuer", config.get("issuer"));
            }
        } catch (Exception e) {
            status.put("reachable", false);
            status.put("url", wellKnownUrl);
            status.put("error", e.getMessage());
        }
        return status;
    }

    private static void writeRespCommand(BufferedOutputStream out, String... parts) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append('*').append(parts.length).append("\r\n");
        for (String part : parts) {
            byte[] bytes = (part != null ? part : "").getBytes(StandardCharsets.UTF_8);
            sb.append('$').append(bytes.length).append("\r\n");
            sb.append(part != null ? part : "").append("\r\n");
        }
        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static String readRespLine(BufferedInputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        int prev = -1;
        int curr;
        while ((curr = in.read()) != -1) {
            if (prev == '\r' && curr == '\n') {
                sb.setLength(Math.max(0, sb.length() - 1));
                break;
            }
            sb.append((char) curr);
            prev = curr;
        }
        if (sb.length() == 0 && curr == -1) {
            return null;
        }
        return sb.toString();
    }

    private Map<String, Object> checkAntivirus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", antivirusService.isEnabled());

        if (antivirusService.isEnabled()) {
            boolean available = antivirusService.isAvailable();
            status.put("available", available);

            if (available) {
                String version = antivirusService.getVersion();
                status.put("version", version != null ? version : "unknown");
                status.put("status", "healthy");
            } else {
                status.put("status", "unavailable");
                status.put("error", "ClamAV daemon is not responding");
            }
        } else {
            status.put("status", "disabled");
        }

        return status;
    }
}
