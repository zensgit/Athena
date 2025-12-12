import React from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
} from '@mui/material';
import { useForm } from 'react-hook-form';
import { useAppDispatch, useAppSelector } from 'store';
import { setCreateFolderDialogOpen } from 'store/slices/uiSlice';
import { createFolder } from 'store/slices/nodeSlice';
import { toast } from 'react-toastify';

interface CreateFolderFormData {
  name: string;
  description?: string;
}

const CreateFolderDialog: React.FC = () => {
  const dispatch = useAppDispatch();
  const { createFolderDialogOpen } = useAppSelector((state) => state.ui);
  const { currentNode } = useAppSelector((state) => state.node);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<CreateFolderFormData>();

  const handleClose = () => {
    dispatch(setCreateFolderDialogOpen(false));
    reset();
  };

  const onSubmit = async (data: CreateFolderFormData) => {
    if (!currentNode) return;

    try {
      await dispatch(
        createFolder({
          parentId: currentNode.id,
          name: data.name,
          properties: data.description ? { description: data.description } : undefined,
        })
      ).unwrap();

      toast.success('Folder created successfully');
      handleClose();
    } catch (error) {
      toast.error('Failed to create folder');
    }
  };

  return (
    <Dialog
      open={createFolderDialogOpen}
      onClose={handleClose}
      maxWidth="sm"
      fullWidth
    >
      <form onSubmit={handleSubmit(onSubmit)}>
        <DialogTitle>Create New Folder</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            margin="dense"
            label="Folder Name"
            fullWidth
            variant="outlined"
            error={!!errors.name}
            helperText={errors.name?.message}
            {...register('name', {
              required: 'Folder name is required',
              pattern: {
                value: /^[^<>:"/\\|?*]+$/,
                message: 'Invalid characters in folder name',
              },
            })}
          />
          <TextField
            margin="dense"
            label="Description (optional)"
            fullWidth
            variant="outlined"
            multiline
            rows={3}
            {...register('description')}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose}>Cancel</Button>
          <Button type="submit" variant="contained">
            Create
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
};

export default CreateFolderDialog;