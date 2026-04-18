package com.ecm.core.event;

import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.ocr.OcrQueueService;
import com.ecm.core.preview.PreviewQueueService;
import com.ecm.core.search.SearchIndexService;
import com.ecm.core.service.AuditService;
import com.ecm.core.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EcmEventListenerPermissionIndexingTest {

    @Mock private AuditService auditService;
    @Mock private SearchIndexService searchIndexService;
    @Mock private NotificationService notificationService;
    @Mock private PreviewQueueService previewQueueService;
    @Mock private OcrQueueService ocrQueueService;

    private EcmEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new EcmEventListener(
            auditService,
            searchIndexService,
            notificationService,
            previewQueueService,
            ocrQueueService
        );
    }

    @Test
    @DisplayName("permission change on folder refreshes node and descendants in search index")
    void permissionChangeOnFolderRefreshesSubtree() {
        Folder folder = folder("Contracts", "/Sites/contracts");

        listener.handleNodePermissionsChanged(new NodePermissionsChangedEvent(folder, "alice", true));

        verify(searchIndexService).updateNode(folder);
        verify(searchIndexService).updateNodeChildren(folder);
    }

    @Test
    @DisplayName("permission change without descendant refresh only updates the node")
    void permissionChangeWithoutDescendantRefreshOnlyUpdatesNode() {
        Folder folder = folder("Contracts", "/Sites/contracts");

        listener.handleNodePermissionsChanged(new NodePermissionsChangedEvent(folder, "alice", false));

        verify(searchIndexService).updateNode(folder);
        verify(searchIndexService, never()).updateNodeChildren(folder);
    }

    @Test
    @DisplayName("node move reindexes subtree from database instead of searching stale index paths")
    void nodeMoveReindexesSubtreeFromDatabase() {
        Folder sourceParent = folder("Source", "/Sites/source");
        Folder targetParent = folder("Target", "/Sites/target");
        Folder movedFolder = folder("Contracts", "/Sites/target/contracts");

        listener.handleNodeMoved(new NodeMovedEvent(movedFolder, sourceParent, targetParent, "alice"));

        verify(searchIndexService).updateNode(movedFolder);
        verify(searchIndexService).reindexNodeSubtree(movedFolder);
        verify(searchIndexService, never()).updateNodeChildren(movedFolder);
    }

    @Test
    @DisplayName("subtree reindex request reindexes descendants from database")
    void subtreeReindexRequestReindexesDescendantsFromDatabase() {
        Folder renamedFolder = folder("HR File Plan", "/Company Home/HR File Plan");

        listener.handleNodeSubtreeReindexRequested(new NodeSubtreeReindexRequestedEvent(renamedFolder, "alice"));

        verify(searchIndexService).reindexNodeSubtree(renamedFolder);
        verify(searchIndexService, never()).updateNode(renamedFolder);
    }

    @Test
    @DisplayName("node batch reindex request refreshes affected nodes from database")
    void nodeBatchReindexRequestRefreshesAffectedNodes() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        listener.handleNodesReindexRequested(new NodesReindexRequestedEvent(java.util.List.of(first, second), "alice"));

        verify(searchIndexService).reindexNodes(java.util.List.of(first, second));
    }

    private Folder folder(String name, String path) {
        Folder folder = new Folder();
        folder.setId(UUID.randomUUID());
        folder.setName(name);
        folder.setPath(path);
        folder.setArchiveStatus(Node.ArchiveStatus.LIVE);
        folder.setStatus(Node.NodeStatus.ACTIVE);
        return folder;
    }
}
