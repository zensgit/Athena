package com.ecm.core.repository;

import com.ecm.core.entity.BlogPost;
import com.ecm.core.entity.BlogPost.BlogStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BlogPostRepository extends JpaRepository<BlogPost, UUID> {
    Page<BlogPost> findBySiteIdOrderByCreatedDateDesc(String siteId, Pageable pageable);
    Page<BlogPost> findBySiteIdAndStatusOrderByPublishedDateDesc(String siteId, BlogStatus status, Pageable pageable);
    Page<BlogPost> findBySiteIdAndStatusOrderByCreatedDateDesc(String siteId, BlogStatus status, Pageable pageable);
    Page<BlogPost> findBySiteIdAndCreatedByOrderByCreatedDateDesc(String siteId, String author, Pageable pageable);
    long countBySiteIdAndStatus(String siteId, BlogStatus status);
}
