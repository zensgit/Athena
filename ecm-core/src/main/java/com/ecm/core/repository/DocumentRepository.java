package com.ecm.core.repository;

import com.ecm.core.entity.Document;
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
}
