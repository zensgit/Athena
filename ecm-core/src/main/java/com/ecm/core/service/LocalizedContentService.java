package com.ecm.core.service;

import com.ecm.core.entity.LocalizedContent;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.exception.ResourceNotFoundException;
import com.ecm.core.repository.LocalizedContentRepository;
import com.ecm.core.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class LocalizedContentService {

    private final LocalizedContentRepository localizedContentRepository;
    private final NodeRepository nodeRepository;
    private final SecurityService securityService;

    // ------------------------------------------------------------------ list

    @Transactional(readOnly = true)
    public List<LocalizedContentDto> listForNode(UUID nodeId) {
        requireReadableNode(nodeId);
        return localizedContentRepository.findByNodeIdOrderByLocaleAsc(nodeId)
            .stream()
            .map(this::toDto)
            .toList();
    }

    // ------------------------------------------------------------------ upsert

    public LocalizedContentDto upsert(UUID nodeId, String locale, LocalizedContentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Localization request is required");
        }
        String normalizedLocale = normalizeLocale(locale);
        Node node = requireWritableNode(nodeId);

        LocalizedContent lc = localizedContentRepository
            .findByNodeIdAndLocale(nodeId, normalizedLocale)
            .orElseGet(() -> {
                LocalizedContent created = new LocalizedContent();
                created.setNode(node);
                created.setLocale(normalizedLocale);
                return created;
            });

        lc.setTitle(request.title());
        lc.setDescription(request.description());

        LocalizedContent saved = localizedContentRepository.save(lc);
        log.debug("Upserted localized content: nodeId={} locale={}", nodeId, normalizedLocale);
        return toDto(saved);
    }

    // ------------------------------------------------------------------ delete

    public void delete(UUID nodeId, String locale) {
        String normalizedLocale = normalizeLocale(locale);
        requireWritableNode(nodeId);
        if (localizedContentRepository.existsByNodeIdAndLocale(nodeId, normalizedLocale)) {
            localizedContentRepository.deleteByNodeIdAndLocale(nodeId, normalizedLocale);
            log.debug("Deleted localized content: nodeId={} locale={}", nodeId, normalizedLocale);
        }
    }

    // ------------------------------------------------------------------ resolve

    @Transactional(readOnly = true)
    public Optional<LocalizedContentDto> resolve(UUID nodeId, String acceptLanguage) {
        requireReadableNode(nodeId);
        List<LocalizedContent> all = localizedContentRepository.findByNodeIdOrderByLocaleAsc(nodeId);
        if (all.isEmpty()) {
            return Optional.empty();
        }

        List<String> tags = parseAcceptLanguage(acceptLanguage);
        for (String tag : tags) {
            // 1. Exact match
            Optional<LocalizedContent> exact = findByLocale(all, tag);
            if (exact.isPresent()) {
                return exact.map(this::toDto);
            }
            // 2. Language-only match (strip region, e.g. "zh-CN" -> "zh")
            int dashIdx = tag.indexOf('-');
            if (dashIdx > 0) {
                String langOnly = tag.substring(0, dashIdx);
                Optional<LocalizedContent> lang = findByLocale(all, langOnly);
                if (lang.isPresent()) {
                    return lang.map(this::toDto);
                }
            }
        }

        // Default fallback: first entry in the DB list
        return Optional.of(toDto(all.get(0)));
    }

    // ------------------------------------------------------------------ helpers

    private Node requireReadableNode(UUID nodeId) {
        Node node = requireLiveNode(nodeId);
        securityService.checkPermission(node, PermissionType.READ);
        return node;
    }

    private Node requireWritableNode(UUID nodeId) {
        Node node = requireLiveNode(nodeId);
        securityService.checkPermission(node, PermissionType.WRITE);
        return node;
    }

    private Node requireLiveNode(UUID nodeId) {
        return nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(nodeId, Node.ArchiveStatus.LIVE)
            .orElseThrow(() -> new ResourceNotFoundException("Node not found: " + nodeId));
    }

    private Optional<LocalizedContent> findByLocale(List<LocalizedContent> list, String locale) {
        return list.stream()
            .filter(lc -> locale.equalsIgnoreCase(lc.getLocale()))
            .findFirst();
    }

    // Parse Accept-Language header: split on comma, strip q-values, trim, normalize
    private List<String> parseAcceptLanguage(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        for (String part : acceptLanguage.split(",")) {
            String tag = part.trim();
            int semicolonIdx = tag.indexOf(';');
            if (semicolonIdx >= 0) {
                tag = tag.substring(0, semicolonIdx).trim();
            }
            if (!tag.isBlank()) {
                tags.add(normalizeLocale(tag));
            }
        }
        return tags;
    }

    private String normalizeLocale(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Locale must not be blank");
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private LocalizedContentDto toDto(LocalizedContent lc) {
        return new LocalizedContentDto(
            lc.getId(),
            lc.getNode() != null ? lc.getNode().getId() : null,
            lc.getLocale(),
            lc.getTitle(),
            lc.getDescription(),
            lc.getCreatedDate(),
            lc.getCreatedBy(),
            lc.getLastModifiedDate()
        );
    }

    // ------------------------------------------------------------------ DTOs / records

    public record LocalizedContentDto(
        UUID id,
        UUID nodeId,
        String locale,
        String title,
        String description,
        LocalDateTime createdDate,
        String createdBy,
        LocalDateTime lastModifiedDate
    ) {}

    public record LocalizedContentRequest(String locale, String title, String description) {}
}
