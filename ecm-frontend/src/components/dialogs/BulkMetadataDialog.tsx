import React, { useEffect, useMemo, useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Box,
  Typography,
  TextField,
  Autocomplete,
  CircularProgress,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  FormControlLabel,
  Switch,
  Divider,
  Alert,
} from '@mui/material';
import { toast } from 'react-toastify';
import bulkMetadataService, { BulkMetadataResult } from 'services/bulkMetadataService';
import tagService from 'services/tagService';
import categoryService, { CategoryTreeNode } from 'services/categoryService';
import correspondentService, { Correspondent } from 'services/correspondentService';

interface CategoryOption {
  id: string;
  label: string;
}

interface BulkMetadataDialogProps {
  open: boolean;
  nodeIds: string[];
  onClose: () => void;
  onApplied?: (result: BulkMetadataResult) => void;
}

const flattenCategories = (nodes: CategoryTreeNode[], prefix = ''): CategoryOption[] => {
  return nodes.flatMap((node) => {
    const label = prefix ? `${prefix} / ${node.name}` : node.name;
    const entry: CategoryOption = { id: node.id, label };
    const children = node.children?.length ? flattenCategories(node.children, label) : [];
    return [entry, ...children];
  });
};

const BulkMetadataDialog: React.FC<BulkMetadataDialogProps> = ({ open, nodeIds, onClose, onApplied }) => {
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [availableTags, setAvailableTags] = useState<string[]>([]);
  const [availableCategories, setAvailableCategories] = useState<CategoryOption[]>([]);
  const [availableCorrespondents, setAvailableCorrespondents] = useState<Correspondent[]>([]);
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [selectedCategories, setSelectedCategories] = useState<CategoryOption[]>([]);
  const [selectedCorrespondent, setSelectedCorrespondent] = useState('');
  const [clearCorrespondent, setClearCorrespondent] = useState(false);

  const hasChanges = useMemo(() => {
    return selectedTags.length > 0
      || selectedCategories.length > 0
      || Boolean(selectedCorrespondent)
      || clearCorrespondent;
  }, [selectedTags, selectedCategories, selectedCorrespondent, clearCorrespondent]);

  useEffect(() => {
    if (!open) {
      return;
    }

    let cancelled = false;

    const loadMetadata = async () => {
      setLoading(true);
      try {
        const [tags, categoryTree, correspondents] = await Promise.all([
          tagService.getAllTags(),
          categoryService.getCategoryTree(),
          correspondentService.list(0, 500),
        ]);
        if (!cancelled) {
          setAvailableTags(tags.map((tag) => tag.name));
          setAvailableCategories(flattenCategories(categoryTree));
          setAvailableCorrespondents(correspondents);
        }
      } catch {
        if (!cancelled) {
          toast.error('Failed to load metadata options');
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    loadMetadata();
    return () => {
      cancelled = true;
    };
  }, [open]);

  useEffect(() => {
    if (!open) {
      setSelectedTags([]);
      setSelectedCategories([]);
      setSelectedCorrespondent('');
      setClearCorrespondent(false);
    }
  }, [open]);

  const handleApply = async () => {
    if (!nodeIds.length) {
      toast.warn('Select at least one item to update');
      return;
    }
    if (!hasChanges) {
      toast.warn('Select tags, categories, or a correspondent to apply');
      return;
    }

    setSaving(true);
    try {
      const result = await bulkMetadataService.applyMetadata({
        ids: nodeIds,
        tagNames: selectedTags.length ? selectedTags : undefined,
        categoryIds: selectedCategories.map((category) => category.id),
        correspondentId: clearCorrespondent ? null : selectedCorrespondent || null,
        clearCorrespondent,
      });

      if (result.failureCount > 0) {
        toast.warn(`Updated ${result.successCount} items; ${result.failureCount} failed`);
      } else {
        toast.success(`Updated ${result.successCount} items`);
      }

      onApplied?.(result);
      onClose();
    } catch {
      toast.error('Failed to apply metadata updates');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Bulk Metadata Update</DialogTitle>
      <DialogContent>
        <Box display="flex" flexDirection="column" gap={2} mt={1}>
          <Alert severity="info">
            Tags and categories are added to existing values. Correspondent can be replaced or cleared.
          </Alert>

          {loading ? (
            <Box display="flex" justifyContent="center" py={3}>
              <CircularProgress size={24} />
            </Box>
          ) : (
            <>
              <Autocomplete
                multiple
                freeSolo
                options={availableTags}
                value={selectedTags}
                onChange={(_, value) => setSelectedTags(value.map((tag) => tag.trim()).filter(Boolean))}
                renderInput={(params) => (
                  <TextField
                    {...params}
                    label="Tags"
                    placeholder="Add tags"
                    size="small"
                  />
                )}
              />

              <Autocomplete
                multiple
                options={availableCategories}
                getOptionLabel={(option) => option.label}
                value={selectedCategories}
                onChange={(_, value) => setSelectedCategories(value)}
                renderInput={(params) => (
                  <TextField
                    {...params}
                    label="Categories"
                    placeholder="Select categories"
                    size="small"
                  />
                )}
              />

              <Divider />

              <FormControl size="small" fullWidth disabled={clearCorrespondent}>
                <InputLabel id="bulk-correspondent-label">Correspondent</InputLabel>
                <Select
                  labelId="bulk-correspondent-label"
                  value={selectedCorrespondent}
                  label="Correspondent"
                  onChange={(event) => setSelectedCorrespondent(event.target.value)}
                >
                  <MenuItem value="">
                    <em>None</em>
                  </MenuItem>
                  {availableCorrespondents.map((correspondent) => (
                    <MenuItem key={correspondent.id} value={correspondent.id}>
                      {correspondent.name}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>

              <FormControlLabel
                control={(
                  <Switch
                    checked={clearCorrespondent}
                    onChange={(event) => setClearCorrespondent(event.target.checked)}
                  />
                )}
                label="Clear correspondent"
              />
            </>
          )}

          <Typography variant="caption" color="text.secondary">
            {nodeIds.length} item(s) selected.
          </Typography>
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={saving}>Cancel</Button>
        <Button
          variant="contained"
          onClick={handleApply}
          disabled={saving || loading || !hasChanges}
        >
          {saving ? 'Applying…' : 'Apply'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default BulkMetadataDialog;
