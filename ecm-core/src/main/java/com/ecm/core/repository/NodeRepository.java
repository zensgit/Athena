package com.ecm.core.repository;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Node.ArchiveStatus;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Repository
public interface NodeRepository extends JpaRepository<Node, UUID>, JpaSpecificationExecutor<Node> {
    
    Optional<Node> findFirstByPathAndDeletedFalseOrderByCreatedDateAsc(String path);

    Optional<Node> findFirstByPathAndDeletedFalseAndArchiveStatusOrderByCreatedDateAsc(String path, ArchiveStatus archiveStatus);
    
    Optional<Node> findByIdAndDeletedFalse(UUID id);

    Optional<Node> findByIdAndDeletedFalseAndArchiveStatus(UUID id, ArchiveStatus archiveStatus);
    
    List<Node> findByParentIdAndDeletedFalse(UUID parentId);

    List<Node> findByParentIdAndDeletedFalseAndArchiveStatus(UUID parentId, ArchiveStatus archiveStatus);

    List<Node> findByParentIdAndDeletedFalse(UUID parentId, Sort sort);

    List<Node> findByParentIdAndDeletedFalseAndArchiveStatus(UUID parentId, ArchiveStatus archiveStatus, Sort sort);
    
    Page<Node> findByParentIdAndDeletedFalse(UUID parentId, Pageable pageable);

    Page<Node> findByParentIdAndDeletedFalseAndArchiveStatus(UUID parentId, ArchiveStatus archiveStatus, Pageable pageable);
    
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

    Page<Node> findByArchiveStatusAndDeletedFalseOrderByArchivedDateDesc(ArchiveStatus archiveStatus, Pageable pageable);
    
    @Query(value = "SELECT * FROM nodes WHERE metadata @> :metadata AND is_deleted = false", nativeQuery = true)
    List<Node> findByMetadata(@Param("metadata") String metadata);
    
    @Query(value = "SELECT * FROM nodes WHERE properties @> :properties AND is_deleted = false", nativeQuery = true)
    List<Node> findByProperties(@Param("properties") String properties);

    Page<Node> findByTagsContainingAndDeletedFalse(Tag tag, Pageable pageable);

    Page<Node> findByTagsContainingAndDeletedFalseAndArchiveStatus(Tag tag, ArchiveStatus archiveStatus, Pageable pageable);

    @Query("SELECT n FROM Node n JOIN n.tags t WHERE t IN :tags AND n.deleted = false " +
           "GROUP BY n.id HAVING COUNT(DISTINCT t) = :tagCount")
    List<Node> findByAllTags(@Param("tags") List<Tag> tags, @Param("tagCount") long tagCount);

    List<Node> findByCategoriesInAndDeletedFalse(Set<Category> categories);

    List<Node> findByCategoriesInAndDeletedFalseAndArchiveStatus(Set<Category> categories, ArchiveStatus archiveStatus);
    
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

    Page<Node> findByNameContainingIgnoreCaseAndDeletedFalseAndArchiveStatus(String name, ArchiveStatus archiveStatus, Pageable pageable);

    @Query("SELECT COUNT(n) FROM Node n WHERE n.deleted = false AND n.path LIKE :pathPattern")
    long countByDeletedFalseAndPathLike(@Param("pathPattern") String pathPattern);

    @Query("SELECT COUNT(n) FROM Node n WHERE n.deleted = false AND n.typeQName = :typeQName")
    long countByTypeQNameAndDeletedFalse(@Param("typeQName") String typeQName);

    @Query("SELECT COUNT(n) FROM Node n JOIN n.aspects a WHERE n.deleted = false AND a = :aspectName")
    long countByAspectNameAndDeletedFalse(@Param("aspectName") String aspectName);

    @Query("SELECT n FROM Node n JOIN n.aspects a WHERE n.deleted = false AND a = :aspectName ORDER BY n.createdDate DESC")
    List<Node> findByAspectNameAndDeletedFalse(@Param("aspectName") String aspectName);

