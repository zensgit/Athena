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
import { FilePlan } from 'types';

interface MoveFilePlanDialogProps {
  open: boolean;
  filePlan?: FilePlan | null;
  filePlans: FilePlan[];
  onClose: () => void;
  onMoved?: (updated: FilePlan) => void | Promise<void>;
}

const MoveFilePlanDialog: React.FC<MoveFilePlanDialogProps> = ({
  open,
  filePlan,
  filePlans,
  onClose,
  onMoved,
}) => {
  const [targetParentId, setTargetParentId] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const parentOptions = useMemo(() => {
    if (!filePlan) {
      return filePlans;
    }
    const subtreePrefix = `${filePlan.path}/`;
    return filePlans.filter((candidate) =>
      candidate.folderId !== filePlan.folderId &&
      candidate.folderId !== filePlan.parentId &&
      !candidate.path.startsWith(subtreePrefix)
    );
  }, [filePlan, filePlans]);

  useEffect(() => {
    if (!open) {
      setSubmitting(false);
      setTargetParentId('');
      return;
    }
    setTargetParentId('');
  }, [filePlan, open]);

  const handleMove = async () => {
    if (!filePlan?.folderId || !targetParentId || submitting) {
      return;
    }

    setSubmitting(true);
    try {
      const updated = await recordsManagementService.moveFilePlan(filePlan.folderId, {
        targetParentId,
      });
      toast.success('File plan moved');
      await onMoved?.(updated);
      onClose();
    } catch {
      toast.error('Failed to move file plan');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onClose={submitting ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Move File Plan</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Move "{filePlan?.name ?? 'file plan'}" under a new RM parent file plan. Descendant paths and search documents will be repaired automatically.
        </Typography>
        <Alert severity="info" sx={{ mb: 2 }}>
          This thin UI currently exposes other file plans as safe RM parents. Workspace and system-root targets remain deferred.
        </Alert>
        <FormControl fullWidth>
          <InputLabel id="move-file-plan-parent-label">New Parent File Plan</InputLabel>
          <Select
            labelId="move-file-plan-parent-label"
            label="New Parent File Plan"
            value={targetParentId}
            onChange={(event) => setTargetParentId(event.target.value)}
          >
            <MenuItem value="">
              <em>Select a new file plan parent</em>
            </MenuItem>
            {parentOptions.map((candidate) => (
              <MenuItem key={candidate.folderId} value={candidate.folderId}>
                {candidate.path}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
        {parentOptions.length === 0 && (
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
            No alternate file plan targets are currently available in this RM view.
          </Typography>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={submitting}>
          Cancel
        </Button>
        <Button
          onClick={() => void handleMove()}
          variant="contained"
          disabled={!filePlan?.folderId || !targetParentId || submitting}
        >
          {submitting ? 'Moving...' : 'Move File Plan'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default MoveFilePlanDialog;
