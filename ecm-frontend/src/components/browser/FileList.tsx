import React from 'react';
import {
  DataGrid,
  GridColDef,
  GridRowSelectionModel,
  GridRenderCellParams,
} from '@mui/x-data-grid';
import {
  Box,
  Card,
  CardContent,
  Checkbox,
  IconButton,
  Typography,
  Chip,
  Menu,
  MenuItem,
  ListItemIcon,
  ListItemText,
  Tooltip,
} from '@mui/material';
import {
  Folder,
  InsertDriveFile,
  MoreVert,
  Download,
  Edit,
  Visibility,
  Delete,
  FileCopy,
  DriveFileMove,
  History,
  Security,
  Approval,
  LocalOffer,
  Category as CategoryIcon,
  Share as ShareIcon,
  AutoAwesome,
  Star,
  StarBorder,
} from '@mui/icons-material';
import { format } from 'date-fns';
import { useNavigate } from 'react-router-dom';
import { Node } from 'types';
import { useAppDispatch, useAppSelector } from 'store';
import { copyNode, deleteNodes, fetchChildren, moveNode, setSelectedNodes, toggleNodeSelection } from 'store/slices/nodeSlice';
import {
  setPropertiesDialogOpen,
  setPermissionsDialogOpen,
  setVersionHistoryDialogOpen,
  setSelectedNodeId,
  setTagManagerOpen,
  setCategoryManagerOpen,
  setShareLinkManagerOpen,
  setMlSuggestionsOpen,
} from 'store/slices/uiSlice';
import nodeService from 'services/nodeService';
import favoriteService from 'services/favoriteService';
import { toast } from 'react-toastify';
import MoveCopyDialog from 'components/dialogs/MoveCopyDialog';

interface FileListProps {
  nodes: Node[];
  onNodeDoubleClick?: (node: Node) => void;
  onStartWorkflow?: (node: Node) => void;
}

