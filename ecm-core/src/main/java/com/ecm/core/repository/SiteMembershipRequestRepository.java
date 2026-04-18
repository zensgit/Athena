package com.ecm.core.repository;

import com.ecm.core.entity.SiteMembershipRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SiteMembershipRequestRepository extends JpaRepository<SiteMembershipRequest, UUID> {
    List<SiteMembershipRequest> findBySiteIdOrderByRequestedAtDesc(UUID siteId);
    List<SiteMembershipRequest> findByUsernameOrderByRequestedAtDesc(String username);
    Optional<SiteMembershipRequest> findBySiteIdAndUsername(UUID siteId, String username);
    long countBySiteId(UUID siteId);
}
