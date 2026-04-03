package com.ecm.core.service;

import com.ecm.core.entity.DiscussionReply;
import com.ecm.core.entity.DiscussionTopic;
import com.ecm.core.entity.DiscussionTopic.TopicStatus;
import com.ecm.core.repository.DiscussionReplyRepository;
import com.ecm.core.repository.DiscussionTopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class DiscussionService {

    private final DiscussionTopicRepository topicRepo;
    private final DiscussionReplyRepository replyRepo;
    private final SecurityService securityService;
    private final ActivityEventListener activityEventListener;

    // ------------------------------------------------------------------ topics

    @Transactional
    public DiscussionTopic createTopic(String siteId, String title, String content, java.util.List<String> tags) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Topic title is required");
        }
        DiscussionTopic topic = new DiscussionTopic();
        topic.setSiteId(siteId);
        topic.setTitle(title.trim());
        topic.setContent(content);
        if (tags != null) topic.setTags(tags);
        DiscussionTopic saved = topicRepo.save(topic);
        activityEventListener.postSiteActivity(
            "discussion.topic.created",
            securityService.getCurrentUser(),
            siteId,
            Map.of("topicId", saved.getId().toString(), "title", saved.getTitle())
        );
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<DiscussionTopic> listTopics(String siteId, TopicStatus status, Pageable pageable) {
        if (status != null) {
            return topicRepo.findBySiteIdAndStatusOrderByCreatedDateDesc(siteId, status, pageable);
        }
        return topicRepo.findBySiteIdOrderByCreatedDateDesc(siteId, pageable);
    }

    @Transactional(readOnly = true)
    public DiscussionTopic getTopic(UUID topicId) {
        return topicRepo.findById(topicId)
            .orElseThrow(() -> new NoSuchElementException("Topic not found: " + topicId));
    }

    @Transactional
    public DiscussionTopic updateTopic(UUID topicId, String title, String content, TopicStatus status) {
        DiscussionTopic topic = getTopic(topicId);
        requireTopicAuthorOrAdmin(topic);
        if (title != null) {
            String trimmed = title.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("Topic title must not be blank");
            }
            topic.setTitle(trimmed);
        }
        if (content != null) topic.setContent(content);
        if (status != null) topic.setStatus(status);
        DiscussionTopic saved = topicRepo.save(topic);
        activityEventListener.postSiteActivity(
            "discussion.topic.updated",
            securityService.getCurrentUser(),
            topic.getSiteId(),
            Map.of("topicId", saved.getId().toString(), "title", saved.getTitle(), "status", saved.getStatus().name())
        );
        return saved;
    }

    @Transactional
    public void deleteTopic(UUID topicId) {
        DiscussionTopic topic = getTopic(topicId);
        requireTopicAuthorOrAdmin(topic);
        activityEventListener.postSiteActivity(
            "discussion.topic.deleted",
            securityService.getCurrentUser(),
            topic.getSiteId(),
            Map.of("topicId", topic.getId().toString(), "title", topic.getTitle())
        );
        topicRepo.delete(topic);
    }

    private void requireTopicAuthorOrAdmin(DiscussionTopic topic) {
        String currentUser = securityService.getCurrentUser();
        if (!currentUser.equals(topic.getCreatedBy()) && !securityService.hasRole("ROLE_ADMIN")) {
            throw new SecurityException("Only topic author or admin can modify this topic");
        }
    }

    // ------------------------------------------------------------------ replies

    @Transactional
    public DiscussionReply createReply(UUID topicId, String content, UUID parentReplyId) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Reply content is required");
        }
        DiscussionTopic topic = getTopic(topicId);
        if (topic.getStatus() == TopicStatus.CLOSED) {
            throw new IllegalStateException("Cannot reply to a closed topic");
        }
        DiscussionReply reply = new DiscussionReply();
        reply.setTopic(topic);
        reply.setContent(content.trim());
        reply.setParentReplyId(parentReplyId);
        DiscussionReply saved = replyRepo.save(reply);
        activityEventListener.postSiteActivity(
            "discussion.reply.created",
            securityService.getCurrentUser(),
            topic.getSiteId(),
            Map.of("topicId", topic.getId().toString(), "replyId", saved.getId().toString(), "title", topic.getTitle())
        );
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<DiscussionReply> listReplies(UUID topicId, Pageable pageable) {
        return replyRepo.findByTopicIdOrderByCreatedDateAsc(topicId, pageable);
    }

    @Transactional
    public DiscussionReply updateReply(UUID replyId, String content) {
        DiscussionReply reply = replyRepo.findById(replyId)
            .orElseThrow(() -> new NoSuchElementException("Reply not found: " + replyId));
        String currentUser = securityService.getCurrentUser();
        if (!reply.getCreatedBy().equals(currentUser) && !securityService.hasRole("ROLE_ADMIN")) {
            throw new SecurityException("Only reply author or admin can edit");
        }
        reply.setContent(content.trim());
        DiscussionReply saved = replyRepo.save(reply);
        activityEventListener.postSiteActivity(
            "discussion.reply.updated",
            securityService.getCurrentUser(),
            saved.getTopic().getSiteId(),
            Map.of("topicId", saved.getTopic().getId().toString(), "replyId", saved.getId().toString(), "title", saved.getTopic().getTitle())
        );
        return saved;
    }

    @Transactional
    public void deleteReply(UUID replyId) {
        DiscussionReply reply = replyRepo.findById(replyId)
            .orElseThrow(() -> new NoSuchElementException("Reply not found: " + replyId));
        String currentUser = securityService.getCurrentUser();
        if (!reply.getCreatedBy().equals(currentUser) && !securityService.hasRole("ROLE_ADMIN")) {
            throw new SecurityException("Only reply author or admin can delete");
        }
        activityEventListener.postSiteActivity(
            "discussion.reply.deleted",
            securityService.getCurrentUser(),
            reply.getTopic().getSiteId(),
            Map.of("topicId", reply.getTopic().getId().toString(), "replyId", reply.getId().toString(), "title", reply.getTopic().getTitle())
        );
        replyRepo.delete(reply);
    }
}
