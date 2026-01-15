package com.ecm.core.integration.mail.model;

import com.ecm.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

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

    @Column(name = "folder")
    private String folder = "INBOX";

    // Filters
    @Column(name = "subject_filter")
    private String subjectFilter; // Regex or contains

    @Column(name = "from_filter")
    private String fromFilter;

    @Column(name = "to_filter")
    private String toFilter;

    @Column(name = "body_filter")
    private String bodyFilter;

    @Column(name = "attachment_filename_include")
    private String attachmentFilenameInclude;

    @Column(name = "attachment_filename_exclude")
    private String attachmentFilenameExclude;

    @Column(name = "max_age_days")
    private Integer maxAgeDays;

    @Column(name = "include_inline_attachments")
    private Boolean includeInlineAttachments = false;

    // Actions
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type")
    private MailActionType actionType = MailActionType.ATTACHMENTS_ONLY;

    @Enumerated(EnumType.STRING)
    @Column(name = "mail_action")
    private MailPostAction mailAction = MailPostAction.MARK_READ;

    @Column(name = "mail_action_param")
    private String mailActionParam;

    @Column(name = "assign_tag_id")
    private UUID assignTagId;

    @Column(name = "assign_folder_id")
    private UUID assignFolderId;

    public enum MailActionType {
        ATTACHMENTS_ONLY,
        METADATA_ONLY, // Create doc from body
        EVERYTHING
    }

    public enum MailPostAction {
        NONE,
        MARK_READ,
        DELETE,
        MOVE,
        FLAG,
        TAG
    }
}
