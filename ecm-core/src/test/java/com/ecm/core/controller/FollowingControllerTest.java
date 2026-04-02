package com.ecm.core.controller;

import com.ecm.core.entity.FollowTargetType;
import com.ecm.core.service.FollowingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FollowingControllerTest {

    @Mock private FollowingService followingService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        FollowingController controller = new FollowingController(followingService);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(converter)
            .setControllerAdvice(new RestExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("GET /followings lists current user subscriptions")
    void listReturnsSubscriptions() throws Exception {
        when(followingService.listCurrentUserSubscriptions()).thenReturn(List.of(
            dto(FollowTargetType.SITE, "engineering")
        ));

        mockMvc.perform(get("/api/v1/followings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].targetType").value("SITE"))
            .andExpect(jsonPath("$[0].targetId").value("engineering"));
    }

    @Test
    @DisplayName("GET /followings/check returns follow state")
    void checkReturnsFollowState() throws Exception {
        when(followingService.isFollowing(FollowTargetType.SITE, "engineering")).thenReturn(true);

        mockMvc.perform(get("/api/v1/followings/check")
                .param("targetType", "SITE")
                .param("targetId", "engineering"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").value(true));
    }

    @Test
    @DisplayName("POST /followings creates subscription")
    void followCreatesSubscription() throws Exception {
        when(followingService.follow(FollowTargetType.SITE, "engineering")).thenReturn(dto(FollowTargetType.SITE, "engineering"));

        mockMvc.perform(post("/api/v1/followings")
                .contentType("application/json")
                .content("""
                    {
                      "targetType": "SITE",
                      "targetId": "engineering"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.targetType").value("SITE"))
            .andExpect(jsonPath("$.targetId").value("engineering"));
    }

    @Test
    @DisplayName("DELETE /followings/{targetType}/{targetId} removes subscription")
    void unfollowRemovesSubscription() throws Exception {
        mockMvc.perform(delete("/api/v1/followings/{targetType}/{targetId}", "SITE", "engineering"))
            .andExpect(status().isNoContent());
    }

    private FollowingService.FollowSubscriptionDto dto(FollowTargetType targetType, String targetId) {
        return new FollowingService.FollowSubscriptionDto(
            UUID.randomUUID(),
            "alice",
            targetType,
            targetId,
            LocalDateTime.now()
        );
    }
}
