import React, { useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Box,
  Paper,
  IconButton,
  ToggleButton,
  ToggleButtonGroup,
  CircularProgress,
  Typography,
} from '@mui/material';
import {
  ViewList,
  ViewModule,
  Delete,
} from '@mui/icons-material';
import { useAppDispatch, useAppSelector } from '@/store';
import { fetchNode, fetchChildren, deleteNodes } from '@/store/slices/nodeSlice';
import { setViewMode } from '@/store/slices/uiSlice';
import FileBreadcrumb from '@/components/browser/FileBreadcrumb';
import FileList from '@/components/browser/FileList';
import { toast } from 'react-toastify';

const FileBrowser: React.FC = () => {
  const { nodeId = 'root' } = useParams<{ nodeId: string }>();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  
  const { currentNode, nodes, loading, selectedNodes } = useAppSelector((state) => state.node);
  const { viewMode, sortBy, sortAscending } = useAppSelector((state) => state.ui);

  useEffect(() => {
    loadNodeData();
  }, [nodeId]);

  const loadNodeData = async () => {
    await dispatch(fetchNode(nodeId));
    await dispatch(fetchChildren({ nodeId, sortBy, ascending: sortAscending }));
  };

  const handleNodeDoubleClick = (node: any) => {
    if (node.nodeType === 'FOLDER') {
      navigate(`/browse/${node.id}`);
    }
  };

  const handleNavigate = (path: string) => {
    // Convert path to node ID navigation
    // This is simplified - in real implementation, you'd resolve the path to node ID
    navigate(`/browse/${path === '/' ? 'root' : path}`);
  };

  const handleViewModeChange = (_: any, newMode: 'list' | 'grid' | null) => {
    if (newMode !== null) {
      dispatch(setViewMode(newMode));
    }
  };

  const handleDelete = async () => {
    if (selectedNodes.length === 0) return;

    if (window.confirm(`Delete ${selectedNodes.length} selected item(s)?`)) {
      try {
        await dispatch(deleteNodes(selectedNodes)).unwrap();
        toast.success('Items deleted successfully');
      } catch (error) {
        toast.error('Failed to delete items');
      }
    }
  };

  if (loading && !currentNode) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" height="400px">
        <CircularProgress />
      </Box>
    );
  }

  if (!currentNode) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" height="400px">
        <Typography variant="h6" color="text.secondary">
          Folder not found
        </Typography>
      </Box>
    );
  }

  return (
    <Box>
      <Paper sx={{ p: 2, mb: 2 }}>
        <Box display="flex" alignItems="center" justifyContent="space-between">
          <FileBreadcrumb path={currentNode.path} onNavigate={handleNavigate} />
          
          <Box display="flex" alignItems="center" gap={1}>
            {selectedNodes.length > 0 && (
              <>
                <Typography variant="body2" color="text.secondary" sx={{ mr: 2 }}>
                  {selectedNodes.length} selected
                </Typography>
                <IconButton onClick={handleDelete} color="error" size="small">
                  <Delete />
                </IconButton>
              </>
            )}
            
            <ToggleButtonGroup
              value={viewMode}
              exclusive
              onChange={handleViewModeChange}
              size="small"
            >
              <ToggleButton value="list" aria-label="list view">
                <ViewList />
              </ToggleButton>
              <ToggleButton value="grid" aria-label="grid view">
                <ViewModule />
              </ToggleButton>
            </ToggleButtonGroup>
          </Box>
        </Box>
      </Paper>

      <Paper>
        {loading ? (
          <Box display="flex" justifyContent="center" p={4}>
            <CircularProgress />
          </Box>
        ) : nodes.length === 0 ? (
          <Box p={4} textAlign="center">
            <Typography variant="body1" color="text.secondary">
              This folder is empty
            </Typography>
          </Box>
        ) : (
          <FileList
            nodes={nodes}
            onNodeDoubleClick={handleNodeDoubleClick}
          />
        )}
      </Paper>
    </Box>
  );
};

export default FileBrowser;