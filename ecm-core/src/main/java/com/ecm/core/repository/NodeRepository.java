package com.ecm.core.repository;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Node.NodeStatus;
import com.ecm.core.model.Category;
import com.ecm.core.model.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Repository
public interface NodeRepository extends JpaRepository<Node, UUID>, JpaSpecificationExecutor<Node> {
    
    Optional<Node> findFirstByPathAndDeletedFalseOrderByCreatedDateAsc(String path);
    
    Optional<Node> findByIdAndDeletedFalse(UUID id);
    
    List<Node> findByParentIdAndDeletedFalse(UUID parentId);

    List<Node> findByParentIdAndDeletedFalse(UUID parentId, Sort sort);
    
    Page<Node> findByParentIdAndDeletedFalse(UUID parentId, Pageable pageable);
    
    @Query("SELECT n FROM Node n WHERE n.parent.id = :parentId AND n.name = :name AND n.deleted = false")
    Optional<Node> findByParentIdAndName(@Param("parentId") UUID parentId, @Param("name") String name);
    
    @Query("SELECT n FROM Node n WHERE n.path LIKE CONCAT(:pathPrefix, '%') AND n.deleted = false")
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

    Page<Node> findByTagsContainingAndDeletedFalse(Tag tag, Pageable pageable);

    @Query("SELECT n FROM Node n JOIN n.tags t WHERE t IN :tags AND n.deleted = false " +
           "GROUP BY n.id HAVING COUNT(DISTINCT t) = :tagCount")
    List<Node> findByAllTags(@Param("tags") List<Tag> tags, @Param("tagCount") long tagCount);

    List<Node> findByCategoriesInAndDeletedFalse(Set<Category> categories);
    
    @Modifying
    @Query("UPDATE Node n SET n.deleted = true, n.status = 'DELETED', n.deletedAt = :deletedAt, n.deletedBy = :deletedBy " +
           "WHERE n.id = :nodeId AND n.deleted = false")
    void softDelete(@Param("nodeId") UUID nodeId,
                    @Param("deletedAt") LocalDateTime deletedAt,
                    @Param("deletedBy") String deletedBy);
    
    @Modifying
    @Query("UPDATE Node n SET n.deleted = true, n.status = 'DELETED', n.deletedAt = :deletedAt, n.deletedBy = :deletedBy " +
           "WHERE n.path LIKE CONCAT(:pathPrefix, '%') AND n.deleted = false")
    void softDeleteByPathPrefix(@Param("pathPrefix") String pathPrefix,
                                @Param("deletedAt") LocalDateTime deletedAt,
                                @Param("deletedBy") String deletedBy);

    // Trash / Recycle Bin queries

    @Query("SELECT n FROM Node n WHERE n.id = :id")
    Optional<Node> findByIdIncludeDeleted(@Param("id") UUID id);

    @Query("SELECT n FROM Node n WHERE n.deleted = true AND n.createdBy = :username")
    Page<Node> findByDeletedTrueAndCreatedBy(@Param("username") String username, Pageable pageable);
    
    @Query("SELECT n FROM Node n WHERE n.deleted = true ORDER BY n.deletedAt DESC")
    List<Node> findDeletedNodes();

    @Query("SELECT n FROM Node n WHERE n.deleted = true AND n.deletedBy = :username ORDER BY n.deletedAt DESC")
    List<Node> findDeletedByUser(@Param("username") String username);

    @Query("SELECT n FROM Node n WHERE n.deleted = true AND n.deletedAt < :before ORDER BY n.deletedAt ASC")
    List<Node> findDeletedBefore(@Param("before") LocalDateTime before);

    @Query("SELECT n FROM Node n WHERE n.parent.id = :parentId AND n.deleted = true")
    List<Node> findDeletedChildren(@Param("parentId") UUID parentId);

    List<Node> findByParentId(UUID parentId);

    @Query("SELECT n FROM Node n LEFT JOIN FETCH n.categories c WHERE n.deleted = false")
    List<Node> findAllWithCategories();

    // Analytics Queries

    @Query("SELECT COUNT(d) FROM Document d WHERE d.deleted = false")
    long countDocuments();

    @Query("SELECT COUNT(f) FROM Folder f WHERE f.deleted = false")
    long countFolders();

    @Query("SELECT COALESCE(SUM(d.fileSize), 0) FROM Document d WHERE d.deleted = false")
    long getTotalSize();

    @Query("SELECT d.mimeType, COUNT(d), SUM(d.fileSize) FROM Document d WHERE d.deleted = false GROUP BY d.mimeType")
    List<Object[]> countByMimeType();

    @Query("SELECT d FROM Document d WHERE d.deleted = false")
    List<Document> findAllDocuments();

    // Index Rebuild
    @Query("SELECT d FROM Document d WHERE d.deleted = false")
    Stream<Node> streamAllDocuments();

    // Folder helpers
    @Query("SELECT f FROM Folder f WHERE f.parent IS NULL AND f.deleted = false")
    List<Folder> findRootFolders();

    Page<Node> findByNameContainingIgnoreCaseAndDeletedFalse(String name, Pageable pageable);
}
