package com.ecm.core.cmis;

import com.ecm.core.entity.AuditLog;
import com.ecm.core.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

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

    public CmisModels.ContentChangesResponse getContentChanges(String changeLogToken, int maxItems) {
        int normalizedMax = Math.max(Math.min(maxItems, 1000), 1);
        PageRequest pageRequest = PageRequest.of(0, normalizedMax);

        Page<AuditLog> page;
        if (changeLogToken == null || changeLogToken.isBlank()) {
            page = auditLogRepository.findByEventTypeInOrderByEventTimeAsc(CMIS_EVENT_TYPES, pageRequest);
        } else {
            LocalDateTime after = LocalDateTime.parse(changeLogToken, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            page = auditLogRepository.findByEventTypeInAndEventTimeAfterOrderByEventTimeAsc(
                CMIS_EVENT_TYPES, after, pageRequest
            );
        }

        List<CmisModels.ChangeEntry> changes = page.getContent().stream()
            .map(this::toChangeEntry)
            .toList();

        String latestToken = null;
        if (!page.getContent().isEmpty()) {
            AuditLog last = page.getContent().get(page.getContent().size() - 1);
            latestToken = last.getEventTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }

        return new CmisModels.ContentChangesResponse(changes, latestToken, page.hasNext());
    }

    private CmisModels.ChangeEntry toChangeEntry(AuditLog auditLog) {
        String objectId = auditLog.getNodeId() != null ? auditLog.getNodeId().toString() : null;
        String changeType = EVENT_TO_CHANGE_TYPE.getOrDefault(auditLog.getEventType(), "updated");
        String changeTime = auditLog.getEventTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return new CmisModels.ChangeEntry(objectId, changeType, changeTime, auditLog.getUsername());
    }
}
