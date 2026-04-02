package com.ecm.core.preview;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class PreviewFailurePolicyRegistry {

    private static final List<String> PROFILE_ORDER = List.of("default", "cad", "pdf", "office", "image", "text");

    private final Map<String, PreviewFailurePolicy> policies = new ConcurrentHashMap<>();

    public PreviewFailurePolicyRegistry() {
        policies.putAll(defaultPolicies());
    }

    public List<PreviewFailurePolicy> listPolicies() {
        List<PreviewFailurePolicy> all = new ArrayList<>(policies.values());
        all.sort(
            Comparator
                .comparingInt((PreviewFailurePolicy policy) -> profileOrder(policy.key()))
                .thenComparing(PreviewFailurePolicy::key)
        );
        return all;
    }

    public PreviewFailurePolicy resolve(String mimeType, String fileName) {
        String key = classifyProfileKey(mimeType, fileName);
        PreviewFailurePolicy policy = policies.get(key);
        if (policy != null) {
            return policy;
        }
        return policies.getOrDefault(
            "default",
            new PreviewFailurePolicy("default", "Default", 3, 60000L, 1.6d, 0L, true)
        );
    }

    public PreviewFailurePolicy upsert(String profileKey, PreviewFailurePolicyUpdate update) {
        String normalizedKey = normalizeProfileKey(profileKey);
        PreviewFailurePolicy previous = policies.getOrDefault(
            normalizedKey,
            new PreviewFailurePolicy(normalizedKey, toTitle(normalizedKey), 3, 60000L, 1.6d, 0L, false)
        );
        PreviewFailurePolicy merged = new PreviewFailurePolicy(
            normalizedKey,
            previous.label(),
            sanitizeAttempts(update.maxAttempts() != null ? update.maxAttempts() : previous.maxAttempts()),
            sanitizeDelay(update.retryDelayMs() != null ? update.retryDelayMs() : previous.retryDelayMs()),
            sanitizeBackoff(update.backoffMultiplier() != null ? update.backoffMultiplier() : previous.backoffMultiplier()),
            sanitizeQuietPeriod(update.quietPeriodMs() != null ? update.quietPeriodMs() : previous.quietPeriodMs()),
            previous.builtIn()
        );
        policies.put(normalizedKey, merged);
        return merged;
    }

    public synchronized void replaceAll(List<PreviewFailurePolicy> snapshot) {
        policies.clear();
        if (snapshot == null || snapshot.isEmpty()) {
            policies.putAll(defaultPolicies());
            return;
        }
        Map<String, PreviewFailurePolicy> restored = snapshot.stream()
            .filter(policy -> policy != null && policy.key() != null && !policy.key().isBlank())
            .collect(Collectors.toMap(
                policy -> normalizeProfileKey(policy.key()),
                policy -> new PreviewFailurePolicy(
                    normalizeProfileKey(policy.key()),
                    policy.label() != null && !policy.label().isBlank() ? policy.label() : toTitle(policy.key()),
                    sanitizeAttempts(policy.maxAttempts()),
                    sanitizeDelay(policy.retryDelayMs()),
                    sanitizeBackoff(policy.backoffMultiplier()),
                    sanitizeQuietPeriod(policy.quietPeriodMs()),
                    policy.builtIn()
                ),
                (left, right) -> right,
                ConcurrentHashMap::new
            ));
        if (restored.isEmpty()) {
            policies.putAll(defaultPolicies());
            return;
        }
        policies.putAll(restored);
    }

    private static Map<String, PreviewFailurePolicy> defaultPolicies() {
        Map<String, PreviewFailurePolicy> defaults = new ConcurrentHashMap<>();
        defaults.put("default", new PreviewFailurePolicy("default", "Default", 3, 60000L, 1.6d, 0L, true));
        defaults.put("cad", new PreviewFailurePolicy("cad", "CAD", 5, 60000L, 2.0d, 120000L, true));
        defaults.put("pdf", new PreviewFailurePolicy("pdf", "PDF", 3, 45000L, 1.5d, 0L, true));
        defaults.put("office", new PreviewFailurePolicy("office", "Office", 3, 45000L, 1.5d, 0L, true));
        defaults.put("image", new PreviewFailurePolicy("image", "Image", 2, 30000L, 1.2d, 0L, true));
        defaults.put("text", new PreviewFailurePolicy("text", "Text", 2, 30000L, 1.2d, 0L, true));
        return defaults;
    }

    private static int profileOrder(String key) {
        int idx = PROFILE_ORDER.indexOf(key);
        return idx >= 0 ? idx : PROFILE_ORDER.size();
    }

    private static String classifyProfileKey(String mimeType, String fileName) {
        String normalizedMime = normalizeMimeType(mimeType);
        String normalizedName = fileName == null ? "" : fileName.trim().toLowerCase(Locale.ROOT);

        if (normalizedMime.contains("dwg")
            || normalizedMime.contains("dxf")
            || normalizedMime.contains("autocad")
            || normalizedMime.contains("cad")
            || normalizedName.endsWith(".dwg")
            || normalizedName.endsWith(".dxf")) {
            return "cad";
        }
        if (normalizedMime.equals("application/pdf") || normalizedName.endsWith(".pdf")) {
            return "pdf";
        }
        if (normalizedMime.startsWith("image/")) {
            return "image";
        }
        if (normalizedMime.startsWith("text/")) {
            return "text";
        }
        if (normalizedMime.contains("officedocument")
            || normalizedMime.contains("msword")
            || normalizedMime.contains("ms-excel")
            || normalizedMime.contains("ms-powerpoint")
            || normalizedMime.contains("opendocument")
            || normalizedName.endsWith(".doc")
            || normalizedName.endsWith(".docx")
            || normalizedName.endsWith(".xls")
            || normalizedName.endsWith(".xlsx")
            || normalizedName.endsWith(".ppt")
            || normalizedName.endsWith(".pptx")
            || normalizedName.endsWith(".odt")
            || normalizedName.endsWith(".ods")
            || normalizedName.endsWith(".odp")) {
            return "office";
        }
        return "default";
    }

    private static String normalizeProfileKey(String profileKey) {
        if (profileKey == null || profileKey.isBlank()) {
            return "default";
        }
        String normalized = profileKey.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        if (normalized.isBlank()) {
            return "default";
        }
        return normalized;
    }

    private static String normalizeMimeType(String mimeType) {
        if (mimeType == null) {
            return "";
        }
        String normalized = mimeType.trim().toLowerCase(Locale.ROOT);
        int semicolon = normalized.indexOf(';');
        if (semicolon >= 0) {
            normalized = normalized.substring(0, semicolon).trim();
        }
        return normalized;
    }

    private static int sanitizeAttempts(int attempts) {
        return Math.min(Math.max(attempts, 1), 10);
    }

    private static long sanitizeDelay(long delayMs) {
        return Math.min(Math.max(delayMs, 1000L), 3600000L);
    }

    private static double sanitizeBackoff(double backoff) {
        if (!Double.isFinite(backoff)) {
            return 1.0d;
        }
        return Math.min(Math.max(backoff, 1.0d), 10.0d);
    }

    private static long sanitizeQuietPeriod(long quietPeriodMs) {
        return Math.min(Math.max(quietPeriodMs, 0L), 86400000L);
    }

    private static String toTitle(String key) {
        if (key == null || key.isBlank()) {
            return "Default";
        }
        String normalized = key.replace('-', ' ').replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    public record PreviewFailurePolicy(
        String key,
        String label,
        int maxAttempts,
        long retryDelayMs,
        double backoffMultiplier,
        long quietPeriodMs,
        boolean builtIn
    ) {}

    public record PreviewFailurePolicyUpdate(
        Integer maxAttempts,
        Long retryDelayMs,
        Double backoffMultiplier,
        Long quietPeriodMs
    ) {}
}
