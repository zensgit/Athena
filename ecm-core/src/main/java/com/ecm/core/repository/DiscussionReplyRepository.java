package com.ecm.core.repository;

import com.ecm.core.entity.DiscussionReply;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DiscussionReplyRepository extends JpaRepository<DiscussionReply, UUID> {
    Page<DiscussionReply> findByTopicIdOrderByCreatedDateAsc(UUID topicId, Pageable pageable);
    long countByTopicId(UUID topicId);
}
