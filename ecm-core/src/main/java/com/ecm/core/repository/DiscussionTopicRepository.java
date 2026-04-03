package com.ecm.core.repository;

import com.ecm.core.entity.DiscussionTopic;
import com.ecm.core.entity.DiscussionTopic.TopicStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DiscussionTopicRepository extends JpaRepository<DiscussionTopic, UUID> {
    Page<DiscussionTopic> findBySiteIdOrderByCreatedDateDesc(String siteId, Pageable pageable);
    Page<DiscussionTopic> findBySiteIdAndStatusOrderByCreatedDateDesc(String siteId, TopicStatus status, Pageable pageable);
    long countBySiteId(String siteId);
}
