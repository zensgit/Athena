package com.ecm.core.service;

import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Site;
import com.ecm.core.entity.Site.SiteStatus;
import com.ecm.core.entity.Site.SiteVisibility;
import com.ecm.core.exception.ResourceNotFoundException;
import com.ecm.core.repository.FolderRepository;
import com.ecm.core.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SiteService {

    private final SiteRepository siteRepository;
    private final FolderRepository folderRepository;
    private final SecurityService securityService;
    private final ActivityEventListener activityEventListener;

    public record CreateSiteRequest(
        String siteId,
        String title,
        String description,
        SiteVisibility visibility,
        UUID rootFolderId
    ) {}

    public record UpdateSiteRequest(
        String title,
        String description,
        SiteVisibility visibility,
        SiteStatus status,
        UUID rootFolderId
    ) {}

    public record SiteDto(
        UUID id,
        String siteId,
        String title,
        String description,
        SiteVisibility visibility,
        SiteStatus status,
        UUID rootFolderId,
        String rootFolderTitle,
        String rootFolderPath,
        String createdBy,
        LocalDateTime createdDate,
        LocalDateTime lastModifiedDate,
        boolean deleted,
        LocalDateTime deletedAt,
        String deletedBy
    ) {}

    @Transactional
    public SiteDto createSite(CreateSiteRequest request) {
        String normalizedSiteId = normalizeSiteId(request.siteId());
        String title = normalizeRequiredText(request.title(), "title");
        SiteVisibility visibility = request.visibility() != null ? request.visibility() : SiteVisibility.PUBLIC;

        siteRepository.findBySiteIdIgnoreCase(normalizedSiteId).ifPresent(existing -> {
            throw new IllegalArgumentException("Site already exists: " + normalizedSiteId);
        });

        Site site = new Site();
        site.setSiteId(normalizedSiteId);
        site.setTitle(title);
        site.setDescription(normalizeOptionalText(request.description()));
        site.setVisibility(visibility);
        site.setStatus(SiteStatus.ACTIVE);
        site.setRootFolder(resolveRootFolder(request.rootFolderId()));
        Site saved = siteRepository.save(site);
        activityEventListener.postSiteActivity(
            "site.created",
            securityService.getCurrentUser(),
            saved.getSiteId(),
            java.util.Map.of(
                "title", saved.getTitle(),
                "visibility", saved.getVisibility().name()
            )
        );
        return toDto(saved);
    }

    public SiteDto getSite(String siteId) {
        return toDto(loadSite(siteId));
    }

    public List<SiteDto> listSites(boolean includeArchived) {
        List<Site> sites = includeArchived
            ? siteRepository.findAll(Sort.by(Sort.Order.asc("title"), Sort.Order.asc("siteId")))
            : siteRepository.findByDeletedFalseOrderByTitleAsc();
        return sites.stream()
            .filter(site -> includeArchived || !site.isDeleted())
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public SiteDto updateSite(String siteId, UpdateSiteRequest request) {
        Site site = loadSite(siteId);
        SiteStatus previousStatus = site.getStatus();
        if (request.title() != null) {
            site.setTitle(normalizeRequiredText(request.title(), "title"));
        }
        if (request.description() != null) {
            site.setDescription(normalizeOptionalText(request.description()));
        }
        if (request.visibility() != null) {
            site.setVisibility(request.visibility());
        }
        if (request.status() != null) {
            site.setStatus(request.status());
        }
        if (request.rootFolderId() != null) {
            site.setRootFolder(resolveRootFolder(request.rootFolderId()));
        }
        Site saved = siteRepository.save(site);
        String activityType = previousStatus != SiteStatus.ARCHIVED && saved.getStatus() == SiteStatus.ARCHIVED
            ? "site.archived"
            : "site.updated";
        activityEventListener.postSiteActivity(
            activityType,
            securityService.getCurrentUser(),
            saved.getSiteId(),
            java.util.Map.of(
                "title", saved.getTitle(),
                "visibility", saved.getVisibility().name(),
                "status", saved.getStatus().name()
            )
        );
        return toDto(saved);
    }

    @Transactional
    public void deleteSite(String siteId) {
        Site site = loadSite(siteId);
        String currentUser = securityService.getCurrentUser();
        site.setDeleted(true);
        site.setDeletedAt(LocalDateTime.now());
        site.setDeletedBy(currentUser);
        site.setStatus(SiteStatus.ARCHIVED);
        siteRepository.save(site);
        activityEventListener.postSiteActivity(
            "site.archived",
            currentUser,
            site.getSiteId(),
            java.util.Map.of(
                "title", site.getTitle(),
                "status", site.getStatus().name()
            )
        );
    }

    private Site loadSite(String siteId) {
        String normalizedSiteId = normalizeSiteId(siteId);
        return siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse(normalizedSiteId)
            .orElseThrow(() -> new ResourceNotFoundException("Site not found: " + normalizedSiteId));
    }

    private Folder resolveRootFolder(UUID rootFolderId) {
        if (rootFolderId == null) {
            return null;
        }
        Folder folder = folderRepository.findById(rootFolderId)
            .orElseThrow(() -> new ResourceNotFoundException("Root folder not found: " + rootFolderId));
        if (folder.isDeleted()) {
            throw new ResourceNotFoundException("Root folder not found: " + rootFolderId);
        }
        return folder;
    }

    private SiteDto toDto(Site site) {
        Folder rootFolder = site.getRootFolder();
        return new SiteDto(
            site.getId(),
            site.getSiteId(),
            site.getTitle(),
            site.getDescription(),
            site.getVisibility(),
            site.getStatus(),
            rootFolder != null ? rootFolder.getId() : null,
            rootFolder != null ? rootFolder.getName() : null,
            rootFolder != null ? rootFolder.getPath() : null,
            site.getCreatedBy(),
            site.getCreatedDate(),
            site.getLastModifiedDate(),
            site.isDeleted(),
            site.getDeletedAt(),
            site.getDeletedBy()
        );
    }

    private String normalizeSiteId(String siteId) {
        String value = normalizeRequiredText(siteId, "siteId").toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z0-9][a-z0-9._-]{0,99}")) {
            throw new IllegalArgumentException("Site ID is invalid: " + siteId);
        }
        return value;
    }

    private String normalizeRequiredText(String value, String fieldName) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalized;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
