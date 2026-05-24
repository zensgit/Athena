import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  AlertTitle,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  InputLabel,
  List,
  ListItem,
  ListItemText,
  MenuItem,
  Select,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { toast } from 'react-toastify';
import recordsManagementService from 'services/recordsManagementService';
import type {
  BulkDeclareErrorCategory,
  BulkDeclareResult,
  RecordCategory,
} from 'types';

// Mirrors the parser in LegalHoldsPage.tsx (8-4-4-4-12 generic UUID — not v4-only).
// Duplicated locally to keep this dialog self-contained and avoid component-from-page
// imports. If a third caller is ever added, lift to src/utils/uuid.ts.
const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

export const parseUuidList = (raw: string): string[] => {
  if (!raw) return [];
  const seen = new Set<string>();
  const ordered: string[] = [];
  for (const token of raw.split(/[\n,;]+/)) {
    const trimmed = token.trim().toLowerCase();
    if (!trimmed) continue;
    if (!UUID_REGEX.test(trimmed)) continue;
    if (seen.has(trimmed)) continue;
    seen.add(trimmed);
    ordered.push(trimmed);
  }
  return ordered;
};

const ERROR_CATEGORY_LABELS: Record<BulkDeclareErrorCategory, string> = {
  NODE_NOT_FOUND: 'Node not found',
  NODE_NOT_VISIBLE: 'Node not visible',
  INTERNAL_ERROR: 'Internal error',
};

const ERROR_CATEGORY_ORDER: BulkDeclareErrorCategory[] = [
  'NODE_NOT_FOUND',
  'NODE_NOT_VISIBLE',
  'INTERNAL_ERROR',
];

interface BulkDeclareRecordsDialogProps {
  open: boolean;
  onClose: () => void;
  onDeclared?: () => void;
  /**
   * Optional pre-fetched record categories. When provided, the dialog skips its own load.
   * When omitted, the dialog fetches categories on first open via
   * {@link recordsManagementService.listRecordCategories}.
   */
  categories?: RecordCategory[];
}

