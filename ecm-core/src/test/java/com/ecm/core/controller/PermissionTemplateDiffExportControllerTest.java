package com.ecm.core.controller;

import com.ecm.core.dto.PermissionTemplateVersionDiffDto;
import com.ecm.core.entity.Permission;
import com.ecm.core.entity.PermissionSet;
import com.ecm.core.service.AuditService;
import com.ecm.core.service.PermissionTemplateService;
import com.ecm.core.service.SecurityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionTemplateDiffExportControllerTest {

    @Mock
    private PermissionTemplateService permissionTemplateService;

    @Mock
    private AuditService auditService;

    @Mock
    private SecurityService securityService;

    @Test
    @DisplayName("exportVersionDiff(format=json) returns dto and logs audit event")
    void exportVersionDiffJsonLogsAudit() {
        UUID templateId = UUID.randomUUID();
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();

        PermissionTemplateVersionDiffDto diff = PermissionTemplateVersionDiffDto.builder()
            .templateId(templateId)
            .templateName("e2e-template")
            .fromVersionId(fromId)
            .fromVersionNumber(1)
            .toVersionId(toId)
            .toVersionNumber(2)
            .added(List.of())
            .removed(List.of())
            .changed(List.of(PermissionTemplateVersionDiffDto.ChangeDto.builder()
                .before(PermissionTemplateVersionDiffDto.EntryDto.builder()
                    .authority("viewer")
                    .authorityType(Permission.AuthorityType.USER)
                    .permissionSet(PermissionSet.CONSUMER)
                    .build())
                .after(PermissionTemplateVersionDiffDto.EntryDto.builder()
                    .authority("viewer")
                    .authorityType(Permission.AuthorityType.USER)
                    .permissionSet(PermissionSet.EDITOR)
                    .build())
                .build()))
            .build();

        when(permissionTemplateService.computeVersionDiff(templateId, fromId, toId)).thenReturn(diff);
        when(securityService.getCurrentUser()).thenReturn("admin");

        PermissionTemplateController controller = new PermissionTemplateController(
            permissionTemplateService,
            auditService,
            securityService
        );

        ResponseEntity<?> response = controller.exportVersionDiff(templateId, fromId, toId, "json");

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(diff, response.getBody());
        assertNotNull(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains(".json"));

        ArgumentCaptor<String> detailsCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).logEvent(
            eq("SECURITY_PERMISSION_TEMPLATE_DIFF_EXPORT"),
            eq(templateId),
            eq("e2e-template"),
            eq("admin"),
            detailsCaptor.capture()
        );
        assertTrue(detailsCaptor.getValue().contains("format=json"));
        assertTrue(detailsCaptor.getValue().contains("fromVersionId=" + fromId));
        assertTrue(detailsCaptor.getValue().contains("toVersionId=" + toId));
    }

    @Test
    @DisplayName("exportVersionDiff(format=csv) returns bytes and logs audit event")
    void exportVersionDiffCsvLogsAudit() {
        UUID templateId = UUID.randomUUID();
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();

        PermissionTemplateVersionDiffDto diff = PermissionTemplateVersionDiffDto.builder()
            .templateId(templateId)
            .templateName("e2e-template")
            .fromVersionId(fromId)
            .fromVersionNumber(1)
            .toVersionId(toId)
            .toVersionNumber(2)
            .added(List.of())
            .removed(List.of())
            .changed(List.of())
            .build();

        when(permissionTemplateService.computeVersionDiff(templateId, fromId, toId)).thenReturn(diff);
        when(permissionTemplateService.formatVersionDiffCsv(diff)).thenReturn(
            "status,authority,authorityType,previousPermissionSet,currentPermissionSet\n"
        );
        when(securityService.getCurrentUser()).thenReturn("admin");

        PermissionTemplateController controller = new PermissionTemplateController(
            permissionTemplateService,
            auditService,
            securityService
        );

        ResponseEntity<?> response = controller.exportVersionDiff(templateId, fromId, toId, "csv");

        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains(".csv"));

        byte[] bytes = (byte[]) response.getBody();
        assertNotNull(bytes);
        String csv = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("status,authority,authorityType,previousPermissionSet,currentPermissionSet"));

        verify(auditService).logEvent(
            eq("SECURITY_PERMISSION_TEMPLATE_DIFF_EXPORT"),
            eq(templateId),
            eq("e2e-template"),
            eq("admin"),
            org.mockito.ArgumentMatchers.contains("format=csv")
        );
    }
}

