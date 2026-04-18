import React, { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  TextField,
  Typography,
} from '@mui/material';
import { toast } from 'react-toastify';
import recordsManagementService from 'services/recordsManagementService';
import { FilePlan } from 'types';

interface RenameFilePlanDialogProps {
  open: boolean;
  filePlan?: FilePlan | null;
  onClose: () => void;
  onRenamed?: (updated: FilePlan) => void | Promise<void>;
}

const RenameFilePlanDialog: React.FC<RenameFilePlanDialogProps> = ({
  open,
  filePlan,
  onClose,
  onRenamed,
}) => {
  const [name, setName] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [nameError, setNameError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) {
      setSubmitting(false);
      setNameError(null);
      setName('');
      return;
    }
    setName(filePlan?.name ?? '');
  }, [filePlan, open]);

  const handleRename = async () => {
    if (!filePlan?.folderId || submitting) {
      return;
    }
    const trimmedName = name.trim();
    if (!trimmedName) {
      setNameError('File plan name is required');
      return;
    }

    setSubmitting(true);
    try {
      const updated = await recordsManagementService.renameFilePlan(filePlan.folderId, {
        name: trimmedName,
      });
      toast.success('File plan renamed');
      await onRenamed?.(updated);
      onClose();
    } catch {
      toast.error('Failed to rename file plan');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onClose={submitting ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Rename File Plan</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Rename "{filePlan?.name ?? 'file plan'}". Descendant node paths and search documents will be repaired automatically.
        </Typography>
        <Alert severity="info" sx={{ mb: 2 }}>
          This keeps the RM subtree intact and reindexes the affected file-plan scope after commit.
        </Alert>
        <TextField
          fullWidth
          label="File Plan Name"
          value={name}
          onChange={(event) => {
            setName(event.target.value);
            if (nameError) {
              setNameError(null);
            }
          }}
          error={Boolean(nameError)}
          helperText={nameError || 'Choose the new display name for this file plan subtree.'}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={submitting}>
          Cancel
        </Button>
        <Button
          onClick={() => void handleRename()}
          variant="contained"
          disabled={!filePlan?.folderId || submitting}
        >
          {submitting ? 'Renaming...' : 'Rename File Plan'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default RenameFilePlanDialog;
