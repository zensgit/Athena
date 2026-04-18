import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Typography,
} from '@mui/material';
import { toast } from 'react-toastify';
import recordsManagementService from 'services/recordsManagementService';
import { RecordCategory } from 'types';

interface MoveRecordCategoryDialogProps {
  open: boolean;
  category?: RecordCategory | null;
  categories: RecordCategory[];
  onClose: () => void;
  onMoved?: (updated: RecordCategory) => void | Promise<void>;
}

const MoveRecordCategoryDialog: React.FC<MoveRecordCategoryDialogProps> = ({
  open,
  category,
  categories,
  onClose,
  onMoved,
}) => {
  const [targetParentId, setTargetParentId] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const parentOptions = useMemo(() => {
    if (!category) {
      return categories;
    }
    const subtreePrefix = `${category.path}/`;
    return categories.filter((candidate) =>
      candidate.categoryId !== category.categoryId &&
      candidate.categoryId !== category.parentId &&
      !candidate.path.startsWith(subtreePrefix)
    );
  }, [categories, category]);

  useEffect(() => {
    if (!open) {
      setSubmitting(false);
      setTargetParentId('');
      return;
    }
    setTargetParentId('');
  }, [category, open]);

  const handleMove = async () => {
    if (!category?.categoryId || !targetParentId || submitting) {
      return;
    }

    setSubmitting(true);
    try {
      const updated = await recordsManagementService.moveRecordCategory(category.categoryId, {
        targetParentId,
      });
      toast.success('Record category moved');
      await onMoved?.(updated);
      onClose();
    } catch {
      toast.error('Failed to move record category');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onClose={submitting ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Move Record Category</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Move "{category?.name ?? 'record category'}" under a new RM parent. Descendant paths and declared-record metadata will be repaired automatically.
        </Typography>
        <Alert severity="info" sx={{ mb: 2 }}>
          Illegal targets inside the category subtree, including the current parent, are filtered out before submission. Choose a new RM parent explicitly.
        </Alert>
        <FormControl fullWidth>
          <InputLabel id="move-record-category-parent-label">New Parent Category</InputLabel>
          <Select
            labelId="move-record-category-parent-label"
            label="New Parent Category"
            value={targetParentId}
            onChange={(event) => setTargetParentId(event.target.value)}
          >
            <MenuItem value="">
              <em>Select a new RM parent</em>
            </MenuItem>
            {parentOptions.map((candidate) => (
              <MenuItem key={candidate.categoryId} value={candidate.categoryId}>
                {candidate.path}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={submitting}>
          Cancel
        </Button>
        <Button
          onClick={() => void handleMove()}
          variant="contained"
          disabled={!category?.categoryId || !targetParentId || submitting}
        >
          {submitting ? 'Moving...' : 'Move Category'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default MoveRecordCategoryDialog;