const BulkDeclareRecordsDialog: React.FC<BulkDeclareRecordsDialogProps> = ({
  open,
  onClose,
  onDeclared,
  categories: providedCategories,
}) => {
  const [nodeIdsRaw, setNodeIdsRaw] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [comment, setComment] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [failedRows, setFailedRows] = useState<BulkDeclareResult[]>([]);
  const [skippedRows, setSkippedRows] = useState<BulkDeclareResult[]>([]);
  const [internalCategories, setInternalCategories] = useState<RecordCategory[]>([]);

  const categories = providedCategories ?? internalCategories;
  const parsedUuids = useMemo(() => parseUuidList(nodeIdsRaw), [nodeIdsRaw]);

  // Fetch categories lazily on first open if the caller did not provide them.
  useEffect(() => {
    if (!open) return;
    if (providedCategories) return;
    if (internalCategories.length > 0) return;
    let cancelled = false;
    void recordsManagementService.listRecordCategories()
      .then((rows) => { if (!cancelled) setInternalCategories(rows); })
      .catch(() => { /* dialog still usable without category dropdown */ });
    return () => { cancelled = true; };
  }, [open, providedCategories, internalCategories.length]);

  // Reset transient state when the dialog closes (matches DeclareRecordDialog pattern).
  useEffect(() => {
    if (!open) {
      setNodeIdsRaw('');
      setCategoryId('');
      setComment('');
      setSubmitting(false);
      setFailedRows([]);
      setSkippedRows([]);
    }
  }, [open]);

  const handleSubmit = async () => {
    if (parsedUuids.length === 0 || submitting) return;
    setSubmitting(true);
    setFailedRows([]);
    setSkippedRows([]);
    try {
      const response = await recordsManagementService.createBulkDeclarations({
        nodeIds: parsedUuids,
        categoryId: categoryId || null,
        comment: comment || null,
      });
      const rows = response.bulkDeclareResults.rows;
      const declared = rows.filter((r) => r.status === 'DECLARED');
      const skipped = rows.filter((r) => r.status === 'SKIPPED_ALREADY_DECLARED');
      const failed = rows.filter((r) => r.status === 'FAILED');

      if (failed.length === 0) {
        // Pure success — closes + toast.
        const parts: string[] = [];
        if (declared.length > 0) parts.push(`${declared.length} declared`);
        if (skipped.length > 0) parts.push(`${skipped.length} already declared`);
        toast.success(`Bulk declare: ${parts.join(', ')}`);
        onDeclared?.();
        onClose();
        return;
      }

      // Partial failure — stay open. Drain successful UUIDs from the textarea so the
      // operator can retry only the failed/unprocessed rows without manually editing
      // the list. Failed rows stay surfaced under the per-category alert.
      const successOrSkipNodeIds = new Set<string>(
        [...declared, ...skipped].map((r) => r.nodeId.toLowerCase())
      );
      const remainingTokens = nodeIdsRaw
        .split(/\n/)
        .map((line) => line)
        .filter((line) => {
          const trimmed = line.trim().toLowerCase();
          // Keep non-UUID tokens (so the operator can see what was malformed); only drain
          // the recognised-and-succeeded UUIDs.
          if (!trimmed) return true;
          if (!UUID_REGEX.test(trimmed)) return true;
          return !successOrSkipNodeIds.has(trimmed);
        });
      setNodeIdsRaw(remainingTokens.join('\n'));
      setFailedRows(failed);
      setSkippedRows(skipped);
      if (declared.length > 0 || skipped.length > 0) {
        // Notify the page so any records list refreshes for the rows that did land.
        onDeclared?.();
      }
      toast.warn(`Bulk declare partial: ${declared.length} declared, ${skipped.length} skipped, ${failed.length} failed`);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Bulk declare failed';
      toast.error(message);
    } finally {
      setSubmitting(false);
    }
  };

  const submitDisabled = submitting || parsedUuids.length === 0;

  // Group failed rows by errorCategory for the per-category alert.
  const failedByCategory: Record<BulkDeclareErrorCategory, BulkDeclareResult[]> = {
    NODE_NOT_FOUND: [],
    NODE_NOT_VISIBLE: [],
    INTERNAL_ERROR: [],
  };
  for (const row of failedRows) {
    if (row.errorCategory) {
      failedByCategory[row.errorCategory].push(row);
    }
  }

  return (
    <Dialog open={open} onClose={submitting ? undefined : onClose} maxWidth="md" fullWidth>
      <DialogTitle>Bulk Declare Records</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Declare a list of documents as immutable records in one batch. Paste node IDs
          (UUID, one per line or comma-separated). Each row reports a status: DECLARED,
          ALREADY DECLARED (skipped, no category change), or FAILED with a reason.
        </Typography>

        <TextField
          fullWidth
          multiline
          minRows={4}
          maxRows={10}
          label="Node IDs"
          required
          value={nodeIdsRaw}
          onChange={(event) => setNodeIdsRaw(event.target.value)}
          helperText={`${parsedUuids.length} valid UUID${parsedUuids.length === 1 ? '' : 's'} parsed (duplicates and malformed entries are dropped)`}
          inputProps={{ 'data-testid': 'bulk-declare-node-ids' }}
          sx={{ mb: 2 }}
        />

        <FormControl fullWidth sx={{ mb: 2 }}>
          <InputLabel id="bulk-declare-category-label">Record Category (optional)</InputLabel>
          <Select
            labelId="bulk-declare-category-label"
            label="Record Category (optional)"
            value={categoryId}
            onChange={(event) => setCategoryId(event.target.value as string)}
            inputProps={{ 'data-testid': 'bulk-declare-category' }}
          >
            <MenuItem value="">
              <em>None</em>
            </MenuItem>
            {categories.map((category) => (
              <MenuItem key={category.categoryId} value={category.categoryId}>
                {category.path}
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        <TextField
          fullWidth
          multiline
          minRows={2}
          label="Declaration Comment (optional)"
          value={comment}
          onChange={(event) => setComment(event.target.value)}
          inputProps={{ 'data-testid': 'bulk-declare-comment' }}
          sx={{ mb: 2 }}
        />

        {skippedRows.length > 0 && (
          <Alert
            severity="info"
            sx={{ mb: 2 }}
            data-testid="bulk-declare-skipped-rows"
          >
            <AlertTitle>{skippedRows.length} row{skippedRows.length === 1 ? '' : 's'} already declared</AlertTitle>
            <Typography variant="body2" color="text.secondary">
              These documents were already records. Their existing category was NOT modified.
              To re-assign a category on an already-declared record, use the single-row
              category action.
            </Typography>
            <List dense>
              {skippedRows.map((row) => (
                <ListItem key={`skipped-${row.nodeId}`} disableGutters>
                  <ListItemText
                    primary={row.declaration?.name ?? row.nodeId}
                    secondary={row.declaration?.path}
                  />
                </ListItem>
              ))}
            </List>
          </Alert>
        )}

        {failedRows.length > 0 && (
          <Alert
            severity="error"
            sx={{ mb: 2 }}
            data-testid="bulk-declare-failed-rows"
          >
            <AlertTitle>{failedRows.length} row{failedRows.length === 1 ? '' : 's'} failed</AlertTitle>
            <Stack spacing={2} sx={{ mt: 1 }}>
              {ERROR_CATEGORY_ORDER.map((category) => {
                const rows = failedByCategory[category];
                if (rows.length === 0) return null;
                return (
                  <Box key={category} data-testid={`bulk-declare-failed-${category.toLowerCase()}`}>
                    <Chip label={`${ERROR_CATEGORY_LABELS[category]} (${rows.length})`} size="small" sx={{ mb: 0.5 }} />
                    <List dense>
                      {rows.map((row) => (
                        <ListItem key={`failed-${row.nodeId}`} disableGutters>
                          <ListItemText
                            primary={row.nodeId}
                            secondary={row.errorMessage}
                          />
                        </ListItem>
                      ))}
                    </List>
                  </Box>
                );
              })}
            </Stack>
          </Alert>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={submitting}>
          Close
        </Button>
        <Button
          onClick={() => void handleSubmit()}
          variant="contained"
          color="warning"
          disabled={submitDisabled}
          data-testid="bulk-declare-submit"
        >
          {submitting ? 'Declaring...' : `Declare ${parsedUuids.length} Record${parsedUuids.length === 1 ? '' : 's'}`}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default BulkDeclareRecordsDialog;
