package com.ecm.core.integration.wopi.service;

import com.ecm.core.integration.wopi.model.CollaboraCapabilities;
import com.ecm.core.integration.wopi.model.CollaboraDiscoveryStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class CollaboraDiscoveryService {

    private final RestTemplate restTemplate;

    @Value("${ecm.wopi.discovery-url:http://collabora:9980/hosting/discovery}")
    private String discoveryUrl;

    @Value("${ecm.wopi.capabilities-url:http://collabora:9980/hosting/capabilities}")
    private String capabilitiesUrl;

    @Value("${ecm.wopi.public-url:http://localhost:9980}")
    private String publicUrl;

    private final AtomicLong lastLoadedAtMs = new AtomicLong(0);
    private final AtomicReference<String> lastLoadError = new AtomicReference<>(null);
    private volatile Map<String, Map<String, String>> urlsrcByExt = Map.of();

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    public CollaboraDiscoveryService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String findUrlsrc(String extension, String action) {
        if (extension == null || extension.isBlank()) {
            return null;
        }
        String ext = normalizeExt(extension);
        String actionName = action != null ? action.trim() : "";

        Map<String, Map<String, String>> discovery = getOrLoadDiscovery();
        Map<String, String> byAction = discovery.get(ext);
        if (byAction == null) {
            return null;
        }
        return byAction.get(actionName);
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public String getDiscoveryUrl() {
        return discoveryUrl;
    }

    public String getCapabilitiesUrl() {
        return capabilitiesUrl;
    }

    public CollaboraDiscoveryStatus getDiscoveryStatus() {
        Map<String, Map<String, String>> discovery = getOrLoadDiscovery();

        List<String> sampleExtensions = discovery.keySet().stream()
            .sorted()
            .limit(20)
            .toList();

        Map<String, List<String>> sampleActions = new LinkedHashMap<>();
        for (String ext : sampleExtensions) {
            Map<String, String> actions = discovery.get(ext);
            if (actions == null || actions.isEmpty()) {
                continue;
            }
            sampleActions.put(ext, actions.keySet().stream().sorted().toList());
        }

        return CollaboraDiscoveryStatus.builder()
            .reachable(!discovery.isEmpty())
            .lastLoadedAtMs(lastLoadedAtMs.get())
            .cacheTtlSeconds(CACHE_TTL.toSeconds())
            .extensionCount(discovery.size())
            .sampleActionsByExtension(Map.copyOf(sampleActions))
            .lastError(lastLoadError.get())
            .build();
    }

    public CollaboraCapabilities getCapabilities() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.HOST, stripScheme(publicUrl).replaceAll("/+$", ""));

            ResponseEntity<Map> response = restTemplate.exchange(
                capabilitiesUrl,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
            );

            Map<?, ?> body = response.getBody();
            if (body == null || body.isEmpty()) {
                return CollaboraCapabilities.builder()
                    .reachable(false)
                    .error("Empty capabilities response")
                    .build();
            }

            Object hasWopiAccessCheck = body.get("hasWopiAccessCheck");
            Object hasSettingIframeSupport = body.get("hasSettingIframeSupport");

            return CollaboraCapabilities.builder()
                .reachable(true)
                .productName(asString(body.get("productName")))
                .productVersion(asString(body.get("productVersion")))
                .productVersionHash(asString(body.get("productVersionHash")))
                .serverId(asString(body.get("serverId")))
                .hasWopiAccessCheck(asBoolean(hasWopiAccessCheck))
                .hasSettingIframeSupport(asBoolean(hasSettingIframeSupport))
                .build();
        } catch (Exception e) {
            return CollaboraCapabilities.builder()
                .reachable(false)
                .error(e.getMessage())
                .build();
        }
    }

    private Map<String, Map<String, String>> getOrLoadDiscovery() {
        long now = System.currentTimeMillis();
        long last = lastLoadedAtMs.get();
        if (now - last < CACHE_TTL.toMillis() && !urlsrcByExt.isEmpty()) {
            return urlsrcByExt;
        }
        synchronized (this) {
            long last2 = lastLoadedAtMs.get();
            if (now - last2 < CACHE_TTL.toMillis() && !urlsrcByExt.isEmpty()) {
                return urlsrcByExt;
            }
            Map<String, Map<String, String>> loaded = loadDiscovery();
            if (!loaded.isEmpty()) {
                urlsrcByExt = loaded;
                lastLoadedAtMs.set(now);
            }
            return urlsrcByExt;
        }
    }

    private Map<String, Map<String, String>> loadDiscovery() {
        try {
            HttpHeaders headers = new HttpHeaders();
            // Force discovery to generate URL templates based on the browser-accessible host.
            // Otherwise it may return urlsrc using the docker hostname (e.g., "collabora"), which the browser can't resolve.
            headers.set(HttpHeaders.HOST, stripScheme(publicUrl).replaceAll("/+$", ""));

            ResponseEntity<String> response = restTemplate.exchange(
                discoveryUrl,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
            );

            String xml = response.getBody();
            if (xml == null || xml.isBlank()) {
                lastLoadError.set("Empty discovery response");
                return Map.of();
            }
            lastLoadError.set(null);
            return parseDiscovery(xml);
        } catch (Exception e) {
            lastLoadError.set(e.getMessage());
            log.warn("Failed to load Collabora discovery from {}: {}", discoveryUrl, e.getMessage());
            return Map.of();
        }
    }

    private Map<String, Map<String, String>> parseDiscovery(String xml) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        NodeList actionNodes = doc.getElementsByTagName("action");
        Map<String, Map<String, String>> result = new HashMap<>();

        for (int i = 0; i < actionNodes.getLength(); i++) {
            if (!(actionNodes.item(i) instanceof Element actionEl)) {
                continue;
            }

            String extAttr = actionEl.getAttribute("ext");
            String nameAttr = actionEl.getAttribute("name");
            String urlsrcAttr = actionEl.getAttribute("urlsrc");

            if (extAttr == null || extAttr.isBlank() || nameAttr == null || nameAttr.isBlank() || urlsrcAttr == null || urlsrcAttr.isBlank()) {
                continue;
            }

            String ext = normalizeExt(extAttr);
            result.computeIfAbsent(ext, k -> new HashMap<>()).put(nameAttr, urlsrcAttr);
        }

        return Map.copyOf(result.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                e -> Map.copyOf(e.getValue())
            )));
    }

    private String normalizeExt(String ext) {
        String v = ext.trim().toLowerCase();
        if (v.startsWith(".")) {
            v = v.substring(1);
        }
        // Collabora sometimes reports multiple extensions separated by '|'
        int pipe = v.indexOf('|');
        if (pipe > 0) {
            v = v.substring(0, pipe);
        }
        return v;
    }

    private String stripScheme(String url) {
        String u = Objects.requireNonNullElse(url, "");
        u = u.replaceFirst("^https?://", "");
        return u;
    }

    private static String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private static Boolean asBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(value.toString());
    }
}
