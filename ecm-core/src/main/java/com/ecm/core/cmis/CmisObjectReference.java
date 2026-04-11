package com.ecm.core.cmis;

import java.util.UUID;

final class CmisObjectReference {

    private static final String VERSION_PREFIX = ";v";

    private final UUID nodeId;
    private final String versionLabel;

    private CmisObjectReference(UUID nodeId, String versionLabel) {
        this.nodeId = nodeId;
        this.versionLabel = versionLabel;
    }

    static CmisObjectReference parse(String objectId) {
        if (objectId == null || objectId.isBlank()) {
            throw new IllegalArgumentException("objectId is required");
        }

        String normalized = objectId.trim();
        if (CmisObjectFactory.ROOT_OBJECT_ID.equalsIgnoreCase(normalized)) {
            throw new IllegalArgumentException("Root objectId is not valid for this operation");
        }

        int separatorIndex = normalized.indexOf(';');
        if (separatorIndex < 0) {
            return new CmisObjectReference(parseUuid(normalized), null);
        }

        String nodeIdPart = normalized.substring(0, separatorIndex).trim();
        String suffix = normalized.substring(separatorIndex).trim();
        if (!suffix.startsWith(VERSION_PREFIX) || suffix.length() <= VERSION_PREFIX.length()) {
            throw new IllegalArgumentException("Malformed CMIS objectId: " + objectId);
        }

        String versionLabel = suffix.substring(VERSION_PREFIX.length()).trim();
        if (versionLabel.isEmpty()) {
            throw new IllegalArgumentException("Malformed CMIS objectId: " + objectId);
        }

        return new CmisObjectReference(parseUuid(nodeIdPart), versionLabel);
    }

    UUID nodeId() {
        return nodeId;
    }

    boolean isVersionSpecific() {
        return versionLabel != null;
    }

    String versionLabel() {
        return versionLabel;
    }

    String rawNodeId() {
        return nodeId.toString();
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Malformed CMIS objectId: " + raw, ex);
        }
    }
}