    @Query(value = "SELECT COUNT(*) FROM nodes n WHERE n.is_deleted = false AND jsonb_exists(n.properties, :propertyKey)", nativeQuery = true)
    long countByPropertyKeyAndDeletedFalse(@Param("propertyKey") String propertyKey);

    @Query(value = "SELECT COUNT(*) FROM nodes n WHERE n.is_deleted = false AND jsonb_exists(n.encrypted_properties, :propertyKey)", nativeQuery = true)
    long countByEncryptedPropertyKeyAndDeletedFalse(@Param("propertyKey") String propertyKey);

    @Query(value = """
        SELECT COUNT(*)
        FROM nodes n
        WHERE n.is_deleted = false
          AND jsonb_exists(n.properties, :propertyKey)
          AND jsonb_exists(n.encrypted_properties, :propertyKey)
        """, nativeQuery = true)
    long countByPropertyKeyInBothStorageAndDeletedFalse(@Param("propertyKey") String propertyKey);

    @Query(value = """
        SELECT COUNT(*)
        FROM nodes n
        WHERE n.is_deleted = false
          AND jsonb_exists(n.properties, :propertyKey)
          AND (
            n.encrypted_properties IS NULL
            OR NOT jsonb_exists(n.encrypted_properties, :propertyKey)
          )
        """, nativeQuery = true)
    long countBackfillReadyByPropertyKeyAndDeletedFalse(@Param("propertyKey") String propertyKey);

    @Query(value = """
        SELECT n.id AS "nodeId",
               CAST(:propertyKey AS text) AS "propertyKey",
               CAST(n.properties -> :propertyKey AS text) AS "plaintextJson",
               n.version AS "entityVersion"
        FROM nodes n
        WHERE n.is_deleted = false
          AND jsonb_exists(n.properties, :propertyKey)
          AND (
            n.encrypted_properties IS NULL
            OR NOT jsonb_exists(n.encrypted_properties, :propertyKey)
          )
        ORDER BY n.id
        LIMIT :limit
        """, nativeQuery = true)
    List<PropertyBackfillCandidateRow> findBackfillCandidatesByPropertyKeyAndDeletedFalse(
        @Param("propertyKey") String propertyKey,
        @Param("limit") int limit
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE nodes n
        SET properties = n.properties - CAST(:propertyKey AS text),
            encrypted_properties = COALESCE(n.encrypted_properties, '{}'::jsonb)
                || jsonb_build_object(CAST(:propertyKey AS text), CAST(:encryptedValue AS text)),
            last_modified_date = :modifiedAt,
            last_modified_by = :modifiedBy,
            version = n.version + 1
        WHERE n.id = :nodeId
          AND n.is_deleted = false
          AND n.version = :expectedVersion
          AND jsonb_exists(n.properties, :propertyKey)
          AND CAST(n.properties -> :propertyKey AS text) = :expectedPlaintextJson
          AND (
            n.encrypted_properties IS NULL
            OR NOT jsonb_exists(n.encrypted_properties, :propertyKey)
          )
        """, nativeQuery = true)
    int backfillEncryptedPropertyIfUnchanged(
        @Param("nodeId") UUID nodeId,
        @Param("propertyKey") String propertyKey,
        @Param("expectedPlaintextJson") String expectedPlaintextJson,
        @Param("expectedVersion") long expectedVersion,
        @Param("encryptedValue") String encryptedValue,
        @Param("modifiedAt") LocalDateTime modifiedAt,
        @Param("modifiedBy") String modifiedBy
    );

    @Query(value = """
        SELECT n.id AS "nodeId",
               payload.key AS "propertyKey",
               payload.value AS "encryptedValue",
               n.version AS "entityVersion"
        FROM nodes n
        CROSS JOIN LATERAL jsonb_each_text(n.encrypted_properties) AS payload(key, value)
        WHERE n.is_deleted = false
          AND n.encrypted_properties IS NOT NULL
          AND n.encrypted_properties <> '{}'::jsonb
          AND payload.value ~ '^enc:[^:]+:.+$'
          AND split_part(payload.value, ':', 2) <> :targetKeyVersion
        ORDER BY n.id, payload.key
        LIMIT :limit
        """, nativeQuery = true)
    List<PropertyRewrapCandidateRow> findRewrapCandidatesByTargetKeyVersionAndDeletedFalse(
        @Param("targetKeyVersion") String targetKeyVersion,
        @Param("limit") int limit
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE nodes n
        SET encrypted_properties = COALESCE(n.encrypted_properties, '{}'::jsonb)
                || jsonb_build_object(CAST(:propertyKey AS text), CAST(:rewrappedValue AS text)),
            last_modified_date = :modifiedAt,
            last_modified_by = :modifiedBy,
            version = n.version + 1
        WHERE n.id = :nodeId
          AND n.is_deleted = false
          AND n.version = :expectedVersion
          AND jsonb_exists(n.encrypted_properties, :propertyKey)
          AND n.encrypted_properties ->> :propertyKey = :expectedEncryptedValue
        """, nativeQuery = true)
    int rewrapEncryptedPropertyIfUnchanged(
        @Param("nodeId") UUID nodeId,
        @Param("propertyKey") String propertyKey,
        @Param("expectedEncryptedValue") String expectedEncryptedValue,
        @Param("expectedVersion") long expectedVersion,
        @Param("rewrappedValue") String rewrappedValue,
        @Param("modifiedAt") LocalDateTime modifiedAt,
        @Param("modifiedBy") String modifiedBy
    );

