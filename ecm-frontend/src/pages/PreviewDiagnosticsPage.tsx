import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  FormControl,
  IconButton,
  MenuItem,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import {
  Autorenew as RetryIcon,
  Build as ForceIcon,
  ContentCopy as CopyIcon,
  FolderOpen as FolderOpenIcon,
  Refresh as RefreshIcon,
  Search as SearchIcon,
} from '@mui/icons-material';
import { format } from 'date-fns';
import { toast } from 'react-toastify';
import { useNavigate } from 'react-router-dom';
import nodeService from 'services/nodeService';
import previewDiagnosticsService, {
  PreviewFailureSample,
  PreviewFailureSummary as PreviewFailureSummaryDto,
} from 'services/previewDiagnosticsService';
import {
  formatPreviewFailureReasonLabel,
  getFailedPreviewMeta,
  isRetryablePreviewFailure,
  normalizePreviewFailureReason,
  summarizeFailedPreviews,
} from 'utils/previewStatusUtils';

const LIMIT_OPTIONS = [25, 50, 100, 200] as const;
const BACKEND_SUMMARY_SAMPLE_LIMIT = 500;
const WINDOW_DAY_OPTIONS = [
  { value: 7, label: 'Last 7 days' },
  { value: 30, label: 'Last 30 days' },
  { value: 0, label: 'All time' },
] as const;
type WindowDays = (typeof WINDOW_DAY_OPTIONS)[number]['value'];

const formatLastUpdated = (raw?: string | null) => {
  if (!raw) {
    return '—';
  }
  const date = new Date(raw);
  if (Number.isNaN(date.getTime())) {
    return raw;
  }
  return format(date, 'PPp');
};

