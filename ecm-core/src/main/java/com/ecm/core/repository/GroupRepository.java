package com.ecm.core.repository;

import com.ecm.core.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public interface GroupRepository extends JpaRepository<Group, UUID> {
    Optional<Group> findByName(String name);

    Optional<Group> findByDirectorySourceAndDirectoryExternalId(String directorySource, String directoryExternalId);

    List<Group> findAllByDirectoryManagedTrueAndDirectorySource(String directorySource);
}
