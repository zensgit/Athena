package com.ecm.core.repository;

import com.ecm.core.entity.ContentReference;
import com.ecm.core.entity.ContentReference.OwnerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContentReferenceRepository extends JpaRepository<ContentReference, UUID> {

    Optional<ContentReference> findByContentIdAndOwnerTypeAndOwnerId(
            String contentId, OwnerType ownerType, UUID ownerId);

    List<ContentReference> findByContentIdAndActiveTrue(String contentId);

    long countByContentIdAndActiveTrue(String contentId);

    List<ContentReference> findByOwnerTypeAndOwnerId(OwnerType ownerType, UUID ownerId);

    boolean existsByContentIdAndOwnerTypeAndOwnerId(
            String contentId, OwnerType ownerType, UUID ownerId);

    /**
     * Deactivate a reference by marking it inactive rather than deleting.
     */
    @Modifying
    @Query("UPDATE ContentReference cr SET cr.active = false, cr.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE cr.contentId = :contentId AND cr.ownerType = :ownerType AND cr.ownerId = :ownerId " +
           "AND cr.active = true")
    int deactivate(@Param("contentId") String contentId,
                   @Param("ownerType") OwnerType ownerType,
                   @Param("ownerId") UUID ownerId);

    @Query("SELECT cr.contentId FROM ContentReference cr " +
           "WHERE cr.contentId NOT IN " +
           "(SELECT cr2.contentId FROM ContentReference cr2 WHERE cr2.active = true) " +
           "GROUP BY cr.contentId " +
           "HAVING MAX(COALESCE(cr.updatedAt, cr.createdAt)) <= :cutoff")
    List<String> findEligibleOrphanContentIds(@Param("cutoff") LocalDateTime cutoff);

    /**
     * Remove all inactive references for a given content ID (after physical cleanup).
     */
    @Modifying
    @Query("DELETE FROM ContentReference cr WHERE cr.contentId = :contentId AND cr.active = false")
    int purgeInactiveReferences(@Param("contentId") String contentId);
}
