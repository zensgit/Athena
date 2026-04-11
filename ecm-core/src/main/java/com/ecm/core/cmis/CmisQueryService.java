package com.ecm.core.cmis;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.service.FolderService;
import com.ecm.core.service.SecurityService;
import com.ecm.core.service.TenantWorkspaceScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CmisQueryService {

    private static final Pattern QUERY_PATTERN = Pattern.compile(
        "(?is)^SELECT\\s+\\*\\s+FROM\\s+(cmis:document|cmis:folder)\\s*(?:WHERE\\s+(.+?))?\\s*(?:ORDER\\s+BY\\s+(cmis:name|cmis:lastModificationDate|cmis:creationDate)\\s*(ASC|DESC)?)?\\s*$"
    );

    private static final Pattern IN_FOLDER_PATTERN = Pattern.compile("(?is)^IN_FOLDER\\('([^']+)'\\)$");
    private static final Pattern IN_TREE_PATTERN = Pattern.compile("(?is)^IN_TREE\\('([^']+)'\\)$");
    private static final Pattern CONTAINS_PATTERN = Pattern.compile("(?is)^CONTAINS\\('([^']*)'\\)$");
    private static final Pattern EQUALS_PATTERN = Pattern.compile("(?is)^cmis:name\\s*=\\s*'([^']*)'$");
    private static final Pattern LIKE_PATTERN = Pattern.compile("(?is)^cmis:name\\s+LIKE\\s+'([^']*)'$");

    private final NodeRepository nodeRepository;
    private final DocumentRepository documentRepository;
    private final FolderService folderService;
    private final SecurityService securityService;
    private final TenantWorkspaceScopeService tenantWorkspaceScopeService;
    private final CmisObjectFactory objectFactory;

    public CmisModels.QueryResponse query(String statement, int skipCount, int maxItems) {
        ParsedQuery parsed = parse(statement);
        int normalizedSkip = Math.max(skipCount, 0);
        int normalizedMax = Math.max(Math.min(maxItems, 200), 1);

        // Resolve full-text candidate IDs when CONTAINS is present
        Set<UUID> containsCandidateIds = resolveContainsCandidates(parsed);

        List<Node> matched = nodeRepository.findAll(buildSpecification(parsed, containsCandidateIds), resolveSort(parsed)).stream()
            .filter(this::isVisibleToCurrentTenant)
            .filter(node -> securityService.hasPermission(node, Permission.PermissionType.READ))
            .toList();

        List<CmisModels.ObjectEntry> entries = matched.stream()
            .skip(normalizedSkip)
            .limit(normalizedMax)
            .map(objectFactory::fromNode)
            .toList();

        return new CmisModels.QueryResponse(
            objectFactory.getRepositoryId(),
            statement,
            entries,
            normalizedSkip,
            normalizedMax,
            matched.size(),
            normalizedSkip + entries.size() < matched.size()
        );
    }

    private ParsedQuery parse(String statement) {
        if (statement == null || statement.isBlank()) {
            throw new IllegalArgumentException("CMIS statement is required");
        }
        Matcher matcher = QUERY_PATTERN.matcher(statement.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Only SELECT * FROM cmis:document|cmis:folder with simple WHERE/ORDER BY is supported");
        }

        String fromType = matcher.group(1).toLowerCase(Locale.ROOT);
        String whereClause = matcher.group(2);
        String sortField = matcher.group(3);
        String sortDirection = matcher.group(4);

        ParsedQuery parsed = new ParsedQuery(
            fromType,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            sortField != null ? sortField.toLowerCase(Locale.ROOT) : null,
            sortDirection != null ? sortDirection.toLowerCase(Locale.ROOT) : "asc"
        );

        if (whereClause == null || whereClause.isBlank()) {
            return parsed;
        }

        String folderRef = null;
        String nameEquals = null;
        String nameLike = null;
        String treeRef = null;
        String containsSearch = null;

        for (String fragment : whereClause.split("(?i)\\s+AND\\s+")) {
            String clause = fragment.trim();
            Matcher inFolder = IN_FOLDER_PATTERN.matcher(clause);
            Matcher inTree = IN_TREE_PATTERN.matcher(clause);
            Matcher contains = CONTAINS_PATTERN.matcher(clause);
            Matcher equals = EQUALS_PATTERN.matcher(clause);
            Matcher like = LIKE_PATTERN.matcher(clause);
            if (inFolder.matches()) {
                folderRef = inFolder.group(1);
                continue;
            }
            if (inTree.matches()) {
                treeRef = inTree.group(1);
                continue;
            }
            if (contains.matches()) {
                containsSearch = contains.group(1);
                continue;
            }
            if (equals.matches()) {
                nameEquals = equals.group(1);
                continue;
            }
            if (like.matches()) {
                nameLike = like.group(1);
                continue;
            }
            throw new IllegalArgumentException("Unsupported CMIS WHERE clause fragment: " + clause);
        }

        return new ParsedQuery(
            parsed.fromType(),
            folderRef,
            resolveFolderId(folderRef),
            treeRef,
            resolveTreeFolderPath(treeRef),
            containsSearch,
            nameEquals,
            nameLike,
            parsed.sortField(),
            parsed.sortDirection()
        );
    }

    private UUID resolveFolderId(String folderRef) {
        if (folderRef == null || folderRef.isBlank() || "root".equalsIgnoreCase(folderRef)) {
            return null;
        }
        if (folderRef.startsWith("/")) {
            return folderService.getFolderByPath(folderRef).getId();
        }
        return folderService.getFolder(UUID.fromString(folderRef)).getId();
    }

    /**
     * Resolves an IN_TREE folder reference to its path string.
     * Accepts either a path (starts with '/') or a folder UUID.
     */
    private String resolveTreeFolderPath(String treeRef) {
        if (treeRef == null || treeRef.isBlank()) {
            return null;
        }
        if (treeRef.startsWith("/")) {
            // Verify the folder exists and return its canonical path
            Folder folder = folderService.getFolderByPath(treeRef);
            return folder.getPath();
        }
        Folder folder = folderService.getFolder(UUID.fromString(treeRef));
        return folder.getPath();
    }

    /**
     * When CONTAINS is present, runs a full-text search via DocumentRepository
     * and returns the set of matching document IDs.
     * Returns null when CONTAINS is not present (no filtering needed).
     * Returns an empty set for folder queries or empty search terms (no results possible).
     */
    private Set<UUID> resolveContainsCandidates(ParsedQuery parsed) {
        if (parsed.containsSearch() == null) {
            return null;
        }
        // CONTAINS does not apply to folders — they have no text_content
        if ("cmis:folder".equals(parsed.fromType())) {
            return Set.of();
        }
        String searchTerm = parsed.containsSearch().trim();
        if (searchTerm.isEmpty()) {
            return Set.of();
        }
        // Use a large page to collect candidate IDs; the JPA spec + permission filter narrows further
        return documentRepository.fullTextSearch(searchTerm, PageRequest.of(0, 10_000))
            .getContent()
            .stream()
            .map(Document::getId)
            .collect(Collectors.toSet());
    }

    private Specification<Node> buildSpecification(ParsedQuery parsed, Set<UUID> containsCandidateIds) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("deleted")));
            predicates.add(cb.equal(root.get("archiveStatus"), Node.ArchiveStatus.LIVE));

            if ("cmis:document".equals(parsed.fromType())) {
                predicates.add(cb.equal(root.type(), com.ecm.core.entity.Document.class));
            } else if ("cmis:folder".equals(parsed.fromType())) {
                predicates.add(cb.equal(root.type(), com.ecm.core.entity.Folder.class));
            }

            // IN_FOLDER — direct children of a specific folder
            if (parsed.folderRef() != null) {
                if ("root".equalsIgnoreCase(parsed.folderRef())) {
                    predicates.add(cb.isNull(root.get("parent")));
                } else if (parsed.folderId() != null) {
                    predicates.add(cb.equal(root.get("parent").get("id"), parsed.folderId()));
                }
            }

            // IN_TREE — all descendants under a folder (path LIKE folderPath/%)
            if (parsed.treeFolderPath() != null) {
                String pathPrefix = parsed.treeFolderPath();
                if (!pathPrefix.endsWith("/")) {
                    pathPrefix = pathPrefix + "/";
                }
                predicates.add(cb.like(root.get("path"), pathPrefix + "%"));
            }

            // CONTAINS — restrict to full-text search candidate IDs
            if (containsCandidateIds != null) {
                if (containsCandidateIds.isEmpty()) {
                    // No full-text matches — force an impossible predicate
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(root.get("id").in(containsCandidateIds));
                }
            }

            if (parsed.nameEquals() != null) {
                predicates.add(cb.equal(cb.lower(root.get("name")), parsed.nameEquals().toLowerCase(Locale.ROOT)));
            }

            if (parsed.nameLike() != null) {
                String sqlLike = parsed.nameLike().toLowerCase(Locale.ROOT).replace("%", "%");
                predicates.add(cb.like(cb.lower(root.get("name")), sqlLike));
            }

            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private Sort resolveSort(ParsedQuery parsed) {
        Sort.Direction direction = "desc".equalsIgnoreCase(parsed.sortDirection()) ? Sort.Direction.DESC : Sort.Direction.ASC;
        if (parsed.sortField() == null || "cmis:name".equals(parsed.sortField())) {
            return Sort.by(direction, "name");
        }
        if ("cmis:lastmodificationdate".equals(parsed.sortField())) {
            return Sort.by(direction, "lastModifiedDate");
        }
        if ("cmis:creationdate".equals(parsed.sortField())) {
            return Sort.by(direction, "createdDate");
        }
        return Sort.by(direction, "name");
    }

    private boolean isVisibleToCurrentTenant(Node node) {
        if (node == null) {
            return false;
        }
        if (!tenantWorkspaceScopeService.hasScopedTenantWorkspace()) {
            return true;
        }
        String path = node.getPath();
        return path != null
            && !path.isBlank()
            && tenantWorkspaceScopeService.isPathVisible(path);
    }

    private record ParsedQuery(
        String fromType,
        String folderRef,
        UUID folderId,
        String treeRef,
        String treeFolderPath,
        String containsSearch,
        String nameEquals,
        String nameLike,
        String sortField,
        String sortDirection
    ) {
    }
}
