package com.ecm.core.repository;

import com.ecm.core.entity.Role.Privilege;
import com.ecm.core.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {
    
    Optional<Role> findByName(String name);
    
    boolean existsByName(String name);
    
    List<Role> findByEnabledTrue();
    
    List<Role> findBySystemTrue();
    
    @Query("SELECT r FROM Role r WHERE :privilege MEMBER OF r.privileges")
    List<Role> findByPrivilege(@Param("privilege") Privilege privilege);
    
    @Query("SELECT r FROM Role r JOIN r.users u WHERE u.id = :userId")
    List<Role> findByUserId(@Param("userId") UUID userId);
    
    @Query("SELECT r FROM Role r JOIN r.groups g WHERE g.id = :groupId")
    List<Role> findByGroupId(@Param("groupId") UUID groupId);
    
    @Query("SELECT DISTINCT r FROM Role r LEFT JOIN FETCH r.privileges WHERE r.id IN :roleIds")
    List<Role> findByIdsWithPrivileges(@Param("roleIds") List<UUID> roleIds);
    
    @Query(value = "SELECT * FROM roles r WHERE r.permissions @> to_jsonb(ARRAY[:permission])", nativeQuery = true)
    List<Role> findByPermission(@Param("permission") String permission);
}
