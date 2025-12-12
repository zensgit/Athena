import React, { useCallback, useEffect, useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  IconButton,
  Typography,
  Chip,
  Menu,
  MenuItem,
  ListItemIcon,
  ListItemText,
  CircularProgress,
  Box,
} from '@mui/material';
import {
  Close,
  Download,
  Restore,
  Compare,
  MoreVert,
} from '@mui/icons-material';
import { format } from 'date-fns';
import { Version } from 'types';
import { useAppDispatch, useAppSelector } from 'store';
import { setVersionHistoryDialogOpen } from 'store/slices/uiSlice';
import nodeService from 'services/nodeService';
import { toast } from 'react-toastify';

const VersionHistoryDialog: React.FC = () => {
  const dispatch = useAppDispatch();
  const { versionHistoryDialogOpen, selectedNodeId } = useAppSelector((state) => state.ui);
  const [versions, setVersions] = useState<Version[]>([]);
  const [loading, setLoading] = useState(false);
  const [contextMenu, setContextMenu] = useState<{
    mouseX: number;
    mouseY: number;
    version: Version;
  } | null>(null);

  const loadVersionHistory = useCallback(async () => {
    if (!selectedNodeId) return;

    setLoading(true);
    try {
      const versionHistory = await nodeService.getVersionHistory(selectedNodeId);
      setVersions(versionHistory);
    } catch (error) {
      toast.error('Failed to load version history');
    } finally {
      setLoading(false);
    }
  }, [selectedNodeId]);

  useEffect(() => {
    if (versionHistoryDialogOpen && selectedNodeId) {
      loadVersionHistory();
    }
  }, [versionHistoryDialogOpen, selectedNodeId, loadVersionHistory]);

  const handleClose = () => {
    dispatch(setVersionHistoryDialogOpen(false));
    setVersions([]);
  };

  const handleContextMenu = (event: React.MouseEvent, version: Version) => {
    event.preventDefault();
    setContextMenu(
      contextMenu === null
        ? { mouseX: event.clientX + 2, mouseY: event.clientY - 6, version }
        : null
    );
  };

  const handleCloseContextMenu = () => {
    setContextMenu(null);
  };

  const handleDownloadVersion = async (version: Version) => {
    if (!selectedNodeId) return;

    try {
      await nodeService.downloadVersion(selectedNodeId, version.id);
      toast.success('Version downloaded successfully');
    } catch (error) {
      toast.error('Failed to download version');
    }
    handleCloseContextMenu();
  };

  const handleRestoreVersion = async (version: Version) => {
    if (!selectedNodeId) return;

    if (window.confirm(`Restore document to version ${version.versionLabel}?`)) {
      try {
        await nodeService.revertToVersion(selectedNodeId, version.id);
        toast.success('Document restored successfully');
        handleClose();
      } catch (error) {
        toast.error('Failed to restore version');
      }
    }
    handleCloseContextMenu();
  };

  const formatFileSize = (bytes: number): string => {
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return `${(bytes / Math.pow(1024, i)).toFixed(2)} ${sizes[i]}`;
  };

  return (
    <Dialog
      open={versionHistoryDialogOpen}
      onClose={handleClose}
      maxWidth="md"
      fullWidth
    >
      <DialogTitle>
        Version History
        <IconButton
          aria-label="close"
          onClick={handleClose}
          sx={{ position: 'absolute', right: 8, top: 8 }}
        >
          <Close />
        </IconButton>
      </DialogTitle>
      <DialogContent>
        {loading ? (
          <Box display="flex" justifyContent="center" p={4}>
            <CircularProgress />
          </Box>
        ) : versions.length === 0 ? (
          <Typography variant="body1" color="text.secondary" align="center" sx={{ py: 4 }}>
            No version history available
          </Typography>
        ) : (
          <TableContainer component={Paper} variant="outlined">
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Version</TableCell>
                  <TableCell>Date</TableCell>
                  <TableCell>Created By</TableCell>
                  <TableCell>Size</TableCell>
                  <TableCell>Comment</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {versions.map((version, index) => (
                  <TableRow key={version.id} hover>
                    <TableCell>
                      <Box display="flex" alignItems="center" gap={1}>
                        {version.versionLabel}
                        {version.isMajor && (
                          <Chip label="Major" size="small" color="primary" />
                        )}
                        {index === 0 && (
                          <Chip label="Current" size="small" color="success" />
                        )}
                      </Box>
                    </TableCell>
                    <TableCell>
                      {format(new Date(version.created), 'PPp')}
                    </TableCell>
                    <TableCell>{version.creator}</TableCell>
                    <TableCell>{formatFileSize(version.size)}</TableCell>
                    <TableCell>
                      <Typography variant="body2" noWrap sx={{ maxWidth: 200 }}>
                        {version.comment || '-'}
                      </Typography>
                    </TableCell>
                    <TableCell align="right">
                      <IconButton
                        size="small"
                        onClick={(e) => handleContextMenu(e, version)}
                      >
                        <MoreVert />
                      </IconButton>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
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
          <MenuItem onClick={() => contextMenu && handleDownloadVersion(contextMenu.version)}>
            <ListItemIcon>
              <Download fontSize="small" />
            </ListItemIcon>
            <ListItemText>Download this version</ListItemText>
          </MenuItem>
          <MenuItem onClick={() => contextMenu && handleRestoreVersion(contextMenu.version)}>
            <ListItemIcon>
              <Restore fontSize="small" />
            </ListItemIcon>
            <ListItemText>Restore to this version</ListItemText>
          </MenuItem>
          <MenuItem disabled>
            <ListItemIcon>
              <Compare fontSize="small" />
            </ListItemIcon>
            <ListItemText>Compare versions</ListItemText>
          </MenuItem>
        </Menu>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
};

export default VersionHistoryDialog;
