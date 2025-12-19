import React, { useEffect, useMemo, useState } from 'react';
import {
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  TextField,
  Typography,
} from '@mui/material';
import { Node } from 'types';
import FolderTree from 'components/browser/FolderTree';
import { useAppSelector } from 'store';
import authService from 'services/authService';

type MoveCopyMode = 'move' | 'copy';

interface MoveCopyDialogProps {
  open: boolean;
  mode: MoveCopyMode;
  sourceNode: Node | null;
  initialFolderId?: string;
  initialFolderName?: string;
  onClose: () => void;
  onConfirm: (targetFolderId: string, options: { newName?: string }) => Promise<void>;
}

const MoveCopyDialog: React.FC<MoveCopyDialogProps> = ({
  open,
  mode,
  sourceNode,
  initialFolderId,
  initialFolderName,
  onClose,
  onConfirm,
}) => {
  const { user } = useAppSelector((state) => state.auth);
  const effectiveUser = user ?? authService.getCurrentUser();
  const canWrite = Boolean(
    effectiveUser?.roles?.includes('ROLE_ADMIN') || effectiveUser?.roles?.includes('ROLE_EDITOR')
  );
  const [selectedFolderId, setSelectedFolderId] = useState<string | null>(null);
  const [selectedFolderName, setSelectedFolderName] = useState<string>('');
  const [newName, setNewName] = useState<string>('');
  const [submitting, setSubmitting] = useState(false);
  const title = useMemo(() => (mode === 'copy' ? 'Copy Item' : 'Move Item'), [mode]);

  useEffect(() => {
    if (!open) {
      return;
    }
    setSelectedFolderId(initialFolderId || null);
    setSelectedFolderName(initialFolderName || '');
    setNewName(sourceNode?.name ? `copy-${sourceNode.name}` : '');
    setSubmitting(false);
  }, [open, initialFolderId, initialFolderName, sourceNode?.name]);

  const isSourceParentSelected = Boolean(
    sourceNode?.parentId && selectedFolderId && selectedFolderId === sourceNode.parentId
  );
  const moveToSameFolder = mode === 'move' && isSourceParentSelected;
  const copyToSameFolderWithoutRename = mode === 'copy' && isSourceParentSelected && !newName.trim();
  const canSubmit = Boolean(
    canWrite && sourceNode && selectedFolderId && !moveToSameFolder && !copyToSameFolderWithoutRename
  );

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        {sourceNode ? (
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Source: {sourceNode.name}
          </Typography>
        ) : (
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            No item selected
          </Typography>
        )}

        {mode === 'copy' && (
          <TextField
            margin="dense"
            label="New Name (optional)"
            fullWidth
            value={newName}
            onChange={(event) => setNewName(event.target.value)}
            sx={{ mb: 2 }}
            disabled={!canWrite}
          />
        )}

        <Typography variant="subtitle2" sx={{ mb: 1 }}>
          Destination Folder
        </Typography>

        <Box
          sx={{
            border: 1,
            borderColor: 'divider',
            borderRadius: 1,
            p: 1,
            height: 360,
            overflow: 'auto',
          }}
        >
          <FolderTree
            rootNodeId="root"
            selectedNodeId={selectedFolderId || initialFolderId}
            variant="picker"
            onNodeSelect={(node) => {
              if (!canWrite) {
                return;
              }
              if (node.nodeType !== 'FOLDER') {
                return;
              }
              setSelectedFolderId(node.id);
              setSelectedFolderName(node.name);
            }}
          />
        </Box>

        {selectedFolderId && (
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
            Selected: {selectedFolderName || selectedFolderId}
          </Typography>
        )}

        {moveToSameFolder && (
          <Typography
            variant="caption"
            sx={{ display: 'block', mt: 1, color: 'warning.main' }}
          >
            Select a different folder (cannot move into the same folder).
          </Typography>
        )}

        {copyToSameFolderWithoutRename && (
          <Typography
            variant="caption"
            sx={{ display: 'block', mt: 1, color: 'warning.main' }}
          >
            Provide a new name when copying into the same folder.
          </Typography>
        )}

        {!canWrite && (
          <Typography variant="caption" sx={{ display: 'block', mt: 2, color: 'text.secondary' }}>
            Read-only: requires write permission.
          </Typography>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={submitting}>
          Cancel
        </Button>
        <Button
          variant="contained"
          disabled={!canSubmit || submitting}
          onClick={() => {
            if (!canWrite) {
              return;
            }
            if (!sourceNode || !selectedFolderId) {
              return;
            }
            setSubmitting(true);
            void onConfirm(selectedFolderId, { newName: newName.trim() || undefined })
              .finally(() => setSubmitting(false));
          }}
        >
          {submitting ? <CircularProgress size={18} color="inherit" /> : mode === 'copy' ? 'Copy' : 'Move'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default MoveCopyDialog;
