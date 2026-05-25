import React, { useMemo, useState } from 'react';
import {
  Alert,
  AlertTitle,
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  List,
  ListItem,
  ListItemText,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { toast } from 'react-toastify';
import mailAutomationService, {
  MailRulePreviewExportRow,
  MailRulePreviewMessage,
} from 'services/mailAutomationService';

export interface MailPreviewExportDialogProps {
  open: boolean;
  onClose: () => void;
  accountId: string;
  ruleId: string;
  matches: MailRulePreviewMessage[];
  onExported?: () => void;
}

// Generic UUID (8-4-4-4-12), not v4-only — backend uses GenerationType.UUID.
const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

// NUL separator: IMAP folder names can contain spaces, so a plain-space key would let
// distinct (folder, uid) pairs collide. \u0000 cannot appear in a folder name or uid.
const keyOf = (folder: string, uid: string): string => `${folder}\u0000${uid}`;

const MailPreviewExportDialog: React.FC<MailPreviewExportDialogProps> = ({
  open,
  onClose,
  accountId,
  ruleId,
  matches,
  onExported,
}) => {
  const [selectedKeys, setSelectedKeys] = useState<Set<string>>(new Set());
  const [targetFolderId, setTargetFolderId] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [failedRows, setFailedRows] = useState<MailRulePreviewExportRow[]>([]);
  const [skippedRows, setSkippedRows] = useState<MailRulePreviewExportRow[]>([]);

  const trimmedFolderId = targetFolderId.trim();
  const folderIdValid = UUID_REGEX.test(trimmedFolderId);
  const selectedCount = selectedKeys.size;
  const submitDisabled = submitting || selectedCount === 0 || !folderIdValid;

  const toggle = (folder: string, uid: string) => {
    setSelectedKeys((prev) => {
      const next = new Set(prev);
      const key = keyOf(folder, uid);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  };

  const allSelected = matches.length > 0 && selectedCount === matches.length;
  const toggleAll = () => {
    setSelectedKeys(() => {
      if (allSelected) {
        return new Set();
      }
      return new Set(matches.map((m) => keyOf(m.folder, m.uid)));
    });
  };

  const failedByCategory = useMemo(() => failedRows, [failedRows]);

  const handleExport = async () => {
    const selections = matches
      .filter((m) => selectedKeys.has(keyOf(m.folder, m.uid)))
      .map((m) => ({ folder: m.folder, uid: m.uid }));
    if (selections.length === 0 || !folderIdValid) {
      return;
    }
    setSubmitting(true);
    setFailedRows([]);
    setSkippedRows([]);
    try {
      const result = await mailAutomationService.exportPreviewMatches(ruleId, {
        accountId,
        targetFolderId: trimmedFolderId,
        selections,
      });
      const { exported, skipped, failed, rows } = result;

      if (failed === 0) {
        const parts: string[] = [];
        if (exported > 0) parts.push(`${exported} exported`);
        if (skipped > 0) parts.push(`${skipped} skipped`);
        toast.success(`Export: ${parts.join(', ') || 'nothing to do'}`);
        if (exported > 0) onExported?.();
        onClose();
        return;
      }

      // Partial failure — stay open. Drain the successfully-handled selections (EXPORTED +
      // any SKIPPED_*) so a retry resubmits only the FAILED rows; keep FAILED checked.
      const handled = new Set<string>(
        rows
          .filter((r) => r.status !== 'FAILED')
          .map((r) => keyOf(r.folder, r.uid)),
      );
      setSelectedKeys((prev) => {
        const next = new Set(prev);
        handled.forEach((k) => next.delete(k));
        return next;
      });
      setFailedRows(rows.filter((r) => r.status === 'FAILED'));
      setSkippedRows(rows.filter((r) => r.status.startsWith('SKIPPED_')));
      if (exported > 0) onExported?.();
      toast.warn(`Export partial: ${exported} exported, ${skipped} skipped, ${failed} failed`);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Mail preview export failed';
      toast.error(message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth data-testid="mail-export-dialog">
      <DialogTitle>Export matched messages to a folder</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2}>
          <Typography variant="body2" color="text.secondary">
            Re-fetches the selected messages and ingests them into the target folder. The mailbox
            is not modified; already-imported messages are skipped. Re-exporting can create
            duplicate documents.
          </Typography>
          <TextField
            label="Target folder ID (UUID)"
            value={targetFolderId}
            onChange={(e) => setTargetFolderId(e.target.value)}
            error={trimmedFolderId.length > 0 && !folderIdValid}
            helperText={
              trimmedFolderId.length > 0 && !folderIdValid
                ? 'Enter a valid folder UUID'
                : 'Documents are staged into this folder'
            }
            fullWidth
            inputProps={{ 'data-testid': 'mail-export-target-folder' }}
          />

          {matches.length === 0 ? (
            <Alert severity="info">No matched messages to export.</Alert>
          ) : (
            <>
              <FormControlLabel
                control={(
                  <Checkbox
                    checked={allSelected}
                    indeterminate={selectedCount > 0 && !allSelected}
                    onChange={toggleAll}
                    data-testid="mail-export-select-all"
                  />
                )}
                label={`Select all (${selectedCount}/${matches.length} selected)`}
              />
              <List dense disablePadding data-testid="mail-export-rows">
                {matches.map((m) => {
                  const key = keyOf(m.folder, m.uid);
                  return (
                    <ListItem key={key} disableGutters>
                      <Checkbox
                        edge="start"
                        checked={selectedKeys.has(key)}
                        onChange={() => toggle(m.folder, m.uid)}
                        data-testid={`mail-export-row-${m.uid}`}
                      />
                      <ListItemText
                        primary={m.subject || '(no subject)'}
                        secondary={`${m.folder} · uid ${m.uid}${m.processable ? '' : ' · not processable'}`}
                      />
                    </ListItem>
                  );
                })}
              </List>
            </>
          )}

          {failedByCategory.length > 0 && (
            <Alert severity="error" data-testid="mail-export-failed-rows">
              <AlertTitle>{failedByCategory.length} rows failed</AlertTitle>
              <List dense disablePadding>
                {failedByCategory.map((r) => (
                  <ListItem key={keyOf(r.folder, r.uid)} disableGutters>
                    <ListItemText
                      primary={`${r.folder} · uid ${r.uid}`}
                      secondary={r.errorMessage || 'Internal error'}
                    />
                  </ListItem>
                ))}
              </List>
            </Alert>
          )}

          {skippedRows.length > 0 && (
            <Alert severity="info" data-testid="mail-export-skipped-rows">
              <AlertTitle>{skippedRows.length} skipped</AlertTitle>
              <Typography variant="caption">
                Already-imported, missing, or no-content messages were not re-exported.
              </Typography>
            </Alert>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={submitting}>
          Close
        </Button>
        <Button
          variant="contained"
          onClick={handleExport}
          disabled={submitDisabled}
          data-testid="mail-export-submit"
        >
          Export selected
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default MailPreviewExportDialog;
