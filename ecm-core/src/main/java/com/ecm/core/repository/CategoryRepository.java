package com.ecm.core.repository;

import com.ecm.core.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByParentIsNullAndActiveTrue();

    List<Category> findByParentAndActiveTrue(Category parent);

    Optional<Category> findByName(String name);
}
