import React, { useCallback, useEffect, useRef, useState } from 'react';
import { TreeView, TreeItem } from '@mui/x-tree-view';
import { ExpandMore, ChevronRight, Folder, FolderOpen } from '@mui/icons-material';
import { Box, Typography, CircularProgress } from '@mui/material';
import { Node } from 'types';
import nodeService from 'services/nodeService';
import { useAppDispatch } from 'store';
import { setCurrentNode, fetchChildren } from 'store/slices/nodeSlice';

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
  const folderIdCacheRef = useRef<Map<string, string>>(new Map());

  const loadRootNode = useCallback(async () => {
    try {
      setLoading(true);
      const rootNode = await nodeService.getNode(rootNodeId);
      const children = await nodeService.getChildren(rootNodeId);
      const folderChildren = children.filter((child) => child.nodeType === 'FOLDER');
      
      const treeNode: TreeNode = {
        ...rootNode,
        children: folderChildren.map((child) => ({ ...child, children: [], loaded: false })),
        loaded: true,
      };
      
      setTreeData(treeNode);
      setExpanded([treeNode.id]);
    } catch (error) {
      console.error('Failed to load root node:', error);
    } finally {
      setLoading(false);
    }
  }, [rootNodeId]);

  useEffect(() => {
    loadRootNode();
  }, [loadRootNode]);

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
      <Box display="flex" justifyContent="center" p={3}>
        <CircularProgress size={24} />
      </Box>
    );
  }

  if (!treeData) {
    return (
      <Typography variant="body2" color="text.secondary" sx={{ p: 2 }}>
        Failed to load folder tree
      </Typography>
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
