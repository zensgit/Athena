package com.ecm.core.integration.mail.repository;

import com.ecm.core.integration.mail.model.MailAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MailAccountRepository extends JpaRepository<MailAccount, UUID> {
    List<MailAccount> findByEnabledTrue();
}
