package com.ecm.core.integration.email.notify;

import com.ecm.core.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Entity
@Table(
    name = "email_template",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_email_template_key_locale",
            columnNames = {"template_key", "locale"}
        )
    },
    indexes = {
        @Index(name = "idx_email_template_key", columnList = "template_key")
    }
)
@EqualsAndHashCode(callSuper = true)
public class EmailTemplate extends BaseEntity {

    @Column(name = "template_key", nullable = false, length = 200)
    private String templateKey;

    @Column(name = "locale", nullable = false, length = 20)
    private String locale = "default";

    @Column(name = "subject_template", nullable = false, length = 500)
    private String subjectTemplate;

    @Column(name = "body_template", nullable = false, columnDefinition = "text")
    private String bodyTemplate;

    @Column(name = "html_body", nullable = false)
    private boolean htmlBody = false;

    @Column(name = "description", length = 500)
    private String description;
}
