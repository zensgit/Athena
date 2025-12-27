import React, { useCallback, useEffect, useState } from 'react';
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
  Download,
} from '@mui/icons-material';
import { useAppDispatch, useAppSelector } from 'store';
import { fetchNode, fetchChildren, deleteNodes } from 'store/slices/nodeSlice';
import { setViewMode } from 'store/slices/uiSlice';
import FileBreadcrumb from 'components/browser/FileBreadcrumb';
import FileList from 'components/browser/FileList';
import { Node } from 'types';
import nodeService from 'services/nodeService';
import { toast } from 'react-toastify';

const DocumentPreview = React.lazy(() => import('components/preview/DocumentPreview'));

const FileBrowser: React.FC = () => {
  const { nodeId = 'root' } = useParams<{ nodeId: string }>();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  
  const { currentNode, nodes, nodesTotal, loading, selectedNodes } = useAppSelector((state) => state.node);
  const { user } = useAppSelector((state) => state.auth);
  const { viewMode, sortBy, sortAscending, compactMode } = useAppSelector((state) => state.ui);
  const canWrite = Boolean(user?.roles?.includes('ROLE_ADMIN') || user?.roles?.includes('ROLE_EDITOR'));
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(50);
  const [previewNode, setPreviewNode] = useState<Node | null>(null);
  const [previewAnnotate, setPreviewAnnotate] = useState(false);
  const previewOpen = Boolean(previewNode);

  const loadNodeData = useCallback(async () => {
    await dispatch(fetchNode(nodeId));
    await dispatch(fetchChildren({ nodeId, sortBy, ascending: sortAscending, page, size: pageSize }));
  }, [dispatch, nodeId, sortAscending, sortBy, page, pageSize]);

  useEffect(() => {
    loadNodeData();
  }, [nodeId, loadNodeData]);
  
  useEffect(() => {
    setPage(0);
  }, [nodeId, sortBy, sortAscending]);

  const isDocumentNode = (node: Node) => {
    if (node.nodeType === 'DOCUMENT') {
      return true;
    }
    if (node.nodeType === 'FOLDER') {
      return false;
    }
    const name = node.name?.toLowerCase() || '';
    const contentTypeHint = node.contentType
      || node.properties?.mimeType
      || node.properties?.contentType;
    const sizeHint = node.size
      || node.properties?.fileSize
      || node.properties?.size;
    const hasExtension = name.includes('.') && !name.endsWith('.');
    return Boolean(contentTypeHint || sizeHint || node.currentVersionLabel || hasExtension);
  };

  const handleNodeDoubleClick = (node: Node) => {
    if (node.nodeType === 'FOLDER') {
      navigate(`/browse/${node.id}`);
      return;
    }
    if (isDocumentNode(node)) {
      setPreviewAnnotate(false);
      setPreviewNode(node);
    }
  };

  const handlePreviewNode = (node: Node, options?: { annotate?: boolean }) => {
    if (isDocumentNode(node)) {
      setPreviewAnnotate(Boolean(options?.annotate));
      setPreviewNode(node);
    }
  };

  const handleClosePreview = () => {
    setPreviewNode(null);
    setPreviewAnnotate(false);
  };

  const handleNavigate = (path: string) => {
    if (path === '/') {
      navigate('/browse/root');
      return;
    }

    void (async () => {
      try {
        const targetFolder = await nodeService.getFolderByPath(path);
        navigate(`/browse/${targetFolder.id}`);
      } catch (error) {
        console.error('Failed to navigate by path:', error);
        toast.error('Failed to navigate to folder');
      }
    })();
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

  const handleBatchDownload = async () => {
    if (selectedNodes.length === 0) {
      return;
    }

    try {
      const baseName = currentNode?.name ? `${currentNode.name}-selection` : 'selection';
      await nodeService.downloadNodesAsZip(selectedNodes, baseName);
      toast.success('Download started');
    } catch {
      toast.error('Failed to download selection');
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
      <Paper sx={{ p: compactMode ? 1.5 : 2, mb: compactMode ? 1.5 : 2 }}>
        <Box display="flex" alignItems="center" justifyContent="space-between">
          <FileBreadcrumb path={currentNode.path} onNavigate={handleNavigate} />
          
          <Box display="flex" alignItems="center" gap={1}>
            {selectedNodes.length > 0 && (
              <>
                <Typography variant="body2" color="text.secondary" sx={{ mr: 2 }}>
                  {selectedNodes.length} selected
                </Typography>
                <IconButton onClick={handleBatchDownload} color="primary" size="small" aria-label="Download selected">
                  <Download />
                </IconButton>
                {canWrite && (
                  <IconButton onClick={handleDelete} color="error" size="small" aria-label="Delete selected">
                    <Delete />
                  </IconButton>
                )}
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
            onPreviewNode={handlePreviewNode}
            page={page}
            pageSize={pageSize}
            totalCount={nodesTotal}
            onPageChange={setPage}
            onPageSizeChange={(size) => {
              setPageSize(size);
              setPage(0);
            }}
          />
        )}
      </Paper>

      {previewOpen && previewNode && (
        <React.Suspense
          fallback={(
            <Box
              sx={{
                position: 'fixed',
                inset: 0,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                bgcolor: 'rgba(0,0,0,0.35)',
                zIndex: (theme) => theme.zIndex.modal + 1,
              }}
            >
              <CircularProgress />
            </Box>
          )}
        >
          <DocumentPreview
            open={previewOpen}
            onClose={handleClosePreview}
            node={previewNode}
            initialAnnotateMode={previewAnnotate}
          />
        </React.Suspense>
      )}
    </Box>
  );
};

export default FileBrowser;
