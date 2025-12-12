package com.ecm.core.repository;

import com.ecm.core.entity.Favorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, UUID> {

    Page<Favorite> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    List<Favorite> findByUserId(String userId);

    Optional<Favorite> findByUserIdAndNodeId(String userId, UUID nodeId);

    boolean existsByUserIdAndNodeId(String userId, UUID nodeId);

    void deleteByUserIdAndNodeId(String userId, UUID nodeId);
}