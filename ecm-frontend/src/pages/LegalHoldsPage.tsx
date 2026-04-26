import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Grid,
  IconButton,
  List,
  ListItem,
  ListItemText,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import {
  Add,
  Delete,
  LockOpen,
  Refresh,
} from '@mui/icons-material';
import { toast } from 'react-toastify';
import legalHoldService, {
  LegalHoldDetail,
  LegalHoldSummary,
} from 'services/legalHoldService';

// ── helpers ──────────────────────────────────────────────────────────────────

const formatDateTime = (value?: string | null): string => {
  if (!value) return '—';
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return parsed.toLocaleString();
};

const parseNodeIds = (raw: string): string[] =>
  raw
    .split(/[\n,]+/)
    .map((s) => s.trim())
    .filter((s) => s.length > 0);

// ── sub-components ────────────────────────────────────────────────────────────

interface CreateHoldDialogProps {
  open: boolean;
  onClose: () => void;
  onCreated: (detail: LegalHoldDetail) => void;
}

const CreateHoldDialog: React.FC<CreateHoldDialogProps> = ({ open, onClose, onCreated }) => {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleClose = () => {
    setName('');
    setDescription('');
    onClose();
  };

  const handleSubmit = async () => {
    const trimmedName = name.trim();
    if (!trimmedName) return;
    setSubmitting(true);
    try {
      const detail = await legalHoldService.createHold({
        name: trimmedName,
        description: description.trim() || undefined,
      });
      toast.success(`Legal hold "${detail.name}" created.`);
      onCreated(detail);
      handleClose();
    } catch (err) {
      console.error('Failed to create legal hold', err);
      toast.error('Failed to create legal hold.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="sm">
      <DialogTitle>Create Legal Hold</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField
            label="Name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            fullWidth
            autoFocus
          />
          <TextField
            label="Description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            fullWidth
            multiline
            rows={3}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={submitting}>
          Cancel
        </Button>
        <Button
          variant="contained"
          onClick={handleSubmit}
          disabled={!name.trim() || submitting}
        >
          {submitting ? 'Creating…' : 'Create'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

interface AddNodesDialogProps {
  open: boolean;
  holdName: string;
  onClose: () => void;
  onAdded: (detail: LegalHoldDetail) => void;
  holdId: string;
}

const AddNodesDialog: React.FC<AddNodesDialogProps> = ({
  open,
  holdName,
  onClose,
  onAdded,
  holdId,
}) => {
  const [raw, setRaw] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleClose = () => {
    setRaw('');
    onClose();
  };

  const nodeIds = parseNodeIds(raw);

  const handleSubmit = async () => {
    if (nodeIds.length === 0) return;
    setSubmitting(true);
    try {
      const detail = await legalHoldService.addItems(holdId, { nodeIds });
      toast.success(`Added ${nodeIds.length} node(s) to "${holdName}".`);
      onAdded(detail);
      handleClose();
    } catch (err) {
      console.error('Failed to add nodes to legal hold', err);
      toast.error('Failed to add nodes to legal hold.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="sm">
      <DialogTitle>Add Nodes to Hold</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Typography variant="body2" color="text.secondary">
            Enter one node UUID per line (or comma-separated). These can be document or folder IDs
            from the repository.
          </Typography>
          <TextField
            label="Node IDs"
            value={raw}
            onChange={(e) => setRaw(e.target.value)}
            fullWidth
            multiline
            rows={6}
            placeholder={
              'e.g.\na1b2c3d4-e5f6-7890-abcd-ef1234567890\nb2c3d4e5-...'
            }
          />
          {nodeIds.length > 0 && (
            <Typography variant="caption" color="text.secondary">
              {nodeIds.length} node ID(s) detected
            </Typography>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={submitting}>
          Cancel
        </Button>
        <Button
          variant="contained"
          onClick={handleSubmit}
          disabled={nodeIds.length === 0 || submitting}
        >
          {submitting ? 'Adding…' : `Add ${nodeIds.length > 0 ? nodeIds.length : ''} Node(s)`}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

interface ReleaseHoldDialogProps {
  open: boolean;
  holdName: string;
  onClose: () => void;
  onReleased: (detail: LegalHoldDetail) => void;
  holdId: string;
}

const ReleaseHoldDialog: React.FC<ReleaseHoldDialogProps> = ({
  open,
  holdName,
  onClose,
  onReleased,
  holdId,
}) => {
  const [comment, setComment] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleClose = () => {
    setComment('');
    onClose();
  };

  const handleSubmit = async () => {
    setSubmitting(true);
    try {
      const detail = await legalHoldService.releaseHold(holdId, {
        comment: comment.trim() || undefined,
      });
      toast.success(`Legal hold "${holdName}" released.`);
      onReleased(detail);
      handleClose();
    } catch (err) {
      console.error('Failed to release legal hold', err);
      toast.error('Failed to release legal hold.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="sm">
      <DialogTitle>Release Legal Hold</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Alert severity="warning">
            Releasing <strong>{holdName}</strong> will remove the preservation hold from all{' '}
            associated nodes. This action cannot be undone.
          </Alert>
          <TextField
            label="Release Comment (optional)"
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            fullWidth
            multiline
            rows={3}
            placeholder="Reason for releasing this hold…"
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={submitting}>
          Cancel
        </Button>
        <Button
          variant="contained"
          color="warning"
          onClick={handleSubmit}
          disabled={submitting}
        >
          {submitting ? 'Releasing…' : 'Release Hold'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

// ── main page ─────────────────────────────────────────────────────────────────

const LegalHoldsPage: React.FC = () => {
  const [holds, setHolds] = useState<LegalHoldSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [selectedHoldId, setSelectedHoldId] = useState<string | null>(null);
  const [detail, setDetail] = useState<LegalHoldDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);

  const [createOpen, setCreateOpen] = useState(false);
  const [addNodesOpen, setAddNodesOpen] = useState(false);
  const [releaseOpen, setReleaseOpen] = useState(false);

  // ── load list ──────────────────────────────────────────────────────────────

  const loadHolds = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const data = await legalHoldService.listHolds();
      setHolds(data);
    } catch (err) {
      console.error('Failed to load legal holds', err);
      setLoadError('Failed to load legal holds.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadHolds();
  }, [loadHolds]);

  // ── load detail ────────────────────────────────────────────────────────────

  const loadDetail = useCallback(async (holdId: string) => {
    setDetailLoading(true);
    setDetailError(null);
    setDetail(null);
    try {
      const data = await legalHoldService.getHold(holdId);
      setDetail(data);
    } catch (err) {
      console.error('Failed to load legal hold detail', err);
      setDetailError('Failed to load hold details.');
    } finally {
      setDetailLoading(false);
    }
  }, []);

  const handleSelectHold = (holdId: string) => {
    if (selectedHoldId === holdId) return;
    setSelectedHoldId(holdId);
    loadDetail(holdId);
  };

  // ── mutations ──────────────────────────────────────────────────────────────

  const handleCreated = (newDetail: LegalHoldDetail) => {
    // Add a summary entry to the list from the detail response
    const summary: LegalHoldSummary = {
      id: newDetail.id,
      name: newDetail.name,
      description: newDetail.description,
      status: newDetail.status,
      itemCount: newDetail.itemCount,
      createdBy: newDetail.createdBy,
      createdDate: newDetail.createdDate,
      releasedBy: newDetail.releasedBy,
      releasedAt: newDetail.releasedAt,
    };
    setHolds((prev) => [summary, ...prev]);
    setSelectedHoldId(newDetail.id);
    setDetail(newDetail);
  };

  const applyDetailUpdate = (updated: LegalHoldDetail) => {
    setDetail(updated);
    // Patch the matching summary in the list
    setHolds((prev) =>
      prev.map((h) =>
        h.id === updated.id
          ? {
              ...h,
              status: updated.status,
              itemCount: updated.itemCount,
              releasedBy: updated.releasedBy,
              releasedAt: updated.releasedAt,
            }
          : h
      )
    );
  };

  const handleRemoveItem = async (nodeId: string) => {
    if (!selectedHoldId) return;
    try {
      const updated = await legalHoldService.removeItem(selectedHoldId, nodeId);
      toast.success('Item removed from hold.');
      applyDetailUpdate(updated);
    } catch (err) {
      console.error('Failed to remove item from legal hold', err);
      toast.error('Failed to remove item from hold.');
    }
  };

  const selectedSummary = holds.find((h) => h.id === selectedHoldId);
  const isActive = detail?.status === 'ACTIVE';

  // ── render ─────────────────────────────────────────────────────────────────

  return (
    <Box sx={{ p: 3 }}>
      {/* Page header */}
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 3 }}>
        <Box>
          <Typography variant="h4" fontWeight={700}>
            Legal Holds
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            Manage preservation holds that prevent modification or deletion of content.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button
            variant="outlined"
            startIcon={<Refresh />}
            onClick={loadHolds}
            disabled={loading}
          >
            Refresh
          </Button>
          <Button
            variant="contained"
            startIcon={<Add />}
            onClick={() => setCreateOpen(true)}
          >
            Create Hold
          </Button>
        </Stack>
      </Stack>

      {loadError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {loadError}
        </Alert>
      )}

      <Grid container spacing={3}>
        {/* Left panel: holds list */}
        <Grid item xs={12} md={4}>
          <Card variant="outlined">
            <CardContent sx={{ p: 0, '&:last-child': { pb: 0 } }}>
              {loading ? (
                <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
                  <CircularProgress size={32} />
                </Box>
              ) : holds.length === 0 ? (
                <Box sx={{ p: 3, textAlign: 'center' }}>
                  <Typography variant="body2" color="text.secondary">
                    No legal holds found. Create one to get started.
                  </Typography>
                </Box>
              ) : (
                <List disablePadding>
                  {holds.map((hold, idx) => (
                    <React.Fragment key={hold.id}>
                      {idx > 0 && <Divider />}
                      <ListItem
                        button
                        selected={selectedHoldId === hold.id}
                        onClick={() => handleSelectHold(hold.id)}
                        sx={{ flexDirection: 'column', alignItems: 'flex-start', py: 1.5 }}
                      >
                        <Stack
                          direction="row"
                          alignItems="center"
                          justifyContent="space-between"
                          width="100%"
                          spacing={1}
                        >
                          <Typography variant="subtitle2" noWrap sx={{ flex: 1 }}>
                            {hold.name}
                          </Typography>
                          <Chip
                            label={hold.status}
                            size="small"
                            color={hold.status === 'ACTIVE' ? 'error' : 'default'}
                          />
                        </Stack>
                        <Stack direction="row" spacing={1} sx={{ mt: 0.5 }}>
                          <Typography variant="caption" color="text.secondary">
                            {hold.itemCount} item{hold.itemCount !== 1 ? 's' : ''}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            ·
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            {hold.createdBy ?? 'unknown'}
                          </Typography>
                        </Stack>
                      </ListItem>
                    </React.Fragment>
                  ))}
                </List>
              )}
            </CardContent>
          </Card>
        </Grid>

        {/* Right panel: hold detail */}
        <Grid item xs={12} md={8}>
          {!selectedHoldId ? (
            <Card variant="outlined">
              <CardContent>
                <Box sx={{ textAlign: 'center', py: 4 }}>
                  <Typography variant="body2" color="text.secondary">
                    Select a legal hold from the list to view details.
                  </Typography>
                </Box>
              </CardContent>
            </Card>
          ) : detailLoading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
              <CircularProgress />
            </Box>
          ) : detailError ? (
            <Alert severity="error">{detailError}</Alert>
          ) : detail ? (
            <Stack spacing={2}>
              {/* Hold metadata */}
              <Card variant="outlined">
                <CardContent>
                  <Stack
                    direction="row"
                    alignItems="flex-start"
                    justifyContent="space-between"
                    spacing={2}
                  >
                    <Box sx={{ flex: 1 }}>
                      <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 0.5 }}>
                        <Typography variant="h6" fontWeight={600}>
                          {detail.name}
                        </Typography>
                        <Chip
                          label={detail.status}
                          size="small"
                          color={detail.status === 'ACTIVE' ? 'error' : 'default'}
                        />
                      </Stack>
                      {detail.description && (
                        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                          {detail.description}
                        </Typography>
                      )}
                      <Grid container spacing={2} sx={{ mt: 0 }}>
                        <Grid item xs={6}>
                          <Typography variant="caption" color="text.secondary" display="block">
                            Created by
                          </Typography>
                          <Typography variant="body2">{detail.createdBy ?? '—'}</Typography>
                        </Grid>
                        <Grid item xs={6}>
                          <Typography variant="caption" color="text.secondary" display="block">
                            Created date
                          </Typography>
                          <Typography variant="body2">
                            {formatDateTime(detail.createdDate)}
                          </Typography>
                        </Grid>
                        {detail.status === 'RELEASED' && (
                          <>
                            <Grid item xs={6}>
                              <Typography variant="caption" color="text.secondary" display="block">
                                Released by
                              </Typography>
                              <Typography variant="body2">{detail.releasedBy ?? '—'}</Typography>
                            </Grid>
                            <Grid item xs={6}>
                              <Typography variant="caption" color="text.secondary" display="block">
                                Released at
                              </Typography>
                              <Typography variant="body2">
                                {formatDateTime(detail.releasedAt)}
                              </Typography>
                            </Grid>
                            {detail.releaseComment && (
                              <Grid item xs={12}>
                                <Typography variant="caption" color="text.secondary" display="block">
                                  Release comment
                                </Typography>
                                <Typography variant="body2">{detail.releaseComment}</Typography>
                              </Grid>
                            )}
                          </>
                        )}
                      </Grid>
                    </Box>

                    {isActive && (
                      <Stack direction="row" spacing={1} flexShrink={0}>
                        <Button
                          variant="outlined"
                          size="small"
                          startIcon={<Add />}
                          onClick={() => setAddNodesOpen(true)}
                        >
                          Add Nodes
                        </Button>
                        <Button
                          variant="outlined"
                          size="small"
                          color="warning"
                          startIcon={<LockOpen />}
                          onClick={() => setReleaseOpen(true)}
                        >
                          Release Hold
                        </Button>
                      </Stack>
                    )}
                  </Stack>
                </CardContent>
              </Card>

              {/* Items list */}
              <Card variant="outlined">
                <CardContent>
                  <Stack
                    direction="row"
                    alignItems="center"
                    justifyContent="space-between"
                    sx={{ mb: 2 }}
                  >
                    <Typography variant="subtitle1" fontWeight={600}>
                      Held Items ({detail.itemCount})
                    </Typography>
                  </Stack>

                  {detail.items.length === 0 ? (
                    <Typography variant="body2" color="text.secondary">
                      No items are currently under this hold.{' '}
                      {isActive && 'Use "Add Nodes" to add content.'}
                    </Typography>
                  ) : (
                    <List disablePadding>
                      {detail.items.map((item, idx) => (
                        <React.Fragment key={item.nodeId}>
                          {idx > 0 && <Divider />}
                          <ListItem
                            disablePadding
                            sx={{ py: 1 }}
                            secondaryAction={
                              isActive ? (
                                <Tooltip title="Remove from hold">
                                  <IconButton
                                    edge="end"
                                    size="small"
                                    onClick={() => handleRemoveItem(item.nodeId)}
                                  >
                                    <Delete fontSize="small" />
                                  </IconButton>
                                </Tooltip>
                              ) : undefined
                            }
                          >
                            <ListItemText
                              primary={
                                <Stack direction="row" alignItems="center" spacing={1}>
                                  <Typography variant="body2" fontWeight={500} noWrap>
                                    {item.nodeName ?? item.nodeId}
                                  </Typography>
                                  {item.nodeType && (
                                    <Chip label={item.nodeType} size="small" variant="outlined" />
                                  )}
                                </Stack>
                              }
                              secondary={
                                <Stack component="span" direction="column" spacing={0}>
                                  {item.nodePath && (
                                    <Typography
                                      component="span"
                                      variant="caption"
                                      color="text.secondary"
                                      noWrap
                                    >
                                      {item.nodePath}
                                    </Typography>
                                  )}
                                  <Typography
                                    component="span"
                                    variant="caption"
                                    color="text.secondary"
                                  >
                                    Added {formatDateTime(item.addedAt)}
                                    {item.addedBy ? ` by ${item.addedBy}` : ''}
                                  </Typography>
                                </Stack>
                              }
                            />
                          </ListItem>
                        </React.Fragment>
                      ))}
                    </List>
                  )}
                </CardContent>
              </Card>
            </Stack>
          ) : null}
        </Grid>
      </Grid>

      {/* Dialogs */}
      <CreateHoldDialog
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={handleCreated}
      />

      {selectedHoldId && selectedSummary && (
        <>
          <AddNodesDialog
            open={addNodesOpen}
            holdId={selectedHoldId}
            holdName={selectedSummary.name}
            onClose={() => setAddNodesOpen(false)}
            onAdded={applyDetailUpdate}
          />
          <ReleaseHoldDialog
            open={releaseOpen}
            holdId={selectedHoldId}
            holdName={selectedSummary.name}
            onClose={() => setReleaseOpen(false)}
            onReleased={applyDetailUpdate}
          />
        </>
      )}
    </Box>
  );
};

export default LegalHoldsPage;
