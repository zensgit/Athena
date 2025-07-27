import React, { useEffect, useState } from 'react';
import { TreeView, TreeItem } from '@mui/x-tree-view';
import { ExpandMore, ChevronRight, Folder, FolderOpen } from '@mui/icons-material';
import { Box, Typography, CircularProgress } from '@mui/material';
import { Node } from '@/types';
import nodeService from '@/services/nodeService';
import { useAppDispatch } from '@/store';
import { setCurrentNode, fetchChildren } from '@/store/slices/nodeSlice';

interface FolderTreeProps {
  rootNodeId: string;
  selectedNodeId?: string;
  onNodeSelect?: (node: Node) => void;
}

interface TreeNode extends Node {
  children?: TreeNode[];
  loaded?: boolean;
}

const FolderTree: React.FC<FolderTreeProps> = ({ rootNodeId, selectedNodeId, onNodeSelect }) => {
  const dispatch = useAppDispatch();
  const [treeData, setTreeData] = useState<TreeNode | null>(null);
  const [expanded, setExpanded] = useState<string[]>([rootNodeId]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadRootNode();
  }, [rootNodeId]);

  const loadRootNode = async () => {
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
    } catch (error) {
      console.error('Failed to load root node:', error);
    } finally {
      setLoading(false);
    }
  };

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
      dispatch(setCurrentNode(node));
      dispatch(fetchChildren({ nodeId }));
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

  return (
    <TreeView
      defaultCollapseIcon={<ExpandMore />}
      defaultExpandIcon={<ChevronRight />}
      expanded={expanded}
      selected={selectedNodeId || ''}
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