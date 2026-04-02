package com.ecm.core.service;

import com.ecm.core.dto.CheckoutInfoDto;
import com.ecm.core.dto.LockInfoDto;
import com.ecm.core.entity.*;
import com.ecm.core.entity.Node.NodeStatus;
import com.ecm.core.entity.Node.NodeType;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.entity.Folder.FolderType;
import com.ecm.core.entity.AutomationRule.TriggerType;
import com.ecm.core.event.*;
import com.ecm.core.exception.PropertyValidationException;
import com.ecm.core.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NodeService {

    private final NodeRepository nodeRepository;
    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;
    private final PermissionRepository permissionRepository;
    private final CorrespondentRepository correspondentRepository;
    private final SecurityService securityService;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    @Lazy
    private RuleEngineService ruleEngineService;

    @Autowired
    @Lazy
    private DictionaryService dictionaryService;

    @Autowired
    @Lazy
    private PropertyConstraintValidator propertyConstraintValidator;

    @Value("${ecm.rules.enabled:true}")
    private boolean rulesEnabled;
    
    public Node createNode(Node node, UUID parentId) {
        log.debug("Creating node: {} under parent: {}", node.getName(), parentId);
        
        if (parentId != null) {
            Folder parent = folderRepository.findById(parentId)
                .orElseThrow(() -> new IllegalArgumentException("Parent folder not found: " + parentId));
            
            // Check permissions
            if (!securityService.hasPermission(parent, PermissionType.CREATE_CHILDREN)) {
                throw new SecurityException("No permission to create children in folder: " + parent.getName());
            }
            
            // Check if folder is full
            if (parent.getMaxItems() != null) {
                long childCount = nodeRepository.countByParentId(parentId);
                if (childCount >= parent.getMaxItems()) {
                    throw new IllegalStateException("Folder is full: " + parent.getName());
                }
            }
            
            node.setParent(parent);
        }
        
        // Check for duplicate names
        if (nodeRepository.findByParentIdAndName(parentId, node.getName()).isPresent()) {
            throw new IllegalArgumentException("Node with name already exists: " + node.getName());
        }

        // Content model enforcement: type → mandatory aspects → aspect properties
        applyMandatoryAspects(node);
        enforceTypeProperties(node);
        enforceAspectProperties(node);

        Node savedNode = nodeRepository.save(node);
        
        // Copy parent permissions if inherit is true
        if (node.isInheritPermissions() && node.getParent() != null) {
            copyPermissions(node.getParent(), savedNode);
        }
        
        eventPublisher.publishEvent(new NodeCreatedEvent(savedNode, securityService.getCurrentUser()));
        
        return savedNode;
    }
    
    public Node getNode(UUID nodeId) {
        Node node = nodeRepository.findByIdAndDeletedFalse(nodeId)
            .orElseThrow(() -> new NoSuchElementException("Node not found: " + nodeId));
        node = normalizeExpiredLock(node);
        
        if (!securityService.hasPermission(node, PermissionType.READ)) {
            throw new SecurityException("No permission to read node: " + node.getName());
        }
        
        return node;
    }
    
    public Node getNodeByPath(String path) {
        Node node = nodeRepository.findFirstByPathAndDeletedFalseOrderByCreatedDateAsc(path)
            .orElseThrow(() -> new NoSuchElementException("Node not found at path: " + path));
        node = normalizeExpiredLock(node);
        
        if (!securityService.hasPermission(node, PermissionType.READ)) {
            throw new SecurityException("No permission to read node: " + node.getName());
        }
        
        return node;
    }
    
    public Page<Node> getChildren(UUID parentId, Pageable pageable) {
        Node parent = getNode(parentId);
        if (securityService.hasRole("ROLE_ADMIN")) {
            return nodeRepository.findByParentIdAndDeletedFalse(parentId, pageable);
        }

        List<Node> children = nodeRepository.findByParentIdAndDeletedFalse(parentId, pageable.getSort());
        List<Node> permitted = children.stream()
            .filter(child -> securityService.hasPermission(child, PermissionType.READ))
            .collect(Collectors.toList());

        return pageFromList(permitted, pageable);
    }

    private Page<Node> pageFromList(List<Node> nodes, Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return new PageImpl<>(nodes);
        }

        long offset = pageable.getOffset();
        if (offset >= nodes.size()) {
            return new PageImpl<>(List.of(), pageable, nodes.size());
        }

        int start = Math.toIntExact(offset);
        int end = Math.min(start + pageable.getPageSize(), nodes.size());
        return new PageImpl<>(nodes.subList(start, end), pageable, nodes.size());
    }
    
    public Node updateNode(UUID nodeId, Map<String, Object> updates) {
        Node node = getNode(nodeId);
        
        if (!securityService.hasPermission(node, PermissionType.WRITE)) {
            throw new SecurityException("No permission to update node: " + node.getName());
        }
        
        if (node.isEffectivelyLocked(LocalDateTime.now()) && !node.getLockedBy().equals(securityService.getCurrentUser())) {
            throw new IllegalStateException("Node is " + node.describeActiveLock(LocalDateTime.now()));
        }
        
        // Update allowed fields
        if (updates.containsKey("name")) {
            String newName = (String) updates.get("name");
            if (!node.getName().equals(newName)) {
                // Check for duplicate names
                UUID parentId = node.getParent() != null ? node.getParent().getId() : null;
                if (nodeRepository.findByParentIdAndName(parentId, newName).isPresent()) {
                    throw new IllegalArgumentException("Node with name already exists: " + newName);
                }
                node.setName(newName);
            }
        }
        
        if (updates.containsKey("description")) {
            node.setDescription((String) updates.get("description"));
        }
        
        if (updates.containsKey("properties")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) updates.get("properties");
            node.getProperties().putAll(properties);
        }
        
        if (updates.containsKey("metadata")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) updates.get("metadata");
            node.getMetadata().putAll(metadata);
        }

        if (updates.containsKey("correspondentId")) {
            Object value = updates.get("correspondentId");
            if (value == null || (value instanceof String str && str.isBlank())) {
                node.setCorrespondent(null);
            } else {
                UUID correspondentId;
                try {
                    correspondentId = UUID.fromString(value.toString());
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException("Invalid correspondentId: " + value, ex);
                }
                Correspondent correspondent = correspondentRepository.findById(correspondentId)
                    .orElseThrow(() -> new IllegalArgumentException("Correspondent not found: " + correspondentId));
                node.setCorrespondent(correspondent);
            }
        }
        
        // Content model enforcement: type + aspect after property merge
        enforceTypeProperties(node);
        enforceAspectProperties(node);

        Node updatedNode = nodeRepository.save(node);
        eventPublisher.publishEvent(new NodeUpdatedEvent(updatedNode, securityService.getCurrentUser()));

        // Trigger automation rules for document updates
        triggerRulesForDocument(updatedNode, TriggerType.DOCUMENT_UPDATED);

        return updatedNode;
    }

    public Node moveNode(UUID nodeId, UUID targetParentId) {
        Node node = getNode(nodeId);
        Folder targetParent = folderRepository.findById(targetParentId)
            .orElseThrow(() -> new IllegalArgumentException("Target parent not found: " + targetParentId));
        
        // Check permissions
        if (!securityService.hasPermission(node, PermissionType.DELETE)) {
            throw new SecurityException("No permission to move node: " + node.getName());
        }
        if (!securityService.hasPermission(targetParent, PermissionType.CREATE_CHILDREN)) {
            throw new SecurityException("No permission to create children in target folder");
        }
        
        // Check for circular reference
        if (isDescendant(targetParent, node)) {
            throw new IllegalArgumentException("Cannot move node to its own descendant");
        }
        
        // Check for duplicate names
        if (nodeRepository.findByParentIdAndName(targetParentId, node.getName()).isPresent()) {
            throw new IllegalArgumentException("Node with name already exists in target folder: " + node.getName());
        }
        
        Node oldParent = node.getParent();
        node.setParent(targetParent);
        
        Node movedNode = nodeRepository.save(node);

        eventPublisher.publishEvent(new NodeMovedEvent(
            movedNode, oldParent, targetParent, securityService.getCurrentUser()));

        // Trigger automation rules for document moves
        triggerRulesForDocument(movedNode, TriggerType.DOCUMENT_MOVED);

        return movedNode;
    }

    /**
     * Convenience helper to create a Document node with basic metadata.
     * Content storage should be handled separately via ContentService.
     */
    public Document createDocument(String name, String mimeType, long size, UUID parentId) {
        Document document = new Document();
        document.setName(name);
        document.setMimeType(mimeType);
        document.setFileSize(size);

        if (parentId != null) {
            Folder parent = folderRepository.findById(parentId)
                .orElseThrow(() -> new IllegalArgumentException("Parent folder not found: " + parentId));
            document.setParent(parent);
            document.setPath(parent.getPath() + "/" + name);
        } else {
            document.setPath("/" + name);
        }

        Node saved = createNode(document, parentId);
        return (Document) saved;
    }
    
    public Node copyNode(UUID nodeId, UUID targetParentId, String newName, boolean deep) {
        Node source = getNode(nodeId);
        Folder targetParent = folderRepository.findById(targetParentId)
            .orElseThrow(() -> new IllegalArgumentException("Target parent not found: " + targetParentId));
        
        // Check permissions
        if (!securityService.hasPermission(source, PermissionType.READ)) {
            throw new SecurityException("No permission to read source node: " + source.getName());
        }
        if (!securityService.hasPermission(targetParent, PermissionType.CREATE_CHILDREN)) {
            throw new SecurityException("No permission to create children in target folder");
        }
        
        String copyName = newName != null ? newName : source.getName() + " (Copy)";
        
        // Check for duplicate names
        if (nodeRepository.findByParentIdAndName(targetParentId, copyName).isPresent()) {
            throw new IllegalArgumentException("Node with name already exists: " + copyName);
        }
        
        Node copy = copyNodeRecursive(source, targetParent, copyName, deep);
        
        eventPublisher.publishEvent(new NodeCopiedEvent(copy, source, securityService.getCurrentUser()));
        
        return copy;
    }
    
    public void deleteNode(UUID nodeId, boolean permanent) {
        Node node = getNode(nodeId);
        
        if (!securityService.hasPermission(node, PermissionType.DELETE)) {
            throw new SecurityException("No permission to delete node: " + node.getName());
        }
        
        if (permanent) {
            deleteNodeRecursive(node);
        } else {
            softDeleteNodeRecursive(node);
        }
        
        eventPublisher.publishEvent(new NodeDeletedEvent(
            node, securityService.getCurrentUser(), permanent));
    }
    
    public void lockNode(UUID nodeId) {
        lockNode(nodeId, null, null);
    }

    public void lockNode(UUID nodeId, LockLifetime lifetime, Integer durationMinutes) {
        Node node = getNode(nodeId);
        node = normalizeExpiredLock(node);
        
        if (!securityService.hasPermission(node, PermissionType.WRITE)) {
            throw new SecurityException("No permission to lock node: " + node.getName());
        }
        
        if (node.isEffectivelyLocked(LocalDateTime.now())) {
            throw new IllegalStateException("Node is already " + node.describeActiveLock(LocalDateTime.now()));
        }

        LockLifetime effectiveLifetime = lifetime != null ? lifetime : LockLifetime.PERSISTENT;
        if (durationMinutes != null && durationMinutes <= 0) {
            throw new IllegalArgumentException("Lock duration must be positive");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = effectiveLifetime == LockLifetime.EPHEMERAL
            ? now.plusMinutes(durationMinutes != null ? durationMinutes : 30L)
            : null;

        node.applyLock(securityService.getCurrentUser(), now, effectiveLifetime, expiresAt);
        
        nodeRepository.save(node);
        eventPublisher.publishEvent(new NodeLockedEvent(node, securityService.getCurrentUser()));
    }
    
    public void unlockNode(UUID nodeId) {
        Node node = getNode(nodeId);
        node = normalizeExpiredLock(node);
        
        String currentUser = securityService.getCurrentUser();
        boolean isOwner = node.getLockedBy() != null && node.getLockedBy().equals(currentUser);
        boolean isAdmin = securityService.hasRole("ROLE_ADMIN");

        if (!node.isEffectivelyLocked(LocalDateTime.now())) {
            return;
        }
        
        if (!isOwner && !isAdmin) {
            throw new SecurityException("Only lock owner or admin can unlock node");
        }

        node.clearLock();
        
        nodeRepository.save(node);
        eventPublisher.publishEvent(new NodeUnlockedEvent(node, securityService.getCurrentUser()));
    }

    public LockInfoDto getLockInfo(UUID nodeId) {
        Node node = nodeRepository.findByIdAndDeletedFalse(nodeId)
            .orElseThrow(() -> new NoSuchElementException("Node not found: " + nodeId));
        if (!securityService.hasPermission(node, PermissionType.READ)) {
            throw new SecurityException("No permission to read node: " + node.getName());
        }
        LocalDateTime now = LocalDateTime.now();
        String currentUser = securityService.getCurrentUser();
        boolean isAdmin = securityService.hasRole("ROLE_ADMIN");

        if (node.isLockExpired(now)) {
            return new LockInfoDto(
                LockStatus.LOCK_EXPIRED,
                node.getLockedBy(),
                node.getLockedDate(),
                node.getLockLifetime(),
                node.getLockExpiresAt(),
                node.getLockType(),
                node.getLockAdditionalInfo(),
                node.isLockDeep(),
                0L,
                ageSeconds(node.getLockedDate(), now),
                false
            );
        }
        if (!node.isEffectivelyLocked(now)) {
            return new LockInfoDto(
                LockStatus.NO_LOCK,
                null, null, null, null, null, null, false,
                null, null, false
            );
        }

        boolean isOwner = Objects.equals(currentUser, node.getLockedBy());
        Long remainingSeconds = node.getLockExpiresAt() != null
            ? Math.max(0L, Duration.between(now, node.getLockExpiresAt()).getSeconds())
            : null;

        return new LockInfoDto(
            isOwner ? LockStatus.LOCK_OWNER : LockStatus.LOCKED_BY_OTHER,
            node.getLockedBy(),
            node.getLockedDate(),
            node.getLockLifetime(),
            node.getLockExpiresAt(),
            node.getLockType(),
            node.getLockAdditionalInfo(),
            node.isLockDeep(),
            remainingSeconds,
            ageSeconds(node.getLockedDate(), now),
            isOwner || isAdmin
        );
    }

    public CheckoutInfoDto getCheckoutInfo(UUID documentId) {
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new NoSuchElementException("Document not found: " + documentId));

        if (document.isDeleted()) {
            throw new NoSuchElementException("Document not found: " + documentId);
        }
        if (!securityService.hasPermission(document, PermissionType.READ)) {
            throw new SecurityException("No permission to read document: " + document.getName());
        }

        document = (Document) normalizeExpiredLock(document);

        String currentUser = securityService.getCurrentUser();
        boolean isAdmin = securityService.hasRole("ROLE_ADMIN");
        boolean canWrite = securityService.hasPermission(document, PermissionType.WRITE);
        boolean lockedByOther = document.isEffectivelyLocked(LocalDateTime.now())
            && !Objects.equals(document.getLockedBy(), currentUser);

        if (!document.isCheckedOut()) {
            String blockingReason = null;
            if (!canWrite) {
                blockingReason = "You do not have permission to check out this document.";
            } else if (lockedByOther) {
                blockingReason = "Cannot check out while " + document.describeActiveLock(LocalDateTime.now()) + ".";
            }
            return new CheckoutInfoDto(
                CheckoutStatus.AVAILABLE,
                null,
                null,
                null,
                canWrite && !lockedByOther,
                false,
                false,
                false,
                true,
                blockingReason
            );
        }

        boolean isOwner = Objects.equals(document.getCheckoutUser(), currentUser);
        String blockingReason = null;
        if (!isOwner) {
            blockingReason = document.getCheckoutUser() != null
                ? "Checked out by " + document.getCheckoutUser() + "."
                : "Checked out by another user.";
        }

        return new CheckoutInfoDto(
            isOwner ? CheckoutStatus.CHECKED_OUT_BY_YOU : CheckoutStatus.CHECKED_OUT_BY_OTHER,
            document.getCheckoutUser(),
            document.getCheckoutDate(),
            ageSeconds(document.getCheckoutDate(), LocalDateTime.now()),
            false,
            isOwner || isAdmin,
            isOwner || isAdmin,
            isOwner,
            true,
            blockingReason
        );
    }

    public Document checkoutDocument(UUID documentId) {
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new NoSuchElementException("Document not found: " + documentId));

        if (document.isDeleted()) {
            throw new NoSuchElementException("Document not found: " + documentId);
        }
        if (!securityService.hasPermission(document, PermissionType.WRITE)) {
            throw new SecurityException("No permission to check out document: " + document.getName());
        }
        document = (Document) normalizeExpiredLock(document);
        if (document.isEffectivelyLocked(LocalDateTime.now()) && !Objects.equals(document.getLockedBy(), securityService.getCurrentUser())) {
            throw new IllegalStateException("Document is " + document.describeActiveLock(LocalDateTime.now()));
        }
        if (document.isCheckedOut()) {
            throw new IllegalStateException("Document is already checked out by: " + document.getCheckoutUser());
        }

        document.checkout(securityService.getCurrentUser());
        return documentRepository.save(document);
    }

    public Document checkinDocument(UUID documentId) {
        return checkinDocument(documentId, false);
    }

    public Document checkinDocument(UUID documentId, boolean keepCheckedOut) {
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new NoSuchElementException("Document not found: " + documentId));

        if (document.isDeleted()) {
            throw new NoSuchElementException("Document not found: " + documentId);
        }
        String currentUser = securityService.getCurrentUser();
        boolean isAdmin = securityService.hasRole("ROLE_ADMIN");
        if (!document.isCheckedOut()) {
            throw new IllegalStateException("Document is not checked out");
        }
        if (!Objects.equals(document.getCheckoutUser(), currentUser) && !isAdmin) {
            throw new SecurityException("Only checkout owner or admin can check in document");
        }
        if (keepCheckedOut && !Objects.equals(document.getCheckoutUser(), currentUser)) {
            throw new SecurityException("Only checkout owner can keep document checked out");
        }

        if (keepCheckedOut) {
            document.checkout(currentUser);
        } else {
            document.checkin();
        }
        return documentRepository.save(document);
    }

    private Node normalizeExpiredLock(Node node) {
        if (node.isLockExpired(LocalDateTime.now())) {
            node.clearLock();
            return nodeRepository.save(node);
        }
        return node;
    }

    private Long ageSeconds(LocalDateTime lockedDate, LocalDateTime now) {
        if (lockedDate == null) {
            return null;
        }
        return Math.max(0L, Duration.between(lockedDate, now).getSeconds());
    }

    public Document cancelCheckoutDocument(UUID documentId) {
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new NoSuchElementException("Document not found: " + documentId));

        if (document.isDeleted()) {
            throw new NoSuchElementException("Document not found: " + documentId);
        }
        String currentUser = securityService.getCurrentUser();
        boolean isAdmin = securityService.hasRole("ROLE_ADMIN");
        if (!document.isCheckedOut()) {
            throw new IllegalStateException("Document is not checked out");
        }
        if (!Objects.equals(document.getCheckoutUser(), currentUser) && !isAdmin) {
            throw new SecurityException("Only checkout owner or admin can cancel checkout");
        }

        document.checkin();
        return documentRepository.save(document);
    }
    
    // ---- aspect management --------------------------------------------------

    public Set<String> getAspects(UUID nodeId) {
        Node node = getNode(nodeId);
        return node.getAspects() != null ? new HashSet<>(node.getAspects()) : new HashSet<>();
    }

    public Node addAspect(UUID nodeId, String aspectName) {
        return addAspect(nodeId, aspectName, null);
    }

    public Node addAspect(UUID nodeId, String aspectName, Map<String, Object> aspectProperties) {
        Node node = getNode(nodeId);
        if (!securityService.hasPermission(node, PermissionType.WRITE)) {
            throw new SecurityException("No permission to modify node: " + node.getName());
        }
        node.addAspect(aspectName);
        // merge caller-supplied properties before defaults (caller overrides defaults)
        if (aspectProperties != null && !aspectProperties.isEmpty()) {
            if (node.getProperties() == null) {
                node.setProperties(new HashMap<>());
            }
            node.getProperties().putAll(aspectProperties);
        }
        applyAspectDefaults(node, aspectName);
        enforceAspectProperties(node);
        return nodeRepository.save(node);
    }

    public Node removeAspect(UUID nodeId, String aspectName) {
        Node node = getNode(nodeId);
        if (!securityService.hasPermission(node, PermissionType.WRITE)) {
            throw new SecurityException("No permission to modify node: " + node.getName());
        }
        node.removeAspect(aspectName);
        // remove aspect-specific properties from the JSONB properties map
        // (prefixed properties like "cm:title" belong to the aspect)
        if (node.getProperties() != null) {
            node.getProperties().entrySet().removeIf(e -> e.getKey().startsWith(aspectName.split(":")[0] + ":"));
        }
        return nodeRepository.save(node);
    }

    public boolean hasAspect(UUID nodeId, String aspectName) {
        Node node = getNode(nodeId);
        return node.hasAspect(aspectName);
    }

    // ---- content model enforcement -----------------------------------------

    /**
     * Validate node properties against the node's content-model type definition.
     * Enforces mandatory properties and constraint rules. Applies defaults first.
     * Silently skips if no typeQName is set or the type has no registered definition.
     */
    void enforceTypeProperties(Node node) {
        if (dictionaryService == null || propertyConstraintValidator == null) return;
        if (node.getTypeQName() == null || node.getTypeQName().isBlank()) return;

        List<com.ecm.core.entity.PropertyDefinition> defs;
        try {
            defs = dictionaryService.getPropertiesForType(node.getTypeQName());
        } catch (Exception e) {
            return; // unmanaged type
        }

        // apply defaults
        if (node.getProperties() == null) {
            node.setProperties(new HashMap<>());
        }
        for (com.ecm.core.entity.PropertyDefinition def : defs) {
            String key = def.qualifiedName();
            if (!node.getProperties().containsKey(key) && def.getDefaultValue() != null) {
                node.getProperties().put(key, def.getDefaultValue());
            }
        }

        // validate
        Map<String, Object> props = node.getProperties();
        List<String> violations = new ArrayList<>();
        for (com.ecm.core.entity.PropertyDefinition def : defs) {
            String key = def.qualifiedName();
            Object value = props.get(key);

            if (def.isMandatory() && (value == null || (value instanceof String s && s.isBlank()))) {
                violations.add("Missing mandatory property '" + key + "' for type " + node.getTypeQName());
            }
            if (value != null && def.getConstraints() != null && !def.getConstraints().isEmpty()) {
                violations.addAll(propertyConstraintValidator.validate(value, def.getConstraints()));
            }
        }
        if (!violations.isEmpty()) {
            throw new PropertyValidationException(
                "Type property validation failed: " + String.join("; ", violations),
                violations
            );
        }
    }

    /**
     * Apply mandatory aspects declared on the node's type definition.
     * Adds the aspects to the node and applies their defaults.
     */
    void applyMandatoryAspects(Node node) {
        if (dictionaryService == null) return;
        if (node.getTypeQName() == null || node.getTypeQName().isBlank()) return;

        List<String> mandatoryAspects;
        try {
            mandatoryAspects = dictionaryService.getMandatoryAspectsForType(node.getTypeQName());
        } catch (Exception e) {
            return;
        }
        for (String aspectName : mandatoryAspects) {
            if (!node.hasAspect(aspectName)) {
                node.addAspect(aspectName);
                applyAspectDefaults(node, aspectName);
            }
        }
    }

    /**
     * Validate node properties against all attached aspect definitions.
     * Enforces mandatory properties and constraint rules from active models.
     * Silently skips aspects that have no registered definition (unmanaged aspects).
     */
    void enforceAspectProperties(Node node) {
        if (dictionaryService == null || propertyConstraintValidator == null) return;
        if (node.getAspects() == null || node.getAspects().isEmpty()) return;

        Map<String, Object> props = node.getProperties() != null ? node.getProperties() : Map.of();
        List<String> violations = new ArrayList<>();

        for (String aspectName : node.getAspects()) {
            List<com.ecm.core.entity.PropertyDefinition> defs;
            try {
                defs = dictionaryService.getPropertiesForAspect(aspectName);
            } catch (Exception e) {
                // unmanaged aspect — no definition registered, skip
                continue;
            }
            for (com.ecm.core.entity.PropertyDefinition def : defs) {
                String key = def.qualifiedName();
                Object value = props.get(key);

                // mandatory check
                if (def.isMandatory() && (value == null || (value instanceof String s && s.isBlank()))) {
                    violations.add("Missing mandatory property '" + key + "' for aspect " + aspectName);
                }

                // constraint check
                if (value != null && def.getConstraints() != null && !def.getConstraints().isEmpty()) {
                    violations.addAll(propertyConstraintValidator.validate(value, def.getConstraints()));
                }
            }
        }

        if (!violations.isEmpty()) {
            throw new PropertyValidationException(
                "Property validation failed: " + String.join("; ", violations),
                violations
            );
        }
    }

    /**
     * Apply default property values from an aspect definition to the node.
     * Only sets defaults for properties that are not already present.
     */
    void applyAspectDefaults(Node node, String aspectName) {
        if (dictionaryService == null) return;
        List<com.ecm.core.entity.PropertyDefinition> defs;
        try {
            defs = dictionaryService.getPropertiesForAspect(aspectName);
        } catch (Exception e) {
            return; // unmanaged aspect
        }
        if (node.getProperties() == null) {
            node.setProperties(new HashMap<>());
        }
        for (com.ecm.core.entity.PropertyDefinition def : defs) {
            String key = def.qualifiedName();
            if (!node.getProperties().containsKey(key) && def.getDefaultValue() != null) {
                node.getProperties().put(key, def.getDefaultValue());
            }
        }
    }

    public List<Node> searchNodes(String query, Map<String, Object> filters, Pageable pageable) {
        // This is a simplified search - in production, use Elasticsearch
        return nodeRepository.findAll((root, criteriaQuery, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            
            // Text search
            if (query != null && !query.isEmpty()) {
                predicates.add(criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), 
                        "%" + query.toLowerCase() + "%"),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), 
                        "%" + query.toLowerCase() + "%")
                ));
            }
            
            // Apply filters
            if (filters != null) {
                if (filters.containsKey("mimeType")) {
                    predicates.add(criteriaBuilder.equal(root.get("mimeType"), 
                        filters.get("mimeType")));
                }
                if (filters.containsKey("createdBy")) {
                    predicates.add(criteriaBuilder.equal(root.get("createdBy"), 
                        filters.get("createdBy")));
                }
                if (filters.containsKey("status")) {
                    predicates.add(criteriaBuilder.equal(root.get("status"), 
                        NodeStatus.valueOf((String) filters.get("status"))));
                }
            }
            
            predicates.add(criteriaBuilder.equal(root.get("deleted"), false));
            
            return criteriaBuilder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        }, pageable).getContent();
    }
    
    private void copyPermissions(Node source, Node target) {
        List<Permission> sourcePermissions = permissionRepository.findByNodeId(source.getId());
        for (Permission perm : sourcePermissions) {
            Permission copy = new Permission();
            copy.setNode(target);
            copy.setAuthority(perm.getAuthority());
            copy.setAuthorityType(perm.getAuthorityType());
            copy.setPermission(perm.getPermission());
            copy.setAllowed(perm.isAllowed());
            copy.setInherited(true);
            permissionRepository.save(copy);
        }
    }
    
    private boolean isDescendant(Node node, Node potentialAncestor) {
        Node current = node;
        while (current != null) {
            if (current.getId().equals(potentialAncestor.getId())) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }
    
    private Node copyNodeRecursive(Node source, Node targetParent, String name, boolean deep) {
        Node copy;
        
        if (source instanceof Document) {
            Document docSource = (Document) source;
            Document docCopy = new Document();
            docCopy.setName(name);
            docCopy.setDescription(docSource.getDescription());
            docCopy.setMimeType(docSource.getMimeType());
            docCopy.setFileSize(docSource.getFileSize());
            docCopy.setContentId(docSource.getContentId());
            docCopy.setContentHash(docSource.getContentHash());
            copy = docCopy;
        } else {
            Folder folderCopy = new Folder();
            folderCopy.setName(name);
            folderCopy.setDescription(source.getDescription());
            copy = folderCopy;
        }
        
        copy.setParent(targetParent);
        copy.getProperties().putAll(source.getProperties());
        copy.getMetadata().putAll(source.getMetadata());
        
        copy = nodeRepository.save(copy);
        
        // Copy permissions
        copyPermissions(source, copy);
        
        // Copy children if deep copy and source is folder
        if (deep && source instanceof Folder) {
            List<Node> children = nodeRepository.findByParentIdAndDeletedFalse(source.getId());
            for (Node child : children) {
                copyNodeRecursive(child, (Folder) copy, child.getName(), true);
            }
        }
        
        return copy;
    }
    
    private void deleteNodeRecursive(Node node) {
        // Delete children first
        List<Node> children = nodeRepository.findByParentIdAndDeletedFalse(node.getId());
        for (Node child : children) {
            deleteNodeRecursive(child);
        }
        
        // Delete permissions
        permissionRepository.deleteByNodeId(node.getId());
        
        // Delete node
        nodeRepository.delete(node);
    }
    
    private void softDeleteNodeRecursive(Node node) {
        String currentUser = securityService.getCurrentUser();
        LocalDateTime deletedAt = LocalDateTime.now();
        nodeRepository.softDeleteByPathPrefix(node.getPath(), deletedAt, currentUser);
    }

    /**
     * Trigger automation rules for a document.
     * Only triggers for Document nodes, not Folders.
     * Catches all exceptions to avoid failing the main operation.
     */
    private void triggerRulesForDocument(Node node, TriggerType triggerType) {
        if (!rulesEnabled) {
            return;
        }

        if (!(node instanceof Document)) {
            return;
        }

        try {
            Document document = (Document) node;
            log.debug("Triggering {} rules for document: {} ({})",
                triggerType, document.getName(), document.getId());

            ruleEngineService.evaluateAndExecute(document, triggerType);
        } catch (Exception e) {
            // Log but don't fail the main operation
            log.error("Failed to trigger {} rules for node {}: {}",
                triggerType, node.getId(), e.getMessage(), e);
        }
    }
}
