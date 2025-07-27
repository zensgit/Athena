package com.ecm.core.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@Table(name = "folders")
@DiscriminatorValue("FOLDER")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Folder extends Node {
    
    @Column(name = "folder_type")
    @Enumerated(EnumType.STRING)
    private FolderType folderType = FolderType.GENERAL;
    
    @Column(name = "max_items")
    private Integer maxItems;
    
    @Column(name = "allowed_types")
    private String allowedTypes;
    
    @Column(name = "auto_file_naming")
    private boolean autoFileNaming = false;
    
    @Column(name = "naming_pattern")
    private String namingPattern;
    
    @Override
    public NodeType getNodeType() {
        return NodeType.FOLDER;
    }
    
    public boolean canContainType(String mimeType) {
        if (allowedTypes == null || allowedTypes.isEmpty()) {
            return true;
        }
        String[] types = allowedTypes.split(",");
        for (String type : types) {
            if (mimeType.matches(type.trim())) {
                return true;
            }
        }
        return false;
    }
}

enum FolderType {
    GENERAL,
    WORKSPACE,
    PROJECT,
    ARCHIVE,
    SYSTEM,
    TEMP
}