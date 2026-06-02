package com.ecm.core.repository;

import com.ecm.core.entity.Rating;
import com.ecm.core.entity.Rating.RatingScheme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RatingRepository extends JpaRepository<Rating, UUID> {

    List<Rating> findByNodeId(UUID nodeId);

    List<Rating> findByNodeIdAndScheme(UUID nodeId, RatingScheme scheme);

    Optional<Rating> findByNodeIdAndUserIdAndScheme(UUID nodeId, String userId, RatingScheme scheme);

    void deleteByNodeIdAndUserIdAndScheme(UUID nodeId, String userId, RatingScheme scheme);

    // Clear all ratings on a node before it is permanently deleted (FK fk_rating_node, no cascade).
    void deleteByNodeId(UUID nodeId);

    long countByNodeIdAndScheme(UUID nodeId, RatingScheme scheme);

    @Query("SELECT COALESCE(AVG(r.score), 0) FROM Rating r WHERE r.node.id = :nodeId AND r.scheme = :scheme")
    double averageScoreByNodeIdAndScheme(@Param("nodeId") UUID nodeId, @Param("scheme") RatingScheme scheme);

    @Query("SELECT COALESCE(SUM(r.score), 0) FROM Rating r WHERE r.node.id = :nodeId AND r.scheme = :scheme")
    long sumScoreByNodeIdAndScheme(@Param("nodeId") UUID nodeId, @Param("scheme") RatingScheme scheme);
}
