package com.ecm.core.controller;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.ShareLink;
import com.ecm.core.entity.ShareLink.SharePermission;
import com.ecm.core.entity.ShareLinkAccessLog;
import com.ecm.core.service.ShareLinkService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ShareLinkCrossSurfaceTest {

    private MockMvc mockMvc;
    @Mock private ShareLinkService shareLinkService;

    @BeforeEach
    void setUp() {
        ShareLinkController controller = new ShareLinkController(shareLinkService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new RestExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("ShareLinkResponse includes nodeId and nodeName for admin navigation")
    void responseIncludesNodeIdForNavigation() throws Exception {
        UUID nodeId = UUID.randomUUID();
        ShareLink link = link("tok1", nodeId, "quarterly-report.pdf");

        when(shareLinkService.listAllShareLinks()).thenReturn(List.of(link));

        mockMvc.perform(get("/api/v1/share/admin/all"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].nodeId").value(nodeId.toString()))
            .andExpect(jsonPath("$[0].nodeName").value("quarterly-report.pdf"))
            .andExpect(jsonPath("$[0].active").value(true))
            .andExpect(jsonPath("$[0].isValid").value(true));
    }

    @Test
    @DisplayName("Reactivate returns nodeId so admin panel can refresh correctly")
    void reactivateReturnsNodeId() throws Exception {
        UUID nodeId = UUID.randomUUID();
        ShareLink link = link("tok1", nodeId, "report.pdf");
        link.setActive(true);

        when(shareLinkService.reactivateShareLink("tok1")).thenReturn(link);

        mockMvc.perform(post("/api/v1/share/tok1/reactivate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nodeId").value(nodeId.toString()))
            .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("Access log includes clientIp for security audit drill-down")
    void accessLogIncludesClientIp() throws Exception {
        ShareLinkAccessLog entry = new ShareLinkAccessLog();
        entry.setId(UUID.randomUUID());
        entry.setAccessedAt(LocalDateTime.of(2026, 3, 30, 14, 30));
        entry.setClientIp("203.0.113.42");
        entry.setUserAgent("Mozilla/5.0");
        entry.setSuccess(false);
        entry.setFailureReason("IP restricted");

        when(shareLinkService.getAccessLog("tok1")).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/v1/share/tok1/access-log"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].clientIp").value("203.0.113.42"))
            .andExpect(jsonPath("$[0].userAgent").value("Mozilla/5.0"))
            .andExpect(jsonPath("$[0].success").value(false))
            .andExpect(jsonPath("$[0].failureReason").value("IP restricted"));
    }

    @Test
    @DisplayName("Admin all returns passwordProtected and hasIpRestrictions flags")
    void adminAllReturnsProtectionFlags() throws Exception {
        UUID nodeId = UUID.randomUUID();
        ShareLink link = link("tok2", nodeId, "secret.pdf");
        link.setPasswordHash("$2a$10$hashed");
        link.setAllowedIps("10.0.0.0/8");

        when(shareLinkService.listAllShareLinks()).thenReturn(List.of(link));

        mockMvc.perform(get("/api/v1/share/admin/all"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].passwordProtected").value(true))
            .andExpect(jsonPath("$[0].hasIpRestrictions").value(true));
    }

    private ShareLink link(String token, UUID nodeId, String nodeName) {
        Document doc = new Document();
        doc.setId(nodeId);
        doc.setName(nodeName);
        doc.setMimeType("application/pdf");
        doc.setPath("/" + nodeName);

        ShareLink sl = new ShareLink();
        sl.setId(UUID.randomUUID());
        sl.setToken(token);
        sl.setNode(doc);
        sl.setActive(true);
        sl.setCreatedBy("alice");
        sl.setPermissionLevel(SharePermission.VIEW);
        sl.setAccessCount(0);
        return sl;
    }
}
