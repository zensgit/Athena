package com.ecm.core.service;

import com.ecm.core.entity.Site;
import com.ecm.core.entity.SiteInvitation;
import com.ecm.core.entity.SiteMember;
import com.ecm.core.entity.SiteMember.SiteMemberRole;
import com.ecm.core.integration.email.notify.EmailNotificationService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

    @Test
    @DisplayName("invite sends the seeded site.invitation email template with complete variables")
    void inviteSendsSiteInvitationEmailTemplate() {
        Site site = new Site();
        UUID siteUuid = UUID.randomUUID();
        site.setId(siteUuid);
        site.setSiteId("finance");
        site.setTitle("Finance Team");

        SiteMember manager = new SiteMember();
        manager.setSite(site);
        manager.setUsername("manager");
        manager.setRole(SiteMemberRole.MANAGER);

        when(siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse("finance")).thenReturn(Optional.of(site));
        when(securityService.getCurrentUser()).thenReturn("manager");
        when(securityService.isAdmin("manager")).thenReturn(false);
        when(siteMemberRepository.findBySiteIdAndUsername(siteUuid, "manager")).thenReturn(Optional.of(manager));
        when(invitationRepository.findByInviteeEmailAndStatusIn(eq("guest@example.com"), any())).thenReturn(List.of());
        when(emailNotificationServiceProvider.getIfAvailable()).thenReturn(emailNotificationService);
        when(invitationRepository.save(any(SiteInvitation.class))).thenAnswer(invocation -> {
            SiteInvitation invitation = invocation.getArgument(0);
            invitation.setId(UUID.randomUUID());
            invitation.setCreatedDate(LocalDateTime.of(2026, 4, 26, 10, 0));
            return invitation;
        });

        SiteInvitationService service = new SiteInvitationService(
            invitationRepository,
            siteRepository,
            siteMemberRepository,
            securityService,
            activityEventListener,
            emailNotificationServiceProvider
        );

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
        verify(emailNotificationService).send(
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
    }
}