const FileList: React.FC<FileListProps> = ({ nodes, onNodeDoubleClick, onStartWorkflow }) => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { selectedNodes, currentNode } = useAppSelector((state) => state.node);
  const { user } = useAppSelector((state) => state.auth);
  const { compactMode, sortBy, sortAscending, viewMode } = useAppSelector((state) => state.ui);
  const canWrite = Boolean(user?.roles?.includes('ROLE_ADMIN') || user?.roles?.includes('ROLE_EDITOR'));
  const [contextMenu, setContextMenu] = React.useState<{
    mouseX: number;
    mouseY: number;
    node: Node;
  } | null>(null);
  const [moveCopyOpen, setMoveCopyOpen] = React.useState(false);
  const [moveCopyMode, setMoveCopyMode] = React.useState<'move' | 'copy'>('copy');
  const [moveCopySource, setMoveCopySource] = React.useState<Node | null>(null);
  const [favoriteIds, setFavoriteIds] = React.useState<Set<string>>(() => new Set());

  React.useEffect(() => {
    let cancelled = false;
    const ids = Array.from(new Set(nodes.map((n) => n.id))).filter(Boolean);
    void (async () => {
      try {
        const favorites = await favoriteService.checkBatch(ids);
        if (!cancelled) {
          setFavoriteIds(favorites);
        }
      } catch {
        // best-effort: keep what we have
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [nodes]);

  const formatFileSize = (bytes?: number): string => {
    if (!bytes) return '-';
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return `${(bytes / Math.pow(1024, i)).toFixed(2)} ${sizes[i]}`;
  };

  const formatModifiedDate = (value?: string) => {
    if (!value) return '-';
    try {
      return format(new Date(value), 'PPp');
    } catch {
      return '-';
    }
  };

  const getNameTypographySx = (name: string) => {
    const length = name?.length ?? 0;
    const isLong = length > 28;
    const isExtraLong = length > 40;
    const lineClamp = compactMode ? 2 : isLong ? 3 : 2;
    const fontSize = compactMode ? '0.85rem' : isExtraLong ? '0.95rem' : isLong ? '1rem' : undefined;

    return {
      display: '-webkit-box',
      WebkitLineClamp: lineClamp,
      WebkitBoxOrient: 'vertical',
      overflow: 'hidden',
      wordBreak: 'break-word',
      overflowWrap: 'anywhere',
      lineHeight: isExtraLong ? 1.15 : 1.25,
      ...(fontSize ? { fontSize } : {}),
    };
  };

  const handleContextMenu = (event: React.MouseEvent, node: Node) => {
    event.preventDefault();
    setContextMenu({ mouseX: event.clientX + 2, mouseY: event.clientY - 6, node });
  };

  const handleCloseContextMenu = () => {
    setContextMenu(null);
  };

  const toggleFavorite = async (node: Node) => {
    const isFav = favoriteIds.has(node.id);
    try {
      if (isFav) {
        await favoriteService.remove(node.id);
        toast.success('Removed from favorites');
        setFavoriteIds((prev) => {
          const next = new Set(prev);
          next.delete(node.id);
          return next;
        });
      } else {
        await favoriteService.add(node.id);
        toast.success('Added to favorites');
        setFavoriteIds((prev) => {
          const next = new Set(prev);
          next.add(node.id);
          return next;
        });
      }
    } catch {
      toast.error(isFav ? 'Failed to remove from favorites' : 'Failed to add to favorites');
    }
  };

  const handleEdit = (node: Node, permission: 'read' | 'write') => {
    navigate(`/editor/${node.id}?provider=wopi&permission=${permission}`);
    handleCloseContextMenu();
  };

  const handleDownload = async (node: Node) => {
    if (node.nodeType === 'DOCUMENT') {
      try {
        await nodeService.downloadDocument(node.id);
      } catch (error) {
        toast.error('Failed to download file');
      }
    }
    handleCloseContextMenu();
  };

  const handleProperties = (node: Node) => {
    dispatch(setSelectedNodeId(node.id));
    dispatch(setPropertiesDialogOpen(true));
    handleCloseContextMenu();
  };

  const handlePermissions = (node: Node) => {
    dispatch(setSelectedNodeId(node.id));
    dispatch(setPermissionsDialogOpen(true));
    handleCloseContextMenu();
  };

  const handleVersionHistory = (node: Node) => {
    if (node.nodeType === 'DOCUMENT') {
      dispatch(setSelectedNodeId(node.id));
      dispatch(setVersionHistoryDialogOpen(true));
    }
    handleCloseContextMenu();
  };

  const handleOpenTags = (node: Node) => {
    dispatch(setSelectedNodeId(node.id));
    dispatch(setTagManagerOpen(true));
    handleCloseContextMenu();
  };

  const handleOpenCategories = (node: Node) => {
    dispatch(setSelectedNodeId(node.id));
    dispatch(setCategoryManagerOpen(true));
    handleCloseContextMenu();
  };

  const handleOpenShareLinks = (node: Node) => {
    dispatch(setSelectedNodeId(node.id));
    dispatch(setShareLinkManagerOpen(true));
    handleCloseContextMenu();
  };

  const handleOpenMlSuggestions = (node: Node) => {
    dispatch(setSelectedNodeId(node.id));
    dispatch(setMlSuggestionsOpen(true));
    handleCloseContextMenu();
  };

  const handleDeleteNode = async (node: Node) => {
    if (window.confirm(`Delete "${node.name}"?`)) {
      try {
        await dispatch(deleteNodes([node.id])).unwrap();
        toast.success('Item deleted successfully');
      } catch {
        toast.error('Failed to delete item');
      }
    }
    handleCloseContextMenu();
  };

  const refreshCurrentFolder = async () => {
    const nodeId = currentNode?.id || 'root';
    await dispatch(fetchChildren({ nodeId, sortBy, ascending: sortAscending }));
  };

  const openMoveCopyDialog = (mode: 'move' | 'copy') => {
    if (!contextMenu) {
      return;
    }
    setMoveCopyMode(mode);
    setMoveCopySource(contextMenu.node);
    setMoveCopyOpen(true);
    handleCloseContextMenu();
  };

  const columns: GridColDef[] = [
    {
      field: 'favorite',
      headerName: '',
      width: 44,
      sortable: false,
      filterable: false,
      disableColumnMenu: true,
      renderCell: (params: GridRenderCellParams<Node>) => {
        const isFav = favoriteIds.has(params.row.id);
        return (
          <IconButton
            size="small"
            aria-label={`${isFav ? 'Unfavorite' : 'Favorite'} ${params.row.name}`}
            onClick={async (e) => {
              e.stopPropagation();
              e.preventDefault();
              await toggleFavorite(params.row);
            }}
          >
            {isFav ? <Star fontSize="small" /> : <StarBorder fontSize="small" />}
          </IconButton>
        );
      },
    },
    {
      field: 'name',
      headerName: 'Name',
      flex: 2,
      renderCell: (params: GridRenderCellParams<Node>) => (
        <Box sx={{ display: 'flex', alignItems: 'center', minWidth: 0 }}>
          {params.row.nodeType === 'FOLDER' ? (
            <Folder sx={{ mr: 1, color: 'primary.main' }} />
          ) : (
            <InsertDriveFile sx={{ mr: 1, color: 'text.secondary' }} />
          )}
          <Tooltip title={params.row.name} placement="top-start" arrow>
            <Typography variant="body2" sx={getNameTypographySx(params.row.name)}>
              {params.value}
            </Typography>
          </Tooltip>
          {params.row.aspects?.includes('cm:versionable') && (
            <Chip
              label={params.row.currentVersionLabel}
              size="small"
              sx={{ ml: 1 }}
            />
          )}
        </Box>
      ),
    },
    {
      field: 'modified',
      headerName: 'Modified',
      width: 180,
      valueFormatter: (params) => {
        if (!params.value) return '-';
        try {
          return format(new Date(params.value), 'PPp');
        } catch {
          return '-';
        }
      },
    },
    {
      field: 'modifier',
      headerName: 'Modified By',
      width: 150,
    },
    {
      field: 'size',
      headerName: 'Size',
      width: 100,
      valueFormatter: (params) => formatFileSize(params.value),
    },
    {
      field: 'actions',
      headerName: '',
      width: 50,
      sortable: false,
      renderCell: (params: GridRenderCellParams<Node>) => (
        <IconButton
          size="small"
          aria-label={`Actions for ${params.row.name}`}
          onClick={(e) => {
            e.stopPropagation();
            handleContextMenu(e, params.row);
          }}
        >
          <MoreVert />
        </IconButton>
      ),
    },
  ];

  const handleSelectionChange = (selectionModel: GridRowSelectionModel) => {
    dispatch(setSelectedNodes(selectionModel as string[]));
  };

  const handleToggleSelection = (nodeId: string) => {
    dispatch(toggleNodeSelection(nodeId));
  };

  const handleRowDoubleClick = (params: any) => {
    if (onNodeDoubleClick) {
      onNodeDoubleClick(params.row);
    }
  };

  const renderGridView = () => (
    <Box p={compactMode ? 1.5 : 2}>
      <Box component="div" display="grid" gridTemplateColumns={{ xs: '1fr', sm: 'repeat(2, 1fr)', md: 'repeat(3, 1fr)', lg: 'repeat(4, 1fr)' }} gap={compactMode ? 1.5 : 2}>
        {nodes.map((node) => {
          const isSelected = selectedNodes.includes(node.id);
          return (
            <Card
              key={node.id}
              variant="outlined"
              onClick={() => handleToggleSelection(node.id)}
              onDoubleClick={() => onNodeDoubleClick?.(node)}
              onContextMenu={(event) => handleContextMenu(event, node)}
              sx={{
                height: '100%',
                cursor: 'pointer',
                borderColor: isSelected ? 'primary.main' : 'divider',
                boxShadow: isSelected ? 3 : 1,
                transition: 'box-shadow 0.2s ease, border-color 0.2s ease',
                '&:hover': {
                  boxShadow: 4,
                },
              }}
            >
              <CardContent sx={{ display: 'flex', flexDirection: 'column', gap: compactMode ? 1 : 1.5, height: '100%' }}>
                <Box display="flex" alignItems="flex-start" gap={1} sx={{ minWidth: 0 }}>
                  <Checkbox
                    size="small"
                    checked={isSelected}
                    onClick={(event) => {
                      event.stopPropagation();
                      handleToggleSelection(node.id);
                    }}
                  />
                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    <Box display="flex" alignItems="center" gap={1} sx={{ minWidth: 0 }}>
                      {node.nodeType === 'FOLDER' ? (
                        <Folder sx={{ color: 'primary.main' }} />
                      ) : (
                        <InsertDriveFile sx={{ color: 'text.secondary' }} />
                      )}
                      <Tooltip title={node.name} placement="top-start" arrow>
                        <Typography variant="subtitle1" sx={getNameTypographySx(node.name)}>
                          {node.name}
                        </Typography>
                      </Tooltip>
                    </Box>
                    {node.currentVersionLabel && (
                      <Chip label={node.currentVersionLabel} size="small" sx={{ mt: 0.5 }} />
                    )}
                  </Box>
                  <Box display="flex" alignItems="center" gap={0.5}>
                    <IconButton
                      size="small"
                      aria-label={`${favoriteIds.has(node.id) ? 'Unfavorite' : 'Favorite'} ${node.name}`}
                      onClick={async (event) => {
                        event.stopPropagation();
                        await toggleFavorite(node);
                      }}
                    >
                      {favoriteIds.has(node.id) ? <Star fontSize="small" /> : <StarBorder fontSize="small" />}
                    </IconButton>
                    <IconButton
                      size="small"
                      aria-label={`Actions for ${node.name}`}
                      onClick={(event) => {
                        event.stopPropagation();
                        handleContextMenu(event, node);
                      }}
                    >
                      <MoreVert fontSize="small" />
                    </IconButton>
                  </Box>
                </Box>

                {node.description && (
                  <Typography
                    variant="body2"
                    color="text.secondary"
                    sx={{
                      display: '-webkit-box',
                      WebkitLineClamp: 2,
                      WebkitBoxOrient: 'vertical',
                      overflow: 'hidden',
                      wordBreak: 'break-word',
                    }}
                  >
                    {node.description}
                  </Typography>
                )}

                <Box display="flex" justifyContent="space-between" alignItems="center" mt="auto">
                  <Typography variant="caption" color="text.secondary">
                    Modified: {formatModifiedDate(node.modified)}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Size: {formatFileSize(node.size)}
                  </Typography>
                </Box>
              </CardContent>
            </Card>
          );
        })}
      </Box>
    </Box>
  );

  return (
    <>
      {viewMode === 'grid' ? (
        renderGridView()
      ) : (
        <DataGrid
          rows={nodes}
          columns={columns}
          density={compactMode ? 'compact' : 'standard'}
          getRowHeight={() => (compactMode ? 52 : 'auto')}
          rowSelectionModel={selectedNodes}
          onRowSelectionModelChange={handleSelectionChange}
          onRowDoubleClick={handleRowDoubleClick}
          checkboxSelection
          disableRowSelectionOnClick
          autoHeight
          sx={{
            border: 'none',
            '& .MuiDataGrid-cell': {
              cursor: 'pointer',
            },
          }}
        />
      )}

      <Menu
        open={contextMenu !== null}
        onClose={handleCloseContextMenu}
        anchorReference="anchorPosition"
        anchorPosition={
          contextMenu !== null
            ? { top: contextMenu.mouseY, left: contextMenu.mouseX }
            : undefined
        }
      >
        {contextMenu?.node.nodeType === 'DOCUMENT' && (
          <MenuItem
            onClick={() => contextMenu && handleEdit(contextMenu.node, canWrite ? 'write' : 'read')}
          >
            <ListItemIcon>
              {canWrite ? <Edit fontSize="small" /> : <Visibility fontSize="small" />}
            </ListItemIcon>
            <ListItemText>{canWrite ? 'Edit Online' : 'View Online'}</ListItemText>
          </MenuItem>
        )}
        {contextMenu?.node.nodeType === 'DOCUMENT' && (
          <MenuItem onClick={() => handleDownload(contextMenu.node)}>
            <ListItemIcon>
              <Download fontSize="small" />
            </ListItemIcon>
            <ListItemText>Download</ListItemText>
          </MenuItem>
        )}
        <MenuItem onClick={() => contextMenu && handleProperties(contextMenu.node)}>
          <ListItemIcon>
            <Edit fontSize="small" />
          </ListItemIcon>
          <ListItemText>Properties</ListItemText>
        </MenuItem>
        <MenuItem onClick={() => contextMenu && handlePermissions(contextMenu.node)}>
          <ListItemIcon>
            <Security fontSize="small" />
          </ListItemIcon>
          <ListItemText>Permissions</ListItemText>
        </MenuItem>
        {canWrite && contextMenu?.node.nodeType === 'DOCUMENT' && (
          <MenuItem onClick={() => contextMenu && handleOpenTags(contextMenu.node)}>
            <ListItemIcon>
              <LocalOffer fontSize="small" />
            </ListItemIcon>
            <ListItemText>Tags</ListItemText>
          </MenuItem>
        )}
        {canWrite && contextMenu?.node.nodeType === 'DOCUMENT' && (
          <MenuItem onClick={() => contextMenu && handleOpenCategories(contextMenu.node)}>
            <ListItemIcon>
              <CategoryIcon fontSize="small" />
            </ListItemIcon>
            <ListItemText>Categories</ListItemText>
          </MenuItem>
        )}
        {canWrite && contextMenu?.node.nodeType === 'DOCUMENT' && (
          <MenuItem onClick={() => contextMenu && handleOpenShareLinks(contextMenu.node)}>
            <ListItemIcon>
              <ShareIcon fontSize="small" />
            </ListItemIcon>
            <ListItemText>Share</ListItemText>
          </MenuItem>
        )}
        {canWrite && contextMenu?.node.nodeType === 'DOCUMENT' && (
          <MenuItem onClick={() => contextMenu && handleOpenMlSuggestions(contextMenu.node)}>
            <ListItemIcon>
              <AutoAwesome fontSize="small" />
            </ListItemIcon>
            <ListItemText>ML Suggestions</ListItemText>
          </MenuItem>
        )}
        {canWrite && contextMenu?.node.nodeType === 'DOCUMENT' && onStartWorkflow && (
          <MenuItem onClick={() => {
            if (contextMenu) onStartWorkflow(contextMenu.node);
            handleCloseContextMenu();
          }}>
            <ListItemIcon>
              <Approval fontSize="small" />
            </ListItemIcon>
            <ListItemText>Start Approval</ListItemText>
          </MenuItem>
        )}
        {contextMenu?.node.nodeType === 'DOCUMENT' && (
          <MenuItem onClick={() => handleVersionHistory(contextMenu.node)}>
            <ListItemIcon>
              <History fontSize="small" />
            </ListItemIcon>
            <ListItemText>Version History</ListItemText>
          </MenuItem>
        )}
        <MenuItem divider />
        {canWrite && (
          <MenuItem onClick={() => openMoveCopyDialog('copy')}>
            <ListItemIcon>
              <FileCopy fontSize="small" />
            </ListItemIcon>
            <ListItemText>Copy</ListItemText>
          </MenuItem>
        )}
        {canWrite && (
          <MenuItem onClick={() => openMoveCopyDialog('move')}>
            <ListItemIcon>
              <DriveFileMove fontSize="small" />
            </ListItemIcon>
            <ListItemText>Move</ListItemText>
          </MenuItem>
        )}
        <MenuItem
          onClick={async () => {
            if (contextMenu) {
              await toggleFavorite(contextMenu.node);
            }
            handleCloseContextMenu();
          }}
        >
          <ListItemIcon>
            {contextMenu?.node && favoriteIds.has(contextMenu.node.id) ? <Star fontSize="small" /> : <StarBorder fontSize="small" />}
          </ListItemIcon>
          <ListItemText>{contextMenu?.node && favoriteIds.has(contextMenu.node.id) ? 'Unfavorite' : 'Add to Favorites'}</ListItemText>
        </MenuItem>
        {canWrite && (
          <MenuItem onClick={() => contextMenu && handleDeleteNode(contextMenu.node)}>
            <ListItemIcon>
              <Delete fontSize="small" />
            </ListItemIcon>
            <ListItemText>Delete</ListItemText>
          </MenuItem>
        )}
      </Menu>

      <MoveCopyDialog
        open={moveCopyOpen}
        mode={moveCopyMode}
        sourceNode={moveCopySource}
        initialFolderId={currentNode?.id}
        initialFolderName={currentNode?.name}
        onClose={() => {
          setMoveCopyOpen(false);
          setMoveCopySource(null);
        }}
        onConfirm={async (targetFolderId, options) => {
          if (!moveCopySource) {
            return;
          }
          try {
            if (moveCopyMode === 'copy') {
              await dispatch(
                copyNode({
                  nodeId: moveCopySource.id,
                  targetParentId: targetFolderId,
                  deepCopy: moveCopySource.nodeType === 'FOLDER',
                  newName: options.newName,
                })
              ).unwrap();
              toast.success('Item copied successfully');
            } else {
              await dispatch(
                moveNode({
                  nodeId: moveCopySource.id,
                  targetParentId: targetFolderId,
                })
              ).unwrap();
              toast.success('Item moved successfully');
            }
            dispatch(setSelectedNodes([]));
            setMoveCopyOpen(false);
            setMoveCopySource(null);
            await refreshCurrentFolder();
          } catch {
            toast.error(moveCopyMode === 'copy' ? 'Failed to copy item' : 'Failed to move item');
          }
        }}
      />
    </>
  );
};

export default FileList;
