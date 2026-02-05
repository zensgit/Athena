package com.ecm.core.entity;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Entity
@Table(name = "permission_template_versions")
@EqualsAndHashCode(callSuper = true)
public class PermissionTemplateVersion extends BaseEntity {

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(nullable = false)
    private String name;

    private String description;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private List<PermissionTemplate.PermissionTemplateEntry> entries = new ArrayList<>();
}
