package com.ecm.core.controller;

import com.ecm.core.entity.Site.SiteVisibility;
import com.ecm.core.service.SiteMembershipService;
import com.ecm.core.service.SiteService;
import com.ecm.core.service.SiteService.SiteDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
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
class SiteMembershipContractTest {

    private MockMvc mockMvc;
    @Mock private SiteService siteService;
    @Mock private SiteMembershipService membershipService;

    @BeforeEach
    void setUp() {
        SiteController controller = new SiteController(siteService, membershipService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new RestExceptionHandler())
            .build();
    }

    @Nested
    @DisplayName("GET /sites")
    class ListSites {

        @Test
        @DisplayName("returns site list with visibility and status")
        void returnsSiteList() throws Exception {
            SiteDto site = siteDto("finance", "Finance Workspace", SiteVisibility.MODERATED);
            when(siteService.listSites(false)).thenReturn(List.of(site));

            mockMvc.perform(get("/api/v1/sites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].siteId").value("finance"))
                .andExpect(jsonPath("$[0].title").value("Finance Workspace"))
                .andExpect(jsonPath("$[0].visibility").value("MODERATED"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
        }

        @Test
        @DisplayName("passes includeArchived parameter")
        void passesIncludeArchived() throws Exception {
            when(siteService.listSites(true)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/sites").param("includeArchived", "true"))
                .andExpect(status().isOk());

            verify(siteService).listSites(true);
        }
    }

    @Nested
    @DisplayName("GET /sites/{siteId}")
    class GetSite {

        @Test
        @DisplayName("returns site by siteId")
        void returnsSite() throws Exception {
            SiteDto site = siteDto("hr", "Human Resources", SiteVisibility.PRIVATE);
            when(siteService.getSite("hr")).thenReturn(site);

            mockMvc.perform(get("/api/v1/sites/hr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siteId").value("hr"))
                .andExpect(jsonPath("$.visibility").value("PRIVATE"));
        }
    }

    @Nested
    @DisplayName("POST /sites")
    class CreateSite {

        @Test
        @DisplayName("creates site and returns 201")
        void createsSite() throws Exception {
            SiteDto created = siteDto("legal", "Legal Team", SiteVisibility.PUBLIC);
            when(siteService.createSite(any())).thenReturn(created);

            mockMvc.perform(post("/api/v1/sites")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "siteId": "legal",
                          "title": "Legal Team",
                          "visibility": "PUBLIC"
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.siteId").value("legal"))
                .andExpect(jsonPath("$.title").value("Legal Team"));
        }
    }

    @Nested
    @DisplayName("PUT /sites/{siteId}")
    class UpdateSite {

        @Test
        @DisplayName("updates site fields")
        void updatesSite() throws Exception {
            SiteDto updated = siteDto("hr", "HR Department", SiteVisibility.MODERATED);
            when(siteService.updateSite(any(), any())).thenReturn(updated);

            mockMvc.perform(put("/api/v1/sites/hr")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        { "title": "HR Department", "visibility": "MODERATED" }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("HR Department"));
        }
    }

    @Nested
    @DisplayName("DELETE /sites/{siteId}")
    class DeleteSite {

        @Test
        @DisplayName("archives site and returns 204")
        void deletesSite() throws Exception {
            mockMvc.perform(delete("/api/v1/sites/hr"))
                .andExpect(status().isNoContent());

            verify(siteService).deleteSite("hr");
        }
    }

    private SiteDto siteDto(String siteId, String title, SiteVisibility visibility) {
        return new SiteDto(
            UUID.randomUUID(), siteId, title, "Description for " + siteId,
            visibility, com.ecm.core.entity.Site.SiteStatus.ACTIVE,
            null, null, null,
            "admin", LocalDateTime.now(), LocalDateTime.now(),
            false, null, null
        );
    }
}
