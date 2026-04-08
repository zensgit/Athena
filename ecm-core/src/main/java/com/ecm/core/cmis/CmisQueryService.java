package com.ecm.core.cmis;

import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.service.FolderService;
import com.ecm.core.service.SecurityService;
import com.ecm.core.service.TenantWorkspaceScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CmisQueryService {

    private static final Pattern QUERY_PATTERN = Pattern.compile(
        "(?is)^SELECT\\s+\\*\\s+FROM\\s+(cmis:document|cmis:folder)\\s*(?:WHERE\\s+(.+?))?\\s*(?:ORDER\\s+BY\\s+(cmis:name|cmis:lastModificationDate|cmis:creationDate)\\s*(ASC|DESC)?)?\\s*$"
    );

    private static final Pattern IN_FOLDER_PATTERN = Pattern.compile("(?is)^IN_FOLDER\\('([^']+)'\\)$");
    private static final Pattern EQUALS_PATTERN = Pattern.compile("(?is)^cmis:name\\s*=\\s*'([^']*)'$");
    private static final Pattern LIKE_PATTERN = Pattern.compile("(?is)^cmis:name\\s+LIKE\\s+'([^']*)'$");

    private final NodeRepository nodeRepository;
    private final FolderService folderService;
    private final SecurityService securityService;
    private final TenantWorkspaceScopeService tenantWorkspaceScopeService;
    private final CmisObjectFactory objectFactory;

    public CmisModels.QueryResponse query(String statement, int skipCount, int maxItems) {
        ParsedQuery parsed = parse(statement);
        int normalizedSkip = Math.max(skipCount, 0);
        int normalizedMax = Math.max(Math.min(maxItems, 200), 1);

        List<Node> matched = nodeRepository.findAll(buildSpecification(parsed), resolveSort(parsed)).stream()
            .filter(this::isVisibleToCurrentTenant)
            .filter(node -> securityService.hasPermission(node, Permission.PermissionType.READ))
            .toList();

        List<CmisModels.ObjectEntry> entries = matched.stream()
            .skip(normalizedSkip)
            .limit(normalizedMax)
            .map(objectFactory::fromNode)
            .toList();

        return new CmisModels.QueryResponse(
            CmisObjectFactory.REPOSITORY_ID,
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
            sortField != null ? sortField.toLowerCase(Locale.ROOT) : null,
            sortDirection != null ? sortDirection.toLowerCase(Locale.ROOT) : "asc"
        );

        if (whereClause == null || whereClause.isBlank()) {
            return parsed;
        }

        String folderRef = null;
        String nameEquals = null;
        String nameLike = null;

        for (String fragment : whereClause.split("(?i)\\s+AND\\s+")) {
            String clause = fragment.trim();
            Matcher inFolder = IN_FOLDER_PATTERN.matcher(clause);
            Matcher equals = EQUALS_PATTERN.matcher(clause);
            Matcher like = LIKE_PATTERN.matcher(clause);
            if (inFolder.matches()) {
                folderRef = inFolder.group(1);
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

    private Specification<Node> buildSpecification(ParsedQuery parsed) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("deleted")));
            predicates.add(cb.equal(root.get("archiveStatus"), Node.ArchiveStatus.LIVE));

            if ("cmis:document".equals(parsed.fromType())) {
                predicates.add(cb.equal(root.type(), com.ecm.core.entity.Document.class));
            } else if ("cmis:folder".equals(parsed.fromType())) {
                predicates.add(cb.equal(root.type(), com.ecm.core.entity.Folder.class));
            }

            if (parsed.folderRef() != null) {
                if ("root".equalsIgnoreCase(parsed.folderRef())) {
                    predicates.add(cb.isNull(root.get("parent")));
                } else if (parsed.folderId() != null) {
                    predicates.add(cb.equal(root.get("parent").get("id"), parsed.folderId()));
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
        String nameEquals,
        String nameLike,
        String sortField,
        String sortDirection
    ) {
    }
}
