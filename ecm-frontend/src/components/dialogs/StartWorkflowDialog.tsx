import React, { useState } from 'react';
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
} from '@mui/material';
import workflowService from '../../services/workflowService';
import { toast } from 'react-toastify';

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

  // Mock users for demo - in real app, fetch from userService
  const availableUsers = ['admin', 'user1', 'manager']; 

  const handleStart = async () => {
    if (approvers.length === 0) {
      toast.warning('Please select at least one approver');
      return;
    }

    try {
      setLoading(true);
      await workflowService.startApproval(documentId, approvers, comment);
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

        <FormControl fullWidth margin="normal">
          <InputLabel>Approvers</InputLabel>
          <Select
            multiple
            value={approvers}
            onChange={(e) => {
              const value = e.target.value;
              setApprovers(typeof value === 'string' ? value.split(',') : value);
            }}
            input={<OutlinedInput label="Approvers" />}
            renderValue={(selected) => (
              <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                {selected.map((value) => (
                  <Chip key={value} label={value} />
                ))}
              </Box>
            )}
          >
            {availableUsers.map((user) => (
              <MenuItem key={user} value={user}>
                {user}
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        <TextField
          margin="normal"
          label="Comment / Instructions"
          fullWidth
          multiline
          rows={3}
          value={comment}
          onChange={(e) => setComment(e.target.value)}
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
