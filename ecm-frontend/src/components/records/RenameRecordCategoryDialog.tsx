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
import { RecordCategory } from 'types';

interface RenameRecordCategoryDialogProps {
  open: boolean;
  category?: RecordCategory | null;
  onClose: () => void;
  onRenamed?: (updated: RecordCategory) => void | Promise<void>;
}

const RenameRecordCategoryDialog: React.FC<RenameRecordCategoryDialogProps> = ({
  open,
  category,
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
    setName(category?.name ?? '');
  }, [category, open]);

  const handleRename = async () => {
    if (!category?.categoryId || submitting) {
      return;
    }
    const trimmedName = name.trim();
    if (!trimmedName) {
      setNameError('Category name is required');
      return;
    }

    setSubmitting(true);
    try {
      const updated = await recordsManagementService.renameRecordCategory(category.categoryId, {
        name: trimmedName,
      });
      toast.success('Record category renamed');
      await onRenamed?.(updated);
      onClose();
    } catch {
      toast.error('Failed to rename record category');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onClose={submitting ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Rename Record Category</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Rename "{category?.name ?? 'record category'}". Descendant category paths and declared-record metadata will be repaired automatically.
        </Typography>
        <Alert severity="info" sx={{ mb: 2 }}>
          This keeps category assignments intact and refreshes affected search documents after commit.
        </Alert>
        <TextField
          fullWidth
          label="Category Name"
          value={name}
          onChange={(event) => {
            setName(event.target.value);
            if (nameError) {
              setNameError(null);
            }
          }}
          error={Boolean(nameError)}
          helperText={nameError || 'Choose the new display name for this category subtree.'}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={submitting}>
          Cancel
        </Button>
        <Button
          onClick={() => void handleRename()}
          variant="contained"
          disabled={!category?.categoryId || submitting}
        >
          {submitting ? 'Renaming...' : 'Rename Category'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default RenameRecordCategoryDialog;
