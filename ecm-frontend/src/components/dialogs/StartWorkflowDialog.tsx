import React, { useEffect, useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Box,
  Typography,
  Chip,
  OutlinedInput,
  CircularProgress,
} from '@mui/material';
import peopleService from '../../services/peopleService';
import workflowService, { WorkflowFormModelElement } from '../../services/workflowService';
import { toast } from 'react-toastify';
import { User } from 'types';

interface StartWorkflowDialogProps {
  open: boolean;
  onClose: () => void;
  documentId: string;
  documentName: string;
}

const StartWorkflowDialog: React.FC<StartWorkflowDialogProps> = ({
  open,
  onClose,
  documentId,
  documentName,
}) => {
  const [approvers, setApprovers] = useState<string[]>([]);
  const [comment, setComment] = useState('');
  const [loading, setLoading] = useState(false);
  const [availableUsers, setAvailableUsers] = useState<User[]>([]);
  const [loadingUsers, setLoadingUsers] = useState(false);
  const [startFormModel, setStartFormModel] = useState<WorkflowFormModelElement[]>([]);
  const [loadingFormModel, setLoadingFormModel] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }

    let cancelled = false;
    setLoadingUsers(true);
    setLoadingFormModel(true);

    peopleService.search('', 0, 50)
      .then((page) => {
        if (!cancelled) {
          setAvailableUsers(page.content || []);
        }
      })
      .catch(() => {
        if (!cancelled) {
          toast.error('Failed to load approvers');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingUsers(false);
        }
      });

    workflowService.getDefinitions()
      .then(async (definitions) => {
        if (cancelled) {
          return;
        }
        const definition = definitions.find((item) => item.key === 'documentApproval');
        if (!definition) {
          setStartFormModel([]);
          return;
        }
        const formModel = await workflowService.getStartFormModel(definition.id);
        if (!cancelled) {
          setStartFormModel(formModel || []);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setStartFormModel([]);
          toast.error('Failed to load workflow start form model');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingFormModel(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [open]);

  const approversField = startFormModel.find((field) => field.name === 'approvers');
  const commentField = startFormModel.find((field) => field.name === 'comment');

  const handleStart = async () => {
    if (approvers.length === 0) {
      toast.warning('Please select at least one approver');
      return;
    }

    try {
      setLoading(true);
      await workflowService.submitStartForm(documentId, {
        approvers,
        comment,
      });
      toast.success('Approval workflow started');
      onClose();
      // Reset form
      setApprovers([]);
      setComment('');
    } catch (error) {
      toast.error('Failed to start workflow');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Start Approval</DialogTitle>
      <DialogContent>
        <Box mb={2} mt={1}>
          <Typography variant="subtitle2" gutterBottom>
            Document: {documentName}
          </Typography>
        </Box>

        <Box mb={1} display="flex" alignItems="center" gap={1} flexWrap="wrap">
          <Typography variant="caption" color="text.secondary">
            Start form model
          </Typography>
          {loadingFormModel && <CircularProgress size={14} />}
          {!loadingFormModel && startFormModel.length === 0 && (
            <Chip size="small" label="Default approval form" variant="outlined" />
          )}
          {startFormModel.map((field) => (
            <Chip
              key={field.id}
              size="small"
              variant="outlined"
              label={`${field.title}${field.required ? ' *' : ''}`}
            />
          ))}
        </Box>

        <FormControl fullWidth margin="normal">
          <InputLabel>{approversField?.title || 'Approvers'}</InputLabel>
          <Select
            multiple
            value={approvers}
            disabled={loadingUsers}
            onChange={(e) => {
              const value = e.target.value;
              setApprovers(typeof value === 'string' ? value.split(',') : value);
            }}
            input={<OutlinedInput label={approversField?.title || 'Approvers'} />}
            renderValue={(selected) => (
              <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                {selected.map((value) => (
                  <Chip key={value} label={value} />
                ))}
              </Box>
            )}
          >
            {loadingUsers && (
              <MenuItem disabled value="">
                Loading approvers...
              </MenuItem>
            )}
            {availableUsers.map((user) => (
              <MenuItem key={user.username} value={user.username}>
                {user.username}
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        <TextField
          margin="normal"
          label={commentField?.title || 'Comment / Instructions'}
          fullWidth
          multiline
          rows={3}
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          placeholder={commentField?.placeholder || 'Add optional instructions'}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button onClick={handleStart} variant="contained" color="primary" disabled={loading}>
          {loading ? 'Starting...' : 'Start Workflow'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default StartWorkflowDialog;
