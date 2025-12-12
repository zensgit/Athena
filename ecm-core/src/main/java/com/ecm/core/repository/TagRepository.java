package com.ecm.core.repository;

import com.ecm.core.model.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TagRepository extends JpaRepository<Tag, UUID> {

    Optional<Tag> findByName(String name);

    boolean existsByName(String name);

    List<Tag> findAllByOrderByUsageCountDesc();

    List<Tag> findByNameContainingIgnoreCase(String name);

    @Query("SELECT t FROM Tag t ORDER BY t.usageCount DESC")
    List<Tag> findTopByOrderByUsageCountDesc(Pageable pageable);
}
