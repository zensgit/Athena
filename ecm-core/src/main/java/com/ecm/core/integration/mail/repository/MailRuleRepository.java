package com.ecm.core.integration.mail.repository;

import com.ecm.core.integration.mail.model.MailRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MailRuleRepository extends JpaRepository<MailRule, UUID> {
    List<MailRule> findAllByOrderByPriorityAsc();

    List<MailRule> findAllByEnabledTrueOrderByPriorityAsc();
}
