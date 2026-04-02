package com.ecm.core.controller;

import com.ecm.core.dto.AddAspectRequest;
import com.ecm.core.dto.CheckoutInfoDto;
import com.ecm.core.dto.CreateNodeRequest;
import com.ecm.core.dto.LockInfoDto;
import com.ecm.core.dto.NodeDto;
import com.ecm.core.dto.UpdateNodeRequest;
import com.ecm.core.dto.VersionDto;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.DocumentRelation;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.LockLifetime;
import com.ecm.core.entity.LockType;
import com.ecm.core.entity.RenditionResource;
import com.ecm.core.service.DocumentRelationService;
import com.ecm.core.service.LockService;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.RenditionResourceService;
import com.ecm.core.service.VersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/api/nodes", "/api/v1/nodes"})
@RequiredArgsConstructor
@Tag(name = "Node Management", description = "APIs for managing nodes (files and folders)")
public class NodeController {

    private static final int DEFAULT_RELATIONS_PAGE_SIZE = 20;
    private static final int MAX_RELATIONS_PAGE_SIZE = 200;
    private static final int MAX_RELATIONS_PARENT_DEPTH = 100;

    private final NodeService nodeService;
    private final DocumentRelationService relationService;
    private final VersionService versionService;
    private final RenditionResourceService renditionResourceService;
    private final LockService lockService;

    @GetMapping("/{nodeId}")
    @Operation(summary = "Get node by ID", description = "Retrieve a node by its ID")
    public ResponseEntity<NodeDto> getNode(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId) {
        Node node = nodeService.getNode(nodeId);
        return ResponseEntity.ok(toNodeDto(node));
    }

    @GetMapping("/path")
    @Operation(summary = "Get node by path", description = "Retrieve a node by its path")
    public ResponseEntity<NodeDto> getNodeByPath(
            @Parameter(description = "Node path") @RequestParam String path) {
        Node node = nodeService.getNodeByPath(path);
        return ResponseEntity.ok(toNodeDto(node));
    }

    @GetMapping("/{nodeId}/children")
    @Operation(summary = "Get node children", description = "List children of a node")
    public ResponseEntity<Page<NodeDto>> getChildren(
            @Parameter(description = "Parent node ID") @PathVariable UUID nodeId,
            Pageable pageable) {
        Page<Node> children = nodeService.getChildren(nodeId, pageable);
        return ResponseEntity.ok(children.map(this::toNodeDto));
    }

    @GetMapping("/{nodeId}/lock-info")
    @Operation(summary = "Get node lock info", description = "Retrieve caller-relative lock status and timing details for a node")
    public ResponseEntity<LockInfoDto> getLockInfo(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId) {
        return ResponseEntity.ok(nodeService.getLockInfo(nodeId));
    }

    @GetMapping("/{nodeId}/relations/summary")
    @Operation(
        summary = "Get node relations summary",
        description = "Get parent/child/source/target/version/rendition relation summary for a node."
    )
    public ResponseEntity<NodeRelationsSummaryDto> getNodeRelationsSummary(
        @Parameter(description = "Node ID") @PathVariable UUID nodeId
    ) {
        Node node = nodeService.getNode(nodeId);
        int parentCount = collectParentChain(node, MAX_RELATIONS_PARENT_DEPTH).size();
        long childCount = nodeService.getChildren(nodeId, PageRequest.of(0, 1)).getTotalElements();

        long sourceRelationCount = 0L;
        long targetRelationCount = 0L;
        long versionCount = 0L;
        boolean checkedOut = false;
        String checkoutUser = null;
        LocalDateTime checkoutDate = null;
        RenditionResourceService.RenditionSummary renditionSummary = RenditionResourceService.RenditionSummary.empty(nodeId);

        if (node instanceof Document document) {
            sourceRelationCount = relationService.getOutgoingRelationsPage(nodeId, PageRequest.of(0, 1), null)
                .getTotalElements();
            targetRelationCount = relationService.getIncomingRelationsPage(nodeId, PageRequest.of(0, 1), null)
                .getTotalElements();
            versionCount = versionService.getVersionHistory(nodeId, PageRequest.of(0, 1), false).getTotalElements();
            renditionSummary = renditionResourceService.summarizeDocument(document);
            checkedOut = document.isCheckedOut();
            checkoutUser = document.getCheckoutUser();
            checkoutDate = document.getCheckoutDate();
        }

        return ResponseEntity.ok(new NodeRelationsSummaryDto(
            nodeId,
            node.getNodeType().name(),
            parentCount,
            childCount,
            sourceRelationCount,
            targetRelationCount,
            versionCount,
            renditionSummary.previewStatus(),
            renditionSummary.renditionAvailable(),
            checkedOut,
            checkoutUser,
            checkoutDate
        ));
    }

