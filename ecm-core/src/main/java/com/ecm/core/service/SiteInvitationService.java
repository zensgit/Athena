package com.ecm.core.service;

import com.ecm.core.entity.Site;
import com.ecm.core.entity.SiteInvitation;
import com.ecm.core.entity.SiteInvitation.Status;
import com.ecm.core.entity.SiteMember;
import com.ecm.core.entity.SiteMember.SiteMemberRole;
import com.ecm.core.exception.AccessDeniedException;
import com.ecm.core.exception.ResourceNotFoundException;
import com.ecm.core.integration.email.notify.EmailNotificationService;
import com.ecm.core.repository.SiteInvitationRepository;
import com.ecm.core.repository.SiteMemberRepository;
import com.ecm.core.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class SiteInvitationService {

    private static final int TOKEN_BYTES = 32;
    private static final int INVITATION_TTL_DAYS = 7;

    private final SiteInvitationRepository invitationRepository;
    private final SiteRepository siteRepository;
    private final SiteMemberRepository siteMemberRepository;
    private final SecurityService securityService;
    private final ActivityEventListener activityEventListener;
    private final ObjectProvider<EmailNotificationService> emailNotificationServiceProvider;

    // ------------------------------------------------------------------ invite

    @Transactional
    public SiteInvitationDto invite(String siteId, InviteRequest request) {
        Site site = loadSite(siteId);
        String currentUser = securityService.getCurrentUser();
        ensureCanManageInvitations(site, currentUser);

        String normalizedEmail = normalizeEmail(request.inviteeEmail());
        if (normalizedEmail == null) {
            throw new IllegalArgumentException("inviteeEmail must not be blank");
        }

        // Reject if invitee already has a PENDING invitation for this site
        List<SiteInvitation> existing = invitationRepository
            .findByInviteeEmailAndStatusIn(normalizedEmail, EnumSet.of(Status.PENDING));
        boolean alreadyPending = existing.stream()
            .anyMatch(inv -> inv.getSite().getId().equals(site.getId()));
        if (alreadyPending) {
            throw new IllegalArgumentException(
                "A pending invitation already exists for " + normalizedEmail + " in site " + siteId);
        }

        String role = normalizeRole(request.invitedRole());

        SiteInvitation invitation = new SiteInvitation();
        invitation.setSite(site);
        invitation.setInviteeEmail(normalizedEmail);
        invitation.setInvitedRole(role);
        invitation.setMessage(request.message());
        invitation.setInvitedBy(currentUser);
        invitation.setToken(generateToken());
        invitation.setStatus(Status.PENDING);
        invitation.setExpiresAt(LocalDateTime.now().plusDays(INVITATION_TTL_DAYS));

        SiteInvitation saved = invitationRepository.save(invitation);
        log.info("Site invitation created: site={} email={} by={}", siteId, normalizedEmail, currentUser);

        sendInvitationEmail(saved, site);

        return toDto(saved);
    }

    // ------------------------------------------------------------------ accept

    @Transactional
    public SiteInvitationDto accept(String token) {
        SiteInvitation invitation = loadByToken(token);
        verifyPendingAndNotExpired(invitation);

        String currentUser = securityService.getCurrentUser();
        Site site = invitation.getSite();

        // Check invitee is not already a member (accept-time guard)
        if (siteMemberRepository.findBySiteIdAndUsername(site.getId(), currentUser).isPresent()) {
            // Treat as accepted — mark invitation done without re-adding
            invitation.setStatus(Status.ACCEPTED);
            invitation.setAcceptedAt(LocalDateTime.now());
            invitation.setInviteeUsername(currentUser);
            invitationRepository.save(invitation);
            log.info("Site invitation accepted (already member): site={} user={}", site.getSiteId(), currentUser);
            return toDto(invitation);
        }

        SiteMemberRole role = parseSiteMemberRole(invitation.getInvitedRole());
        SiteMember member = new SiteMember();
        member.setSite(site);
        member.setUsername(currentUser);
        member.setRole(role);
        siteMemberRepository.save(member);

        invitation.setStatus(Status.ACCEPTED);
        invitation.setAcceptedAt(LocalDateTime.now());
        invitation.setInviteeUsername(currentUser);
        SiteInvitation saved = invitationRepository.save(invitation);

        activityEventListener.postSiteMemberActivity(
            "site.member.added",
            currentUser,
            site.getSiteId(),
            currentUser,
            role.name()
        );
        log.info("Site invitation accepted: site={} user={} role={}", site.getSiteId(), currentUser, role);
        return toDto(saved);
    }

    // ------------------------------------------------------------------ reject

    @Transactional
    public SiteInvitationDto reject(String token) {
        SiteInvitation invitation = loadByToken(token);
        verifyPending(invitation);

        invitation.setStatus(Status.REJECTED);
        SiteInvitation saved = invitationRepository.save(invitation);
        log.info("Site invitation rejected: site={} email={}", saved.getSite().getSiteId(), saved.getInviteeEmail());
        return toDto(saved);
    }

    // ------------------------------------------------------------------ cancel

    @Transactional
    public void cancel(String siteId, UUID invitationId) {
        Site site = loadSite(siteId);
        String currentUser = securityService.getCurrentUser();
        ensureCanManageInvitations(site, currentUser);

        SiteInvitation invitation = invitationRepository.findById(invitationId)
            .orElseThrow(() -> new ResourceNotFoundException("Invitation not found: " + invitationId));

        if (!invitation.getSite().getId().equals(site.getId())) {
            throw new ResourceNotFoundException("Invitation not found: " + invitationId);
        }

        if (invitation.getStatus() != Status.PENDING) {
            throw new IllegalArgumentException(
                "Only PENDING invitations can be cancelled; current status: " + invitation.getStatus());
        }

        invitation.setStatus(Status.CANCELLED);
        invitationRepository.save(invitation);
        log.info("Site invitation cancelled: site={} id={} by={}", siteId, invitationId, currentUser);
    }

    // ------------------------------------------------------------------ list

    @Transactional(readOnly = true)
    public List<SiteInvitationDto> listForSite(String siteId) {
        Site site = loadSite(siteId);
        String currentUser = securityService.getCurrentUser();
        ensureCanManageInvitations(site, currentUser);

        return invitationRepository.findBySiteIdOrderByCreatedDateDesc(site.getId())
            .stream()
            .map(this::toDto)
            .toList();
    }

    // ------------------------------------------------------------------ cleanup

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpired() {
        List<SiteInvitation> expired = invitationRepository
            .findByExpiresAtBeforeAndStatus(LocalDateTime.now(), Status.PENDING);
        if (expired.isEmpty()) {
            return;
        }
        expired.forEach(inv -> inv.setStatus(Status.EXPIRED));
        invitationRepository.saveAll(expired);
        log.info("Marked {} site invitation(s) as EXPIRED", expired.size());
    }

    // ------------------------------------------------------------------ helpers

    private Site loadSite(String siteId) {
        return siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse(siteId)
            .orElseThrow(() -> new ResourceNotFoundException("Site not found: " + siteId));
    }

    private SiteInvitation loadByToken(String token) {
        return invitationRepository.findByToken(token)
            .orElseThrow(() -> new ResourceNotFoundException("Invitation not found for token"));
    }

    private void verifyPending(SiteInvitation invitation) {
        if (invitation.getStatus() != Status.PENDING) {
            throw new IllegalArgumentException(
                "Invitation is no longer pending; current status: " + invitation.getStatus());
        }
    }

    private void verifyPendingAndNotExpired(SiteInvitation invitation) {
        verifyPending(invitation);
        if (LocalDateTime.now().isAfter(invitation.getExpiresAt())) {
            invitation.setStatus(Status.EXPIRED);
            invitationRepository.save(invitation);
            throw new IllegalArgumentException("Invitation has expired");
        }
    }

    private void ensureCanManageInvitations(Site site, String username) {
        if (securityService.isAdmin(username)) {
            return;
        }
        boolean isManager = siteMemberRepository.findBySiteIdAndUsername(site.getId(), username)
            .map(member -> member.getRole() == SiteMemberRole.MANAGER)
            .orElse(false);
        if (!isManager) {
            throw new AccessDeniedException("You must be a site manager to manage invitations for site: " + site.getSiteId());
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return SiteMemberRole.CONSUMER.name();
        }
        String upper = role.trim().toUpperCase(Locale.ROOT);
        try {
            SiteMemberRole.valueOf(upper);
            return upper;
        } catch (IllegalArgumentException ex) {
            return SiteMemberRole.CONSUMER.name();
        }
    }

    private SiteMemberRole parseSiteMemberRole(String role) {
        try {
            return SiteMemberRole.valueOf(role);
        } catch (IllegalArgumentException ex) {
            return SiteMemberRole.CONSUMER;
        }
    }

    private void sendInvitationEmail(SiteInvitation invitation, Site site) {
        EmailNotificationService emailService = emailNotificationServiceProvider.getIfAvailable();
        if (emailService == null) {
            return;
        }
        try {
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("siteTitle", site.getTitle());
            variables.put("siteId", site.getSiteId());
            variables.put("invitedBy", invitation.getInvitedBy());
            variables.put("token", invitation.getToken());
            variables.put("role", invitation.getInvitedRole());
            variables.put("message", invitation.getMessage() != null ? invitation.getMessage() : "");
            variables.put("expiresAt", invitation.getExpiresAt());

            emailService.send(
                "site.invitation",
                invitation.getInviteeEmail(),
                null,
                variables
            );
        } catch (Exception ex) {
            log.warn("Failed to send invitation email to {}: {}", invitation.getInviteeEmail(), ex.getMessage());
        }
    }

    private SiteInvitationDto toDto(SiteInvitation inv) {
        Site site = inv.getSite();
        return new SiteInvitationDto(
            inv.getId(),
            site != null ? site.getId() : null,
            site != null ? site.getTitle() : null,
            inv.getInviteeEmail(),
            inv.getInviteeUsername(),
            inv.getInvitedRole(),
            inv.getStatus() != null ? inv.getStatus().name() : null,
            inv.getMessage(),
            inv.getInvitedBy(),
            inv.getExpiresAt(),
            inv.getAcceptedAt(),
            inv.getCreatedDate()
        );
    }

    // ------------------------------------------------------------------ DTOs/records

    public record InviteRequest(String inviteeEmail, String invitedRole, String message) {}

    public record SiteInvitationDto(
        UUID id,
        UUID siteId,
        String siteTitle,
        String inviteeEmail,
        String inviteeUsername,
        String invitedRole,
        String status,
        String message,
        String invitedBy,
        LocalDateTime expiresAt,
        LocalDateTime acceptedAt,
        LocalDateTime createdDate
    ) {}
}
