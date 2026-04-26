package com.ecm.core.integration.email.notify;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, UUID> {

    Optional<EmailTemplate> findByTemplateKeyAndLocale(String templateKey, String locale);

    List<EmailTemplate> findByTemplateKeyAndLocaleInOrderByLocaleAsc(
        String templateKey,
        List<String> locales
    );
}