    @GetMapping("/{nodeId}/relations/parents")
    @Operation(
        summary = "Get node parent chain",
        description = "Return ancestor chain from root to direct parent."
    )
    public ResponseEntity<List<NodeRelationNodeRefDto>> getNodeRelationParents(
        @Parameter(description = "Node ID") @PathVariable UUID nodeId,
        @RequestParam(defaultValue = "20") int maxDepth
    ) {
        Node node = nodeService.getNode(nodeId);
        int boundedDepth = clamp(maxDepth, 1, MAX_RELATIONS_PARENT_DEPTH);
        List<Node> parents = collectParentChain(node, boundedDepth);
        Collections.reverse(parents);
        return ResponseEntity.ok(parents.stream().map(this::toNodeRef).toList());
    }

    @GetMapping("/{nodeId}/relations/children")
    @Operation(
        summary = "Get node relation children",
        description = "List children as relation graph neighbors with optional query/nodeType filters."
    )
    public ResponseEntity<Page<NodeRelationNodeRefDto>> getNodeRelationChildren(
        @Parameter(description = "Node ID") @PathVariable UUID nodeId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String query,
        @RequestParam(required = false) String nodeType
    ) {
        nodeService.getNode(nodeId);
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = clamp(size, 1, MAX_RELATIONS_PAGE_SIZE);
        List<Node> allChildren = nodeService.getChildren(nodeId, Pageable.unpaged()).getContent();
        List<Node> filtered = allChildren.stream()
            .filter(node -> matchNodeQuery(node, query))
            .filter(node -> matchNodeType(node, nodeType))
            .toList();
        return ResponseEntity.ok(paginate(filtered, normalizedPage, normalizedSize).map(this::toNodeRef));
    }

    @GetMapping("/{nodeId}/relations/sources")
    @Operation(
        summary = "Get source relations for node",
        description = "List incoming document relations where current node is relation target."
    )
    public ResponseEntity<Page<NodeRelationEdgeDto>> getNodeRelationSources(
        @Parameter(description = "Node ID") @PathVariable UUID nodeId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String relationType
    ) {
        Node node = nodeService.getNode(nodeId);
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = clamp(size, 1, MAX_RELATIONS_PAGE_SIZE);
        Pageable pageable = PageRequest.of(normalizedPage, normalizedSize);
        if (!(node instanceof Document)) {
            return ResponseEntity.ok(Page.empty(pageable));
        }
        Page<DocumentRelation> relations = relationService.getIncomingRelationsPage(nodeId, pageable, relationType);
        return ResponseEntity.ok(relations.map(this::toRelationEdge));
    }

    @GetMapping("/{nodeId}/relations/targets")
    @Operation(
        summary = "Get target relations for node",
        description = "List outgoing document relations where current node is relation source."
    )
    public ResponseEntity<Page<NodeRelationEdgeDto>> getNodeRelationTargets(
        @Parameter(description = "Node ID") @PathVariable UUID nodeId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String relationType
    ) {
        Node node = nodeService.getNode(nodeId);
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = clamp(size, 1, MAX_RELATIONS_PAGE_SIZE);
        Pageable pageable = PageRequest.of(normalizedPage, normalizedSize);
        if (!(node instanceof Document)) {
            return ResponseEntity.ok(Page.empty(pageable));
        }
        Page<DocumentRelation> relations = relationService.getOutgoingRelationsPage(nodeId, pageable, relationType);
        return ResponseEntity.ok(relations.map(this::toRelationEdge));
    }

