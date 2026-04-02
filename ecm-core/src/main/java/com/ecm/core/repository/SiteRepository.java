package com.ecm.core.repository;

import com.ecm.core.entity.Site;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SiteRepository extends JpaRepository<Site, UUID> {

    Optional<Site> findBySiteIdIgnoreCase(String siteId);

    Optional<Site> findBySiteIdIgnoreCaseAndDeletedFalse(String siteId);

    List<Site> findByDeletedFalseOrderByTitleAsc();
}
