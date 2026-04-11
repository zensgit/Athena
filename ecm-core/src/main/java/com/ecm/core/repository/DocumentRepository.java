package com.ecm.core.repository;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.PreviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID>, JpaSpecificationExecutor<Document> {
    
    Optional<Document> findByContentHash(String contentHash);
    
    List<Document> findByMimeType(String mimeType);

    Page<Document> findByDeletedFalse(Pageable pageable);
    
    Page<Document> findByMimeTypeIn(List<String> mimeTypes, Pageable pageable);
    
    @Query("SELECT d FROM Document d WHERE d.checkoutUser = :userId AND d.deleted = false")
    List<Document> findCheckedOutByUser(@Param("userId") String userId);

    @Query("SELECT d FROM Document d WHERE d.workingCopyOf = :originalId AND d.workingCopy = true AND d.deleted = false")
    Optional<Document> findWorkingCopyOf(@Param("originalId") UUID originalId);

    @Query("SELECT d FROM Document d WHERE d.workingCopy = true AND d.checkoutUser = :userId AND d.deleted = false")
    List<Document> findWorkingCopiesByUser(@Param("userId") String userId);
    
    @Query("SELECT d FROM Document d WHERE d.fileSize > :minSize AND d.deleted = false ORDER BY d.fileSize DESC")
    List<Document> findLargeDocuments(@Param("minSize") Long minSize);
    
    @Query("SELECT d FROM Document d WHERE d.textContent LIKE %:searchTerm% AND d.deleted = false")
    Page<Document> searchByContent(@Param("searchTerm") String searchTerm, Pageable pageable);
    
    @Query("SELECT d FROM Document d WHERE d.versionLabel = :versionLabel AND d.deleted = false")
    List<Document> findByVersionLabel(@Param("versionLabel") String versionLabel);
    
    @Query("SELECT d FROM Document d WHERE d.previewAvailable = false AND d.deleted = false")
    List<Document> findDocumentsWithoutPreview();
    
    @Query("SELECT d FROM Document d WHERE d.thumbnailId IS NULL AND d.deleted = false")
    List<Document> findDocumentsWithoutThumbnail();
    
    @Query("SELECT SUM(d.fileSize) FROM Document d WHERE d.createdBy = :userId AND d.deleted = false")
    Long calculateUserStorageUsage(@Param("userId") String userId);

    @Query("SELECT COALESCE(SUM(d.fileSize), 0) FROM Document d WHERE d.deleted = false AND d.path LIKE :pathPrefix")
    long sumFileSizeByPathPrefix(@Param("pathPrefix") String pathPrefix);
    
    @Query("SELECT d.mimeType, COUNT(d) FROM Document d WHERE d.deleted = false GROUP BY d.mimeType")
    List<Object[]> getDocumentCountByMimeType();
    
    @Query("SELECT d FROM Document d WHERE d.language = :language AND d.deleted = false")
    List<Document> findByLanguage(@Param("language") String language);
    
    @Query("SELECT d FROM Document d WHERE d.checkoutDate < :beforeDate AND d.checkoutUser IS NOT NULL")
    List<Document> findLongCheckedOutDocuments(@Param("beforeDate") LocalDateTime beforeDate);
    
    @Query(value = "SELECT * FROM documents d " +
           "JOIN nodes n ON d.id = n.id " +
           "WHERE to_tsvector('english', d.text_content) @@ plainto_tsquery('english', :query) " +
           "AND n.is_deleted = false",
           nativeQuery = true)
    Page<Document> fullTextSearch(@Param("query") String query, Pageable pageable);

    @Query(
        value = "SELECT n.*, d.* FROM documents d " +
            "JOIN nodes n ON d.id = n.id " +
            "WHERE n.is_deleted = false " +
            "AND n.node_type = 'DOCUMENT' " +
            "AND n.properties ->> 'mail:source' = 'true' " +
            "ORDER BY n.created_date DESC " +
            "LIMIT :limit",
        nativeQuery = true
    )
    List<Document> findRecentMailDocuments(@Param("limit") int limit);

    @Query(
        value = "SELECT n.*, d.* FROM documents d " +
            "JOIN nodes n ON d.id = n.id " +
            "WHERE n.is_deleted = false " +
            "AND n.node_type = 'DOCUMENT' " +
            "AND n.properties ->> 'mail:source' = 'true' " +
            "AND (:accountId IS NULL OR n.properties ->> 'mail:accountId' = :accountId) " +
            "AND (:ruleId IS NULL OR n.properties ->> 'mail:ruleId' = :ruleId) " +
            "ORDER BY n.created_date DESC " +
            "LIMIT :limit",
        nativeQuery = true
    )
    List<Document> findRecentMailDocumentsWithFilters(
        @Param("limit") int limit,
        @Param("accountId") String accountId,
        @Param("ruleId") String ruleId
    );

    @Query(
        value = "SELECT n.*, d.* FROM documents d " +
            "JOIN nodes n ON d.id = n.id " +
            "WHERE n.is_deleted = false " +
            "AND n.node_type = 'DOCUMENT' " +
            "AND n.properties ->> 'mail:source' = 'true' " +
            "AND (:accountId IS NULL OR n.properties ->> 'mail:accountId' = :accountId) " +
            "AND (:folder IS NULL OR n.properties ->> 'mail:folder' = :folder) " +
            "AND (:uid IS NULL OR n.properties ->> 'mail:uid' = :uid) " +
            "ORDER BY n.created_date DESC " +
            "LIMIT :limit",
        nativeQuery = true
    )
    List<Document> findMailDocumentsForMessage(
        @Param("limit") int limit,
        @Param("accountId") String accountId,
        @Param("folder") String folder,
        @Param("uid") String uid
    );

    /**
     * Find documents modified since a given date (for scheduled rules)
     */
    @Query("SELECT d FROM Document d " +
           "WHERE d.deleted = false " +
           "AND d.lastModifiedDate > :since " +
           "ORDER BY d.lastModifiedDate ASC")
    Page<Document> findModifiedSince(@Param("since") LocalDateTime since, Pageable pageable);

    /**
     * Find documents modified since a given date within a specific folder
     */
    @Query("SELECT d FROM Document d " +
           "WHERE d.deleted = false " +
           "AND d.lastModifiedDate > :since " +
           "AND d.parent.id = :folderId " +
           "ORDER BY d.lastModifiedDate ASC")
    Page<Document> findModifiedSinceInFolder(
        @Param("since") LocalDateTime since,
        @Param("folderId") UUID folderId,
        Pageable pageable);

    @Query("SELECT d FROM Document d " +
           "WHERE d.deleted = false " +
           "AND d.previewStatus IN :statuses " +
           "ORDER BY d.previewLastUpdated DESC")
    Page<Document> findRecentPreviewFailures(
        @Param("statuses") List<PreviewStatus> statuses,
        Pageable pageable);

    long countByDeletedFalseAndPreviewStatusIn(List<PreviewStatus> statuses);

    @Query("SELECT d FROM Document d " +
           "WHERE d.deleted = false " +
           "AND d.previewStatus IN :statuses " +
           "AND (:updatedSince IS NULL OR d.previewLastUpdated >= :updatedSince) " +
           "ORDER BY d.previewLastUpdated DESC")
    Page<Document> findRecentPreviewFailuresByWindow(
        @Param("statuses") List<PreviewStatus> statuses,
        @Param("updatedSince") LocalDateTime updatedSince,
        Pageable pageable);

    @Query("SELECT COUNT(d) FROM Document d " +
           "WHERE d.deleted = false " +
           "AND d.previewStatus IN :statuses " +
           "AND (:updatedSince IS NULL OR d.previewLastUpdated >= :updatedSince)")
    long countPreviewFailuresByWindow(
        @Param("statuses") List<PreviewStatus> statuses,
        @Param("updatedSince") LocalDateTime updatedSince);

    @Query("SELECT d FROM Document d " +
           "WHERE d.deleted = false " +
           "AND d.previewStatus IN :statuses " +
           "AND (:updatedSince IS NULL OR d.previewLastUpdated >= :updatedSince) " +
           "AND ((:reason = 'UNSPECIFIED' AND TRIM(COALESCE(d.previewFailureReason, '')) = '') " +
           "  OR d.previewFailureReason = :reason) " +
           "ORDER BY d.previewLastUpdated DESC")
    Page<Document> findPreviewFailuresByReasonAndWindow(
        @Param("statuses") List<PreviewStatus> statuses,
        @Param("updatedSince") LocalDateTime updatedSince,
        @Param("reason") String reason,
        Pageable pageable);

    @Query("SELECT COUNT(d) FROM Document d " +
           "WHERE d.deleted = false " +
           "AND d.previewStatus IN :statuses " +
           "AND (:updatedSince IS NULL OR d.previewLastUpdated >= :updatedSince) " +
           "AND ((:reason = 'UNSPECIFIED' AND TRIM(COALESCE(d.previewFailureReason, '')) = '') " +
           "  OR d.previewFailureReason = :reason)")
    long countPreviewFailuresByReasonAndWindow(
        @Param("statuses") List<PreviewStatus> statuses,
        @Param("updatedSince") LocalDateTime updatedSince,
        @Param("reason") String reason);

    @Query("SELECT d FROM Document d " +
           "WHERE d.deleted = false " +
           "AND COALESCE(d.previewFailureCount, 0) > 0 " +
           "AND (:failedSince IS NULL OR d.previewFailedAt >= :failedSince) " +
           "ORDER BY d.previewFailedAt DESC, d.previewLastUpdated DESC")
    Page<Document> findPreviewFailureLedgerEntries(
        @Param("failedSince") LocalDateTime failedSince,
        Pageable pageable);

    @Query("SELECT COUNT(d) FROM Document d " +
           "WHERE d.deleted = false " +
           "AND COALESCE(d.previewFailureCount, 0) > 0 " +
           "AND (:failedSince IS NULL OR d.previewFailedAt >= :failedSince)")
    long countPreviewFailureLedgerEntries(
        @Param("failedSince") LocalDateTime failedSince);
}