    @GetMapping("/{nodeId}/relations/versions")
    @Operation(
        summary = "Get version relations for node",
        description = "List version history as relation neighbors with pagination and majorOnly filter."
    )
    public ResponseEntity<Page<VersionDto>> getNodeRelationVersions(
        @Parameter(description = "Node ID") @PathVariable UUID nodeId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "false") boolean majorOnly
    ) {
        Node node = nodeService.getNode(nodeId);
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = clamp(size, 1, MAX_RELATIONS_PAGE_SIZE);
        Pageable pageable = PageRequest.of(normalizedPage, normalizedSize);
        if (!(node instanceof Document)) {
            return ResponseEntity.ok(Page.empty(pageable));
        }
        return ResponseEntity.ok(versionService.getVersionHistory(nodeId, pageable, majorOnly).map(VersionDto::from));
    }

    @GetMapping("/{nodeId}/relations/checkout")
    @Operation(
        summary = "Get checkout relation for node",
        description = "Expose caller-relative checkout relation metadata for a document node without requiring a separate working-copy entity."
    )
    public ResponseEntity<NodeCheckoutRelationDto> getNodeRelationCheckout(
        @Parameter(description = "Node ID") @PathVariable UUID nodeId
    ) {
        Node node = nodeService.getNode(nodeId);
        if (!(node instanceof Document document)) {
            return ResponseEntity.ok(new NodeCheckoutRelationDto(
                nodeId,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                false,
                false,
                null
            ));
        }

        CheckoutInfoDto checkoutInfo = nodeService.getCheckoutInfo(nodeId);
        return ResponseEntity.ok(new NodeCheckoutRelationDto(
            nodeId,
            true,
            document.isCheckedOut(),
            document.getCheckoutUser(),
            document.getCheckoutDate(),
            document.getCheckoutBaselineVersionId(),
            document.getCheckoutBaselineVersionLabel(),
            document.getVersionLabel(),
            checkoutInfo.canCheckout(),
            checkoutInfo.canCheckIn(),
            checkoutInfo.canCancelCheckout(),
            checkoutInfo.canKeepCheckedOut(),
            checkoutInfo.requiresNewVersionFile(),
            checkoutInfo.blockingReason()
        ));
    }

    @GetMapping("/{nodeId}/relations/checkout-graph")
    @Operation(
        summary = "Get checkout graph for node",
        description = "Expose a virtual working-copy graph for a checked-out document without requiring a persisted working-copy entity."
    )
    public ResponseEntity<NodeCheckoutGraphDto> getNodeRelationCheckoutGraph(
        @Parameter(description = "Node ID") @PathVariable UUID nodeId
    ) {
        Node node = nodeService.getNode(nodeId);
        if (!(node instanceof Document document)) {
            return ResponseEntity.ok(new NodeCheckoutGraphDto(
                nodeId,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                false,
                false,
                false,
                null
            ));
        }

        CheckoutInfoDto checkoutInfo = nodeService.getCheckoutInfo(nodeId);
        NodeCheckoutGraphNodeDto documentNode = toCheckoutGraphNode(
            nodeId.toString(),
            "DOCUMENT",
            document.getName(),
            true,
            false,
            true
        );
        NodeCheckoutGraphNodeDto destinationNode = document.getParent() != null
            ? toCheckoutGraphNode(
                document.getParent().getId().toString(),
                "DESTINATION_FOLDER",
                firstNonBlank(document.getParent().getPath(), document.getParent().getName()),
                false,
                false,
                true
            )
            : null;
        VersionDto currentVersion = document.getCurrentVersion() != null
            ? VersionDto.from(document.getCurrentVersion())
            : null;

        if (!document.isCheckedOut()) {
            List<NodeCheckoutGraphEdgeDto> edges = new ArrayList<>();
            if (currentVersion != null && currentVersion.id() != null) {
                edges.add(new NodeCheckoutGraphEdgeDto(
                    "DOCUMENT_CURRENT_VERSION",
                    nodeId.toString(),
                    currentVersion.id().toString(),
                    "current persisted version"
                ));
            }
            List<NodeCheckoutGraphNodeDto> nodes = buildCheckoutGraphNodes(
                documentNode,
                null,
                destinationNode,
                null,
                currentVersion
            );
            return ResponseEntity.ok(new NodeCheckoutGraphDto(
                nodeId,
                true,
                false,
                null,
                null,
                documentNode,
                null,
                destinationNode,
                null,
                currentVersion,
                nodes,
                edges,
                checkoutInfo.canCheckIn(),
                checkoutInfo.canCancelCheckout(),
                checkoutInfo.canKeepCheckedOut(),
                checkoutInfo.blockingReason()
            ));
        }

        String workingCopyId = "working-copy:" + nodeId;
        VersionDto baselineVersion = resolveVersionDto(document.getCheckoutBaselineVersionId());
        NodeCheckoutGraphNodeDto workingCopyNode = toCheckoutGraphNode(
            workingCopyId,
            "WORKING_COPY",
            document.getCheckoutUser() != null && !document.getCheckoutUser().isBlank()
                ? document.getCheckoutUser() + " working copy"
                : "Working copy",
            false,
            true,
            true
        );
        List<NodeCheckoutGraphEdgeDto> edges = new ArrayList<>();
        edges.add(new NodeCheckoutGraphEdgeDto(
            "HAS_WORKING_COPY",
            nodeId.toString(),
            workingCopyId,
            "active checkout"
        ));
        if (baselineVersion != null && baselineVersion.id() != null) {
            edges.add(new NodeCheckoutGraphEdgeDto(
                "WORKING_COPY_BASELINE",
                workingCopyId,
                baselineVersion.id().toString(),
                "baseline version"
            ));
        }
        if (currentVersion != null && currentVersion.id() != null) {
            edges.add(new NodeCheckoutGraphEdgeDto(
                "WORKING_COPY_CURRENT",
                workingCopyId,
                currentVersion.id().toString(),
                "current version"
            ));
        }
        if (destinationNode != null) {
            edges.add(new NodeCheckoutGraphEdgeDto(
                "CHECKIN_DESTINATION",
                workingCopyId,
                destinationNode.id(),
                "check-in target"
            ));
        }
        if (currentVersion != null && currentVersion.id() != null) {
            edges.add(new NodeCheckoutGraphEdgeDto(
                "DOCUMENT_CURRENT_VERSION",
                nodeId.toString(),
                currentVersion.id().toString(),
                "current persisted version"
            ));
        }
        if (baselineVersion != null && baselineVersion.id() != null) {
            edges.add(new NodeCheckoutGraphEdgeDto(
                "CHECKOUT_BASELINE_VERSION",
                nodeId.toString(),
                baselineVersion.id().toString(),
                "checkout source version"
            ));
        }
        List<NodeCheckoutGraphNodeDto> nodes = buildCheckoutGraphNodes(
            documentNode,
            workingCopyNode,
            destinationNode,
            baselineVersion,
            currentVersion
        );

        return ResponseEntity.ok(new NodeCheckoutGraphDto(
            nodeId,
            true,
            true,
            document.getCheckoutUser(),
            document.getCheckoutDate(),
            documentNode,
            workingCopyNode,
            destinationNode,
            baselineVersion,
            currentVersion,
            nodes,
            edges,
            checkoutInfo.canCheckIn(),
            checkoutInfo.canCancelCheckout(),
            checkoutInfo.canKeepCheckedOut(),
            checkoutInfo.blockingReason()
        ));
    }

    @GetMapping("/{nodeId}/relations/renditions")
    @Operation(
        summary = "Get renditions for node",
        description = "List virtual rendition resources (preview and thumbnail) for a document node."
    )
    public ResponseEntity<Page<NodeRenditionRelationDto>> getNodeRelationRenditions(
        @Parameter(description = "Node ID") @PathVariable UUID nodeId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Node node = nodeService.getNode(nodeId);
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = clamp(size, 1, MAX_RELATIONS_PAGE_SIZE);
        Pageable pageable = PageRequest.of(normalizedPage, normalizedSize);
        if (!(node instanceof Document document)) {
            return ResponseEntity.ok(Page.empty(pageable));
        }
        return ResponseEntity.ok(paginate(buildRenditionRelations(document), normalizedPage, normalizedSize));
    }

    @GetMapping("/{nodeId}/relations/renditions/{renditionId}")
    @Operation(
        summary = "Get rendition for node",
        description = "Return a single virtual rendition resource (preview or thumbnail) for a document node."
    )
    public ResponseEntity<NodeRenditionRelationDto> getNodeRelationRendition(
        @Parameter(description = "Node ID") @PathVariable UUID nodeId,
        @Parameter(description = "Rendition ID") @PathVariable String renditionId
    ) {
        Node node = nodeService.getNode(nodeId);
        if (!(node instanceof Document document)) {
            return ResponseEntity.notFound().build();
        }
        return resolveRendition(document, renditionId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{nodeId}/relations/renditions/summary")
    @Operation(
        summary = "Get rendition relation summary for node",
        description = "Return rendition readiness summary and preview failure classification for a document node."
    )
    public ResponseEntity<NodeRenditionRelationSummaryDto> getNodeRenditionRelationSummary(
        @Parameter(description = "Node ID") @PathVariable UUID nodeId
    ) {
        Node node = nodeService.getNode(nodeId);
        if (!(node instanceof Document document)) {
            return ResponseEntity.ok(new NodeRenditionRelationSummaryDto(
                nodeId,
                false,
                null,
                false,
                null,
                null,
                null,
                null
            ));
        }
        RenditionResourceService.RenditionSummary summary = renditionResourceService.summarizeDocument(document);

        return ResponseEntity.ok(new NodeRenditionRelationSummaryDto(
            nodeId,
            summary.document(),
            summary.previewStatus(),
            summary.renditionAvailable(),
            summary.previewFailureReason(),
            summary.previewFailureCategory(),
            summary.previewLastUpdated(),
            summary.currentVersionLabel()
        ));
    }

    @PostMapping
    @Operation(summary = "Create node", description = "Create a new node from a typed request")
    public ResponseEntity<NodeDto> createNode(
            @Valid @RequestBody CreateNodeRequest request,
            @Parameter(description = "Parent node ID") @RequestParam(required = false) UUID parentId) {
        Node node;
        if ("FOLDER".equalsIgnoreCase(request.nodeType())) {
            Folder folder = new Folder();
            folder.setName(request.name());
            folder.setDescription(request.description());
            node = folder;
        } else {
            Document doc = new Document();
            doc.setName(request.name());
            doc.setDescription(request.description());
            doc.setMimeType(request.mimeType() != null ? request.mimeType() : "application/octet-stream");
            node = doc;
        }
        if (request.properties() != null) {
            node.setProperties(new java.util.HashMap<>(request.properties()));
        }
        if (request.metadata() != null) {
            node.setMetadata(new java.util.HashMap<>(request.metadata()));
        }
        if (request.typeQName() != null && !request.typeQName().isBlank()) {
            node.setTypeQName(request.typeQName());
        }
        if (request.aspects() != null) {
            for (String aspect : request.aspects()) {
                node.addAspect(aspect);
            }
        }
        Node created = nodeService.createNode(node, parentId);
        return ResponseEntity.status(HttpStatus.CREATED).body(toNodeDto(created));
    }

    @PatchMapping("/{nodeId}")
    @Operation(summary = "Update node", description = "Update node properties via typed request")
    public ResponseEntity<NodeDto> updateNode(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId,
            @Valid @RequestBody UpdateNodeRequest request) {
        Map<String, Object> updates = new java.util.HashMap<>();
        if (request.name() != null) updates.put("name", request.name());
        if (request.description() != null) updates.put("description", request.description());
        if (request.properties() != null) updates.put("properties", request.properties());
        if (request.metadata() != null) updates.put("metadata", request.metadata());
        if (request.correspondentId() != null) updates.put("correspondentId", request.correspondentId());
        Node updated = nodeService.updateNode(nodeId, updates);
        return ResponseEntity.ok(toNodeDto(updated));
    }

    @PostMapping("/{nodeId}/move")
    @Operation(summary = "Move node", description = "Move a node to a different parent")
    public ResponseEntity<NodeDto> moveNode(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId,
            @Parameter(description = "Target parent ID") @RequestParam UUID targetParentId) {
        Node moved = nodeService.moveNode(nodeId, targetParentId);
        return ResponseEntity.ok(toNodeDto(moved));
    }

    @PostMapping("/{nodeId}/copy")
    @Operation(summary = "Copy node", description = "Copy a node to a different location")
    public ResponseEntity<NodeDto> copyNode(
            @Parameter(description = "Source node ID") @PathVariable UUID nodeId,
            @Parameter(description = "Target parent ID") @RequestParam UUID targetParentId,
            @Parameter(description = "New name") @RequestParam(required = false) String newName,
            @Parameter(description = "Deep copy") @RequestParam(defaultValue = "true") boolean deep) {
        Node copied = nodeService.copyNode(nodeId, targetParentId, newName, deep);
        return ResponseEntity.status(HttpStatus.CREATED).body(toNodeDto(copied));
    }

    @DeleteMapping("/{nodeId}")
    @Operation(summary = "Delete node", description = "Delete a node")
    public ResponseEntity<Void> deleteNode(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId,
            @Parameter(description = "Permanent delete") @RequestParam(defaultValue = "false") boolean permanent) {
        nodeService.deleteNode(nodeId, permanent);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{nodeId}/lock")
    @Operation(summary = "Lock node", description = "Lock a node to prevent modifications")
    public ResponseEntity<Void> lockNode(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId,
            @Parameter(description = "Lock lifetime") @RequestParam(required = false) LockLifetime lifetime,
            @Parameter(description = "Ephemeral lock duration in minutes") @RequestParam(required = false) Integer durationMinutes) {
        nodeService.lockNode(nodeId, lifetime, durationMinutes);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{nodeId}/unlock")
    @Operation(summary = "Unlock node", description = "Unlock a locked node")
    public ResponseEntity<Void> unlockNode(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId) {
        nodeService.unlockNode(nodeId);
        return ResponseEntity.ok().build();
    }

    // ---- enhanced lock endpoints -----------------------------------------

    @PostMapping("/{nodeId}/lock-typed")
    @Operation(summary = "Lock node with type",
               description = "Lock a node specifying lock type (WRITE_LOCK, READ_ONLY_LOCK, NODE_LOCK), "
                   + "lifetime, duration, deep (recursive), and optional additional info.")
    public ResponseEntity<LockInfoDto> lockTyped(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId,
            @Parameter(description = "Lock type") @RequestParam(defaultValue = "WRITE_LOCK") LockType lockType,
            @Parameter(description = "Lock lifetime") @RequestParam(defaultValue = "PERSISTENT") LockLifetime lifetime,
            @Parameter(description = "Duration in seconds for EPHEMERAL locks") @RequestParam(required = false) Integer durationSeconds,
            @Parameter(description = "Lock child nodes recursively") @RequestParam(defaultValue = "false") boolean deep,
            @Parameter(description = "Additional metadata") @RequestParam(required = false) String additionalInfo) {
        lockService.lock(nodeId, lockType, lifetime, durationSeconds, deep, additionalInfo);
        return ResponseEntity.ok(lockService.getLockInfo(nodeId));
    }

    @PostMapping("/{nodeId}/unlock-deep")
    @Operation(summary = "Unlock node recursively",
               description = "Unlock a node and optionally all locked descendant nodes.")
    public ResponseEntity<Void> unlockDeep(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId,
            @Parameter(description = "Also unlock children") @RequestParam(defaultValue = "true") boolean unlockChildren) {
        lockService.unlock(nodeId, unlockChildren);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/batch-lock")
    @Operation(summary = "Batch lock nodes",
               description = "Lock multiple nodes in one request.")
    public ResponseEntity<Void> batchLock(
            @Parameter(description = "Node IDs") @RequestBody List<UUID> nodeIds,
            @Parameter(description = "Lock type") @RequestParam(defaultValue = "WRITE_LOCK") LockType lockType,
            @Parameter(description = "Duration in seconds") @RequestParam(defaultValue = "1800") int durationSeconds) {
        lockService.batchLock(nodeIds, lockType, durationSeconds);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/batch-unlock")
    @Operation(summary = "Batch unlock nodes",
               description = "Unlock multiple nodes in one request.")
    public ResponseEntity<Void> batchUnlock(
            @Parameter(description = "Node IDs") @RequestBody List<UUID> nodeIds) {
        lockService.batchUnlock(nodeIds);
        return ResponseEntity.ok().build();
    }

    // ---- aspect endpoints ---------------------------------------------------

    @GetMapping("/{nodeId}/aspects")
    @Operation(summary = "List node aspects", description = "List all aspects attached to a node")
    public ResponseEntity<Set<String>> getAspects(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId) {
        return ResponseEntity.ok(nodeService.getAspects(nodeId));
    }

    @PostMapping("/{nodeId}/aspects")
    @Operation(summary = "Add aspect to node",
               description = "Attach an aspect with optional initial property values. "
                   + "Missing mandatory properties are auto-filled from defaults if available.")
    public ResponseEntity<NodeDto> addAspectByBody(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId,
            @Valid @RequestBody AddAspectRequest request) {
        Node node = nodeService.addAspect(nodeId, request.aspectName(), request.properties());
        return ResponseEntity.ok(toNodeDto(node));
    }

    @PostMapping("/{nodeId}/aspects/{aspectName}")
    @Operation(summary = "Add aspect to node (path style)",
               description = "Attach an aspect with optional initial property values via path variable.")
    public ResponseEntity<NodeDto> addAspect(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId,
            @Parameter(description = "Qualified aspect name") @PathVariable String aspectName,
            @RequestBody(required = false) Map<String, Object> properties) {
        Node node = nodeService.addAspect(nodeId, aspectName, properties);
        return ResponseEntity.ok(toNodeDto(node));
    }

    @DeleteMapping("/{nodeId}/aspects/{aspectName}")
    @Operation(summary = "Remove aspect from node", description = "Detach an aspect from a node")
    public ResponseEntity<NodeDto> removeAspect(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId,
            @Parameter(description = "Aspect name") @PathVariable String aspectName) {
        Node node = nodeService.removeAspect(nodeId, aspectName);
        return ResponseEntity.ok(toNodeDto(node));
    }

    // ---- peer / secondary-child association endpoints ----------------------

    @GetMapping("/{nodeId}/targets")
    @Operation(summary = "Get target associations",
               description = "List peer associations where this node is the source.")
    public ResponseEntity<List<NodeRelationEdgeDto>> getTargetAssociations(
            @PathVariable UUID nodeId,
            @RequestParam(required = false) String assocType) {
        return ResponseEntity.ok(
            relationService.getTargetAssociations(nodeId, assocType).stream()
                .map(this::toEdgeDto).toList());
    }

    @PostMapping("/{nodeId}/targets")
    @Operation(summary = "Create peer target association")
    public ResponseEntity<NodeRelationEdgeDto> createTargetAssociation(
            @PathVariable UUID nodeId,
            @RequestParam UUID targetId,
            @RequestParam(defaultValue = "cm:references") String assocType) {
        DocumentRelation rel = relationService.createPeerAssociation(nodeId, targetId, assocType);
        return ResponseEntity.status(HttpStatus.CREATED).body(toEdgeDto(rel));
    }

    @DeleteMapping("/{nodeId}/targets/{targetId}")
    @Operation(summary = "Remove peer target association")
    public ResponseEntity<Void> removeTargetAssociation(
            @PathVariable UUID nodeId,
            @PathVariable UUID targetId) {
        relationService.removePeerAssociation(nodeId, targetId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{nodeId}/sources")
    @Operation(summary = "Get source associations",
               description = "List peer associations where this node is the target.")
    public ResponseEntity<List<NodeRelationEdgeDto>> getSourceAssociations(
            @PathVariable UUID nodeId,
            @RequestParam(required = false) String assocType) {
        return ResponseEntity.ok(
            relationService.getSourceAssociations(nodeId, assocType).stream()
                .map(this::toEdgeDto).toList());
    }

    @PostMapping("/{nodeId}/secondary-children")
    @Operation(summary = "Add secondary child",
               description = "Add a node as a secondary child (multi-filing).")
    public ResponseEntity<NodeRelationEdgeDto> addSecondaryChild(
            @PathVariable UUID nodeId,
            @RequestParam UUID childId) {
        DocumentRelation rel = relationService.addSecondaryChild(nodeId, childId);
        return ResponseEntity.status(HttpStatus.CREATED).body(toEdgeDto(rel));
    }

    @GetMapping("/{nodeId}/secondary-children")
    @Operation(summary = "List secondary children")
    public ResponseEntity<List<NodeRelationEdgeDto>> getSecondaryChildren(
            @PathVariable UUID nodeId) {
        return ResponseEntity.ok(
            relationService.getSecondaryChildren(nodeId).stream()
                .map(this::toEdgeDto).toList());
    }

    @DeleteMapping("/{nodeId}/secondary-children/{childId}")
    @Operation(summary = "Remove secondary child")
    public ResponseEntity<Void> removeSecondaryChild(
            @PathVariable UUID nodeId,
            @PathVariable UUID childId) {
        relationService.removeSecondaryChild(nodeId, childId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{nodeId}/secondary-parents")
    @Operation(summary = "List secondary parents",
               description = "List nodes that have this node as a secondary child.")
    public ResponseEntity<List<NodeRelationEdgeDto>> getSecondaryParents(
            @PathVariable UUID nodeId) {
        return ResponseEntity.ok(
            relationService.getSecondaryParents(nodeId).stream()
                .map(this::toEdgeDto).toList());
    }

    private NodeRelationEdgeDto toEdgeDto(DocumentRelation rel) {
        return new NodeRelationEdgeDto(
            rel.getId(),
            rel.getAssocType() != null ? rel.getAssocType() : rel.getRelationType(),
            new NodeRelationNodeRefDto(
                rel.getSource().getId(),
                rel.getSource().getName(),
                rel.getSource().getPath(),
                rel.getSource().getNodeType().name(),
                rel.getSource().getParent() != null ? rel.getSource().getParent().getId() : null
            ),
            new NodeRelationNodeRefDto(
                rel.getTarget().getId(),
                rel.getTarget().getName(),
                rel.getTarget().getPath(),
                rel.getTarget().getNodeType().name(),
                rel.getTarget().getParent() != null ? rel.getTarget().getParent().getId() : null
            ),
            rel.getCreatedDate()
        );
    }

    @GetMapping("/search")
    @Operation(summary = "Search nodes", description = "Search for nodes based on criteria")
    public ResponseEntity<List<NodeDto>> searchNodes(
            @Parameter(description = "Search query") @RequestParam(required = false) String query,
            @Parameter(description = "Search filters") @RequestParam Map<String, Object> filters,
            Pageable pageable) {
        List<Node> results = nodeService.searchNodes(query, filters, pageable);
        return ResponseEntity.ok(results.stream().map(this::toNodeDto).toList());
    }

    private List<Node> collectParentChain(Node node, int maxDepth) {
        List<Node> parents = new ArrayList<>();
        Node current = node;
        int depth = 0;
        Set<UUID> visited = new HashSet<>();
        while (current.getParent() != null && depth < maxDepth) {
            UUID parentId = current.getParent().getId();
            if (parentId == null || !visited.add(parentId)) {
                break;
            }
            Node parent = nodeService.getNode(parentId);
            parents.add(parent);
            current = parent;
            depth += 1;
        }
        return parents;
    }

    private NodeDto toNodeDto(Node node) {
        NodeDto base = NodeDto.from(node);
        if (!(node instanceof Document document)) {
            return base;
        }
        RenditionResourceService.RenditionSummary renditionSummary = renditionResourceService.summarizeDocument(document);
        if (renditionSummary == null || !renditionSummary.document()) {
            return base;
        }
        return base.withPreviewSemantics(
            renditionSummary.previewStatus(),
            renditionSummary.previewFailureReason(),
            renditionSummary.previewFailureCategory(),
            renditionSummary.previewLastUpdated()
        );
    }

    private boolean matchNodeQuery(Node node, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String normalized = query.trim().toLowerCase();
        return node.getName() != null && node.getName().toLowerCase().contains(normalized);
    }

    private boolean matchNodeType(Node node, String nodeType) {
        if (nodeType == null || nodeType.isBlank()) {
            return true;
        }
        String normalized = nodeType.trim().toUpperCase();
        return node.getNodeType().name().equals(normalized);
    }

    private <T> Page<T> paginate(List<T> source, int page, int size) {
        int fromIndex = page * size;
        if (fromIndex >= source.size()) {
            return new PageImpl<>(List.of(), PageRequest.of(page, size), source.size());
        }
        int toIndex = Math.min(fromIndex + size, source.size());
        return new PageImpl<>(source.subList(fromIndex, toIndex), PageRequest.of(page, size), source.size());
    }

    private List<NodeRenditionRelationDto> buildRenditionRelations(Document document) {
        return renditionResourceService.listForDocument(document).stream()
            .map(this::toNodeRenditionRelation)
            .toList();
    }

    private java.util.Optional<NodeRenditionRelationDto> resolveRendition(Document document, String renditionId) {
        if (renditionId == null) {
            return java.util.Optional.empty();
        }
        String normalized = renditionId.trim().toLowerCase();
        return buildRenditionRelations(document).stream()
            .filter(relation -> normalized.equals(relation.renditionId()))
            .findFirst();
    }

    private NodeRenditionRelationDto toNodeRenditionRelation(RenditionResource resource) {
        return new NodeRenditionRelationDto(
            resource.getDocument().getId(),
            resource.getRenditionKey(),
            resource.getLabel(),
            resource.getState().name(),
            resource.isAvailable(),
            resource.getMimeType(),
            resource.getContentUrl(),
            resource.isDownloadable(),
            resource.getErrorReason(),
            resource.getErrorCategory(),
            resource.getSourceUpdatedAt(),
            resource.getVersionLabel()
        );
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private NodeRelationNodeRefDto toNodeRef(Node node) {
        return new NodeRelationNodeRefDto(
            node.getId(),
            node.getName(),
            node.getPath(),
            node.getNodeType().name(),
            node.getParent() != null ? node.getParent().getId() : null
        );
    }

    private VersionDto resolveVersionDto(String versionId) {
        if (versionId == null || versionId.isBlank()) {
            return null;
        }
        try {
            return VersionDto.from(versionService.getVersion(UUID.fromString(versionId)));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private NodeCheckoutGraphNodeDto toCheckoutGraphNode(
        String id,
        String kind,
        String label,
        boolean focus,
        boolean virtualNode,
        boolean available
    ) {
        return new NodeCheckoutGraphNodeDto(id, kind, label, focus, virtualNode, available);
    }

    private List<NodeCheckoutGraphNodeDto> buildCheckoutGraphNodes(
        NodeCheckoutGraphNodeDto documentNode,
        NodeCheckoutGraphNodeDto workingCopyNode,
        NodeCheckoutGraphNodeDto destinationNode,
        VersionDto baselineVersion,
        VersionDto currentVersion
    ) {
        Map<String, NodeCheckoutGraphNodeDto> nodes = new LinkedHashMap<>();
        addCheckoutGraphNode(nodes, documentNode);
        addCheckoutGraphNode(nodes, workingCopyNode);
        addCheckoutGraphNode(nodes, toCheckoutGraphVersionNode(baselineVersion, "BASELINE_VERSION", "Baseline"));
        addCheckoutGraphNode(nodes, toCheckoutGraphVersionNode(currentVersion, "CURRENT_VERSION", "Current"));
        addCheckoutGraphNode(nodes, destinationNode);
        return new ArrayList<>(nodes.values());
    }

    private void addCheckoutGraphNode(Map<String, NodeCheckoutGraphNodeDto> nodes, NodeCheckoutGraphNodeDto node) {
        if (node == null || node.id() == null || node.id().isBlank()) {
            return;
        }
        nodes.putIfAbsent(node.id(), node);
    }

    private NodeCheckoutGraphNodeDto toCheckoutGraphVersionNode(VersionDto version, String kind, String fallbackLabel) {
        if (version == null || version.id() == null) {
            return null;
        }
        String versionLabel = version.versionLabel() != null && !version.versionLabel().isBlank()
            ? "v" + version.versionLabel()
            : fallbackLabel;
        return toCheckoutGraphNode(
            version.id().toString(),
            kind,
            versionLabel,
            false,
            false,
            true
        );
    }

    private NodeRelationEdgeDto toRelationEdge(DocumentRelation relation) {
        return new NodeRelationEdgeDto(
            relation.getId(),
            relation.getRelationType(),
            toNodeRef(relation.getSource()),
            toNodeRef(relation.getTarget()),
            relation.getCreatedDate()
        );
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public record NodeRelationsSummaryDto(
        UUID nodeId,
        String nodeType,
        int parentCount,
        long childCount,
        long sourceRelationCount,
        long targetRelationCount,
        long versionCount,
        String previewStatus,
        boolean renditionAvailable,
        boolean checkedOut,
        String checkoutUser,
        LocalDateTime checkoutDate
    ) {}

    public record NodeRelationNodeRefDto(
        UUID id,
        String name,
        String path,
        String nodeType,
        UUID parentId
    ) {}

    public record NodeRelationEdgeDto(
        UUID relationId,
        String relationType,
        NodeRelationNodeRefDto source,
        NodeRelationNodeRefDto target,
        LocalDateTime createdDate
    ) {}

    public record NodeRenditionRelationSummaryDto(
        UUID nodeId,
        boolean document,
        String previewStatus,
        boolean renditionAvailable,
        String previewFailureReason,
        String previewFailureCategory,
        LocalDateTime previewLastUpdated,
        String currentVersionLabel
    ) {}

    public record NodeRenditionRelationDto(
        UUID nodeId,
        String renditionId,
        String label,
        String status,
        boolean available,
        String mimeType,
        String url,
        boolean downloadable,
        String failureReason,
        String failureCategory,
        LocalDateTime previewLastUpdated,
        String currentVersionLabel
    ) {}

    public record NodeCheckoutRelationDto(
        UUID nodeId,
        boolean document,
        boolean checkedOut,
        String checkoutUser,
        LocalDateTime checkoutDate,
        String checkoutBaselineVersionId,
        String checkoutBaselineVersionLabel,
        String currentVersionLabel,
        boolean canCheckout,
        boolean canCheckIn,
        boolean canCancelCheckout,
        boolean canKeepCheckedOut,
        boolean requiresNewVersionFile,
        String blockingReason
    ) {}

    public record NodeCheckoutGraphNodeDto(
        String id,
        String kind,
        String label,
        boolean focus,
        boolean virtualNode,
        boolean available
    ) {}

    public record NodeCheckoutGraphEdgeDto(
        String relationType,
        String sourceId,
        String targetId,
        String label
    ) {}

    public record NodeCheckoutGraphDto(
        UUID nodeId,
        boolean document,
        boolean checkedOut,
        String checkoutUser,
        LocalDateTime checkoutDate,
        NodeCheckoutGraphNodeDto documentNode,
        NodeCheckoutGraphNodeDto workingCopyNode,
        NodeCheckoutGraphNodeDto destinationNode,
        VersionDto baselineVersion,
        VersionDto currentVersion,
        List<NodeCheckoutGraphNodeDto> nodes,
        List<NodeCheckoutGraphEdgeDto> edges,
        boolean canCheckIn,
        boolean canCancelCheckout,
        boolean canKeepCheckedOut,
        String blockingReason
    ) {}
}
