package com.ecm.core.integration.mail.service;

import com.ecm.core.config.TenantContext;
import com.ecm.core.entity.Document;
import com.ecm.core.integration.email.EmailIngestionService;
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
import jakarta.mail.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Q2b: MailFetcherService scopes its two write points -- ingestEmailMessage's .eml write and
 * ingestAttachments' uploads -- to the tenant that owns the message's effective target folder,
 * resolved ONCE in processContent. These tests reach the package-private processContent via
 * reflection because the existing MailFetcher test suite has no IMAP-store mock to drive the public
 * live-fetch / preview-export paths down to the ingest layer.
 *
 * <p>The load-bearing assertion is that a tenant reject THROWS out of processContent rather than
 * returning false. Per the gate ruling, processMessage's catch maps a thrown exception to an ERROR
 * ProcessedMail row and exportOneMatch's catch maps it to a FAILED preview-export row, whereas a
 * {@code false} return is silently swallowed as no_content. Those two upper-layer catches are
 * pre-existing and unchanged; this test locks the behaviour they rely on (throw, not false), so a
 * future refactor that "helpfully" turns the reject into {@code return false} fails loudly here.
 */
@ExtendWith(MockitoExtension.class)
class MailFetcherServiceTenantScopingTest {

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

    @InjectMocks private MailFetcherService service;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Q2b: processContent scopes the write to the resolved tenant and restores after")
    void scopesWriteToResolvedTenantThenRestores() throws Throwable {
        UUID folderId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        MailRule rule = metadataRule(folderId);
        Message message = mock(Message.class);
        when(tenantContextResolverService.resolveTenantForTargetFolder(folderId))
            .thenReturn(TenantContextResolverService.TenantResolution.resolved("acme", rootId));
        // The .eml write must observe the resolved tenant AT write time.
        when(emailIngestionService.ingestEmail(any(), eq(folderId), any())).thenAnswer(inv -> {
            assertEquals("acme", TenantContext.getCurrentTenantDomain());
            assertEquals(rootId, TenantContext.getCurrentTenantRootNodeId());
            return mock(Document.class);
        });

        invokeProcessContent(message, rule, null); // targetFolderId null -> uses rule.getAssignFolderId()

        // This thread had no tenant on entry -> restored to empty, no leak into the next message.
        assertNull(TenantContext.getCurrentTenantDomain());
        assertNull(TenantContext.getCurrentTenantRootNodeId());
    }

    @Test
    @DisplayName("Q2b: folder not under an enabled tenant -> processContent THROWS (caller records ERROR/FAILED, not no_content)")
    void rejectThrowsSoCallerMapsErrorNotNoContent() {
        UUID folderId = UUID.randomUUID();
        MailRule rule = everythingRule(folderId);
        Message message = mock(Message.class);
        when(tenantContextResolverService.resolveTenantForTargetFolder(folderId))
            .thenReturn(TenantContextResolverService.TenantResolution.unresolved());

        // Caller-owned THROW (not return false): processMessage's catch -> ERROR row, exportOneMatch's
        // catch -> FAILED row. A false return would be silently swallowed as no_content. The resolver
        // RETURNS (no proxy poison); processContent itself raises the caller-owned exception.
        assertThrows(IllegalStateException.class,
            () -> invokeProcessContent(message, rule, null));
        verifyNoInteractions(uploadService, emailIngestionService);
    }

    @Test
    @DisplayName("Q2b Option A: null effective folder rejects (no silent untenanted write)")
    void nullFolderRejectsPerOptionA() {
        MailRule rule = everythingRule(null); // no assignFolderId, and no targetFolder override below
        Message message = mock(Message.class);
        // Per gate Option A a null effective folder is unresolved when tenants exist (the rule must
        // name a tenant-folder target) -- processContent raises a caller-owned exception, never an
        // untenanted write.
        when(tenantContextResolverService.resolveTenantForTargetFolder(null))
            .thenReturn(TenantContextResolverService.TenantResolution.unresolved());

        assertThrows(IllegalStateException.class,
            () -> invokeProcessContent(message, rule, null));
        verifyNoInteractions(uploadService, emailIngestionService);
    }

    @Test
    @DisplayName("Q2b: ingest failure restores the caller's tenant (manual / preview-export path)")
    void restoresCallerTenantWhenIngestThrows() {
        UUID folderId = UUID.randomUUID();
        MailRule rule = metadataRule(folderId);
        Message message = mock(Message.class);
        // A preview-export / manual path that already carries its own tenant on entry.
        TenantContext.setCurrentTenantDomain("caller-tenant");
        when(tenantContextResolverService.resolveTenantForTargetFolder(folderId))
            .thenReturn(TenantContextResolverService.TenantResolution.resolved("acme", UUID.randomUUID()));
        when(emailIngestionService.ingestEmail(any(), eq(folderId), any()))
            .thenThrow(new RuntimeException("ingest boom"));

        assertThrows(RuntimeException.class, () -> invokeProcessContent(message, rule, null));
        // caller's tenant restored -- not cleared, not left as the resolved "acme".
        assertEquals("caller-tenant", TenantContext.getCurrentTenantDomain());
    }

    private MailRule metadataRule(UUID folderId) {
        MailRule rule = new MailRule();
        rule.setActionType(MailRule.MailActionType.METADATA_ONLY);
        rule.setAssignFolderId(folderId);
        return rule;
    }

    private MailRule everythingRule(UUID folderId) {
        MailRule rule = new MailRule();
        rule.setActionType(MailRule.MailActionType.EVERYTHING);
        rule.setAssignFolderId(folderId);
        return rule;
    }

    private Object invokeProcessContent(Message message, MailRule rule, UUID targetFolderId) throws Throwable {
        Method m = MailFetcherService.class.getDeclaredMethod(
            "processContent", Message.class, MailRule.class, List.class, Map.class, UUID.class);
        m.setAccessible(true);
        try {
            return m.invoke(service, message, rule, List.of(), Map.of(), targetFolderId);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
