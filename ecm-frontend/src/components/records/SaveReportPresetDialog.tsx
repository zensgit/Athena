import React, { useEffect, useState } from 'react';
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  TextField,
  Typography,
} from '@mui/material';

interface SaveReportPresetDialogProps {
  open: boolean;
  title: string;
  helperText: string;
  initialName: string;
  initialDescription?: string;
  submitLabel?: string;
  submitting?: boolean;
  onClose: () => void;
  onSave: (input: { name: string; description?: string }) => Promise<void> | void;
}

const SaveReportPresetDialog: React.FC<SaveReportPresetDialogProps> = ({
  open,
  title,
  helperText,
  initialName,
  initialDescription,
  submitLabel = 'Save Preset',
  submitting = false,
  onClose,
  onSave,
}) => {
  const [name, setName] = useState(initialName);
  const [description, setDescription] = useState(initialDescription ?? '');
  const [nameError, setNameError] = useState<string | null>(null);

  useEffect(() => {
    if (open) {
      setName(initialName);
      setDescription(initialDescription ?? '');
      setNameError(null);
    }
  }, [initialDescription, initialName, open]);

  const handleSave = async () => {
    const trimmedName = name.trim();
    if (!trimmedName) {
      setNameError('Preset name is required');
      return;
    }
    await onSave({
      name: trimmedName,
      description: description.trim() || undefined,
    });
  };

  return (
    <Dialog open={open} onClose={submitting ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          {helperText}
        </Typography>
        <TextField
          fullWidth
          required
          label="Preset Name"
          value={name}
          onChange={(event) => {
            setName(event.target.value);
            if (nameError) {
              setNameError(null);
            }
          }}
          error={Boolean(nameError)}
          helperText={nameError || 'Choose a reusable name for this RM report preset.'}
          sx={{ mb: 2 }}
        />
        <TextField
          fullWidth
          multiline
          minRows={3}
          label="Description (optional)"
          value={description}
          onChange={(event) => setDescription(event.target.value)}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={submitting}>
          Cancel
        </Button>
        <Button onClick={() => void handleSave()} variant="contained" disabled={submitting}>
          {submitting ? 'Saving...' : submitLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default SaveReportPresetDialog;
