package com.ecm.core.repository;

import com.ecm.core.entity.Activity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    Page<Activity> findByUserIdOrderByPostedAtDesc(String userId, Pageable pageable);

    Page<Activity> findBySiteIdOrderByPostedAtDesc(String siteId, Pageable pageable);

    Page<Activity> findByNodeIdOrderByPostedAtDesc(UUID nodeId, Pageable pageable);

    Page<Activity> findAllByOrderByPostedAtDesc(Pageable pageable);

    @Query("SELECT a FROM Activity a WHERE a.userId IN :userIds ORDER BY a.postedAt DESC")
    Page<Activity> findByUserIdInOrderByPostedAtDesc(@Param("userIds") List<String> userIds, Pageable pageable);

    @Modifying
    @Query("DELETE FROM Activity a WHERE a.postedAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);
}
