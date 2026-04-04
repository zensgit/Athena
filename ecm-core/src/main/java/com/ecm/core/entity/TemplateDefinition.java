package com.ecm.core.entity;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Type;

import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "template_definitions", indexes = {
    @Index(name = "idx_td_template_path", columnList = "template_path"),
    @Index(name = "idx_td_active", columnList = "active")
})
@EqualsAndHashCode(callSuper = true)
public class TemplateDefinition extends BaseEntity {

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Column(name = "template_path", nullable = false, unique = true, length = 255)
    private String templatePath;

    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TemplateEngine engine = TemplateEngine.FREEMARKER;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private List<String> tags = new ArrayList<>();

    @Column(nullable = false)
    private boolean active = true;

    public enum TemplateEngine {
        FREEMARKER
    }
}
