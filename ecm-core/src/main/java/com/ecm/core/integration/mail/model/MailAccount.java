package com.ecm.core.integration.mail.model;

import com.ecm.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@Table(name = "mail_accounts")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = "password")
public class MailAccount extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private Integer port;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String password; // Should be encrypted in prod

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SecurityType security = SecurityType.SSL;

    @Column(name = "is_enabled")
    private boolean enabled = true;

    @Column(name = "poll_interval_minutes")
    private Integer pollIntervalMinutes = 10;

    public enum SecurityType {
        NONE, SSL, STARTTLS
    }
}
