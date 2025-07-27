package com.ecm.core.repository;

import com.ecm.core.entity.Group;
import com.ecm.core.entity.GroupType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupRepository extends JpaRepository<Group, UUID> {
    
    Optional<Group> findByName(String name);
    
    boolean existsByName(String name);
    
    List<Group> findByEnabledTrue();
    
    List<Group> findByGroupType(GroupType groupType);
    
    @Query("SELECT g FROM Group g WHERE g.parentGroup IS NULL")
    List<Group> findRootGroups();
    
    @Query("SELECT g FROM Group g WHERE g.parentGroup.id = :parentId")
    List<Group> findByParentGroupId(@Param("parentId") UUID parentId);
    
    @Query("SELECT g FROM Group g JOIN g.users u WHERE u.id = :userId")
    List<Group> findByUserId(@Param("userId") UUID userId);
    
    @Query("SELECT g FROM Group g JOIN g.roles r WHERE r.id = :roleId")
    List<Group> findByRoleId(@Param("roleId") UUID roleId);
    
    @Query("SELECT COUNT(u) FROM Group g JOIN g.users u WHERE g.id = :groupId")
    Long countMembers(@Param("groupId") UUID groupId);
    
    @Query("SELECT DISTINCT g FROM Group g LEFT JOIN FETCH g.users WHERE g.id IN :groupIds")
    List<Group> findByIdsWithUsers(@Param("groupIds") List<UUID> groupIds);
}