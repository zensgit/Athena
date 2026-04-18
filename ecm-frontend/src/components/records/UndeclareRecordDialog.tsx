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

interface UndeclareRecordDialogProps {
  open: boolean;
  nodeId?: string | null;
  nodeName: string;
  onClose: () => void;
  onUndeclared?: () => void;
}

const UndeclareRecordDialog: React.FC<UndeclareRecordDialogProps> = ({
  open,
  nodeId,
  nodeName,
  onClose,
  onUndeclared,
}) => {
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [reasonError, setReasonError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) {
      setReason('');
      setSubmitting(false);
      setReasonError(null);
    }
  }, [open]);

  const handleReasonChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    setReason(event.target.value);
    if (reasonError) {
      setReasonError(null);
    }
  };

  const handleUndeclare = async () => {
    if (!nodeId || submitting) {
      return;
    }
    const trimmedReason = reason.trim();
    if (!trimmedReason) {
      setReasonError('Reason is required');
      return;
    }

    setSubmitting(true);
    try {
      await recordsManagementService.undeclareRecord(nodeId, { reason: trimmedReason });
      toast.success('Document undeclared as record');
      onUndeclared?.();
      onClose();
    } catch {
      toast.error('Failed to undeclare document as record');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onClose={submitting ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Undeclare Record</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Remove the record declaration from "{nodeName}". Content, versions, ACLs, and path are unchanged.
        </Typography>
        <Alert severity="warning" sx={{ mb: 2 }}>
          This action only removes the record state. It does not delete the document or its versions.
        </Alert>
        <TextField
          fullWidth
          multiline
          minRows={3}
          label="Reason"
          required
          value={reason}
          onChange={handleReasonChange}
          error={Boolean(reasonError)}
          helperText={reasonError || 'Explain why this record declaration is being removed.'}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={submitting}>
          Cancel
        </Button>
        <Button onClick={() => void handleUndeclare()} variant="contained" color="warning" disabled={!nodeId || submitting}>
          {submitting ? 'Undeclaring...' : 'Undeclare Record'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default UndeclareRecordDialog;
