package com.ecm.core.service;

import com.ecm.core.entity.AuditLog;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.event.NodeSubtreeReindexRequestedEvent;
import com.ecm.core.event.NodesReindexRequestedEvent;
import com.ecm.core.event.RepositoryLifecyclePublisher;
import com.ecm.core.exception.IllegalOperationException;
import com.ecm.core.exception.ResourceNotFoundException;
import com.ecm.core.model.Category;
import com.ecm.core.repository.AuditLogRepository;
import com.ecm.core.repository.ArchivePolicyRepository;
import com.ecm.core.repository.CategoryRepository;
import com.ecm.core.repository.DispositionScheduleRepository;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.FolderRepository;
import com.ecm.core.repository.ImportJobRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.ReplicationJobRepository;
import com.ecm.core.repository.TransferTargetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RecordsManagementService {

    public static final String RECORD_ASPECT = "rm:record";
    private static final String RM_AUDIT_PREFIX = "RM_";
    private static final Set<String> RM_TIMELINE_GOVERNANCE_EVENTS = Set.of(
        "RM_FILE_PLAN_CREATED",
        "RM_FILE_PLAN_UPDATED",
        "RM_FILE_PLAN_RENAMED",
        "RM_FILE_PLAN_MOVED",
        "RM_FILE_PLAN_DELETED",
        "RM_RECORD_CATEGORY_CREATED",
        "RM_RECORD_CATEGORY_UPDATED",
        "RM_RECORD_CATEGORY_RENAMED",
        "RM_RECORD_CATEGORY_MOVED",
        "RM_RECORD_CATEGORY_DELETED"
    );
    private static final int RM_ACTIVITY_HIGHLIGHTS_DEFAULT_WINDOW_DAYS = 7;
    private static final int RM_ACTIVITY_HIGHLIGHTS_MIN_WINDOW_DAYS = 2;
    private static final int RM_ACTIVITY_HIGHLIGHTS_MAX_WINDOW_DAYS = 30;
    private static final int RM_ACTIVITY_BREAKDOWN_DEFAULT_DAYS = 28;
    private static final int RM_ACTIVITY_BREAKDOWN_MIN_DAYS = 7;
    private static final int RM_ACTIVITY_BREAKDOWN_MAX_DAYS = 84;
    private static final int RM_ACTIVITY_BREAKDOWN_DEFAULT_BUCKET_DAYS = 7;
    private static final int RM_ACTIVITY_BREAKDOWN_MIN_BUCKET_DAYS = 2;
    private static final int RM_ACTIVITY_BREAKDOWN_MAX_BUCKET_DAYS = 14;
    private static final int RM_ACTIVITY_CONTRIBUTORS_DEFAULT_DAYS = 28;
    private static final int RM_ACTIVITY_CONTRIBUTORS_MIN_DAYS = 7;
    private static final int RM_ACTIVITY_CONTRIBUTORS_MAX_DAYS = 90;
    private static final int RM_ACTIVITY_CONTRIBUTORS_DEFAULT_LIMIT = 5;
    private static final int RM_ACTIVITY_CONTRIBUTORS_MIN_LIMIT = 1;
    private static final int RM_ACTIVITY_CONTRIBUTORS_MAX_LIMIT = 50;
    private static final int RM_ACTIVITY_CONTRIBUTOR_TREND_DEFAULT_DAYS = 28;
    private static final int RM_ACTIVITY_CONTRIBUTOR_TREND_MIN_DAYS = 7;
    private static final int RM_ACTIVITY_CONTRIBUTOR_TREND_MAX_DAYS = 90;
    private static final int RM_ACTIVITY_CONTRIBUTOR_TREND_DEFAULT_BUCKET_DAYS = 7;
    private static final int RM_ACTIVITY_CONTRIBUTOR_TREND_MIN_BUCKET_DAYS = 1;
    private static final int RM_ACTIVITY_CONTRIBUTOR_TREND_MAX_BUCKET_DAYS = 14;
    private static final int RM_ACTIVITY_CONTRIBUTOR_TREND_DEFAULT_LIMIT = 5;
    private static final int RM_ACTIVITY_CONTRIBUTOR_TREND_MIN_LIMIT = 1;
    private static final int RM_ACTIVITY_CONTRIBUTOR_TREND_MAX_LIMIT = 20;
    private static final int RM_ACTIVITY_EVENT_TYPES_DEFAULT_DAYS = 28;
    private static final int RM_ACTIVITY_EVENT_TYPES_MIN_DAYS = 7;
    private static final int RM_ACTIVITY_EVENT_TYPES_MAX_DAYS = 90;
    private static final int RM_ACTIVITY_EVENT_TYPES_DEFAULT_LIMIT = 8;
    private static final int RM_ACTIVITY_EVENT_TYPES_MIN_LIMIT = 1;
    private static final int RM_ACTIVITY_EVENT_TYPES_MAX_LIMIT = 20;
    private static final int RM_ACTIVITY_EVENT_TYPE_TREND_DEFAULT_DAYS = 28;
    private static final int RM_ACTIVITY_EVENT_TYPE_TREND_MIN_DAYS = 7;
    private static final int RM_ACTIVITY_EVENT_TYPE_TREND_MAX_DAYS = 90;
    private static final int RM_ACTIVITY_EVENT_TYPE_TREND_DEFAULT_BUCKET_DAYS = 7;
    private static final int RM_ACTIVITY_EVENT_TYPE_TREND_MIN_BUCKET_DAYS = 1;
    private static final int RM_ACTIVITY_EVENT_TYPE_TREND_MAX_BUCKET_DAYS = 14;
    private static final int RM_ACTIVITY_EVENT_TYPE_TREND_DEFAULT_LIMIT = 8;
    private static final int RM_ACTIVITY_EVENT_TYPE_TREND_MIN_LIMIT = 1;
    private static final int RM_ACTIVITY_EVENT_TYPE_TREND_MAX_LIMIT = 20;
    private static final int RM_ACTIVITY_EVENT_TYPE_REPORT_DEFAULT_DAYS = 28;
    private static final int RM_ACTIVITY_EVENT_TYPE_REPORT_MAX_DAYS = 90;
    private static final int RM_ACTIVITY_EVENT_TYPE_REPORT_DEFAULT_LIMIT = 8;
    private static final int RM_ACTIVITY_EVENT_TYPE_REPORT_MIN_LIMIT = 1;
    private static final int RM_ACTIVITY_EVENT_TYPE_REPORT_MAX_LIMIT = 20;
    private static final int RM_ACTIVITY_CONTRIBUTOR_REPORT_DEFAULT_DAYS = 28;
    private static final int RM_ACTIVITY_CONTRIBUTOR_REPORT_MAX_DAYS = 90;
    private static final int RM_ACTIVITY_CONTRIBUTOR_REPORT_DEFAULT_LIMIT = 5;
    private static final int RM_ACTIVITY_CONTRIBUTOR_REPORT_MIN_LIMIT = 1;
    private static final int RM_ACTIVITY_CONTRIBUTOR_REPORT_MAX_LIMIT = 50;
    private static final int RM_ACTIVITY_CONTRIBUTOR_REPORT_DEFAULT_EVENT_TYPE_LIMIT = 3;
    private static final int RM_ACTIVITY_CONTRIBUTOR_REPORT_MIN_EVENT_TYPE_LIMIT = 1;
    private static final int RM_ACTIVITY_CONTRIBUTOR_REPORT_MAX_EVENT_TYPE_LIMIT = 10;
    private static final int RM_ACTIVITY_CONTRIBUTOR_FAMILY_REPORT_DEFAULT_DAYS = 28;
    private static final int RM_ACTIVITY_CONTRIBUTOR_FAMILY_REPORT_MAX_DAYS = 90;
    private static final int RM_ACTIVITY_CONTRIBUTOR_FAMILY_REPORT_DEFAULT_LIMIT = 5;
    private static final int RM_ACTIVITY_CONTRIBUTOR_FAMILY_REPORT_MIN_LIMIT = 1;
    private static final int RM_ACTIVITY_CONTRIBUTOR_FAMILY_REPORT_MAX_LIMIT = 50;
    private static final int RM_ACTIVITY_FAMILIES_DEFAULT_DAYS = 28;
    private static final int RM_ACTIVITY_FAMILIES_MIN_DAYS = 7;
    private static final int RM_ACTIVITY_FAMILIES_MAX_DAYS = 90;
    private static final int RM_ACTIVITY_FAMILY_TREND_DEFAULT_DAYS = 28;
    private static final int RM_ACTIVITY_FAMILY_TREND_MIN_DAYS = 7;
    private static final int RM_ACTIVITY_FAMILY_TREND_MAX_DAYS = 90;
    private static final int RM_ACTIVITY_FAMILY_TREND_DEFAULT_BUCKET_DAYS = 7;
    private static final int RM_ACTIVITY_FAMILY_TREND_MIN_BUCKET_DAYS = 1;
    private static final int RM_ACTIVITY_FAMILY_TREND_MAX_BUCKET_DAYS = 14;
    private static final int RM_ACTIVITY_FAMILY_REPORT_DEFAULT_DAYS = 28;
    private static final int RM_ACTIVITY_FAMILY_REPORT_MAX_DAYS = 90;
    private static final int RM_ACTIVITY_FAMILY_REPORT_DEFAULT_EVENT_TYPE_LIMIT = 3;
    private static final int RM_ACTIVITY_FAMILY_REPORT_DEFAULT_CONTRIBUTOR_LIMIT = 3;
    private static final int RM_ACTIVITY_FAMILY_REPORT_MIN_LIMIT = 1;
    private static final int RM_ACTIVITY_FAMILY_REPORT_MAX_LIMIT = 10;
    public static final String DECLARED_AT_PROPERTY = "rm:declaredAt";
    public static final String DECLARED_BY_PROPERTY = "rm:declaredBy";
    public static final String DECLARATION_COMMENT_PROPERTY = "rm:declarationComment";
    public static final String DECLARED_VERSION_LABEL_PROPERTY = "rm:declaredVersionLabel";
    public static final String RECORD_CATEGORY_ID_PROPERTY = "rm:recordCategoryId";
    public static final String RECORD_CATEGORY_NAME_PROPERTY = "rm:recordCategoryName";
    public static final String RECORD_CATEGORY_PATH_PROPERTY = "rm:recordCategoryPath";
    public static final String RECORD_CATEGORY_ROOT_PATH = "/Records Management";

    public enum RmEventFamily {
        DECLARED,
        UNDECLARED,
        CATEGORY_ASSIGNED,
        GOVERNANCE_CHANGE,
        OTHER
    }

    private final NodeRepository nodeRepository;
    private final DocumentRepository documentRepository;
    private final FolderRepository folderRepository;
    private final CategoryRepository categoryRepository;
    private final AuditLogRepository auditLogRepository;
    private final ArchivePolicyRepository archivePolicyRepository;
    private final DispositionScheduleRepository dispositionScheduleRepository;
    private final ImportJobRepository importJobRepository;
    private final ReplicationJobRepository replicationJobRepository;
    private final TransferTargetRepository transferTargetRepository;
    private final SecurityService securityService;
    private final AuditService auditService;
    private final LegalHoldService legalHoldService;
    private final FolderService folderService;
    private final TenantWorkspaceScopeService tenantWorkspaceScopeService;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    @Lazy
    private NodeService nodeService;

    @Transactional(readOnly = true)
    public List<RecordDeclarationDto> listRecords() {
        requireAdmin();
        return loadVisibleLiveRecords().stream()
            .sorted(Comparator.comparing(Document::getCreatedDate, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
            .map(this::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<FilePlanDto> listFilePlans() {
        requireAdmin();
        return folderRepository.findActiveFoldersByType(Folder.FolderType.FILE_PLAN).stream()
            .filter(folder -> folder.getArchiveStatus() == Node.ArchiveStatus.LIVE)
            .filter(folder -> tenantWorkspaceScopeService.isPathVisible(folder.getPath()))
            .sorted(Comparator.comparing(Folder::getPath, Comparator.nullsLast(String::compareToIgnoreCase)))
            .map(this::toFilePlanDto)
            .toList();
    }

    public FilePlanDto createFilePlan(CreateFilePlanRequest request) {
        requireAdmin();
        if (request == null || !StringUtils.hasText(request.name())) {
            throw new IllegalArgumentException("File plan name is required");
        }
        validateFilePlanParent(request.parentId());
        Folder created = folderService.createFolder(new FolderService.CreateFolderRequest(
            request.name().trim(),
            StringUtils.hasText(request.description()) ? request.description().trim() : null,
            request.parentId(),
            Folder.FolderType.FILE_PLAN,
            null,
            null,
            false,
            null,
            true,
            false,
            null
        ));
        auditService.logEvent(
            "RM_FILE_PLAN_CREATED",
            created.getId(),
            created.getName(),
            securityService.getCurrentUser(),
            "Created file plan at path " + created.getPath()
        );
        return toFilePlanDto(created);
    }

    public FilePlanDto updateFilePlan(UUID folderId, UpdateFilePlanRequest request) {
        requireAdmin();
        Folder filePlan = loadFilePlan(folderId);
        String description = trimToNull(request != null ? request.description() : null);
        filePlan.setDescription(description);
        Folder saved = folderRepository.save(filePlan);
        RepositoryLifecyclePublisher.publishNodeUpdated(
            eventPublisher,
            saved,
            securityService.getCurrentUser(),
            null
        );
        auditService.logEvent(
            "RM_FILE_PLAN_UPDATED",
            saved.getId(),
            saved.getName(),
            securityService.getCurrentUser(),
            "Updated file plan description"
        );
        return toFilePlanDto(saved);
    }

    public FilePlanDto renameFilePlan(UUID folderId, RenameFilePlanRequest request) {
        requireAdmin();
        String name = trimToNull(request != null ? request.name() : null);
        if (name == null) {
            throw new IllegalArgumentException("File plan name is required");
        }

        Folder filePlan = loadFilePlan(folderId);
        String oldPath = filePlan.getPath();
        String oldName = filePlan.getName();
        if (name.equals(filePlan.getName())) {
            return toFilePlanDto(filePlan);
        }

        Folder renamed = folderService.updateFolder(
            folderId,
            new FolderService.UpdateFolderRequest(name, null, null, null, null, null, null, null, null)
        );
        refreshDescendantPaths(renamed);
        eventPublisher.publishEvent(new NodeSubtreeReindexRequestedEvent(renamed, securityService.getCurrentUser()));
        auditService.logEvent(
            "RM_FILE_PLAN_RENAMED",
            renamed.getId(),
            renamed.getName(),
            securityService.getCurrentUser(),
            "Renamed file plan from '" + oldName + "' to '" + renamed.getName() + "' (" + oldPath + " -> " + renamed.getPath() + ")"
        );
        return toFilePlanDto(renamed);
    }

    public FilePlanDto moveFilePlan(UUID folderId, MoveFilePlanRequest request) {
        requireAdmin();
        UUID targetParentId = request != null ? request.targetParentId() : null;
        if (targetParentId == null) {
            throw new IllegalArgumentException("Target parent is required");
        }

        Folder filePlan = loadFilePlan(folderId);
        if (filePlan.getParent() != null && Objects.equals(filePlan.getParent().getId(), targetParentId)) {
            return toFilePlanDto(filePlan);
        }

        validateFilePlanParent(targetParentId);
        String oldPath = filePlan.getPath();
        Node movedNode = nodeService.moveNode(folderId, targetParentId);
        Folder moved = movedNode instanceof Folder folder ? folder : loadFilePlan(folderId);
        auditService.logEvent(
            "RM_FILE_PLAN_MOVED",
            moved.getId(),
            moved.getName(),
            securityService.getCurrentUser(),
            "Moved file plan from " + oldPath + " to " + moved.getPath()
        );
        return toFilePlanDto(moved);
    }

    public void deleteFilePlan(UUID folderId) {
        requireAdmin();
        Folder filePlan = loadFilePlan(folderId);
        long childCount = folderRepository.countChildren(filePlan.getId());
        if (childCount > 0) {
            throw new IllegalOperationException(
                "Cannot delete file plan '" + filePlan.getName() + "' because it is not empty"
            );
        }
        dispositionScheduleRepository.findByFolderId(filePlan.getId()).ifPresent(schedule -> {
            throw new IllegalOperationException(
                "Cannot delete file plan '" + filePlan.getName() + "' because it has a disposition schedule"
            );
        });
        archivePolicyRepository.findByFolderId(filePlan.getId()).ifPresent(policy -> {
            throw new IllegalOperationException(
                "Cannot delete file plan '" + filePlan.getName() + "' because it has an archive policy"
            );
        });
        folderService.deleteFolder(filePlan.getId(), false, false);
        auditService.logEvent(
            "RM_FILE_PLAN_DELETED",
            filePlan.getId(),
            filePlan.getName(),
            securityService.getCurrentUser(),
            "Deleted empty file plan"
        );
    }

    @Transactional(readOnly = true)
    public List<RecordCategoryDto> listRecordCategories() {
        requireAdmin();
        ensureRecordCategoryRoot();
        return categoryRepository.findByPurposeAndActiveTrueOrderByPathAsc(Category.Purpose.RECORD).stream()
            .filter(category -> category.getPath() != null
                && (category.getPath().equals(RECORD_CATEGORY_ROOT_PATH) || category.getPath().startsWith(RECORD_CATEGORY_ROOT_PATH + "/")))
            .map(this::toRecordCategoryDto)
            .toList();
    }

    public RecordCategoryDto createRecordCategory(CreateRecordCategoryRequest request) {
        requireAdmin();
        if (request == null || !StringUtils.hasText(request.name())) {
            throw new IllegalArgumentException("Record category name is required");
        }

        Category parent = request.parentId() != null
            ? loadRecordCategory(request.parentId())
            : ensureRecordCategoryRoot();

        Category category = new Category();
        category.setName(request.name().trim());
        category.setDescription(StringUtils.hasText(request.description()) ? request.description().trim() : null);
        category.setCreator(securityService.getCurrentUser());
        category.setParent(parent);
        category.setPurpose(Category.Purpose.RECORD);
        category.setActive(true);
        Category saved = categoryRepository.save(category);
        auditService.logEvent(
            "RM_RECORD_CATEGORY_CREATED",
            null,
            saved.getName(),
            securityService.getCurrentUser(),
            "Created record category at path " + saved.getPath()
        );
        return toRecordCategoryDto(saved);
    }

    public RecordCategoryDto updateRecordCategory(UUID categoryId, UpdateRecordCategoryRequest request) {
        requireAdmin();
        Category category = loadActiveRecordCategory(categoryId);
        if (isRecordCategoryRoot(category)) {
            throw new IllegalOperationException("The record category root cannot be modified");
        }
        category.setDescription(trimToNull(request != null ? request.description() : null));
        Category saved = categoryRepository.save(category);
        auditService.logEvent(
            "RM_RECORD_CATEGORY_UPDATED",
            null,
            saved.getName(),
            securityService.getCurrentUser(),
            "Updated record category at path " + saved.getPath()
        );
        return toRecordCategoryDto(saved);
    }

    public RecordCategoryDto renameRecordCategory(UUID categoryId, RenameRecordCategoryRequest request) {
        requireAdmin();
        String name = trimToNull(request != null ? request.name() : null);
        if (name == null) {
            throw new IllegalArgumentException("Record category name is required");
        }

        Category category = loadActiveRecordCategory(categoryId);
        if (isRecordCategoryRoot(category)) {
            throw new IllegalOperationException("The record category root cannot be renamed");
        }
        if (name.equals(category.getName())) {
            return toRecordCategoryDto(category);
        }

        String oldName = category.getName();
        String oldPath = category.getPath();
        category.setName(name);
        category.setParent(category.getParent());
        Category saved = categoryRepository.save(category);
        List<Category> subtree = new ArrayList<>();
        subtree.add(saved);
        refreshRecordCategoryDescendantPaths(saved, subtree);
        int repairedRecords = repairDeclaredRecordCategoryMetadata(subtree).size();
        auditService.logEvent(
            "RM_RECORD_CATEGORY_RENAMED",
            null,
            saved.getName(),
            securityService.getCurrentUser(),
            "Renamed record category from '" + oldName + "' to '" + saved.getName()
                + "' (" + oldPath + " -> " + saved.getPath() + "); repaired " + repairedRecords + " declared record(s)"
        );
        return toRecordCategoryDto(saved);
    }

    public RecordCategoryDto moveRecordCategory(UUID categoryId, MoveRecordCategoryRequest request) {
        requireAdmin();
        UUID targetParentId = request != null ? request.targetParentId() : null;
        if (targetParentId == null) {
            throw new IllegalArgumentException("Target parent is required");
        }

        Category category = loadActiveRecordCategory(categoryId);
        if (isRecordCategoryRoot(category)) {
            throw new IllegalOperationException("The record category root cannot be moved");
        }

        Category targetParent = loadActiveRecordCategory(targetParentId);
        assertRecordCategoryMoveAllowed(category, targetParent);
        if (category.getParent() != null && Objects.equals(category.getParent().getId(), targetParentId)) {
            return toRecordCategoryDto(category);
        }

        String oldPath = category.getPath();
        category.setParent(targetParent);
        Category saved = categoryRepository.save(category);
        List<Category> subtree = new ArrayList<>();
        subtree.add(saved);
        refreshRecordCategoryDescendantPaths(saved, subtree);
        int repairedRecords = repairDeclaredRecordCategoryMetadata(subtree).size();
        auditService.logEvent(
            "RM_RECORD_CATEGORY_MOVED",
            null,
            saved.getName(),
            securityService.getCurrentUser(),
            "Moved record category from " + oldPath + " to " + saved.getPath()
                + "; repaired " + repairedRecords + " declared record(s)"
        );
        return toRecordCategoryDto(saved);
    }

    public void deleteRecordCategory(UUID categoryId) {
        requireAdmin();
        Category category = loadActiveRecordCategory(categoryId);
        if (isRecordCategoryRoot(category)) {
            throw new IllegalOperationException("The record category root cannot be deleted");
        }
        if (!categoryRepository.findByParentAndActiveTrue(category).isEmpty()) {
            throw new IllegalOperationException(
                "Cannot delete record category '" + category.getName() + "' because it has child categories"
            );
        }
        if (!nodeRepository.findByCategoriesInAndDeletedFalse(Set.of(category)).isEmpty()) {
            throw new IllegalOperationException(
                "Cannot delete record category '" + category.getName() + "' because it is assigned to node(s)"
            );
        }
        categoryRepository.delete(category);
        auditService.logEvent(
            "RM_RECORD_CATEGORY_DELETED",
            null,
            category.getName(),
            securityService.getCurrentUser(),
            "Deleted record category at path " + category.getPath()
        );
    }

    @Transactional(readOnly = true)
    public Optional<RecordDeclarationDto> getRecord(UUID nodeId) {
        requireAdmin();
        return documentRepository.findById(nodeId)
            .filter(this::isVisibleLiveDocument)
            .filter(this::isDeclaredRecord)
            .map(this::toDto);
    }

    public RecordDeclarationDto declareRecord(UUID nodeId, DeclareRecordRequest request) {
        requireAdmin();
        Document document = loadLiveDocument(nodeId);
        if (document.isWorkingCopy()) {
            throw new IllegalArgumentException("Working copies cannot be declared as records");
        }
        if (document.isCheckedOut()) {
            throw new IllegalStateException("Checked out documents cannot be declared as records");
        }
        if (isDeclaredRecord(document)) {
            return toDto(document);
        }

        LocalDateTime now = LocalDateTime.now();
        String currentUser = securityService.getCurrentUser();

        document.addAspect(RECORD_ASPECT);
        Map<String, Object> properties = document.getProperties();
        properties.put(DECLARED_AT_PROPERTY, now.toString());
        properties.put(DECLARED_BY_PROPERTY, currentUser);
        properties.put(DECLARED_VERSION_LABEL_PROPERTY, document.getVersionLabel());
        if (request != null && StringUtils.hasText(request.comment())) {
            properties.put(DECLARATION_COMMENT_PROPERTY, request.comment().trim());
        }
        if (request != null && request.categoryId() != null) {
            Category category = loadRecordCategory(request.categoryId());
            applyRecordCategory(document, category);
        }
        document.setLastModifiedBy(currentUser);
        document.setLastModifiedDate(now);

        Document saved = documentRepository.save(document);
        RepositoryLifecyclePublisher.publishNodeUpdated(eventPublisher, saved, currentUser, null);
        auditService.logEvent(
            "RM_RECORD_DECLARED",
            saved.getId(),
            saved.getName(),
            currentUser,
            "Declared document as record"
        );
        log.info("Declared document {} as a record", saved.getId());
        return toDto(saved);
    }

    public RecordDeclarationDto assignRecordCategory(UUID nodeId, UUID categoryId) {
        requireAdmin();
        if (categoryId == null) {
            throw new IllegalArgumentException("Record category id is required");
        }
        Document document = loadLiveDocument(nodeId);
        if (!isDeclaredRecord(document)) {
            throw new IllegalOperationException("Cannot assign record category because node '" + document.getName() + "' is not declared as a record");
        }
        Category category = loadRecordCategory(categoryId);
        applyRecordCategory(document, category);
        document.setLastModifiedBy(securityService.getCurrentUser());
        document.setLastModifiedDate(LocalDateTime.now());
        Document saved = documentRepository.save(document);
        RepositoryLifecyclePublisher.publishNodeUpdated(eventPublisher, saved, securityService.getCurrentUser(), null);
        auditService.logEvent(
            "RM_RECORD_CATEGORY_ASSIGNED",
            saved.getId(),
            saved.getName(),
            securityService.getCurrentUser(),
            "Assigned record category " + category.getPath()
        );
        return toDto(saved);
    }

    public void undeclareRecord(UUID nodeId, UndeclareRecordRequest request) {
        requireAdmin();
        Document document = loadLiveDocument(nodeId);
        String currentUser = securityService.getCurrentUser();
        String reason = trimToNull(request != null ? request.reason() : null);
        if (reason == null) {
            throw new IllegalArgumentException("Undeclare reason is required");
        }

        if (!isDeclaredRecord(document)) {
            blockedUndeclare(document, currentUser, reason,
                "Cannot undeclare because node '" + document.getName() + "' is not declared as a record");
        }
        if (document.isWorkingCopy()) {
            blockedUndeclare(document, currentUser, reason,
                "Cannot undeclare because node '" + document.getName() + "' is a working copy");
        }
        if (document.isCheckedOut()) {
            blockedUndeclare(document, currentUser, reason,
                "Cannot undeclare because node '" + document.getName() + "' is checked out");
        }
        try {
            legalHoldService.assertOperationAllowed(document, "undeclare record");
        } catch (IllegalOperationException ex) {
            blockedUndeclare(document, currentUser, reason, ex.getMessage());
        }
        findContainingFilePlan(document).ifPresent(filePlan -> blockedUndeclare(
            document,
            currentUser,
            reason,
            "Cannot undeclare because node '" + document.getName() + "' is governed by file plan '" + filePlan.getName() + "'"
        ));

        Map<String, Object> properties = document.getProperties();
        properties.remove(DECLARED_AT_PROPERTY);
        properties.remove(DECLARED_BY_PROPERTY);
        properties.remove(DECLARATION_COMMENT_PROPERTY);
        properties.remove(DECLARED_VERSION_LABEL_PROPERTY);
        properties.remove(RECORD_CATEGORY_ID_PROPERTY);
        properties.remove(RECORD_CATEGORY_NAME_PROPERTY);
        properties.remove(RECORD_CATEGORY_PATH_PROPERTY);
        document.getCategories().removeIf(category -> category != null && category.getPurpose() == Category.Purpose.RECORD);
        document.removeAspect(RECORD_ASPECT);
        document.setLastModifiedBy(currentUser);
        document.setLastModifiedDate(LocalDateTime.now());

        Document saved = documentRepository.save(document);
        RepositoryLifecyclePublisher.publishNodeUpdated(eventPublisher, saved, currentUser, null);
        auditService.logEvent(
            "RM_RECORD_UNDECLARED",
            saved.getId(),
            saved.getName(),
            currentUser,
            "Undeclared document as record. Reason: " + reason
        );
        log.info("Undeclared document {} as a record", saved.getId());
    }

    @Transactional(readOnly = true)
    public boolean isDeclaredRecord(Node node) {
        return node != null && node.hasAspect(RECORD_ASPECT);
    }

    @Transactional(readOnly = true)
    public boolean isFilePlanFolder(Folder folder) {
        return folder != null && folder.getFolderType() == Folder.FolderType.FILE_PLAN;
    }

    @Transactional(readOnly = true)
    public boolean isGovernedByFilePlan(Node node) {
        return findContainingFilePlan(node).isPresent();
    }

    @Transactional(readOnly = true)
    public void assertCreateInFolderAllowed(Node targetFolder, String operation) {
        if (targetFolder == null) {
            return;
        }
        findContainingFilePlan(targetFolder).ifPresent(filePlan -> {
            if (Objects.equals(filePlan.getId(), targetFolder.getId())) {
                throw new IllegalOperationException(
                    "Cannot " + operation + " because target folder '" + targetFolder.getName() + "' is a file plan"
                );
            }
            throw new IllegalOperationException(
                "Cannot " + operation + " because target folder '" + targetFolder.getName()
                    + "' is governed by file plan '" + filePlan.getName() + "'"
            );
        });
    }

    @Transactional(readOnly = true)
    public void assertArchiveMutationAllowed(Node node, String operation) {
        if (node == null) {
            return;
        }
        assertArchiveMutationAllowed(node, operation, visibleFilePlans());
    }

    @Transactional(readOnly = true)
    public void assertDirectMutationAllowed(Node node, String operation) {
        if (!isDeclaredRecord(node)) {
            return;
        }
        throw new IllegalOperationException(
            "Cannot " + operation + " because node '" + node.getName() + "' is declared as a record"
        );
    }

    @Transactional(readOnly = true)
    public void assertHierarchyMutationAllowed(Node node, String operation) {
        if (node == null) {
            return;
        }
        List<Document> blockingRecords = findBlockingRecords(node);
        if (blockingRecords.isEmpty()) {
            return;
        }

        boolean direct = blockingRecords.stream().anyMatch(record -> Objects.equals(record.getId(), node.getId()));
        if (direct) {
            throw new IllegalOperationException(
                "Cannot " + operation + " because node '" + node.getName() + "' is declared as a record"
            );
        }

        String recordNames = blockingRecords.stream()
            .map(Node::getName)
            .distinct()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .reduce((left, right) -> left + ", " + right)
            .orElse("unknown");
        throw new IllegalOperationException(
            "Cannot " + operation + " because node '" + node.getName() + "' contains declared record(s): " + recordNames
        );
    }

    @Transactional(readOnly = true)
    public void assertRestoreAllowed(Node node, String operation) {
        assertArchiveMutationAllowed(node, operation);
        assertHierarchyMutationAllowed(node, operation);
    }

    @Transactional(readOnly = true)
    public void assertRestoreScopeAllowed(Node root, List<Node> scope, String operation) {
        if (root == null) {
            return;
        }
        List<Folder> filePlans = visibleFilePlans();
        assertArchiveMutationAllowed(root, operation, filePlans);
        assertHierarchyMutationAllowed(root, operation);

        if (scope == null || scope.isEmpty()) {
            return;
        }

        for (Node candidate : scope) {
            if (candidate == null || Objects.equals(candidate.getId(), root.getId())) {
                continue;
            }
            if (isDeclaredRecord(candidate)) {
                throw new IllegalOperationException(
                    "Cannot " + operation + " because node '" + root.getName()
                        + "' contains declared record(s): " + candidate.getName()
                );
            }
            findContainingFilePlan(candidate, filePlans).ifPresent(filePlan -> {
                if (Objects.equals(filePlan.getId(), candidate.getId())) {
                    throw new IllegalOperationException(
                        "Cannot " + operation + " because node '" + root.getName()
                            + "' contains file plan '" + candidate.getName() + "'"
                    );
                }
                throw new IllegalOperationException(
                    "Cannot " + operation + " because node '" + root.getName()
                        + "' contains node '" + candidate.getName()
                        + "' governed by file plan '" + filePlan.getName() + "'"
                );
            });
        }
    }

    @Transactional(readOnly = true)
    public RecordsSummaryDto getSummary() {
        requireAdmin();
        List<Document> records = loadVisibleLiveRecords();
        List<Folder> filePlans = visibleFilePlans();
        List<Category> recordCategories = categoryRepository.findByPurposeAndActiveTrueOrderByPathAsc(Category.Purpose.RECORD).stream()
            .filter(category -> category.getPath() != null
                && (category.getPath().equals(RECORD_CATEGORY_ROOT_PATH) || category.getPath().startsWith(RECORD_CATEGORY_ROOT_PATH + "/")))
            .toList();

        Map<String, Long> categoryBreakdown = new LinkedHashMap<>();
        Map<String, Long> filePlanBreakdown = new LinkedHashMap<>();
        long uncategorizedCount = 0;
        long outsideFilePlanCount = 0;

        for (Document record : records) {
            RecordDeclarationDto dto = toDto(record);
            String categoryPath = StringUtils.hasText(dto.recordCategoryPath()) ? dto.recordCategoryPath() : "(Uncategorized)";
            categoryBreakdown.merge(categoryPath, 1L, Long::sum);
            if (!StringUtils.hasText(dto.recordCategoryPath())) {
                uncategorizedCount++;
            }

            Optional<Folder> filePlan = findContainingFilePlan(record);
            if (filePlan.isPresent()) {
                filePlanBreakdown.merge(filePlan.get().getPath(), 1L, Long::sum);
            } else {
                outsideFilePlanCount++;
                filePlanBreakdown.merge("(Outside File Plan)", 1L, Long::sum);
            }
        }

        return new RecordsSummaryDto(
            records.size(),
            filePlans.size(),
            recordCategories.size(),
            uncategorizedCount,
            outsideFilePlanCount,
            categoryBreakdown.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER))
                .map(entry -> new SummaryBucketDto(entry.getKey(), entry.getValue()))
                .toList(),
            filePlanBreakdown.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER))
                .map(entry -> new SummaryBucketDto(entry.getKey(), entry.getValue()))
                .toList()
        );
    }

    @Transactional(readOnly = true)
    public Page<RecordAuditEntryDto> listAudit(String eventType, String username, LocalDateTime from, LocalDateTime to, RmEventFamily family, Pageable pageable) {
        requireAdmin();
        String normalizedEventType = normalizeAuditEventType(eventType);
        String normalizedUsername = StringUtils.hasText(username) ? username.trim() : null;

        if (family == null) {
            return auditLogRepository.findRecordsManagementTimeline(normalizedEventType, normalizedUsername, from, to, pageable)
                .map(this::toAuditDto);
        }

        if (family == RmEventFamily.OTHER) {
            if (normalizedEventType != null && !"OTHER".equals(classifyActivityFamily(normalizedEventType))) {
                return Page.empty(pageable);
            }
            return auditLogRepository.findOtherRecordsManagementTimeline(
                    resolveNonOtherEventTypes(),
                    normalizedEventType,
                    normalizedUsername,
                    from,
                    to,
                    pageable
                )
                .map(this::toAuditDto);
        }

        List<String> familyEventTypes = resolveEventTypes(family);

        if (normalizedEventType != null) {
            if (!familyEventTypes.contains(normalizedEventType)) {
                return Page.empty(pageable);
            }
            familyEventTypes = List.of(normalizedEventType);
        }

        return auditLogRepository.findByEventTypesAndFilters(familyEventTypes, normalizedUsername, from, to, pageable)
            .map(this::toAuditDto);
    }

    @Transactional(readOnly = true)
    public RecordsOperationsTelemetryDto getOperationsTelemetry(Integer limit) {
        requireAdmin();
        int effectiveLimit = normalizeTelemetryLimit(limit);

        List<GovernedImportJobDto> recentImportJobs = importJobRepository
            .findAllByOrderByCreatedAtDesc(PageRequest.of(0, effectiveLimit))
            .getContent().stream()
            .map(this::toGovernedImportJobDto)
            .flatMap(Optional::stream)
            .toList();

        List<GovernedTransferJobDto> recentTransferJobs = replicationJobRepository
            .findAllByOrderByCreatedAtDesc(PageRequest.of(0, effectiveLimit))
            .getContent().stream()
            .map(this::toGovernedTransferJobDto)
            .flatMap(Optional::stream)
            .toList();

        List<com.ecm.core.entity.ImportJob> governedImportJobs = importJobRepository.findAll().stream()
            .filter(job -> classifyImportJob(job).governed())
            .toList();
        List<com.ecm.core.entity.ReplicationJob> governedTransferJobs = replicationJobRepository.findAll().stream()
            .filter(job -> classifyTransferJob(job).governed())
            .toList();

        return new RecordsOperationsTelemetryDto(
            governedImportJobs.size(),
            governedImportJobs.stream().filter(this::isActiveImportJob).count(),
            governedImportJobs.stream().filter(this::isFailedImportJob).count(),
            governedTransferJobs.size(),
            governedTransferJobs.stream().filter(this::isActiveTransferJob).count(),
            governedTransferJobs.stream().filter(this::isFailedTransferJob).count(),
            bucketBreakdown(
                governedImportJobs.stream()
                    .map(job -> job.getStatus() != null ? job.getStatus().name() : "UNKNOWN")
                    .toList()
            ),
            bucketBreakdown(
                governedTransferJobs.stream()
                    .map(job -> {
                        String status = job.getStatus() != null ? job.getStatus().name() : "UNKNOWN";
                        String transport = job.getTransportStatus() != null ? job.getTransportStatus().name() : "UNKNOWN";
                        return status + " / " + transport;
                    })
                    .toList()
            ),
            bucketBreakdown(
                governedImportJobs.stream()
                    .map(this::classifyImportJob)
                    .flatMap(classification -> classification.reasons().stream())
                    .toList()
            ),
            bucketBreakdown(
                governedTransferJobs.stream()
                    .map(this::classifyTransferJob)
                    .flatMap(classification -> classification.reasons().stream())
                    .toList()
            ),
            recentImportJobs,
            recentTransferJobs
        );
    }

    @Transactional(readOnly = true)
    public RecordsActivityTimelineDto getActivityTimeline(Integer days) {
        requireAdmin();
        int effectiveDays = normalizeTimelineDays(days);
        return new RecordsActivityTimelineDto(effectiveDays, buildActivityTimeline(effectiveDays));
    }

    @Transactional(readOnly = true)
    public RecordsActivityHighlightsDto getActivityHighlights(Integer windowDays) {
        requireAdmin();
        int effectiveWindowDays = normalizeHighlightsWindowDays(windowDays);
        List<RecordsActivityPointDto> points = buildActivityTimeline(effectiveWindowDays * 2);
        int splitIndex = Math.max(points.size() - effectiveWindowDays, 0);
        List<RecordsActivityPointDto> previousPoints = points.subList(0, splitIndex);
        List<RecordsActivityPointDto> currentPoints = points.subList(splitIndex, points.size());
        RecordsActivityPeakDto busiestDay = points.stream()
            .max(Comparator
                .comparingLong(RecordsActivityPointDto::totalCount)
                .thenComparing(RecordsActivityPointDto::day))
            .map(point -> new RecordsActivityPeakDto(point.day(), point.totalCount()))
            .orElse(null);

        return new RecordsActivityHighlightsDto(
            effectiveWindowDays,
            summarizeTimelineWindow(currentPoints),
            summarizeTimelineWindow(previousPoints),
            busiestDay
        );
    }

    @Transactional(readOnly = true)
    public ActivityFamilyHighlightsDto getActivityFamilyHighlights(Integer windowDays) {
        requireAdmin();
        int effectiveWindowDays = normalizeHighlightsWindowDays(windowDays);
        LocalDate endDate = LocalDate.now();
        LocalDate currentStartDate = endDate.minusDays(effectiveWindowDays - 1L);
        LocalDate previousEndDate = currentStartDate.minusDays(1);
        LocalDate previousStartDate = previousEndDate.minusDays(effectiveWindowDays - 1L);

        LocalDateTime currentFrom = currentStartDate.atStartOfDay();
        LocalDateTime currentTo = endDate.atTime(23, 59, 59);
        LocalDateTime previousFrom = previousStartDate.atStartOfDay();
        LocalDateTime previousTo = previousEndDate.atTime(23, 59, 59);

        Map<String, ActivityFamilyAccumulator> currentFamilies = aggregateActivityFamilies(currentFrom, currentTo);
        Map<String, ActivityFamilyAccumulator> previousFamilies = aggregateActivityFamilies(previousFrom, previousTo);

        LinkedHashSet<String> familyKeys = new LinkedHashSet<>();
        familyKeys.addAll(currentFamilies.keySet());
        familyKeys.addAll(previousFamilies.keySet());

        List<ActivityFamilyHighlightDto> families = familyKeys.stream()
            .map(family -> {
                ActivityFamilyAccumulator current = currentFamilies.get(family);
                ActivityFamilyAccumulator previous = previousFamilies.get(family);
                long currentCount = current != null ? current.count() : 0;
                long previousCount = previous != null ? previous.count() : 0;
                LocalDateTime lastEventTime = current != null && current.lastEventTime() != null
                    ? current.lastEventTime()
                    : previous != null ? previous.lastEventTime() : null;
                return new ActivityFamilyHighlightDto(
                    family,
                    currentCount,
                    previousCount,
                    currentCount - previousCount,
                    lastEventTime
                );
            })
            .filter(entry -> entry.currentCount() > 0 || entry.previousCount() > 0)
            .sorted(Comparator
                .comparingLong((ActivityFamilyHighlightDto entry) -> Math.max(entry.currentCount(), entry.previousCount())).reversed()
                .thenComparing(Comparator.comparingLong(ActivityFamilyHighlightDto::currentCount).reversed())
                .thenComparingInt(entry -> activityFamilySortRank(entry.family()))
                .thenComparing(ActivityFamilyHighlightDto::family, String.CASE_INSENSITIVE_ORDER))
            .toList();

        return new ActivityFamilyHighlightsDto(
            effectiveWindowDays,
            new ActivityWindowRangeDto(currentStartDate.toString(), endDate.toString()),
            new ActivityWindowRangeDto(previousStartDate.toString(), previousEndDate.toString()),
            families
        );
    }

    @Transactional(readOnly = true)
    public ActivityContributorHighlightsDto getActivityContributorHighlights(Integer windowDays, Integer limit) {
        requireAdmin();
        int effectiveWindowDays = normalizeHighlightsWindowDays(windowDays);
        int effectiveLimit = normalizeContributorHighlightsLimit(limit);
        LocalDate endDate = LocalDate.now();
        LocalDate currentStartDate = endDate.minusDays(effectiveWindowDays - 1L);
        LocalDate previousEndDate = currentStartDate.minusDays(1);
        LocalDate previousStartDate = previousEndDate.minusDays(effectiveWindowDays - 1L);

        LocalDateTime currentFrom = currentStartDate.atStartOfDay();
        LocalDateTime currentTo = endDate.atTime(23, 59, 59);
        LocalDateTime previousFrom = previousStartDate.atStartOfDay();
        LocalDateTime previousTo = previousEndDate.atTime(23, 59, 59);

        Map<String, ActivityContributorReportAccumulator> currentContributors =
            aggregateContributorReportContributors(auditLogRepository.countRmEventsByUsernameAndTypeBetween(currentFrom, currentTo));
        Map<String, ActivityContributorReportAccumulator> previousContributors =
            aggregateContributorReportContributors(auditLogRepository.countRmEventsByUsernameAndTypeBetween(previousFrom, previousTo));

        LinkedHashSet<String> contributorKeys = new LinkedHashSet<>();
        contributorKeys.addAll(currentContributors.keySet());
        contributorKeys.addAll(previousContributors.keySet());

        List<ActivityContributorHighlightDto> contributors = contributorKeys.stream()
            .map(key -> {
                ActivityContributorReportAccumulator current = currentContributors.get(key);
                ActivityContributorReportAccumulator previous = previousContributors.get(key);
                long currentCount = current != null ? current.count() : 0;
                long previousCount = previous != null ? previous.count() : 0;
                LocalDateTime lastEventTime = current != null && current.lastEventTime() != null
                    ? current.lastEventTime()
                    : previous != null ? previous.lastEventTime() : null;
                String username = current != null ? current.username() : previous != null ? previous.username() : null;
                String label = current != null ? current.label() : previous != null ? previous.label() : "(System)";
                return new ActivityContributorHighlightDto(
                    username,
                    label,
                    currentCount,
                    previousCount,
                    currentCount - previousCount,
                    lastEventTime
                );
            })
            .filter(entry -> entry.currentCount() > 0 || entry.previousCount() > 0)
            .sorted(Comparator
                .comparingLong((ActivityContributorHighlightDto entry) -> Math.max(entry.currentCount(), entry.previousCount())).reversed()
                .thenComparing(Comparator.comparingLong(ActivityContributorHighlightDto::currentCount).reversed())
                .thenComparing(ActivityContributorHighlightDto::label, String.CASE_INSENSITIVE_ORDER))
            .limit(effectiveLimit)
            .toList();

        return new ActivityContributorHighlightsDto(
            effectiveWindowDays,
            effectiveLimit,
            new ActivityWindowRangeDto(currentStartDate.toString(), endDate.toString()),
            new ActivityWindowRangeDto(previousStartDate.toString(), previousEndDate.toString()),
            contributors
        );
    }

    @Transactional(readOnly = true)
    public ActivityContributorFamilyHighlightsDto getActivityContributorFamilyHighlights(Integer windowDays, Integer limit) {
        requireAdmin();
        int effectiveWindowDays = normalizeHighlightsWindowDays(windowDays);
        int effectiveLimit = normalizeActivityContributorFamilyReportLimit(limit);
        LocalDate endDate = LocalDate.now();
        LocalDate currentStartDate = endDate.minusDays(effectiveWindowDays - 1L);
        LocalDate previousEndDate = currentStartDate.minusDays(1);
        LocalDate previousStartDate = previousEndDate.minusDays(effectiveWindowDays - 1L);

        LocalDateTime currentFrom = currentStartDate.atStartOfDay();
        LocalDateTime currentTo = endDate.atTime(23, 59, 59);
        LocalDateTime previousFrom = previousStartDate.atStartOfDay();
        LocalDateTime previousTo = previousEndDate.atTime(23, 59, 59);

        List<Object[]> currentRows = auditLogRepository.countRmEventsByUsernameAndTypeBetween(currentFrom, currentTo);
        List<Object[]> previousRows = auditLogRepository.countRmEventsByUsernameAndTypeBetween(previousFrom, previousTo);

        Map<String, ActivityContributorReportAccumulator> currentContributors =
            aggregateContributorReportContributors(currentRows);
        Map<String, ActivityContributorReportAccumulator> previousContributors =
            aggregateContributorReportContributors(previousRows);
        Map<String, Map<String, ActivityContributorFamilyReportFamilyAccumulator>> currentFamilies =
            aggregateContributorFamilyReportFamilies(currentRows);
        Map<String, Map<String, ActivityContributorFamilyReportFamilyAccumulator>> previousFamilies =
            aggregateContributorFamilyReportFamilies(previousRows);

        LinkedHashSet<String> contributorKeys = new LinkedHashSet<>();
        contributorKeys.addAll(currentContributors.keySet());
        contributorKeys.addAll(previousContributors.keySet());

        List<ActivityContributorFamilyHighlightsEntryDto> contributors = contributorKeys.stream()
            .map(key -> {
                ActivityContributorReportAccumulator current = currentContributors.get(key);
                ActivityContributorReportAccumulator previous = previousContributors.get(key);
                long currentCount = current != null ? current.count() : 0;
                long previousCount = previous != null ? previous.count() : 0;
                LocalDateTime lastEventTime = current != null && current.lastEventTime() != null
                    ? current.lastEventTime()
                    : previous != null ? previous.lastEventTime() : null;
                String username = current != null ? current.username() : previous != null ? previous.username() : null;
                String label = current != null ? current.label() : previous != null ? previous.label() : "(System)";
                return new ActivityContributorFamilyHighlightsEntryDto(
                    username,
                    label,
                    currentCount,
                    previousCount,
                    currentCount - previousCount,
                    lastEventTime,
                    mergeContributorFamilyReportFamilies(
                        currentFamilies.getOrDefault(key, Map.of()),
                        previousFamilies.getOrDefault(key, Map.of())
                    )
                );
            })
            .filter(entry -> entry.currentCount() > 0 || entry.previousCount() > 0)
            .sorted(Comparator
                .comparingLong((ActivityContributorFamilyHighlightsEntryDto entry) -> Math.max(entry.currentCount(), entry.previousCount())).reversed()
                .thenComparing(Comparator.comparingLong(ActivityContributorFamilyHighlightsEntryDto::currentCount).reversed())
                .thenComparing(ActivityContributorFamilyHighlightsEntryDto::label, String.CASE_INSENSITIVE_ORDER))
            .limit(effectiveLimit)
            .toList();

        return new ActivityContributorFamilyHighlightsDto(
            effectiveWindowDays,
            effectiveLimit,
            new ActivityWindowRangeDto(currentStartDate.toString(), endDate.toString()),
            new ActivityWindowRangeDto(previousStartDate.toString(), previousEndDate.toString()),
            contributors
        );
    }

    @Transactional(readOnly = true)
    public ActivityFamilyTrendDto getActivityFamilyTrend(Integer days, Integer bucketDays) {
        requireAdmin();
        int effectiveDays = normalizeActivityFamilyTrendDays(days);
        int effectiveBucketDays = normalizeActivityFamilyTrendBucketDays(bucketDays, effectiveDays);
        return new ActivityFamilyTrendDto(
            effectiveDays,
            effectiveBucketDays,
            buildActivityFamilyTrendBuckets(effectiveDays, effectiveBucketDays)
        );
    }

    @Transactional(readOnly = true)
    public ActivityEventTypeReportDto getActivityEventTypeReport(
        LocalDateTime from,
        LocalDateTime to,
        Integer limit
    ) {
        requireAdmin();
        ResolvedActivityComparisonRange range = resolveActivityComparisonRange(
            from,
            to,
            RM_ACTIVITY_EVENT_TYPE_REPORT_DEFAULT_DAYS,
            RM_ACTIVITY_EVENT_TYPE_REPORT_MAX_DAYS
        );
        int effectiveLimit = normalizeActivityEventTypeReportLimit(limit);

        List<ActivityEventTypeDto> currentEventTypes = buildActivityEventTypes(
            range.currentFrom(),
            range.currentTo(),
            effectiveLimit
        );
        List<ActivityEventTypeDto> previousEventTypes = buildActivityEventTypes(
            range.previousFrom(),
            range.previousTo(),
            effectiveLimit
        );

        Map<String, ActivityEventTypeDto> currentByType = currentEventTypes.stream()
            .collect(java.util.stream.Collectors.toMap(
                ActivityEventTypeDto::eventType,
                item -> item,
                (left, right) -> left,
                LinkedHashMap::new
            ));
        Map<String, ActivityEventTypeDto> previousByType = previousEventTypes.stream()
            .collect(java.util.stream.Collectors.toMap(
                ActivityEventTypeDto::eventType,
                item -> item,
                (left, right) -> left,
                LinkedHashMap::new
            ));

        LinkedHashSet<String> trackedEventTypes = new LinkedHashSet<>(currentByType.keySet());
        if (trackedEventTypes.isEmpty()) {
            trackedEventTypes.addAll(previousByType.keySet());
        }

        List<ActivityEventTypeReportEntryDto> eventTypes = trackedEventTypes.stream()
            .map(eventType -> {
                ActivityEventTypeDto currentItem = currentByType.get(eventType);
                ActivityEventTypeDto previousItem = previousByType.get(eventType);
                long currentCount = currentItem != null ? currentItem.count() : 0;
                long previousCount = previousItem != null ? previousItem.count() : 0;
                LocalDateTime lastEventTime = currentItem != null && currentItem.lastEventTime() != null
                    ? currentItem.lastEventTime()
                    : previousItem != null ? previousItem.lastEventTime() : null;
                String family = currentItem != null
                    ? currentItem.family()
                    : previousItem != null ? previousItem.family() : "OTHER";
                return new ActivityEventTypeReportEntryDto(
                    eventType,
                    family,
                    currentCount,
                    previousCount,
                    currentCount - previousCount,
                    lastEventTime
                );
            })
            .sorted(Comparator
                .comparingLong(ActivityEventTypeReportEntryDto::currentCount).reversed()
                .thenComparing(Comparator.comparingLong(ActivityEventTypeReportEntryDto::previousCount).reversed())
                .thenComparingInt(entry -> activityFamilySortRank(entry.family()))
                .thenComparing(ActivityEventTypeReportEntryDto::eventType, String.CASE_INSENSITIVE_ORDER))
            .toList();

        long currentTotalCount = currentByType.values().stream().mapToLong(ActivityEventTypeDto::count).sum();
        long previousTotalCount = previousByType.values().stream().mapToLong(ActivityEventTypeDto::count).sum();

        return new ActivityEventTypeReportDto(
            new ActivityDateTimeRangeDto(range.currentFrom().toString(), range.currentTo().toString()),
            new ActivityDateTimeRangeDto(range.previousFrom().toString(), range.previousTo().toString()),
            effectiveLimit,
            currentTotalCount,
            previousTotalCount,
            eventTypes
        );
    }

    @Transactional(readOnly = true)
    public ActivityContributorReportDto getActivityContributorReport(
        LocalDateTime from,
        LocalDateTime to,
        Integer limit,
        Integer eventTypeLimit
    ) {
        requireAdmin();
        ResolvedActivityComparisonRange range = resolveActivityComparisonRange(
            from,
            to,
            RM_ACTIVITY_CONTRIBUTOR_REPORT_DEFAULT_DAYS,
            RM_ACTIVITY_CONTRIBUTOR_REPORT_MAX_DAYS
        );
        int effectiveLimit = normalizeActivityContributorReportLimit(limit);
        int effectiveEventTypeLimit = normalizeActivityContributorReportEventTypeLimit(eventTypeLimit);

        List<Object[]> currentRows = auditLogRepository.countRmEventsByUsernameAndTypeBetween(
            range.currentFrom(),
            range.currentTo()
        );
        List<Object[]> previousRows = auditLogRepository.countRmEventsByUsernameAndTypeBetween(
            range.previousFrom(),
            range.previousTo()
        );

        Map<String, ActivityContributorReportAccumulator> currentContributors =
            aggregateContributorReportContributors(currentRows);
        Map<String, ActivityContributorReportAccumulator> previousContributors =
            aggregateContributorReportContributors(previousRows);
        Map<String, List<ActivityContributorReportEventTypeDto>> currentTopEventTypes =
            buildActivityContributorReportEventTypes(currentRows, effectiveEventTypeLimit);

        LinkedHashSet<String> contributorKeys = new LinkedHashSet<>();
        contributorKeys.addAll(currentContributors.keySet());
        contributorKeys.addAll(previousContributors.keySet());

        List<ActivityContributorReportEntryDto> contributors = contributorKeys.stream()
            .map(key -> {
                ActivityContributorReportAccumulator current = currentContributors.get(key);
                ActivityContributorReportAccumulator previous = previousContributors.get(key);
                long currentCount = current != null ? current.count() : 0;
                long previousCount = previous != null ? previous.count() : 0;
                LocalDateTime lastEventTime = current != null && current.lastEventTime() != null
                    ? current.lastEventTime()
                    : previous != null ? previous.lastEventTime() : null;
                String username = current != null ? current.username() : previous != null ? previous.username() : null;
                String label = current != null ? current.label() : previous != null ? previous.label() : "(System)";
                return new ActivityContributorReportEntryDto(
                    username,
                    label,
                    currentCount,
                    previousCount,
                    currentCount - previousCount,
                    lastEventTime,
                    currentTopEventTypes.getOrDefault(key, List.of())
                );
            })
            .filter(entry -> entry.currentCount() > 0 || entry.previousCount() > 0)
            .sorted(Comparator
                .comparingLong((ActivityContributorReportEntryDto entry) -> Math.max(entry.currentCount(), entry.previousCount())).reversed()
                .thenComparing(Comparator.comparingLong(ActivityContributorReportEntryDto::currentCount).reversed())
                .thenComparing(ActivityContributorReportEntryDto::label, String.CASE_INSENSITIVE_ORDER))
            .limit(effectiveLimit)
            .toList();

        long currentTotalCount = currentContributors.values().stream().mapToLong(ActivityContributorReportAccumulator::count).sum();
        long previousTotalCount = previousContributors.values().stream().mapToLong(ActivityContributorReportAccumulator::count).sum();

        return new ActivityContributorReportDto(
            new ActivityDateTimeRangeDto(range.currentFrom().toString(), range.currentTo().toString()),
            new ActivityDateTimeRangeDto(range.previousFrom().toString(), range.previousTo().toString()),
            effectiveLimit,
            effectiveEventTypeLimit,
            currentTotalCount,
            previousTotalCount,
            contributors
        );
    }

    @Transactional(readOnly = true)
    public ActivityContributorEventTypeHighlightsDto getActivityContributorEventTypeHighlights(
        Integer windowDays,
        Integer limit,
        Integer eventTypeLimit
    ) {
        requireAdmin();
        int effectiveWindowDays = normalizeHighlightsWindowDays(windowDays);
        int effectiveLimit = normalizeContributorHighlightsLimit(limit);
        int effectiveEventTypeLimit = normalizeActivityContributorReportEventTypeLimit(eventTypeLimit);
        LocalDate endDate = LocalDate.now();
        LocalDate currentStartDate = endDate.minusDays(effectiveWindowDays - 1L);
        LocalDate previousEndDate = currentStartDate.minusDays(1);
        LocalDate previousStartDate = previousEndDate.minusDays(effectiveWindowDays - 1L);

        LocalDateTime currentFrom = currentStartDate.atStartOfDay();
        LocalDateTime currentTo = endDate.atTime(23, 59, 59);
        LocalDateTime previousFrom = previousStartDate.atStartOfDay();
        LocalDateTime previousTo = previousEndDate.atTime(23, 59, 59);

        List<Object[]> currentRows = auditLogRepository.countRmEventsByUsernameAndTypeBetween(currentFrom, currentTo);
        List<Object[]> previousRows = auditLogRepository.countRmEventsByUsernameAndTypeBetween(previousFrom, previousTo);

        Map<String, ActivityContributorReportAccumulator> currentContributors =
            aggregateContributorReportContributors(currentRows);
        Map<String, ActivityContributorReportAccumulator> previousContributors =
            aggregateContributorReportContributors(previousRows);
        Map<String, Map<String, ActivityContributorEventTypeReportEventTypeAccumulator>> currentEventTypes =
            aggregateContributorEventTypeReportEventTypes(currentRows);
        Map<String, Map<String, ActivityContributorEventTypeReportEventTypeAccumulator>> previousEventTypes =
            aggregateContributorEventTypeReportEventTypes(previousRows);

        LinkedHashSet<String> contributorKeys = new LinkedHashSet<>();
        contributorKeys.addAll(currentContributors.keySet());
        contributorKeys.addAll(previousContributors.keySet());

        List<ActivityContributorEventTypeReportEntryDto> contributors = contributorKeys.stream()
            .map(key -> {
                ActivityContributorReportAccumulator current = currentContributors.get(key);
                ActivityContributorReportAccumulator previous = previousContributors.get(key);
                long currentCount = current != null ? current.count() : 0;
                long previousCount = previous != null ? previous.count() : 0;
                LocalDateTime lastEventTime = current != null && current.lastEventTime() != null
                    ? current.lastEventTime()
                    : previous != null ? previous.lastEventTime() : null;
                String username = current != null ? current.username() : previous != null ? previous.username() : null;
                String label = current != null ? current.label() : previous != null ? previous.label() : "(System)";
                return new ActivityContributorEventTypeReportEntryDto(
                    username,
                    label,
                    currentCount,
                    previousCount,
                    currentCount - previousCount,
                    lastEventTime,
                    mergeContributorEventTypeReportEventTypes(
                        currentEventTypes.getOrDefault(key, Map.of()),
                        previousEventTypes.getOrDefault(key, Map.of()),
                        effectiveEventTypeLimit
                    )
                );
            })
            .filter(entry -> entry.currentCount() > 0 || entry.previousCount() > 0)
            .sorted(Comparator
                .comparingLong((ActivityContributorEventTypeReportEntryDto entry) -> Math.max(entry.currentCount(), entry.previousCount())).reversed()
                .thenComparing(Comparator.comparingLong(ActivityContributorEventTypeReportEntryDto::currentCount).reversed())
                .thenComparing(ActivityContributorEventTypeReportEntryDto::label, String.CASE_INSENSITIVE_ORDER))
            .limit(effectiveLimit)
            .toList();

        return new ActivityContributorEventTypeHighlightsDto(
            effectiveWindowDays,
            effectiveLimit,
            effectiveEventTypeLimit,
            new ActivityWindowRangeDto(currentStartDate.toString(), endDate.toString()),
            new ActivityWindowRangeDto(previousStartDate.toString(), previousEndDate.toString()),
            contributors
        );
    }

    @Transactional(readOnly = true)
    public ActivityContributorEventTypeReportDto getActivityContributorEventTypeReport(
        LocalDateTime from,
        LocalDateTime to,
        Integer limit,
        Integer eventTypeLimit
    ) {
        requireAdmin();
        ResolvedActivityComparisonRange range = resolveActivityComparisonRange(
            from,
            to,
            RM_ACTIVITY_CONTRIBUTOR_REPORT_DEFAULT_DAYS,
            RM_ACTIVITY_CONTRIBUTOR_REPORT_MAX_DAYS
        );
        int effectiveLimit = normalizeActivityContributorReportLimit(limit);
        int effectiveEventTypeLimit = normalizeActivityContributorReportEventTypeLimit(eventTypeLimit);

        List<Object[]> currentRows = auditLogRepository.countRmEventsByUsernameAndTypeBetween(
            range.currentFrom(),
            range.currentTo()
        );
        List<Object[]> previousRows = auditLogRepository.countRmEventsByUsernameAndTypeBetween(
            range.previousFrom(),
            range.previousTo()
        );

        Map<String, ActivityContributorReportAccumulator> currentContributors =
            aggregateContributorReportContributors(currentRows);
        Map<String, ActivityContributorReportAccumulator> previousContributors =
            aggregateContributorReportContributors(previousRows);
        Map<String, Map<String, ActivityContributorEventTypeReportEventTypeAccumulator>> currentEventTypes =
            aggregateContributorEventTypeReportEventTypes(currentRows);
        Map<String, Map<String, ActivityContributorEventTypeReportEventTypeAccumulator>> previousEventTypes =
            aggregateContributorEventTypeReportEventTypes(previousRows);

        LinkedHashSet<String> contributorKeys = new LinkedHashSet<>();
        contributorKeys.addAll(currentContributors.keySet());
        contributorKeys.addAll(previousContributors.keySet());

        List<ActivityContributorEventTypeReportEntryDto> contributors = contributorKeys.stream()
            .map(key -> {
                ActivityContributorReportAccumulator current = currentContributors.get(key);
                ActivityContributorReportAccumulator previous = previousContributors.get(key);
                long currentCount = current != null ? current.count() : 0;
                long previousCount = previous != null ? previous.count() : 0;
                LocalDateTime lastEventTime = current != null && current.lastEventTime() != null
                    ? current.lastEventTime()
                    : previous != null ? previous.lastEventTime() : null;
                String username = current != null ? current.username() : previous != null ? previous.username() : null;
                String label = current != null ? current.label() : previous != null ? previous.label() : "(System)";
                return new ActivityContributorEventTypeReportEntryDto(
                    username,
                    label,
                    currentCount,
                    previousCount,
                    currentCount - previousCount,
                    lastEventTime,
                    mergeContributorEventTypeReportEventTypes(
                        currentEventTypes.getOrDefault(key, Map.of()),
                        previousEventTypes.getOrDefault(key, Map.of()),
                        effectiveEventTypeLimit
                    )
                );
            })
            .filter(entry -> entry.currentCount() > 0 || entry.previousCount() > 0)
            .sorted(Comparator
                .comparingLong((ActivityContributorEventTypeReportEntryDto entry) -> Math.max(entry.currentCount(), entry.previousCount())).reversed()
                .thenComparing(Comparator.comparingLong(ActivityContributorEventTypeReportEntryDto::currentCount).reversed())
                .thenComparing(ActivityContributorEventTypeReportEntryDto::label, String.CASE_INSENSITIVE_ORDER))
            .limit(effectiveLimit)
            .toList();

        long currentTotalCount = currentContributors.values().stream().mapToLong(ActivityContributorReportAccumulator::count).sum();
        long previousTotalCount = previousContributors.values().stream().mapToLong(ActivityContributorReportAccumulator::count).sum();

        return new ActivityContributorEventTypeReportDto(
            new ActivityDateTimeRangeDto(range.currentFrom().toString(), range.currentTo().toString()),
            new ActivityDateTimeRangeDto(range.previousFrom().toString(), range.previousTo().toString()),
            effectiveLimit,
            effectiveEventTypeLimit,
            currentTotalCount,
            previousTotalCount,
            contributors
        );
    }

    @Transactional(readOnly = true)
    public ActivityContributorFamilyReportDto getActivityContributorFamilyReport(
        LocalDateTime from,
        LocalDateTime to,
        Integer limit
    ) {
        requireAdmin();
        ResolvedActivityComparisonRange range = resolveActivityComparisonRange(
            from,
            to,
            RM_ACTIVITY_CONTRIBUTOR_FAMILY_REPORT_DEFAULT_DAYS,
            RM_ACTIVITY_CONTRIBUTOR_FAMILY_REPORT_MAX_DAYS
        );
        int effectiveLimit = normalizeActivityContributorFamilyReportLimit(limit);

        List<Object[]> currentRows = auditLogRepository.countRmEventsByUsernameAndTypeBetween(
            range.currentFrom(),
            range.currentTo()
        );
        List<Object[]> previousRows = auditLogRepository.countRmEventsByUsernameAndTypeBetween(
            range.previousFrom(),
            range.previousTo()
        );

        Map<String, ActivityContributorReportAccumulator> currentContributors =
            aggregateContributorReportContributors(currentRows);
        Map<String, ActivityContributorReportAccumulator> previousContributors =
            aggregateContributorReportContributors(previousRows);
        Map<String, Map<String, ActivityContributorFamilyReportFamilyAccumulator>> currentFamilies =
            aggregateContributorFamilyReportFamilies(currentRows);
        Map<String, Map<String, ActivityContributorFamilyReportFamilyAccumulator>> previousFamilies =
            aggregateContributorFamilyReportFamilies(previousRows);

        LinkedHashSet<String> contributorKeys = new LinkedHashSet<>();
        contributorKeys.addAll(currentContributors.keySet());
        contributorKeys.addAll(previousContributors.keySet());

        List<ActivityContributorFamilyReportEntryDto> contributors = contributorKeys.stream()
            .map(key -> {
                ActivityContributorReportAccumulator current = currentContributors.get(key);
                ActivityContributorReportAccumulator previous = previousContributors.get(key);
                long currentCount = current != null ? current.count() : 0;
                long previousCount = previous != null ? previous.count() : 0;
                LocalDateTime lastEventTime = current != null && current.lastEventTime() != null
                    ? current.lastEventTime()
                    : previous != null ? previous.lastEventTime() : null;
                String username = current != null ? current.username() : previous != null ? previous.username() : null;
                String label = current != null ? current.label() : previous != null ? previous.label() : "(System)";
                return new ActivityContributorFamilyReportEntryDto(
                    username,
                    label,
                    currentCount,
                    previousCount,
                    currentCount - previousCount,
                    lastEventTime,
                    mergeContributorFamilyReportFamilies(
                        currentFamilies.getOrDefault(key, Map.of()),
                        previousFamilies.getOrDefault(key, Map.of())
                    )
                );
            })
            .filter(entry -> entry.currentCount() > 0 || entry.previousCount() > 0)
            .sorted(Comparator
                .comparingLong((ActivityContributorFamilyReportEntryDto entry) -> Math.max(entry.currentCount(), entry.previousCount())).reversed()
                .thenComparing(Comparator.comparingLong(ActivityContributorFamilyReportEntryDto::currentCount).reversed())
                .thenComparing(ActivityContributorFamilyReportEntryDto::label, String.CASE_INSENSITIVE_ORDER))
            .limit(effectiveLimit)
            .toList();

        long currentTotalCount = currentContributors.values().stream().mapToLong(ActivityContributorReportAccumulator::count).sum();
        long previousTotalCount = previousContributors.values().stream().mapToLong(ActivityContributorReportAccumulator::count).sum();

        return new ActivityContributorFamilyReportDto(
            new ActivityDateTimeRangeDto(range.currentFrom().toString(), range.currentTo().toString()),
            new ActivityDateTimeRangeDto(range.previousFrom().toString(), range.previousTo().toString()),
            effectiveLimit,
            currentTotalCount,
            previousTotalCount,
            contributors
        );
    }

    @Transactional(readOnly = true)
    public ActivityFamilyReportDto getActivityFamilyReport(
        LocalDateTime from,
        LocalDateTime to,
        Integer eventTypeLimit,
        Integer contributorLimit
    ) {
        requireAdmin();
        ResolvedActivityComparisonRange range = resolveActivityComparisonRange(
            from,
            to,
            RM_ACTIVITY_FAMILY_REPORT_DEFAULT_DAYS,
            RM_ACTIVITY_FAMILY_REPORT_MAX_DAYS
        );
        int effectiveEventTypeLimit = normalizeActivityFamilyReportLimit(
            eventTypeLimit,
            RM_ACTIVITY_FAMILY_REPORT_DEFAULT_EVENT_TYPE_LIMIT
        );
        int effectiveContributorLimit = normalizeActivityFamilyReportLimit(
            contributorLimit,
            RM_ACTIVITY_FAMILY_REPORT_DEFAULT_CONTRIBUTOR_LIMIT
        );

        Map<String, ActivityFamilyAccumulator> currentFamilies =
            aggregateActivityFamilies(range.currentFrom(), range.currentTo());
        Map<String, ActivityFamilyAccumulator> previousFamilies =
            aggregateActivityFamilies(range.previousFrom(), range.previousTo());
        Map<String, List<ActivityFamilyReportEventTypeDto>> topEventTypes =
            buildActivityFamilyReportEventTypes(range.currentFrom(), range.currentTo(), effectiveEventTypeLimit);
        Map<String, List<ActivityFamilyReportContributorDto>> topContributors =
            buildActivityFamilyReportContributors(range.currentFrom(), range.currentTo(), effectiveContributorLimit);

        LinkedHashSet<String> familyKeys = new LinkedHashSet<>();
        familyKeys.addAll(currentFamilies.keySet());
        familyKeys.addAll(previousFamilies.keySet());

        List<ActivityFamilyReportEntryDto> families = familyKeys.stream()
            .map(family -> {
                ActivityFamilyAccumulator current = currentFamilies.get(family);
                ActivityFamilyAccumulator previous = previousFamilies.get(family);
                long currentCount = current != null ? current.count() : 0;
                long previousCount = previous != null ? previous.count() : 0;
                LocalDateTime lastEventTime = current != null && current.lastEventTime() != null
                    ? current.lastEventTime()
                    : previous != null ? previous.lastEventTime() : null;
                return new ActivityFamilyReportEntryDto(
                    family,
                    currentCount,
                    previousCount,
                    currentCount - previousCount,
                    lastEventTime,
                    topEventTypes.getOrDefault(family, List.of()),
                    topContributors.getOrDefault(family, List.of())
                );
            })
            .filter(entry -> entry.currentCount() > 0 || entry.previousCount() > 0)
            .sorted(Comparator
                .comparingLong((ActivityFamilyReportEntryDto entry) -> Math.max(entry.currentCount(), entry.previousCount())).reversed()
                .thenComparing(Comparator.comparingLong(ActivityFamilyReportEntryDto::currentCount).reversed())
                .thenComparingInt(entry -> activityFamilySortRank(entry.family()))
                .thenComparing(ActivityFamilyReportEntryDto::family, String.CASE_INSENSITIVE_ORDER))
            .toList();

        long currentTotalCount = families.stream().mapToLong(ActivityFamilyReportEntryDto::currentCount).sum();
        long previousTotalCount = families.stream().mapToLong(ActivityFamilyReportEntryDto::previousCount).sum();

        return new ActivityFamilyReportDto(
            new ActivityDateTimeRangeDto(range.currentFrom().toString(), range.currentTo().toString()),
            new ActivityDateTimeRangeDto(range.previousFrom().toString(), range.previousTo().toString()),
            effectiveEventTypeLimit,
            effectiveContributorLimit,
            currentTotalCount,
            previousTotalCount,
            families
        );
    }

    @Transactional(readOnly = true)
    public RecordsActivityBreakdownDto getActivityBreakdown(Integer days, Integer bucketDays) {
        requireAdmin();
        int effectiveDays = normalizeBreakdownDays(days);
        int effectiveBucketDays = normalizeBreakdownBucketDays(bucketDays, effectiveDays);
        List<RecordsActivityPointDto> points = buildActivityTimeline(effectiveDays);
        List<RecordsActivityBucketDto> buckets = new ArrayList<>();
        int remainder = points.size() % effectiveBucketDays;
        int start = 0;
        while (start < points.size()) {
            int currentBucketSize = start == 0 && remainder > 0 ? remainder : effectiveBucketDays;
            List<RecordsActivityPointDto> bucketPoints = points.subList(start, Math.min(start + currentBucketSize, points.size()));
            if (bucketPoints.isEmpty()) {
                continue;
            }
            RecordsActivityWindowDto window = summarizeTimelineWindow(bucketPoints);
            String label = Objects.equals(window.fromDay(), window.toDay())
                ? window.fromDay()
                : window.fromDay() + " to " + window.toDay();
            buckets.add(new RecordsActivityBucketDto(
                label,
                window.fromDay(),
                window.toDay(),
                window.activeDayCount(),
                window.declaredCount(),
                window.undeclaredCount(),
                window.categoryAssignedCount(),
                window.governanceChangeCount(),
                window.totalCount()
            ));
            start += currentBucketSize;
        }
        return new RecordsActivityBreakdownDto(effectiveDays, effectiveBucketDays, buckets);
    }

    @Transactional(readOnly = true)
    public ActivityContributorsDto getActivityContributors(Integer days, Integer limit) {
        requireAdmin();
        int effectiveDays = normalizeContributorsDays(days);
        int effectiveLimit = normalizeContributorsLimit(limit);

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(effectiveDays - 1L);
        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to = endDate.atTime(23, 59, 59);

        List<Object[]> rows = auditLogRepository.countRmEventsByUsernameAndTypeBetween(from, to);

        Map<String, ContributorAccumulator> accumulators = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String username = row[0] != null ? row[0].toString().trim() : null;
            String eventType = row[1] != null ? row[1].toString().trim().toUpperCase(Locale.ROOT) : null;
            long count = toLong(row[2]);
            LocalDateTime maxEventTime = row[3] instanceof LocalDateTime ldt ? ldt : null;
            if (eventType == null || count <= 0) {
                continue;
            }

            String key = username != null && !username.isEmpty() ? username : "";
            ContributorAccumulator acc = accumulators.computeIfAbsent(key, k -> new ContributorAccumulator(k));
            classifyContributorEvent(acc, eventType, count);
            if (maxEventTime != null && (acc.lastEventTime == null || maxEventTime.isAfter(acc.lastEventTime))) {
                acc.lastEventTime = maxEventTime;
            }
        }

        List<ActivityContributorDto> contributors = accumulators.values().stream()
            .filter(acc -> acc.totalCount() > 0)
            .sorted(Comparator.comparingLong(ContributorAccumulator::totalCount).reversed()
                .thenComparing(ContributorAccumulator::label, String.CASE_INSENSITIVE_ORDER))
            .limit(effectiveLimit)
            .map(ContributorAccumulator::toDto)
            .toList();

        return new ActivityContributorsDto(effectiveDays, effectiveLimit, contributors);
    }

    @Transactional(readOnly = true)
    public ActivityContributorTrendDto getActivityContributorTrend(Integer days, Integer bucketDays, Integer limit) {
        requireAdmin();
        int effectiveDays = normalizeContributorTrendDays(days);
        int effectiveBucketDays = normalizeContributorTrendBucketDays(bucketDays, effectiveDays);
        int effectiveLimit = normalizeContributorTrendLimit(limit);

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(effectiveDays - 1L);
        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to = endDate.atTime(23, 59, 59);

        List<ActivityContributorTrendContributorDto> topContributors = buildTrackedContributors(from, to, effectiveLimit);
        Set<String> trackedContributorKeys = topContributors.stream()
            .map(ActivityContributorTrendContributorDto::username)
            .map(this::contributorKey)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        List<ActivityContributorTrendBucketDto> buckets = buildActivityContributorTrendBuckets(
            startDate,
            effectiveDays,
            effectiveBucketDays,
            trackedContributorKeys
        );

        return new ActivityContributorTrendDto(
            effectiveDays,
            effectiveBucketDays,
            effectiveLimit,
            topContributors,
            buckets
        );
    }

    @Transactional(readOnly = true)
    public ActivityContributorEventTypeTrendDto getActivityContributorEventTypeTrend(
        Integer days,
        Integer bucketDays,
        Integer limit,
        Integer eventTypeLimit
    ) {
        requireAdmin();
        int effectiveDays = normalizeContributorTrendDays(days);
        int effectiveBucketDays = normalizeContributorTrendBucketDays(bucketDays, effectiveDays);
        int effectiveLimit = normalizeContributorTrendLimit(limit);
        int effectiveEventTypeLimit = normalizeActivityContributorReportEventTypeLimit(eventTypeLimit);

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(effectiveDays - 1L);
        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to = endDate.atTime(23, 59, 59);

        List<ActivityContributorTrendContributorDto> topContributors = buildTrackedContributors(from, to, effectiveLimit);
        Set<String> trackedContributorKeys = topContributors.stream()
            .map(ActivityContributorTrendContributorDto::username)
            .map(this::contributorKey)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        List<ActivityContributorEventTypeTrendBucketDto> buckets = buildActivityContributorEventTypeTrendBuckets(
            startDate,
            effectiveDays,
            effectiveBucketDays,
            trackedContributorKeys,
            effectiveEventTypeLimit
        );

        return new ActivityContributorEventTypeTrendDto(
            effectiveDays,
            effectiveBucketDays,
            effectiveLimit,
            effectiveEventTypeLimit,
            topContributors,
            buckets
        );
    }

    @Transactional(readOnly = true)
    public ActivityContributorFamilyTrendDto getActivityContributorFamilyTrend(
        Integer days,
        Integer bucketDays,
        Integer limit
    ) {
        requireAdmin();
        int effectiveDays = normalizeContributorTrendDays(days);
        int effectiveBucketDays = normalizeContributorTrendBucketDays(bucketDays, effectiveDays);
        int effectiveLimit = normalizeContributorTrendLimit(limit);

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(effectiveDays - 1L);
        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to = endDate.atTime(23, 59, 59);

        List<ActivityContributorTrendContributorDto> topContributors = buildTrackedContributors(from, to, effectiveLimit);
        Set<String> trackedContributorKeys = topContributors.stream()
            .map(ActivityContributorTrendContributorDto::username)
            .map(this::contributorKey)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        List<ActivityContributorFamilyTrendBucketDto> buckets = buildActivityContributorFamilyTrendBuckets(
            startDate,
            effectiveDays,
            effectiveBucketDays,
            trackedContributorKeys
        );

        return new ActivityContributorFamilyTrendDto(
            effectiveDays,
            effectiveBucketDays,
            effectiveLimit,
            topContributors,
            buckets
        );
    }

    @Transactional(readOnly = true)
    public ActivityEventTypeTrendDto getActivityEventTypeTrend(Integer days, Integer bucketDays, Integer limit) {
        requireAdmin();
        int effectiveDays = normalizeEventTypeTrendDays(days);
        int effectiveBucketDays = normalizeEventTypeTrendBucketDays(bucketDays, effectiveDays);
        int effectiveLimit = normalizeEventTypeTrendLimit(limit);

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(effectiveDays - 1L);
        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to = endDate.atTime(23, 59, 59);

        List<ActivityEventTypeDto> topEventTypes = auditLogRepository.countRmEventsByTypeBetween(from, to).stream()
            .map(row -> {
                String eventType = row[0] != null ? row[0].toString().trim().toUpperCase(Locale.ROOT) : null;
                long count = toLong(row[1]);
                LocalDateTime lastEventTime = row[2] instanceof LocalDateTime ldt ? ldt : null;
                if (!StringUtils.hasText(eventType) || count <= 0) {
                    return null;
                }
                return new ActivityEventTypeDto(
                    eventType,
                    classifyActivityFamily(eventType),
                    count,
                    lastEventTime
                );
            })
            .filter(Objects::nonNull)
            .limit(effectiveLimit)
            .toList();

        Set<String> trackedEventTypes = topEventTypes.stream()
            .map(ActivityEventTypeDto::eventType)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        List<ActivityEventTypeTrendBucketDto> buckets = buildActivityEventTypeTrendBuckets(
            startDate,
            effectiveDays,
            effectiveBucketDays,
            trackedEventTypes
        );

        return new ActivityEventTypeTrendDto(
            effectiveDays,
            effectiveBucketDays,
            effectiveLimit,
            topEventTypes,
            buckets
        );
    }

    @Transactional(readOnly = true)
    public ActivityEventTypesDto getActivityEventTypes(Integer days, Integer limit) {
        requireAdmin();
        int effectiveDays = normalizeEventTypesDays(days);
        int effectiveLimit = normalizeEventTypesLimit(limit);

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(effectiveDays - 1L);
        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to = endDate.atTime(23, 59, 59);

        List<ActivityEventTypeDto> eventTypes = buildActivityEventTypes(from, to, effectiveLimit);

        return new ActivityEventTypesDto(effectiveDays, effectiveLimit, eventTypes);
    }

    @Transactional(readOnly = true)
    public ActivityFamiliesDto getActivityFamilies(Integer days) {
        requireAdmin();
        int effectiveDays = normalizeFamiliesDays(days);

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(effectiveDays - 1L);
        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to = endDate.atTime(23, 59, 59);

        Map<String, ActivityFamilyAccumulator> accumulators = aggregateActivityFamilies(from, to);

        List<ActivityFamilyDto> families = accumulators.values().stream()
            .filter(acc -> acc.count() > 0)
            .sorted(Comparator
                .comparingLong(ActivityFamilyAccumulator::count).reversed()
                .thenComparingInt(acc -> activityFamilySortRank(acc.family()))
                .thenComparing(ActivityFamilyAccumulator::family, String.CASE_INSENSITIVE_ORDER))
            .map(ActivityFamilyAccumulator::toDto)
            .toList();

        long totalCount = families.stream()
            .mapToLong(ActivityFamilyDto::count)
            .sum();

        return new ActivityFamiliesDto(effectiveDays, totalCount, families);
    }

    private Map<String, ActivityFamilyAccumulator> aggregateActivityFamilies(LocalDateTime from, LocalDateTime to) {
        Map<String, ActivityFamilyAccumulator> accumulators = new LinkedHashMap<>();
        for (Object[] row : auditLogRepository.countRmEventsByTypeBetween(from, to)) {
            String eventType = row[0] != null ? row[0].toString().trim().toUpperCase(Locale.ROOT) : null;
            long count = toLong(row[1]);
            LocalDateTime lastEventTime = row[2] instanceof LocalDateTime ldt ? ldt : null;
            if (!StringUtils.hasText(eventType) || count <= 0) {
                continue;
            }

            String family = classifyActivityFamily(eventType);
            ActivityFamilyAccumulator acc = accumulators.computeIfAbsent(
                family,
                key -> new ActivityFamilyAccumulator(key)
            );
            acc.count += count;
            if (lastEventTime != null && (acc.lastEventTime == null || lastEventTime.isAfter(acc.lastEventTime))) {
                acc.lastEventTime = lastEventTime;
            }
        }
        return accumulators;
    }

    private List<ActivityFamilyTrendBucketDto> buildActivityFamilyTrendBuckets(int effectiveDays, int effectiveBucketDays) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(effectiveDays - 1L);

        LinkedHashMap<LocalDate, ActivityFamilyDayAccumulator> timeline = new LinkedHashMap<>();
        for (int i = 0; i < effectiveDays; i++) {
            LocalDate day = startDate.plusDays(i);
            timeline.put(day, new ActivityFamilyDayAccumulator(day));
        }

        for (Object[] row : auditLogRepository.countRecordsManagementEventsByDaySince(startDate.atStartOfDay())) {
            LocalDate day = parseAuditDay(row[0]);
            String eventType = row[1] != null ? row[1].toString().trim().toUpperCase(Locale.ROOT) : null;
            long count = toLong(row[2]);
            if (day == null || eventType == null || count <= 0 || !timeline.containsKey(day)) {
                continue;
            }
            timeline.get(day).record(classifyActivityFamily(eventType), count);
        }

        List<ActivityFamilyDayAccumulator> points = new ArrayList<>(timeline.values());
        List<ActivityFamilyTrendBucketDto> buckets = new ArrayList<>();
        int remainder = points.size() % effectiveBucketDays;
        int start = 0;
        while (start < points.size()) {
            int currentBucketSize = start == 0 && remainder > 0 ? remainder : effectiveBucketDays;
            List<ActivityFamilyDayAccumulator> bucketPoints = points.subList(start, Math.min(start + currentBucketSize, points.size()));
            if (bucketPoints.isEmpty()) {
                continue;
            }

            String fromDay = bucketPoints.get(0).day.toString();
            String toDay = bucketPoints.get(bucketPoints.size() - 1).day.toString();
            String label = Objects.equals(fromDay, toDay) ? fromDay : fromDay + " to " + toDay;
            long activeDayCount = bucketPoints.stream().filter(point -> point.totalCount() > 0).count();
            long totalCount = bucketPoints.stream().mapToLong(ActivityFamilyDayAccumulator::totalCount).sum();

            Map<String, Long> familyCounts = new LinkedHashMap<>();
            for (ActivityFamilyDayAccumulator point : bucketPoints) {
                for (Map.Entry<String, Long> entry : point.familyCounts.entrySet()) {
                    familyCounts.merge(entry.getKey(), entry.getValue(), Long::sum);
                }
            }

            List<ActivityFamilyTrendFamilyCountDto> familyBreakdown = familyCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                    .thenComparingInt(entry -> activityFamilySortRank(entry.getKey()))
                    .thenComparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER))
                .map(entry -> new ActivityFamilyTrendFamilyCountDto(entry.getKey(), entry.getValue()))
                .toList();

            buckets.add(new ActivityFamilyTrendBucketDto(
                label,
                fromDay,
                toDay,
                activeDayCount,
                totalCount,
                familyBreakdown
            ));
            start += currentBucketSize;
        }

        return buckets;
    }

    private List<ActivityEventTypeTrendBucketDto> buildActivityEventTypeTrendBuckets(
        LocalDate startDate,
        int effectiveDays,
        int effectiveBucketDays,
        Set<String> trackedEventTypes
    ) {
        LinkedHashMap<LocalDate, ActivityEventTypeDayAccumulator> timeline = new LinkedHashMap<>();
        for (int i = 0; i < effectiveDays; i++) {
            LocalDate day = startDate.plusDays(i);
            timeline.put(day, new ActivityEventTypeDayAccumulator(day));
        }

        for (Object[] row : auditLogRepository.countRecordsManagementEventsByDaySince(startDate.atStartOfDay())) {
            LocalDate day = parseAuditDay(row[0]);
            String eventType = row[1] != null ? row[1].toString().trim().toUpperCase(Locale.ROOT) : null;
            long count = toLong(row[2]);
            if (day == null || !timeline.containsKey(day) || !StringUtils.hasText(eventType) || count <= 0) {
                continue;
            }
            timeline.get(day).record(eventType, classifyActivityFamily(eventType), count, trackedEventTypes.contains(eventType));
        }

        List<ActivityEventTypeDayAccumulator> points = new ArrayList<>(timeline.values());
        List<ActivityEventTypeTrendBucketDto> buckets = new ArrayList<>();
        int remainder = points.size() % effectiveBucketDays;
        int start = 0;
        while (start < points.size()) {
            int currentBucketSize = start == 0 && remainder > 0 ? remainder : effectiveBucketDays;
            List<ActivityEventTypeDayAccumulator> bucketPoints = points.subList(start, Math.min(start + currentBucketSize, points.size()));
            if (bucketPoints.isEmpty()) {
                continue;
            }

            String fromDay = bucketPoints.get(0).day.toString();
            String toDay = bucketPoints.get(bucketPoints.size() - 1).day.toString();
            String label = Objects.equals(fromDay, toDay) ? fromDay : fromDay + " to " + toDay;
            long activeDayCount = bucketPoints.stream().filter(point -> point.totalCount() > 0).count();
            long totalCount = bucketPoints.stream().mapToLong(ActivityEventTypeDayAccumulator::totalCount).sum();
            long trackedCount = bucketPoints.stream().mapToLong(ActivityEventTypeDayAccumulator::trackedCount).sum();

            Map<String, ActivityEventTypeTrendCountAccumulator> eventTypeCounts = new LinkedHashMap<>();
            for (ActivityEventTypeDayAccumulator point : bucketPoints) {
                for (Map.Entry<String, ActivityEventTypeTrendCountAccumulator> entry : point.trackedEventTypeCounts.entrySet()) {
                    ActivityEventTypeTrendCountAccumulator acc = eventTypeCounts.computeIfAbsent(
                        entry.getKey(),
                        key -> new ActivityEventTypeTrendCountAccumulator(entry.getValue().eventType, entry.getValue().family)
                    );
                    acc.count += entry.getValue().count;
                }
            }

            List<ActivityEventTypeTrendCountDto> trackedBreakdown = eventTypeCounts.values().stream()
                .filter(acc -> acc.count > 0)
                .sorted(Comparator
                    .comparingLong(ActivityEventTypeTrendCountAccumulator::count).reversed()
                    .thenComparingInt(acc -> activityFamilySortRank(acc.family))
                    .thenComparing(ActivityEventTypeTrendCountAccumulator::eventType, String.CASE_INSENSITIVE_ORDER))
                .map(ActivityEventTypeTrendCountAccumulator::toDto)
                .toList();

            buckets.add(new ActivityEventTypeTrendBucketDto(
                label,
                fromDay,
                toDay,
                activeDayCount,
                totalCount,
                Math.max(0, totalCount - trackedCount),
                trackedBreakdown
            ));
            start += currentBucketSize;
        }

        return buckets;
    }

    private List<ActivityContributorTrendBucketDto> buildActivityContributorTrendBuckets(
        LocalDate startDate,
        int effectiveDays,
        int effectiveBucketDays,
        Set<String> trackedContributorKeys
    ) {
        LinkedHashMap<LocalDate, ActivityContributorDayAccumulator> timeline = new LinkedHashMap<>();
        for (int i = 0; i < effectiveDays; i++) {
            LocalDate day = startDate.plusDays(i);
            timeline.put(day, new ActivityContributorDayAccumulator(day));
        }

        for (Object[] row : auditLogRepository.countRmEventsByDayUsernameAndTypeSince(startDate.atStartOfDay())) {
            LocalDate day = parseAuditDay(row[0]);
            String username = row[1] != null ? row[1].toString().trim() : null;
            String eventType = row[2] != null ? row[2].toString().trim().toUpperCase(Locale.ROOT) : null;
            long count = toLong(row[3]);
            if (day == null || !timeline.containsKey(day) || !StringUtils.hasText(eventType) || count <= 0) {
                continue;
            }
            String key = contributorKey(username);
            timeline.get(day).record(key, contributorLabel(username), count, trackedContributorKeys.contains(key));
        }

        List<ActivityContributorDayAccumulator> points = new ArrayList<>(timeline.values());
        List<ActivityContributorTrendBucketDto> buckets = new ArrayList<>();
        int remainder = points.size() % effectiveBucketDays;
        int start = 0;
        while (start < points.size()) {
            int currentBucketSize = start == 0 && remainder > 0 ? remainder : effectiveBucketDays;
            List<ActivityContributorDayAccumulator> bucketPoints = points.subList(start, Math.min(start + currentBucketSize, points.size()));
            if (bucketPoints.isEmpty()) {
                continue;
            }

            String fromDay = bucketPoints.get(0).day.toString();
            String toDay = bucketPoints.get(bucketPoints.size() - 1).day.toString();
            String label = Objects.equals(fromDay, toDay) ? fromDay : fromDay + " to " + toDay;
            long activeDayCount = bucketPoints.stream().filter(point -> point.totalCount() > 0).count();
            long totalCount = bucketPoints.stream().mapToLong(ActivityContributorDayAccumulator::totalCount).sum();
            long trackedCount = bucketPoints.stream().mapToLong(ActivityContributorDayAccumulator::trackedCount).sum();

            Map<String, ActivityContributorTrendCountAccumulator> contributorCounts = new LinkedHashMap<>();
            for (ActivityContributorDayAccumulator point : bucketPoints) {
                for (Map.Entry<String, ActivityContributorTrendCountAccumulator> entry : point.trackedContributorCounts.entrySet()) {
                    ActivityContributorTrendCountAccumulator acc = contributorCounts.computeIfAbsent(
                        entry.getKey(),
                        key -> new ActivityContributorTrendCountAccumulator(
                            entry.getValue().username,
                            entry.getValue().label
                        )
                    );
                    acc.count += entry.getValue().count;
                }
            }

            List<ActivityContributorTrendCountDto> trackedBreakdown = contributorCounts.values().stream()
                .filter(acc -> acc.count > 0)
                .sorted(Comparator
                    .comparingLong(ActivityContributorTrendCountAccumulator::count).reversed()
                    .thenComparing(ActivityContributorTrendCountAccumulator::label, String.CASE_INSENSITIVE_ORDER))
                .map(ActivityContributorTrendCountAccumulator::toDto)
                .toList();

            buckets.add(new ActivityContributorTrendBucketDto(
                label,
                fromDay,
                toDay,
                activeDayCount,
                totalCount,
                Math.max(0, totalCount - trackedCount),
                trackedBreakdown
            ));
            start += currentBucketSize;
        }

        return buckets;
    }

    private List<ActivityContributorEventTypeTrendBucketDto> buildActivityContributorEventTypeTrendBuckets(
        LocalDate startDate,
        int effectiveDays,
        int effectiveBucketDays,
        Set<String> trackedContributorKeys,
        int eventTypeLimit
    ) {
        LinkedHashMap<LocalDate, ActivityContributorEventTypeTrendDayAccumulator> timeline = new LinkedHashMap<>();
        for (int i = 0; i < effectiveDays; i++) {
            LocalDate day = startDate.plusDays(i);
            timeline.put(day, new ActivityContributorEventTypeTrendDayAccumulator(day));
        }

        for (Object[] row : auditLogRepository.countRmEventsByDayUsernameAndTypeSince(startDate.atStartOfDay())) {
            LocalDate day = parseAuditDay(row[0]);
            String username = row[1] != null ? row[1].toString().trim() : null;
            String eventType = row[2] != null ? row[2].toString().trim().toUpperCase(Locale.ROOT) : null;
            long count = toLong(row[3]);
            if (day == null || !timeline.containsKey(day) || !StringUtils.hasText(eventType) || count <= 0) {
                continue;
            }
            String key = contributorKey(username);
            timeline.get(day).record(
                key,
                contributorLabel(username),
                eventType,
                classifyActivityFamily(eventType),
                count,
                trackedContributorKeys.contains(key)
            );
        }

        List<ActivityContributorEventTypeTrendDayAccumulator> points = new ArrayList<>(timeline.values());
        List<ActivityContributorEventTypeTrendBucketDto> buckets = new ArrayList<>();
        int remainder = points.size() % effectiveBucketDays;
        int start = 0;
        while (start < points.size()) {
            int currentBucketSize = start == 0 && remainder > 0 ? remainder : effectiveBucketDays;
            List<ActivityContributorEventTypeTrendDayAccumulator> bucketPoints = points.subList(start, Math.min(start + currentBucketSize, points.size()));
            if (bucketPoints.isEmpty()) {
                continue;
            }

            String fromDay = bucketPoints.get(0).day.toString();
            String toDay = bucketPoints.get(bucketPoints.size() - 1).day.toString();
            String label = Objects.equals(fromDay, toDay) ? fromDay : fromDay + " to " + toDay;
            long activeDayCount = bucketPoints.stream().filter(point -> point.totalCount() > 0).count();
            long totalCount = bucketPoints.stream().mapToLong(ActivityContributorEventTypeTrendDayAccumulator::totalCount).sum();
            long trackedCount = bucketPoints.stream().mapToLong(ActivityContributorEventTypeTrendDayAccumulator::trackedCount).sum();

            Map<String, ActivityContributorEventTypeTrendContributorAccumulator> contributorCounts = new LinkedHashMap<>();
            for (ActivityContributorEventTypeTrendDayAccumulator point : bucketPoints) {
                for (Map.Entry<String, ActivityContributorEventTypeTrendContributorAccumulator> entry : point.trackedContributorCounts.entrySet()) {
                    ActivityContributorEventTypeTrendContributorAccumulator acc = contributorCounts.computeIfAbsent(
                        entry.getKey(),
                        key -> new ActivityContributorEventTypeTrendContributorAccumulator(
                            entry.getValue().username,
                            entry.getValue().label
                        )
                    );
                    acc.count += entry.getValue().count;
                    mergeContributorEventTypeTrendEventTypes(acc.eventTypes, entry.getValue().eventTypes);
                }
            }

            List<ActivityContributorEventTypeTrendContributorDto> trackedBreakdown = contributorCounts.values().stream()
                .filter(acc -> acc.count > 0)
                .sorted(Comparator
                    .comparingLong(ActivityContributorEventTypeTrendContributorAccumulator::count).reversed()
                    .thenComparing(ActivityContributorEventTypeTrendContributorAccumulator::label, String.CASE_INSENSITIVE_ORDER))
                .map(acc -> acc.toDto(eventTypeLimit))
                .toList();

            buckets.add(new ActivityContributorEventTypeTrendBucketDto(
                label,
                fromDay,
                toDay,
                activeDayCount,
                totalCount,
                Math.max(0, totalCount - trackedCount),
                trackedBreakdown
            ));
            start += currentBucketSize;
        }

        return buckets;
    }

    private List<ActivityContributorFamilyTrendBucketDto> buildActivityContributorFamilyTrendBuckets(
        LocalDate startDate,
        int effectiveDays,
        int effectiveBucketDays,
        Set<String> trackedContributorKeys
    ) {
        LinkedHashMap<LocalDate, ActivityContributorFamilyTrendDayAccumulator> timeline = new LinkedHashMap<>();
        for (int i = 0; i < effectiveDays; i++) {
            LocalDate day = startDate.plusDays(i);
            timeline.put(day, new ActivityContributorFamilyTrendDayAccumulator(day));
        }

        for (Object[] row : auditLogRepository.countRmEventsByDayUsernameAndTypeSince(startDate.atStartOfDay())) {
            LocalDate day = parseAuditDay(row[0]);
            String username = row[1] != null ? row[1].toString().trim() : null;
            String eventType = row[2] != null ? row[2].toString().trim().toUpperCase(Locale.ROOT) : null;
            long count = toLong(row[3]);
            if (day == null || !timeline.containsKey(day) || !StringUtils.hasText(eventType) || count <= 0) {
                continue;
            }
            String key = contributorKey(username);
            timeline.get(day).record(
                key,
                contributorLabel(username),
                classifyActivityFamily(eventType),
                count,
                trackedContributorKeys.contains(key)
            );
        }

        List<ActivityContributorFamilyTrendDayAccumulator> points = new ArrayList<>(timeline.values());
        List<ActivityContributorFamilyTrendBucketDto> buckets = new ArrayList<>();
        int remainder = points.size() % effectiveBucketDays;
        int start = 0;
        while (start < points.size()) {
            int currentBucketSize = start == 0 && remainder > 0 ? remainder : effectiveBucketDays;
            List<ActivityContributorFamilyTrendDayAccumulator> bucketPoints = points.subList(start, Math.min(start + currentBucketSize, points.size()));
            if (bucketPoints.isEmpty()) {
                continue;
            }

            String fromDay = bucketPoints.get(0).day.toString();
            String toDay = bucketPoints.get(bucketPoints.size() - 1).day.toString();
            String label = Objects.equals(fromDay, toDay) ? fromDay : fromDay + " to " + toDay;
            long activeDayCount = bucketPoints.stream().filter(point -> point.totalCount() > 0).count();
            long totalCount = bucketPoints.stream().mapToLong(ActivityContributorFamilyTrendDayAccumulator::totalCount).sum();
            long trackedCount = bucketPoints.stream().mapToLong(ActivityContributorFamilyTrendDayAccumulator::trackedCount).sum();

            Map<String, ActivityContributorFamilyTrendContributorAccumulator> contributorCounts = new LinkedHashMap<>();
            for (ActivityContributorFamilyTrendDayAccumulator point : bucketPoints) {
                for (Map.Entry<String, ActivityContributorFamilyTrendContributorAccumulator> entry : point.trackedContributorCounts.entrySet()) {
                    ActivityContributorFamilyTrendContributorAccumulator acc = contributorCounts.computeIfAbsent(
                        entry.getKey(),
                        key -> new ActivityContributorFamilyTrendContributorAccumulator(
                            entry.getValue().username,
                            entry.getValue().label
                        )
                    );
                    acc.count += entry.getValue().count;
                    mergeContributorFamilyTrendFamilies(acc.families, entry.getValue().families);
                }
            }

            List<ActivityContributorFamilyTrendContributorDto> trackedBreakdown = contributorCounts.values().stream()
                .filter(acc -> acc.count > 0)
                .sorted(Comparator
                    .comparingLong(ActivityContributorFamilyTrendContributorAccumulator::count).reversed()
                    .thenComparing(ActivityContributorFamilyTrendContributorAccumulator::label, String.CASE_INSENSITIVE_ORDER))
                .map(ActivityContributorFamilyTrendContributorAccumulator::toDto)
                .toList();

            buckets.add(new ActivityContributorFamilyTrendBucketDto(
                label,
                fromDay,
                toDay,
                activeDayCount,
                totalCount,
                Math.max(0, totalCount - trackedCount),
                trackedBreakdown
            ));
            start += currentBucketSize;
        }

        return buckets;
    }

    private Map<String, List<ActivityFamilyReportEventTypeDto>> buildActivityFamilyReportEventTypes(
        LocalDateTime from,
        LocalDateTime to,
        int limit
    ) {
        Map<String, List<ActivityFamilyReportEventTypeDto>> grouped = new LinkedHashMap<>();
        for (Object[] row : auditLogRepository.countRmEventsByTypeBetween(from, to)) {
            String eventType = row[0] != null ? row[0].toString().trim().toUpperCase(Locale.ROOT) : null;
            long count = toLong(row[1]);
            LocalDateTime lastEventTime = row[2] instanceof LocalDateTime ldt ? ldt : null;
            if (!StringUtils.hasText(eventType) || count <= 0) {
                continue;
            }

            String family = classifyActivityFamily(eventType);
            grouped.computeIfAbsent(family, key -> new ArrayList<>())
                .add(new ActivityFamilyReportEventTypeDto(eventType, count, lastEventTime));
        }

        Map<String, List<ActivityFamilyReportEventTypeDto>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<ActivityFamilyReportEventTypeDto>> entry : grouped.entrySet()) {
            result.put(
                entry.getKey(),
                entry.getValue().stream()
                    .sorted(Comparator
                        .comparingLong(ActivityFamilyReportEventTypeDto::count).reversed()
                        .thenComparing(ActivityFamilyReportEventTypeDto::eventType, String.CASE_INSENSITIVE_ORDER))
                    .limit(limit)
                    .toList()
            );
        }
        return result;
    }

    private Map<String, List<ActivityFamilyReportContributorDto>> buildActivityFamilyReportContributors(
        LocalDateTime from,
        LocalDateTime to,
        int limit
    ) {
        Map<String, Map<String, ActivityFamilyReportContributorAccumulator>> grouped = new LinkedHashMap<>();
        for (Object[] row : auditLogRepository.countRmEventsByUsernameAndTypeBetween(from, to)) {
            String username = row[0] != null ? row[0].toString().trim() : null;
            String eventType = row[1] != null ? row[1].toString().trim().toUpperCase(Locale.ROOT) : null;
            long count = toLong(row[2]);
            LocalDateTime lastEventTime = row[3] instanceof LocalDateTime ldt ? ldt : null;
            if (!StringUtils.hasText(eventType) || count <= 0) {
                continue;
            }

            String family = classifyActivityFamily(eventType);
            String key = StringUtils.hasText(username) ? username : "";
            ActivityFamilyReportContributorAccumulator acc = grouped
                .computeIfAbsent(family, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(key, ignored -> new ActivityFamilyReportContributorAccumulator(username));
            acc.count += count;
            if (lastEventTime != null && (acc.lastEventTime == null || lastEventTime.isAfter(acc.lastEventTime))) {
                acc.lastEventTime = lastEventTime;
            }
        }

        Map<String, List<ActivityFamilyReportContributorDto>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, ActivityFamilyReportContributorAccumulator>> entry : grouped.entrySet()) {
            result.put(
                entry.getKey(),
                entry.getValue().values().stream()
                    .filter(acc -> acc.count > 0)
                    .sorted(Comparator
                        .comparingLong(ActivityFamilyReportContributorAccumulator::count).reversed()
                        .thenComparing(ActivityFamilyReportContributorAccumulator::label, String.CASE_INSENSITIVE_ORDER))
                    .limit(limit)
                    .map(ActivityFamilyReportContributorAccumulator::toDto)
                    .toList()
            );
        }
        return result;
    }

    private List<ActivityContributorTrendContributorDto> buildTrackedContributors(
        LocalDateTime from,
        LocalDateTime to,
        int limit
    ) {
        return aggregateContributorReportContributors(auditLogRepository.countRmEventsByUsernameAndTypeBetween(from, to))
            .values().stream()
            .filter(acc -> acc.count() > 0)
            .sorted(Comparator
                .comparingLong(ActivityContributorReportAccumulator::count).reversed()
                .thenComparing(ActivityContributorReportAccumulator::label, String.CASE_INSENSITIVE_ORDER))
            .limit(limit)
            .map(acc -> new ActivityContributorTrendContributorDto(
                acc.username(),
                acc.label(),
                acc.count(),
                acc.lastEventTime()
            ))
            .toList();
    }

    private Map<String, ActivityContributorReportAccumulator> aggregateContributorReportContributors(List<Object[]> rows) {
        Map<String, ActivityContributorReportAccumulator> contributors = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String username = row[0] != null ? row[0].toString().trim() : null;
            String eventType = row[1] != null ? row[1].toString().trim().toUpperCase(Locale.ROOT) : null;
            long count = toLong(row[2]);
            LocalDateTime lastEventTime = row[3] instanceof LocalDateTime ldt ? ldt : null;
            if (!StringUtils.hasText(eventType) || count <= 0) {
                continue;
            }

            String key = contributorKey(username);
            ActivityContributorReportAccumulator acc = contributors.computeIfAbsent(
                key,
                ignored -> new ActivityContributorReportAccumulator(username)
            );
            acc.count += count;
            if (lastEventTime != null && (acc.lastEventTime == null || lastEventTime.isAfter(acc.lastEventTime))) {
                acc.lastEventTime = lastEventTime;
            }
        }
        return contributors;
    }

    private Map<String, List<ActivityContributorReportEventTypeDto>> buildActivityContributorReportEventTypes(
        List<Object[]> rows,
        int limit
    ) {
        Map<String, Map<String, ActivityContributorReportEventTypeAccumulator>> grouped = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String username = row[0] != null ? row[0].toString().trim() : null;
            String eventType = row[1] != null ? row[1].toString().trim().toUpperCase(Locale.ROOT) : null;
            long count = toLong(row[2]);
            LocalDateTime lastEventTime = row[3] instanceof LocalDateTime ldt ? ldt : null;
            if (!StringUtils.hasText(eventType) || count <= 0) {
                continue;
            }

            String key = contributorKey(username);
            ActivityContributorReportEventTypeAccumulator acc = grouped
                .computeIfAbsent(key, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(
                    eventType,
                    ignored -> new ActivityContributorReportEventTypeAccumulator(
                        eventType,
                        classifyActivityFamily(eventType)
                    )
                );
            acc.count += count;
            if (lastEventTime != null && (acc.lastEventTime == null || lastEventTime.isAfter(acc.lastEventTime))) {
                acc.lastEventTime = lastEventTime;
            }
        }

        Map<String, List<ActivityContributorReportEventTypeDto>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, ActivityContributorReportEventTypeAccumulator>> entry : grouped.entrySet()) {
            result.put(
                entry.getKey(),
                entry.getValue().values().stream()
                    .filter(acc -> acc.count > 0)
                    .sorted(Comparator
                        .comparingLong(ActivityContributorReportEventTypeAccumulator::count).reversed()
                        .thenComparingInt(acc -> activityFamilySortRank(acc.family()))
                        .thenComparing(ActivityContributorReportEventTypeAccumulator::eventType, String.CASE_INSENSITIVE_ORDER))
                    .limit(limit)
                    .map(ActivityContributorReportEventTypeAccumulator::toDto)
                    .toList()
            );
        }
        return result;
    }

    private Map<String, Map<String, ActivityContributorEventTypeReportEventTypeAccumulator>> aggregateContributorEventTypeReportEventTypes(
        List<Object[]> rows
    ) {
        Map<String, Map<String, ActivityContributorEventTypeReportEventTypeAccumulator>> grouped = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String username = row[0] != null ? row[0].toString().trim() : null;
            String eventType = row[1] != null ? row[1].toString().trim().toUpperCase(Locale.ROOT) : null;
            long count = toLong(row[2]);
            LocalDateTime lastEventTime = row[3] instanceof LocalDateTime ldt ? ldt : null;
            if (!StringUtils.hasText(eventType) || count <= 0) {
                continue;
            }

            String key = contributorKey(username);
            ActivityContributorEventTypeReportEventTypeAccumulator acc = grouped
                .computeIfAbsent(key, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(
                    eventType,
                    ignored -> new ActivityContributorEventTypeReportEventTypeAccumulator(
                        eventType,
                        classifyActivityFamily(eventType)
                    )
                );
            acc.count += count;
            if (lastEventTime != null && (acc.lastEventTime == null || lastEventTime.isAfter(acc.lastEventTime))) {
                acc.lastEventTime = lastEventTime;
            }
        }
        return grouped;
    }

    private List<ActivityContributorEventTypeReportEventTypeDto> mergeContributorEventTypeReportEventTypes(
        Map<String, ActivityContributorEventTypeReportEventTypeAccumulator> currentEventTypes,
        Map<String, ActivityContributorEventTypeReportEventTypeAccumulator> previousEventTypes,
        int limit
    ) {
        LinkedHashSet<String> eventTypeKeys = new LinkedHashSet<>();
        eventTypeKeys.addAll(currentEventTypes.keySet());
        eventTypeKeys.addAll(previousEventTypes.keySet());

        return eventTypeKeys.stream()
            .map(eventType -> {
                ActivityContributorEventTypeReportEventTypeAccumulator current = currentEventTypes.get(eventType);
                ActivityContributorEventTypeReportEventTypeAccumulator previous = previousEventTypes.get(eventType);
                long currentCount = current != null ? current.count() : 0;
                long previousCount = previous != null ? previous.count() : 0;
                String family = current != null ? current.family() : previous != null ? previous.family() : "OTHER";
                LocalDateTime lastEventTime = current != null && current.lastEventTime() != null
                    ? current.lastEventTime()
                    : previous != null ? previous.lastEventTime() : null;
                return new ActivityContributorEventTypeReportEventTypeDto(
                    eventType,
                    family,
                    currentCount,
                    previousCount,
                    currentCount - previousCount,
                    lastEventTime
                );
            })
            .filter(entry -> entry.currentCount() > 0 || entry.previousCount() > 0)
            .sorted(Comparator
                .comparingLong((ActivityContributorEventTypeReportEventTypeDto entry) -> Math.max(entry.currentCount(), entry.previousCount())).reversed()
                .thenComparing(Comparator.comparingLong(ActivityContributorEventTypeReportEventTypeDto::currentCount).reversed())
                .thenComparingInt(entry -> activityFamilySortRank(entry.family()))
                .thenComparing(ActivityContributorEventTypeReportEventTypeDto::eventType, String.CASE_INSENSITIVE_ORDER))
            .limit(limit)
            .toList();
    }

    private void mergeContributorEventTypeTrendEventTypes(
        Map<String, ActivityContributorEventTypeTrendEventTypeAccumulator> target,
        Map<String, ActivityContributorEventTypeTrendEventTypeAccumulator> source
    ) {
        for (Map.Entry<String, ActivityContributorEventTypeTrendEventTypeAccumulator> entry : source.entrySet()) {
            ActivityContributorEventTypeTrendEventTypeAccumulator sourceAcc = entry.getValue();
            ActivityContributorEventTypeTrendEventTypeAccumulator targetAcc = target.computeIfAbsent(
                entry.getKey(),
                key -> new ActivityContributorEventTypeTrendEventTypeAccumulator(
                    sourceAcc.eventType(),
                    sourceAcc.family()
                )
            );
            targetAcc.count += sourceAcc.count();
        }
    }

    private void mergeContributorFamilyTrendFamilies(
        Map<String, ActivityContributorFamilyTrendFamilyAccumulator> target,
        Map<String, ActivityContributorFamilyTrendFamilyAccumulator> source
    ) {
        for (Map.Entry<String, ActivityContributorFamilyTrendFamilyAccumulator> entry : source.entrySet()) {
            ActivityContributorFamilyTrendFamilyAccumulator sourceAcc = entry.getValue();
            ActivityContributorFamilyTrendFamilyAccumulator targetAcc = target.computeIfAbsent(
                entry.getKey(),
                key -> new ActivityContributorFamilyTrendFamilyAccumulator(sourceAcc.family())
            );
            targetAcc.count += sourceAcc.count();
        }
    }

    private Map<String, Map<String, ActivityContributorFamilyReportFamilyAccumulator>> aggregateContributorFamilyReportFamilies(
        List<Object[]> rows
    ) {
        Map<String, Map<String, ActivityContributorFamilyReportFamilyAccumulator>> grouped = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String username = row[0] != null ? row[0].toString().trim() : null;
            String eventType = row[1] != null ? row[1].toString().trim().toUpperCase(Locale.ROOT) : null;
            long count = toLong(row[2]);
            LocalDateTime lastEventTime = row[3] instanceof LocalDateTime ldt ? ldt : null;
            if (!StringUtils.hasText(eventType) || count <= 0) {
                continue;
            }

            String key = contributorKey(username);
            String family = classifyActivityFamily(eventType);
            ActivityContributorFamilyReportFamilyAccumulator acc = grouped
                .computeIfAbsent(key, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(family, ignored -> new ActivityContributorFamilyReportFamilyAccumulator(family));
            acc.count += count;
            if (lastEventTime != null && (acc.lastEventTime == null || lastEventTime.isAfter(acc.lastEventTime))) {
                acc.lastEventTime = lastEventTime;
            }
        }
        return grouped;
    }

    private List<ActivityContributorFamilyReportFamilyDto> mergeContributorFamilyReportFamilies(
        Map<String, ActivityContributorFamilyReportFamilyAccumulator> currentFamilies,
        Map<String, ActivityContributorFamilyReportFamilyAccumulator> previousFamilies
    ) {
        LinkedHashSet<String> familyKeys = new LinkedHashSet<>();
        familyKeys.addAll(currentFamilies.keySet());
        familyKeys.addAll(previousFamilies.keySet());

        return familyKeys.stream()
            .map(family -> {
                ActivityContributorFamilyReportFamilyAccumulator current = currentFamilies.get(family);
                ActivityContributorFamilyReportFamilyAccumulator previous = previousFamilies.get(family);
                long currentCount = current != null ? current.count() : 0;
                long previousCount = previous != null ? previous.count() : 0;
                LocalDateTime lastEventTime = current != null && current.lastEventTime() != null
                    ? current.lastEventTime()
                    : previous != null ? previous.lastEventTime() : null;
                return new ActivityContributorFamilyReportFamilyDto(
                    family,
                    currentCount,
                    previousCount,
                    currentCount - previousCount,
                    lastEventTime
                );
            })
            .filter(entry -> entry.currentCount() > 0 || entry.previousCount() > 0)
            .sorted(Comparator
                .comparingLong((ActivityContributorFamilyReportFamilyDto entry) -> Math.max(entry.currentCount(), entry.previousCount())).reversed()
                .thenComparing(Comparator.comparingLong(ActivityContributorFamilyReportFamilyDto::currentCount).reversed())
                .thenComparingInt(entry -> activityFamilySortRank(entry.family()))
                .thenComparing(ActivityContributorFamilyReportFamilyDto::family, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private List<ActivityEventTypeDto> buildActivityEventTypes(LocalDateTime from, LocalDateTime to, int limit) {
        return auditLogRepository.countRmEventsByTypeBetween(from, to).stream()
            .map(row -> {
                String eventType = row[0] != null ? row[0].toString().trim().toUpperCase(Locale.ROOT) : null;
                long count = toLong(row[1]);
                LocalDateTime lastEventTime = row[2] instanceof LocalDateTime ldt ? ldt : null;
                if (!StringUtils.hasText(eventType) || count <= 0) {
                    return null;
                }
                return new ActivityEventTypeDto(
                    eventType,
                    classifyActivityFamily(eventType),
                    count,
                    lastEventTime
                );
            })
            .filter(Objects::nonNull)
            .limit(limit)
            .toList();
    }

    private ResolvedActivityComparisonRange resolveActivityComparisonRange(
        LocalDateTime from,
        LocalDateTime to,
        int defaultDays,
        int maxDays
    ) {
        LocalDateTime currentFrom = from;
        LocalDateTime currentTo = to;
        if (currentFrom == null && currentTo == null) {
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(defaultDays - 1L);
            currentFrom = startDate.atStartOfDay();
            currentTo = endDate.atTime(23, 59, 59);
        } else if (currentFrom == null || currentTo == null) {
            throw new IllegalArgumentException("Both from and to are required when specifying a custom range");
        }

        if (currentFrom.isAfter(currentTo)) {
            throw new IllegalArgumentException("from must be before or equal to to");
        }

        long daySpan = ChronoUnit.DAYS.between(currentFrom.toLocalDate(), currentTo.toLocalDate()) + 1L;
        if (daySpan > maxDays) {
            throw new IllegalArgumentException("Range exceeds maximum of " + maxDays + " days");
        }

        long spanSeconds = ChronoUnit.SECONDS.between(currentFrom, currentTo);
        LocalDateTime previousTo = currentFrom.minusSeconds(1);
        LocalDateTime previousFrom = previousTo.minusSeconds(spanSeconds);
        return new ResolvedActivityComparisonRange(currentFrom, currentTo, previousFrom, previousTo);
    }

    private int normalizeActivityFamilyReportLimit(Integer limit, int defaultValue) {
        if (limit == null) {
            return defaultValue;
        }
        return Math.max(RM_ACTIVITY_FAMILY_REPORT_MIN_LIMIT, Math.min(limit, RM_ACTIVITY_FAMILY_REPORT_MAX_LIMIT));
    }

    private int normalizeActivityEventTypeReportLimit(Integer limit) {
        if (limit == null) {
            return RM_ACTIVITY_EVENT_TYPE_REPORT_DEFAULT_LIMIT;
        }
        return Math.max(RM_ACTIVITY_EVENT_TYPE_REPORT_MIN_LIMIT, Math.min(limit, RM_ACTIVITY_EVENT_TYPE_REPORT_MAX_LIMIT));
    }

    private int normalizeActivityContributorReportLimit(Integer limit) {
        if (limit == null) {
            return RM_ACTIVITY_CONTRIBUTOR_REPORT_DEFAULT_LIMIT;
        }
        return Math.max(RM_ACTIVITY_CONTRIBUTOR_REPORT_MIN_LIMIT, Math.min(limit, RM_ACTIVITY_CONTRIBUTOR_REPORT_MAX_LIMIT));
    }

    private int normalizeContributorHighlightsLimit(Integer limit) {
        if (limit == null) {
            return RM_ACTIVITY_CONTRIBUTORS_DEFAULT_LIMIT;
        }
        return Math.max(RM_ACTIVITY_CONTRIBUTORS_MIN_LIMIT, Math.min(limit, RM_ACTIVITY_CONTRIBUTORS_MAX_LIMIT));
    }

    private int normalizeActivityContributorReportEventTypeLimit(Integer limit) {
        if (limit == null) {
            return RM_ACTIVITY_CONTRIBUTOR_REPORT_DEFAULT_EVENT_TYPE_LIMIT;
        }
        return Math.max(
            RM_ACTIVITY_CONTRIBUTOR_REPORT_MIN_EVENT_TYPE_LIMIT,
            Math.min(limit, RM_ACTIVITY_CONTRIBUTOR_REPORT_MAX_EVENT_TYPE_LIMIT)
        );
    }

    private int normalizeActivityContributorFamilyReportLimit(Integer limit) {
        if (limit == null) {
            return RM_ACTIVITY_CONTRIBUTOR_FAMILY_REPORT_DEFAULT_LIMIT;
        }
        return Math.max(
            RM_ACTIVITY_CONTRIBUTOR_FAMILY_REPORT_MIN_LIMIT,
            Math.min(limit, RM_ACTIVITY_CONTRIBUTOR_FAMILY_REPORT_MAX_LIMIT)
        );
    }

    private void classifyContributorEvent(ContributorAccumulator acc, String eventType, long count) {
        switch (eventType) {
            case "RM_RECORD_DECLARED" -> acc.declaredCount += count;
            case "RM_RECORD_UNDECLARED" -> acc.undeclaredCount += count;
            case "RM_RECORD_CATEGORY_ASSIGNED" -> acc.categoryAssignedCount += count;
            default -> {
                if (RM_TIMELINE_GOVERNANCE_EVENTS.contains(eventType)) {
                    acc.governanceChangeCount += count;
                }
            }
        }
    }

    private int normalizeContributorsDays(Integer days) {
        if (days == null) {
            return RM_ACTIVITY_CONTRIBUTORS_DEFAULT_DAYS;
        }
        return Math.max(RM_ACTIVITY_CONTRIBUTORS_MIN_DAYS, Math.min(days, RM_ACTIVITY_CONTRIBUTORS_MAX_DAYS));
    }

    private int normalizeContributorsLimit(Integer limit) {
        if (limit == null) {
            return RM_ACTIVITY_CONTRIBUTORS_DEFAULT_LIMIT;
        }
        return Math.max(RM_ACTIVITY_CONTRIBUTORS_MIN_LIMIT, Math.min(limit, RM_ACTIVITY_CONTRIBUTORS_MAX_LIMIT));
    }

    private int normalizeContributorTrendDays(Integer days) {
        if (days == null) {
            return RM_ACTIVITY_CONTRIBUTOR_TREND_DEFAULT_DAYS;
        }
        return Math.max(RM_ACTIVITY_CONTRIBUTOR_TREND_MIN_DAYS, Math.min(days, RM_ACTIVITY_CONTRIBUTOR_TREND_MAX_DAYS));
    }

    private int normalizeContributorTrendBucketDays(Integer bucketDays, int effectiveDays) {
        if (bucketDays == null) {
            return Math.min(RM_ACTIVITY_CONTRIBUTOR_TREND_DEFAULT_BUCKET_DAYS, effectiveDays);
        }
        int normalized = Math.max(
            RM_ACTIVITY_CONTRIBUTOR_TREND_MIN_BUCKET_DAYS,
            Math.min(bucketDays, RM_ACTIVITY_CONTRIBUTOR_TREND_MAX_BUCKET_DAYS)
        );
        return Math.min(normalized, effectiveDays);
    }

    private int normalizeContributorTrendLimit(Integer limit) {
        if (limit == null) {
            return RM_ACTIVITY_CONTRIBUTOR_TREND_DEFAULT_LIMIT;
        }
        return Math.max(RM_ACTIVITY_CONTRIBUTOR_TREND_MIN_LIMIT, Math.min(limit, RM_ACTIVITY_CONTRIBUTOR_TREND_MAX_LIMIT));
    }

    private int normalizeEventTypesDays(Integer days) {
        if (days == null) {
            return RM_ACTIVITY_EVENT_TYPES_DEFAULT_DAYS;
        }
        return Math.max(RM_ACTIVITY_EVENT_TYPES_MIN_DAYS, Math.min(days, RM_ACTIVITY_EVENT_TYPES_MAX_DAYS));
    }

    private int normalizeEventTypesLimit(Integer limit) {
        if (limit == null) {
            return RM_ACTIVITY_EVENT_TYPES_DEFAULT_LIMIT;
        }
        return Math.max(RM_ACTIVITY_EVENT_TYPES_MIN_LIMIT, Math.min(limit, RM_ACTIVITY_EVENT_TYPES_MAX_LIMIT));
    }

    private int normalizeEventTypeTrendDays(Integer days) {
        if (days == null) {
            return RM_ACTIVITY_EVENT_TYPE_TREND_DEFAULT_DAYS;
        }
        return Math.max(RM_ACTIVITY_EVENT_TYPE_TREND_MIN_DAYS, Math.min(days, RM_ACTIVITY_EVENT_TYPE_TREND_MAX_DAYS));
    }

    private int normalizeEventTypeTrendBucketDays(Integer bucketDays, int effectiveDays) {
        if (bucketDays == null) {
            return Math.min(RM_ACTIVITY_EVENT_TYPE_TREND_DEFAULT_BUCKET_DAYS, effectiveDays);
        }
        int normalized = Math.max(
            RM_ACTIVITY_EVENT_TYPE_TREND_MIN_BUCKET_DAYS,
            Math.min(bucketDays, RM_ACTIVITY_EVENT_TYPE_TREND_MAX_BUCKET_DAYS)
        );
        return Math.min(normalized, effectiveDays);
    }

    private int normalizeEventTypeTrendLimit(Integer limit) {
        if (limit == null) {
            return RM_ACTIVITY_EVENT_TYPE_TREND_DEFAULT_LIMIT;
        }
        return Math.max(RM_ACTIVITY_EVENT_TYPE_TREND_MIN_LIMIT, Math.min(limit, RM_ACTIVITY_EVENT_TYPE_TREND_MAX_LIMIT));
    }

    private int normalizeFamiliesDays(Integer days) {
        if (days == null) {
            return RM_ACTIVITY_FAMILIES_DEFAULT_DAYS;
        }
        return Math.max(RM_ACTIVITY_FAMILIES_MIN_DAYS, Math.min(days, RM_ACTIVITY_FAMILIES_MAX_DAYS));
    }

    private int normalizeActivityFamilyTrendDays(Integer days) {
        if (days == null) {
            return RM_ACTIVITY_FAMILY_TREND_DEFAULT_DAYS;
        }
        return Math.max(RM_ACTIVITY_FAMILY_TREND_MIN_DAYS, Math.min(days, RM_ACTIVITY_FAMILY_TREND_MAX_DAYS));
    }

    private int normalizeActivityFamilyTrendBucketDays(Integer bucketDays, int effectiveDays) {
        if (bucketDays == null) {
            return Math.min(RM_ACTIVITY_FAMILY_TREND_DEFAULT_BUCKET_DAYS, effectiveDays);
        }
        int normalized = Math.max(
            RM_ACTIVITY_FAMILY_TREND_MIN_BUCKET_DAYS,
            Math.min(bucketDays, RM_ACTIVITY_FAMILY_TREND_MAX_BUCKET_DAYS)
        );
        return Math.min(normalized, effectiveDays);
    }

    @Transactional(readOnly = true)
    List<Document> findBlockingRecords(Node node) {
        String targetPath = normalizePath(node.getPath());
        if (targetPath == null) {
            return List.of();
        }
        return nodeRepository.findByAspectNameAndDeletedFalse(RECORD_ASPECT).stream()
            .filter(Document.class::isInstance)
            .map(Document.class::cast)
            .filter(this::isVisibleLiveDocument)
            .filter(record -> intersects(targetPath, normalizePath(record.getPath())))
            .sorted(Comparator.comparing(Document::getPath, Comparator.nullsLast(String::compareToIgnoreCase)))
            .toList();
    }

    private List<Document> loadVisibleLiveRecords() {
        return nodeRepository.findByAspectNameAndDeletedFalse(RECORD_ASPECT).stream()
            .filter(Document.class::isInstance)
            .map(Document.class::cast)
            .filter(this::isVisibleLiveDocument)
            .toList();
    }

    private boolean intersects(String targetPath, String recordPath) {
        if (targetPath == null || recordPath == null) {
            return false;
        }
        return targetPath.equals(recordPath)
            || recordPath.startsWith(targetPath + "/")
            || targetPath.startsWith(recordPath + "/");
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        String normalized = path.trim();
        if (normalized.endsWith("/") && normalized.length() > 1) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private Document loadLiveDocument(UUID nodeId) {
        Document document = documentRepository.findById(nodeId)
            .orElseThrow(() -> new NoSuchElementException("Document not found: " + nodeId));
        if (!isVisibleLiveDocument(document)) {
            throw new ResourceNotFoundException("Document not found: " + nodeId);
        }
        return document;
    }

    private Optional<Node> loadVisibleNode(UUID nodeId) {
        if (nodeId == null) {
            return Optional.empty();
        }
        return nodeRepository.findById(nodeId)
            .filter(node -> node.getPath() != null)
            .filter(node -> tenantWorkspaceScopeService.isPathVisible(node.getPath()));
    }

    private GovernanceClassification classifyImportJob(com.ecm.core.entity.ImportJob job) {
        Node targetFolder = loadVisibleNode(job.getTargetFolderId()).orElse(null);
        return new GovernanceClassification(classifyGovernanceReasons(targetFolder, "TARGET"), targetFolder, null);
    }

    private GovernanceClassification classifyTransferJob(com.ecm.core.entity.ReplicationJob job) {
        Node sourceNode = loadVisibleNode(job.getSourceNodeId()).orElse(null);
        List<String> reasons = new ArrayList<>(classifyGovernanceReasons(sourceNode, "SOURCE"));
        Node targetFolder = transferTargetRepository.findById(job.getTransferTargetId())
            .flatMap(target -> loadVisibleNode(target.getTargetFolderId()))
            .orElse(null);
        reasons.addAll(classifyGovernanceReasons(targetFolder, "TARGET"));
        return new GovernanceClassification(reasons.stream().distinct().toList(), sourceNode, targetFolder);
    }

    private List<String> classifyGovernanceReasons(Node node, String prefix) {
        if (node == null) {
            return List.of();
        }
        List<String> reasons = new ArrayList<>();
        if (isDeclaredRecord(node)) {
            reasons.add(prefix + "_DECLARED_RECORD");
        }
        if (node instanceof Folder folder && isFilePlanFolder(folder)) {
            reasons.add(prefix + "_FILE_PLAN");
        }
        findContainingFilePlan(node)
            .filter(filePlan -> !Objects.equals(filePlan.getId(), node.getId()))
            .ifPresent(filePlan -> reasons.add(prefix + "_INSIDE_FILE_PLAN"));
        return reasons;
    }

    private Optional<GovernedImportJobDto> toGovernedImportJobDto(com.ecm.core.entity.ImportJob job) {
        GovernanceClassification classification = classifyImportJob(job);
        if (!classification.governed()) {
            return Optional.empty();
        }
        Node targetFolder = classification.primaryNode();
        return Optional.of(new GovernedImportJobDto(
            job.getId(),
            job.getTargetFolderId(),
            targetFolder != null ? targetFolder.getName() : null,
            targetFolder != null ? targetFolder.getPath() : null,
            job.getStatus(),
            job.getConflictPolicy(),
            job.getTotalFiles(),
            job.getImportedFiles(),
            job.getSkippedFiles(),
            job.getFailedFiles(),
            job.getLastMessage(),
            classification.reasons(),
            job.getStartedAt(),
            job.getCompletedAt(),
            job.getCreatedAt()
        ));
    }

    private Optional<GovernedTransferJobDto> toGovernedTransferJobDto(com.ecm.core.entity.ReplicationJob job) {
        GovernanceClassification classification = classifyTransferJob(job);
        if (!classification.governed()) {
            return Optional.empty();
        }
        Node sourceNode = classification.primaryNode();
        Node targetFolder = classification.secondaryNode();
        return Optional.of(new GovernedTransferJobDto(
            job.getId(),
            job.getDefinitionId(),
            job.getSourceNodeId(),
            sourceNode != null ? sourceNode.getName() : null,
            sourceNode != null ? sourceNode.getPath() : null,
            targetFolder != null ? targetFolder.getId() : null,
            targetFolder != null ? targetFolder.getName() : null,
            targetFolder != null ? targetFolder.getPath() : null,
            job.getStatus(),
            job.getTransportStatus(),
            job.getLastMessage(),
            job.getTransportMessage(),
            classification.reasons(),
            job.getStartedAt(),
            job.getCompletedAt(),
            job.getCreatedAt()
        ));
    }

    private boolean isActiveImportJob(com.ecm.core.entity.ImportJob job) {
        return job.getStatus() == com.ecm.core.entity.ImportJob.ImportJobStatus.PENDING
            || job.getStatus() == com.ecm.core.entity.ImportJob.ImportJobStatus.RUNNING;
    }

    private boolean isActiveTransferJob(com.ecm.core.entity.ReplicationJob job) {
        return job.getStatus() == com.ecm.core.entity.ReplicationJob.ReplicationJobStatus.PENDING
            || job.getStatus() == com.ecm.core.entity.ReplicationJob.ReplicationJobStatus.RUNNING;
    }

    private boolean isFailedImportJob(com.ecm.core.entity.ImportJob job) {
        return job.getStatus() == com.ecm.core.entity.ImportJob.ImportJobStatus.FAILED
            || job.getStatus() == com.ecm.core.entity.ImportJob.ImportJobStatus.CANCELED;
    }

    private boolean isFailedTransferJob(com.ecm.core.entity.ReplicationJob job) {
        return job.getStatus() == com.ecm.core.entity.ReplicationJob.ReplicationJobStatus.FAILED
            || job.getStatus() == com.ecm.core.entity.ReplicationJob.ReplicationJobStatus.CANCELED
            || job.getTransportStatus() == com.ecm.core.entity.ReplicationJob.TransportStatus.FAILED;
    }

    private List<SummaryBucketDto> bucketBreakdown(List<String> rawStatuses) {
        Map<String, Long> breakdown = new LinkedHashMap<>();
        for (String status : rawStatuses) {
            breakdown.merge(status, 1L, Long::sum);
        }
        return breakdown.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER))
            .map(entry -> new SummaryBucketDto(entry.getKey(), entry.getValue()))
            .toList();
    }

    private int normalizeTelemetryLimit(Integer limit) {
        if (limit == null) {
            return 20;
        }
        return Math.max(1, Math.min(limit, 100));
    }

    private int normalizeTimelineDays(Integer days) {
        if (days == null) {
            return 14;
        }
        return Math.max(3, Math.min(days, 90));
    }

    private int normalizeHighlightsWindowDays(Integer days) {
        if (days == null) {
            return RM_ACTIVITY_HIGHLIGHTS_DEFAULT_WINDOW_DAYS;
        }
        return Math.max(RM_ACTIVITY_HIGHLIGHTS_MIN_WINDOW_DAYS, Math.min(days, RM_ACTIVITY_HIGHLIGHTS_MAX_WINDOW_DAYS));
    }

    private int normalizeBreakdownDays(Integer days) {
        if (days == null) {
            return RM_ACTIVITY_BREAKDOWN_DEFAULT_DAYS;
        }
        return Math.max(RM_ACTIVITY_BREAKDOWN_MIN_DAYS, Math.min(days, RM_ACTIVITY_BREAKDOWN_MAX_DAYS));
    }

    private int normalizeBreakdownBucketDays(Integer bucketDays, int effectiveDays) {
        if (bucketDays == null) {
            return Math.min(RM_ACTIVITY_BREAKDOWN_DEFAULT_BUCKET_DAYS, effectiveDays);
        }
        int normalized = Math.max(
            RM_ACTIVITY_BREAKDOWN_MIN_BUCKET_DAYS,
            Math.min(bucketDays, RM_ACTIVITY_BREAKDOWN_MAX_BUCKET_DAYS)
        );
        return Math.min(normalized, effectiveDays);
    }

    private List<RecordsActivityPointDto> buildActivityTimeline(int effectiveDays) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(effectiveDays - 1L);

        Map<LocalDate, TimelineAccumulator> timeline = new LinkedHashMap<>();
        for (int i = 0; i < effectiveDays; i++) {
            LocalDate day = startDate.plusDays(i);
            timeline.put(day, new TimelineAccumulator(day));
        }

        for (Object[] row : auditLogRepository.countRecordsManagementEventsByDaySince(startDate.atStartOfDay())) {
            LocalDate day = parseAuditDay(row[0]);
            String eventType = row[1] != null ? row[1].toString().trim().toUpperCase(Locale.ROOT) : null;
            long count = toLong(row[2]);
            if (day == null || eventType == null || count <= 0 || !timeline.containsKey(day)) {
                continue;
            }

            TimelineAccumulator accumulator = timeline.get(day);
            switch (eventType) {
                case "RM_RECORD_DECLARED" -> accumulator.declaredCount += count;
                case "RM_RECORD_UNDECLARED" -> accumulator.undeclaredCount += count;
                case "RM_RECORD_CATEGORY_ASSIGNED" -> accumulator.categoryAssignedCount += count;
                default -> {
                    if (RM_TIMELINE_GOVERNANCE_EVENTS.contains(eventType)) {
                        accumulator.governanceChangeCount += count;
                    }
                }
            }
        }

        return timeline.values().stream().map(TimelineAccumulator::toDto).toList();
    }

    private RecordsActivityWindowDto summarizeTimelineWindow(List<RecordsActivityPointDto> points) {
        if (points == null || points.isEmpty()) {
            return new RecordsActivityWindowDto(null, null, 0, 0, 0, 0, 0, 0);
        }
        String fromDay = points.get(0).day();
        String toDay = points.get(points.size() - 1).day();
        long activeDayCount = points.stream().filter(point -> point.totalCount() > 0).count();
        long declaredCount = points.stream().mapToLong(RecordsActivityPointDto::declaredCount).sum();
        long undeclaredCount = points.stream().mapToLong(RecordsActivityPointDto::undeclaredCount).sum();
        long categoryAssignedCount = points.stream().mapToLong(RecordsActivityPointDto::categoryAssignedCount).sum();
        long governanceChangeCount = points.stream().mapToLong(RecordsActivityPointDto::governanceChangeCount).sum();
        long totalCount = points.stream().mapToLong(RecordsActivityPointDto::totalCount).sum();
        return new RecordsActivityWindowDto(
            fromDay,
            toDay,
            activeDayCount,
            declaredCount,
            undeclaredCount,
            categoryAssignedCount,
            governanceChangeCount,
            totalCount
        );
    }

    private LocalDate parseAuditDay(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toLocalDate();
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof java.util.Date utilDate) {
            return new java.sql.Date(utilDate.getTime()).toLocalDate();
        }
        try {
            return LocalDate.parse(value.toString());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private void validateFilePlanParent(UUID parentId) {
        if (parentId == null) {
            return;
        }
        Folder parent = folderService.getFolder(parentId);
        if (isFilePlanFolder(parent)) {
            return;
        }
        if (parent.getParent() == null || parent.getFolderType() == Folder.FolderType.WORKSPACE || parent.getFolderType() == Folder.FolderType.SYSTEM) {
            return;
        }
        throw new IllegalOperationException(
            "File plans can only be created at the root, under workspace/system roots, or inside another file plan"
        );
    }

    private Category ensureRecordCategoryRoot() {
        return categoryRepository.findFirstByPathAndActiveTrue(RECORD_CATEGORY_ROOT_PATH)
            .map(existing -> {
                if (existing.getPurpose() != Category.Purpose.RECORD) {
                    existing.setPurpose(Category.Purpose.RECORD);
                    return categoryRepository.save(existing);
                }
                return existing;
            })
            .orElseGet(() -> {
                Category root = new Category();
                root.setName("Records Management");
                root.setDescription("Seeded root category for record classification");
                root.setCreator("system");
                root.setPurpose(Category.Purpose.RECORD);
                root.setActive(true);
                return categoryRepository.save(root);
            });
    }

    private Folder loadFilePlan(UUID folderId) {
        Folder folder = folderService.getFolder(folderId);
        if (!isFilePlanFolder(folder)) {
            throw new IllegalOperationException("Folder '" + folder.getName() + "' is not a file plan");
        }
        return folder;
    }

    private void refreshDescendantPaths(Node parent) {
        if (!(parent instanceof Folder)) {
            return;
        }

        List<Node> children = nodeRepository.findByParentIdAndDeletedFalse(parent.getId());
        for (Node child : children) {
            child.setPath(parent.getPath() + "/" + child.getName());
            Node savedChild = nodeRepository.save(child);
            refreshDescendantPaths(savedChild);
        }
    }

    private void refreshRecordCategoryDescendantPaths(Category parent, List<Category> subtree) {
        for (Category child : categoryRepository.findByParentAndActiveTrue(parent)) {
            child.setParent(parent);
            Category savedChild = categoryRepository.save(child);
            subtree.add(savedChild);
            refreshRecordCategoryDescendantPaths(savedChild, subtree);
        }
    }

    private Category loadRecordCategory(UUID categoryId) {
        Category category = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new NoSuchElementException("Record category not found: " + categoryId));
        if (category.getPurpose() != Category.Purpose.RECORD) {
            throw new IllegalOperationException("Category '" + category.getName() + "' is not a record category");
        }
        ensureRecordCategoryRoot();
        String categoryPath = category.getPath();
        if (categoryPath == null
            || !(categoryPath.equals(RECORD_CATEGORY_ROOT_PATH) || categoryPath.startsWith(RECORD_CATEGORY_ROOT_PATH + "/"))) {
            throw new IllegalOperationException("Category '" + category.getName() + "' is outside the record category tree");
        }
        return category;
    }

    private Category loadActiveRecordCategory(UUID categoryId) {
        Category category = loadRecordCategory(categoryId);
        if (!Boolean.TRUE.equals(category.getActive())) {
            throw new NoSuchElementException("Record category not found: " + categoryId);
        }
        return category;
    }

    private boolean isRecordCategoryRoot(Category category) {
        return category != null && RECORD_CATEGORY_ROOT_PATH.equals(category.getPath());
    }

    private void assertRecordCategoryMoveAllowed(Category category, Category targetParent) {
        if (category == null || targetParent == null) {
            return;
        }
        if (Objects.equals(category.getId(), targetParent.getId())) {
            throw new IllegalOperationException("A record category cannot be moved under itself");
        }

        Category current = targetParent;
        while (current != null) {
            if (Objects.equals(current.getId(), category.getId())) {
                throw new IllegalOperationException(
                    "Cannot move record category '" + category.getName() + "' under its own subtree"
                );
            }
            current = current.getParent();
        }
    }

    private void applyRecordCategory(Document document, Category category) {
        Set<Category> categories = document.getCategories();
        categories.removeIf(existing -> existing != null && existing.getPurpose() == Category.Purpose.RECORD);
        categories.add(category);
        Map<String, Object> properties = document.getProperties();
        properties.put(RECORD_CATEGORY_ID_PROPERTY, category.getId().toString());
        properties.put(RECORD_CATEGORY_NAME_PROPERTY, category.getName());
        properties.put(RECORD_CATEGORY_PATH_PROPERTY, category.getPath());
    }

    private List<UUID> repairDeclaredRecordCategoryMetadata(List<Category> subtreeCategories) {
        if (subtreeCategories == null || subtreeCategories.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<UUID> categoryIds = subtreeCategories.stream()
            .map(Category::getId)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (categoryIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, Document> affectedDocuments = new LinkedHashMap<>();
        for (Node node : nodeRepository.findByCategoryIds(List.copyOf(categoryIds))) {
            if (!(node instanceof Document document) || !isDeclaredRecord(document)) {
                continue;
            }
            Category recordCategory = document.getCategories().stream()
                .filter(existing -> existing != null
                    && existing.getPurpose() == Category.Purpose.RECORD
                    && existing.getId() != null
                    && categoryIds.contains(existing.getId()))
                .findFirst()
                .orElse(null);
            if (recordCategory == null) {
                continue;
            }

            applyRecordCategory(document, recordCategory);
            Document saved = documentRepository.save(document);
            affectedDocuments.put(saved.getId(), saved);
        }

        List<UUID> affectedNodeIds = List.copyOf(affectedDocuments.keySet());
        if (!affectedNodeIds.isEmpty()) {
            eventPublisher.publishEvent(new NodesReindexRequestedEvent(affectedNodeIds, securityService.getCurrentUser()));
        }
        return affectedNodeIds;
    }

    private List<Folder> visibleFilePlans() {
        return folderRepository.findActiveFoldersByType(Folder.FolderType.FILE_PLAN).stream()
            .filter(folder -> folder.getPath() != null)
            .filter(folder -> tenantWorkspaceScopeService.isPathVisible(folder.getPath()))
            .sorted(Comparator.comparingInt((Folder folder) -> normalizePath(folder.getPath()) != null
                ? normalizePath(folder.getPath()).length()
                : 0).reversed())
            .toList();
    }

    private void assertArchiveMutationAllowed(Node node, String operation, List<Folder> filePlans) {
        if (isDeclaredRecord(node)) {
            throw new IllegalOperationException(
                "Cannot " + operation + " because node '" + node.getName() + "' is declared as a record"
            );
        }
        findContainingFilePlan(node, filePlans).ifPresent(filePlan -> {
            if (Objects.equals(filePlan.getId(), node.getId())) {
                throw new IllegalOperationException(
                    "Cannot " + operation + " because node '" + node.getName() + "' is a file plan"
                );
            }
            throw new IllegalOperationException(
                "Cannot " + operation + " because node '" + node.getName()
                    + "' is governed by file plan '" + filePlan.getName() + "'"
            );
        });
    }

    private Optional<Folder> findContainingFilePlan(Node node) {
        return findContainingFilePlan(node, visibleFilePlans());
    }

    private Optional<Folder> findContainingFilePlan(Node node, List<Folder> filePlans) {
        if (node == null) {
            return Optional.empty();
        }
        String nodePath = normalizePath(node.getPath());
        if (nodePath == null) {
            return Optional.empty();
        }
        return filePlans.stream()
            .filter(filePlan -> {
                String filePlanPath = normalizePath(filePlan.getPath());
                return filePlanPath != null
                    && (nodePath.equals(filePlanPath) || nodePath.startsWith(filePlanPath + "/"));
            })
            .findFirst();
    }

    private boolean isVisibleLiveDocument(Document document) {
        if (document == null || document.isDeleted() || document.getArchiveStatus() != Node.ArchiveStatus.LIVE) {
            return false;
        }
        if (!tenantWorkspaceScopeService.hasScopedTenantWorkspace()) {
            return true;
        }
        return tenantWorkspaceScopeService.isPathVisible(document.getPath());
    }

    private String normalizeAuditEventType(String eventType) {
        if (!StringUtils.hasText(eventType)) {
            return null;
        }
        String normalized = eventType.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith(RM_AUDIT_PREFIX) ? normalized : RM_AUDIT_PREFIX + normalized;
    }

    private List<String> resolveEventTypes(RmEventFamily family) {
        return switch (family) {
            case DECLARED -> List.of("RM_RECORD_DECLARED");
            case UNDECLARED -> List.of("RM_RECORD_UNDECLARED");
            case CATEGORY_ASSIGNED -> List.of("RM_RECORD_CATEGORY_ASSIGNED");
            case GOVERNANCE_CHANGE -> List.copyOf(RM_TIMELINE_GOVERNANCE_EVENTS);
            case OTHER -> throw new IllegalArgumentException("OTHER family requires complementary RM audit filtering");
        };
    }

    private List<String> resolveNonOtherEventTypes() {
        LinkedHashSet<String> eventTypes = new LinkedHashSet<>();
        eventTypes.addAll(resolveEventTypes(RmEventFamily.DECLARED));
        eventTypes.addAll(resolveEventTypes(RmEventFamily.UNDECLARED));
        eventTypes.addAll(resolveEventTypes(RmEventFamily.CATEGORY_ASSIGNED));
        eventTypes.addAll(resolveEventTypes(RmEventFamily.GOVERNANCE_CHANGE));
        return List.copyOf(eventTypes);
    }

    private String classifyActivityFamily(String eventType) {
        if (!StringUtils.hasText(eventType)) {
            return "OTHER";
        }
        String normalized = eventType.trim().toUpperCase(Locale.ROOT);
        if ("RM_RECORD_DECLARED".equals(normalized)) {
            return "DECLARED";
        }
        if ("RM_RECORD_UNDECLARED".equals(normalized)) {
            return "UNDECLARED";
        }
        if ("RM_RECORD_CATEGORY_ASSIGNED".equals(normalized)) {
            return "CATEGORY_ASSIGNED";
        }
        if (RM_TIMELINE_GOVERNANCE_EVENTS.contains(normalized)) {
            return "GOVERNANCE_CHANGE";
        }
        return "OTHER";
    }

    private static int activityFamilySortRank(String family) {
        return switch (family) {
            case "DECLARED" -> 0;
            case "UNDECLARED" -> 1;
            case "CATEGORY_ASSIGNED" -> 2;
            case "GOVERNANCE_CHANGE" -> 3;
            default -> 4;
        };
    }

    private void requireAdmin() {
        if (!securityService.hasRole("ROLE_ADMIN")) {
            throw new SecurityException("Admin access required for records management operations");
        }
    }

    private void blockedUndeclare(Document document, String currentUser, String reason, String message) {
        auditService.logEvent(
            "RM_RECORD_UNDECLARE_BLOCKED",
            document.getId(),
            document.getName(),
            currentUser,
            message + ". Reason: " + reason
        );
        throw new IllegalOperationException(message);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private RecordDeclarationDto toDto(Document document) {
        Map<String, Object> properties = document.getProperties() != null ? document.getProperties() : Map.of();
        Category recordCategory = document.getCategories().stream()
            .filter(category -> category != null && category.getPurpose() == Category.Purpose.RECORD)
            .findFirst()
            .orElse(null);
        return new RecordDeclarationDto(
            document.getId(),
            document.getName(),
            document.getPath(),
            document.getVersionLabel(),
            asString(properties.get(DECLARED_VERSION_LABEL_PROPERTY)),
            asString(properties.get(DECLARED_BY_PROPERTY)),
            parseDateTime(properties.get(DECLARED_AT_PROPERTY)),
            asString(properties.get(DECLARATION_COMMENT_PROPERTY)),
            recordCategory != null ? recordCategory.getId() : parseUuid(properties.get(RECORD_CATEGORY_ID_PROPERTY)),
            recordCategory != null ? recordCategory.getName() : asString(properties.get(RECORD_CATEGORY_NAME_PROPERTY)),
            recordCategory != null ? recordCategory.getPath() : asString(properties.get(RECORD_CATEGORY_PATH_PROPERTY))
        );
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private LocalDateTime parseDateTime(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.toString());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private UUID parseUuid(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private FilePlanDto toFilePlanDto(Folder folder) {
        return new FilePlanDto(
            folder.getId(),
            folder.getName(),
            folder.getDescription(),
            folder.getPath(),
            folder.getParent() != null ? folder.getParent().getId() : null
        );
    }

    private RecordCategoryDto toRecordCategoryDto(Category category) {
        return new RecordCategoryDto(
            category.getId(),
            category.getName(),
            category.getDescription(),
            category.getPath(),
            category.getLevel(),
            category.getParent() != null ? category.getParent().getId() : null
        );
    }

    private RecordAuditEntryDto toAuditDto(AuditLog auditLog) {
        return new RecordAuditEntryDto(
            auditLog.getId(),
            auditLog.getEventType(),
            auditLog.getNodeId(),
            auditLog.getNodeName(),
            auditLog.getUsername(),
            auditLog.getEventTime(),
            auditLog.getDetails()
        );
    }

    public record DeclareRecordRequest(String comment, UUID categoryId) {
    }

    public record UndeclareRecordRequest(String reason) {
    }

    public record CreateFilePlanRequest(String name, String description, UUID parentId) {
    }

    public record UpdateFilePlanRequest(String description) {
    }

    public record RenameFilePlanRequest(String name) {
    }

    public record MoveFilePlanRequest(UUID targetParentId) {
    }

    public record FilePlanDto(
        UUID folderId,
        String name,
        String description,
        String path,
        UUID parentId
    ) {
    }

    public record CreateRecordCategoryRequest(String name, String description, UUID parentId) {
    }

    public record UpdateRecordCategoryRequest(String description) {
    }

    public record RenameRecordCategoryRequest(String name) {
    }

    public record MoveRecordCategoryRequest(UUID targetParentId) {
    }

    public record RecordCategoryAssignmentRequest(UUID categoryId) {
    }

    public record RecordCategoryDto(
        UUID categoryId,
        String name,
        String description,
        String path,
        Integer level,
        UUID parentId
    ) {
    }

    public record SummaryBucketDto(String key, long count) {
    }

    public record RecordsSummaryDto(
        int declaredRecordCount,
        int filePlanCount,
        int recordCategoryCount,
        long uncategorizedRecordCount,
        long outsideFilePlanRecordCount,
        List<SummaryBucketDto> categoryBreakdown,
        List<SummaryBucketDto> filePlanBreakdown
    ) {
    }

    public record RecordAuditEntryDto(
        UUID auditLogId,
        String eventType,
        UUID nodeId,
        String nodeName,
        String username,
        LocalDateTime eventTime,
        String details
    ) {
    }

    public record GovernedImportJobDto(
        UUID jobId,
        UUID targetFolderId,
        String targetFolderName,
        String targetFolderPath,
        com.ecm.core.entity.ImportJob.ImportJobStatus status,
        com.ecm.core.entity.ImportJob.ConflictPolicy conflictPolicy,
        int totalFiles,
        int importedFiles,
        int skippedFiles,
        int failedFiles,
        String lastMessage,
        List<String> governanceReasons,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt
    ) {
    }

    public record GovernedTransferJobDto(
        UUID jobId,
        UUID definitionId,
        UUID sourceNodeId,
        String sourceNodeName,
        String sourceNodePath,
        UUID targetFolderId,
        String targetFolderName,
        String targetFolderPath,
        com.ecm.core.entity.ReplicationJob.ReplicationJobStatus status,
        com.ecm.core.entity.ReplicationJob.TransportStatus transportStatus,
        String lastMessage,
        String transportMessage,
        List<String> governanceReasons,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt
    ) {
    }

    public record RecordsOperationsTelemetryDto(
        int governedImportJobCount,
        long activeGovernedImportJobCount,
        long failedGovernedImportJobCount,
        int governedTransferJobCount,
        long activeGovernedTransferJobCount,
        long failedGovernedTransferJobCount,
        List<SummaryBucketDto> importStatusBreakdown,
        List<SummaryBucketDto> transferStatusBreakdown,
        List<SummaryBucketDto> importGovernanceReasonBreakdown,
        List<SummaryBucketDto> transferGovernanceReasonBreakdown,
        List<GovernedImportJobDto> recentImportJobs,
        List<GovernedTransferJobDto> recentTransferJobs
    ) {
    }

    public record RecordsActivityPointDto(
        String day,
        long declaredCount,
        long undeclaredCount,
        long categoryAssignedCount,
        long governanceChangeCount,
        long totalCount
    ) {
    }

    public record RecordsActivityTimelineDto(
        int days,
        List<RecordsActivityPointDto> points
    ) {
    }

    public record RecordsActivityWindowDto(
        String fromDay,
        String toDay,
        long activeDayCount,
        long declaredCount,
        long undeclaredCount,
        long categoryAssignedCount,
        long governanceChangeCount,
        long totalCount
    ) {
    }

    public record RecordsActivityPeakDto(
        String day,
        long totalCount
    ) {
    }

    public record RecordsActivityHighlightsDto(
        int windowDays,
        RecordsActivityWindowDto currentWindow,
        RecordsActivityWindowDto previousWindow,
        RecordsActivityPeakDto busiestDay
    ) {
    }

    public record RecordsActivityBucketDto(
        String label,
        String fromDay,
        String toDay,
        long activeDayCount,
        long declaredCount,
        long undeclaredCount,
        long categoryAssignedCount,
        long governanceChangeCount,
        long totalCount
    ) {
    }

    public record RecordsActivityBreakdownDto(
        int days,
        int bucketDays,
        List<RecordsActivityBucketDto> buckets
    ) {
    }

    public record RecordDeclarationDto(
        UUID nodeId,
        String name,
        String path,
        String currentVersionLabel,
        String declaredVersionLabel,
        String declaredBy,
        LocalDateTime declaredAt,
        String declarationComment,
        UUID recordCategoryId,
        String recordCategoryName,
        String recordCategoryPath
    ) {
    }

    public record ActivityContributorDto(
        String username,
        String label,
        long declaredCount,
        long undeclaredCount,
        long categoryAssignedCount,
        long governanceChangeCount,
        long totalCount,
        LocalDateTime lastEventTime
    ) {
    }

    public record ActivityContributorsDto(
        int days,
        int limit,
        List<ActivityContributorDto> contributors
    ) {
    }

    public record ActivityContributorHighlightDto(
        String username,
        String label,
        long currentCount,
        long previousCount,
        long delta,
        LocalDateTime lastEventTime
    ) {
    }

    public record ActivityContributorHighlightsDto(
        int windowDays,
        int limit,
        ActivityWindowRangeDto currentWindow,
        ActivityWindowRangeDto previousWindow,
        List<ActivityContributorHighlightDto> contributors
    ) {
    }

    public record ActivityContributorEventTypeHighlightsDto(
        int windowDays,
        int limit,
        int eventTypeLimit,
        ActivityWindowRangeDto currentWindow,
        ActivityWindowRangeDto previousWindow,
        List<ActivityContributorEventTypeReportEntryDto> contributors
    ) {
    }

    public record ActivityContributorFamilyHighlightsEntryDto(
        String username,
        String label,
        long currentCount,
        long previousCount,
        long delta,
        LocalDateTime lastEventTime,
        List<ActivityContributorFamilyReportFamilyDto> families
    ) {
    }

    public record ActivityContributorFamilyHighlightsDto(
        int windowDays,
        int limit,
        ActivityWindowRangeDto currentWindow,
        ActivityWindowRangeDto previousWindow,
        List<ActivityContributorFamilyHighlightsEntryDto> contributors
    ) {
    }

    public record ActivityContributorTrendContributorDto(
        String username,
        String label,
        long count,
        LocalDateTime lastEventTime
    ) {
    }

    public record ActivityContributorTrendCountDto(
        String username,
        String label,
        long count
    ) {
    }

    public record ActivityContributorTrendBucketDto(
        String label,
        String fromDay,
        String toDay,
        long activeDayCount,
        long totalCount,
        long otherCount,
        List<ActivityContributorTrendCountDto> contributorCounts
    ) {
    }

    public record ActivityContributorTrendDto(
        int days,
        int bucketDays,
        int limit,
        List<ActivityContributorTrendContributorDto> trackedContributors,
        List<ActivityContributorTrendBucketDto> buckets
    ) {
    }

    public record ActivityContributorEventTypeTrendCountDto(
        String eventType,
        String family,
        long count
    ) {
    }

    public record ActivityContributorEventTypeTrendContributorDto(
        String username,
        String label,
        long count,
        List<ActivityContributorEventTypeTrendCountDto> eventTypes
    ) {
    }

    public record ActivityContributorEventTypeTrendBucketDto(
        String label,
        String fromDay,
        String toDay,
        long activeDayCount,
        long totalCount,
        long otherCount,
        List<ActivityContributorEventTypeTrendContributorDto> contributorCounts
    ) {
    }

    public record ActivityContributorEventTypeTrendDto(
        int days,
        int bucketDays,
        int limit,
        int eventTypeLimit,
        List<ActivityContributorTrendContributorDto> trackedContributors,
        List<ActivityContributorEventTypeTrendBucketDto> buckets
    ) {
    }

    public record ActivityContributorFamilyTrendCountDto(
        String family,
        long count
    ) {
    }

    public record ActivityContributorFamilyTrendContributorDto(
        String username,
        String label,
        long count,
        List<ActivityContributorFamilyTrendCountDto> families
    ) {
    }

    public record ActivityContributorFamilyTrendBucketDto(
        String label,
        String fromDay,
        String toDay,
        long activeDayCount,
        long totalCount,
        long otherCount,
        List<ActivityContributorFamilyTrendContributorDto> contributorCounts
    ) {
    }

    public record ActivityContributorFamilyTrendDto(
        int days,
        int bucketDays,
        int limit,
        List<ActivityContributorTrendContributorDto> trackedContributors,
        List<ActivityContributorFamilyTrendBucketDto> buckets
    ) {
    }

    public record ActivityContributorReportEventTypeDto(
        String eventType,
        String family,
        long count,
        LocalDateTime lastEventTime
    ) {
    }

    public record ActivityContributorReportEntryDto(
        String username,
        String label,
        long currentCount,
        long previousCount,
        long delta,
        LocalDateTime lastEventTime,
        List<ActivityContributorReportEventTypeDto> currentTopEventTypes
    ) {
    }

    public record ActivityContributorReportDto(
        ActivityDateTimeRangeDto currentWindow,
        ActivityDateTimeRangeDto previousWindow,
        int limit,
        int eventTypeLimit,
        long currentTotalCount,
        long previousTotalCount,
        List<ActivityContributorReportEntryDto> contributors
    ) {
    }

    public record ActivityContributorEventTypeReportEventTypeDto(
        String eventType,
        String family,
        long currentCount,
        long previousCount,
        long delta,
        LocalDateTime lastEventTime
    ) {
    }

    public record ActivityContributorEventTypeReportEntryDto(
        String username,
        String label,
        long currentCount,
        long previousCount,
        long delta,
        LocalDateTime lastEventTime,
        List<ActivityContributorEventTypeReportEventTypeDto> eventTypes
    ) {
    }

    public record ActivityContributorEventTypeReportDto(
        ActivityDateTimeRangeDto currentWindow,
        ActivityDateTimeRangeDto previousWindow,
        int limit,
        int eventTypeLimit,
        long currentTotalCount,
        long previousTotalCount,
        List<ActivityContributorEventTypeReportEntryDto> contributors
    ) {
    }

    public record ActivityContributorFamilyReportFamilyDto(
        String family,
        long currentCount,
        long previousCount,
        long delta,
        LocalDateTime lastEventTime
    ) {
    }

    public record ActivityContributorFamilyReportEntryDto(
        String username,
        String label,
        long currentCount,
        long previousCount,
        long delta,
        LocalDateTime lastEventTime,
        List<ActivityContributorFamilyReportFamilyDto> families
    ) {
    }

    public record ActivityContributorFamilyReportDto(
        ActivityDateTimeRangeDto currentWindow,
        ActivityDateTimeRangeDto previousWindow,
        int limit,
        long currentTotalCount,
        long previousTotalCount,
        List<ActivityContributorFamilyReportEntryDto> contributors
    ) {
    }

    public record ActivityEventTypeTrendCountDto(
        String eventType,
        String family,
        long count
    ) {
    }

    public record ActivityEventTypeTrendBucketDto(
        String label,
        String fromDay,
        String toDay,
        long activeDayCount,
        long totalCount,
        long otherCount,
        List<ActivityEventTypeTrendCountDto> eventTypeCounts
    ) {
    }

    public record ActivityEventTypeTrendDto(
        int days,
        int bucketDays,
        int limit,
        List<ActivityEventTypeDto> trackedEventTypes,
        List<ActivityEventTypeTrendBucketDto> buckets
    ) {
    }

    public record ActivityEventTypeReportEntryDto(
        String eventType,
        String family,
        long currentCount,
        long previousCount,
        long delta,
        LocalDateTime lastEventTime
    ) {
    }

    public record ActivityEventTypeReportDto(
        ActivityDateTimeRangeDto currentWindow,
        ActivityDateTimeRangeDto previousWindow,
        int limit,
        long currentTotalCount,
        long previousTotalCount,
        List<ActivityEventTypeReportEntryDto> eventTypes
    ) {
    }

    public record ActivityEventTypeDto(
        String eventType,
        String family,
        long count,
        LocalDateTime lastEventTime
    ) {
    }

    public record ActivityEventTypesDto(
        int days,
        int limit,
        List<ActivityEventTypeDto> eventTypes
    ) {
    }

    public record ActivityFamilyDto(
        String family,
        long count,
        LocalDateTime lastEventTime
    ) {
    }

    public record ActivityWindowRangeDto(
        String fromDay,
        String toDay
    ) {
    }

    public record ActivityFamilyHighlightDto(
        String family,
        long currentCount,
        long previousCount,
        long delta,
        LocalDateTime lastEventTime
    ) {
    }

    public record ActivityFamilyHighlightsDto(
        int windowDays,
        ActivityWindowRangeDto currentWindow,
        ActivityWindowRangeDto previousWindow,
        List<ActivityFamilyHighlightDto> families
    ) {
    }

    public record ActivityFamilyTrendFamilyCountDto(
        String family,
        long count
    ) {
    }

    public record ActivityFamilyTrendBucketDto(
        String label,
        String fromDay,
        String toDay,
        long activeDayCount,
        long totalCount,
        List<ActivityFamilyTrendFamilyCountDto> familyCounts
    ) {
    }

    public record ActivityFamilyTrendDto(
        int days,
        int bucketDays,
        List<ActivityFamilyTrendBucketDto> buckets
    ) {
    }

    public record ActivityDateTimeRangeDto(
        String from,
        String to
    ) {
    }

    public record ActivityFamilyReportEventTypeDto(
        String eventType,
        long count,
        LocalDateTime lastEventTime
    ) {
    }

    public record ActivityFamilyReportContributorDto(
        String username,
        String label,
        long count,
        LocalDateTime lastEventTime
    ) {
    }

    public record ActivityFamilyReportEntryDto(
        String family,
        long currentCount,
        long previousCount,
        long delta,
        LocalDateTime lastEventTime,
        List<ActivityFamilyReportEventTypeDto> topEventTypes,
        List<ActivityFamilyReportContributorDto> topContributors
    ) {
    }

    public record ActivityFamilyReportDto(
        ActivityDateTimeRangeDto currentWindow,
        ActivityDateTimeRangeDto previousWindow,
        int eventTypeLimit,
        int contributorLimit,
        long currentTotalCount,
        long previousTotalCount,
        List<ActivityFamilyReportEntryDto> families
    ) {
    }

    public record ActivityFamiliesDto(
        int days,
        long totalCount,
        List<ActivityFamilyDto> families
    ) {
    }

    private record GovernanceClassification(
        List<String> reasons,
        Node primaryNode,
        Node secondaryNode
    ) {
        boolean governed() {
            return reasons != null && !reasons.isEmpty();
        }
    }

    private static final class ActivityFamilyDayAccumulator {
        private final LocalDate day;
        private final Map<String, Long> familyCounts = new LinkedHashMap<>();

        private ActivityFamilyDayAccumulator(LocalDate day) {
            this.day = day;
        }

        private void record(String family, long count) {
            familyCounts.merge(family, count, Long::sum);
        }

        private long totalCount() {
            return familyCounts.values().stream().mapToLong(Long::longValue).sum();
        }
    }

    private static final class ActivityEventTypeDayAccumulator {
        private final LocalDate day;
        private long totalCount;
        private long trackedCount;
        private final Map<String, ActivityEventTypeTrendCountAccumulator> trackedEventTypeCounts = new LinkedHashMap<>();

        private ActivityEventTypeDayAccumulator(LocalDate day) {
            this.day = day;
        }

        private void record(String eventType, String family, long count, boolean tracked) {
            totalCount += count;
            if (!tracked) {
                return;
            }
            trackedCount += count;
            ActivityEventTypeTrendCountAccumulator acc = trackedEventTypeCounts.computeIfAbsent(
                eventType,
                key -> new ActivityEventTypeTrendCountAccumulator(eventType, family)
            );
            acc.count += count;
        }

        private long totalCount() {
            return totalCount;
        }

        private long trackedCount() {
            return trackedCount;
        }
    }

    private static final class ActivityContributorDayAccumulator {
        private final LocalDate day;
        private long totalCount;
        private long trackedCount;
        private final Map<String, ActivityContributorTrendCountAccumulator> trackedContributorCounts = new LinkedHashMap<>();

        private ActivityContributorDayAccumulator(LocalDate day) {
            this.day = day;
        }

        private void record(String username, String label, long count, boolean tracked) {
            totalCount += count;
            if (!tracked) {
                return;
            }
            trackedCount += count;
            ActivityContributorTrendCountAccumulator acc = trackedContributorCounts.computeIfAbsent(
                username,
                key -> new ActivityContributorTrendCountAccumulator(username, label)
            );
            acc.count += count;
        }

        private long totalCount() {
            return totalCount;
        }

        private long trackedCount() {
            return trackedCount;
        }
    }

    private static final class ActivityEventTypeTrendCountAccumulator {
        private final String eventType;
        private final String family;
        private long count;

        private ActivityEventTypeTrendCountAccumulator(String eventType, String family) {
            this.eventType = eventType;
            this.family = family;
        }

        private long count() {
            return count;
        }

        private String eventType() {
            return eventType;
        }

        private ActivityEventTypeTrendCountDto toDto() {
            return new ActivityEventTypeTrendCountDto(eventType, family, count);
        }
    }

    private static final class ActivityContributorTrendCountAccumulator {
        private final String username;
        private final String label;
        private long count;

        private ActivityContributorTrendCountAccumulator(String username, String label) {
            this.username = username;
            this.label = label;
        }

        private long count() {
            return count;
        }

        private String label() {
            return label;
        }

        private ActivityContributorTrendCountDto toDto() {
            return new ActivityContributorTrendCountDto(
                username.isEmpty() ? null : username,
                label,
                count
            );
        }
    }

    private static final class ActivityContributorEventTypeTrendEventTypeAccumulator {
        private final String eventType;
        private final String family;
        private long count;

        private ActivityContributorEventTypeTrendEventTypeAccumulator(String eventType, String family) {
            this.eventType = eventType;
            this.family = family;
        }

        private String eventType() {
            return eventType;
        }

        private String family() {
            return family;
        }

        private long count() {
            return count;
        }

        private ActivityContributorEventTypeTrendCountDto toDto() {
            return new ActivityContributorEventTypeTrendCountDto(eventType, family, count);
        }
    }

    private static final class ActivityContributorEventTypeTrendContributorAccumulator {
        private final String username;
        private final String label;
        private long count;
        private final Map<String, ActivityContributorEventTypeTrendEventTypeAccumulator> eventTypes = new LinkedHashMap<>();

        private ActivityContributorEventTypeTrendContributorAccumulator(String username, String label) {
            this.username = username;
            this.label = label;
        }

        private long count() {
            return count;
        }

        private String label() {
            return label;
        }

        private ActivityContributorEventTypeTrendContributorDto toDto(int eventTypeLimit) {
            List<ActivityContributorEventTypeTrendCountDto> topEventTypes = eventTypes.values().stream()
                .filter(acc -> acc.count > 0)
                .sorted(Comparator
                    .comparingLong(ActivityContributorEventTypeTrendEventTypeAccumulator::count).reversed()
                    .thenComparing(ActivityContributorEventTypeTrendEventTypeAccumulator::family, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(ActivityContributorEventTypeTrendEventTypeAccumulator::eventType, String.CASE_INSENSITIVE_ORDER))
                .limit(eventTypeLimit)
                .map(ActivityContributorEventTypeTrendEventTypeAccumulator::toDto)
                .toList();
            return new ActivityContributorEventTypeTrendContributorDto(
                username.isEmpty() ? null : username,
                label,
                count,
                topEventTypes
            );
        }
    }

    private static final class ActivityContributorEventTypeTrendDayAccumulator {
        private final LocalDate day;
        private long totalCount;
        private long trackedCount;
        private final Map<String, ActivityContributorEventTypeTrendContributorAccumulator> trackedContributorCounts = new LinkedHashMap<>();

        private ActivityContributorEventTypeTrendDayAccumulator(LocalDate day) {
            this.day = day;
        }

        private void record(String username, String label, String eventType, String family, long count, boolean tracked) {
            totalCount += count;
            if (!tracked) {
                return;
            }
            trackedCount += count;
            ActivityContributorEventTypeTrendContributorAccumulator acc = trackedContributorCounts.computeIfAbsent(
                username,
                key -> new ActivityContributorEventTypeTrendContributorAccumulator(username, label)
            );
            acc.count += count;
            ActivityContributorEventTypeTrendEventTypeAccumulator eventTypeAcc = acc.eventTypes.computeIfAbsent(
                eventType,
                key -> new ActivityContributorEventTypeTrendEventTypeAccumulator(eventType, family)
            );
            eventTypeAcc.count += count;
        }

        private long totalCount() {
            return totalCount;
        }

        private long trackedCount() {
            return trackedCount;
        }
    }

    private static final class ActivityContributorFamilyTrendFamilyAccumulator {
        private final String family;
        private long count;

        private ActivityContributorFamilyTrendFamilyAccumulator(String family) {
            this.family = family;
        }

        private String family() {
            return family;
        }

        private long count() {
            return count;
        }

        private ActivityContributorFamilyTrendCountDto toDto() {
            return new ActivityContributorFamilyTrendCountDto(family, count);
        }
    }

    private static final class ActivityContributorFamilyTrendContributorAccumulator {
        private final String username;
        private final String label;
        private long count;
        private final Map<String, ActivityContributorFamilyTrendFamilyAccumulator> families = new LinkedHashMap<>();

        private ActivityContributorFamilyTrendContributorAccumulator(String username, String label) {
            this.username = username;
            this.label = label;
        }

        private long count() {
            return count;
        }

        private String label() {
            return label;
        }

        private ActivityContributorFamilyTrendContributorDto toDto() {
            List<ActivityContributorFamilyTrendCountDto> familyBreakdown = families.values().stream()
                .filter(acc -> acc.count > 0)
                .sorted(Comparator
                    .comparingLong(ActivityContributorFamilyTrendFamilyAccumulator::count).reversed()
                    .thenComparingInt(acc -> activityFamilySortRank(acc.family()))
                    .thenComparing(ActivityContributorFamilyTrendFamilyAccumulator::family, String.CASE_INSENSITIVE_ORDER))
                .map(ActivityContributorFamilyTrendFamilyAccumulator::toDto)
                .toList();
            return new ActivityContributorFamilyTrendContributorDto(
                username.isEmpty() ? null : username,
                label,
                count,
                familyBreakdown
            );
        }
    }

    private static final class ActivityContributorFamilyTrendDayAccumulator {
        private final LocalDate day;
        private long totalCount;
        private long trackedCount;
        private final Map<String, ActivityContributorFamilyTrendContributorAccumulator> trackedContributorCounts = new LinkedHashMap<>();

        private ActivityContributorFamilyTrendDayAccumulator(LocalDate day) {
            this.day = day;
        }

        private void record(String username, String label, String family, long count, boolean tracked) {
            totalCount += count;
            if (!tracked) {
                return;
            }
            trackedCount += count;
            ActivityContributorFamilyTrendContributorAccumulator acc = trackedContributorCounts.computeIfAbsent(
                username,
                key -> new ActivityContributorFamilyTrendContributorAccumulator(username, label)
            );
            acc.count += count;
            ActivityContributorFamilyTrendFamilyAccumulator familyAcc = acc.families.computeIfAbsent(
                family,
                key -> new ActivityContributorFamilyTrendFamilyAccumulator(family)
            );
            familyAcc.count += count;
        }

        private long totalCount() {
            return totalCount;
        }

        private long trackedCount() {
            return trackedCount;
        }
    }

    private record ResolvedActivityComparisonRange(
        LocalDateTime currentFrom,
        LocalDateTime currentTo,
        LocalDateTime previousFrom,
        LocalDateTime previousTo
    ) {
    }

    private static final class ActivityFamilyReportContributorAccumulator {
        private final String username;
        private long count;
        private LocalDateTime lastEventTime;

        private ActivityFamilyReportContributorAccumulator(String username) {
            this.username = StringUtils.hasText(username) ? username : null;
        }

        private long count() {
            return count;
        }

        private String label() {
            return username != null ? username : "(System)";
        }

        private ActivityFamilyReportContributorDto toDto() {
            return new ActivityFamilyReportContributorDto(username, label(), count, lastEventTime);
        }
    }

    private static final class ActivityContributorReportEventTypeAccumulator {
        private final String eventType;
        private final String family;
        private long count;
        private LocalDateTime lastEventTime;

        private ActivityContributorReportEventTypeAccumulator(String eventType, String family) {
            this.eventType = eventType;
            this.family = family;
        }

        private long count() {
            return count;
        }

        private String family() {
            return family;
        }

        private String eventType() {
            return eventType;
        }

        private ActivityContributorReportEventTypeDto toDto() {
            return new ActivityContributorReportEventTypeDto(eventType, family, count, lastEventTime);
        }
    }

    private static final class ActivityContributorEventTypeReportEventTypeAccumulator {
        private final String eventType;
        private final String family;
        private long count;
        private LocalDateTime lastEventTime;

        private ActivityContributorEventTypeReportEventTypeAccumulator(String eventType, String family) {
            this.eventType = eventType;
            this.family = family;
        }

        private String family() {
            return family;
        }

        private long count() {
            return count;
        }

        private LocalDateTime lastEventTime() {
            return lastEventTime;
        }
    }

    private static final class ActivityContributorReportAccumulator {
        private final String username;
        private long count;
        private LocalDateTime lastEventTime;

        private ActivityContributorReportAccumulator(String username) {
            this.username = StringUtils.hasText(username) ? username : null;
        }

        private String username() {
            return username;
        }

        private String label() {
            return username != null ? username : "(System)";
        }

        private long count() {
            return count;
        }

        private LocalDateTime lastEventTime() {
            return lastEventTime;
        }
    }

    private static final class ActivityContributorFamilyReportFamilyAccumulator {
        private final String family;
        private long count;
        private LocalDateTime lastEventTime;

        private ActivityContributorFamilyReportFamilyAccumulator(String family) {
            this.family = family;
        }

        private String family() {
            return family;
        }

        private long count() {
            return count;
        }

        private LocalDateTime lastEventTime() {
            return lastEventTime;
        }
    }

    private static final class ContributorAccumulator {
        private final String username;
        private long declaredCount;
        private long undeclaredCount;
        private long categoryAssignedCount;
        private long governanceChangeCount;
        private LocalDateTime lastEventTime;

        private ContributorAccumulator(String username) {
            this.username = username;
        }

        private long totalCount() {
            return declaredCount + undeclaredCount + categoryAssignedCount + governanceChangeCount;
        }

        private String label() {
            return username.isEmpty() ? "(System)" : username;
        }

        private ActivityContributorDto toDto() {
            return new ActivityContributorDto(
                username.isEmpty() ? null : username,
                label(),
                declaredCount,
                undeclaredCount,
                categoryAssignedCount,
                governanceChangeCount,
                totalCount(),
                lastEventTime
            );
        }
    }

    private String contributorKey(String username) {
        return StringUtils.hasText(username) ? username.trim() : "";
    }

    private String contributorLabel(String username) {
        return StringUtils.hasText(username) ? username.trim() : "(System)";
    }

    private static final class ActivityFamilyAccumulator {
        private final String family;
        private long count;
        private LocalDateTime lastEventTime;

        private ActivityFamilyAccumulator(String family) {
            this.family = family;
        }

        private String family() {
            return family;
        }

        private long count() {
            return count;
        }

        private LocalDateTime lastEventTime() {
            return lastEventTime;
        }

        private ActivityFamilyDto toDto() {
            return new ActivityFamilyDto(family, count, lastEventTime);
        }
    }

    private static final class TimelineAccumulator {
        private final LocalDate day;
        private long declaredCount;
        private long undeclaredCount;
        private long categoryAssignedCount;
        private long governanceChangeCount;

        private TimelineAccumulator(LocalDate day) {
            this.day = day;
        }

        private RecordsActivityPointDto toDto() {
            long totalCount = declaredCount + undeclaredCount + categoryAssignedCount + governanceChangeCount;
            return new RecordsActivityPointDto(
                day.toString(),
                declaredCount,
                undeclaredCount,
                categoryAssignedCount,
                governanceChangeCount,
                totalCount
            );
        }
    }
}
