package com.ecm.core.repository;

import com.ecm.core.entity.Folder;
import com.ecm.core.entity.FolderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FolderRepository extends JpaRepository<Folder, UUID> {
    
    List<Folder> findByFolderType(FolderType folderType);
    
    @Query("SELECT f FROM Folder f WHERE f.folderType = :type AND f.deleted = false")
    List<Folder> findActiveFoldersByType(@Param("type") FolderType type);
    
    @Query("SELECT f FROM Folder f WHERE f.path = :path AND f.deleted = false")
    Optional<Folder> findByPath(@Param("path") String path);
    
    @Query("SELECT f FROM Folder f WHERE f.parent IS NULL AND f.deleted = false")
    List<Folder> findRootFolders();
    
    @Query("SELECT COUNT(n) FROM Node n WHERE n.parent.id = :folderId AND n.deleted = false")
    long countChildren(@Param("folderId") UUID folderId);
    
    @Query("SELECT f FROM Folder f WHERE f.maxItems IS NOT NULL AND " +
           "(SELECT COUNT(n) FROM Node n WHERE n.parent.id = f.id AND n.deleted = false) >= f.maxItems")
    List<Folder> findFullFolders();
}