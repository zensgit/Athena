package com.ecm.core.cmis;

import com.ecm.core.entity.AuditLog;
import com.ecm.core.entity.Node;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.AuditLogRepository;
import com.ecm.core.service.SecurityService;
import com.ecm.core.service.TenantWorkspaceScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CmisChangeLogService {

    private static final List<String> CMIS_EVENT_TYPES = List.of(
        "NODE_CREATED", "NODE_UPDATED", "NODE_DELETED", "VERSION_CREATED"
    );

    private static final Map<String, String> EVENT_TO_CHANGE_TYPE = Map.of(
        "NODE_CREATED", "created",
        "NODE_UPDATED", "updated",
        "NODE_DELETED", "deleted",
        "VERSION_CREATED", "updated"
    );

    private final AuditLogRepository auditLogRepository;
    private final NodeRepository nodeRepository;
    private final SecurityService securityService;
    private final TenantWorkspaceScopeService tenantWorkspaceScopeService;

    public CmisModels.ContentChangesResponse getContentChanges(String changeLogToken, int maxItems) {
        int normalizedMax = Math.max(Math.min(maxItems, 1000), 1);
        int batchSize = Math.min(Math.max(normalizedMax * 3, 50), 1000);
        ChangeCursor cursor = ChangeCursor.parse(changeLogToken);
        String latestToken = normalizeToken(changeLogToken);
        List<CmisModels.ChangeEntry> changes = new java.util.ArrayList<>();
        boolean hasMoreItems = false;

        while (changes.size() < normalizedMax) {
            Page<AuditLog> page = fetchPage(cursor, batchSize);
            if (page.getContent().isEmpty()) {
                return new CmisModels.ContentChangesResponse(changes, latestToken, false);
            }

            List<AuditLog> logs = page.getContent();
            for (int index = 0; index < logs.size(); index++) {
                AuditLog log = logs.get(index);
                cursor = ChangeCursor.from(log);
                latestToken = cursor.serialize();
                if (!isVisible(log)) {
                    continue;
                }
                changes.add(toChangeEntry(log));
                if (changes.size() == normalizedMax) {
                    hasMoreItems = index < logs.size() - 1 || page.hasNext();
                    break;
                }
            }

            if (changes.size() == normalizedMax || !page.hasNext()) {
                return new CmisModels.ContentChangesResponse(changes, latestToken, hasMoreItems);
            }
        }

        return new CmisModels.ContentChangesResponse(changes, latestToken, hasMoreItems);
    }

    private CmisModels.ChangeEntry toChangeEntry(AuditLog auditLog) {
        String objectId = auditLog.getNodeId() != null ? auditLog.getNodeId().toString() : null;
        String changeType = EVENT_TO_CHANGE_TYPE.getOrDefault(auditLog.getEventType(), "updated");
        String changeTime = auditLog.getEventTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return new CmisModels.ChangeEntry(objectId, changeType, changeTime, auditLog.getUsername());
    }

    private Page<AuditLog> fetchPage(ChangeCursor cursor, int batchSize) {
        PageRequest pageRequest = PageRequest.of(0, batchSize);
        if (cursor == null) {
            return auditLogRepository.findByEventTypeInOrderByEventTimeAscIdAsc(CMIS_EVENT_TYPES, pageRequest);
        }
        if (cursor.legacy()) {
            return auditLogRepository.findByEventTypeInAndEventTimeAfterOrderByEventTimeAsc(
                CMIS_EVENT_TYPES, cursor.eventTime(), pageRequest
            );
        }
        return auditLogRepository.findByEventTypeInAfterCursorOrderByEventTimeAscIdAsc(
            CMIS_EVENT_TYPES, cursor.eventTime(), cursor.auditLogId(), pageRequest
        );
    }

    private boolean isVisible(AuditLog auditLog) {
        if (auditLog.getNodeId() == null) {
            return false;
        }
        Node node = nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(
            auditLog.getNodeId(), Node.ArchiveStatus.LIVE
        ).orElse(null);
        if (node == null) {
            return false;
        }
        if (tenantWorkspaceScopeService.hasScopedTenantWorkspace()
            && !tenantWorkspaceScopeService.isPathVisible(node.getPath())) {
            return false;
        }
        return securityService.hasPermission(node, com.ecm.core.entity.Permission.PermissionType.READ);
    }

    private String normalizeToken(String changeLogToken) {
        if (changeLogToken == null || changeLogToken.isBlank()) {
            return null;
        }
        return changeLogToken.trim();
    }

    private record ChangeCursor(LocalDateTime eventTime, UUID auditLogId, boolean legacy) {

        static ChangeCursor parse(String token) {
            if (token == null || token.isBlank()) {
                return null;
            }

            String normalized = token.trim();
            int separatorIndex = normalized.indexOf('|');
            if (separatorIndex < 0) {
                return new ChangeCursor(
                    LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    new UUID(0L, 0L),
                    true
                );
            }

            String timePart = normalized.substring(0, separatorIndex);
            String idPart = normalized.substring(separatorIndex + 1);
            return new ChangeCursor(
                LocalDateTime.parse(timePart, DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                UUID.fromString(idPart),
                false
            );
        }

        static ChangeCursor from(AuditLog auditLog) {
            return new ChangeCursor(auditLog.getEventTime(), auditLog.getId(), false);
        }

        String serialize() {
            return eventTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "|" + auditLogId;
        }
    }
}
