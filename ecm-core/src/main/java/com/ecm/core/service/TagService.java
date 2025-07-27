package com.ecm.core.service;

import com.ecm.core.model.*;
import com.ecm.core.repository.*;
import com.ecm.core.exception.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class TagService {
    
    @Autowired
    private TagRepository tagRepository;
    
    @Autowired
    private NodeRepository nodeRepository;
    
    @Autowired
    private SecurityService securityService;
    
    /**
     * 创建标签
     */
    public Tag createTag(String name, String description, String color) {
        // 检查标签是否已存在
        if (tagRepository.existsByName(name)) {
            throw new DuplicateResourceException("Tag already exists: " + name);
        }
        
        Tag tag = new Tag();
        tag.setName(name.toLowerCase().trim());
        tag.setDescription(description);
        tag.setColor(color != null ? color : "#1976d2");
        tag.setCreator(securityService.getCurrentUser());
        
        return tagRepository.save(tag);
    }
    
    /**
     * 获取所有标签
     */
    public List<Tag> getAllTags() {
        return tagRepository.findAllByOrderByUsageCountDesc();
    }
    
    /**
     * 搜索标签
     */
    public List<Tag> searchTags(String query) {
        return tagRepository.findByNameContainingIgnoreCase(query);
    }
    
    /**
     * 获取热门标签
     */
    public List<Tag> getPopularTags(int limit) {
        return tagRepository.findTopByOrderByUsageCountDesc(limit);
    }
    
    /**
     * 为节点添加标签
     */
    public void addTagToNode(String nodeId, String tagName) {
        Node node = nodeRepository.findByIdAndDeletedFalse(nodeId)
            .orElseThrow(() -> new NodeNotFoundException("Node not found: " + nodeId));
        
        // 权限检查
        securityService.checkPermission(node, Permission.WRITE);
        
        // 获取或创建标签
        Tag tag = tagRepository.findByName(tagName.toLowerCase().trim())
            .orElseGet(() -> createTag(tagName, null, null));
        
        // 添加标签
        if (node.getTags().add(tag)) {
            tag.incrementUsage();
            nodeRepository.save(node);
            tagRepository.save(tag);
        }
    }
    
    /**
     * 为节点批量添加标签
     */
    public void addTagsToNode(String nodeId, List<String> tagNames) {
        Node node = nodeRepository.findByIdAndDeletedFalse(nodeId)
            .orElseThrow(() -> new NodeNotFoundException("Node not found: " + nodeId));
        
        // 权限检查
        securityService.checkPermission(node, Permission.WRITE);
        
        for (String tagName : tagNames) {
            Tag tag = tagRepository.findByName(tagName.toLowerCase().trim())
                .orElseGet(() -> createTag(tagName, null, null));
            
            if (node.getTags().add(tag)) {
                tag.incrementUsage();
                tagRepository.save(tag);
            }
        }
        
        nodeRepository.save(node);
    }
    
    /**
     * 从节点移除标签
     */
    public void removeTagFromNode(String nodeId, String tagName) {
        Node node = nodeRepository.findByIdAndDeletedFalse(nodeId)
            .orElseThrow(() -> new NodeNotFoundException("Node not found: " + nodeId));
        
        // 权限检查
        securityService.checkPermission(node, Permission.WRITE);
        
        Tag tag = tagRepository.findByName(tagName.toLowerCase().trim())
            .orElseThrow(() -> new ResourceNotFoundException("Tag not found: " + tagName));
        
        if (node.getTags().remove(tag)) {
            tag.decrementUsage();
            nodeRepository.save(node);
            tagRepository.save(tag);
            
            // 如果标签没有被使用，可以选择删除
            if (tag.getUsageCount() == 0) {
                // tagRepository.delete(tag);
            }
        }
    }
    
    /**
     * 获取节点的所有标签
     */
    public Set<Tag> getNodeTags(String nodeId) {
        Node node = nodeRepository.findByIdAndDeletedFalse(nodeId)
            .orElseThrow(() -> new NodeNotFoundException("Node not found: " + nodeId));
        
        // 权限检查
        securityService.checkPermission(node, Permission.READ);
        
        return node.getTags();
    }
    
    /**
     * 根据标签查找节点
     */
    public Page<Node> findNodesByTag(String tagName, Pageable pageable) {
        Tag tag = tagRepository.findByName(tagName.toLowerCase().trim())
            .orElseThrow(() -> new ResourceNotFoundException("Tag not found: " + tagName));
        
        Page<Node> nodes = nodeRepository.findByTagsContainingAndDeletedFalse(tag, pageable);
        
        // 过滤权限
        List<Node> filteredNodes = nodes.getContent().stream()
            .filter(node -> securityService.hasPermission(node, Permission.READ))
            .collect(Collectors.toList());
        
        return new PageImpl<>(filteredNodes, pageable, nodes.getTotalElements());
    }
    
    /**
     * 根据多个标签查找节点（AND条件）
     */
    public List<Node> findNodesByTags(List<String> tagNames) {
        List<Tag> tags = tagNames.stream()
            .map(name -> tagRepository.findByName(name.toLowerCase().trim()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
        
        if (tags.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Node> nodes = nodeRepository.findByAllTags(tags, tags.size());
        
        // 过滤权限
        return nodes.stream()
            .filter(node -> securityService.hasPermission(node, Permission.READ))
            .collect(Collectors.toList());
    }
    
    /**
     * 更新标签
     */
    public Tag updateTag(String tagId, String name, String description, String color) {
        Tag tag = tagRepository.findById(tagId)
            .orElseThrow(() -> new ResourceNotFoundException("Tag not found: " + tagId));
        
        // 检查新名称是否已存在
        if (!tag.getName().equals(name) && tagRepository.existsByName(name)) {
            throw new DuplicateResourceException("Tag already exists: " + name);
        }
        
        tag.setName(name.toLowerCase().trim());
        tag.setDescription(description);
        tag.setColor(color);
        
        return tagRepository.save(tag);
    }
    
    /**
     * 删除标签
     */
    public void deleteTag(String tagId) {
        Tag tag = tagRepository.findById(tagId)
            .orElseThrow(() -> new ResourceNotFoundException("Tag not found: " + tagId));
        
        // 从所有节点中移除该标签
        for (Node node : tag.getNodes()) {
            node.getTags().remove(tag);
            nodeRepository.save(node);
        }
        
        tagRepository.delete(tag);
    }
    
    /**
     * 合并标签
     */
    public void mergeTags(String sourceTagId, String targetTagId) {
        Tag sourceTag = tagRepository.findById(sourceTagId)
            .orElseThrow(() -> new ResourceNotFoundException("Source tag not found: " + sourceTagId));
        
        Tag targetTag = tagRepository.findById(targetTagId)
            .orElseThrow(() -> new ResourceNotFoundException("Target tag not found: " + targetTagId));
        
        // 将源标签的所有节点添加到目标标签
        for (Node node : sourceTag.getNodes()) {
            node.getTags().remove(sourceTag);
            node.getTags().add(targetTag);
            nodeRepository.save(node);
        }
        
        // 更新使用计数
        targetTag.setUsageCount(targetTag.getUsageCount() + sourceTag.getUsageCount());
        tagRepository.save(targetTag);
        
        // 删除源标签
        tagRepository.delete(sourceTag);
    }
    
    /**
     * 获取标签云数据
     */
    public List<TagCloudItem> getTagCloud() {
        List<Tag> tags = tagRepository.findAll();
        
        return tags.stream()
            .map(tag -> new TagCloudItem(
                tag.getName(),
                tag.getUsageCount(),
                tag.getColor()
            ))
            .sorted((a, b) -> b.getCount().compareTo(a.getCount()))
            .collect(Collectors.toList());
    }
    
    // 内部类
    public static class TagCloudItem {
        private String name;
        private Integer count;
        private String color;
        
        public TagCloudItem(String name, Integer count, String color) {
            this.name = name;
            this.count = count;
            this.color = color;
        }
        
        // Getters
        public String getName() { return name; }
        public Integer getCount() { return count; }
        public String getColor() { return color; }
    }
}