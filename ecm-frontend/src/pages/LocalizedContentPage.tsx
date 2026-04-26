import React, { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  IconButton,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import { Add, Delete, Edit } from '@mui/icons-material';
import { toast } from 'react-toastify';
import localizedContentService, {
  LocalizedContentDto,
  LocalizedContentRequest,
} from 'services/localizedContentService';

// Helpers

const formatDateTime = (value?: string | null): string => {
  if (!value) return '-';
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return parsed.toLocaleString();
};

const truncate = (value: string | null | undefined, max = 60): string => {
  if (!value) return '-';
  return value.length > max ? `${value.slice(0, max)}...` : value;
};

const COMMON_LOCALES = ['en', 'zh', 'zh-CN', 'zh-TW', 'fr', 'de', 'es', 'ja', 'ko', 'ar'];
const CUSTOM_SENTINEL = '__custom__';

// AddLocaleDialog

interface AddLocaleDialogProps {
  open: boolean;
  nodeId: string;
  existingLocales: string[];
  onClose: () => void;
  onUpserted: (dto: LocalizedContentDto) => void;
}

const AddLocaleDialog: React.FC<AddLocaleDialogProps> = ({
  open,
  nodeId,
  existingLocales,
  onClose,
  onUpserted,
}) => {
  const [localeSelect, setLocaleSelect] = useState('en');
  const [customLocale, setCustomLocale] = useState('');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleClose = () => {
    setLocaleSelect('en');
    setCustomLocale('');
    setTitle('');
    setDescription('');
    onClose();
  };

  const effectiveLocale =
    localeSelect === CUSTOM_SENTINEL ? customLocale.trim() : localeSelect;
  const existingLocaleKeys = existingLocales.map((l) => l.toLowerCase());

  const handleSubmit = async () => {
    if (!effectiveLocale) return;
    setSubmitting(true);
    try {
      const payload: LocalizedContentRequest = {};
      if (title.trim()) payload.title = title.trim();
      if (description.trim()) payload.description = description.trim();
      const dto = await localizedContentService.upsertLocalization(
        nodeId,
        effectiveLocale,
        payload
      );
      toast.success(`Localization for "${effectiveLocale}" saved.`);
      onUpserted(dto);
      handleClose();
    } catch (err) {
      console.error('Failed to save localization', err);
      toast.error('Failed to save localization.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="sm">
      <DialogTitle>Add Localization</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <FormControl fullWidth>
            <InputLabel id="locale-select-label">Locale</InputLabel>
            <Select
              labelId="locale-select-label"
              label="Locale"
              value={localeSelect}
              onChange={(e) => setLocaleSelect(String(e.target.value))}
            >
              {COMMON_LOCALES.map((l) => (
                <MenuItem key={l} value={l}>
                  {l}
                  {existingLocaleKeys.includes(l.toLowerCase()) && (
                    <Typography
                      variant="caption"
                      color="text.secondary"
                      sx={{ ml: 1 }}
                    >
                      (will overwrite)
                    </Typography>
                  )}
                </MenuItem>
              ))}
              <MenuItem value={CUSTOM_SENTINEL}>custom...</MenuItem>
            </Select>
          </FormControl>

          {localeSelect === CUSTOM_SENTINEL && (
            <TextField
              label="Custom Locale Code"
              value={customLocale}
              onChange={(e) => setCustomLocale(e.target.value)}
              placeholder="e.g. pt-BR"
              fullWidth
              autoFocus
            />
          )}

          <TextField
            label="Title (optional)"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            fullWidth
          />
          <TextField
            label="Description (optional)"
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
          disabled={!effectiveLocale || submitting}
        >
          {submitting ? 'Saving...' : 'Save'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

// EditLocaleDialog

interface EditLocaleDialogProps {
  open: boolean;
  nodeId: string;
  dto: LocalizedContentDto | null;
  onClose: () => void;
  onUpserted: (dto: LocalizedContentDto) => void;
}

const EditLocaleDialog: React.FC<EditLocaleDialogProps> = ({
  open,
  nodeId,
  dto,
  onClose,
  onUpserted,
}) => {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // Sync fields when the dto changes (dialog re-opens for a different row)
  React.useEffect(() => {
    if (dto) {
      setTitle(dto.title ?? '');
      setDescription(dto.description ?? '');
    }
  }, [dto]);

  const handleClose = () => {
    onClose();
  };

  const handleSubmit = async () => {
    if (!dto) return;
    setSubmitting(true);
    try {
      const payload: LocalizedContentRequest = {
        title: title.trim() || undefined,
        description: description.trim() || undefined,
      };
      const updated = await localizedContentService.upsertLocalization(
        nodeId,
        dto.locale,
        payload
      );
      toast.success(`Localization for "${dto.locale}" updated.`);
      onUpserted(updated);
      handleClose();
    } catch (err) {
      console.error('Failed to update localization', err);
      toast.error('Failed to update localization.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="sm">
      <DialogTitle>Edit Localization</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Box>
            <Typography variant="caption" color="text.secondary">
              Locale
            </Typography>
            <Box sx={{ mt: 0.5 }}>
              <Chip label={dto?.locale ?? ''} size="small" />
            </Box>
          </Box>
          <TextField
            label="Title (optional)"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            fullWidth
            autoFocus
          />
          <TextField
            label="Description (optional)"
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
          disabled={submitting}
        >
          {submitting ? 'Saving...' : 'Save'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

// Main page

const LocalizedContentPage: React.FC = () => {
  const [nodeIdInput, setNodeIdInput] = useState('');
  const [activeNodeId, setActiveNodeId] = useState<string | null>(null);
  const [items, setItems] = useState<LocalizedContentDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [addOpen, setAddOpen] = useState(false);
  const [editDto, setEditDto] = useState<LocalizedContentDto | null>(null);
  const [editOpen, setEditOpen] = useState(false);

  // Per-row inline delete confirmation: stores the locale being confirmed.
  const [confirmDeleteLocale, setConfirmDeleteLocale] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);

  // Load

  const handleLoad = async () => {
    const trimmed = nodeIdInput.trim();
    if (!trimmed) return;
    setLoading(true);
    setLoadError(null);
    setItems([]);
    setConfirmDeleteLocale(null);
    try {
      const data = await localizedContentService.listLocalizations(trimmed);
      setItems(data);
      setActiveNodeId(trimmed);
    } catch (err) {
      console.error('Failed to load localizations', err);
      setLoadError('Node not found or failed to load localizations.');
      setActiveNodeId(null);
    } finally {
      setLoading(false);
    }
  };

  // Patch helpers

  const applyUpsert = (dto: LocalizedContentDto) => {
    setItems((prev) => {
      const idx = prev.findIndex((x) => x.locale === dto.locale);
      if (idx >= 0) {
        const copy = [...prev];
        copy[idx] = dto;
        return copy;
      }
      return [...prev, dto];
    });
  };

  // Delete

  const handleConfirmDelete = async (locale: string) => {
    if (!activeNodeId) return;
    setDeleting(true);
    try {
      await localizedContentService.deleteLocalization(activeNodeId, locale);
      toast.success(`Localization for "${locale}" deleted.`);
      setItems((prev) => prev.filter((x) => x.locale !== locale));
      setConfirmDeleteLocale(null);
    } catch (err) {
      console.error('Failed to delete localization', err);
      toast.error('Failed to delete localization.');
    } finally {
      setDeleting(false);
    }
  };

  // Render

  return (
    <Box sx={{ p: 3 }}>
      {/* Page header */}
      <Box sx={{ mb: 3 }}>
        <Typography variant="h4" fontWeight={700}>
          Multilingual Content
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
          Manage per-locale titles and descriptions for content nodes
        </Typography>
      </Box>

      {/* Node lookup */}
      <Stack direction="row" spacing={1} alignItems="flex-start" sx={{ mb: 3, maxWidth: 600 }}>
        <TextField
          label="Node ID (UUID)"
          value={nodeIdInput}
          onChange={(e) => setNodeIdInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              void handleLoad();
            }
          }}
          size="small"
          fullWidth
        />
        <Button
          variant="contained"
          onClick={() => void handleLoad()}
          disabled={!nodeIdInput.trim() || loading}
          sx={{ whiteSpace: 'nowrap', flexShrink: 0 }}
        >
          {loading ? <CircularProgress size={20} color="inherit" /> : 'Load'}
        </Button>
      </Stack>

      {loadError && (
        <Alert severity="error" sx={{ mb: 2, maxWidth: 600 }}>
          {loadError}
        </Alert>
      )}

      {/* Localizations table */}
      {activeNodeId && (
        <>
          <Stack
            direction="row"
            alignItems="center"
            justifyContent="space-between"
            sx={{ mb: 1 }}
          >
            <Typography variant="subtitle1" fontWeight={600}>
              Localizations for{' '}
              <Typography component="span" variant="subtitle1" fontFamily="monospace">
                {activeNodeId}
              </Typography>
            </Typography>
            <Button
              variant="outlined"
              size="small"
              startIcon={<Add />}
              onClick={() => setAddOpen(true)}
            >
              Add Locale
            </Button>
          </Stack>

          {items.length === 0 ? (
            <Typography variant="body2" color="text.secondary">
              No localizations found for this node.
            </Typography>
          ) : (
            <Box sx={{ overflowX: 'auto' }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Locale</TableCell>
                    <TableCell>Title</TableCell>
                    <TableCell>Description</TableCell>
                    <TableCell>Created By</TableCell>
                    <TableCell>Last Modified</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {items.map((item) => (
                    <TableRow key={item.locale} hover>
                      <TableCell>
                        <Chip label={item.locale} size="small" />
                      </TableCell>
                      <TableCell>{truncate(item.title)}</TableCell>
                      <TableCell>{truncate(item.description)}</TableCell>
                      <TableCell>{item.createdBy}</TableCell>
                      <TableCell>{formatDateTime(item.lastModifiedDate)}</TableCell>
                      <TableCell align="right">
                        {confirmDeleteLocale === item.locale ? (
                          <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                            <Typography variant="caption" color="text.secondary" sx={{ alignSelf: 'center' }}>
                              Confirm delete?
                            </Typography>
                            <Button
                              size="small"
                              color="error"
                              variant="contained"
                              onClick={() => void handleConfirmDelete(item.locale)}
                              disabled={deleting}
                            >
                              {deleting ? <CircularProgress size={14} color="inherit" /> : 'Yes'}
                            </Button>
                            <Button
                              size="small"
                              onClick={() => setConfirmDeleteLocale(null)}
                              disabled={deleting}
                            >
                              No
                            </Button>
                          </Stack>
                        ) : (
                          <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                            <Tooltip title="Edit">
                              <IconButton
                                size="small"
                                onClick={() => {
                                  setEditDto(item);
                                  setEditOpen(true);
                                }}
                              >
                                <Edit fontSize="small" />
                              </IconButton>
                            </Tooltip>
                            <Tooltip title="Delete">
                              <IconButton
                                size="small"
                                color="error"
                                aria-label="Delete"
                                onClick={() => setConfirmDeleteLocale(item.locale)}
                              >
                                <Delete fontSize="small" />
                              </IconButton>
                            </Tooltip>
                          </Stack>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Box>
          )}
        </>
      )}

      {/* Dialogs */}
      {activeNodeId && (
        <>
          <AddLocaleDialog
            open={addOpen}
            nodeId={activeNodeId}
            existingLocales={items.map((i) => i.locale)}
            onClose={() => setAddOpen(false)}
            onUpserted={(dto) => applyUpsert(dto)}
          />
          <EditLocaleDialog
            open={editOpen}
            nodeId={activeNodeId}
            dto={editDto}
            onClose={() => setEditOpen(false)}
            onUpserted={(dto) => applyUpsert(dto)}
          />
        </>
      )}
    </Box>
  );
};

export default LocalizedContentPage;
