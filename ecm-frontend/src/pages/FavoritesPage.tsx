import React, { useEffect, useState } from 'react';
import { Box, Paper, Typography, IconButton, CircularProgress, Button } from '@mui/material';
import { DataGrid, GridColDef } from '@mui/x-data-grid';
import { ChatBubbleOutline, Delete, FolderOpen, Person, Refresh, Star, Visibility } from '@mui/icons-material';
import { format } from 'date-fns';
import { useNavigate } from 'react-router-dom';
import { toast } from 'react-toastify';
import favoriteService from 'services/favoriteService';
import nodeService from 'services/nodeService';
import { Node } from 'types';
import { useAppDispatch } from 'store';
import { setPropertiesDialogOpen, setSelectedNodeId } from 'store/slices/uiSlice';
const DocumentPreview = React.lazy(() => import('components/preview/DocumentPreview'));

type FavoriteRow = Node & { favoritedAt: string };

const FavoritesPage: React.FC = () => {
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const [items, setItems] = useState<FavoriteRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [previewNode, setPreviewNode] = useState<FavoriteRow | null>(null);
  const [previewCommentsOpen, setPreviewCommentsOpen] = useState(false);
  const [previewCommentDraftText, setPreviewCommentDraftText] = useState<string | null>(null);

  const loadFavorites = async () => {
    setLoading(true);
    try {
      const page = await favoriteService.list(0, 200);
      const favorites = page.content || [];
      const nodes = await Promise.all(
        favorites.map(async (fav) => {
          const node = await nodeService.getNode(fav.nodeId);
          return { ...node, favoritedAt: fav.createdAt };
        })
      );
      setItems(nodes);
    } catch {
      toast.error('Failed to load favorites');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadFavorites();
  }, []);

  const handleOpen = (node: FavoriteRow) => {
    if (node.nodeType === 'FOLDER') {
      navigate(`/browse/${node.id}`);
      return;
    }
    handlePreview(node);
  };

  const handleInspect = (node: FavoriteRow) => {
    if (node.nodeType === 'FOLDER') {
      navigate(`/browse/${node.id}`);
      return;
    }
    dispatch(setSelectedNodeId(node.id));
    dispatch(setPropertiesDialogOpen(true));
  };

  const handleOpenProfile = (node: FavoriteRow) => {
    if (!node.creator) {
      return;
    }
    navigate(`/people-directory?username=${encodeURIComponent(node.creator)}`);
  };

  const handlePreview = (node: FavoriteRow, options?: { discuss?: boolean }) => {
    const discussionDraft = options?.discuss && node.creator ? `@${node.creator} ` : null;
    setPreviewNode(node);
    setPreviewCommentsOpen(Boolean(options?.discuss));
    setPreviewCommentDraftText(discussionDraft);
  };

  const handleClosePreview = () => {
    setPreviewNode(null);
    setPreviewCommentsOpen(false);
    setPreviewCommentDraftText(null);
  };

  const handleRemove = async (node: FavoriteRow) => {
    try {
      await favoriteService.remove(node.id);
      toast.success('Removed from favorites');
      await loadFavorites();
    } catch {
      toast.error('Failed to remove from favorites');
    }
  };

  const columns: GridColDef[] = [
    { field: 'name', headerName: 'Name', flex: 2 },
    { field: 'nodeType', headerName: 'Type', width: 120 },
    { field: 'path', headerName: 'Path', flex: 2, minWidth: 260 },
    {
      field: 'modified',
      headerName: 'Modified',
      width: 180,
      valueFormatter: (params) => {
        if (!params.value) return '-';
        try {
          return format(new Date(params.value as string), 'PPp');
        } catch {
          return '-';
        }
      },
    },
    {
      field: 'favoritedAt',
      headerName: 'Favorited At',
      width: 180,
      valueFormatter: (params) => {
        if (!params.value) return '-';
        try {
          return format(new Date(params.value as string), 'PPp');
        } catch {
          return '-';
        }
      },
    },
    {
      field: 'actions',
      headerName: '',
      width: 180,
      sortable: false,
      renderCell: (params) => (
        <Box display="flex" gap={1}>
          {(params.row as FavoriteRow).nodeType === 'FOLDER' ? (
            <IconButton
              size="small"
              aria-label={`Open ${String((params.row as FavoriteRow).name)}`}
              onClick={() => handleOpen(params.row as FavoriteRow)}
            >
              <FolderOpen fontSize="small" />
            </IconButton>
          ) : (
            <>
              <IconButton
                size="small"
                aria-label={`Preview ${String((params.row as FavoriteRow).name)}`}
                onClick={() => handlePreview(params.row as FavoriteRow)}
              >
                <Visibility fontSize="small" />
              </IconButton>
              <IconButton
                size="small"
                aria-label={`Discuss ${String((params.row as FavoriteRow).name)}`}
                onClick={() => handlePreview(params.row as FavoriteRow, { discuss: true })}
              >
                <ChatBubbleOutline fontSize="small" />
              </IconButton>
            </>
          )}
          {(params.row as FavoriteRow).creator && (
            <IconButton
              size="small"
              aria-label={`Open profile for ${String((params.row as FavoriteRow).creator)}`}
              onClick={() => handleOpenProfile(params.row as FavoriteRow)}
            >
              <Person fontSize="small" />
            </IconButton>
          )}
          <IconButton
            size="small"
            aria-label={`Inspect ${String((params.row as FavoriteRow).name)}`}
            onClick={() => handleInspect(params.row as FavoriteRow)}
          >
            <Visibility fontSize="small" />
          </IconButton>
          <IconButton
            size="small"
            aria-label={`Remove favorite ${String((params.row as FavoriteRow).name)}`}
            onClick={() => handleRemove(params.row as FavoriteRow)}
          >
            <Delete fontSize="small" />
          </IconButton>
        </Box>
      ),
    },
  ];

  return (
    <Box>
      <Paper sx={{ p: 2, mb: 2 }}>
        <Box display="flex" justifyContent="space-between" alignItems="center">
          <Box display="flex" alignItems="center" gap={1}>
            <Star fontSize="small" color="primary" />
            <Typography variant="h6">Favorites</Typography>
          </Box>
          <Button variant="outlined" startIcon={<Refresh />} onClick={loadFavorites}>
            Refresh
          </Button>
        </Box>
      </Paper>

      <Paper sx={{ height: 600 }}>
        {loading ? (
          <Box display="flex" justifyContent="center" p={4}>
            <CircularProgress />
          </Box>
        ) : (
          <DataGrid
            rows={items}
            columns={columns}
            autoHeight
            disableRowSelectionOnClick
            getRowId={(row) => row.id}
          />
        )}
      </Paper>

      {previewNode && (
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
            open={Boolean(previewNode)}
            onClose={handleClosePreview}
            node={previewNode}
            initialCommentsOpen={previewCommentsOpen}
            initialCommentDraftText={previewCommentDraftText}
          />
        </React.Suspense>
      )}
    </Box>
  );
};

export default FavoritesPage;