const PreviewDiagnosticsPage: React.FC = () => {
  const navigate = useNavigate();
  const [limit, setLimit] = useState<(typeof LIMIT_OPTIONS)[number]>(50);
  const [windowDays, setWindowDays] = useState<WindowDays>(7);
  const [filterText, setFilterText] = useState('');
  const [items, setItems] = useState<PreviewFailureSample[]>([]);
  const [backendSummary, setBackendSummary] = useState<PreviewFailureSummaryDto | null>(null);
  const [loading, setLoading] = useState(false);
  const [queueingId, setQueueingId] = useState<string | null>(null);
  const [reasonBatchActionKey, setReasonBatchActionKey] = useState<string | null>(null);

  const loadFailures = useCallback(async () => {
    try {
      setLoading(true);
      const [data, summaryData] = await Promise.all([
        previewDiagnosticsService.listRecentFailures(limit, windowDays),
        previewDiagnosticsService.getFailureSummary(BACKEND_SUMMARY_SAMPLE_LIMIT, windowDays),
      ]);
      setItems(Array.isArray(data) ? data : []);
      setBackendSummary(summaryData || null);
    } catch {
      toast.error('Failed to load preview diagnostics (admin required)');
      setItems([]);
      setBackendSummary(null);
    } finally {
      setLoading(false);
    }
  }, [limit, windowDays]);

  useEffect(() => {
    void loadFailures();
  }, [loadFailures]);

  const summary = useMemo(() => {
    return summarizeFailedPreviews(
      items.map((item) => ({
        previewStatus: item.previewStatus,
        previewFailureCategory: item.previewFailureCategory,
        previewFailureReason: item.previewFailureReason,
        mimeType: item.mimeType,
      }))
    );
  }, [items]);

  const filteredItems = useMemo(() => {
    const query = filterText.trim().toLowerCase();
    if (!query) {
      return items;
    }
    return items.filter((item) => {
      const haystacks = [
        item.name,
        item.path,
        item.mimeType,
        item.previewStatus,
        item.previewFailureCategory,
        item.previewFailureReason,
      ]
        .filter(Boolean)
        .map((value) => String(value).toLowerCase());
      return haystacks.some((value) => value.includes(query));
    });
  }, [filterText, items]);

  const confidenceColor = backendSummary?.confidenceLevel === 'LOW' ? 'warning' : 'success';
  const confidenceLabel =
    backendSummary?.confidenceLevel === 'LOW'
      ? 'LOW confidence (sample truncated)'
      : 'HIGH confidence (sample complete)';

  const getReasonActionKey = (reason: string, category: string, force: boolean) =>
    `${category.toUpperCase()}|${reason}|${force ? 'force' : 'retry'}`;

  const matchReasonEntriesInCurrentList = useCallback(
    (reason: string, category: string, retryable: boolean) => {
      const normalizedReason = normalizePreviewFailureReason(reason);
      const normalizedCategory = (category || '').toUpperCase();
      return items.filter((item) => {
        const itemRetryable = isRetryablePreviewFailure(
          item.previewFailureCategory,
          item.mimeType,
          item.previewFailureReason
        );
        const itemReason = normalizePreviewFailureReason(item.previewFailureReason);
        const itemCategory = (item.previewFailureCategory || '').toUpperCase();
        return (
          itemRetryable === retryable
          && itemReason === normalizedReason
          && itemCategory === normalizedCategory
        );
      });
    },
    [items]
  );

  const getParentFolderPath = (rawPath?: string | null): string | null => {
    if (!rawPath) {
      return null;
    }
    const cleaned = rawPath.trim();
    if (!cleaned.startsWith('/')) {
      return null;
    }
    const parts = cleaned.split('/').filter(Boolean);
    if (parts.length < 2) {
      return null;
    }
    return `/${parts.slice(0, -1).join('/')}`;
  };

  const handleOpenInSearch = (item: PreviewFailureSample) => {
    const params = new URLSearchParams();
    const q = (item.name || '').trim();
    if (q) {
      params.set('q', q);
    }
    const previewStatus = (item.previewStatus || '').toUpperCase();
    if (previewStatus === 'FAILED' || previewStatus === 'UNSUPPORTED') {
      params.set('previewStatus', previewStatus);
    }
    const query = params.toString();
    navigate(query ? `/search?${query}` : '/search');
  };

  const handleCopyId = async (item: PreviewFailureSample) => {
    try {
      await navigator.clipboard.writeText(item.id);
      toast.success('Document id copied');
    } catch {
      toast.error('Failed to copy document id');
    }
  };

  const handleOpenParentFolder = async (item: PreviewFailureSample) => {
    const parentPath = getParentFolderPath(item.path);
    if (!parentPath) {
      return;
    }
    try {
      const folder = await nodeService.getFolderByPath(parentPath);
      navigate(`/browse/${folder.id}`);
    } catch {
      toast.error('Failed to open parent folder');
    }
  };

  const handleQueuePreview = async (item: PreviewFailureSample, force: boolean) => {
    const retryable = isRetryablePreviewFailure(item.previewFailureCategory, item.mimeType, item.previewFailureReason);
    if (!retryable) {
      return;
    }
    try {
      setQueueingId(item.id);
      await nodeService.queuePreview(item.id, force);
      toast.success(`Preview ${force ? 'rebuild' : 'retry'} queued`);
      await loadFailures();
    } catch {
      toast.error('Failed to queue preview');
    } finally {
      setQueueingId(null);
    }
  };

  const handleQueuePreviewByReason = async (
    reason: string,
    category: string,
    retryable: boolean,
    force: boolean
  ) => {
    if (!retryable) {
      return;
    }
    const matches = matchReasonEntriesInCurrentList(reason, category, retryable);
    const reasonLabel = reason === 'UNSPECIFIED' ? 'Unspecified reason' : formatPreviewFailureReasonLabel(reason);
    if (matches.length === 0) {
      toast.info(`No retryable documents in current list for reason: ${reasonLabel}`);
      return;
    }

    const actionKey = getReasonActionKey(reason, category, force);
    let queued = 0;
    let failed = 0;
    setReasonBatchActionKey(actionKey);
    try {
      const results = await Promise.allSettled(
        matches.map((item) => nodeService.queuePreview(item.id, force))
      );
      results.forEach((result) => {
        if (result.status === 'fulfilled') {
          queued += 1;
        } else {
          failed += 1;
        }
      });

      const actionLabel = force ? 'Force rebuild' : 'Retry';
      if (queued > 0 && failed === 0) {
        toast.success(`${actionLabel} queued for ${queued} document(s): ${reasonLabel}`);
      } else if (queued > 0) {
        toast.warning(`${actionLabel} queued for ${queued} document(s), ${failed} failed: ${reasonLabel}`);
      } else {
        toast.error(`${actionLabel} failed for reason group: ${reasonLabel}`);
      }
      await loadFailures();
    } finally {
      setReasonBatchActionKey(null);
    }
  };

  return (
    <Box p={3}>
      <Stack direction="row" alignItems="flex-start" justifyContent="space-between" flexWrap="wrap" gap={2} mb={2}>
        <Box>
          <Typography variant="h4">Preview Diagnostics</Typography>
          <Typography variant="body2" color="text.secondary">
            Recent preview failures (FAILED/UNSUPPORTED) from backend diagnostics.
          </Typography>
        </Box>
        <Stack direction="row" alignItems="center" gap={1} flexWrap="wrap">
          <FormControl size="small">
            <Select
              aria-label="Preview diagnostics limit"
              value={limit}
              onChange={(event) => setLimit(event.target.value as (typeof LIMIT_OPTIONS)[number])}
            >
              {LIMIT_OPTIONS.map((option) => (
                <MenuItem key={option} value={option}>
                  Limit {option}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <FormControl size="small">
            <Select
              aria-label="Preview diagnostics days"
              value={windowDays}
              onChange={(event) => setWindowDays(event.target.value as WindowDays)}
            >
              {WINDOW_DAY_OPTIONS.map((option) => (
                <MenuItem key={option.value} value={option.value}>
                  {option.label}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <Button
            variant="outlined"
            startIcon={<RefreshIcon />}
            onClick={() => void loadFailures()}
            disabled={loading}
          >
            Refresh
          </Button>
        </Stack>
      </Stack>

      <Stack direction="row" alignItems="center" gap={1} flexWrap="wrap" mb={2}>
        <TextField
          size="small"
          placeholder="Filter by name, path, mime type..."
          value={filterText}
          onChange={(event) => setFilterText(event.target.value)}
          sx={{ minWidth: 320, flex: '1 1 320px' }}
        />
        <Chip label={`Total ${summary.totalFailed}`} />
        <Chip label={`Retryable ${summary.retryableFailed}`} color="warning" variant="outlined" />
        <Chip label={`Permanent ${summary.permanentFailed}`} color="error" variant="outlined" />
        <Chip label={`Unsupported ${summary.unsupportedFailed}`} variant="outlined" />
      </Stack>

      {backendSummary && (
        <Paper sx={{ p: 2, mb: 2 }}>
          <Stack direction="row" alignItems="center" justifyContent="space-between" gap={1} flexWrap="wrap" mb={1}>
            <Typography variant="h6">Backend Failure Summary</Typography>
            <Stack direction="row" gap={1} flexWrap="wrap">
              <Chip
                label={confidenceLabel}
                color={confidenceColor}
                variant={backendSummary.confidenceLevel === 'LOW' ? 'filled' : 'outlined'}
              />
              <Chip label={`Sampled ${backendSummary.sampledFailures}/${backendSummary.totalFailures}`} />
            </Stack>
          </Stack>

          {backendSummary.sampleTruncated && (
            <Alert severity="warning" sx={{ mb: 2 }}>
              Summary is sampled ({backendSummary.sampledFailures}/{backendSummary.totalFailures}) and may miss long-tail
              reasons. Increase sample limit in backend endpoint if you need full precision.
            </Alert>
          )}

          <Stack direction="row" gap={1} flexWrap="wrap" mb={1}>
            {backendSummary.statusCounts.map((entry) => (
              <Chip
                key={`status-${entry.status}`}
                size="small"
                label={`Status ${entry.status}: ${entry.count}`}
                variant="outlined"
              />
            ))}
          </Stack>

          <Stack direction="row" gap={1} flexWrap="wrap" mb={2}>
            {backendSummary.categoryCounts.map((entry) => (
              <Chip
                key={`category-${entry.category}`}
                size="small"
                color={entry.retryable ? 'warning' : 'default'}
                label={`${entry.category}: ${entry.count}${entry.retryable ? ' (retryable)' : ''}`}
                variant="outlined"
              />
            ))}
          </Stack>

          {backendSummary.topReasons.length === 0 ? (
            <Alert severity="info">No failure reasons in current summary sample.</Alert>
          ) : (
            <TableContainer>
              <Table size="small" aria-label="Preview diagnostics top reasons">
                <TableHead>
                  <TableRow>
                    <TableCell>Top Failure Reason</TableCell>
                    <TableCell>Category</TableCell>
                    <TableCell>Retryable</TableCell>
                    <TableCell align="right">Current List</TableCell>
                    <TableCell align="right">Actions</TableCell>
                    <TableCell align="right">Count</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {backendSummary.topReasons.map((entry) => {
                    const reasonLabel =
                      entry.reason === 'UNSPECIFIED'
                        ? 'Unspecified reason'
                        : formatPreviewFailureReasonLabel(entry.reason);
                    const matchedInCurrentList = matchReasonEntriesInCurrentList(
                      entry.reason,
                      entry.category,
                      entry.retryable
                    ).length;
                    const retryActionKey = getReasonActionKey(entry.reason, entry.category, false);
                    const forceActionKey = getReasonActionKey(entry.reason, entry.category, true);
                    const rowBusy = reasonBatchActionKey === retryActionKey || reasonBatchActionKey === forceActionKey;
                    return (
                      <TableRow key={`${entry.reason}-${entry.category}-${entry.retryable}`}>
                        <TableCell sx={{ maxWidth: 520 }}>
                          <Tooltip title={reasonLabel} placement="top-start" arrow>
                            <Typography variant="body2" noWrap>
                              {reasonLabel}
                            </Typography>
                          </Tooltip>
                        </TableCell>
                        <TableCell>
                          <Chip size="small" label={entry.category || 'UNKNOWN'} variant="outlined" />
                        </TableCell>
                        <TableCell>
                          <Chip
                            size="small"
                            label={entry.retryable ? 'Yes' : 'No'}
                            color={entry.retryable ? 'warning' : 'default'}
                            variant={entry.retryable ? 'filled' : 'outlined'}
                          />
                        </TableCell>
                        <TableCell align="right">{matchedInCurrentList}</TableCell>
                        <TableCell align="right">
                          <Stack direction="row" justifyContent="flex-end" gap={0.5}>
                            <Button
                              size="small"
                              variant="outlined"
                              disabled={!entry.retryable || matchedInCurrentList === 0 || Boolean(reasonBatchActionKey)}
                              onClick={() => void handleQueuePreviewByReason(entry.reason, entry.category, entry.retryable, false)}
                              aria-label={`Retry reason group ${reasonLabel}`}
                            >
                              {rowBusy ? 'Running...' : 'Retry'}
                            </Button>
                            <Button
                              size="small"
                              variant="outlined"
                              disabled={!entry.retryable || matchedInCurrentList === 0 || Boolean(reasonBatchActionKey)}
                              onClick={() => void handleQueuePreviewByReason(entry.reason, entry.category, entry.retryable, true)}
                              aria-label={`Force rebuild reason group ${reasonLabel}`}
                            >
                              Force
                            </Button>
                          </Stack>
                        </TableCell>
                        <TableCell align="right">{entry.count}</TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Paper>
      )}

      <Paper sx={{ p: 2 }}>
        {loading ? (
          <Box display="flex" justifyContent="center" py={6}>
            <CircularProgress />
          </Box>
        ) : filteredItems.length === 0 ? (
          <Alert severity="info">No preview failures found.</Alert>
        ) : (
          <TableContainer>
            <Table size="small" aria-label="Preview diagnostics failures">
              <TableHead>
                <TableRow>
                  <TableCell>Name</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Mime Type</TableCell>
                  <TableCell>Category</TableCell>
                  <TableCell>Reason</TableCell>
                  <TableCell>Last Updated</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {filteredItems.map((item) => {
                  const meta = getFailedPreviewMeta(item.mimeType, item.previewFailureCategory, item.previewFailureReason);
                  const retryable = isRetryablePreviewFailure(
                    item.previewFailureCategory,
                    item.mimeType,
                    item.previewFailureReason
                  );
                  const queueBusy = queueingId === item.id || Boolean(reasonBatchActionKey);
                  const reasonLabel = formatPreviewFailureReasonLabel(item.previewFailureReason);
                  const parentPath = getParentFolderPath(item.path);

                  return (
                    <TableRow key={item.id} hover>
                      <TableCell sx={{ maxWidth: 360 }}>
                        <Tooltip title={item.path || item.name || item.id} placement="top-start" arrow>
                          <Typography variant="body2" noWrap>
                            {item.name || item.id}
                          </Typography>
                        </Tooltip>
                        {item.path && (
                          <Typography variant="caption" color="text.secondary" noWrap>
                            {item.path}
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell>
                        <Chip label={meta.label} color={meta.color} size="small" />
                      </TableCell>
                      <TableCell sx={{ maxWidth: 220 }}>
                        <Typography variant="body2" noWrap>
                          {item.mimeType || '—'}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Chip label={item.previewFailureCategory || '—'} size="small" variant="outlined" />
                      </TableCell>
                      <TableCell sx={{ maxWidth: 420 }}>
                        <Tooltip title={reasonLabel} placement="top-start" arrow>
                          <Typography variant="body2" noWrap>
                            {reasonLabel}
                          </Typography>
                        </Tooltip>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">{formatLastUpdated(item.previewLastUpdated)}</Typography>
                      </TableCell>
                      <TableCell align="right">
                        <Stack direction="row" justifyContent="flex-end" gap={0.5}>
                          <Tooltip title="Copy document id" placement="top-start" arrow>
                            <IconButton
                              aria-label="Copy document id"
                              size="small"
                              onClick={() => void handleCopyId(item)}
                            >
                              <CopyIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>

                          <Tooltip
                            title={parentPath ? 'Open parent folder' : 'Path is missing; cannot open parent folder'}
                            placement="top-start"
                            arrow
                          >
                            <span>
                              <IconButton
                                aria-label="Open parent folder"
                                size="small"
                                disabled={!parentPath}
                                onClick={() => void handleOpenParentFolder(item)}
                              >
                                <FolderOpenIcon fontSize="small" />
                              </IconButton>
                            </span>
                          </Tooltip>

                          <Tooltip title="Open in Advanced Search" placement="top-start" arrow>
                            <IconButton
                              aria-label="Open in Advanced Search"
                              size="small"
                              onClick={() => handleOpenInSearch(item)}
                            >
                              <SearchIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>

                          <Tooltip
                            title={
                              retryable
                                ? 'Retry preview'
                                : 'Retry is only available for temporary failures (unsupported/permanent are disabled)'
                            }
                            placement="top-start"
                            arrow
                          >
                            <span>
                              <IconButton
                                aria-label="Retry preview"
                                size="small"
                                disabled={!retryable || queueBusy}
                                onClick={() => void handleQueuePreview(item, false)}
                              >
                                <RetryIcon fontSize="small" />
                              </IconButton>
                            </span>
                          </Tooltip>

                          <Tooltip
                            title={
                              retryable
                                ? 'Force rebuild preview'
                                : 'Force rebuild is only available for temporary failures (unsupported/permanent are disabled)'
                            }
                            placement="top-start"
                            arrow
                          >
                            <span>
                              <IconButton
                                aria-label="Force rebuild preview"
                                size="small"
                                disabled={!retryable || queueBusy}
                                onClick={() => void handleQueuePreview(item, true)}
                              >
                                <ForceIcon fontSize="small" />
                              </IconButton>
                            </span>
                          </Tooltip>
                        </Stack>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Paper>
    </Box>
  );
};

export default PreviewDiagnosticsPage;
