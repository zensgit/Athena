package com.ecm.core.service;

import com.ecm.core.entity.BlogPost;
import com.ecm.core.entity.BlogPost.BlogStatus;
import com.ecm.core.repository.BlogPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class BlogService {

    private final BlogPostRepository blogRepo;
    private final SecurityService securityService;
    private final ActivityEventListener activityEventListener;

    @Transactional
    public BlogPost createPost(String siteId, String title, String content, List<String> tags) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Blog post title is required");
        }
        BlogPost post = new BlogPost();
        post.setSiteId(siteId);
        post.setTitle(title.trim());
        post.setContent(content);
        post.setStatus(BlogStatus.DRAFT);
        if (tags != null) post.setTags(tags);
        BlogPost saved = blogRepo.save(post);
        log.info("Blog post created: {} in site {}", saved.getId(), siteId);
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
        return blogRepo.save(post);
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
        return blogRepo.save(post);
    }

    @Transactional
    public void deletePost(UUID postId) {
        BlogPost post = getPost(postId);
        requireAuthorOrAdmin(post);
        blogRepo.delete(post);
    }

    @Transactional(readOnly = true)
    public BlogPost getPost(UUID postId) {
        return blogRepo.findById(postId)
            .orElseThrow(() -> new NoSuchElementException("Blog post not found: " + postId));
    }

    @Transactional(readOnly = true)
    public Page<BlogPost> listPosts(String siteId, BlogStatus status, Pageable pageable) {
        if (status != null) {
            return blogRepo.findBySiteIdAndStatusOrderByPublishedDateDesc(siteId, status, pageable);
        }
        return blogRepo.findBySiteIdOrderByCreatedDateDesc(siteId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<BlogPost> listDrafts(String siteId, Pageable pageable) {
        return blogRepo.findBySiteIdAndStatusOrderByPublishedDateDesc(siteId, BlogStatus.DRAFT, pageable);
    }

    private void requireAuthorOrAdmin(BlogPost post) {
        String currentUser = securityService.getCurrentUser();
        if (!currentUser.equals(post.getCreatedBy()) && !securityService.hasRole("ROLE_ADMIN")) {
            throw new SecurityException("Only post author or admin can modify this post");
        }
    }
}
