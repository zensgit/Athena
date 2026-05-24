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
import com.ecm.core.repository.SiteInvitationRepository;
import com.ecm.core.repository.SiteMemberRepository;
import com.ecm.core.repository.SiteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class SiteInvitationService {

    private static final int TOKEN_BYTES = 32;
    private static final int INVITATION_TTL_DAYS = 7;

    // Phase 2 logging audit + Bulk slice (2026-05-24): stable fixed copies used in
    // BulkInviteResult.errorMessage. Never embed raw ex.getMessage() or the
    // invitee-supplied email back into the error string — both are operator-
    // controlled inputs that could be repurposed for log/UI injection probes.
    static final String BULK_ERROR_MESSAGE_INVALID_EMAIL =
        "Email address is blank or invalid.";
    static final String BULK_ERROR_MESSAGE_DUPLICATE_PENDING =
        "A pending invitation already exists for this email in this site.";
    static final String BULK_ERROR_MESSAGE_INTERNAL =
        "Unexpected error during invitation creation";

    private final SiteInvitationRepository invitationRepository;
    private final SiteRepository siteRepository;
    private final SiteMemberRepository siteMemberRepository;
    private final SecurityService securityService;
    private final ActivityEventListener activityEventListener;
    private final ObjectProvider<EmailNotificationService> emailNotificationServiceProvider;
    private final TransactionTemplate bulkRowTransactionTemplate;

    @Value("${ecm.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    /**
     * Explicit constructor because the bulk-create slice needs to inject a
     * Spring {@link PlatformTransactionManager} to build the per-row
     * {@link TransactionTemplate}. Lombok {@code @RequiredArgsConstructor} would
     * not let us derive the template at construction time without an extra
     * {@code @PostConstruct} method, so the explicit form keeps the wiring in
     * one place. The template is held as a field (constructed once) rather than
     * built per call.
     *
     * <p>Per-row transactions use {@code REQUIRES_NEW} via the template so a
     * duplicate-pending or send-failure on row N does NOT roll back rows
     * 0..N-1. Self-invocation of an {@code @Transactional} private method
     * would silently skip Spring's transaction proxy — see the brief at
     * {@code docs/SITE_INVITATION_BULK_CREATE_DESIGN_20260524.md}
     * §"Transactional strategy" for the rationale.
     */
    public SiteInvitationService(
        SiteInvitationRepository invitationRepository,
        SiteRepository siteRepository,
        SiteMemberRepository siteMemberRepository,
        SecurityService securityService,
        ActivityEventListener activityEventListener,
        ObjectProvider<EmailNotificationService> emailNotificationServiceProvider,
        PlatformTransactionManager transactionManager
    ) {
        this.invitationRepository = invitationRepository;
        this.siteRepository = siteRepository;
        this.siteMemberRepository = siteMemberRepository;
        this.securityService = securityService;
        this.activityEventListener = activityEventListener;
        this.emailNotificationServiceProvider = emailNotificationServiceProvider;
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(
            org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.bulkRowTransactionTemplate = template;
    }

    // ------------------------------------------------------------------ invite

    @Transactional
    public SiteInvitationDto invite(String siteId, InviteRequest request) {
        Site site = loadSite(siteId);
        String currentUser = securityService.getCurrentUser();
        ensureCanManageInvitations(site, currentUser);

        return createInvitationRow(site, currentUser, request);
    }

    // -------------------------------------------------------------- invite-bulk

    /**
     * Bulk create site invitations. One per-row {@code REQUIRES_NEW}
     * transaction per email so duplicates / send failures on row N do NOT
     * roll back rows 0..N-1. Security check ({@code ensureCanManageInvitations})
     * runs once at the request boundary.
     *
     * <p>Per-row error categories:
     * <ul>
     *   <li>{@link BulkInviteErrorCategory#INVALID_EMAIL} — blank / empty input.</li>
     *   <li>{@link BulkInviteErrorCategory#DUPLICATE_PENDING} — existing PENDING for this site.</li>
     *   <li>{@link BulkInviteErrorCategory#INTERNAL_ERROR} — any other {@code Throwable}.</li>
     * </ul>
     *
     * <p>Email-send failure is NOT a per-row failure here — the invitation row
     * exists, the operator can resend. Such rows return
     * {@link BulkInviteResult.Status#SUCCESS} with the returned DTO carrying
     * {@code lastSendStatus = FAILED}. The frontend toast logic distinguishes
     * the two cases.
     *
     * <p>Per the {@code feedback_sanitize_throwable_cause_for_log_emission}
     * memory: the {@code INTERNAL_ERROR} branch does NOT include
     * {@code ex.getMessage()} in the response, and does NOT pass the raw
     * {@code Throwable} to SLF4J. Only the exception class simple name is
     * surfaced.
     */
    public BulkInviteResponse inviteBulk(String siteId, BulkInviteRequest request) {
        if (request == null || request.inviteeEmails() == null || request.inviteeEmails().isEmpty()) {
            throw new IllegalArgumentException("inviteeEmails must contain at least one entry");
        }

        Site site = loadSite(siteId);
        String currentUser = securityService.getCurrentUser();
        ensureCanManageInvitations(site, currentUser);

        List<BulkInviteResult> results = new ArrayList<>(request.inviteeEmails().size());

        for (String rawEmail : request.inviteeEmails()) {
            String responseEmail = rawEmail == null ? "" : rawEmail.trim();
            InviteRequest perRowRequest = new InviteRequest(
                rawEmail,
                request.invitedRole(),
                request.message()
            );

            try {
                SiteInvitationDto dto = bulkRowTransactionTemplate.execute(status ->
                    createInvitationRow(site, currentUser, perRowRequest)
                );
                results.add(BulkInviteResult.success(
                    dto != null ? dto.inviteeEmail() : responseEmail,
                    dto
                ));
            } catch (BlankInviteeEmailException ex) {
                results.add(BulkInviteResult.failed(
                    responseEmail,
                    BulkInviteErrorCategory.INVALID_EMAIL,
                    BULK_ERROR_MESSAGE_INVALID_EMAIL
                ));
            } catch (DuplicatePendingInvitationException ex) {
                results.add(BulkInviteResult.failed(
                    responseEmail,
                    BulkInviteErrorCategory.DUPLICATE_PENDING,
                    BULK_ERROR_MESSAGE_DUPLICATE_PENDING
                ));
            } catch (RuntimeException ex) {
                // Phase 2 logging audit follow-up #1 (cause-chain leak prevention):
                // do NOT pass `ex` to SLF4J; do NOT echo ex.getMessage() back to the
                // caller. Log only the class simple name at DEBUG; surface fixed copy
                // + class name to the client.
                log.debug(
                    "Bulk invite per-row internal error: siteId={} class={}",
                    siteId,
                    ex.getClass().getSimpleName()
                );
                results.add(BulkInviteResult.failed(
                    responseEmail,
                    BulkInviteErrorCategory.INTERNAL_ERROR,
                    BULK_ERROR_MESSAGE_INTERNAL
                        + " (" + ex.getClass().getSimpleName() + ")."
                ));
            }
        }

        log.info(
            "Site invitation bulk completed: site={} total={} success={} failed={} by={}",
            siteId,
            results.size(),
            results.stream().filter(r -> r.status() == BulkInviteResult.Status.SUCCESS).count(),
            results.stream().filter(r -> r.status() == BulkInviteResult.Status.FAILED).count(),
            currentUser
        );
        return new BulkInviteResponse(results);
    }

    /**
     * Shared per-row creation logic. Called by both {@link #invite(String, InviteRequest)}
     * and the bulk path. Throws typed subclasses of {@link IllegalArgumentException}
     * so the bulk catch can dispatch without inspecting messages while the
     * single-invite path's {@code @ExceptionHandler(IllegalArgumentException.class)}
     * mapping at {@code RestExceptionHandler:34} still maps to HTTP 400.
     *
     * <p>Site load + security check are the caller's responsibility — this
     * helper only does the row work.
     */
    private SiteInvitationDto createInvitationRow(Site site, String currentUser, InviteRequest request) {
        String normalizedEmail = normalizeEmail(request.inviteeEmail());
        if (normalizedEmail == null) {
            throw new BlankInviteeEmailException();
        }

        // Reject if invitee already has a PENDING invitation for this site
        List<SiteInvitation> existing = invitationRepository
            .findByInviteeEmailAndStatusIn(normalizedEmail, EnumSet.of(Status.PENDING));
        boolean alreadyPending = existing.stream()
            .anyMatch(inv -> inv.getSite().getId().equals(site.getId()));
        if (alreadyPending) {
            throw new DuplicatePendingInvitationException(normalizedEmail, site.getSiteId());
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
        log.info("Site invitation created: site={} email={} by={}", site.getSiteId(), normalizedEmail, currentUser);

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

    // ------------------------------------------------------------------ resend

    /**
     * Manually re-attempt sending the invitation email. Strict PENDING-only
     * gate mirrors {@link #cancel(String, UUID)}. SMTP latency executes inside
     * the transaction so the per-row send-tracking columns reflect the latest
     * attempt; v1 has no auto retry worker — every retry comes through this
     * endpoint.
     */
    @Transactional
    public SiteInvitationDto resend(String siteId, UUID invitationId) {
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
                "Only PENDING invitations can be resent; current status: " + invitation.getStatus());
        }

        sendInvitationEmail(invitation, site);
        log.info("Site invitation resent: site={} id={} by={}", siteId, invitationId, currentUser);
        return toDto(invitation);
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

    /**
     * Synchronously dispatches the invitation email and records the outcome
     * on the invitation row. Used by both {@link #invite(String, InviteRequest)}
     * and {@link #resend(String, UUID)} — every send attempt updates
     * {@code lastSendAttemptAt}, increments {@code sendAttemptCount}, and
     * populates exactly one of {@code lastSentAt + lastSendStatus=SENT} or
     * {@code lastSendError + lastSendStatus=FAILED}.
     *
     * <p>Note: this blocks the caller for SMTP latency (~1-3s typical). The
     * trade-off is explicit — without a synchronous outcome we could not
     * persist failure diagnostics for the admin UI's resend button.
     */
    private void sendInvitationEmail(SiteInvitation invitation, Site site) {
        EmailNotificationService emailService = emailNotificationServiceProvider.getIfAvailable();
        LocalDateTime now = LocalDateTime.now();
        invitation.setLastSendAttemptAt(now);
        invitation.setSendAttemptCount(invitation.getSendAttemptCount() + 1);

        if (emailService == null) {
            invitation.setLastSendStatus(LastSendStatus.FAILED);
            invitation.setLastSendError("EmailNotificationService not available");
            invitationRepository.save(invitation);
            return;
        }

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("siteTitle", site.getTitle());
        variables.put("siteId", site.getSiteId());
        variables.put("invitedBy", invitation.getInvitedBy());
        variables.put("token", invitation.getToken());
        variables.put("role", invitation.getInvitedRole());
        variables.put("message", invitation.getMessage() != null ? invitation.getMessage() : "");
        variables.put("expiresAt", invitation.getExpiresAt());
        variables.put("invitationUrl", buildInvitationUrl(invitation.getToken()));

        EmailNotificationService.SendResult result;
        try {
            result = emailService.sendSync(
                "site.invitation",
                invitation.getInviteeEmail(),
                null,
                variables
            );
        } catch (Exception ex) {
            // sendSync should not throw, but defend against future regressions.
            log.warn("Failed to send invitation email to {}: {}", invitation.getInviteeEmail(), ex.getMessage());
            result = new EmailNotificationService.SendResult(
                false,
                "unexpected send failure: " + ex.getClass().getSimpleName() + " " + ex.getMessage()
            );
        }

        if (result.ok()) {
            invitation.setLastSendStatus(LastSendStatus.SENT);
            invitation.setLastSentAt(now);
            invitation.setLastSendError(null);
        } else {
            invitation.setLastSendStatus(LastSendStatus.FAILED);
            invitation.setLastSendError(truncate(result.error(), 1000));
        }
        invitationRepository.save(invitation);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String buildInvitationUrl(String token) {
        String baseUrl = (frontendBaseUrl == null || frontendBaseUrl.isBlank())
            ? "http://localhost:3000"
            : frontendBaseUrl.trim();
        String normalizedBaseUrl = baseUrl.replaceAll("/+$", "");
        return normalizedBaseUrl + "/invitations/accept?token="
            + URLEncoder.encode(token, StandardCharsets.UTF_8);
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
            inv.getCreatedDate(),
            inv.getLastSendAttemptAt(),
            inv.getLastSendStatus() != null ? inv.getLastSendStatus().name() : null,
            inv.getLastSendError(),
            inv.getSendAttemptCount(),
            inv.getLastSentAt()
        );
    }

    // ------------------------------------------------------------------ DTOs/records

    public record InviteRequest(String inviteeEmail, String invitedRole, String message) {}

    public record BulkInviteRequest(
        // Shared across all rows; the request body's role + message apply to every
        // entry in inviteeEmails. Per-row override is intentionally OOS for v1 —
        // see docs/SITE_INVITATION_BULK_CREATE_DESIGN_20260524.md.
        List<String> inviteeEmails,
        String invitedRole,
        String message
    ) {}

    public record BulkInviteResponse(
        List<BulkInviteResult> results
    ) {}

    public record BulkInviteResult(
        String inviteeEmail,
        Status status,
        SiteInvitationDto invitation,
        BulkInviteErrorCategory errorCategory,
        String errorMessage
    ) {
        public enum Status { SUCCESS, FAILED }

        public static BulkInviteResult success(String email, SiteInvitationDto invitation) {
            return new BulkInviteResult(email, Status.SUCCESS, invitation, null, null);
        }

        public static BulkInviteResult failed(
            String email, BulkInviteErrorCategory category, String message
        ) {
            return new BulkInviteResult(email, Status.FAILED, null, category, message);
        }
    }

    public enum BulkInviteErrorCategory {
        INVALID_EMAIL,
        DUPLICATE_PENDING,
        INTERNAL_ERROR
    }

    /**
     * Marker subclass of {@link IllegalArgumentException} so that
     * {@link #inviteBulk(String, BulkInviteRequest)}'s catch can dispatch without
     * inspecting the message string. Still maps to HTTP 400 via the existing
     * {@code @ExceptionHandler(IllegalArgumentException.class)} at
     * {@code RestExceptionHandler:34} for the single-invite path.
     */
    public static class BlankInviteeEmailException extends IllegalArgumentException {
        public BlankInviteeEmailException() {
            super("inviteeEmail must not be blank");
        }
    }

    /**
     * Marker subclass for the duplicate-pending guard. Message text is preserved
     * for the single-invite HTTP 400 body to keep backward compatibility; the
     * bulk path replaces it with a fixed stable string in
     * {@link BulkInviteResult#errorMessage()}.
     */
    public static class DuplicatePendingInvitationException extends IllegalArgumentException {
        public DuplicatePendingInvitationException(String normalizedEmail, String siteSlug) {
            super("A pending invitation already exists for " + normalizedEmail + " in site " + siteSlug);
        }
    }

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
        LocalDateTime createdDate,
        // Send-status tracking (migration 092). nullability is load-bearing —
        // null means "no send attempt has occurred yet". Frontend renders a
        // dash placeholder for null values.
        LocalDateTime lastSendAttemptAt,
        String lastSendStatus,           // "SENT" | "FAILED" | null
        String lastSendError,            // populated only when lastSendStatus == "FAILED"
        int sendAttemptCount,
        LocalDateTime lastSentAt
    ) {}
}
