package com.ecm.core.repository;

import com.ecm.core.entity.ContentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContentTypeRepository extends JpaRepository<ContentType, UUID> {
    Optional<ContentType> findByName(String name);
}
