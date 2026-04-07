package com.ecm.core.service;

import com.ecm.core.entity.DiscussionReply;
import com.ecm.core.entity.DiscussionTopic;
import com.ecm.core.entity.DiscussionTopic.TopicStatus;
import com.ecm.core.entity.Site;
import com.ecm.core.exception.ResourceNotFoundException;
import com.ecm.core.repository.DiscussionReplyRepository;
import com.ecm.core.repository.DiscussionTopicRepository;
import com.ecm.core.repository.SiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiscussionServiceTest {

    @Mock private DiscussionTopicRepository topicRepo;
    @Mock private DiscussionReplyRepository replyRepo;
    @Mock private SiteRepository siteRepository;
    @Mock private SecurityService securityService;
    @Mock private ActivityEventListener activityEventListener;
    @Mock private TenantWorkspaceScopeService tenantWorkspaceScopeService;

    private DiscussionService service;

    @BeforeEach
    void setUp() {
        service = new DiscussionService(
            topicRepo,
            replyRepo,
            siteRepository,
            securityService,
            activityEventListener,
            tenantWorkspaceScopeService
        );
    }

    @Nested
    @DisplayName("topics")
    class Topics {

        @Test
        @DisplayName("creates topic with title and siteId")
        void createsTopic() {
            stubVisibleSite("finance");
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(topicRepo.save(any())).thenAnswer(inv -> {
                DiscussionTopic t = inv.getArgument(0);
                t.setId(UUID.randomUUID());
                return t;
            });

            DiscussionTopic t = service.createTopic("finance", "Q1 Planning", "Let's discuss Q1", List.of("planning"));

            assertEquals("finance", t.getSiteId());
            assertEquals("Q1 Planning", t.getTitle());
            assertEquals(TopicStatus.OPEN, t.getStatus());
            assertEquals(List.of("planning"), t.getTags());
            verify(activityEventListener).postSiteActivity(
                eq("discussion.topic.created"),
                eq("alice"),
                eq("finance"),
                anyMap()
            );
        }

        @Test
        @DisplayName("rejects blank title")
        void rejectsBlankTitle() {
            assertThrows(IllegalArgumentException.class,
                () -> service.createTopic("finance", "", null, null));
        }

        @Test
        @DisplayName("author can update topic fields")
        void authorUpdatesTopic() {
            DiscussionTopic topic = new DiscussionTopic();
            topic.setId(UUID.randomUUID());
            topic.setTitle("Old");
            topic.setStatus(TopicStatus.OPEN);
            topic.setCreatedBy("alice");
            topic.setSiteId("finance");
            stubVisibleSite("finance");
            when(topicRepo.findById(topic.getId())).thenReturn(Optional.of(topic));
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(topicRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            DiscussionTopic updated = service.updateTopic(topic.getId(), "New Title", null, TopicStatus.PINNED);

            assertEquals("New Title", updated.getTitle());
            assertEquals(TopicStatus.PINNED, updated.getStatus());
            verify(activityEventListener).postSiteActivity(
                eq("discussion.topic.updated"),
                eq("alice"),
                eq("finance"),
                anyMap()
            );
        }

        @Test
        @DisplayName("non-author non-admin cannot update topic")
        void nonAuthorCannotUpdate() {
            DiscussionTopic topic = new DiscussionTopic();
            topic.setId(UUID.randomUUID());
            topic.setCreatedBy("alice");
            topic.setSiteId("finance");
            stubVisibleSite("finance");
            when(topicRepo.findById(topic.getId())).thenReturn(Optional.of(topic));
            when(securityService.getCurrentUser()).thenReturn("bob");
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);

            assertThrows(SecurityException.class,
                () -> service.updateTopic(topic.getId(), "Hacked", null, null));
        }

        @Test
        @DisplayName("admin can update any topic")
        void adminCanUpdate() {
            DiscussionTopic topic = new DiscussionTopic();
            topic.setId(UUID.randomUUID());
            topic.setTitle("Old");
            topic.setCreatedBy("alice");
            topic.setSiteId("finance");
            stubVisibleSite("finance");
            when(topicRepo.findById(topic.getId())).thenReturn(Optional.of(topic));
            when(securityService.getCurrentUser()).thenReturn("admin");
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
            when(topicRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertDoesNotThrow(() -> service.updateTopic(topic.getId(), "Admin Edit", null, null));
        }

        @Test
        @DisplayName("update rejects blank title after trim")
        void updateRejectsBlankTitle() {
            DiscussionTopic topic = new DiscussionTopic();
            topic.setId(UUID.randomUUID());
            topic.setCreatedBy("alice");
            topic.setSiteId("finance");
            stubVisibleSite("finance");
            when(topicRepo.findById(topic.getId())).thenReturn(Optional.of(topic));
            when(securityService.getCurrentUser()).thenReturn("alice");

            assertThrows(IllegalArgumentException.class,
                () -> service.updateTopic(topic.getId(), "   ", null, null));
        }

        @Test
        @DisplayName("author can delete topic")
        void authorDeletesTopic() {
            DiscussionTopic topic = new DiscussionTopic();
            topic.setId(UUID.randomUUID());
            topic.setCreatedBy("alice");
            topic.setSiteId("finance");
            topic.setTitle("Q1 Planning");
            stubVisibleSite("finance");
            when(topicRepo.findById(topic.getId())).thenReturn(Optional.of(topic));
            when(securityService.getCurrentUser()).thenReturn("alice");

            service.deleteTopic(topic.getId());

            verify(activityEventListener).postSiteActivity(
                eq("discussion.topic.deleted"),
                eq("alice"),
                eq("finance"),
                anyMap()
            );
            verify(topicRepo).delete(topic);
        }

        @Test
        @DisplayName("non-author non-admin cannot delete topic")
        void nonAuthorCannotDelete() {
            DiscussionTopic topic = new DiscussionTopic();
            topic.setId(UUID.randomUUID());
            topic.setCreatedBy("alice");
            topic.setSiteId("finance");
            stubVisibleSite("finance");
            when(topicRepo.findById(topic.getId())).thenReturn(Optional.of(topic));
            when(securityService.getCurrentUser()).thenReturn("bob");
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);

            assertThrows(SecurityException.class,
                () -> service.deleteTopic(topic.getId()));
            verify(topicRepo, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("replies")
    class Replies {

        @Test
        @DisplayName("creates reply on open topic")
        void createsReply() {
            UUID topicId = UUID.randomUUID();
            DiscussionTopic topic = new DiscussionTopic();
            topic.setId(topicId);
            topic.setStatus(TopicStatus.OPEN);
            topic.setSiteId("finance");
            topic.setTitle("Q1 Planning");
            stubVisibleSite("finance");
            when(topicRepo.findById(topicId)).thenReturn(Optional.of(topic));
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(replyRepo.save(any())).thenAnswer(inv -> {
                DiscussionReply r = inv.getArgument(0);
                r.setId(UUID.randomUUID());
                return r;
            });

            DiscussionReply reply = service.createReply(topicId, "Good idea!", null);

            assertEquals("Good idea!", reply.getContent());
            assertEquals(topicId, reply.getTopic().getId());
            verify(activityEventListener).postSiteActivity(
                eq("discussion.reply.created"),
                eq("alice"),
                eq("finance"),
                anyMap()
            );
        }

        @Test
        @DisplayName("rejects reply on closed topic")
        void rejectsReplyOnClosed() {
            UUID topicId = UUID.randomUUID();
            DiscussionTopic topic = new DiscussionTopic();
            topic.setId(topicId);
            topic.setStatus(TopicStatus.CLOSED);
            topic.setSiteId("finance");
            stubVisibleSite("finance");
            when(topicRepo.findById(topicId)).thenReturn(Optional.of(topic));

            assertThrows(IllegalStateException.class,
                () -> service.createReply(topicId, "test", null));
        }

        @Test
        @DisplayName("rejects blank reply content")
        void rejectsBlankContent() {
            assertThrows(IllegalArgumentException.class,
                () -> service.createReply(UUID.randomUUID(), "", null));
        }

        @Test
        @DisplayName("author can edit own reply")
        void authorEditsReply() {
            DiscussionReply reply = new DiscussionReply();
            reply.setId(UUID.randomUUID());
            reply.setCreatedBy("alice");
            reply.setContent("old");
            DiscussionTopic topic = new DiscussionTopic();
            topic.setId(UUID.randomUUID());
            topic.setSiteId("finance");
            topic.setTitle("Q1 Planning");
            reply.setTopic(topic);
            stubVisibleSite("finance");
            when(replyRepo.findById(reply.getId())).thenReturn(Optional.of(reply));
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(replyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            DiscussionReply updated = service.updateReply(reply.getId(), "updated content");

            assertEquals("updated content", updated.getContent());
            verify(activityEventListener).postSiteActivity(
                eq("discussion.reply.updated"),
                eq("alice"),
                eq("finance"),
                anyMap()
            );
        }

        @Test
        @DisplayName("non-author non-admin cannot edit reply")
        void nonAuthorCannotEdit() {
            DiscussionReply reply = new DiscussionReply();
            reply.setId(UUID.randomUUID());
            reply.setCreatedBy("alice");
            DiscussionTopic topic = new DiscussionTopic();
            topic.setId(UUID.randomUUID());
            topic.setSiteId("finance");
            topic.setTitle("Q1 Planning");
            reply.setTopic(topic);
            stubVisibleSite("finance");
            when(replyRepo.findById(reply.getId())).thenReturn(Optional.of(reply));
            when(securityService.getCurrentUser()).thenReturn("bob");
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);

            assertThrows(SecurityException.class,
                () -> service.updateReply(reply.getId(), "hacked"));
        }

        @Test
        @DisplayName("author can delete own reply and posts activity")
        void authorDeletesReply() {
            DiscussionReply reply = new DiscussionReply();
            reply.setId(UUID.randomUUID());
            reply.setCreatedBy("alice");
            DiscussionTopic topic = new DiscussionTopic();
            topic.setId(UUID.randomUUID());
            topic.setSiteId("finance");
            topic.setTitle("Q1 Planning");
            reply.setTopic(topic);
            stubVisibleSite("finance");
            when(replyRepo.findById(reply.getId())).thenReturn(Optional.of(reply));
            when(securityService.getCurrentUser()).thenReturn("alice");

            service.deleteReply(reply.getId());

            verify(activityEventListener).postSiteActivity(
                eq("discussion.reply.deleted"),
                eq("alice"),
                eq("finance"),
                anyMap()
            );
            verify(replyRepo).delete(reply);
        }

        @Test
        @DisplayName("getTopic hides topics outside current tenant workspace")
        void getTopicHidesForeignTenantTopic() {
            DiscussionTopic topic = new DiscussionTopic();
            topic.setId(UUID.randomUUID());
            topic.setSiteId("finance");
            when(topicRepo.findById(topic.getId())).thenReturn(Optional.of(topic));
            when(siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse("finance")).thenReturn(Optional.of(site("finance")));
            when(tenantWorkspaceScopeService.resolveCurrentTenantRootPath()).thenReturn("/Tenant Workspace");
            when(tenantWorkspaceScopeService.isSiteVisible("finance", "/Tenant Workspace")).thenReturn(false);

            assertThrows(ResourceNotFoundException.class, () -> service.getTopic(topic.getId()));
        }
    }

    private void stubVisibleSite(String siteId) {
        when(siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse(siteId)).thenReturn(Optional.of(site(siteId)));
    }

    private Site site(String siteId) {
        Site site = new Site();
        site.setId(UUID.randomUUID());
        site.setSiteId(siteId);
        site.setTitle(siteId.toUpperCase());
        site.setVisibility(Site.SiteVisibility.PUBLIC);
        site.setStatus(Site.SiteStatus.ACTIVE);
        return site;
    }
}
