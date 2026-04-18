package com.ecm.core.repository;

import com.ecm.core.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);
    
    boolean existsByUsername(String username);

    Optional<User> findByDirectorySourceAndDirectoryExternalId(String directorySource, String directoryExternalId);

    List<User> findAllByDirectoryManagedTrueAndDirectorySource(String directorySource);
    
    Page<User> findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(String username, String email, Pageable pageable);
}
