package com.ecm.core.integration.mail.service;

import com.ecm.core.entity.Node;
import com.ecm.core.integration.email.EmailIngestionService;
import com.ecm.core.integration.mail.model.MailAccount;
import com.ecm.core.integration.mail.model.MailRule;
import com.ecm.core.integration.mail.repository.MailAccountRepository;
import com.ecm.core.integration.mail.repository.MailRuleRepository;
import com.ecm.core.integration.mail.repository.ProcessedMailRepository;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.service.DocumentUploadService;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.TagService;
import com.ecm.core.service.TenantContextResolverService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pre-connect validation paths of {@link MailFetcherService#exportPreviewMatches}. Every case
 * here throws before {@code connect()} is reached, so no IMAP store is needed — they exercise the
 * 400-mapped {@link IllegalArgumentException} guards. The IMAP re-fetch + ingestion path is
 * covered behaviourally at the controller/contract layer (mailbox access cannot be unit-mocked).
 *
 * <p>Strict-stub discipline: each test stubs only the repositories its validation actually reaches.
 */
@ExtendWith(MockitoExtension.class)
class MailFetcherServiceExportValidationTest {

    @Mock private MailAccountRepository accountRepository;
    @Mock private MailRuleRepository ruleRepository;
    @Mock private ProcessedMailRepository processedMailRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private NodeRepository nodeRepository;
    @Mock private DocumentUploadService uploadService;
    @Mock private NodeService nodeService;
    @Mock private TagService tagService;
    @Mock private EmailIngestionService emailIngestionService;
    @Mock private MeterRegistry meterRegistry;
    @Mock private MailOAuthService mailOAuthService;
    @Mock private TenantContextResolverService tenantContextResolverService;

    private MailFetcherService service;

    private final UUID accountId = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private final UUID ruleId = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private final UUID targetFolderId = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @BeforeEach
    void setUp() {
        service = new MailFetcherService(
            accountRepository,
            ruleRepository,
            processedMailRepository,
            documentRepository,
            nodeRepository,
            uploadService,
            nodeService,
            tagService,
            emailIngestionService,
            meterRegistry,
            mailOAuthService,
            tenantContextResolverService
        );
    }

    private MailAccount account() {
        MailAccount account = new MailAccount();
        account.setId(accountId);
        account.setName("Ops mailbox");
        return account;
    }

    private MailRule rule() {
        MailRule rule = new MailRule();
        rule.setId(ruleId);
        rule.setAccountId(accountId);
        rule.setName("Invoices");
        return rule;
    }

    private List<MailFetcherService.MailPreviewExportSelection> oneSelection() {
        return List.of(new MailFetcherService.MailPreviewExportSelection("INBOX", "42"));
    }

    @Test
    @DisplayName("account not found -> IllegalArgumentException (400)")
    void accountNotFound() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
            service.exportPreviewMatches(accountId, ruleId, targetFolderId, oneSelection()));
    }

    @Test
    @DisplayName("rule not found -> IllegalArgumentException (400)")
    void ruleNotFound() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account()));
        when(ruleRepository.findById(ruleId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
            service.exportPreviewMatches(accountId, ruleId, targetFolderId, oneSelection()));
    }

    @Test
    @DisplayName("rule belongs to a different account -> IllegalArgumentException (400)")
    void ruleAccountMismatch() {
        MailRule foreign = rule();
        foreign.setAccountId(UUID.fromString("99999999-9999-4999-8999-999999999999"));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account()));
        when(ruleRepository.findById(ruleId)).thenReturn(Optional.of(foreign));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            service.exportPreviewMatches(accountId, ruleId, targetFolderId, oneSelection()));
        assertTrue(ex.getMessage().contains("does not belong"));
    }

    @Test
    @DisplayName("null targetFolderId -> IllegalArgumentException (400) before any folder lookup")
    void nullTargetFolder() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account()));
        when(ruleRepository.findById(ruleId)).thenReturn(Optional.of(rule()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            service.exportPreviewMatches(accountId, ruleId, null, oneSelection()));
        assertTrue(ex.getMessage().contains("targetFolderId"));
    }

    @Test
    @DisplayName("target folder does not exist -> IllegalArgumentException (400)")
    void targetFolderNotFound() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account()));
        when(ruleRepository.findById(ruleId)).thenReturn(Optional.of(rule()));
        when(nodeRepository.findById(targetFolderId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
            service.exportPreviewMatches(accountId, ruleId, targetFolderId, oneSelection()));
    }

    @Test
    @DisplayName("empty selections -> IllegalArgumentException (400)")
    void emptySelections() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account()));
        when(ruleRepository.findById(ruleId)).thenReturn(Optional.of(rule()));
        when(nodeRepository.findById(targetFolderId)).thenReturn(Optional.of(mock(Node.class)));

        assertThrows(IllegalArgumentException.class, () ->
            service.exportPreviewMatches(accountId, ruleId, targetFolderId, List.of()));
    }

    @Test
    @DisplayName("null selections -> IllegalArgumentException (400)")
    void nullSelections() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account()));
        when(ruleRepository.findById(ruleId)).thenReturn(Optional.of(rule()));
        when(nodeRepository.findById(targetFolderId)).thenReturn(Optional.of(mock(Node.class)));

        assertThrows(IllegalArgumentException.class, () ->
            service.exportPreviewMatches(accountId, ruleId, targetFolderId, null));
    }

    @Test
    @DisplayName("selections above the hard cap (200) -> IllegalArgumentException naming the cap")
    void overCapSelections() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account()));
        when(ruleRepository.findById(ruleId)).thenReturn(Optional.of(rule()));
        when(nodeRepository.findById(targetFolderId)).thenReturn(Optional.of(mock(Node.class)));

        List<MailFetcherService.MailPreviewExportSelection> tooMany = new ArrayList<>();
        for (int i = 0; i < 201; i++) {
            tooMany.add(new MailFetcherService.MailPreviewExportSelection("INBOX", Integer.toString(i)));
        }

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            service.exportPreviewMatches(accountId, ruleId, targetFolderId, tooMany));
        // Locks the chosen cap (gate ruling D6).
        assertTrue(ex.getMessage().contains("200"), "cap message should name the 200 limit");
    }

    @Test
    @DisplayName("a selection with a blank folder/uid -> IllegalArgumentException (400)")
    void blankSelectionEntry() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account()));
        when(ruleRepository.findById(ruleId)).thenReturn(Optional.of(rule()));
        when(nodeRepository.findById(targetFolderId)).thenReturn(Optional.of(mock(Node.class)));

        List<MailFetcherService.MailPreviewExportSelection> withBlank = new ArrayList<>();
        withBlank.add(new MailFetcherService.MailPreviewExportSelection("INBOX", "42"));
        withBlank.add(new MailFetcherService.MailPreviewExportSelection("  ", ""));

        assertThrows(IllegalArgumentException.class, () ->
            service.exportPreviewMatches(accountId, ruleId, targetFolderId, withBlank));
    }

    @Test
    @DisplayName("exactly 200 selections is within the cap (passes validation, reaches connect)")
    void exactlyAtCapIsAccepted() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account()));
        when(ruleRepository.findById(ruleId)).thenReturn(Optional.of(rule()));
        when(nodeRepository.findById(targetFolderId)).thenReturn(Optional.of(mock(Node.class)));

        List<MailFetcherService.MailPreviewExportSelection> exactly = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            exactly.add(new MailFetcherService.MailPreviewExportSelection("INBOX", Integer.toString(i)));
        }

        // 200 is within the cap, so it is NOT rejected for exceeding it; validation passes and the
        // flow reaches connect(), which fails fast in this unit context (no IMAP server) and is
        // mapped to the sanitized top-level IllegalStateException (gate connect-failure ruling).
        // This locks the cap boundary from the accepting side (the >200 test locks rejection).
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            service.exportPreviewMatches(accountId, ruleId, targetFolderId, exactly));
        assertTrue(ex.getMessage().toLowerCase().contains("connect"));
    }

    @Test
    @DisplayName("export result factory rows carry the right status/error invariants")
    void rowFactoryInvariants() {
        // Locks the closed-set invariants the frontend guard relies on, without needing IMAP.
        var exported = MailFetcherService.MailRulePreviewExportRow.exported("INBOX", "1");
        assertEquals(MailFetcherService.MailRulePreviewExportStatus.EXPORTED, exported.status());
        assertEquals(null, exported.errorCategory());
        assertEquals(null, exported.errorMessage());

        var failed = MailFetcherService.MailRulePreviewExportRow.failed("INBOX", "2", "boom");
        assertEquals(MailFetcherService.MailRulePreviewExportStatus.FAILED, failed.status());
        assertEquals(MailFetcherService.MailRulePreviewExportErrorCategory.INTERNAL_ERROR, failed.errorCategory());
        assertEquals("boom", failed.errorMessage());

        var alreadyDeclared = MailFetcherService.MailRulePreviewExportRow.skippedAlreadyProcessed("INBOX", "3");
        assertEquals(null, alreadyDeclared.errorCategory());
        assertEquals(null, alreadyDeclared.errorMessage());
    }
}
