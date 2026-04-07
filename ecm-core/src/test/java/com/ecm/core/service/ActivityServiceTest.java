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
import org.springframework.data.domain.Pageable;
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
    @Mock private FollowingService followingService;
    @Mock private TenantWorkspaceScopeService tenantWorkspaceScopeService;

    private ActivityService service;

    @BeforeEach
    void setUp() {
        service = new ActivityService(activityRepository, followingService, tenantWorkspaceScopeService);
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
            when(activityRepository.findByUserIdOrderByPostedAtDesc("alice", Pageable.unpaged()))
                .thenReturn(page);
            when(tenantWorkspaceScopeService.resolveCurrentTenantRootPath()).thenReturn(null);
            when(tenantWorkspaceScopeService.isActivityVisible(any(), eq((String) null))).thenReturn(true);

            Page<Activity> result = service.getUserFeed("alice", PageRequest.of(0, 20));

            assertEquals(1, result.getContent().size());
        }

        @Test
        @DisplayName("getSiteFeed delegates to repository")
        void getSiteFeed() {
            when(activityRepository.findBySiteIdOrderByPostedAtDesc(eq("finance"), eq(Pageable.unpaged())))
                .thenReturn(new PageImpl<>(List.of()));

            assertEquals(0, service.getSiteFeed("finance", PageRequest.of(0, 10)).getContent().size());
        }

        @Test
        @DisplayName("getFollowingFeed returns empty page when user follows nothing")
        void getFollowingFeedReturnsEmptyWhenNoSubscriptions() {
            PageRequest pageable = PageRequest.of(0, 10);
            when(followingService.getFollowingTargets("alice"))
                .thenReturn(new FollowingService.FollowingTargets(List.of(), List.of(), List.of()));

            Page<Activity> result = service.getFollowingFeed("alice", pageable);

            assertTrue(result.isEmpty());
            verify(activityRepository, never()).findFollowingFeed(anyBoolean(), anyList(), anyBoolean(), anyList(), anyBoolean(), anyList(), any());
        }

        @Test
        @DisplayName("getFollowingFeed delegates to repository with followed targets")
        void getFollowingFeedDelegatesToRepository() {
            UUID nodeId = UUID.randomUUID();
            PageRequest pageable = PageRequest.of(0, 10);
            Page<Activity> page = new PageImpl<>(List.of(new Activity()));
            when(followingService.getFollowingTargets("alice"))
                .thenReturn(new FollowingService.FollowingTargets(List.of("bob"), List.of("finance"), List.of(nodeId)));
            when(activityRepository.findFollowingFeed(
                eq(true),
                eq(List.of("bob")),
                eq(true),
                eq(List.of("finance")),
                eq(true),
                eq(List.of(nodeId)),
                eq(Pageable.unpaged())
            )).thenReturn(page);
            when(tenantWorkspaceScopeService.resolveCurrentTenantRootPath()).thenReturn(null);
            when(tenantWorkspaceScopeService.isActivityVisible(any(), eq((String) null))).thenReturn(true);

            Page<Activity> result = service.getFollowingFeed("alice", pageable);

            assertEquals(1, result.getContent().size());
        }

        @Test
        @DisplayName("getGlobalFeed delegates to repository")
        void getGlobalFeed() {
            when(activityRepository.findAllByOrderByPostedAtDesc(eq(Pageable.unpaged())))
                .thenReturn(new PageImpl<>(List.of(new Activity(), new Activity())));
            when(tenantWorkspaceScopeService.resolveCurrentTenantRootPath()).thenReturn(null);
            when(tenantWorkspaceScopeService.isActivityVisible(any(), eq((String) null))).thenReturn(true);

            assertEquals(2, service.getGlobalFeed(PageRequest.of(0, 20)).getContent().size());
        }

        @Test
        @DisplayName("getNodeFeed delegates to repository")
        void getNodeFeed() {
            UUID nodeId = UUID.randomUUID();
            when(activityRepository.findByNodeIdOrderByPostedAtDesc(eq(nodeId), eq(Pageable.unpaged())))
                .thenReturn(new PageImpl<>(List.of(new Activity())));
            when(tenantWorkspaceScopeService.resolveCurrentTenantRootPath()).thenReturn(null);
            when(tenantWorkspaceScopeService.isActivityVisible(any(), eq((String) null))).thenReturn(true);

            assertEquals(1, service.getNodeFeed(nodeId, PageRequest.of(0, 10)).getContent().size());
        }

        @Test
        @DisplayName("scoped tenant filters cross-workspace activities from global feed")
        void getGlobalFeedFiltersCrossWorkspaceActivities() {
            Activity visible = new Activity();
            visible.setId(UUID.randomUUID());
            Activity hidden = new Activity();
            hidden.setId(UUID.randomUUID());
            when(activityRepository.findAllByOrderByPostedAtDesc(Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(visible, hidden)));
            when(tenantWorkspaceScopeService.resolveCurrentTenantRootPath()).thenReturn("/Acme Workspace [acme]");
            when(tenantWorkspaceScopeService.isActivityVisible(visible, "/Acme Workspace [acme]")).thenReturn(true);
            when(tenantWorkspaceScopeService.isActivityVisible(hidden, "/Acme Workspace [acme]")).thenReturn(false);

            Page<Activity> result = service.getGlobalFeed(PageRequest.of(0, 20));

            assertEquals(1, result.getTotalElements());
            assertEquals(visible.getId(), result.getContent().get(0).getId());
        }
    }
}
