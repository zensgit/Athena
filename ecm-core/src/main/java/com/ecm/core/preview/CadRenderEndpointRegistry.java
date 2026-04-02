package com.ecm.core.preview;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class CadRenderEndpointRegistry {

    @Value("${ecm.preview.cad.enabled:true}")
    private boolean cadPreviewEnabled;

    @Value("${ecm.preview.cad.render-url:}")
    private String primaryRenderUrl;

    @Value("${ecm.preview.cad.render-fallback-urls:}")
    private String fallbackRenderUrls;

    public boolean isCadPreviewEnabled() {
        return cadPreviewEnabled;
    }

    public List<String> resolveEndpoints() {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        addUrl(urls, primaryRenderUrl);
        if (fallbackRenderUrls != null && !fallbackRenderUrls.isBlank()) {
            for (String token : fallbackRenderUrls.split("[,;\\n]")) {
                addUrl(urls, token);
            }
        }
        return new ArrayList<>(urls);
    }

    public boolean isConfigured() {
        return !resolveEndpoints().isEmpty();
    }

    private void addUrl(LinkedHashSet<String> urls, String candidate) {
        if (candidate == null) {
            return;
        }
        String normalized = candidate.trim();
        if (!normalized.isEmpty()) {
            urls.add(normalized);
        }
    }
}
