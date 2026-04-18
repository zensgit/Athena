import React, { useEffect, useState } from 'react';
import {
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
import { RecordDeclaration } from 'types';

interface DeclareRecordDialogProps {
  open: boolean;
  nodeId?: string | null;
  nodeName: string;
  onClose: () => void;
  onDeclared?: (declaration: RecordDeclaration) => void;
}

const DeclareRecordDialog: React.FC<DeclareRecordDialogProps> = ({
  open,
  nodeId,
  nodeName,
  onClose,
  onDeclared,
}) => {
  const [comment, setComment] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) {
      setComment('');
      setSubmitting(false);
    }
  }, [open]);

  const handleDeclare = async () => {
    if (!nodeId || submitting) {
      return;
    }
    setSubmitting(true);
    try {
      const declaration = await recordsManagementService.declareRecord(nodeId, {
        comment: comment.trim() || undefined,
      });
      toast.success('Document declared as record');
      onDeclared?.(declaration);
      onClose();
    } catch {
      toast.error('Failed to declare document as record');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onClose={submitting ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Declare Record</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Declare "{nodeName}" as an immutable record. This action cannot be reversed in the current UI.
        </Typography>
        <TextField
          fullWidth
          multiline
          minRows={3}
          label="Declaration Comment (optional)"
          value={comment}
          onChange={(event) => setComment(event.target.value)}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={submitting}>
          Cancel
        </Button>
        <Button onClick={() => void handleDeclare()} variant="contained" color="warning" disabled={!nodeId || submitting}>
          {submitting ? 'Declaring...' : 'Declare Record'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default DeclareRecordDialog;
