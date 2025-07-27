package com.ecm.core.repository;

import com.ecm.core.entity.AuthorityType;
import com.ecm.core.entity.Permission;
import com.ecm.core.entity.PermissionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    
    List<Permission> findByNodeId(UUID nodeId);
    
    List<Permission> findByAuthority(String authority);
    
    List<Permission> findByNodeIdAndAuthority(UUID nodeId, String authority);
    
    @Query("SELECT p FROM Permission p WHERE p.node.id = :nodeId AND p.permission = :permission")
    List<Permission> findByNodeIdAndPermissionType(@Param("nodeId") UUID nodeId, @Param("permission") PermissionType permission);
    
    @Query("SELECT p FROM Permission p WHERE p.authority = :authority AND p.permission = :permission AND p.allowed = true")
    List<Permission> findAllowedPermissions(@Param("authority") String authority, @Param("permission") PermissionType permission);
    
    @Query("SELECT p FROM Permission p WHERE p.authorityType = :type AND p.node.id = :nodeId")
    List<Permission> findByNodeIdAndAuthorityType(@Param("nodeId") UUID nodeId, @Param("type") AuthorityType type);
    
    @Query("SELECT p FROM Permission p WHERE p.expiryDate IS NOT NULL AND p.expiryDate < CURRENT_TIMESTAMP")
    List<Permission> findExpiredPermissions();
    
    @Modifying
    @Query("DELETE FROM Permission p WHERE p.node.id = :nodeId")
    void deleteByNodeId(@Param("nodeId") UUID nodeId);
    
    @Modifying
    @Query("DELETE FROM Permission p WHERE p.authority = :authority")
    void deleteByAuthority(@Param("authority") String authority);
    
    @Query("SELECT DISTINCT p.authority FROM Permission p WHERE p.node.id = :nodeId AND p.allowed = true")
    List<String> findAuthoritiesWithAccess(@Param("nodeId") UUID nodeId);
    
    @Query("SELECT COUNT(DISTINCT p.node.id) FROM Permission p WHERE p.authority = :authority AND p.allowed = true")
    Long countAccessibleNodes(@Param("authority") String authority);
}