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
  FormControlLabel,
  Switch,
  Menu,
  MenuItem,
  ListItemIcon,
  ListItemText,
  CircularProgress,
  Box,
  Tooltip,
  Alert,
  Stack,
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
  const [loadingMore, setLoadingMore] = useState(false);
  const [page, setPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [majorOnly, setMajorOnly] = useState(false);
  const [contextMenu, setContextMenu] = useState<{
    mouseX: number;
    mouseY: number;
    version: Version;
  } | null>(null);
  const [pendingAction, setPendingAction] = useState<{
    type: 'download' | 'restore';
    version: Version;
  } | null>(null);
  const [comparePair, setComparePair] = useState<{
    current: Version;
    previous: Version;
  } | null>(null);
  const [textDiff, setTextDiff] = useState<{
    available: boolean;
    truncated: boolean;
    reason?: string | null;
    diff?: string | null;
  } | null>(null);
  const [loadingTextDiff, setLoadingTextDiff] = useState(false);

  const pageSize = 20;

  const loadVersionHistory = useCallback(async (targetPage = 0, append = false) => {
    if (!selectedNodeId) return;

    if (append) {
      setLoadingMore(true);
    } else {
      setLoading(true);
    }
    try {
      const response = await nodeService.getVersionHistoryPage(
        selectedNodeId,
        targetPage,
        pageSize,
        majorOnly
      );
      setVersions((prev) => (append ? [...prev, ...response.versions] : response.versions));
      setPage(response.page);
      setTotalElements(response.totalElements);
    } catch (error) {
      toast.error('Failed to load version history');
    } finally {
      setLoading(false);
      setLoadingMore(false);
    }
  }, [selectedNodeId, pageSize, majorOnly]);

  useEffect(() => {
    if (versionHistoryDialogOpen && selectedNodeId) {
      loadVersionHistory(0, false);
    }
  }, [versionHistoryDialogOpen, selectedNodeId, majorOnly, loadVersionHistory]);

  const handleClose = () => {
    dispatch(setVersionHistoryDialogOpen(false));
    setVersions([]);
    setPage(0);
    setTotalElements(0);
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

  const getPreviousVersion = (version: Version) => {
    const index = versions.findIndex((item) => item.id === version.id);
    if (index === -1) {
      return null;
    }
    return versions[index + 1] ?? null;
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

  const formatHash = (hash?: string | null) => {
    if (!hash) {
      return '-';
    }
    if (hash.length <= 16) {
      return hash;
    }
    return `${hash.slice(0, 12)}…${hash.slice(-4)}`;
  };

  const buildCompareSummary = (current: Version, previous: Version) => {
    const items: Array<{ label: string; color?: 'default' | 'primary' | 'info' | 'warning' }> = [];
    const sizeDelta = current.size - previous.size;
    if (sizeDelta !== 0) {
      const sign = sizeDelta > 0 ? '+' : '−';
      const sizeLabel = `${sign}${formatFileSize(Math.abs(sizeDelta))}`;
      items.push({ label: `Size ${sizeLabel}`, color: sizeDelta > 0 ? 'warning' : 'info' });
    }
    if (current.isMajor !== previous.isMajor) {
      items.push({ label: `Major: ${current.isMajor ? 'Yes' : 'No'}`, color: 'primary' });
    }
    const hashChanged = current.contentHash && previous.contentHash
      ? current.contentHash !== previous.contentHash
      : Boolean(current.contentHash || previous.contentHash);
    if (hashChanged) {
      items.push({ label: 'Checksum changed', color: 'warning' });
    }
    if (current.mimeType !== previous.mimeType) {
      items.push({
        label: `Type: ${previous.mimeType || '—'} → ${current.mimeType || '—'}`,
      });
    }
    if ((current.comment || '') !== (previous.comment || '')) {
      items.push({ label: 'Comment updated' });
    }
    return items;
  };

  const normalizeMimeType = (mimeType?: string | null) => (mimeType || '').split(';')[0]?.trim().toLowerCase();
  const isTextLikeMimeType = (mimeType?: string | null) => {
    const normalized = normalizeMimeType(mimeType);
    if (!normalized) {
      return false;
    }
    if (normalized.startsWith('text/')) {
      return true;
    }
    return normalized === 'application/json'
      || normalized === 'application/xml'
      || normalized === 'text/xml'
      || normalized === 'application/x-yaml'
      || normalized === 'application/yaml'
      || normalized === 'text/yaml';
  };

  const handleLoadTextDiff = async (current: Version, previous: Version) => {
    if (!selectedNodeId) return;
    setLoadingTextDiff(true);
    setTextDiff(null);
    try {
      const diff = await nodeService.getVersionTextDiff(selectedNodeId, previous.id, current.id);
      setTextDiff(diff);
    } catch {
      setTextDiff({ available: false, truncated: false, reason: 'Failed to load diff', diff: null });
    } finally {
      setLoadingTextDiff(false);
    }
  };

  const downloadTextDiff = (current: Version, previous: Version, diff: string) => {
    const baseName = `version-diff-${previous.versionLabel}-to-${current.versionLabel}`;
    const blob = new Blob([diff], { type: 'text/plain;charset=utf-8' });
    const url = window.URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `${baseName}.diff.txt`;
    anchor.click();
    window.URL.revokeObjectURL(url);
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

  const contextPrevious = contextMenu ? getPreviousVersion(contextMenu.version) : null;
  const currentVersion = versions[0] ?? null;
  const hasMore = versions.length < totalElements;

  useEffect(() => {
    // Reset content diff state when selecting a new compare pair.
    setTextDiff(null);
    setLoadingTextDiff(false);
  }, [comparePair?.current?.id, comparePair?.previous?.id]);

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
        <Box display="flex" alignItems="center" justifyContent="space-between" flexWrap="wrap" gap={1} mb={2}>
          <FormControlLabel
            control={
              <Switch
                checked={majorOnly}
                onChange={(event) => {
                  setMajorOnly(event.target.checked);
                }}
                color="primary"
              />
            }
            label="Major versions only"
          />
          <Typography variant="body2" color="text.secondary">
            Showing {versions.length} of {totalElements || versions.length} versions
          </Typography>
        </Box>
        {loading ? (
          <Box display="flex" justifyContent="center" p={4}>
            <CircularProgress />
          </Box>
        ) : versions.length === 0 ? (
          <Typography variant="body1" color="text.secondary" align="center" sx={{ py: 4 }}>
            No version history available
          </Typography>
        ) : (
          <>
            <TableContainer component={Paper} variant="outlined">
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>Version</TableCell>
                    <TableCell>Date</TableCell>
                    <TableCell>Created By</TableCell>
                    <TableCell>Size</TableCell>
                    <TableCell>Checksum</TableCell>
                    <TableCell>
                      <Tooltip title="Delta shows the size change vs the previous version." placement="top" arrow>
                        <Box component="span">Delta</Box>
                      </Tooltip>
                    </TableCell>
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
                        {version.contentHash ? (
                          <Tooltip title={version.contentHash}>
                            <Typography variant="body2">{formatHash(version.contentHash)}</Typography>
                          </Tooltip>
                        ) : (
                          '-'
                        )}
                      </TableCell>
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
            {hasMore && (
              <Box display="flex" justifyContent="center" mt={2}>
                <Button
                  variant="outlined"
                  onClick={() => loadVersionHistory(page + 1, true)}
                  disabled={loadingMore}
                >
                  {loadingMore ? 'Loading...' : 'Load more'}
                </Button>
              </Box>
            )}
          </>
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
          <MenuItem
            disabled={!contextPrevious}
            onClick={() => {
              if (contextMenu && contextPrevious) {
                setComparePair({ current: contextMenu.version, previous: contextPrevious });
                handleCloseContextMenu();
              }
            }}
          >
            <ListItemIcon>
              <Compare fontSize="small" />
            </ListItemIcon>
            <ListItemText>Compare versions</ListItemText>
          </MenuItem>
          <MenuItem
            disabled={!currentVersion || !contextMenu || contextMenu.version.id === currentVersion.id}
            onClick={() => {
              if (contextMenu && currentVersion && contextMenu.version.id !== currentVersion.id) {
                setComparePair({ current: currentVersion, previous: contextMenu.version });
                handleCloseContextMenu();
              }
            }}
          >
            <ListItemIcon>
              <Compare fontSize="small" />
            </ListItemIcon>
            <ListItemText>Compare with current</ListItemText>
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

      <Dialog open={comparePair !== null} onClose={() => setComparePair(null)} maxWidth="sm" fullWidth>
        <DialogTitle>Compare Versions</DialogTitle>
        <DialogContent>
          {comparePair && (
            <>
              <Box mb={2}>
                <Typography variant="subtitle2" gutterBottom>
                  Change Summary
                </Typography>
                {(() => {
                  const summary = buildCompareSummary(comparePair.current, comparePair.previous);
                  if (summary.length === 0) {
                    return (
                      <Typography variant="body2" color="text.secondary">
                        No differences detected.
                      </Typography>
                    );
                  }
                  return (
                    <Stack direction="row" gap={1} flexWrap="wrap">
                      {summary.map((item, index) => (
                        <Chip
                          key={`${item.label}-${index}`}
                          size="small"
                          label={item.label}
                          color={item.color ?? 'default'}
                          variant="outlined"
                        />
                      ))}
                    </Stack>
                  );
                })()}
              </Box>
              <TableContainer component={Paper} variant="outlined">
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Field</TableCell>
                      <TableCell>Current</TableCell>
                      <TableCell>Previous</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    <TableRow>
                      <TableCell>Version</TableCell>
                      <TableCell>{comparePair.current.versionLabel}</TableCell>
                      <TableCell>{comparePair.previous.versionLabel}</TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell>Major</TableCell>
                      <TableCell>{comparePair.current.isMajor ? 'Yes' : 'No'}</TableCell>
                      <TableCell>{comparePair.previous.isMajor ? 'Yes' : 'No'}</TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell>Created</TableCell>
                      <TableCell>{format(new Date(comparePair.current.created), 'PPp')}</TableCell>
                      <TableCell>{format(new Date(comparePair.previous.created), 'PPp')}</TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell>Creator</TableCell>
                      <TableCell>{comparePair.current.creator}</TableCell>
                      <TableCell>{comparePair.previous.creator}</TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell>Size</TableCell>
                      <TableCell>{formatFileSize(comparePair.current.size)}</TableCell>
                      <TableCell>{formatFileSize(comparePair.previous.size)}</TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell>Size delta</TableCell>
                      <TableCell>{formatSizeDelta(comparePair.current, comparePair.previous)}</TableCell>
                      <TableCell>—</TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell>Mime Type</TableCell>
                      <TableCell>{comparePair.current.mimeType || '-'}</TableCell>
                      <TableCell>{comparePair.previous.mimeType || '-'}</TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell>Checksum</TableCell>
                      <TableCell>{comparePair.current.contentHash || '-'}</TableCell>
                      <TableCell>{comparePair.previous.contentHash || '-'}</TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell>Hash changed</TableCell>
                      <TableCell>
                        {comparePair.current.contentHash && comparePair.previous.contentHash
                          ? (comparePair.current.contentHash === comparePair.previous.contentHash ? 'No' : 'Yes')
                          : '—'}
                      </TableCell>
                      <TableCell>—</TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell>Content ID</TableCell>
                      <TableCell>{comparePair.current.contentId || '-'}</TableCell>
                      <TableCell>{comparePair.previous.contentId || '-'}</TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell>Status</TableCell>
                      <TableCell>{comparePair.current.status || '-'}</TableCell>
                      <TableCell>{comparePair.previous.status || '-'}</TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell>Comment</TableCell>
                      <TableCell>{comparePair.current.comment || '-'}</TableCell>
                      <TableCell>{comparePair.previous.comment || '-'}</TableCell>
                    </TableRow>
                  </TableBody>
                </Table>
              </TableContainer>

              <Box mt={2}>
                <Typography variant="subtitle2" gutterBottom>
                  Content Diff (Text)
                </Typography>
                {(!isTextLikeMimeType(comparePair.current.mimeType) || !isTextLikeMimeType(comparePair.previous.mimeType)) && (
                  <Typography variant="body2" color="text.secondary">
                    Text diff is available for text documents only. Download the two versions to compare externally.
                  </Typography>
                )}
                {isTextLikeMimeType(comparePair.current.mimeType) && isTextLikeMimeType(comparePair.previous.mimeType) && (
                  <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap" mb={1}>
                    <Button
                      size="small"
                      variant="outlined"
                      onClick={() => handleLoadTextDiff(comparePair.current, comparePair.previous)}
                      disabled={loadingTextDiff}
                    >
                      Load text diff
                    </Button>
                    {textDiff?.available && textDiff.diff && (
                      <Button
                        size="small"
                        variant="text"
                        onClick={() => downloadTextDiff(comparePair.current, comparePair.previous, textDiff.diff || '')}
                      >
                        Download diff
                      </Button>
                    )}
                    {loadingTextDiff && <CircularProgress size={16} />}
                    {textDiff?.truncated && (
                      <Chip size="small" label="Truncated" variant="outlined" />
                    )}
                  </Stack>
                )}
                {textDiff && !textDiff.available && (
                  <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 1 }}>
                    {textDiff.reason || 'Diff unavailable'}
                  </Typography>
                )}
                {textDiff?.available && textDiff.diff && (
                  <Paper variant="outlined" sx={{ p: 1, bgcolor: 'grey.50', maxHeight: 240, overflow: 'auto' }}>
                    <Typography
                      component="pre"
                      variant="caption"
                      sx={{ m: 0, whiteSpace: 'pre-wrap', fontFamily: 'monospace' }}
                    >
                      {textDiff.diff}
                    </Typography>
                  </Paper>
                )}
              </Box>
            </>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setComparePair(null)}>Close</Button>
        </DialogActions>
      </Dialog>
    </Dialog>
  );
};

export default VersionHistoryDialog;
