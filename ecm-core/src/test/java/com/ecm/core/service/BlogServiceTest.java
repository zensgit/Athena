package com.ecm.core.service;

import com.ecm.core.entity.BlogPost;
import com.ecm.core.entity.BlogPost.BlogStatus;
import com.ecm.core.repository.BlogPostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlogServiceTest {

    @Mock private BlogPostRepository blogRepo;
    @Mock private SecurityService securityService;
    @Mock private ActivityEventListener activityEventListener;

    private BlogService service;

    @BeforeEach
    void setUp() {
        service = new BlogService(blogRepo, securityService, activityEventListener);
    }

    @Nested
    @DisplayName("createPost")
    class Create {

        @Test
        @DisplayName("creates draft post with title and siteId")
        void createsDraft() {
            when(blogRepo.save(any())).thenAnswer(inv -> {
                BlogPost p = inv.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });

            BlogPost p = service.createPost("finance", "Q1 Report", "Our Q1 results", List.of("report"));

            assertEquals("finance", p.getSiteId());
            assertEquals("Q1 Report", p.getTitle());
            assertEquals(BlogStatus.DRAFT, p.getStatus());
            assertNull(p.getPublishedDate());
        }

        @Test
        @DisplayName("rejects blank title")
        void rejectsBlank() {
            assertThrows(IllegalArgumentException.class,
                () -> service.createPost("finance", "  ", null, null));
        }
    }

    @Nested
    @DisplayName("publish / unpublish")
    class Lifecycle {

        @Test
        @DisplayName("publish sets PUBLISHED status and publishedDate")
        void publishes() {
            BlogPost post = draft("alice");
            when(blogRepo.findById(post.getId())).thenReturn(Optional.of(post));
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(blogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            BlogPost result = service.publish(post.getId());

            assertEquals(BlogStatus.PUBLISHED, result.getStatus());
            assertNotNull(result.getPublishedDate());
            verify(activityEventListener).postSiteActivity(eq("blog.published"), eq("alice"), eq("finance"), anyMap());
        }

        @Test
        @DisplayName("publish rejects already published")
        void rejectsDoublePublish() {
            BlogPost post = draft("alice");
            post.setStatus(BlogStatus.PUBLISHED);
            when(blogRepo.findById(post.getId())).thenReturn(Optional.of(post));
            when(securityService.getCurrentUser()).thenReturn("alice");

            assertThrows(IllegalStateException.class, () -> service.publish(post.getId()));
        }

        @Test
        @DisplayName("unpublish reverts to draft")
        void unpublishes() {
            BlogPost post = draft("alice");
            post.setStatus(BlogStatus.PUBLISHED);
            when(blogRepo.findById(post.getId())).thenReturn(Optional.of(post));
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(blogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            BlogPost result = service.unpublish(post.getId());

            assertEquals(BlogStatus.DRAFT, result.getStatus());
            assertNull(result.getPublishedDate());
        }

        @Test
        @DisplayName("non-author non-admin cannot publish")
        void nonAuthorCannotPublish() {
            BlogPost post = draft("alice");
            when(blogRepo.findById(post.getId())).thenReturn(Optional.of(post));
            when(securityService.getCurrentUser()).thenReturn("bob");
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);

            assertThrows(SecurityException.class, () -> service.publish(post.getId()));
        }
    }

    @Nested
    @DisplayName("update / delete permissions")
    class Permissions {

        @Test
        @DisplayName("author can update")
        void authorUpdates() {
            BlogPost post = draft("alice");
            when(blogRepo.findById(post.getId())).thenReturn(Optional.of(post));
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(blogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            BlogPost result = service.updatePost(post.getId(), "New Title", null, null);
            assertEquals("New Title", result.getTitle());
        }

        @Test
        @DisplayName("non-author non-admin cannot update")
        void nonAuthorCannotUpdate() {
            BlogPost post = draft("alice");
            when(blogRepo.findById(post.getId())).thenReturn(Optional.of(post));
            when(securityService.getCurrentUser()).thenReturn("bob");
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);

            assertThrows(SecurityException.class,
                () -> service.updatePost(post.getId(), "Hacked", null, null));
        }

        @Test
        @DisplayName("author can delete")
        void authorDeletes() {
            BlogPost post = draft("alice");
            when(blogRepo.findById(post.getId())).thenReturn(Optional.of(post));
            when(securityService.getCurrentUser()).thenReturn("alice");

            service.deletePost(post.getId());
            verify(blogRepo).delete(post);
        }

        @Test
        @DisplayName("update rejects blank title")
        void rejectsBlankTitle() {
            BlogPost post = draft("alice");
            when(blogRepo.findById(post.getId())).thenReturn(Optional.of(post));
            when(securityService.getCurrentUser()).thenReturn("alice");

            assertThrows(IllegalArgumentException.class,
                () -> service.updatePost(post.getId(), "  ", null, null));
        }
    }

    private BlogPost draft(String author) {
        BlogPost p = new BlogPost();
        p.setId(UUID.randomUUID());
        p.setSiteId("finance");
        p.setTitle("Draft");
        p.setStatus(BlogStatus.DRAFT);
        p.setCreatedBy(author);
        return p;
    }
}
