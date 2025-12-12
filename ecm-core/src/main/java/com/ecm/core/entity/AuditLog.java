package com.ecm.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "node_id")
    private UUID nodeId;

    @Column(name = "node_name")
    private String nodeName;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    @Column(name = "details", columnDefinition = "text")
    private String details;
    
    @Column(name = "client_ip")
    private String clientIp;
    
    @Column(name = "user_agent")
    private String userAgent;
    
    // Additional data in JSON
    @Column(name = "metadata", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata;
}
