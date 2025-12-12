package com.ecm.core.repository;

import com.ecm.core.entity.Version;
import com.ecm.core.entity.Version.VersionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VersionRepository extends JpaRepository<Version, UUID> {
    
    List<Version> findByDocumentIdOrderByVersionNumberDesc(UUID documentId);
    
    Optional<Version> findByDocumentIdAndVersionNumber(UUID documentId, Integer versionNumber);
    
    @Query("SELECT v FROM Version v WHERE v.document.id = :documentId AND v.versionLabel = :label")
    Optional<Version> findByDocumentIdAndLabel(@Param("documentId") UUID documentId, @Param("label") String label);
    
    @Query("SELECT MAX(v.versionNumber) FROM Version v WHERE v.document.id = :documentId")
    Integer findMaxVersionNumber(@Param("documentId") UUID documentId);
    
    List<Version> findByStatus(VersionStatus status);
    
    @Query("SELECT v FROM Version v WHERE v.document.id = :documentId AND v.majorVersionFlag = true ORDER BY v.versionNumber DESC")
    List<Version> findMajorVersions(@Param("documentId") UUID documentId);
    
    @Query("SELECT v FROM Version v WHERE v.contentHash = :contentHash")
    List<Version> findByContentHash(@Param("contentHash") String contentHash);
    
    @Query("SELECT SUM(v.fileSize) FROM Version v")
    Long calculateTotalVersionStorage();
    
    @Query("SELECT v FROM Version v WHERE v.document.id = :documentId " +
           "AND v.majorVersion = :major AND v.minorVersion = :minor")
    Optional<Version> findByDocumentIdAndVersionNumbers(@Param("documentId") UUID documentId,
                                                        @Param("major") Integer major,
                                                        @Param("minor") Integer minor);

    long countByDocumentId(UUID documentId);
}
