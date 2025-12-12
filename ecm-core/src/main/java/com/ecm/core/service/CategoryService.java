package com.ecm.core.service;

import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.model.*;
import com.ecm.core.repository.*;
import com.ecm.core.exception.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class CategoryService {
    
    @Autowired
    private CategoryRepository categoryRepository;
    
    @Autowired
    private NodeRepository nodeRepository;
    
    @Autowired
    private SecurityService securityService;
    
    /**
     * 创建分类
     */
    public Category createCategory(String name, String description, String parentId) {
        Category category = new Category();
        category.setName(name);
        category.setDescription(description);
        category.setCreator(securityService.getCurrentUser());
        
        if (parentId != null) {
            Category parent = loadCategory(parentId);
            category.setParent(parent);
        }
        
        return categoryRepository.save(category);
    }
    
    /**
     * 获取分类树
     */
    public List<CategoryTreeNode> getCategoryTree() {
        List<Category> rootCategories = categoryRepository.findByParentIsNullAndActiveTrue();
        return buildCategoryTree(rootCategories);
    }
    
    private List<CategoryTreeNode> buildCategoryTree(List<Category> categories) {
        return categories.stream()
            .map(category -> {
                CategoryTreeNode node = new CategoryTreeNode();
                node.setId(category.getId().toString());
                node.setName(category.getName());
                node.setDescription(category.getDescription());
                node.setPath(category.getPath());
                node.setLevel(category.getLevel());
                
                // 递归构建子分类
                if (!category.getChildren().isEmpty()) {
                    node.setChildren(buildCategoryTree(category.getChildren()));
                }
                
                return node;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * 获取分类路径
     */
    public List<Category> getCategoryPath(String categoryId) {
        Category category = loadCategory(categoryId);
        
        List<Category> path = new ArrayList<>();
        Category current = category;
        
        while (current != null) {
            path.add(0, current);
            current = current.getParent();
        }
        
        return path;
    }
    
    /**
     * 为节点添加分类
     */
    public void addCategoryToNode(String nodeId, String categoryId) {
        Node node = loadActiveNode(nodeId);
        
        // 权限检查
        securityService.checkPermission(node, PermissionType.WRITE);
        
        Category category = loadCategory(categoryId);
        
        node.getCategories().add(category);
        nodeRepository.save(node);
    }
    
    /**
     * 从节点移除分类
     */
    public void removeCategoryFromNode(String nodeId, String categoryId) {
        Node node = loadActiveNode(nodeId);
        
        // 权限检查
        securityService.checkPermission(node, PermissionType.WRITE);
        
        Category category = loadCategory(categoryId);
        
        node.getCategories().remove(category);
        nodeRepository.save(node);
    }
    
    /**
     * 获取节点的所有分类
     */
    public Set<Category> getNodeCategories(String nodeId) {
        Node node = loadActiveNode(nodeId);
        
        // 权限检查
        securityService.checkPermission(node, PermissionType.READ);
        
        return node.getCategories();
    }
    
    /**
     * 根据分类查找节点
     */
    public List<Node> findNodesByCategory(String categoryId, boolean includeSubcategories) {
        Category category = loadCategory(categoryId);
        
        Set<Category> categories = new HashSet<>();
        categories.add(category);
        
        if (includeSubcategories) {
            categories.addAll(getAllSubcategories(category));
        }
        
        List<Node> nodes = nodeRepository.findByCategoriesInAndDeletedFalse(categories);
        
        // 过滤权限
        return nodes.stream()
            .filter(node -> securityService.hasPermission(node, PermissionType.READ))
            .collect(Collectors.toList());
    }
    
    private Set<Category> getAllSubcategories(Category category) {
        Set<Category> subcategories = new HashSet<>();
        
        for (Category child : category.getChildren()) {
            subcategories.add(child);
            subcategories.addAll(getAllSubcategories(child));
        }
        
        return subcategories;
    }
    
    /**
     * 更新分类
     */
    public Category updateCategory(String categoryId, String name, String description) {
        Category category = loadCategory(categoryId);
        
        category.setName(name);
        category.setDescription(description);
        
        return categoryRepository.save(category);
    }
    
    /**
     * 移动分类
     */
    public Category moveCategory(String categoryId, String newParentId) {
        Category category = loadCategory(categoryId);
        
        Category newParent = null;
        if (newParentId != null) {
            newParent = loadCategory(newParentId);
            
            // 检查循环引用
            if (isDescendantOf(newParent, category)) {
                throw new IllegalOperationException("Cannot move category to its descendant");
            }
        }
        
        category.setParent(newParent);
        return categoryRepository.save(category);
    }
    
    private boolean isDescendantOf(Category category, Category ancestor) {
        Category current = category;
        while (current != null) {
            if (current.equals(ancestor)) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }
    
    /**
     * 删除分类
     */
    public void deleteCategory(String categoryId, boolean deleteChildren) {
        Category category = loadCategory(categoryId);
        
        if (deleteChildren) {
            // 递归删除所有子分类
            deleteCategoryRecursive(category);
        } else {
            // 将子分类移到父分类下
            Category parent = category.getParent();
            for (Category child : category.getChildren()) {
                child.setParent(parent);
                categoryRepository.save(child);
            }
            
            // 从所有节点中移除该分类
            for (Node node : category.getNodes()) {
                node.getCategories().remove(category);
                nodeRepository.save(node);
            }
            
            categoryRepository.delete(category);
        }
    }
    
    private void deleteCategoryRecursive(Category category) {
        // 递归删除子分类
        for (Category child : new ArrayList<>(category.getChildren())) {
            deleteCategoryRecursive(child);
        }
        
        // 从所有节点中移除该分类
        for (Node node : category.getNodes()) {
            node.getCategories().remove(category);
            nodeRepository.save(node);
        }
        
        categoryRepository.delete(category);
    }
    
    /**
     * 获取分类统计
     */
    public CategoryStatistics getCategoryStatistics(String categoryId) {
        Category category = loadCategory(categoryId);
        
        CategoryStatistics stats = new CategoryStatistics();
        stats.setCategoryId(categoryId);
        stats.setCategoryName(category.getName());
        stats.setDirectNodeCount(category.getNodes().size());
        
        // 计算包含子分类的节点总数
        Set<Node> allNodes = new HashSet<>(category.getNodes());
        for (Category subcategory : getAllSubcategories(category)) {
            allNodes.addAll(subcategory.getNodes());
        }
        stats.setTotalNodeCount(allNodes.size());
        
        stats.setSubcategoryCount(getAllSubcategories(category).size());
        
        return stats;
    }

    private Category loadCategory(String categoryId) {
        try {
            UUID id = UUID.fromString(categoryId);
            return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));
        } catch (IllegalArgumentException ex) {
            throw new ResourceNotFoundException("Invalid category id: " + categoryId, ex);
        }
    }

    private Node loadActiveNode(String nodeId) {
        try {
            UUID id = UUID.fromString(nodeId);
            return nodeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NodeNotFoundException("Node not found: " + nodeId));
        } catch (IllegalArgumentException ex) {
            throw new NodeNotFoundException("Invalid node id: " + nodeId, ex);
        }
    }
    
    // 内部类
    public static class CategoryTreeNode {
        private String id;
        private String name;
        private String description;
        private String path;
        private Integer level;
        private List<CategoryTreeNode> children = new ArrayList<>();
        
        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        
        public Integer getLevel() { return level; }
        public void setLevel(Integer level) { this.level = level; }
        
        public List<CategoryTreeNode> getChildren() { return children; }
        public void setChildren(List<CategoryTreeNode> children) { this.children = children; }
    }
    
    public static class CategoryStatistics {
        private String categoryId;
        private String categoryName;
        private Integer directNodeCount;
        private Integer totalNodeCount;
        private Integer subcategoryCount;
        
        // Getters and setters
        public String getCategoryId() { return categoryId; }
        public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
        
        public String getCategoryName() { return categoryName; }
        public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
        
        public Integer getDirectNodeCount() { return directNodeCount; }
        public void setDirectNodeCount(Integer directNodeCount) { this.directNodeCount = directNodeCount; }
        
        public Integer getTotalNodeCount() { return totalNodeCount; }
        public void setTotalNodeCount(Integer totalNodeCount) { this.totalNodeCount = totalNodeCount; }
        
        public Integer getSubcategoryCount() { return subcategoryCount; }
        public void setSubcategoryCount(Integer subcategoryCount) { this.subcategoryCount = subcategoryCount; }
    }
}
