package com.ecm.core.repository;

import com.ecm.core.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    
    Optional<User> findByUsername(String username);
    
    Optional<User> findByEmail(String email);
    
    Optional<User> findByUsernameOrEmail(String username, String email);
    
    boolean existsByUsername(String username);
    
    boolean existsByEmail(String email);
    
    List<User> findByEnabledTrue();
    
    List<User> findByLockedTrue();
    
    @Query("SELECT u FROM User u WHERE u.lastLoginDate < :date")
    List<User> findInactiveUsersSince(@Param("date") LocalDateTime date);
    
    @Query("SELECT u FROM User u JOIN u.groups g WHERE g.id = :groupId")
    List<User> findByGroupId(@Param("groupId") UUID groupId);
    
    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.id = :roleId")
    List<User> findByRoleId(@Param("roleId") UUID roleId);
    
    @Query("SELECT u FROM User u WHERE u.department = :department")
    List<User> findByDepartment(@Param("department") String department);
    
    @Query("SELECT u FROM User u WHERE u.passwordExpired = true OR u.lastPasswordChangeDate < :date")
    List<User> findUsersNeedingPasswordChange(@Param("date") LocalDateTime date);
    
    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = u.failedLoginAttempts + 1 WHERE u.username = :username")
    void incrementFailedAttempts(@Param("username") String username);
    
    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = 0, u.locked = false, u.lockedDate = null WHERE u.username = :username")
    void resetFailedAttempts(@Param("username") String username);
    
    @Modifying
    @Query("UPDATE User u SET u.lastLoginDate = :date WHERE u.username = :username")
    void updateLastLoginDate(@Param("username") String username, @Param("date") LocalDateTime date);
    
    @Query("SELECT u FROM User u WHERE u.quotaSizeMb IS NOT NULL AND u.usedSizeMb >= u.quotaSizeMb * :threshold")
    List<User> findUsersApproachingQuota(@Param("threshold") Double threshold);
    
    Page<User> findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(String username, String email, Pageable pageable);
}