package com.ecm.core.integration.mail.controller;

import com.ecm.core.integration.mail.model.MailAccount;
import com.ecm.core.integration.mail.model.MailRule;
import com.ecm.core.integration.mail.repository.MailAccountRepository;
import com.ecm.core.integration.mail.repository.MailRuleRepository;
import com.ecm.core.integration.mail.service.MailFetcherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integration/mail")
@RequiredArgsConstructor
@Tag(name = "Integration: Mail Automation", description = "Manage mail accounts and rules")
public class MailAutomationController {

    private final MailAccountRepository accountRepository;
    private final MailRuleRepository ruleRepository;
    private final MailFetcherService fetcherService;

    // === Accounts ===

    @GetMapping("/accounts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<MailAccount>> getAccounts() {
        return ResponseEntity.ok(accountRepository.findAll());
    }

    @PostMapping("/accounts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MailAccount> createAccount(@RequestBody MailAccount account) {
        return ResponseEntity.ok(accountRepository.save(account));
    }

    @DeleteMapping("/accounts/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteAccount(@PathVariable UUID id) {
        accountRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // === Rules ===

    @GetMapping("/rules")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<MailRule>> getRules() {
        return ResponseEntity.ok(ruleRepository.findAllByOrderByPriorityAsc());
    }

    @PostMapping("/rules")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MailRule> createRule(@RequestBody MailRule rule) {
        return ResponseEntity.ok(ruleRepository.save(rule));
    }

    // === Actions ===

    @PostMapping("/fetch")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Trigger fetch", description = "Manually trigger mail fetching")
    public ResponseEntity<Void> triggerFetch() {
        fetcherService.fetchAllAccounts();
        return ResponseEntity.ok().build();
    }
}
