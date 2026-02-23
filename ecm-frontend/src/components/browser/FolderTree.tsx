import React, { useCallback, useEffect, useRef, useState } from 'react';
import { TreeView, TreeItem } from '@mui/x-tree-view';
import { ExpandMore, ChevronRight, Folder, FolderOpen } from '@mui/icons-material';
import { Box, Typography, CircularProgress, Alert, Button } from '@mui/material';
import { Node } from 'types';
import nodeService from 'services/nodeService';
import { useAppDispatch } from 'store';
import { setCurrentNode, fetchChildren } from 'store/slices/nodeSlice';
import { resolvePositiveIntEnv } from 'constants/auth';

interface FolderTreeProps {
  rootNodeId: string;
  selectedNodeId?: string;
  onNodeSelect?: (node: Node) => void;
  variant?: 'navigation' | 'picker';
}

interface TreeNode extends Node {
  children?: TreeNode[];
  loaded?: boolean;
}

const FOLDER_TREE_LOADING_WATCHDOG_MS = resolvePositiveIntEnv(
  process.env.REACT_APP_FOLDER_TREE_LOADING_WATCHDOG_MS,
  12_000
);
const E2E_FOLDER_TREE_WATCHDOG_MS_KEY = 'ecm_e2e_folder_tree_watchdog_ms';

const safeLocalGetItem = (key: string): string | null => {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
};

const canUseE2EWatchdogOverrides = (): boolean => {
  if (typeof window === 'undefined') {
    return false;
  }
  return window.navigator?.webdriver === true
    || window.location.hostname === 'localhost'
    || window.location.hostname === '127.0.0.1';
};

const resolveFolderTreeWatchdogMs = (): number => {
  if (!canUseE2EWatchdogOverrides()) {
    return FOLDER_TREE_LOADING_WATCHDOG_MS;
  }
  const override = safeLocalGetItem(E2E_FOLDER_TREE_WATCHDOG_MS_KEY) || undefined;
  return resolvePositiveIntEnv(override, FOLDER_TREE_LOADING_WATCHDOG_MS);
};

