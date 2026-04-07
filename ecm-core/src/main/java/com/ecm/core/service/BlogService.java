package com.ecm.core.service;

import com.ecm.core.entity.BlogPost;
import com.ecm.core.entity.BlogPost.BlogStatus;
import com.ecm.core.entity.Site;
import com.ecm.core.exception.ResourceNotFoundException;
import com.ecm.core.repository.BlogPostRepository;
import com.ecm.core.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class BlogService {

    private final BlogPostRepository blogRepo;
    private final SiteRepository siteRepository;
    private final SecurityService securityService;
    private final ActivityEventListener activityEventListener;
    private final TenantWorkspaceScopeService tenantWorkspaceScopeService;

    @Transactional
    public BlogPost createPost(String siteId, String title, String content, List<String> tags) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Blog post title is required");
        }
        String visibleSiteId = requireVisibleSiteId(siteId);
        BlogPost post = new BlogPost();
        post.setSiteId(visibleSiteId);
        post.setTitle(title.trim());
        post.setContent(content);
        post.setStatus(BlogStatus.DRAFT);
        if (tags != null) post.setTags(tags);
        BlogPost saved = blogRepo.save(post);
        log.info("Blog post created: {} in site {}", saved.getId(), visibleSiteId);
        activityEventListener.postSiteActivity(
            "blog.created", securityService.getCurrentUser(), visibleSiteId,
            Map.of("postId", saved.getId().toString(), "title", saved.getTitle(), "status", "DRAFT")
        );
        return saved;
    }

    @Transactional
    public BlogPost publish(UUID postId) {
        BlogPost post = getPost(postId);
        requireAuthorOrAdmin(post);
        if (post.getStatus() == BlogStatus.PUBLISHED) {
            throw new IllegalStateException("Post is already published");
        }
        post.setStatus(BlogStatus.PUBLISHED);
        post.setPublishedDate(LocalDateTime.now());
        BlogPost saved = blogRepo.save(post);
        activityEventListener.postSiteActivity(
            "blog.published", securityService.getCurrentUser(), post.getSiteId(),
            Map.of("postId", saved.getId().toString(), "title", saved.getTitle())
        );
        return saved;
    }

    @Transactional
    public BlogPost unpublish(UUID postId) {
        BlogPost post = getPost(postId);
        requireAuthorOrAdmin(post);
        post.setStatus(BlogStatus.DRAFT);
        post.setPublishedDate(null);
        BlogPost saved = blogRepo.save(post);
        activityEventListener.postSiteActivity(
            "blog.unpublished", securityService.getCurrentUser(), post.getSiteId(),
            Map.of("postId", saved.getId().toString(), "title", saved.getTitle())
        );
        return saved;
    }

    @Transactional
    public BlogPost updatePost(UUID postId, String title, String content, List<String> tags) {
        BlogPost post = getPost(postId);
        requireAuthorOrAdmin(post);
        if (title != null) {
            String trimmed = title.trim();
            if (trimmed.isEmpty()) throw new IllegalArgumentException("Blog post title must not be blank");
            post.setTitle(trimmed);
        }
        if (content != null) post.setContent(content);
        if (tags != null) post.setTags(tags);
        BlogPost saved = blogRepo.save(post);
        activityEventListener.postSiteActivity(
            "blog.updated", securityService.getCurrentUser(), post.getSiteId(),
            Map.of("postId", saved.getId().toString(), "title", saved.getTitle())
        );
        return saved;
    }

    @Transactional
    public void deletePost(UUID postId) {
        BlogPost post = getPost(postId);
        requireAuthorOrAdmin(post);
        blogRepo.delete(post);
    }

    @Transactional(readOnly = true)
    public BlogPost getPost(UUID postId) {
        BlogPost post = blogRepo.findById(postId)
            .orElseThrow(() -> new NoSuchElementException("Blog post not found: " + postId));
        requireVisibleSiteId(post.getSiteId());
        return post;
    }

    @Transactional(readOnly = true)
    public Page<BlogPost> listPosts(String siteId, BlogStatus status, Pageable pageable) {
        String visibleSiteId = requireVisibleSiteId(siteId);
        if (status != null) {
            return blogRepo.findBySiteIdAndStatusOrderByPublishedDateDesc(visibleSiteId, status, pageable);
        }
        return blogRepo.findBySiteIdOrderByCreatedDateDesc(visibleSiteId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<BlogPost> listDrafts(String siteId, Pageable pageable) {
        String visibleSiteId = requireVisibleSiteId(siteId);
        return blogRepo.findBySiteIdAndStatusOrderByCreatedDateDesc(visibleSiteId, BlogStatus.DRAFT, pageable);
    }

    private void requireAuthorOrAdmin(BlogPost post) {
        String currentUser = securityService.getCurrentUser();
        if (!currentUser.equals(post.getCreatedBy()) && !securityService.hasRole("ROLE_ADMIN")) {
            throw new SecurityException("Only post author or admin can modify this post");
        }
    }

    private String requireVisibleSiteId(String siteId) {
        String normalizedSiteId = siteId != null ? siteId.trim().toLowerCase(Locale.ROOT) : "";
        Site site = siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse(normalizedSiteId)
            .orElseThrow(() -> new ResourceNotFoundException("Site not found: " + normalizedSiteId));
        String tenantRootPath = tenantWorkspaceScopeService.resolveCurrentTenantRootPath();
        if (tenantRootPath != null && !tenantWorkspaceScopeService.isSiteVisible(site.getSiteId(), tenantRootPath)) {
            throw new ResourceNotFoundException("Site not found: " + normalizedSiteId);
        }
        return site.getSiteId();
    }
}
