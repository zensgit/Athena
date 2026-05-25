import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  AlertTitle,
  Button,
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
import shareLinkService, {
  BulkCreateShareLinkResult,
  SharePermission,
} from 'services/shareLinkService';

export interface BulkShareLinksDialogProps {
  open: boolean;
  onClose: () => void;
  // Document IDs only — FileBrowser filters out folders before passing them in (gate D4).
  documentIds: string[];
  // Count of selected nodes that were excluded because they are not documents (for copy only).
  excludedNonDocumentCount?: number;
  onCreated?: () => void;
}

const PERMISSIONS: SharePermission[] = ['VIEW', 'COMMENT', 'EDIT'];

const BulkShareLinksDialog: React.FC<BulkShareLinksDialogProps> = ({
  open,
  onClose,
  documentIds,
  excludedNonDocumentCount = 0,
  onCreated,
}) => {
  const [permissionLevel, setPermissionLevel] = useState<SharePermission>('VIEW');
  const [name, setName] = useState('');
  const [expiryDate, setExpiryDate] = useState('');
  const [maxAccessCount, setMaxAccessCount] = useState('');
  const [password, setPassword] = useState('');
  const [allowedIps, setAllowedIps] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [failedRows, setFailedRows] = useState<BulkCreateShareLinkResult[]>([]);

  useEffect(() => {
    if (open) {
      setPermissionLevel('VIEW');
      setName('');
      setExpiryDate('');
      setMaxAccessCount('');
      setPassword('');
      setAllowedIps('');
      setFailedRows([]);
      setSubmitting(false);
    }
  }, [open]);

  const submitDisabled = submitting || documentIds.length === 0;

  const failedByCategory = useMemo(() => {
    const groups: Record<string, BulkCreateShareLinkResult[]> = {};
    failedRows.forEach((row) => {
      const key = row.errorCategory || 'INTERNAL_ERROR';
      (groups[key] = groups[key] || []).push(row);
    });
    return groups;
  }, [failedRows]);

  const handleSubmit = async () => {
    if (documentIds.length === 0) return;
    setSubmitting(true);
    setFailedRows([]);
    try {
      const response = await shareLinkService.bulkCreateLinks({
        nodeIds: documentIds,
        permissionLevel,
        name: name.trim() || undefined,
        expiryDate: expiryDate.trim() || undefined,
        maxAccessCount: maxAccessCount.trim() ? Number(maxAccessCount.trim()) : undefined,
        password: password ? password : undefined,
        allowedIps: allowedIps.trim() || undefined,
      });
      const rows = response.bulkShareLinkCreateResults.rows;
      const created = rows.filter((r) => r.status === 'CREATED');
      const failed = rows.filter((r) => r.status === 'FAILED');

      if (failed.length === 0) {
        toast.success(`Share links created: ${created.length}`);
        onCreated?.();
        onClose();
        return;
      }

      setFailedRows(failed);
      if (created.length > 0) onCreated?.();
      toast.warn(`Bulk share: ${created.length} created, ${failed.length} failed`);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Bulk share-link creation failed';
      toast.error(message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth data-testid="bulk-share-dialog">
      <DialogTitle>Create share links for selected documents</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2}>
          <Typography variant="body2" color="text.secondary">
            {documentIds.length} document{documentIds.length === 1 ? '' : 's'} selected. The settings
            below apply to every created link.
            {excludedNonDocumentCount > 0
              ? ` ${excludedNonDocumentCount} non-document item(s) are excluded — bulk sharing applies to documents only.`
              : ''}
          </Typography>

          <FormControl fullWidth size="small">
            <InputLabel id="bulk-share-permission-label">Permission</InputLabel>
            <Select
              labelId="bulk-share-permission-label"
              label="Permission"
              value={permissionLevel}
              onChange={(e) => setPermissionLevel(e.target.value as SharePermission)}
              inputProps={{ 'data-testid': 'bulk-share-permission' }}
            >
              {PERMISSIONS.map((p) => (
                <MenuItem key={p} value={p}>{p}</MenuItem>
              ))}
            </Select>
          </FormControl>

          <TextField
            label="Name (optional, applied to every link)"
            value={name}
            onChange={(e) => setName(e.target.value)}
            size="small"
            fullWidth
            inputProps={{ 'data-testid': 'bulk-share-name' }}
          />
          <TextField
            label="Expiry (optional)"
            type="datetime-local"
            value={expiryDate}
            onChange={(e) => setExpiryDate(e.target.value)}
            size="small"
            fullWidth
            InputLabelProps={{ shrink: true }}
          />
          <TextField
            label="Max access count (optional)"
            type="number"
            value={maxAccessCount}
            onChange={(e) => setMaxAccessCount(e.target.value)}
            size="small"
            fullWidth
          />
          <TextField
            label="Password (optional)"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            size="small"
            fullWidth
          />
          <TextField
            label="Allowed IPs (optional, comma-separated)"
            value={allowedIps}
            onChange={(e) => setAllowedIps(e.target.value)}
            size="small"
            fullWidth
          />

          {failedRows.length > 0 && (
            <Alert severity="error" data-testid="bulk-share-failed-rows">
              <AlertTitle>{failedRows.length} failed</AlertTitle>
              {Object.entries(failedByCategory).map(([category, rows]) => (
                <div key={category} data-testid={`bulk-share-failed-${category.toLowerCase()}`}>
                  <Typography variant="caption" color="text.secondary">{category} ({rows.length})</Typography>
                  <List dense disablePadding>
                    {rows.map((row) => (
                      <ListItem key={row.nodeId} disableGutters>
                        <ListItemText primary={row.nodeId} secondary={row.message || category} />
                      </ListItem>
                    ))}
                  </List>
                </div>
              ))}
            </Alert>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={submitting}>Close</Button>
        <Button
          variant="contained"
          onClick={handleSubmit}
          disabled={submitDisabled}
          data-testid="bulk-share-submit"
        >
          Create links
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default BulkShareLinksDialog;
