package com.ecm.core.controller;

import com.ecm.core.entity.Activity;
import com.ecm.core.entity.Notification;
import com.ecm.core.service.NotificationInboxService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationInboxService inboxService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);

        mockMvc = MockMvcBuilders
            .standaloneSetup(new NotificationController(inboxService))
            .setMessageConverters(converter)
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .setControllerAdvice(new RestExceptionHandler())
            .build();
    }

    // ------------------------------------------------------------------ helpers

    private Notification mockNotification(UUID notifId) {
        Activity activity = Mockito.mock(Activity.class);
        Mockito.when(activity.getActivityType()).thenReturn("DOCUMENT_CREATED");
        Mockito.when(activity.getUserId()).thenReturn("alice");
        Mockito.when(activity.getSiteId()).thenReturn("site-1");
        Mockito.when(activity.getNodeId()).thenReturn(UUID.randomUUID());
        Mockito.when(activity.getNodeName()).thenReturn("contract.pdf");
        Mockito.when(activity.getSummary()).thenReturn(Map.of("key", "value"));

        Notification n = Mockito.mock(Notification.class);
        Mockito.when(n.getId()).thenReturn(notifId);
        Mockito.when(n.getActivity()).thenReturn(activity);
        Mockito.when(n.isRead()).thenReturn(false);
        Mockito.when(n.getReadAt()).thenReturn(null);
        Mockito.when(n.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 4, 26, 9, 0));

        return n;
    }

    // ------------------------------------------------------------------ tests

    @Test
    @DisplayName("GET /api/v1/notifications returns inbox page with notification")
    void getInboxReturnsPageWithNotification() throws Exception {
        UUID notifId = UUID.randomUUID();
        Notification n = mockNotification(notifId);
        Mockito.when(inboxService.getInbox(any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(n), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/notifications"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(notifId.toString()))
            .andExpect(jsonPath("$.content[0].activityType").value("DOCUMENT_CREATED"));
    }

    @Test
    @DisplayName("GET /api/v1/notifications/unread returns unread notifications page")
    void getUnreadReturnsUnreadNotificationsPage() throws Exception {
        UUID notifId = UUID.randomUUID();
        Notification n = mockNotification(notifId);
        Mockito.when(inboxService.getUnread(any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(n), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/notifications/unread"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(notifId.toString()))
            .andExpect(jsonPath("$.content[0].activityType").value("DOCUMENT_CREATED"));
    }

    @Test
    @DisplayName("GET /api/v1/notifications/unread-count returns count map")
    void getUnreadCountReturnsCountMap() throws Exception {
        Mockito.when(inboxService.getUnreadCount()).thenReturn(5L);

        mockMvc.perform(get("/api/v1/notifications/unread-count"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(5));
    }

    @Test
    @DisplayName("PATCH /api/v1/notifications/{id}/read marks notification as read")
    void markReadReturnsUpdatedNotification() throws Exception {
        UUID notifId = UUID.randomUUID();
        Notification n = mockNotification(notifId);
        Mockito.when(inboxService.markRead(notifId)).thenReturn(n);

        mockMvc.perform(patch("/api/v1/notifications/{id}/read", notifId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(notifId.toString()));
    }

    @Test
    @DisplayName("POST /api/v1/notifications/mark-all-read returns marked count")
    void markAllReadReturnsMarkedCount() throws Exception {
        Mockito.when(inboxService.markAllRead()).thenReturn(7);

        mockMvc.perform(post("/api/v1/notifications/mark-all-read"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.marked").value(7));
    }

    @Test
    @DisplayName("DELETE /api/v1/notifications/{id} returns 204 no content")
    void deleteNotificationReturnsNoContent() throws Exception {
        UUID notifId = UUID.randomUUID();
        Mockito.doNothing().when(inboxService).deleteNotification(notifId);

        mockMvc.perform(delete("/api/v1/notifications/{id}", notifId))
            .andExpect(status().isNoContent());
    }
}
