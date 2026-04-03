package com.ecm.core.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

@Data
@Entity
@Table(name = "discussion_replies", indexes = {
    @Index(name = "idx_dr_topic_id", columnList = "topic_id"),
    @Index(name = "idx_dr_parent_reply_id", columnList = "parent_reply_id")
})
@EqualsAndHashCode(callSuper = true, exclude = {"topic"})
public class DiscussionReply extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private DiscussionTopic topic;

    @Column(name = "parent_reply_id")
    private UUID parentReplyId;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;
}