    @Query(value = """
        SELECT COUNT(*)
        FROM nodes n
        WHERE n.is_deleted = false
          AND (
            jsonb_exists(n.properties, :propertyKey)
            OR jsonb_exists(n.encrypted_properties, :propertyKey)
          )
        """, nativeQuery = true)
    long countByPropertyKeyAcrossStorageAndDeletedFalse(@Param("propertyKey") String propertyKey);

    @Query(value = """
        SELECT COUNT(*)
        FROM nodes n
        WHERE n.is_deleted = false
          AND n.encrypted_properties IS NOT NULL
          AND n.encrypted_properties <> '{}'::jsonb
        """, nativeQuery = true)
    long countNodesWithEncryptedPropertiesAndDeletedFalse();

    @Query(value = """
        SELECT COALESCE(SUM(jsonb_object_length(n.encrypted_properties)), 0)
        FROM nodes n
        WHERE n.is_deleted = false
          AND n.encrypted_properties IS NOT NULL
          AND n.encrypted_properties <> '{}'::jsonb
        """, nativeQuery = true)
    long countEncryptedPropertyValuesAndDeletedFalse();

    @Query(value = """
        SELECT split_part(payload.value, ':', 2) AS key_version,
               COUNT(*) AS value_count
        FROM nodes n
        CROSS JOIN LATERAL jsonb_each_text(n.encrypted_properties) AS payload(key, value)
        WHERE n.is_deleted = false
          AND n.encrypted_properties IS NOT NULL
          AND n.encrypted_properties <> '{}'::jsonb
          AND payload.value ~ '^enc:[^:]+:.+$'
        GROUP BY split_part(payload.value, ':', 2)
        """, nativeQuery = true)
    List<Object[]> countEncryptedPropertyValuesByKeyVersionAndDeletedFalse();

    interface PropertyBackfillCandidateRow {
        UUID getNodeId();

        String getPropertyKey();

        String getPlaintextJson();

        Long getEntityVersion();
    }

    interface PropertyRewrapCandidateRow {
        UUID getNodeId();

        String getPropertyKey();

        String getEncryptedValue();

        Long getEntityVersion();
    }
}
