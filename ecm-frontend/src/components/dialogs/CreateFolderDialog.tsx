import React from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  FormControlLabel,
  Switch,
} from '@mui/material';
import { useForm } from 'react-hook-form';
import { useAppDispatch, useAppSelector } from 'store';
import { setCreateFolderDialogOpen } from 'store/slices/uiSlice';
import { createFolder } from 'store/slices/nodeSlice';
import { toast } from 'react-toastify';
import authService from 'services/authService';

interface CreateFolderFormData {
  name: string;
  description?: string;
  smart: boolean;
  searchQuery?: string;
  pathPrefix?: string;
}

const CreateFolderDialog: React.FC = () => {
  const dispatch = useAppDispatch();
  const { createFolderDialogOpen } = useAppSelector((state) => state.ui);
  const { currentNode } = useAppSelector((state) => state.node);
  const { user } = useAppSelector((state) => state.auth);
  const effectiveUser = user ?? authService.getCurrentUser();
  const canWrite = Boolean(
    effectiveUser?.roles?.includes('ROLE_ADMIN') || effectiveUser?.roles?.includes('ROLE_EDITOR')
  );

  const buildDefaults = React.useCallback(
    () => ({
      name: '',
      description: '',
      smart: false,
      searchQuery: '',
      pathPrefix: currentNode?.path || '',
    }),
    [currentNode?.path]
  );

  const {
    register,
    handleSubmit,
    reset,
    watch,
    setValue,
    clearErrors,
    formState: { errors },
  } = useForm<CreateFolderFormData>({
    defaultValues: buildDefaults(),
  });

  const smartFolder = watch('smart', false);

  React.useEffect(() => {
    if (createFolderDialogOpen) {
      reset(buildDefaults());
    }
  }, [buildDefaults, createFolderDialogOpen, reset]);

  const handleClose = () => {
    dispatch(setCreateFolderDialogOpen(false));
    reset(buildDefaults());
  };

  const onSubmit = async (data: CreateFolderFormData) => {
    if (!canWrite) {
      toast.error('Requires write permission');
      return;
    }

    const trimmedName = data.name.trim();
    const trimmedDescription = data.description?.trim() || undefined;
    const trimmedQuery = data.searchQuery?.trim();
    const trimmedPathPrefix = data.pathPrefix?.trim();
    if (data.smart && !trimmedQuery) {
      toast.error('Search query is required for smart folders');
      return;
    }

    const parentId = currentNode?.id || 'root';

    try {
      await dispatch(
        createFolder({
          parentId,
          name: trimmedName,
          description: trimmedDescription,
          isSmart: data.smart,
          queryCriteria: data.smart
            ? {
                query: trimmedQuery,
                ...(trimmedPathPrefix ? { pathPrefix: trimmedPathPrefix } : {}),
              }
            : undefined,
        })
      ).unwrap();

      toast.success(data.smart ? 'Smart folder created successfully' : 'Folder created successfully');
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
            disabled={!canWrite}
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
            disabled={!canWrite}
            {...register('description')}
          />
          <FormControlLabel
            control={
              <Switch
                checked={smartFolder}
                disabled={!canWrite}
                onChange={(event) => {
                  const checked = event.target.checked;
                  setValue('smart', checked, { shouldDirty: true, shouldValidate: true });
                  if (!checked) {
                    setValue('searchQuery', '', { shouldDirty: true });
                    setValue('pathPrefix', '', { shouldDirty: true });
                    clearErrors(['searchQuery', 'pathPrefix']);
                    return;
                  }
                  if (!watch('pathPrefix') && currentNode?.path) {
                    setValue('pathPrefix', currentNode.path, { shouldDirty: true });
                  }
                }}
              />
            }
            label="Create as Smart Folder"
            sx={{ mt: 1 }}
          />
          {smartFolder && (
            <>
              <TextField
                margin="dense"
                label="Search Query"
                fullWidth
                variant="outlined"
                error={!!errors.searchQuery}
                helperText={errors.searchQuery?.message || 'Required. Full-text query used to populate the smart folder.'}
                disabled={!canWrite}
                {...register('searchQuery', {
                  validate: (value) =>
                    !watch('smart') || value?.trim() ? true : 'Search query is required for smart folders',
                })}
              />
              <TextField
                margin="dense"
                label="Path Prefix (optional)"
                fullWidth
                variant="outlined"
                helperText="Optional. Limit smart-folder results to this path prefix."
                disabled={!canWrite}
                {...register('pathPrefix')}
              />
            </>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={!canWrite}>
            Create
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
};

export default CreateFolderDialog;
