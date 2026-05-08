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

    private SiteInvitationService newService() {
        return new SiteInvitationService(
            invitationRepository,
            siteRepository,
            siteMemberRepository,
            securityService,
            activityEventListener,
            emailNotificationServiceProvider
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
}
