package com.ecm.core.integration.mail.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(
    name = "mail_processed_messages",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "ux_mail_processed_account_folder_uid",
            columnNames = {"account_id", "folder", "uid"}
        )
    }
)
public class ProcessedMail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "rule_id")
    private UUID ruleId;

    @Column(name = "folder", nullable = false)
    private String folder;

    @Column(name = "uid", nullable = false)
    private String uid;

    @Column(name = "subject")
    private String subject;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status = Status.PROCESSED;

    @Column(name = "error_message")
    private String errorMessage;

    public enum Status {
        PROCESSED,
        ERROR
    }
}
