package com.ecm.core.repository;

import com.ecm.core.entity.SiteInvitation;
import com.ecm.core.entity.SiteInvitation.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SiteInvitationRepository extends JpaRepository<SiteInvitation, UUID> {

    Optional<SiteInvitation> findByToken(String token);

    List<SiteInvitation> findBySiteIdAndStatusOrderByCreatedDateDesc(UUID siteId, Status status);

    List<SiteInvitation> findBySiteIdOrderByCreatedDateDesc(UUID siteId);

    List<SiteInvitation> findByInviteeEmailAndStatusIn(String inviteeEmail, Collection<Status> statuses);

    List<SiteInvitation> findByExpiresAtBeforeAndStatus(LocalDateTime cutoff, Status status);
}
