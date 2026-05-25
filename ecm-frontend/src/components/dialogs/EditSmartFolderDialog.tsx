import React from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Typography,
} from '@mui/material';
import { useForm } from 'react-hook-form';
import { toast } from 'react-toastify';
import nodeService from 'services/nodeService';
import { Node } from 'types';

interface EditSmartFolderFormData {
  searchQuery: string;
  pathPrefix?: string;
}

export interface EditSmartFolderDialogProps {
  open: boolean;
  onClose: () => void;
  // The smart folder to edit, fetched fresh via nodeService.getFolder so queryCriteria is current.
  folder: Node | null;
  onUpdated?: () => void;
}

const EditSmartFolderDialog: React.FC<EditSmartFolderDialogProps> = ({
  open,
  onClose,
  folder,
  onUpdated,
}) => {
  const [submitting, setSubmitting] = React.useState(false);

  const buildDefaults = React.useCallback(
    (): EditSmartFolderFormData => ({
      searchQuery: (folder?.queryCriteria?.query as string) || '',
      pathPrefix: (folder?.queryCriteria?.pathPrefix as string) || '',
    }),
    [folder],
  );

  const {
    register,
    handleSubmit,
    reset,
    watch,
    formState: { errors },
  } = useForm<EditSmartFolderFormData>({ defaultValues: buildDefaults() });

  // Re-seed from the freshly fetched folder each time the dialog opens.
  React.useEffect(() => {
    if (open) {
      reset(buildDefaults());
    }
  }, [open, buildDefaults, reset]);

  const handleClose = () => {
    if (submitting) return;
    onClose();
  };

  const onSubmit = async (data: EditSmartFolderFormData) => {
    if (!folder) return;
    const trimmedQuery = data.searchQuery?.trim();
    if (!trimmedQuery) {
      toast.error('Search query is required for smart folders');
      return;
    }
    const trimmedPathPrefix = data.pathPrefix?.trim();

    setSubmitting(true);
    try {
      await nodeService.updateFolder(folder.id, {
        // Sent explicitly (self-describing) — do not rely on the server inferring smart-ness.
        isSmart: true,
        queryCriteria: {
          query: trimmedQuery,
          ...(trimmedPathPrefix ? { pathPrefix: trimmedPathPrefix } : {}),
        },
      });
      toast.success('Smart folder updated');
      onUpdated?.();
      onClose();
    } catch (error) {
      // Server-side failures (validation 400, no-permission 403, locked-folder 500) are already
      // surfaced by the api response interceptor, which toasts error.response.data.message
      // (e.g. the backend's "Folder is locked by ..." text). Re-toasting here would double up,
      // so we only keep the dialog open for retry.
    } finally {
      setSubmitting(false);
    }
  };

  const queryValue = watch('searchQuery');
  const submitDisabled = submitting || !queryValue?.trim();

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth data-testid="edit-smart-folder-dialog">
      <form onSubmit={handleSubmit(onSubmit)}>
        <DialogTitle>Edit Smart Folder Query</DialogTitle>
        <DialogContent>
          {folder && (
            <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
              Editing “{folder.name}”. Smart folders re-evaluate on every open, so the new query
              takes effect immediately.
            </Typography>
          )}
          <TextField
            autoFocus
            margin="dense"
            label="Search Query"
            fullWidth
            variant="outlined"
            error={!!errors.searchQuery}
            helperText={errors.searchQuery?.message || 'Required. Full-text query used to populate the smart folder.'}
            inputProps={{ 'data-testid': 'edit-smart-folder-query' }}
            {...register('searchQuery', {
              validate: (value) => (value?.trim() ? true : 'Search query is required for smart folders'),
            })}
          />
          <TextField
            margin="dense"
            label="Path Prefix (optional)"
            fullWidth
            variant="outlined"
            helperText="Optional. Limit smart-folder results to this path prefix."
            inputProps={{ 'data-testid': 'edit-smart-folder-pathprefix' }}
            {...register('pathPrefix')}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose} disabled={submitting}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={submitDisabled} data-testid="edit-smart-folder-submit">
            Save
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
};

export default EditSmartFolderDialog;
