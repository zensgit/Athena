package com.ecm.core.service;

import com.ecm.core.entity.Site;
import com.ecm.core.entity.SiteInvitation;
import com.ecm.core.entity.SiteInvitation.LastSendStatus;
import com.ecm.core.entity.SiteInvitation.Status;
import com.ecm.core.entity.SiteMember;
import com.ecm.core.entity.SiteMember.SiteMemberRole;
import com.ecm.core.exception.AccessDeniedException;
import com.ecm.core.exception.ResourceNotFoundException;
import com.ecm.core.integration.email.notify.EmailNotificationService;
import com.ecm.core.integration.email.notify.EmailNotificationService.SendResult;
import com.ecm.core.repository.SiteInvitationRepository;
import com.ecm.core.repository.SiteMemberRepository;
import com.ecm.core.repository.SiteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.io.PrintWriter;
import java.io.StringWriter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteInvitationServiceTest {

    @Mock private SiteInvitationRepository invitationRepository;
    @Mock private SiteRepository siteRepository;
    @Mock private SiteMemberRepository siteMemberRepository;
    @Mock private SecurityService securityService;
    @Mock private ActivityEventListener activityEventListener;
    @Mock private ObjectProvider<EmailNotificationService> emailNotificationServiceProvider;
    @Mock private EmailNotificationService emailNotificationService;
    @Mock private PlatformTransactionManager transactionManager;

    private SiteInvitationService newService() {
        // Stub the transaction manager so the bulk path's TransactionTemplate
        // can drive its callback. Single-invite tests do not exercise the
        // template, so leniently stubbed here at construction time and ignored
        // by paths that never call execute().
        org.mockito.Mockito.lenient().when(
            transactionManager.getTransaction(org.mockito.ArgumentMatchers.any(TransactionDefinition.class))
        ).thenReturn(new SimpleTransactionStatus());
        return new SiteInvitationService(
            invitationRepository,
            siteRepository,
            siteMemberRepository,
            securityService,
            activityEventListener,
            emailNotificationServiceProvider,
            transactionManager
        );
    }

    private static Site newSite(UUID siteUuid, String slug, String title) {
        Site site = new Site();
        site.setId(siteUuid);
        site.setSiteId(slug);
        site.setTitle(title);
        return site;
    }

    private static SiteMember newManager(Site site, String username) {
        SiteMember manager = new SiteMember();
        manager.setSite(site);
        manager.setUsername(username);
        manager.setRole(SiteMemberRole.MANAGER);
        return manager;
    }

    private static SiteInvitation newPendingInvitation(Site site) {
        SiteInvitation invitation = new SiteInvitation();
        invitation.setId(UUID.randomUUID());
        invitation.setSite(site);
        invitation.setInviteeEmail("guest@example.com");
        invitation.setInvitedRole("CONSUMER");
        invitation.setInvitedBy("manager");
        invitation.setToken("0".repeat(64));
        invitation.setStatus(Status.PENDING);
        invitation.setExpiresAt(LocalDateTime.now().plusDays(7));
        return invitation;
    }

    // ---------------------------------------------------------------- invite

    @Test
    @DisplayName("invite sends the seeded site.invitation email template via sendSync, populates send-tracking fields")
    void inviteSendsSiteInvitationEmailTemplate() {
        UUID siteUuid = UUID.randomUUID();
        Site site = newSite(siteUuid, "finance", "Finance Team");

        when(siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse("finance")).thenReturn(Optional.of(site));
        when(securityService.getCurrentUser()).thenReturn("manager");
        when(securityService.isAdmin("manager")).thenReturn(false);
        when(siteMemberRepository.findBySiteIdAndUsername(siteUuid, "manager"))
            .thenReturn(Optional.of(newManager(site, "manager")));
        when(invitationRepository.findByInviteeEmailAndStatusIn(eq("guest@example.com"), any())).thenReturn(List.of());
        when(emailNotificationServiceProvider.getIfAvailable()).thenReturn(emailNotificationService);
        when(emailNotificationService.sendSync(eq("site.invitation"), eq("guest@example.com"), isNull(), any()))
            .thenReturn(new SendResult(true, null));
        when(invitationRepository.save(any(SiteInvitation.class))).thenAnswer(invocation -> {
            SiteInvitation invitation = invocation.getArgument(0);
            if (invitation.getId() == null) {
                invitation.setId(UUID.randomUUID());
            }
            if (invitation.getCreatedDate() == null) {
                invitation.setCreatedDate(LocalDateTime.of(2026, 4, 26, 10, 0));
            }
            return invitation;
        });

        SiteInvitationService service = newService();

        SiteInvitationService.SiteInvitationDto dto = service.invite(
            "finance",
            new SiteInvitationService.InviteRequest(
                " Guest@Example.COM ",
                "contributor",
                "Please review the closeout documents."
            )
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(emailNotificationService).sendSync(
            eq("site.invitation"),
            eq("guest@example.com"),
            isNull(),
            variablesCaptor.capture()
        );

        Map<String, Object> variables = variablesCaptor.getValue();
        assertThat(dto.inviteeEmail()).isEqualTo("guest@example.com");
        assertThat(dto.invitedRole()).isEqualTo("CONTRIBUTOR");
        assertThat(variables)
            .containsEntry("siteTitle", "Finance Team")
            .containsEntry("siteId", "finance")
            .containsEntry("invitedBy", "manager")
            .containsEntry("role", "CONTRIBUTOR")
            .containsEntry("message", "Please review the closeout documents.");
        assertThat(variables.get("token")).isInstanceOf(String.class);
        assertThat((String) variables.get("token")).hasSize(64);
        assertThat(variables.get("invitationUrl")).isInstanceOf(String.class);
        assertThat((String) variables.get("invitationUrl"))
            .startsWith("http://localhost:3000/invitations/accept?token=");
        assertThat((String) variables.get("invitationUrl"))
            .endsWith((String) variables.get("token"));
        assertThat(variables.get("expiresAt")).isInstanceOf(LocalDateTime.class);

        // New send-tracking fields populated on the returned DTO.
        assertThat(dto.lastSendStatus()).isEqualTo("SENT");
        assertThat(dto.lastSentAt()).isNotNull();
        assertThat(dto.lastSendAttemptAt()).isNotNull();
        assertThat(dto.lastSendError()).isNull();
        assertThat(dto.sendAttemptCount()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- resend happy path

    @Test
    @DisplayName("resend on PENDING invitation marks SENT, increments sendAttemptCount, clears lastSendError")
    void resendPendingHappyPath() {
        UUID siteUuid = UUID.randomUUID();
        Site site = newSite(siteUuid, "finance", "Finance Team");
        SiteInvitation invitation = newPendingInvitation(site);
        invitation.setSendAttemptCount(1);
        invitation.setLastSendStatus(LastSendStatus.FAILED);
        invitation.setLastSendError("previous failure");

        when(siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse("finance")).thenReturn(Optional.of(site));
        when(securityService.getCurrentUser()).thenReturn("manager");
        when(securityService.isAdmin("manager")).thenReturn(false);
        when(siteMemberRepository.findBySiteIdAndUsername(siteUuid, "manager"))
            .thenReturn(Optional.of(newManager(site, "manager")));
        when(invitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(emailNotificationServiceProvider.getIfAvailable()).thenReturn(emailNotificationService);
        when(emailNotificationService.sendSync(eq("site.invitation"), eq("guest@example.com"), isNull(), any()))
            .thenReturn(new SendResult(true, null));
        when(invitationRepository.save(any(SiteInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SiteInvitationService.SiteInvitationDto dto = newService().resend("finance", invitation.getId());

        assertThat(dto.lastSendStatus()).isEqualTo("SENT");
        assertThat(dto.lastSendError()).isNull();
        assertThat(dto.lastSentAt()).isNotNull();
        assertThat(dto.sendAttemptCount()).isEqualTo(2);
        assertThat(invitation.getStatus()).isEqualTo(Status.PENDING);
    }

    // ---------------------------------------------------------------- resend failure path

    @Test
    @DisplayName("resend with sendSync failure: status FAILED, error stored, lastSentAt unchanged, count incremented")
    void resendPendingFailurePath() {
        UUID siteUuid = UUID.randomUUID();
        Site site = newSite(siteUuid, "finance", "Finance Team");
        SiteInvitation invitation = newPendingInvitation(site);
        LocalDateTime priorSentAt = LocalDateTime.of(2026, 4, 1, 12, 0);
        invitation.setLastSentAt(priorSentAt);
        invitation.setLastSendStatus(LastSendStatus.SENT);
        invitation.setSendAttemptCount(2);

        when(siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse("finance")).thenReturn(Optional.of(site));
        when(securityService.getCurrentUser()).thenReturn("manager");
        when(securityService.isAdmin("manager")).thenReturn(false);
        when(siteMemberRepository.findBySiteIdAndUsername(siteUuid, "manager"))
            .thenReturn(Optional.of(newManager(site, "manager")));
        when(invitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(emailNotificationServiceProvider.getIfAvailable()).thenReturn(emailNotificationService);
        when(emailNotificationService.sendSync(eq("site.invitation"), eq("guest@example.com"), isNull(), any()))
            .thenReturn(new SendResult(false, "SMTP send failed: connection refused"));
        when(invitationRepository.save(any(SiteInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SiteInvitationService.SiteInvitationDto dto = newService().resend("finance", invitation.getId());

        assertThat(dto.lastSendStatus()).isEqualTo("FAILED");
        assertThat(dto.lastSendError()).isEqualTo("SMTP send failed: connection refused");
        assertThat(dto.lastSentAt()).isEqualTo(priorSentAt); // unchanged
        assertThat(dto.sendAttemptCount()).isEqualTo(3);
    }

    // ---------------------------------------------------------------- resend rejects non-PENDING

    @Test
    @DisplayName("resend rejects ACCEPTED with IllegalArgumentException naming the current status")
    void resendRejectsAccepted() {
        UUID siteUuid = UUID.randomUUID();
        Site site = newSite(siteUuid, "finance", "Finance Team");
        SiteInvitation invitation = newPendingInvitation(site);
        invitation.setStatus(Status.ACCEPTED);

        when(siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse("finance")).thenReturn(Optional.of(site));
        when(securityService.getCurrentUser()).thenReturn("manager");
        when(securityService.isAdmin("manager")).thenReturn(false);
        when(siteMemberRepository.findBySiteIdAndUsername(siteUuid, "manager"))
            .thenReturn(Optional.of(newManager(site, "manager")));
        when(invitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));

        SiteInvitationService service = newService();

        assertThatThrownBy(() -> service.resend("finance", invitation.getId()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("current status: ACCEPTED");

        verify(invitationRepository, never()).save(any(SiteInvitation.class));
        // EXPIRED, REJECTED, CANCELLED reject identically — covered conceptually
        // by the same code path; one happy/sad pair plus this guard suffices.
    }

    @Test
    @DisplayName("resend rejects EXPIRED with IllegalArgumentException naming the current status")
    void resendRejectsExpired() {
        UUID siteUuid = UUID.randomUUID();
        Site site = newSite(siteUuid, "finance", "Finance Team");
        SiteInvitation invitation = newPendingInvitation(site);
        invitation.setStatus(Status.EXPIRED);

        when(siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse("finance")).thenReturn(Optional.of(site));
        when(securityService.getCurrentUser()).thenReturn("manager");
        when(securityService.isAdmin("manager")).thenReturn(false);
        when(siteMemberRepository.findBySiteIdAndUsername(siteUuid, "manager"))
            .thenReturn(Optional.of(newManager(site, "manager")));
        when(invitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> newService().resend("finance", invitation.getId()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("current status: EXPIRED");
    }

    // ---------------------------------------------------------------- resend rejects unknown id

    @Test
    @DisplayName("resend rejects unknown invitation id with ResourceNotFoundException")
    void resendRejectsUnknownId() {
        UUID siteUuid = UUID.randomUUID();
        Site site = newSite(siteUuid, "finance", "Finance Team");
        UUID unknownId = UUID.randomUUID();

        when(siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse("finance")).thenReturn(Optional.of(site));
        when(securityService.getCurrentUser()).thenReturn("manager");
        when(securityService.isAdmin("manager")).thenReturn(false);
        when(siteMemberRepository.findBySiteIdAndUsername(siteUuid, "manager"))
            .thenReturn(Optional.of(newManager(site, "manager")));
        when(invitationRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().resend("finance", unknownId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining(unknownId.toString());
    }

    // ---------------------------------------------------------------- resend rejects when invitation belongs to other site

    @Test
    @DisplayName("resend on invitation belonging to another site responds with ResourceNotFoundException (no leak)")
    void resendRejectsCrossSiteInvitation() {
        UUID siteUuid = UUID.randomUUID();
        Site site = newSite(siteUuid, "finance", "Finance Team");
        Site otherSite = newSite(UUID.randomUUID(), "engineering", "Engineering");
        SiteInvitation invitation = newPendingInvitation(otherSite);

        when(siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse("finance")).thenReturn(Optional.of(site));
        when(securityService.getCurrentUser()).thenReturn("manager");
        when(securityService.isAdmin("manager")).thenReturn(false);
        when(siteMemberRepository.findBySiteIdAndUsername(siteUuid, "manager"))
            .thenReturn(Optional.of(newManager(site, "manager")));
        when(invitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> newService().resend("finance", invitation.getId()))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------------- resend rejects non-manager/non-admin

    @Test
    @DisplayName("resend rejects non-manager/non-admin via ensureCanManageInvitations (AccessDeniedException)")
    void resendRejectsNonManager() {
        UUID siteUuid = UUID.randomUUID();
        Site site = newSite(siteUuid, "finance", "Finance Team");

        when(siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse("finance")).thenReturn(Optional.of(site));
        when(securityService.getCurrentUser()).thenReturn("bystander");
        when(securityService.isAdmin("bystander")).thenReturn(false);
        when(siteMemberRepository.findBySiteIdAndUsername(siteUuid, "bystander")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().resend("finance", UUID.randomUUID()))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("site manager");

        verify(invitationRepository, never()).findById(any(UUID.class));
    }

    // ====================================================================== //
    // Bulk invite tests (Phase 2 logging audit + sanitised error category)     //
    // ====================================================================== //

    /**
     * Set up a manager + a site + a sendSync that returns success. The bulk
     * tests share this so individual cases only stub what they need to differ.
     */
    private Site setUpBulkBaseSite(String slug) {
        UUID siteUuid = UUID.randomUUID();
        Site site = newSite(siteUuid, slug, slug + " title");
        when(siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse(slug)).thenReturn(Optional.of(site));
        when(securityService.getCurrentUser()).thenReturn("manager");
        when(securityService.isAdmin("manager")).thenReturn(false);
        when(siteMemberRepository.findBySiteIdAndUsername(siteUuid, "manager"))
            .thenReturn(Optional.of(newManager(site, "manager")));
        return site;
    }

    private void stubSuccessfulSave() {
        when(invitationRepository.save(any(SiteInvitation.class))).thenAnswer(invocation -> {
            SiteInvitation inv = invocation.getArgument(0);
            if (inv.getId() == null) {
                inv.setId(UUID.randomUUID());
            }
            if (inv.getCreatedDate() == null) {
                inv.setCreatedDate(LocalDateTime.of(2026, 5, 24, 10, 0));
            }
            return inv;
        });
    }

    @Test
    @DisplayName("inviteBulk creates one SUCCESS row per email when all sends succeed")
    void inviteBulkAllSuccess() {
        setUpBulkBaseSite("finance");
        when(invitationRepository.findByInviteeEmailAndStatusIn(any(), any())).thenReturn(List.of());
        when(emailNotificationServiceProvider.getIfAvailable()).thenReturn(emailNotificationService);
        when(emailNotificationService.sendSync(eq("site.invitation"), any(), isNull(), any()))
            .thenReturn(new SendResult(true, null));
        stubSuccessfulSave();

        SiteInvitationService.BulkInviteResponse response = newService().inviteBulk(
            "finance",
            new SiteInvitationService.BulkInviteRequest(
                List.of("alice@example.com", "bob@example.com", "claire@example.com"),
                "CONSUMER",
                "Welcome"
            )
        );

        assertThat(response.results()).hasSize(3);
        assertThat(response.results()).allSatisfy(r -> {
            assertThat(r.status()).isEqualTo(SiteInvitationService.BulkInviteResult.Status.SUCCESS);
            assertThat(r.invitation()).isNotNull();
            assertThat(r.invitation().lastSendStatus()).isEqualTo("SENT");
            assertThat(r.errorCategory()).isNull();
            assertThat(r.errorMessage()).isNull();
        });
    }

    @Test
    @DisplayName("inviteBulk partial failure does NOT roll back successful rows (per-row REQUIRES_NEW)")
    void inviteBulkPartialFailureDoesNotRollback() {
        setUpBulkBaseSite("finance");
        when(invitationRepository.findByInviteeEmailAndStatusIn(eq("dup@example.com"), any()))
            .thenAnswer(invocation -> {
                // Simulate an existing PENDING in this site so row 1 is DUPLICATE_PENDING.
                SiteInvitation existing = newPendingInvitation(
                    newSite(
                        siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse("finance").get().getId(),
                        "finance",
                        "Finance Team"
                    )
                );
                existing.setInviteeEmail("dup@example.com");
                return List.of(existing);
            });
        when(invitationRepository.findByInviteeEmailAndStatusIn(
            org.mockito.ArgumentMatchers.argThat(e -> !"dup@example.com".equals(e)), any()
        )).thenReturn(List.of());
        when(emailNotificationServiceProvider.getIfAvailable()).thenReturn(emailNotificationService);
        when(emailNotificationService.sendSync(eq("site.invitation"), any(), isNull(), any()))
            .thenReturn(new SendResult(true, null));
        stubSuccessfulSave();

        SiteInvitationService.BulkInviteResponse response = newService().inviteBulk(
            "finance",
            new SiteInvitationService.BulkInviteRequest(
                List.of("alice@example.com", "dup@example.com", "claire@example.com"),
                "CONSUMER",
                null
            )
        );

        assertThat(response.results()).hasSize(3);
        assertThat(response.results().get(0).status())
            .isEqualTo(SiteInvitationService.BulkInviteResult.Status.SUCCESS);
        assertThat(response.results().get(1).status())
            .isEqualTo(SiteInvitationService.BulkInviteResult.Status.FAILED);
        assertThat(response.results().get(1).errorCategory())
            .isEqualTo(SiteInvitationService.BulkInviteErrorCategory.DUPLICATE_PENDING);
        assertThat(response.results().get(2).status())
            .isEqualTo(SiteInvitationService.BulkInviteResult.Status.SUCCESS);
    }

    @Test
    @DisplayName("inviteBulk classifies blank emails as INVALID_EMAIL with fixed copy")
    void inviteBulkInvalidEmailFixedCopy() {
        setUpBulkBaseSite("finance");

        SiteInvitationService.BulkInviteResponse response = newService().inviteBulk(
            "finance",
            new SiteInvitationService.BulkInviteRequest(
                List.of("   ", ""),
                "CONSUMER",
                null
            )
        );

        assertThat(response.results()).hasSize(2);
        assertThat(response.results()).allSatisfy(r -> {
            assertThat(r.status()).isEqualTo(SiteInvitationService.BulkInviteResult.Status.FAILED);
            assertThat(r.errorCategory())
                .isEqualTo(SiteInvitationService.BulkInviteErrorCategory.INVALID_EMAIL);
            assertThat(r.errorMessage())
                .isEqualTo(SiteInvitationService.BULK_ERROR_MESSAGE_INVALID_EMAIL);
            assertThat(r.invitation()).isNull();
        });
    }

    @Test
    @DisplayName("inviteBulk DUPLICATE_PENDING errorMessage is the fixed copy, not the raw exception text containing the email")
    void inviteBulkDuplicatePendingDoesNotEchoEmail() {
        setUpBulkBaseSite("finance");
        when(invitationRepository.findByInviteeEmailAndStatusIn(any(), any()))
            .thenAnswer(invocation -> {
                SiteInvitation existing = newPendingInvitation(
                    newSite(
                        siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse("finance").get().getId(),
                        "finance",
                        "Finance Team"
                    )
                );
                existing.setInviteeEmail((String) invocation.getArgument(0));
                return List.of(existing);
            });

        // Probe email shaped like a UI injection attempt.
        String probeEmail = "<script>alert(1)</script>@example.com";
        SiteInvitationService.BulkInviteResponse response = newService().inviteBulk(
            "finance",
            new SiteInvitationService.BulkInviteRequest(
                List.of(probeEmail),
                "CONSUMER",
                null
            )
        );

        SiteInvitationService.BulkInviteResult result = response.results().get(0);
        assertThat(result.status()).isEqualTo(SiteInvitationService.BulkInviteResult.Status.FAILED);
        assertThat(result.errorCategory())
            .isEqualTo(SiteInvitationService.BulkInviteErrorCategory.DUPLICATE_PENDING);
        // Fixed copy. The probe substring must NOT be present in errorMessage.
        assertThat(result.errorMessage())
            .isEqualTo(SiteInvitationService.BULK_ERROR_MESSAGE_DUPLICATE_PENDING);
        assertThat(result.errorMessage()).doesNotContain("<script>");
    }

    @Test
    @DisplayName("inviteBulk EMAIL_SEND_FAILED stays SUCCESS — invitation row exists, operator can resend")
    void inviteBulkEmailSendFailureStillSuccess() {
        setUpBulkBaseSite("finance");
        when(invitationRepository.findByInviteeEmailAndStatusIn(any(), any())).thenReturn(List.of());
        when(emailNotificationServiceProvider.getIfAvailable()).thenReturn(emailNotificationService);
        when(emailNotificationService.sendSync(eq("site.invitation"), any(), isNull(), any()))
            .thenReturn(new SendResult(false, "SMTP relay rejected"));
        stubSuccessfulSave();

        SiteInvitationService.BulkInviteResponse response = newService().inviteBulk(
            "finance",
            new SiteInvitationService.BulkInviteRequest(
                List.of("alice@example.com"),
                "CONSUMER",
                null
            )
        );

        SiteInvitationService.BulkInviteResult result = response.results().get(0);
        assertThat(result.status()).isEqualTo(SiteInvitationService.BulkInviteResult.Status.SUCCESS);
        assertThat(result.invitation()).isNotNull();
        assertThat(result.invitation().lastSendStatus()).isEqualTo("FAILED");
        assertThat(result.invitation().lastSendError()).isEqualTo("SMTP relay rejected");
        // Per-row status is SUCCESS (the invitation row exists); the frontend
        // toast logic reads lastSendStatus to surface the email-send miss.
        assertThat(result.errorCategory()).isNull();
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    @DisplayName("inviteBulk INTERNAL_ERROR errorMessage carries class name only — never the raw exception message")
    void inviteBulkInternalErrorSanitisedErrorMessage() {
        setUpBulkBaseSite("finance");
        String probeText = "USER_PII_FROM_EXCEPTION_LEAK_PROBE";
        when(invitationRepository.findByInviteeEmailAndStatusIn(any(), any())).thenReturn(List.of());
        // Force createInvitationRow to throw a generic RuntimeException whose message
        // would leak sensitive content if echoed back. The Phase 2 logging audit
        // discipline forbids this.
        when(invitationRepository.save(any(SiteInvitation.class)))
            .thenThrow(new RuntimeException(probeText));

        SiteInvitationService.BulkInviteResponse response = newService().inviteBulk(
            "finance",
            new SiteInvitationService.BulkInviteRequest(
                List.of("alice@example.com"),
                "CONSUMER",
                null
            )
        );

        SiteInvitationService.BulkInviteResult result = response.results().get(0);
        assertThat(result.status()).isEqualTo(SiteInvitationService.BulkInviteResult.Status.FAILED);
        assertThat(result.errorCategory())
            .isEqualTo(SiteInvitationService.BulkInviteErrorCategory.INTERNAL_ERROR);
        // Class name preserved (operator-actionable); raw message ABSENT.
        assertThat(result.errorMessage()).contains("RuntimeException");
        assertThat(result.errorMessage()).doesNotContain(probeText);
    }

    @Test
    @DisplayName("inviteBulk shared role applies to every row")
    void inviteBulkSharedRoleAppliedToEveryRow() {
        setUpBulkBaseSite("finance");
        when(invitationRepository.findByInviteeEmailAndStatusIn(any(), any())).thenReturn(List.of());
        when(emailNotificationServiceProvider.getIfAvailable()).thenReturn(emailNotificationService);
        when(emailNotificationService.sendSync(eq("site.invitation"), any(), isNull(), any()))
            .thenReturn(new SendResult(true, null));
        stubSuccessfulSave();

        SiteInvitationService.BulkInviteResponse response = newService().inviteBulk(
            "finance",
            new SiteInvitationService.BulkInviteRequest(
                List.of("alice@example.com", "bob@example.com"),
                "manager",
                null
            )
        );

        assertThat(response.results()).hasSize(2);
        assertThat(response.results()).allSatisfy(r -> {
            assertThat(r.invitation().invitedRole()).isEqualTo("MANAGER");
        });
    }

    @Test
    @DisplayName("inviteBulk empty inviteeEmails throws IllegalArgumentException at the request boundary")
    void inviteBulkEmptyArrayThrows() {
        setUpBulkBaseSite("finance");
        SiteInvitationService service = newService();

        assertThatThrownBy(() -> service.inviteBulk(
            "finance",
            new SiteInvitationService.BulkInviteRequest(List.of(), "CONSUMER", null)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at least one entry");

        assertThatThrownBy(() -> service.inviteBulk(
            "finance",
            new SiteInvitationService.BulkInviteRequest(null, "CONSUMER", null)
        ))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("inviteBulk security gate enforced — non-manager non-admin gets AccessDeniedException, no rows processed")
    void inviteBulkSecurityGateEnforced() {
        UUID siteUuid = UUID.randomUUID();
        Site site = newSite(siteUuid, "finance", "Finance Team");
        when(siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse("finance")).thenReturn(Optional.of(site));
        when(securityService.getCurrentUser()).thenReturn("bystander");
        when(securityService.isAdmin("bystander")).thenReturn(false);
        when(siteMemberRepository.findBySiteIdAndUsername(siteUuid, "bystander")).thenReturn(Optional.empty());

        SiteInvitationService service = newService();

        assertThatThrownBy(() -> service.inviteBulk(
            "finance",
            new SiteInvitationService.BulkInviteRequest(List.of("alice@example.com"), "CONSUMER", null)
        ))
            .isInstanceOf(AccessDeniedException.class);

        verify(invitationRepository, never()).save(any(SiteInvitation.class));
    }
}
