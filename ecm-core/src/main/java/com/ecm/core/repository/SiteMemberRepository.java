package com.ecm.core.repository;

import com.ecm.core.entity.SiteMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SiteMemberRepository extends JpaRepository<SiteMember, UUID> {
    List<SiteMember> findBySiteIdOrderByRoleAscUsernameAsc(UUID siteId);
    Optional<SiteMember> findBySiteIdAndUsername(UUID siteId, String username);
    void deleteBySiteIdAndUsername(UUID siteId, String username);
    long countBySiteId(UUID siteId);
    List<SiteMember> findByUsername(String username);
}
