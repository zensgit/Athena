import React, { useState, useEffect } from 'react';
import {
  Box,
  Typography,
  Paper,
  List,
  ListItem,
  ListItemText,
  ListItemSecondaryAction,
  Button,
  Divider,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  TextField,
} from '@mui/material';
import { CheckCircle, Cancel } from '@mui/icons-material';
import workflowService, { Task } from '../services/workflowService';
import { toast } from 'react-toastify';
import { format } from 'date-fns';

const TasksPage: React.FC = () => {
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedTask, setSelectedTask] = useState<Task | null>(null);
  const [actionDialog, setActionDialog] = useState<'approve' | 'reject' | null>(null);
  const [comment, setComment] = useState('');

  useEffect(() => {
    loadTasks();
  }, []);

  const loadTasks = async () => {
    try {
      setLoading(true);
      const data = await workflowService.getMyTasks();
      setTasks(data);
    } catch (error) {
      toast.error('Failed to load tasks');
    } finally {
      setLoading(false);
    }
  };

  const handleAction = async (approved: boolean) => {
    if (!selectedTask) return;

    try {
      await workflowService.completeTask(selectedTask.id, {
        approved,
        comment,
      });
      toast.success(`Task ${approved ? 'approved' : 'rejected'} successfully`);
      setActionDialog(null);
      setComment('');
      loadTasks();
    } catch (error) {
      toast.error('Failed to complete task');
    }
  };

  return (
    <Box p={3}>
      <Typography variant="h4" gutterBottom>
        My Tasks
      </Typography>

      <Paper>
        {tasks.length === 0 && !loading ? (
          <Box p={4} textAlign="center">
            <Typography color="textSecondary">No pending tasks</Typography>
          </Box>
        ) : (
          <List>
            {tasks.map((task, index) => (
              <React.Fragment key={task.id}>
                {index > 0 && <Divider />}
                <ListItem alignItems="flex-start">
                  <ListItemText
                    primary={task.name}
                    secondary={
                      <>
                        <Typography component="span" variant="body2" color="textPrimary">
                          {task.description || 'No description'}
                        </Typography>
                        <br />
                        <Typography component="span" variant="caption" color="textSecondary">
                          Created: {format(new Date(task.createTime), 'PPp')}
                        </Typography>
                      </>
                    }
                  />
                  <ListItemSecondaryAction>
                    <Button
                      variant="contained"
                      color="primary"
                      size="small"
                      startIcon={<CheckCircle />}
                      onClick={() => {
                        setSelectedTask(task);
                        setActionDialog('approve');
                      }}
                      sx={{ mr: 1 }}
                    >
                      Approve
                    </Button>
                    <Button
                      variant="outlined"
                      color="error"
                      size="small"
                      startIcon={<Cancel />}
                      onClick={() => {
                        setSelectedTask(task);
                        setActionDialog('reject');
                      }}
                    >
                      Reject
                    </Button>
                  </ListItemSecondaryAction>
                </ListItem>
              </React.Fragment>
            ))}
          </List>
        )}
      </Paper>

      {/* Action Dialog */}
      <Dialog open={!!actionDialog} onClose={() => setActionDialog(null)}>
        <DialogTitle>
          {actionDialog === 'approve' ? 'Approve Document' : 'Reject Document'}
        </DialogTitle>
        <DialogContent>
          <DialogContentText>
            Are you sure you want to {actionDialog} this document?
            You can add an optional comment below.
          </DialogContentText>
          <TextField
            autoFocus
            margin="dense"
            label="Comment"
            fullWidth
            multiline
            rows={3}
            value={comment}
            onChange={(e) => setComment(e.target.value)}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setActionDialog(null)}>Cancel</Button>
          <Button
            onClick={() => handleAction(actionDialog === 'approve')}
            color={actionDialog === 'approve' ? 'primary' : 'error'}
            variant="contained"
          >
            Confirm
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default TasksPage;
