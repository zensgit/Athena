package com.ecm.core.service;

import com.ecm.core.entity.Site;
import com.ecm.core.entity.SiteMember;
import com.ecm.core.entity.SiteMember.SiteMemberRole;
import com.ecm.core.entity.SiteMembershipRequest;
import com.ecm.core.entity.SiteMembershipRequest.RequestStatus;
import com.ecm.core.entity.User;
import com.ecm.core.exception.AccessDeniedException;
import com.ecm.core.exception.ResourceNotFoundException;
import com.ecm.core.repository.SiteMemberRepository;
import com.ecm.core.repository.SiteMembershipRequestRepository;
import com.ecm.core.repository.SiteRepository;
import com.ecm.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class SiteMembershipService {

    private static final String SITE_REQUESTS_KEY = "siteMembershipRequests";

    private final UserRepository userRepository;
    private final SiteRepository siteRepository;
    private final SiteMemberRepository siteMemberRepository;
    private final SiteMembershipRequestRepository siteMembershipRequestRepository;
    private final SecurityService securityService;
    private final ActivityEventListener activityEventListener;
    private final TenantWorkspaceScopeService tenantWorkspaceScopeService;

    @Value("${ecm.site.membership.persistence.enabled:true}")
    private boolean persistenceEnabled = true;

    @Value("${ecm.site.membership.legacy-reader.enabled:true}")
    private boolean legacyReaderEnabled = true;

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public List<MembershipRequestDto> getRequestsForSite(String siteId) {
        Site site = loadSite(siteId);
        ensureCanModerateSiteRequests(site, securityService.getCurrentUser());

        List<MembershipRequestDto> persistent = persistenceEnabled
            ? siteMembershipRequestRepository.findBySiteIdOrderByRequestedAtDesc(site.getId()).stream()
                .map(this::toDto)
                .toList()
            : List.of();

        return mergeAndSort(persistent, loadLegacyRequestsForSite(site.getSiteId()));
    }

    @Transactional(readOnly = true)
    public List<MembershipRequestDto> getRequestsForUser(String username) {
        loadUser(username);
        List<MembershipRequestDto> persistent = persistenceEnabled
            ? siteMembershipRequestRepository.findByUsernameOrderByRequestedAtDesc(username).stream()
                .filter(request -> isSiteVisible(request.getSite().getSiteId()))
                .map(this::toDto)
                .toList()
            : List.of();

        return mergeAndSort(persistent, loadLegacyRequestsForUser(username));
    }

    @Transactional(readOnly = true)
    public Page<MembershipRequestDto> getVisibleRequests(
        String siteId,
        String status,
        String requester,
        Pageable pageable
    ) {
        String currentUser = securityService.getCurrentUser();
        String normalizedSiteId = normalizeSiteId(siteId);

        if (normalizedSiteId != null) {
            Site site = loadSite(normalizedSiteId);
            ensureCanModerateSiteRequests(site, currentUser);
        } else if (!canModerateAnySiteRequests(currentUser)) {
            throw new AccessDeniedException("You are not allowed to manage site membership requests");
        }

        List<MembershipRequestDto> persistent = persistenceEnabled
            ? siteMembershipRequestRepository.findAll().stream()
                .map(this::toDto)
                .toList()
            : List.of();

        List<MembershipRequestDto> merged = mergeAndSort(persistent, loadLegacyVisibleRequests()).stream()
            .filter(request -> normalizedSiteId == null || normalizedSiteId.equals(normalizeSiteId(request.siteId())))
            .filter(request -> status == null || status.isBlank() || status.equalsIgnoreCase(request.status()))
            .filter(request -> requester == null || requester.isBlank() || requester.equalsIgnoreCase(request.username()))
            .filter(request -> securityService.hasRole("ROLE_ADMIN", currentUser) || canModerateSiteRequests(request.siteId(), currentUser))
            .toList();

        if (pageable == null || pageable.isUnpaged()) {
            return new PageImpl<>(merged);
        }

        int start = Math.toIntExact(pageable.getOffset());
        int end = Math.min(start + pageable.getPageSize(), merged.size());
        List<MembershipRequestDto> pageContent = start >= merged.size() ? List.of() : merged.subList(start, end);
        return new PageImpl<>(pageContent, pageable, merged.size());
    }

    // ------------------------------------------------------------------ write

    @Transactional
    public MembershipRequestDto createRequest(String siteId, CreateMembershipRequest request) {
        return createRequestForUser(securityService.getCurrentUser(), siteId, request);
    }

    @Transactional
    public MembershipRequestDto createRequestForUser(String username, String siteId, CreateMembershipRequest request) {
        requireWritableRequester(username);
        loadUser(username);
        Site site = loadSite(siteId);

        if (siteMemberRepository.findBySiteIdAndUsername(site.getId(), username).isPresent()) {
            throw new IllegalArgumentException("User '" + username + "' is already a member of site " + site.getSiteId());
        }

        if (siteMembershipRequestRepository.findBySiteIdAndUsername(site.getId(), username).isPresent()
            || findLegacyRequest(username, site.getSiteId()).isPresent()) {
            throw new IllegalArgumentException("Site membership request already exists for site " + site.getSiteId());
        }

        SiteMembershipRequest entity = new SiteMembershipRequest();

        entity.setSite(site);
        entity.setUsername(username);
        entity.setSiteTitle(normalizeOptionalString(request.siteTitle()) != null ? normalizeOptionalString(request.siteTitle()) : site.getTitle());
        entity.setRequestedRole(normalizeRoleName(request.role()));
        entity.setMessage(normalizeOptionalString(request.message()));
        entity.setStatus(RequestStatus.PENDING);
        entity.setRequestedAt(LocalDateTime.now());
        entity.setDecisionBy(null);
        entity.setDecisionAt(null);
        entity.setDecisionComment(null);

        SiteMembershipRequest saved = savePersistentRequest(entity);
        log.info("Membership request created: user={} site={}", username, site.getSiteId());
        activityEventListener.postMembershipActivity(
            "site.membership.requested",
            username,
            site.getSiteId(),
            Map.of("role", saved.getRequestedRole())
        );
        return toDto(saved);
    }

    @Transactional
    public MembershipRequestDto updateRequestForUser(String username, String siteId, CreateMembershipRequest request) {
        requireWritableRequester(username);
        loadUser(username);
        Site site = loadSite(siteId);
        SiteMembershipRequest entity = loadPersistentRequestForMutation(site, username);

        if (entity.getStatus() != RequestStatus.PENDING) {
            throw new IllegalArgumentException("Only pending membership requests can be updated");
        }

        entity.setSiteTitle(normalizeOptionalString(request.siteTitle()) != null ? normalizeOptionalString(request.siteTitle()) : site.getTitle());
        entity.setRequestedRole(normalizeRoleName(request.role()));
        entity.setMessage(normalizeOptionalString(request.message()));

        return toDto(savePersistentRequest(entity));
    }

    @Transactional
    public MembershipRequestDto approve(String siteId, String username, String comment) {
        return moderate(siteId, username, RequestStatus.APPROVED, comment);
    }

    @Transactional
    public MembershipRequestDto reject(String siteId, String username, String comment) {
        return moderate(siteId, username, RequestStatus.REJECTED, comment);
    }

    @Transactional
    public void withdraw(String siteId) {
        withdrawForUser(securityService.getCurrentUser(), siteId);
    }

    @Transactional
    public void withdrawForUser(String username, String siteId) {
        requireWritableRequester(username);
        loadUser(username);
        Site site = loadSite(siteId);
        SiteMembershipRequest entity = loadPersistentRequestForMutation(site, username);

        if (entity.getStatus() == RequestStatus.WITHDRAWN) {
            return;
        }

        entity.setStatus(RequestStatus.WITHDRAWN);
        entity.setDecisionBy(username);
        entity.setDecisionAt(LocalDateTime.now());
        entity.setDecisionComment(null);
        savePersistentRequest(entity);

        log.info("Membership request withdrawn: user={} site={}", username, site.getSiteId());
        activityEventListener.postMembershipActivity(
            "site.membership.withdrawn",
            username,
            site.getSiteId(),
            Map.of("status", RequestStatus.WITHDRAWN.name())
        );
    }

    // ------------------------------------------------------------------ members

    @Transactional(readOnly = true)
    public List<SiteMemberDto> getMembers(String siteId) {
        Site site = loadSite(siteId);
        return siteMemberRepository.findBySiteIdOrderByRoleAscUsernameAsc(site.getId())
            .stream()
            .map(SiteMembershipService::toMemberDto)
            .toList();
    }

    @Transactional
    public SiteMemberDto addMember(String siteId, String username, SiteMemberRole role) {
        Site site = loadSite(siteId);
        String currentUser = securityService.getCurrentUser();
        ensureCanManageSiteMembers(site, currentUser);

        if (siteMemberRepository.findBySiteIdAndUsername(site.getId(), username).isPresent()) {
            throw new IllegalArgumentException("User '" + username + "' is already a member of site " + siteId);
        }

        loadUser(username);
        SiteMember member = new SiteMember();
        member.setSite(site);
        member.setUsername(username);
        member.setRole(role != null ? role : SiteMemberRole.CONSUMER);
        SiteMember saved = siteMemberRepository.save(member);

        activityEventListener.postSiteMemberActivity(
            "site.member.added",
            currentUser,
            siteId,
            username,
            saved.getRole().name()
        );
        return toMemberDto(saved);
    }

    @Transactional
    public SiteMemberDto updateMemberRole(String siteId, String username, SiteMemberRole role) {
        Site site = loadSite(siteId);
        String currentUser = securityService.getCurrentUser();
        ensureCanManageSiteMembers(site, currentUser);

        SiteMember member = siteMemberRepository.findBySiteIdAndUsername(site.getId(), username)
            .orElseThrow(() -> new NoSuchElementException("Member not found: " + username + " in site " + siteId));
        member.setRole(role != null ? role : SiteMemberRole.CONSUMER);
        SiteMember saved = siteMemberRepository.save(member);

        activityEventListener.postSiteMemberActivity(
            "site.member.role_changed",
            currentUser,
            siteId,
            username,
            saved.getRole().name()
        );
        return toMemberDto(saved);
    }

    @Transactional
    public void removeMember(String siteId, String username) {
        Site site = loadSite(siteId);
        String currentUser = securityService.getCurrentUser();
        ensureCanManageSiteMembers(site, currentUser);

        siteMemberRepository.deleteBySiteIdAndUsername(site.getId(), username);
        activityEventListener.postSiteMemberActivity(
            "site.member.removed",
            currentUser,
            siteId,
            username,
            "REMOVED"
        );
    }

    @Transactional(readOnly = true)
    public List<SiteMemberDto> getUserSites(String username) {
        return siteMemberRepository.findByUsername(username)
            .stream()
            .filter(member -> isSiteVisible(member.getSite().getSiteId()))
            .map(SiteMembershipService::toMemberDto)
            .toList();
    }

    // ------------------------------------------------------------------ internals

    private MembershipRequestDto moderate(String siteId, String username, RequestStatus status, String comment) {
        Site site = loadSite(siteId);
        String currentUser = securityService.getCurrentUser();
        ensureCanModerateSiteRequests(site, currentUser);

        SiteMembershipRequest entity = loadPersistentRequestForMutation(site, username);
        entity.setStatus(status);
        entity.setDecisionBy(currentUser);
        entity.setDecisionAt(LocalDateTime.now());
        entity.setDecisionComment(normalizeOptionalString(comment));

        if (status == RequestStatus.APPROVED) {
            upsertMemberFromRequest(site, entity, currentUser);
        }

        SiteMembershipRequest saved = savePersistentRequest(entity);
        log.info("Membership request {}: user={} site={} by={}", status.name().toLowerCase(Locale.ROOT), username, site.getSiteId(), currentUser);
        activityEventListener.postMembershipActivity(
            "site.membership." + status.name().toLowerCase(Locale.ROOT),
            username,
            site.getSiteId(),
            Map.of("decidedBy", currentUser)
        );
        return toDto(saved);
    }

    private void upsertMemberFromRequest(Site site, SiteMembershipRequest request, String currentUser) {
        SiteMemberRole requestedRole = parseRole(request.getRequestedRole());
        SiteMember member = siteMemberRepository.findBySiteIdAndUsername(site.getId(), request.getUsername())
            .orElseGet(() -> {
                SiteMember created = new SiteMember();
                created.setSite(site);
                created.setUsername(request.getUsername());
                return created;
            });
        member.setRole(requestedRole);
        SiteMember saved = siteMemberRepository.save(member);
        activityEventListener.postSiteMemberActivity(
            "site.member.added",
            currentUser,
            site.getSiteId(),
            saved.getUsername(),
            saved.getRole().name()
        );
    }

    private SiteMembershipRequest loadPersistentRequestForMutation(Site site, String username) {
        return siteMembershipRequestRepository.findBySiteIdAndUsername(site.getId(), username)
            .or(() -> materializeLegacyRequest(site, username))
            .orElseThrow(() -> new NoSuchElementException("Request not found: user=" + username + " site=" + site.getSiteId()));
    }

    private SiteMembershipRequest savePersistentRequest(SiteMembershipRequest request) {
        if (!persistenceEnabled) {
            throw new IllegalStateException("Site membership persistence is disabled");
        }
        return siteMembershipRequestRepository.save(request);
    }

    private Optional<SiteMembershipRequest> materializeLegacyRequest(Site site, String username) {
        if (!legacyReaderEnabled) {
            return Optional.empty();
        }

        return findLegacyRequest(username, site.getSiteId()).map(legacy -> {
            SiteMembershipRequest entity = new SiteMembershipRequest();
            entity.setSite(site);
            entity.setUsername(username);
            entity.setSiteTitle(normalizeOptionalString(legacy.siteTitle()) != null ? normalizeOptionalString(legacy.siteTitle()) : site.getTitle());
            entity.setRequestedRole(normalizeRoleName(legacy.role()));
            entity.setMessage(normalizeOptionalString(legacy.message()));
            entity.setStatus(parseStatus(legacy.status()));
            entity.setRequestedAt(parseDateTime(legacy.requestedAt()) != null ? parseDateTime(legacy.requestedAt()) : LocalDateTime.now());
            entity.setDecisionBy(normalizeOptionalString(legacy.decisionBy()));
            entity.setDecisionAt(parseDateTime(legacy.decisionAt()));
            entity.setDecisionComment(normalizeOptionalString(legacy.decisionComment()));
            return savePersistentRequest(entity);
        });
    }

    private boolean canModerateAnySiteRequests(String username) {
        return securityService.hasRole("ROLE_ADMIN", username) || siteMemberRepository.findByUsername(username).stream()
            .anyMatch(member -> member.getRole() == SiteMemberRole.MANAGER && isSiteVisible(member.getSite().getSiteId()));
    }

    private boolean canModerateSiteRequests(String siteId, String username) {
        return securityService.hasRole("ROLE_ADMIN", username) || siteMemberRepository.findByUsername(username).stream()
            .anyMatch(member ->
                member.getRole() == SiteMemberRole.MANAGER
                    && member.getSite() != null
                    && siteId.equalsIgnoreCase(member.getSite().getSiteId())
                    && isSiteVisible(member.getSite().getSiteId())
            );
    }

    private boolean canManageSiteMembers(String siteId, String username) {
        return canModerateSiteRequests(siteId, username);
    }

    private void ensureCanModerateSiteRequests(Site site, String username) {
        if (!canModerateSiteRequests(site.getSiteId(), username)) {
            throw new AccessDeniedException("You are not allowed to manage site membership requests");
        }
    }

    private void ensureCanManageSiteMembers(Site site, String username) {
        if (!canManageSiteMembers(site.getSiteId(), username)) {
            throw new AccessDeniedException("You are not allowed to manage site members");
        }
    }

    private void requireWritableRequester(String username) {
        String currentUser = securityService.getCurrentUser();
        if (!Objects.equals(currentUser, username) && !securityService.hasRole("ROLE_ADMIN", currentUser)) {
            throw new AccessDeniedException("You are not allowed to update this profile");
        }
    }

    private Site loadSite(String siteId) {
        String normalizedSiteId = normalizeSiteId(siteId);
        Site site = siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse(normalizedSiteId)
            .orElseThrow(() -> new ResourceNotFoundException("Site not found: " + normalizedSiteId));
        if (!isSiteVisible(site.getSiteId())) {
            throw new ResourceNotFoundException("Site not found: " + normalizedSiteId);
        }
        return site;
    }

    private User loadUser(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementException("User not found: " + username));
    }

    private boolean isSiteVisible(String siteId) {
        String tenantRootPath = tenantWorkspaceScopeService.resolveCurrentTenantRootPath();
        return tenantRootPath == null || tenantWorkspaceScopeService.isSiteVisible(siteId, tenantRootPath);
    }

    private List<MembershipRequestDto> mergeAndSort(List<MembershipRequestDto> persistent, List<MembershipRequestDto> legacy) {
        Map<String, MembershipRequestDto> merged = new LinkedHashMap<>();
        Stream.concat(persistent.stream(), legacy.stream()).forEach(request ->
            merged.putIfAbsent(requestKey(request.username(), request.siteId()), request)
        );
        return merged.values().stream()
            .sorted(
                Comparator.comparing(this::dtoRequestedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(MembershipRequestDto::username, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                    .thenComparing(MembershipRequestDto::siteId, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            )
            .toList();
    }

    private LocalDateTime dtoRequestedAt(MembershipRequestDto request) {
        return parseDateTime(request.requestedAt());
    }

    private String requestKey(String username, String siteId) {
        return (username != null ? username.toLowerCase(Locale.ROOT) : "") + "::" + normalizeSiteId(siteId);
    }

    private List<MembershipRequestDto> loadLegacyRequestsForSite(String siteId) {
        if (!legacyReaderEnabled) {
            return List.of();
        }
        List<MembershipRequestDto> requests = new ArrayList<>();
        for (User user : userRepository.findAll()) {
            for (Map<String, Object> request : extractLegacyRequests(user)) {
                if (siteId.equalsIgnoreCase(str(request, "siteId"))) {
                    requests.add(toDto(user.getUsername(), request));
                }
            }
        }
        return requests;
    }

    private List<MembershipRequestDto> loadLegacyRequestsForUser(String username) {
        if (!legacyReaderEnabled) {
            return List.of();
        }
        User user = loadUser(username);
        return extractLegacyRequests(user).stream()
            .filter(request -> isSiteVisible(str(request, "siteId")))
            .map(request -> toDto(username, request))
            .toList();
    }

    private List<MembershipRequestDto> loadLegacyVisibleRequests() {
        if (!legacyReaderEnabled) {
            return List.of();
        }
        List<MembershipRequestDto> requests = new ArrayList<>();
        for (User user : userRepository.findAll()) {
            for (Map<String, Object> request : extractLegacyRequests(user)) {
                if (isSiteVisible(str(request, "siteId"))) {
                    requests.add(toDto(user.getUsername(), request));
                }
            }
        }
        return requests;
    }

    private Optional<MembershipRequestDto> findLegacyRequest(String username, String siteId) {
        User user = loadUser(username);
        return extractLegacyRequests(user).stream()
            .filter(request -> siteId.equalsIgnoreCase(str(request, "siteId")))
            .map(request -> toDto(username, request))
            .findFirst();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractLegacyRequests(User user) {
        if (user.getPreferences() == null) {
            return List.of();
        }
        Object raw = user.getPreferences().get(SITE_REQUESTS_KEY);
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> typed = new LinkedHashMap<>();
                map.forEach((key, value) -> typed.put(String.valueOf(key), value));
                result.add(typed);
            }
        }
        return result;
    }

    private MembershipRequestDto toDto(SiteMembershipRequest request) {
        return new MembershipRequestDto(
            request.getUsername(),
            request.getSite().getSiteId(),
            request.getSiteTitle(),
            request.getRequestedRole(),
            request.getMessage(),
            request.getStatus().name(),
            request.getRequestedAt() != null ? request.getRequestedAt().toString() : null,
            request.getDecisionBy(),
            request.getDecisionAt() != null ? request.getDecisionAt().toString() : null,
            request.getDecisionComment()
        );
    }

    private MembershipRequestDto toDto(String username, Map<String, Object> request) {
        return new MembershipRequestDto(
            username,
            str(request, "siteId"),
            str(request, "siteTitle"),
            normalizeRoleName(str(request, "role")),
            str(request, "message"),
            parseStatus(str(request, "status")).name(),
            parseDateTime(request.get("requestedAt")) != null ? parseDateTime(request.get("requestedAt")).toString() : null,
            str(request, "decisionBy"),
            parseDateTime(request.get("decisionAt")) != null ? parseDateTime(request.get("decisionAt")).toString() : null,
            str(request, "decisionComment")
        );
    }

    private String str(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    private LocalDateTime parseDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        try {
            return LocalDateTime.parse(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    private String normalizeSiteId(String siteId) {
        String normalized = normalizeOptionalString(siteId);
        return normalized != null ? normalized.toLowerCase(Locale.ROOT) : null;
    }

    private String normalizeOptionalString(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeRoleName(String role) {
        String normalized = normalizeOptionalString(role);
        return normalized != null ? normalized.toUpperCase(Locale.ROOT) : SiteMemberRole.CONSUMER.name();
    }

    private RequestStatus parseStatus(String status) {
        String normalized = normalizeOptionalString(status);
        if (normalized == null) {
            return RequestStatus.PENDING;
        }
        try {
            return RequestStatus.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return RequestStatus.PENDING;
        }
    }

    private SiteMemberRole parseRole(String role) {
        try {
            return SiteMemberRole.valueOf(normalizeRoleName(role));
        } catch (IllegalArgumentException ex) {
            return SiteMemberRole.CONSUMER;
        }
    }

    private static SiteMemberDto toMemberDto(SiteMember member) {
        return new SiteMemberDto(
            member.getId(),
            member.getSite().getSiteId(),
            member.getUsername(),
            member.getRole().name(),
            member.getJoinedAt() != null ? member.getJoinedAt().toString() : null
        );
    }

    public record SiteMemberDto(UUID id, String siteId, String username, String role, String joinedAt) {}

    public record AddMemberRequest(String username, String role) {}

    public record UpdateRoleRequest(String role) {}

    public record CreateMembershipRequest(String siteTitle, String role, String message) {}

    public record ModerationRequest(String comment) {}

    public record MembershipRequestDto(
        String username,
        String siteId,
        String siteTitle,
        String role,
        String message,
        String status,
        String requestedAt,
        String decisionBy,
        String decisionAt,
        String decisionComment
    ) {}
}
