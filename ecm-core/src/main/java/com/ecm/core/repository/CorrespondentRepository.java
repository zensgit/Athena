package com.ecm.core.repository;

import com.ecm.core.entity.Correspondent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CorrespondentRepository extends JpaRepository<Correspondent, UUID> {
    
    Optional<Correspondent> findByName(String name);
    
    List<Correspondent> findAllByOrderByNameAsc();
    
    // For matching: find all correspondents that have a pattern defined
    List<Correspondent> findByMatchPatternIsNotNullAndMatchPatternNot(String empty);
}
