package com.ecm.core.service;

import com.ecm.core.entity.Site;
import com.ecm.core.entity.SiteMember;
import com.ecm.core.entity.SiteMember.SiteMemberRole;
import com.ecm.core.entity.User;
import com.ecm.core.exception.ResourceNotFoundException;
import com.ecm.core.repository.SiteMemberRepository;
import com.ecm.core.repository.SiteRepository;
import com.ecm.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Site membership request service — wraps the JSONB-in-User-preferences model
 * and exposes it through a clean API for SiteController consumption.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SiteMembershipService {

    private static final String SITE_REQUESTS_KEY = "siteMembershipRequests";

    private final UserRepository userRepository;
    private final SiteRepository siteRepository;
    private final SiteMemberRepository siteMemberRepository;
    private final SecurityService securityService;
    private final ActivityEventListener activityEventListener;
    private final TenantWorkspaceScopeService tenantWorkspaceScopeService;

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public List<MembershipRequestDto> getRequestsForSite(String siteId) {
        Site site = loadSite(siteId);
        List<MembershipRequestDto> result = new ArrayList<>();
        for (User user : userRepository.findAll()) {
            for (Map<String, Object> req : extractRequests(user)) {
                if (site.getSiteId().equalsIgnoreCase(str(req, "siteId"))) {
                    result.add(toDto(user.getUsername(), req));
                }
            }
        }
        result.sort(Comparator.comparing(MembershipRequestDto::requestedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    @Transactional(readOnly = true)
    public List<MembershipRequestDto> getRequestsForUser(String username) {
        User user = loadUser(username);
        String tenantRootPath = tenantWorkspaceScopeService.resolveCurrentTenantRootPath();
        return extractRequests(user).stream()
            .filter(request -> tenantRootPath == null
                || tenantWorkspaceScopeService.isSiteVisible(str(request, "siteId"), tenantRootPath))
            .map(r -> toDto(username, r))
            .toList();
    }

    // ------------------------------------------------------------------ write

    @Transactional
    public MembershipRequestDto createRequest(String siteId, CreateMembershipRequest request) {
        String username = securityService.getCurrentUser();
        Site site = loadSite(siteId);
        User user = loadUser(username);
        List<Map<String, Object>> requests = mutableRequests(user);

        if (findIndex(requests, site.getSiteId()) >= 0) {
            throw new IllegalArgumentException("Request already exists for site: " + site.getSiteId());
        }

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("siteId", site.getSiteId());
        entry.put("siteTitle", request.siteTitle() != null ? request.siteTitle() : site.getTitle());
        entry.put("role", request.role() != null ? request.role() : "CONSUMER");
        entry.put("message", request.message());
        entry.put("status", "PENDING");
        entry.put("requestedAt", LocalDateTime.now().toString());
        requests.add(entry);
        saveRequests(user, requests);

        log.info("Membership request created: user={} site={}", username, site.getSiteId());
        activityEventListener.postMembershipActivity("site.membership.requested", username, site.getSiteId(), Map.of("role", request.role() != null ? request.role() : "CONSUMER"));
        return toDto(username, entry);
    }

    @Transactional
    public MembershipRequestDto approve(String siteId, String username, String comment) {
        return moderate(siteId, username, "APPROVED", comment);
    }

    @Transactional
    public MembershipRequestDto reject(String siteId, String username, String comment) {
        return moderate(siteId, username, "REJECTED", comment);
    }

    @Transactional
    public void withdraw(String siteId) {
        String username = securityService.getCurrentUser();
        Site site = loadSite(siteId);
        User user = loadUser(username);
        List<Map<String, Object>> requests = mutableRequests(user);
        int idx = findIndex(requests, site.getSiteId());
        if (idx < 0) {
            throw new NoSuchElementException("Request not found for site: " + site.getSiteId());
        }
        requests.remove(idx);
        saveRequests(user, requests);
        log.info("Membership request withdrawn: user={} site={}", username, site.getSiteId());
        activityEventListener.postMembershipActivity(
            "site.membership.withdrawn",
            username,
            site.getSiteId(),
            Map.of("status", "WITHDRAWN")
        );
    }

    // ------------------------------------------------------------------ members (real roster)

    @Transactional(readOnly = true)
    public List<SiteMemberDto> getMembers(String siteId) {
        Site site = loadSite(siteId);
        return siteMemberRepository.findBySiteIdOrderByRoleAscUsernameAsc(site.getId())
            .stream().map(SiteMembershipService::toMemberDto).toList();
    }

    @Transactional
    public SiteMemberDto addMember(String siteId, String username, SiteMemberRole role) {
        if (!securityService.hasRole("ROLE_ADMIN")) {
            throw new SecurityException("Only admin can add site members");
        }
        String currentUser = securityService.getCurrentUser();
        Site site = loadSite(siteId);
        if (siteMemberRepository.findBySiteIdAndUsername(site.getId(), username).isPresent()) {
            throw new IllegalArgumentException("User '" + username + "' is already a member of site " + siteId);
        }
        loadUser(username); // validate user exists
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
        if (!securityService.hasRole("ROLE_ADMIN")) {
            throw new SecurityException("Only admin can update member roles");
        }
        String currentUser = securityService.getCurrentUser();
        Site site = loadSite(siteId);
        SiteMember member = siteMemberRepository.findBySiteIdAndUsername(site.getId(), username)
            .orElseThrow(() -> new NoSuchElementException("Member not found: " + username + " in site " + siteId));
        member.setRole(role);
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
        if (!securityService.hasRole("ROLE_ADMIN")) {
            throw new SecurityException("Only admin can remove site members");
        }
        String currentUser = securityService.getCurrentUser();
        Site site = loadSite(siteId);
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
        String tenantRootPath = tenantWorkspaceScopeService.resolveCurrentTenantRootPath();
        return siteMemberRepository.findByUsername(username)
            .stream()
            .filter(member -> tenantRootPath == null
                || tenantWorkspaceScopeService.isSiteVisible(member.getSite().getSiteId(), tenantRootPath))
            .map(SiteMembershipService::toMemberDto)
            .toList();
    }

    private Site loadSite(String siteId) {
        String normalizedSiteId = normalizeSiteId(siteId);
        Site site = siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse(normalizedSiteId)
            .orElseThrow(() -> new ResourceNotFoundException("Site not found: " + normalizedSiteId));
        String tenantRootPath = tenantWorkspaceScopeService.resolveCurrentTenantRootPath();
        if (tenantRootPath != null && !tenantWorkspaceScopeService.isSiteVisible(site.getSiteId(), tenantRootPath)) {
            throw new ResourceNotFoundException("Site not found: " + normalizedSiteId);
        }
        return site;
    }

    private static SiteMemberDto toMemberDto(SiteMember m) {
        return new SiteMemberDto(
            m.getId(), m.getSite().getSiteId(), m.getUsername(),
            m.getRole().name(), m.getJoinedAt() != null ? m.getJoinedAt().toString() : null
        );
    }

    public record SiteMemberDto(java.util.UUID id, String siteId, String username, String role, String joinedAt) {}

    public record AddMemberRequest(String username, String role) {}

    public record UpdateRoleRequest(String role) {}

    // ------------------------------------------------------------------ DTOs (requests)

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

    // ------------------------------------------------------------------ internal

    private MembershipRequestDto moderate(String siteId, String username, String status, String comment) {
        if (!securityService.hasRole("ROLE_ADMIN")) {
            throw new SecurityException("Only admin can moderate membership requests");
        }
        Site site = loadSite(siteId);
        User user = loadUser(username);
        List<Map<String, Object>> requests = mutableRequests(user);
        int idx = findIndex(requests, site.getSiteId());
        if (idx < 0) {
            throw new NoSuchElementException("Request not found: user=" + username + " site=" + site.getSiteId());
        }
        Map<String, Object> entry = requests.get(idx);
        entry.put("status", status);
        entry.put("decisionBy", securityService.getCurrentUser());
        entry.put("decisionAt", LocalDateTime.now().toString());
        entry.put("decisionComment", comment);
        requests.set(idx, entry);
        saveRequests(user, requests);
        log.info("Membership request {}: user={} site={} by={}", status.toLowerCase(), username, site.getSiteId(), securityService.getCurrentUser());
        activityEventListener.postMembershipActivity("site.membership." + status.toLowerCase(), username, site.getSiteId(), Map.of("decidedBy", securityService.getCurrentUser()));
        return toDto(username, entry);
    }

    private String normalizeSiteId(String siteId) {
        return siteId != null ? siteId.trim().toLowerCase(Locale.ROOT) : "";
    }

    private User loadUser(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementException("User not found: " + username));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractRequests(User user) {
        if (user.getPreferences() == null) return List.of();
        Object raw = user.getPreferences().get(SITE_REQUESTS_KEY);
        if (!(raw instanceof List<?> list)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> typed = new LinkedHashMap<>();
                map.forEach((k, v) -> typed.put(String.valueOf(k), v));
                result.add(typed);
            }
        }
        return result;
    }

    private List<Map<String, Object>> mutableRequests(User user) {
        return new ArrayList<>(extractRequests(user));
    }

    private void saveRequests(User user, List<Map<String, Object>> requests) {
        Map<String, Object> prefs = user.getPreferences() != null
            ? new LinkedHashMap<>(user.getPreferences())
            : new LinkedHashMap<>();
        prefs.put(SITE_REQUESTS_KEY, requests);
        user.setPreferences(prefs);
        userRepository.save(user);
    }

    private int findIndex(List<Map<String, Object>> requests, String siteId) {
        for (int i = 0; i < requests.size(); i++) {
            if (siteId.equalsIgnoreCase(str(requests.get(i), "siteId"))) return i;
        }
        return -1;
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private MembershipRequestDto toDto(String username, Map<String, Object> m) {
        return new MembershipRequestDto(
            username, str(m, "siteId"), str(m, "siteTitle"), str(m, "role"),
            str(m, "message"), str(m, "status"), str(m, "requestedAt"),
            str(m, "decisionBy"), str(m, "decisionAt"), str(m, "decisionComment")
        );
    }
}
