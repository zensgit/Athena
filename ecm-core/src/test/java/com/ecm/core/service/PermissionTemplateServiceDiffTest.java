package com.ecm.core.service;

import com.ecm.core.dto.PermissionTemplateVersionDiffDto;
import com.ecm.core.entity.Permission;
import com.ecm.core.entity.PermissionSet;
import com.ecm.core.entity.PermissionTemplate;
import com.ecm.core.entity.PermissionTemplateVersion;
import com.ecm.core.repository.PermissionTemplateRepository;
import com.ecm.core.repository.PermissionTemplateVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionTemplateServiceDiffTest {

    @Mock
    private PermissionTemplateRepository repository;

    @Mock
    private PermissionTemplateVersionRepository versionRepository;

    @Mock
    private SecurityService securityService;

    @Mock
    private NodeService nodeService;

    private PermissionTemplateService service;

    @BeforeEach
    void setUp() {
        service = new PermissionTemplateService(repository, versionRepository, securityService, nodeService);
    }

    @Test
    @DisplayName("computeVersionDiff detects permissionSet changes for the same authority identity")
    void computeVersionDiffDetectsChange() {
        UUID templateId = UUID.randomUUID();
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();

        PermissionTemplate template = new PermissionTemplate();
        template.setId(templateId);
        template.setName("e2e-template");

        PermissionTemplateVersion fromVersion = new PermissionTemplateVersion();
        fromVersion.setId(fromId);
        fromVersion.setTemplateId(templateId);
        fromVersion.setVersionNumber(1);
        fromVersion.setEntries(List.of(entry("viewer", Permission.AuthorityType.USER, PermissionSet.CONSUMER)));

        PermissionTemplateVersion toVersion = new PermissionTemplateVersion();
        toVersion.setId(toId);
        toVersion.setTemplateId(templateId);
        toVersion.setVersionNumber(2);
        toVersion.setEntries(List.of(entry("viewer", Permission.AuthorityType.USER, PermissionSet.EDITOR)));

        when(repository.existsById(templateId)).thenReturn(true);
        when(repository.findById(templateId)).thenReturn(Optional.of(template));
        when(versionRepository.findById(fromId)).thenReturn(Optional.of(fromVersion));
        when(versionRepository.findById(toId)).thenReturn(Optional.of(toVersion));

        PermissionTemplateVersionDiffDto diff = service.computeVersionDiff(templateId, fromId, toId);

        assertNotNull(diff);
        assertEquals(templateId, diff.getTemplateId());
        assertEquals("e2e-template", diff.getTemplateName());
        assertEquals(fromId, diff.getFromVersionId());
        assertEquals(1, diff.getFromVersionNumber());
        assertEquals(toId, diff.getToVersionId());
        assertEquals(2, diff.getToVersionNumber());
        assertEquals(0, diff.getAdded().size());
        assertEquals(0, diff.getRemoved().size());
        assertEquals(1, diff.getChanged().size());
        assertEquals(PermissionSet.CONSUMER, diff.getChanged().get(0).getBefore().getPermissionSet());
        assertEquals(PermissionSet.EDITOR, diff.getChanged().get(0).getAfter().getPermissionSet());
    }

    @Test
    @DisplayName("formatVersionDiffCsv outputs expected header and row values")
    void formatVersionDiffCsvOutputsRows() {
        PermissionTemplateVersionDiffDto diff = PermissionTemplateVersionDiffDto.builder()
            .templateId(UUID.randomUUID())
            .templateName("e2e-template")
            .fromVersionId(UUID.randomUUID())
            .fromVersionNumber(1)
            .toVersionId(UUID.randomUUID())
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

        String csv = service.formatVersionDiffCsv(diff);

        assertTrue(csv.startsWith("status,authority,authorityType,previousPermissionSet,currentPermissionSet"));
        assertTrue(csv.contains("CHANGED,viewer,USER,CONSUMER,EDITOR"));
    }

    private static PermissionTemplate.PermissionTemplateEntry entry(
        String authority,
        Permission.AuthorityType authorityType,
        PermissionSet permissionSet
    ) {
        PermissionTemplate.PermissionTemplateEntry entry = new PermissionTemplate.PermissionTemplateEntry();
        entry.setAuthority(authority);
        entry.setAuthorityType(authorityType);
        entry.setPermissionSet(permissionSet);
        return entry;
    }
}

