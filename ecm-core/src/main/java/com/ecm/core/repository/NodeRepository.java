package com.ecm.core.repository;

import com.ecm.core.entity.Node;
import com.ecm.core.entity.NodeStatus;
import com.ecm.core.entity.NodeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NodeRepository extends JpaRepository<Node, UUID>, JpaSpecificationExecutor<Node> {
    
    Optional<Node> findByPath(String path);
    
    Optional<Node> findByIdAndDeletedFalse(UUID id);
    
    List<Node> findByParentIdAndDeletedFalse(UUID parentId);
    
    Page<Node> findByParentIdAndDeletedFalse(UUID parentId, Pageable pageable);
    
    @Query("SELECT n FROM Node n WHERE n.parent.id = :parentId AND n.name = :name AND n.deleted = false")
    Optional<Node> findByParentIdAndName(@Param("parentId") UUID parentId, @Param("name") String name);
    
    @Query("SELECT n FROM Node n WHERE n.path LIKE :pathPrefix% AND n.deleted = false")
    List<Node> findByPathPrefix(@Param("pathPrefix") String pathPrefix);
    
    @Query("SELECT COUNT(n) FROM Node n WHERE n.parent.id = :parentId AND n.deleted = false")
    long countByParentId(@Param("parentId") UUID parentId);
    
    @Query("SELECT n FROM Node n JOIN n.tags t WHERE t.name IN :tagNames AND n.deleted = false")
    List<Node> findByTagNames(@Param("tagNames") List<String> tagNames);
    
    @Query("SELECT n FROM Node n JOIN n.categories c WHERE c.id IN :categoryIds AND n.deleted = false")
    List<Node> findByCategoryIds(@Param("categoryIds") List<UUID> categoryIds);
    
    @Query("SELECT n FROM Node n WHERE TYPE(n) = :nodeType AND n.deleted = false")
    Page<Node> findByNodeType(@Param("nodeType") Class<?> nodeType, Pageable pageable);
    
    @Query("SELECT n FROM Node n WHERE n.locked = true AND n.lockedBy = :userId")
    List<Node> findLockedByUser(@Param("userId") String userId);
    
    @Query("SELECT n FROM Node n WHERE n.createdBy = :userId AND n.deleted = false ORDER BY n.createdDate DESC")
    Page<Node> findByCreatedBy(@Param("userId") String userId, Pageable pageable);
    
    @Query("SELECT n FROM Node n WHERE n.lastModifiedDate > :since AND n.deleted = false ORDER BY n.lastModifiedDate DESC")
    List<Node> findModifiedSince(@Param("since") LocalDateTime since);
    
    @Query("SELECT n FROM Node n WHERE n.status = :status AND n.deleted = false")
    List<Node> findByStatus(@Param("status") NodeStatus status);
    
    @Query(value = "SELECT * FROM nodes WHERE metadata @> :metadata AND is_deleted = false", nativeQuery = true)
    List<Node> findByMetadata(@Param("metadata") String metadata);
    
    @Query(value = "SELECT * FROM nodes WHERE properties @> :properties AND is_deleted = false", nativeQuery = true)
    List<Node> findByProperties(@Param("properties") String properties);
    
    @Modifying
    @Query("UPDATE Node n SET n.deleted = true, n.status = 'DELETED' WHERE n.id = :nodeId")
    void softDelete(@Param("nodeId") UUID nodeId);
    
    @Modifying
    @Query("UPDATE Node n SET n.deleted = true, n.status = 'DELETED' WHERE n.path LIKE :pathPrefix%")
    void softDeleteByPathPrefix(@Param("pathPrefix") String pathPrefix);
    
    @Query("SELECT DISTINCT n FROM Node n " +
           "LEFT JOIN n.permissions p " +
           "WHERE n.deleted = false " +
           "AND (n.inheritPermissions = false AND p.authority = :authority AND p.permission = :permission AND p.allowed = true) " +
           "OR (n.inheritPermissions = true AND EXISTS (" +
           "    SELECT parent FROM Node parent " +
           "    LEFT JOIN parent.permissions pp " +
           "    WHERE parent.id = n.parent.id " +
           "    AND pp.authority = :authority " +
           "    AND pp.permission = :permission " +
           "    AND pp.allowed = true" +
           "))")
    Page<Node> findByPermission(@Param("authority") String authority, 
                                @Param("permission") String permission, 
                                Pageable pageable);
}