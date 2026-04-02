package com.ecm.core.service;

import com.ecm.core.entity.Activity;
import com.ecm.core.repository.ActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    @Mock private ActivityRepository activityRepository;

    private ActivityService service;

    @BeforeEach
    void setUp() {
        service = new ActivityService(activityRepository);
    }

    @Nested
    @DisplayName("postActivity")
    class PostActivity {

        @Test
        @DisplayName("creates activity with all fields")
        void createsActivity() {
            UUID nodeId = UUID.randomUUID();
            when(activityRepository.save(any())).thenAnswer(inv -> {
                Activity a = inv.getArgument(0);
                a.setId(UUID.randomUUID());
                return a;
            });

            Activity result = service.postActivity(
                "node.created", "alice", "finance", nodeId, "report.pdf",
                Map.of("action", "created")
            );

            assertNotNull(result.getId());
            assertEquals("node.created", result.getActivityType());
            assertEquals("alice", result.getUserId());
            assertEquals("finance", result.getSiteId());
            assertEquals(nodeId, result.getNodeId());
            assertEquals("report.pdf", result.getNodeName());
            assertEquals("created", result.getSummary().get("action"));
        }

        @Test
        @DisplayName("handles null summary gracefully")
        void handlesNullSummary() {
            when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Activity result = service.postActivity("node.deleted", "bob", null, null, null, null);

            assertNotNull(result.getSummary());
            assertTrue(result.getSummary().isEmpty());
        }
    }

    @Nested
    @DisplayName("feed queries")
    class FeedQueries {

        @Test
        @DisplayName("getUserFeed delegates to repository")
        void getUserFeed() {
            Page<Activity> page = new PageImpl<>(List.of(new Activity()));
            when(activityRepository.findByUserIdOrderByPostedAtDesc("alice", PageRequest.of(0, 20)))
                .thenReturn(page);

            Page<Activity> result = service.getUserFeed("alice", PageRequest.of(0, 20));

            assertEquals(1, result.getContent().size());
        }

        @Test
        @DisplayName("getSiteFeed delegates to repository")
        void getSiteFeed() {
            when(activityRepository.findBySiteIdOrderByPostedAtDesc(eq("finance"), any()))
                .thenReturn(new PageImpl<>(List.of()));

            assertEquals(0, service.getSiteFeed("finance", PageRequest.of(0, 10)).getContent().size());
        }

        @Test
        @DisplayName("getGlobalFeed delegates to repository")
        void getGlobalFeed() {
            when(activityRepository.findAllByOrderByPostedAtDesc(any()))
                .thenReturn(new PageImpl<>(List.of(new Activity(), new Activity())));

            assertEquals(2, service.getGlobalFeed(PageRequest.of(0, 20)).getContent().size());
        }

        @Test
        @DisplayName("getNodeFeed delegates to repository")
        void getNodeFeed() {
            UUID nodeId = UUID.randomUUID();
            when(activityRepository.findByNodeIdOrderByPostedAtDesc(eq(nodeId), any()))
                .thenReturn(new PageImpl<>(List.of(new Activity())));

            assertEquals(1, service.getNodeFeed(nodeId, PageRequest.of(0, 10)).getContent().size());
        }
    }
}
