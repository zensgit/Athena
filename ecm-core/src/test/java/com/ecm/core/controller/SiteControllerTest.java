package com.ecm.core.controller;

import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Site;
import com.ecm.core.entity.Site.SiteStatus;
import com.ecm.core.entity.Site.SiteVisibility;
import com.ecm.core.service.SiteMembershipService;
import com.ecm.core.service.SiteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class SiteControllerTest {

    @Mock private SiteService siteService;
    @Mock private SiteMembershipService membershipService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SiteController(siteService, membershipService))
            .setControllerAdvice(new RestExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("GET /sites returns the site registry")
    void listSites() throws Exception {
        when(siteService.listSites(false)).thenReturn(List.of(
            siteDto("finance", "Finance Workspace", SiteVisibility.PUBLIC, SiteStatus.ACTIVE)
        ));

        mockMvc.perform(get("/api/v1/sites"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].siteId").value("finance"))
            .andExpect(jsonPath("$[0].title").value("Finance Workspace"));
    }

    @Test
    @DisplayName("POST /sites creates a registry entry")
    void createSite() throws Exception {
        when(siteService.createSite(any())).thenReturn(siteDto("finance", "Finance Workspace", SiteVisibility.MODERATED, SiteStatus.ACTIVE));

        mockMvc.perform(post("/api/v1/sites")
                .contentType("application/json")
                .content("""
                    {
                      "siteId": "Finance",
                      "title": "Finance Workspace",
                      "description": "Finance collaboration space",
                      "visibility": "MODERATED"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.siteId").value("finance"))
            .andExpect(jsonPath("$.visibility").value("MODERATED"));
    }

    @Test
    @DisplayName("PUT /sites/{siteId} updates a registry entry")
    void updateSite() throws Exception {
        when(siteService.updateSite(any(), any())).thenReturn(siteDto("finance", "Finance Hub", SiteVisibility.PUBLIC, SiteStatus.ARCHIVED));

        mockMvc.perform(put("/api/v1/sites/finance")
                .contentType("application/json")
                .content("""
                    {
                      "title": "Finance Hub",
                      "status": "ARCHIVED"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Finance Hub"))
            .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    @DisplayName("DELETE /sites/{siteId} archives a registry entry")
    void deleteSite() throws Exception {
        mockMvc.perform(delete("/api/v1/sites/finance"))
            .andExpect(status().isNoContent());

        verify(siteService).deleteSite("finance");
    }

    private SiteService.SiteDto siteDto(String siteId, String title, SiteVisibility visibility, SiteStatus status) {
        Site site = new Site();
        site.setId(UUID.randomUUID());
        site.setSiteId(siteId);
        site.setTitle(title);
        site.setDescription(title + " description");
        site.setVisibility(visibility);
        site.setStatus(status);
        site.setCreatedBy("admin");
        site.setCreatedDate(LocalDateTime.of(2026, 4, 1, 10, 0));
        return new SiteService.SiteDto(
            site.getId(),
            site.getSiteId(),
            site.getTitle(),
            site.getDescription(),
            site.getVisibility(),
            site.getStatus(),
            null,
            null,
            null,
            site.getCreatedBy(),
            site.getCreatedDate(),
            site.getLastModifiedDate(),
            site.isDeleted(),
            site.getDeletedAt(),
            site.getDeletedBy()
        );
    }
}
