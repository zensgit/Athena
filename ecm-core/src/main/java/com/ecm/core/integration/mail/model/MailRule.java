package com.ecm.core.integration.mail.model;

import com.ecm.core.entity.BaseEntity;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Type;

import java.util.UUID;

@Data
@Entity
@Table(name = "mail_rules")
@EqualsAndHashCode(callSuper = true)
public class MailRule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "account_id")
    private UUID accountId; // Optional: Apply to specific account

    @Column(nullable = false)
    private Integer priority = 100;

    // Filters
    @Column(name = "subject_filter")
    private String subjectFilter; // Regex or contains

    @Column(name = "from_filter")
    private String fromFilter;

    @Column(name = "body_filter")
    private String bodyFilter;

    // Actions
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type")
    private MailActionType actionType = MailActionType.ATTACHMENTS_ONLY;

    @Column(name = "assign_tag_id")
    private UUID assignTagId;

    @Column(name = "assign_folder_id")
    private UUID assignFolderId;

    public enum MailActionType {
        ATTACHMENTS_ONLY,
        METADATA_ONLY, // Create doc from body
        EVERYTHING
    }
}