const FolderTree: React.FC<FolderTreeProps> = ({
  rootNodeId,
  selectedNodeId,
  onNodeSelect,
  variant = 'navigation',
}) => {
  const dispatch = useAppDispatch();
  const [treeData, setTreeData] = useState<TreeNode | null>(null);
  const [expanded, setExpanded] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [loadingWatchdogTriggered, setLoadingWatchdogTriggered] = useState(false);
  const folderIdCacheRef = useRef<Map<string, string>>(new Map());
  const rootLoadRequestIdRef = useRef(0);
  const folderTreeWatchdogMs = resolveFolderTreeWatchdogMs();

  const loadRootNode = useCallback(async () => {
    const requestId = rootLoadRequestIdRef.current + 1;
    rootLoadRequestIdRef.current = requestId;
    try {
      setLoading(true);
      setLoadError(null);
      setLoadingWatchdogTriggered(false);
      const rootNode = await nodeService.getNode(rootNodeId);
      const children = await nodeService.getChildren(rootNodeId);
      const folderChildren = children.filter((child) => child.nodeType === 'FOLDER');
      if (requestId !== rootLoadRequestIdRef.current) {
        return;
      }

      const treeNode: TreeNode = {
        ...rootNode,
        children: folderChildren.map((child) => ({ ...child, children: [], loaded: false })),
        loaded: true,
      };

      setTreeData(treeNode);
      setExpanded([treeNode.id]);
    } catch (error) {
      if (requestId !== rootLoadRequestIdRef.current) {
        return;
      }
      console.error('Failed to load root node:', error);
      setTreeData(null);
      setLoadError('Failed to load folder tree');
    } finally {
      if (requestId === rootLoadRequestIdRef.current) {
        setLoading(false);
      }
    }
  }, [rootNodeId]);

  useEffect(() => {
    loadRootNode();
  }, [loadRootNode]);

  useEffect(() => {
    if (!loading) {
      setLoadingWatchdogTriggered(false);
      return;
    }
    const timerId = window.setTimeout(() => {
      setLoadingWatchdogTriggered(true);
    }, folderTreeWatchdogMs);
    return () => window.clearTimeout(timerId);
  }, [folderTreeWatchdogMs, loading]);

  useEffect(() => {
    if (!treeData) {
      return;
    }

    if (!selectedNodeId) {
      return;
    }

    const mergeExpanded = (current: string[], extra: string[]) => {
      if (extra.length === 0) {
        return current;
      }
      let changed = false;
      const next = [...current];
      for (const id of extra) {
        if (!next.includes(id)) {
          next.push(id);
          changed = true;
        }
      }
      return changed ? next : current;
    };

    const findPathIdsToNode = (root: TreeNode, targetId: string) => {
      const path: string[] = [];

      const walk = (node: TreeNode): boolean => {
        path.push(node.id);
        if (node.id === targetId) {
          return true;
        }
        if (node.children) {
          for (const child of node.children) {
            if (walk(child)) {
              return true;
            }
          }
        }
        path.pop();
        return false;
      };

      return walk(root) ? path : null;
    };

    const findNodeInTree = (root: TreeNode, nodeId: string): TreeNode | null => {
      if (root.id === nodeId) {
        return root;
      }
      if (root.children) {
        for (const child of root.children) {
          const found = findNodeInTree(child, nodeId);
          if (found) {
            return found;
          }
        }
      }
      return null;
    };

    const updateNodeInTree = (root: TreeNode, nodeId: string, updater: (node: TreeNode) => TreeNode): TreeNode => {
      if (root.id === nodeId) {
        return updater(root);
      }

      if (!root.children || root.children.length === 0) {
        return root;
      }

      let changed = false;
      const nextChildren = root.children.map((child) => {
        const updatedChild = updateNodeInTree(child, nodeId, updater);
        if (updatedChild !== child) {
          changed = true;
        }
        return updatedChild;
      });

      return changed ? { ...root, children: nextChildren } : root;
    };

    const resolveFolderIdByPath = async (path: string) => {
      const cached = folderIdCacheRef.current.get(path);
      if (cached) {
        return cached;
      }
      const folder = await nodeService.getFolderByPath(path);
      folderIdCacheRef.current.set(path, folder.id);
      return folder.id;
    };

    const effectiveSelectedId = selectedNodeId === 'root' ? treeData.id : selectedNodeId;
    const existingPathIds = findPathIdsToNode(treeData, effectiveSelectedId);
    if (existingPathIds) {
      setExpanded((current) => mergeExpanded(current, existingPathIds));
      return;
    }

    let cancelled = false;

    void (async () => {
      try {
        const node = await nodeService.getNode(effectiveSelectedId);
        let folderPath = node.path || '';

        if (node.nodeType === 'DOCUMENT') {
          const parts = folderPath.split('/').filter(Boolean);
          folderPath = parts.length > 0 ? '/' + parts.slice(0, -1).join('/') : '';
        }

        if (!folderPath) {
          return;
        }

        const rootPath = (treeData.path || '').toLowerCase();
        if (rootPath && !folderPath.toLowerCase().startsWith(rootPath)) {
          return;
        }

        const segments = folderPath.split('/').filter(Boolean);
        const folderPaths: string[] = [];
        let currentPath = '';
        for (const segment of segments) {
          currentPath += '/' + segment;
          folderPaths.push(currentPath);
        }

        const folderIds: string[] = [];
        for (const path of folderPaths) {
          folderIds.push(await resolveFolderIdByPath(path));
        }

        let nextTree = treeData;
        for (const folderId of folderIds) {
          const treeNode = findNodeInTree(nextTree, folderId);
          if (!treeNode) {
            break;
          }

          if (!treeNode.loaded) {
            const children = await nodeService.getChildren(folderId);
            const folderChildren = children.filter((child) => child.nodeType === 'FOLDER');
            nextTree = updateNodeInTree(nextTree, folderId, (current) => ({
              ...current,
              children: folderChildren.map((child) => ({ ...child, children: [], loaded: false })),
              loaded: true,
            }));
          }
        }

        if (cancelled) {
          return;
        }

        if (nextTree !== treeData) {
          setTreeData(nextTree);
        }

        setExpanded((current) => mergeExpanded(current, folderIds));
      } catch (error) {
        console.warn('Failed to auto-expand folder tree:', error);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [selectedNodeId, treeData]);

  const loadNodeChildren = async (nodeId: string) => {
    try {
      const children = await nodeService.getChildren(nodeId);
      const folderChildren = children.filter((child) => child.nodeType === 'FOLDER');
      
      updateTreeNode(nodeId, (node) => ({
        ...node,
        children: folderChildren.map((child) => ({ ...child, children: [], loaded: false })),
        loaded: true,
      }));
    } catch (error) {
      console.error('Failed to load node children:', error);
    }
  };

  const updateTreeNode = (nodeId: string, updater: (node: TreeNode) => TreeNode) => {
    setTreeData((prevData) => {
      if (!prevData) return null;
      
      const updateNode = (node: TreeNode): TreeNode => {
        if (node.id === nodeId) {
          return updater(node);
        }
        
        if (node.children) {
          return {
            ...node,
            children: node.children.map(updateNode),
          };
        }
        
        return node;
      };
      
      return updateNode(prevData);
    });
  };

  const handleNodeToggle = async (event: React.SyntheticEvent, nodeIds: string[]) => {
    setExpanded(nodeIds);
    
    for (const nodeId of nodeIds) {
      const node = findNode(nodeId);
      if (node && !node.loaded) {
        await loadNodeChildren(nodeId);
      }
    }
  };

  const handleNodeSelect = async (event: React.SyntheticEvent, nodeId: string) => {
    const node = findNode(nodeId);
    if (node) {
      if (variant === 'navigation') {
        dispatch(setCurrentNode(node));
        dispatch(fetchChildren({ nodeId }));
      }
      if (onNodeSelect) {
        onNodeSelect(node);
      }
    }
  };

  const findNode = (nodeId: string): TreeNode | null => {
    if (!treeData) return null;
    
    const find = (node: TreeNode): TreeNode | null => {
      if (node.id === nodeId) return node;
      
      if (node.children) {
        for (const child of node.children) {
          const found = find(child);
          if (found) return found;
        }
      }
      
      return null;
    };
    
    return find(treeData);
  };

  const renderTree = (node: TreeNode) => (
    <TreeItem
      key={node.id}
      nodeId={node.id}
      label={
        <Box sx={{ display: 'flex', alignItems: 'center', py: 0.5 }}>
          {expanded.includes(node.id) ? (
            <FolderOpen sx={{ mr: 1, fontSize: 20 }} />
          ) : (
            <Folder sx={{ mr: 1, fontSize: 20 }} />
          )}
          <Typography variant="body2">{node.name}</Typography>
        </Box>
      }
    >
      {node.children && node.children.map((child) => renderTree(child))}
    </TreeItem>
  );

  if (loading) {
    return (
      <Box display="flex" flexDirection="column" gap={1.5} p={2} data-testid="folder-tree-loading-state">
        <Box display="flex" justifyContent="center" p={1}>
          <CircularProgress size={24} />
        </Box>
        {loadingWatchdogTriggered && (
          <Alert severity="warning" data-testid="folder-tree-loading-watchdog-alert">
            <Box display="flex" alignItems="center" gap={1} flexWrap="wrap">
              <Typography variant="body2">
                Folder tree is taking longer than expected to load.
              </Typography>
              <Button
                size="small"
                variant="outlined"
                onClick={() => {
                  void loadRootNode();
                }}
                data-testid="folder-tree-loading-watchdog-retry"
              >
                Retry
              </Button>
            </Box>
          </Alert>
        )}
      </Box>
    );
  }

  if (!treeData) {
    return (
      <Box sx={{ p: 2 }} data-testid="folder-tree-load-error">
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
          {loadError || 'Failed to load folder tree'}
        </Typography>
        <Button
          size="small"
          variant="outlined"
          onClick={() => {
            void loadRootNode();
          }}
          data-testid="folder-tree-load-error-retry"
        >
          Retry
        </Button>
      </Box>
    );
  }

  const effectiveSelectedNodeId = selectedNodeId === 'root' ? treeData.id : selectedNodeId;

  return (
    <TreeView
      defaultCollapseIcon={<ExpandMore />}
      defaultExpandIcon={<ChevronRight />}
      expanded={expanded}
      selected={effectiveSelectedNodeId}
      onNodeToggle={handleNodeToggle}
      onNodeSelect={handleNodeSelect}
      sx={{
        flexGrow: 1,
        overflowY: 'auto',
        '& .MuiTreeItem-content': {
          '&:hover': {
            backgroundColor: 'action.hover',
          },
          '&.Mui-selected': {
            backgroundColor: 'action.selected',
            '&:hover': {
              backgroundColor: 'action.selected',
            },
          },
        },
      }}
    >
      {renderTree(treeData)}
    </TreeView>
  );
};

export default FolderTree;
