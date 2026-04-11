package com.ecm.core.cmis;

import com.ecm.core.entity.RenditionResource;
import com.ecm.core.service.RenditionResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CmisRenditionService {

    private final RenditionResourceService renditionResourceService;

    public CmisModels.RenditionsResponse getRenditions(String objectId, String filter) {
        if (objectId == null || objectId.isBlank()) {
            throw new IllegalArgumentException("objectId is required");
        }

        UUID nodeId = CmisObjectReference.parse(objectId).nodeId();

        if ("cmis:none".equals(filter)) {
            return new CmisModels.RenditionsResponse(objectId.trim(), List.of());
        }

        List<RenditionResource> renditions = renditionResourceService.listForNode(nodeId);

        List<CmisModels.RenditionEntry> entries = renditions.stream()
            .filter(RenditionResource::isAvailable)
            .filter(r -> matchesFilter(r, filter))
            .map(this::toRenditionEntry)
            .toList();

        return new CmisModels.RenditionsResponse(objectId.trim(), entries);
    }

    private boolean matchesFilter(RenditionResource r, String filter) {
        if (filter == null || filter.isBlank() || "*".equals(filter.trim())) {
            return true;
        }
        String[] parts = filter.split(",");
        for (String part : parts) {
            String f = part.trim();
            if (f.endsWith("/*")) {
                String prefix = f.substring(0, f.length() - 1);
                if (r.getMimeType() != null && r.getMimeType().startsWith(prefix)) {
                    return true;
                }
            } else if (f.equals(r.getMimeType())) {
                return true;
            } else if (f.equals(r.getRenditionKey())) {
                return true;
            }
        }
        return false;
    }

    private CmisModels.RenditionEntry toRenditionEntry(RenditionResource r) {
        return new CmisModels.RenditionEntry(
            r.getId().toString(),
            r.getRenditionKey(),
            r.getMimeType(),
            r.getLabel(),
            r.getContentUrl()
        );
    }
}
