import React from 'react';
import {
  DataGrid,
  GridColDef,
  GridRowSelectionModel,
  GridRenderCellParams,
} from '@mui/x-data-grid';
import {
  Box,
  IconButton,
  Typography,
  Chip,
  Menu,
  MenuItem,
  ListItemIcon,
  ListItemText,
} from '@mui/material';
import {
  Folder,
  InsertDriveFile,
  MoreVert,
  Download,
  Edit,
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
} from '@mui/icons-material';
import { format } from 'date-fns';
import { useNavigate } from 'react-router-dom';
import { Node } from 'types';
import { useAppDispatch, useAppSelector } from 'store';
import { deleteNodes, setSelectedNodes } from 'store/slices/nodeSlice';
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
import { toast } from 'react-toastify';

interface FileListProps {
  nodes: Node[];
  onNodeDoubleClick?: (node: Node) => void;
  onStartWorkflow?: (node: Node) => void;
}

const FileList: React.FC<FileListProps> = ({ nodes, onNodeDoubleClick, onStartWorkflow }) => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { selectedNodes } = useAppSelector((state) => state.node);
  const [contextMenu, setContextMenu] = React.useState<{
    mouseX: number;
    mouseY: number;
    node: Node;
  } | null>(null);

  const formatFileSize = (bytes?: number): string => {
    if (!bytes) return '-';
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return `${(bytes / Math.pow(1024, i)).toFixed(2)} ${sizes[i]}`;
  };

  const handleContextMenu = (event: React.MouseEvent, node: Node) => {
    event.preventDefault();
    setContextMenu(
      contextMenu === null
        ? { mouseX: event.clientX + 2, mouseY: event.clientY - 6, node }
        : null
    );
  };

  const handleCloseContextMenu = () => {
    setContextMenu(null);
  };

  const handleEdit = (node: Node) => {
    navigate(`/editor/${node.id}?provider=wps&permission=write`);
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

  const columns: GridColDef[] = [
    {
      field: 'name',
      headerName: 'Name',
      flex: 2,
      renderCell: (params: GridRenderCellParams<Node>) => (
        <Box sx={{ display: 'flex', alignItems: 'center' }}>
          {params.row.nodeType === 'FOLDER' ? (
            <Folder sx={{ mr: 1, color: 'primary.main' }} />
          ) : (
            <InsertDriveFile sx={{ mr: 1, color: 'text.secondary' }} />
          )}
          <Typography variant="body2">{params.value}</Typography>
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

  const handleRowDoubleClick = (params: any) => {
    if (onNodeDoubleClick) {
      onNodeDoubleClick(params.row);
    }
  };

  return (
    <>
      <DataGrid
        rows={nodes}
        columns={columns}
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
          <MenuItem onClick={() => contextMenu && handleEdit(contextMenu.node)}>
            <ListItemIcon>
              <Edit fontSize="small" />
            </ListItemIcon>
            <ListItemText>Edit Online</ListItemText>
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
        {contextMenu?.node.nodeType === 'DOCUMENT' && (
          <MenuItem onClick={() => contextMenu && handleOpenTags(contextMenu.node)}>
            <ListItemIcon>
              <LocalOffer fontSize="small" />
            </ListItemIcon>
            <ListItemText>Tags</ListItemText>
          </MenuItem>
        )}
        {contextMenu?.node.nodeType === 'DOCUMENT' && (
          <MenuItem onClick={() => contextMenu && handleOpenCategories(contextMenu.node)}>
            <ListItemIcon>
              <CategoryIcon fontSize="small" />
            </ListItemIcon>
            <ListItemText>Categories</ListItemText>
          </MenuItem>
        )}
        {contextMenu?.node.nodeType === 'DOCUMENT' && (
          <MenuItem onClick={() => contextMenu && handleOpenShareLinks(contextMenu.node)}>
            <ListItemIcon>
              <ShareIcon fontSize="small" />
            </ListItemIcon>
            <ListItemText>Share</ListItemText>
          </MenuItem>
        )}
        {contextMenu?.node.nodeType === 'DOCUMENT' && (
          <MenuItem onClick={() => contextMenu && handleOpenMlSuggestions(contextMenu.node)}>
            <ListItemIcon>
              <AutoAwesome fontSize="small" />
            </ListItemIcon>
            <ListItemText>ML Suggestions</ListItemText>
          </MenuItem>
        )}
        {contextMenu?.node.nodeType === 'DOCUMENT' && onStartWorkflow && (
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
        <MenuItem onClick={handleCloseContextMenu}>
          <ListItemIcon>
            <FileCopy fontSize="small" />
          </ListItemIcon>
          <ListItemText>Copy</ListItemText>
        </MenuItem>
        <MenuItem onClick={handleCloseContextMenu}>
          <ListItemIcon>
            <DriveFileMove fontSize="small" />
          </ListItemIcon>
          <ListItemText>Move</ListItemText>
        </MenuItem>
        <MenuItem onClick={() => contextMenu && handleDeleteNode(contextMenu.node)}>
          <ListItemIcon>
            <Delete fontSize="small" />
          </ListItemIcon>
          <ListItemText>Delete</ListItemText>
        </MenuItem>
      </Menu>
    </>
  );
};

export default FileList;
