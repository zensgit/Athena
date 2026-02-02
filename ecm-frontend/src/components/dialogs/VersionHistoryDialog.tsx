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
  Tooltip,
  Alert,
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
  const [pendingAction, setPendingAction] = useState<{
    type: 'download' | 'restore';
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
  };

  const handleRestoreVersion = async (version: Version) => {
    if (!selectedNodeId) return;

    try {
      await nodeService.revertToVersion(selectedNodeId, version.id);
      toast.success('Document restored successfully');
      handleClose();
    } catch (error) {
      toast.error('Failed to restore version');
    }
  };

  const handleConfirmAction = async () => {
    if (!pendingAction) {
      return;
    }
    const { type, version } = pendingAction;
    if (type === 'download') {
      await handleDownloadVersion(version);
    } else {
      await handleRestoreVersion(version);
    }
    setPendingAction(null);
  };

  const formatFileSize = (bytes: number): string => {
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return `${(bytes / Math.pow(1024, i)).toFixed(2)} ${sizes[i]}`;
  };

  const formatSizeDelta = (current: Version, previous?: Version | null) => {
    if (!previous) {
      return 'Baseline';
    }
    const delta = current.size - previous.size;
    if (delta === 0) {
      return 'No change';
    }
    const sign = delta > 0 ? '+' : '−';
    const absDelta = Math.abs(delta);
    return `${sign}${formatFileSize(absDelta)}`;
  };

  const renderVersionLabel = (version: Version, index: number) => {
    const hasDetails = Boolean(version.mimeType || version.contentHash || version.contentId || version.status);
    const label = (
      <Box component="span" display="flex" alignItems="center" gap={1}>
        {version.versionLabel}
        {version.isMajor && (
          <Chip label="Major" size="small" color="primary" />
        )}
        {index === 0 && (
          <Chip label="Current" size="small" color="success" />
        )}
      </Box>
    );

    if (!hasDetails) {
      return label;
    }

    const details = (
      <Box>
        <Typography variant="caption" display="block">Mime: {version.mimeType ?? '-'}</Typography>
        <Typography variant="caption" display="block">Hash: {version.contentHash ?? '-'}</Typography>
        <Typography variant="caption" display="block">Content ID: {version.contentId ?? '-'}</Typography>
        <Typography variant="caption" display="block">Status: {version.status ?? '-'}</Typography>
      </Box>
    );

    return (
      <Tooltip title={details} placement="top-start" arrow>
        {label}
      </Tooltip>
    );
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
        <Alert severity="info" sx={{ mb: 2 }}>
          Download and restore actions are recorded in the audit log.
        </Alert>
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
                  <TableCell>Delta</TableCell>
                  <TableCell>Comment</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {versions.map((version, index) => (
                  <TableRow key={version.id} hover>
                    <TableCell>
                      {renderVersionLabel(version, index)}
                    </TableCell>
                    <TableCell>
                      {format(new Date(version.created), 'PPp')}
                    </TableCell>
                    <TableCell>{version.creator}</TableCell>
                    <TableCell>{formatFileSize(version.size)}</TableCell>
                    <TableCell>
                      <Typography variant="body2">
                        {formatSizeDelta(version, versions[index + 1])}
                      </Typography>
                    </TableCell>
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
          <MenuItem
            onClick={() => {
              if (contextMenu) {
                setPendingAction({ type: 'download', version: contextMenu.version });
                handleCloseContextMenu();
              }
            }}
          >
            <ListItemIcon>
              <Download fontSize="small" />
            </ListItemIcon>
            <ListItemText>Download this version</ListItemText>
          </MenuItem>
          <MenuItem
            onClick={() => {
              if (contextMenu) {
                setPendingAction({ type: 'restore', version: contextMenu.version });
                handleCloseContextMenu();
              }
            }}
          >
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

      <Dialog open={pendingAction !== null} onClose={() => setPendingAction(null)}>
        <DialogTitle>
          {pendingAction?.type === 'download' ? 'Download version' : 'Restore version'}
        </DialogTitle>
        <DialogContent>
          <Typography variant="body2" gutterBottom>
            Version: {pendingAction?.version.versionLabel}
          </Typography>
          <Typography variant="body2" gutterBottom>
            Created: {pendingAction?.version.created ? format(new Date(pendingAction.version.created), 'PPp') : '—'}
          </Typography>
          <Typography variant="body2">
            Size: {pendingAction?.version.size !== undefined ? formatFileSize(pendingAction.version.size) : '—'}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPendingAction(null)}>Cancel</Button>
          <Button variant="contained" onClick={handleConfirmAction}>
            Confirm
          </Button>
        </DialogActions>
      </Dialog>
    </Dialog>
  );
};

export default VersionHistoryDialog;
