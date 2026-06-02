package com.ecm.core.repository;

import com.ecm.core.entity.Version;
import com.ecm.core.entity.Version.VersionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VersionRepository extends JpaRepository<Version, UUID> {
    
    List<Version> findByDocumentIdOrderByVersionNumberDesc(UUID documentId);

    Page<Version> findByDocumentIdOrderByVersionNumberDesc(UUID documentId, Pageable pageable);
    
    Optional<Version> findByDocumentIdAndVersionNumber(UUID documentId, Integer versionNumber);
    
    @Query("SELECT v FROM Version v WHERE v.document.id = :documentId AND v.versionLabel = :label")
    Optional<Version> findByDocumentIdAndLabel(@Param("documentId") UUID documentId, @Param("label") String label);
    
    @Query("SELECT MAX(v.versionNumber) FROM Version v WHERE v.document.id = :documentId")
    Integer findMaxVersionNumber(@Param("documentId") UUID documentId);
    
    List<Version> findByStatus(VersionStatus status);
    
    @Query("SELECT v FROM Version v WHERE v.document.id = :documentId AND v.majorVersionFlag = true ORDER BY v.versionNumber DESC")
    List<Version> findMajorVersions(@Param("documentId") UUID documentId);

    @Query("SELECT v FROM Version v WHERE v.document.id = :documentId AND v.majorVersionFlag = true ORDER BY v.versionNumber DESC")
    Page<Version> findMajorVersions(@Param("documentId") UUID documentId, Pageable pageable);
    
    @Query("SELECT v FROM Version v WHERE v.contentHash = :contentHash")
    List<Version> findByContentHash(@Param("contentHash") String contentHash);

    long countByContentIdAndDeletedFalse(String contentId);
    
    @Query("SELECT SUM(v.fileSize) FROM Version v")
    Long calculateTotalVersionStorage();

    /**
     * Sum the fileSize of NON-CURRENT retained versions under a tenant's root path, for quota.
     * Excludes each document's current version (its bytes are already counted via the live
     * Document.fileSize), so an initial-only versioned doc — where version 1 references the current
     * contentId (InitialVersionProcessor) — is NOT double-counted; older retained versions add to
     * usage. No physical blob dedup is applied: this is logical per-tenant accounting (ADR-002
     * addendum; ADR-001 global dedup makes per-tenant physical accounting ill-defined).
     */
    @Query("SELECT COALESCE(SUM(v.fileSize), 0) FROM Version v " +
           "WHERE v.document.deleted = false AND v.document.path LIKE :pathPrefix " +
           "AND v.document.currentVersion IS NOT NULL AND v.id <> v.document.currentVersion.id")
    long sumNonCurrentVersionFileSizeByPathPrefix(@Param("pathPrefix") String pathPrefix);
    
    @Query("SELECT v FROM Version v WHERE v.document.id = :documentId " +
           "AND v.majorVersion = :major AND v.minorVersion = :minor")
    Optional<Version> findByDocumentIdAndVersionNumbers(@Param("documentId") UUID documentId,
                                                        @Param("major") Integer major,
                                                        @Param("minor") Integer minor);

    long countByDocumentId(UUID documentId);
}
