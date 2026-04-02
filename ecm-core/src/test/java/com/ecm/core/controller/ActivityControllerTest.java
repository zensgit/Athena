package com.ecm.core.controller;

import com.ecm.core.entity.Activity;
import com.ecm.core.service.ActivityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ActivityControllerTest {

    private MockMvc mockMvc;
    @Mock private ActivityService activityService;

    @BeforeEach
    void setUp() {
        ActivityController controller = new ActivityController(activityService);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .setMessageConverters(converter)
            .setControllerAdvice(new RestExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("GET /activities returns global feed with activity fields")
    void globalFeedReturnsActivities() throws Exception {
        Activity a = activity("node.created", "alice", "report.pdf");
        when(activityService.getGlobalFeed(any())).thenReturn(new PageImpl<>(List.of(a), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/activities").param("page", "0").param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].activityType").value("node.created"))
            .andExpect(jsonPath("$.content[0].userId").value("alice"))
            .andExpect(jsonPath("$.content[0].nodeName").value("report.pdf"));
    }

    @Test
    @DisplayName("GET /activities/users/{userId} returns user feed")
    void userFeedReturnsActivities() throws Exception {
        when(activityService.getUserFeed(eq("alice"), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/activities/users/alice").param("page", "0").param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("GET /activities/sites/{siteId} returns site feed")
    void siteFeedReturnsActivities() throws Exception {
        Activity a = activity("node.updated", "bob", "invoice.pdf");
        a.setSiteId("finance");
        when(activityService.getSiteFeed(eq("finance"), any())).thenReturn(new PageImpl<>(List.of(a), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/activities/sites/finance").param("page", "0").param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].siteId").value("finance"));
    }

    @Test
    @DisplayName("GET /activities/nodes/{nodeId} returns node feed")
    void nodeFeedReturnsActivities() throws Exception {
        UUID nodeId = UUID.randomUUID();
        when(activityService.getNodeFeed(eq(nodeId), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/activities/nodes/{nodeId}", nodeId).param("page", "0").param("size", "20"))
            .andExpect(status().isOk());
    }

    private Activity activity(String type, String user, String nodeName) {
        Activity a = new Activity();
        a.setId(UUID.randomUUID());
        a.setActivityType(type);
        a.setUserId(user);
        a.setNodeName(nodeName);
        a.setNodeId(UUID.randomUUID());
        a.setSummary(Map.of("action", type.split("\\.")[1]));
        a.setPostedAt(LocalDateTime.now());
        return a;
    }
}
