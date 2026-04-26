package com.ecm.core.repository;

import com.ecm.core.entity.LocalizedContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LocalizedContentRepository extends JpaRepository<LocalizedContent, UUID> {

    List<LocalizedContent> findByNodeIdOrderByLocaleAsc(UUID nodeId);

    Optional<LocalizedContent> findByNodeIdAndLocale(UUID nodeId, String locale);

    void deleteByNodeIdAndLocale(UUID nodeId, String locale);

    boolean existsByNodeIdAndLocale(UUID nodeId, String locale);
}
