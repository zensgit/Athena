package com.ecm.core.repository;

import com.ecm.core.entity.SavedSearch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SavedSearchRepository extends JpaRepository<SavedSearch, UUID> {
    
    List<SavedSearch> findByUserIdOrderByCreatedAtDesc(String userId);
    
    boolean existsByUserIdAndName(String userId, String name);
}