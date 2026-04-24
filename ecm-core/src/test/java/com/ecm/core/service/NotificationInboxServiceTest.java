package com.ecm.core.service;

import com.ecm.core.entity.Activity;
import com.ecm.core.entity.Notification;
import com.ecm.core.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationInboxServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private FollowingService followingService;
    @Mock private SecurityService securityService;
    @Mock private TenantWorkspaceScopeService tenantWorkspaceScopeService;

    private NotificationInboxService service;

    @BeforeEach
    void setUp() {
        service = new NotificationInboxService(notificationRepository, followingService, securityService, tenantWorkspaceScopeService);
    }

    @Nested
    @DisplayName("routeActivityToFollowers")
    class Routing {

        @Test
        @DisplayName("creates notifications for followers of the activity user")
        void routesToUserFollowers() {
            Activity activity = activity("node.created", "alice", null, null);
            when(tenantWorkspaceScopeService.isActivityVisible(activity)).thenReturn(true);
            when(followingService.getFollowersOf("USER", "alice")).thenReturn(List.of("bob", "charlie"));

            service.routeActivityToFollowers(activity);

            verify(notificationRepository, times(2)).save(any(Notification.class));
        }

        @Test
        @DisplayName("creates notifications for followers of the activity site")
        void routesToSiteFollowers() {
            Activity activity = activity("node.created", "alice", "finance", null);
            when(tenantWorkspaceScopeService.isActivityVisible(activity)).thenReturn(true);
            when(followingService.getFollowersOf("USER", "alice")).thenReturn(List.of());
            when(followingService.getFollowersOf("SITE", "finance")).thenReturn(List.of("bob"));

            service.routeActivityToFollowers(activity);

            verify(notificationRepository, times(1)).save(any(Notification.class));
        }

        @Test
        @DisplayName("does not notify the actor about their own activity")
        void excludesActor() {
            Activity activity = activity("node.created", "alice", null, null);
            when(tenantWorkspaceScopeService.isActivityVisible(activity)).thenReturn(true);
            when(followingService.getFollowersOf("USER", "alice")).thenReturn(List.of("alice", "bob"));

            service.routeActivityToFollowers(activity);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository, times(1)).save(captor.capture());
            assertEquals("bob", captor.getValue().getUserId());
        }

        @Test
        @DisplayName("deduplicates across user/site/node followers")
        void deduplicatesRecipients() {
            UUID nodeId = UUID.randomUUID();
            Activity activity = activity("node.updated", "alice", "finance", nodeId);
            when(tenantWorkspaceScopeService.isActivityVisible(activity)).thenReturn(true);
            when(followingService.getFollowersOf("USER", "alice")).thenReturn(List.of("bob"));
            when(followingService.getFollowersOf("SITE", "finance")).thenReturn(List.of("bob", "charlie"));
            when(followingService.getFollowersOf("NODE", nodeId.toString())).thenReturn(List.of("bob"));

            service.routeActivityToFollowers(activity);

            // bob appears in all 3 follow lists but should get only 1 notification
            verify(notificationRepository, times(2)).save(any(Notification.class)); // bob + charlie
        }

        @Test
        @DisplayName("does nothing when no followers")
        void noFollowersNoNotifications() {
            Activity activity = activity("node.deleted", "alice", null, null);
            when(tenantWorkspaceScopeService.isActivityVisible(activity)).thenReturn(true);
            when(followingService.getFollowersOf("USER", "alice")).thenReturn(List.of());

            service.routeActivityToFollowers(activity);

            verify(notificationRepository, never()).save(any());
        }

        @Test
        @DisplayName("does not route notifications for out-of-scope tenant activity")
        void skipsOutOfScopeActivity() {
            Activity activity = activity("node.created", "alice", "finance", UUID.randomUUID());
            when(tenantWorkspaceScopeService.isActivityVisible(activity)).thenReturn(false);

            service.routeActivityToFollowers(activity);

            verifyNoInteractions(followingService);
            verify(notificationRepository, never()).save(any());
        }

        @Test
        @DisplayName("creates a direct notification without follower resolution")
        void createsDirectNotification() {
            Activity activity = activity("rm.report_preset.delivery.failed", "system", null, UUID.randomUUID());
            Notification saved = notification(activity, "alice");
            when(notificationRepository.save(any(Notification.class))).thenReturn(saved);

            Notification result = service.createDirectNotification("alice", activity);

            assertSame(saved, result);
            verifyNoInteractions(followingService);
            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            assertEquals("alice", captor.getValue().getUserId());
            assertSame(activity, captor.getValue().getActivity());
        }
    }

    @Nested
    @DisplayName("inbox operations")
    class InboxOps {

        @Test
        @DisplayName("getUnreadCount delegates to repository")
        void unreadCount() {
            when(securityService.getCurrentUser()).thenReturn("bob");
            when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);
            when(notificationRepository.countByUserIdAndReadFalse("bob")).thenReturn(5L);

            assertEquals(5L, service.getUnreadCount());
        }

        @Test
        @DisplayName("unread count filters notifications outside current tenant")
        void unreadCountFiltersByTenantScope() {
            when(securityService.getCurrentUser()).thenReturn("bob");
            when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(true);
            Notification visible = notification(activity("node.created", "alice", "finance", UUID.randomUUID()), "bob");
            Notification hidden = notification(activity("node.created", "alice", "hr", UUID.randomUUID()), "bob");
            when(notificationRepository.findByUserIdAndReadOrderByCreatedAtDesc("bob", false, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(visible, hidden)));
            when(tenantWorkspaceScopeService.isActivityVisible(visible.getActivity())).thenReturn(true);
            when(tenantWorkspaceScopeService.isActivityVisible(hidden.getActivity())).thenReturn(false);

            assertEquals(1L, service.getUnreadCount());
        }

        @Test
        @DisplayName("markRead sets read=true and readAt")
        void markRead() {
            UUID id = UUID.randomUUID();
            Notification n = notification(activity("node.created", "alice", "finance", UUID.randomUUID()), "bob");
            n.setId(id);
            n.setRead(false);
            when(securityService.getCurrentUser()).thenReturn("bob");
            when(notificationRepository.findById(id)).thenReturn(Optional.of(n));
            when(tenantWorkspaceScopeService.isActivityVisible(n.getActivity())).thenReturn(true);
            when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Notification result = service.markRead(id);

            assertTrue(result.isRead());
            assertNotNull(result.getReadAt());
        }

        @Test
        @DisplayName("markRead rejects other user's notification")
        void markReadRejectsOtherUser() {
            UUID id = UUID.randomUUID();
            Notification n = new Notification();
            n.setId(id);
            n.setUserId("charlie");
            when(securityService.getCurrentUser()).thenReturn("bob");
            when(notificationRepository.findById(id)).thenReturn(Optional.of(n));

            assertThrows(SecurityException.class, () -> service.markRead(id));
        }

        @Test
        @DisplayName("markRead hides notification outside current tenant scope")
        void markReadHidesOutOfScopeNotification() {
            UUID id = UUID.randomUUID();
            Notification n = notification(activity("node.created", "alice", "finance", UUID.randomUUID()), "bob");
            n.setId(id);
            when(securityService.getCurrentUser()).thenReturn("bob");
            when(notificationRepository.findById(id)).thenReturn(Optional.of(n));
            when(tenantWorkspaceScopeService.isActivityVisible(n.getActivity())).thenReturn(false);

            assertThrows(NoSuchElementException.class, () -> service.markRead(id));
        }

        @Test
        @DisplayName("markAllRead delegates to repository")
        void markAllRead() {
            when(securityService.getCurrentUser()).thenReturn("bob");
            when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);
            when(notificationRepository.markAllRead(eq("bob"), any())).thenReturn(3);

            assertEquals(3, service.markAllRead());
        }

        @Test
        @DisplayName("markAllRead only marks visible notifications in scoped tenant")
        void markAllReadFiltersByTenantScope() {
            when(securityService.getCurrentUser()).thenReturn("bob");
            when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(true);
            Notification visible = notification(activity("node.created", "alice", "finance", UUID.randomUUID()), "bob");
            Notification hidden = notification(activity("node.created", "alice", "hr", UUID.randomUUID()), "bob");
            when(notificationRepository.findByUserIdAndReadOrderByCreatedAtDesc("bob", false, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(visible, hidden)));
            when(tenantWorkspaceScopeService.isActivityVisible(visible.getActivity())).thenReturn(true);
            when(tenantWorkspaceScopeService.isActivityVisible(hidden.getActivity())).thenReturn(false);
            when(notificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            assertEquals(1, service.markAllRead());
            verify(notificationRepository, times(1)).save(any(Notification.class));
        }

        @Test
        @DisplayName("deleteNotification removes from repository")
        void deleteNotification() {
            UUID id = UUID.randomUUID();
            Notification n = notification(activity("node.created", "alice", "finance", UUID.randomUUID()), "bob");
            n.setId(id);
            when(securityService.getCurrentUser()).thenReturn("bob");
            when(notificationRepository.findById(id)).thenReturn(Optional.of(n));
            when(tenantWorkspaceScopeService.isActivityVisible(n.getActivity())).thenReturn(true);

            service.deleteNotification(id);

            verify(notificationRepository).delete(n);
        }
    }

    private Notification notification(Activity activity, String userId) {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setUserId(userId);
        notification.setActivity(activity);
        return notification;
    }

    private Activity activity(String type, String userId, String siteId, UUID nodeId) {
        Activity a = new Activity();
        a.setId(UUID.randomUUID());
        a.setActivityType(type);
        a.setUserId(userId);
        a.setSiteId(siteId);
        a.setNodeId(nodeId);
        a.setPostedAt(LocalDateTime.now());
        return a;
    }
}
