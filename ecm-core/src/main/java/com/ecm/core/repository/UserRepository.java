package com.ecm.core.repository;

import com.ecm.core.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);
    
    boolean existsByUsername(String username);
    
    Page<User> findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(String username, String email, Pageable pageable);
}
