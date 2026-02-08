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
  Button,
  IconButton,
  Typography,
  Chip,
  Pagination,
  Menu,
  MenuItem,
  ListItemIcon,
  ListItemText,
  Tooltip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
} from '@mui/material';
import { SxProps, Theme } from '@mui/material/styles';
import {
  Folder,
  InsertDriveFile,
  MoreVert,
  Download,
  Edit,
  Visibility,
  Delete,
  ContentCopy,
  FileCopy,
  DriveFileMove,
  History,
  Security,
  Approval,
  LocalOffer,
  Category as CategoryIcon,
  Share as ShareIcon,
  AutoAwesome,
  InfoOutlined,
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
import { getFailedPreviewMeta } from 'utils/previewStatusUtils';

interface FileListProps {
  nodes: Node[];
  onNodeDoubleClick?: (node: Node) => void;
  onPreviewNode?: (node: Node, options?: { annotate?: boolean }) => void;
  onStartWorkflow?: (node: Node) => void;
  page: number;
  pageSize: number;
  totalCount: number;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
}

const FileList: React.FC<FileListProps> = ({
  nodes,
  onNodeDoubleClick,
  onPreviewNode,
  onStartWorkflow,
  page,
  pageSize,
  totalCount,
  onPageChange,
  onPageSizeChange,
}) => {
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
  const [nameDialogNode, setNameDialogNode] = React.useState<Node | null>(null);
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

  const getFileTypeLabel = (node: Node) => {
    if (node.nodeType === 'FOLDER') return null;
    const type = node.contentType?.toLowerCase();
    if (type) {
      if (type.includes('pdf')) return 'PDF';
      if (type.includes('word')) return 'Word';
      if (type.includes('excel') || type.includes('spreadsheet')) return 'Excel';
      if (type.includes('powerpoint') || type.includes('presentation')) return 'PPT';
      if (type.startsWith('image/')) return 'Image';
      if (type.startsWith('text/')) return 'Text';
    }

    const name = node.name?.trim().toLowerCase() || '';
    if (name.endsWith('.pdf')) return 'PDF';
    if (name.endsWith('.doc') || name.endsWith('.docx')) return 'Word';
    if (name.endsWith('.xls') || name.endsWith('.xlsx')) return 'Excel';
    if (name.endsWith('.ppt') || name.endsWith('.pptx')) return 'PPT';
    if (name.endsWith('.png') || name.endsWith('.jpg') || name.endsWith('.jpeg')) return 'Image';
    if (name.endsWith('.txt')) return 'Text';

    return 'File';
  };

  const getPreviewStatusMeta = (node: Node) => {
    const status = node.previewStatus?.toUpperCase();
    if (!status || status === 'READY') {
      return null;
    }
    if (status === 'FAILED') {
      const mimeType = node.contentType || node.properties?.mimeType || node.properties?.contentType;
      return getFailedPreviewMeta(mimeType, node.previewFailureCategory, node.previewFailureReason);
    }
    if (status === 'PROCESSING') {
      return { label: 'Preview processing', color: 'warning' as const };
    }
    if (status === 'QUEUED') {
      return { label: 'Preview queued', color: 'info' as const };
    }
    return { label: `Preview ${status.toLowerCase()}`, color: 'default' as const };
  };

  const isDocumentNode = (node: Node) => {
    if (node.nodeType === 'DOCUMENT') {
      return true;
    }
    if (node.nodeType === 'FOLDER') {
      return false;
    }
    const name = node.name?.trim().toLowerCase() || '';
    const contentTypeHint = node.contentType
      || node.properties?.mimeType
      || node.properties?.contentType;
    const sizeHint = node.size
      || node.properties?.fileSize
      || node.properties?.size;
    const hasExtension = name.includes('.') && !name.endsWith('.');
    return Boolean(contentTypeHint || sizeHint || node.currentVersionLabel || hasExtension);
  };

  const isOfficeDocument = (node: Node) => {
    const contentType = node.contentType
      || node.properties?.mimeType
      || node.properties?.contentType;
    const normalizedType = contentType?.toLowerCase();
    if (normalizedType) {
      const officeTypes = new Set([
        'application/msword',
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        'application/vnd.ms-excel',
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        'application/vnd.ms-powerpoint',
        'application/vnd.openxmlformats-officedocument.presentationml.presentation',
        'application/vnd.oasis.opendocument.text',
        'application/vnd.oasis.opendocument.spreadsheet',
        'application/vnd.oasis.opendocument.presentation',
        'application/rtf',
        'text/rtf',
      ]);
      if (officeTypes.has(normalizedType)) {
        return true;
      }
    }
    const name = node.name?.trim().toLowerCase() || '';
    return [
      '.doc',
      '.docx',
      '.xls',
      '.xlsx',
      '.ppt',
      '.pptx',
      '.odt',
      '.ods',
      '.odp',
      '.rtf',
    ].some((ext) => name.endsWith(ext));
  };

  const isPdfDocument = (node: Node) => {
    const contentType = node.contentType
      || node.properties?.mimeType
      || node.properties?.contentType;
    const normalizedType = contentType?.toLowerCase();
    if (normalizedType && normalizedType.includes('pdf')) {
      return true;
    }
    const name = node.name?.toLowerCase() || '';
    return name.endsWith('.pdf');
  };

  const formatModifiedDate = (value?: string) => {
    if (!value) return '-';
    try {
      return format(new Date(value), 'PPp');
    } catch {
      return '-';
    }
  };

  const getVisualLength = (value?: string) => {
    if (!value) return 0;
    let total = 0;
    for (const char of value) {
      total += char.charCodeAt(0) > 0xff ? 2 : 1;
    }
    return total;
  };

  const getNameTypographySx = (name: string, variant: 'grid' | 'list'): SxProps<Theme> => {
    const base: SxProps<Theme> = {
      display: 'block',
      minWidth: 0,
      maxWidth: '100%',
      lineHeight: 1.25,
    };

    if (variant === 'list') {
      return {
        ...base,
        display: '-webkit-box',
        flex: 1,
        WebkitLineClamp: 2,
        WebkitBoxOrient: 'vertical',
        overflow: 'hidden',
        wordBreak: 'break-word',
        overflowWrap: 'anywhere',
        whiteSpace: 'normal',
      };
    }

    const length = getVisualLength(name);
    const longThreshold = compactMode ? 20 : 24;
    const extraLongThreshold = compactMode ? 34 : 40;
    const veryLongThreshold = compactMode ? 50 : 60;
    const isLong = length > longThreshold;
    const isExtraLong = length > extraLongThreshold;
    const isVeryLong = length > veryLongThreshold;
    const lineClamp = isLong ? 3 : 2;
    const lineHeight = lineClamp === 3
      ? (compactMode ? (isVeryLong ? 1.04 : isExtraLong ? 1.08 : 1.12) : (isVeryLong ? 1.05 : isExtraLong ? 1.1 : 1.16))
      : 1.25;
    const fontSize = lineClamp === 3
      ? compactMode
        ? (isVeryLong ? '0.72rem' : isExtraLong ? '0.76rem' : '0.8rem')
        : (isVeryLong ? '0.8rem' : isExtraLong ? '0.86rem' : '0.9rem')
      : undefined;

    return {
      ...base,
      display: '-webkit-box',
      WebkitLineClamp: lineClamp,
      WebkitBoxOrient: 'vertical',
      overflow: 'hidden',
      wordBreak: 'break-word',
      overflowWrap: 'anywhere',
      whiteSpace: 'normal',
      lineHeight,
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

  const handlePreview = (node: Node, options?: { annotate?: boolean }) => {
    if (onPreviewNode) {
      onPreviewNode(node, options);
    }
    handleCloseContextMenu();
  };

  const handleAnnotate = (node: Node) => {
    handlePreview(node, { annotate: true });
  };

  const handleDownload = async (node: Node) => {
    if (isDocumentNode(node)) {
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
    if (isDocumentNode(node)) {
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
    await dispatch(fetchChildren({ nodeId, sortBy, ascending: sortAscending, page, size: pageSize }));
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

  const openNameDialog = (node: Node) => {
    setNameDialogNode(node);
    handleCloseContextMenu();
  };

  const handleCopyName = async (value?: string) => {
    if (!value) {
      return;
    }
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(value);
      } else {
        const textarea = document.createElement('textarea');
        textarea.value = value;
        textarea.style.position = 'fixed';
        textarea.style.opacity = '0';
        document.body.appendChild(textarea);
        textarea.focus();
        textarea.select();
        const copied = document.execCommand('copy');
        document.body.removeChild(textarea);
        if (!copied) {
          throw new Error('copy failed');
        }
      }
      toast.success('Copied file name');
    } catch (error) {
      console.error('Failed to copy name', error);
      toast.error('Failed to copy file name');
    }
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
      renderCell: (params: GridRenderCellParams<Node>) => {
        const previewMeta = params.row.nodeType !== 'FOLDER' ? getPreviewStatusMeta(params.row) : null;
        return (
          <Box sx={{ display: 'flex', alignItems: 'center', minWidth: 0 }}>
          {params.row.nodeType === 'FOLDER' ? (
            <Folder sx={{ mr: 1, color: 'primary.main' }} />
          ) : (
            <InsertDriveFile sx={{ mr: 1, color: 'text.secondary' }} />
          )}
          <Tooltip title={params.row.name} placement="top-start" arrow>
            <Typography variant="body2" sx={getNameTypographySx(params.row.name, 'list')}>
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
          {previewMeta && (
            <Box display="flex" alignItems="center" gap={0.5} ml={1}>
              <Tooltip
                title={params.row.previewFailureReason || ''}
                placement="top-start"
                arrow
                disableHoverListener={!params.row.previewFailureReason}
              >
                <Chip
                  label={previewMeta.label}
                  color={previewMeta.color}
                  size="small"
                  variant="outlined"
                />
              </Tooltip>
              {params.row.previewStatus?.toUpperCase() === 'FAILED' && params.row.previewFailureReason && (
                <Tooltip title={params.row.previewFailureReason} placement="top-start" arrow>
                  <IconButton size="small" aria-label="Preview failure reason">
                    <InfoOutlined fontSize="small" />
                  </IconButton>
                </Tooltip>
              )}
            </Box>
          )}
        </Box>
        );
      },
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

  const renderGridView = () => {
    const totalPages = Math.max(1, Math.ceil(totalCount / pageSize));
    const gridMinWidth = compactMode ? 220 : 260;

    return (
      <Box p={compactMode ? 1 : 1.5}>
        <Box
          component="div"
          display="grid"
          gridTemplateColumns={`repeat(auto-fit, minmax(${gridMinWidth}px, 1fr))`}
          gap={compactMode ? 1 : 1.5}
        >
        {nodes.map((node) => {
          const isSelected = selectedNodes.includes(node.id);
          const fileTypeLabel = getFileTypeLabel(node);
          const previewMeta = node.nodeType !== 'FOLDER' ? getPreviewStatusMeta(node) : null;
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
                        <Typography variant="subtitle1" sx={getNameTypographySx(node.name, 'grid')}>
                          {node.name}
                        </Typography>
                      </Tooltip>
                    </Box>
                    {node.currentVersionLabel && (
                      <Chip label={node.currentVersionLabel} size="small" sx={{ mt: 0.5 }} />
                    )}
                    {fileTypeLabel && (
                      <Box display="flex" gap={0.5} flexWrap="wrap" mt={0.5}>
                        <Chip label={fileTypeLabel} size="small" variant="outlined" />
                        {previewMeta && (
                          <Box display="flex" alignItems="center" gap={0.5}>
                            <Tooltip
                              title={node.previewFailureReason || ''}
                              placement="top-start"
                              arrow
                              disableHoverListener={!node.previewFailureReason}
                            >
                              <Chip
                                label={previewMeta.label}
                                color={previewMeta.color}
                                size="small"
                                variant="outlined"
                              />
                            </Tooltip>
                            {node.previewStatus?.toUpperCase() === 'FAILED' && node.previewFailureReason && (
                              <Tooltip title={node.previewFailureReason} placement="top-start" arrow>
                                <IconButton size="small" aria-label="Preview failure reason">
                                  <InfoOutlined fontSize="small" />
                                </IconButton>
                              </Tooltip>
                            )}
                          </Box>
                        )}
                      </Box>
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
        {totalPages > 1 && (
          <Box display="flex" justifyContent="center" mt={compactMode ? 2 : 3}>
            <Pagination
              count={totalPages}
              page={page + 1}
              onChange={(_, value) => onPageChange(value - 1)}
              color="primary"
              size={compactMode ? 'small' : 'medium'}
            />
          </Box>
        )}
      </Box>
    );
  };

  return (
    <>
      {viewMode === 'grid' ? (
        renderGridView()
      ) : (
        <DataGrid
          rows={nodes}
          columns={columns}
          density={compactMode ? 'compact' : 'standard'}
          getRowHeight={() => 'auto'}
          paginationMode="server"
          pagination
          rowCount={totalCount}
          paginationModel={{ page, pageSize }}
          onPaginationModelChange={(model) => {
            if (model.pageSize !== pageSize) {
              onPageSizeChange(model.pageSize);
            } else if (model.page !== page) {
              onPageChange(model.page);
            }
          }}
          pageSizeOptions={[25, 50, 100]}
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
            '& .MuiDataGrid-cell[data-field="name"]': {
              whiteSpace: 'normal',
              alignItems: 'flex-start',
              lineHeight: 1.3,
              py: 1,
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
        {contextMenu && isDocumentNode(contextMenu.node) && (
          <MenuItem onClick={() => contextMenu && handlePreview(contextMenu.node)}>
            <ListItemIcon>
              <Visibility fontSize="small" />
            </ListItemIcon>
            <ListItemText>View</ListItemText>
          </MenuItem>
        )}
        {contextMenu && isDocumentNode(contextMenu.node) && isPdfDocument(contextMenu.node) && canWrite && (
          <MenuItem onClick={() => contextMenu && handleAnnotate(contextMenu.node)}>
            <ListItemIcon>
              <Edit fontSize="small" />
            </ListItemIcon>
            <ListItemText>Annotate (PDF)</ListItemText>
          </MenuItem>
        )}
        {contextMenu && isDocumentNode(contextMenu.node) && isOfficeDocument(contextMenu.node) && !isPdfDocument(contextMenu.node) && (
          <MenuItem
            onClick={() => contextMenu && handleEdit(contextMenu.node, canWrite ? 'write' : 'read')}
          >
            <ListItemIcon>
              {canWrite ? <Edit fontSize="small" /> : <Visibility fontSize="small" />}
            </ListItemIcon>
            <ListItemText>{canWrite ? 'Edit Online' : 'View Online'}</ListItemText>
          </MenuItem>
        )}
        {contextMenu && isDocumentNode(contextMenu.node) && (
          <MenuItem onClick={() => handleDownload(contextMenu.node)}>
            <ListItemIcon>
              <Download fontSize="small" />
            </ListItemIcon>
            <ListItemText>Download</ListItemText>
          </MenuItem>
        )}
        {contextMenu && (
          <MenuItem onClick={() => openNameDialog(contextMenu.node)}>
            <ListItemIcon>
              <ContentCopy fontSize="small" />
            </ListItemIcon>
            <ListItemText>Full Name</ListItemText>
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
        {canWrite && contextMenu && isDocumentNode(contextMenu.node) && (
          <MenuItem onClick={() => contextMenu && handleOpenTags(contextMenu.node)}>
            <ListItemIcon>
              <LocalOffer fontSize="small" />
            </ListItemIcon>
            <ListItemText>Tags</ListItemText>
          </MenuItem>
        )}
        {canWrite && contextMenu && isDocumentNode(contextMenu.node) && (
          <MenuItem onClick={() => contextMenu && handleOpenCategories(contextMenu.node)}>
            <ListItemIcon>
              <CategoryIcon fontSize="small" />
            </ListItemIcon>
            <ListItemText>Categories</ListItemText>
          </MenuItem>
        )}
        {canWrite && contextMenu && isDocumentNode(contextMenu.node) && (
          <MenuItem onClick={() => contextMenu && handleOpenShareLinks(contextMenu.node)}>
            <ListItemIcon>
              <ShareIcon fontSize="small" />
            </ListItemIcon>
            <ListItemText>Share</ListItemText>
          </MenuItem>
        )}
        {canWrite && contextMenu && isDocumentNode(contextMenu.node) && (
          <MenuItem onClick={() => contextMenu && handleOpenMlSuggestions(contextMenu.node)}>
            <ListItemIcon>
              <AutoAwesome fontSize="small" />
            </ListItemIcon>
            <ListItemText>ML Suggestions</ListItemText>
          </MenuItem>
        )}
        {canWrite && contextMenu && isDocumentNode(contextMenu.node) && onStartWorkflow && (
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
        {contextMenu && isDocumentNode(contextMenu.node) && (
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

      <Dialog
        open={Boolean(nameDialogNode)}
        onClose={() => setNameDialogNode(null)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Full Name</DialogTitle>
        <DialogContent dividers>
          <Typography variant="body2" sx={{ wordBreak: 'break-all', whiteSpace: 'pre-wrap' }}>
            {nameDialogNode?.name || '-'}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => handleCopyName(nameDialogNode?.name)} disabled={!nameDialogNode?.name}>
            Copy
          </Button>
          <Button onClick={() => setNameDialogNode(null)}>Close</Button>
        </DialogActions>
      </Dialog>

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
