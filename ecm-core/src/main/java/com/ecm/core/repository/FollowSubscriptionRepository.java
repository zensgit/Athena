package com.ecm.core.repository;

import com.ecm.core.entity.FollowSubscription;
import com.ecm.core.entity.FollowTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FollowSubscriptionRepository extends JpaRepository<FollowSubscription, UUID> {

    boolean existsByUserIdAndTargetTypeAndTargetId(String userId, FollowTargetType targetType, String targetId);

    List<FollowSubscription> findByUserIdOrderByCreatedAtDesc(String userId);

    List<FollowSubscription> findByUserIdAndTargetTypeOrderByCreatedAtDesc(String userId, FollowTargetType targetType);

    Optional<FollowSubscription> findByUserIdAndTargetTypeAndTargetId(String userId, FollowTargetType targetType, String targetId);

    void deleteByUserIdAndTargetTypeAndTargetId(String userId, FollowTargetType targetType, String targetId);
}
