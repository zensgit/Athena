package com.ecm.core.service;

import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Site;
import com.ecm.core.entity.Site.SiteStatus;
import com.ecm.core.entity.Site.SiteVisibility;
import com.ecm.core.repository.FolderRepository;
import com.ecm.core.repository.SiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SiteServiceTest {

    @Mock private SiteRepository siteRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private SecurityService securityService;
    @Mock private ActivityEventListener activityEventListener;

    private SiteService siteService;

    @BeforeEach
    void setUp() {
        siteService = new SiteService(siteRepository, folderRepository, securityService, activityEventListener);
    }

    @Test
    @DisplayName("createSite persists a normalized site registry entry")
    void createSitePersistsRegistryEntry() {
        Folder rootFolder = folder("Finance Workspace", "/Sites/Finance_Workspace");
        Site saved = site("finance", "Finance Workspace", SiteVisibility.MODERATED, SiteStatus.ACTIVE, rootFolder);
        saved.setId(UUID.randomUUID());
        saved.setCreatedBy("admin");
        saved.setCreatedDate(LocalDateTime.of(2026, 4, 1, 9, 30));

        when(siteRepository.findBySiteIdIgnoreCase("finance")).thenReturn(Optional.empty());
        when(folderRepository.findById(rootFolder.getId())).thenReturn(Optional.of(rootFolder));
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(siteRepository.save(any(Site.class))).thenAnswer(invocation -> {
            Site toSave = invocation.getArgument(0);
            toSave.setId(saved.getId());
            toSave.setCreatedBy(saved.getCreatedBy());
            toSave.setCreatedDate(saved.getCreatedDate());
            return toSave;
        });

        SiteService.SiteDto response = siteService.createSite(new SiteService.CreateSiteRequest(
            " Finance ",
            "Finance Workspace",
            "Finance collaboration space",
            SiteVisibility.MODERATED,
            rootFolder.getId()
        ));

        assertEquals("finance", response.siteId());
        assertEquals("Finance Workspace", response.title());
        assertEquals("Finance collaboration space", response.description());
        assertEquals(SiteVisibility.MODERATED, response.visibility());
        assertEquals(rootFolder.getId(), response.rootFolderId());
        assertEquals("Finance Workspace", response.rootFolderTitle());
        assertEquals("/Sites/Finance_Workspace", response.rootFolderPath());
        verify(siteRepository).save(any(Site.class));
        verify(activityEventListener).postSiteActivity(
            eq("site.created"),
            eq("admin"),
            eq("finance"),
            anyMap()
        );
    }

    @Test
    @DisplayName("createSite rejects duplicate site ids")
    void createSiteRejectsDuplicateIds() {
        when(siteRepository.findBySiteIdIgnoreCase("finance")).thenReturn(Optional.of(site("finance", "Finance", SiteVisibility.PUBLIC, SiteStatus.ACTIVE, null)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            siteService.createSite(new SiteService.CreateSiteRequest(
                "Finance",
                "Finance Workspace",
                null,
                null,
                null
            ))
        );

        assertTrue(ex.getMessage().contains("Site already exists"));
        verify(siteRepository, never()).save(any());
    }

    @Test
    @DisplayName("listSites excludes archived rows unless requested")
    void listSitesFiltersArchivedRows() {
        Site active = site("finance", "Finance Workspace", SiteVisibility.PUBLIC, SiteStatus.ACTIVE, null);
        Site archived = site("legal", "Legal Workspace", SiteVisibility.PRIVATE, SiteStatus.ARCHIVED, null);
        archived.setDeleted(true);

        when(siteRepository.findByDeletedFalseOrderByTitleAsc()).thenReturn(List.of(active));
        when(siteRepository.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(active, archived));

        assertEquals(1, siteService.listSites(false).size());
        assertEquals(2, siteService.listSites(true).size());
    }

    @Test
    @DisplayName("updateSite changes mutable fields and deleteSite soft deletes")
    void updateAndDeleteSite() {
        Site site = site("finance", "Finance Workspace", SiteVisibility.PUBLIC, SiteStatus.ACTIVE, null);
        site.setId(UUID.randomUUID());
        site.setCreatedBy("admin");

        when(siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse("finance")).thenReturn(Optional.of(site));
        when(siteRepository.save(any(Site.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(securityService.getCurrentUser()).thenReturn("moderator");

        SiteService.SiteDto updated = siteService.updateSite(
            "finance",
            new SiteService.UpdateSiteRequest("Finance Hub", "Updated description", SiteVisibility.MODERATED, SiteStatus.ARCHIVED, null)
        );
        assertEquals("Finance Hub", updated.title());
        assertEquals(SiteVisibility.MODERATED, updated.visibility());
        assertEquals(SiteStatus.ARCHIVED, updated.status());
        verify(activityEventListener).postSiteActivity(
            eq("site.archived"),
            eq("moderator"),
            eq("finance"),
            anyMap()
        );

        siteService.deleteSite("finance");
        assertTrue(site.isDeleted());
        assertEquals("moderator", site.getDeletedBy());
        assertEquals(SiteStatus.ARCHIVED, site.getStatus());
        verify(activityEventListener, times(2)).postSiteActivity(
            eq("site.archived"),
            eq("moderator"),
            eq("finance"),
            anyMap()
        );
    }

    private Site site(String siteId, String title, SiteVisibility visibility, SiteStatus status, Folder rootFolder) {
        Site site = new Site();
        site.setSiteId(siteId);
        site.setTitle(title);
        site.setDescription(title + " description");
        site.setVisibility(visibility);
        site.setStatus(status);
        site.setRootFolder(rootFolder);
        return site;
    }

    private Folder folder(String name, String path) {
        Folder folder = new Folder();
        folder.setId(UUID.randomUUID());
        folder.setName(name);
        folder.setPath(path);
        folder.setDeleted(false);
        return folder;
    }
}
