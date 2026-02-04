package com.ecm.core.entity;

import com.ecm.core.entity.Permission.AuthorityType;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * Reusable permission template applied to nodes.
 */
@Data
@Entity
@Table(name = "permission_templates")
@EqualsAndHashCode(callSuper = true)
public class PermissionTemplate extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private List<PermissionTemplateEntry> entries = new ArrayList<>();

    @Data
    public static class PermissionTemplateEntry {
        private String authority;
        private AuthorityType authorityType;
        private PermissionSet permissionSet;
    }
}
