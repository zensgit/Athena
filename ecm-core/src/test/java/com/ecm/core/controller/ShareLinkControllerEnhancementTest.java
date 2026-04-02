package com.ecm.core.controller;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.ShareLink;
import com.ecm.core.entity.ShareLink.SharePermission;
import com.ecm.core.entity.ShareLinkAccessLog;
import com.ecm.core.service.ShareLinkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ShareLinkControllerEnhancementTest {

    private MockMvc mockMvc;
    @Mock private ShareLinkService shareLinkService;

    @BeforeEach
    void setUp() {
        ShareLinkController controller = new ShareLinkController(shareLinkService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new RestExceptionHandler())
            .build();
    }

    @Nested
    @DisplayName("POST /{token}/reactivate")
    class Reactivate {

        @Test
        @DisplayName("returns reactivated link as ShareLinkResponse")
        void reactivatesLink() throws Exception {
            ShareLink link = activeLink("tok123");
            when(shareLinkService.reactivateShareLink("tok123")).thenReturn(link);

            mockMvc.perform(post("/api/v1/share/tok123/reactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("tok123"))
                .andExpect(jsonPath("$.active").value(true));

            verify(shareLinkService).reactivateShareLink("tok123");
        }
    }

    @Nested
    @DisplayName("GET /admin/all")
    class AdminAll {

        @Test
        @DisplayName("returns list of all share links")
        void returnsAll() throws Exception {
            when(shareLinkService.listAllShareLinks())
                .thenReturn(List.of(activeLink("a"), activeLink("b")));

            mockMvc.perform(get("/api/v1/share/admin/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].token").value("a"))
                .andExpect(jsonPath("$[1].token").value("b"));
        }
    }

    @Nested
    @DisplayName("GET /{token}/access-log")
    class AccessLog {

        @Test
        @DisplayName("returns access log entries")
        void returnsLog() throws Exception {
            ShareLinkAccessLog entry = new ShareLinkAccessLog();
            entry.setId(UUID.randomUUID());
            entry.setAccessedAt(LocalDateTime.of(2026, 3, 30, 12, 0));
            entry.setClientIp("10.0.0.1");
            entry.setSuccess(true);

            when(shareLinkService.getAccessLog("tok123")).thenReturn(List.of(entry));

            mockMvc.perform(get("/api/v1/share/tok123/access-log"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].clientIp").value("10.0.0.1"))
                .andExpect(jsonPath("$[0].success").value(true));
        }
    }

    @Nested
    @DisplayName("GET /{token}/access-stats")
    class AccessStats {

        @Test
        @DisplayName("returns total/successful/failed counts")
        void returnsStats() throws Exception {
            when(shareLinkService.getAccessStats("tok123"))
                .thenReturn(new ShareLinkService.ShareLinkAccessStats(15, 12, 3));

            mockMvc.perform(get("/api/v1/share/tok123/access-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAccesses").value(15))
                .andExpect(jsonPath("$.successfulAccesses").value(12))
                .andExpect(jsonPath("$.failedAccesses").value(3));
        }
    }

    private ShareLink activeLink(String token) {
        Document doc = new Document();
        doc.setId(UUID.randomUUID());
        doc.setName("report.pdf");
        doc.setMimeType("application/pdf");
        doc.setPath("/report.pdf");

        ShareLink link = new ShareLink();
        link.setId(UUID.randomUUID());
        link.setToken(token);
        link.setNode(doc);
        link.setActive(true);
        link.setCreatedBy("alice");
        link.setPermissionLevel(SharePermission.VIEW);
        link.setAccessCount(0);
        return link;
    }
}
