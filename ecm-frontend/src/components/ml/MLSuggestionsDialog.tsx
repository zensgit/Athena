import React, { useEffect, useState } from 'react';
import {
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  Typography,
} from '@mui/material';
import { toast } from 'react-toastify';
import { useAppDispatch, useAppSelector } from 'store';
import { setMlSuggestionsOpen } from 'store/slices/uiSlice';
import mlService, { ClassificationResult } from 'services/mlService';
import tagService from 'services/tagService';
import categoryService, { CategoryTreeNode } from 'services/categoryService';

const findCategoryIdByName = (
  nodes: CategoryTreeNode[],
  name: string
): string | null => {
  const target = name.toLowerCase();
  for (const node of nodes) {
    if (node.name.toLowerCase() === target) return node.id;
    if (node.children?.length) {
      const found = findCategoryIdByName(node.children, name);
      if (found) return found;
    }
  }
  return null;
};

const MLSuggestionsDialog: React.FC = () => {
  const dispatch = useAppDispatch();
  const { mlSuggestionsOpen, selectedNodeId } = useAppSelector((state) => state.ui);
  const [loading, setLoading] = useState(false);
  const [tags, setTags] = useState<string[]>([]);
  const [classification, setClassification] = useState<ClassificationResult | null>(null);
  const [suggestedCategoryId, setSuggestedCategoryId] = useState<string | null>(null);
  const [applying, setApplying] = useState(false);

  useEffect(() => {
    const loadSuggestions = async () => {
      if (!selectedNodeId) return;
      setLoading(true);
      try {
        const [suggestedTags, classificationResult, categoryTree] = await Promise.all([
          mlService.suggestTagsForDocument(selectedNodeId).catch(() => []),
          mlService.classifyDocument(selectedNodeId).catch(() => null),
          categoryService.getCategoryTree().catch(() => []),
        ]);

        setTags(suggestedTags || []);
        setClassification(classificationResult);

        if (classificationResult?.success && classificationResult.suggestedCategory) {
          const id = findCategoryIdByName(
            categoryTree,
            classificationResult.suggestedCategory
          );
          setSuggestedCategoryId(id);
        } else {
          setSuggestedCategoryId(null);
        }
      } catch (error) {
        toast.error('Failed to load ML suggestions');
      } finally {
        setLoading(false);
      }
    };

    if (mlSuggestionsOpen && selectedNodeId) {
      loadSuggestions();
    }
  }, [mlSuggestionsOpen, selectedNodeId]);

  const handleClose = () => {
    dispatch(setMlSuggestionsOpen(false));
  };

  const handleApplyTags = async () => {
    if (!selectedNodeId || tags.length === 0) return;
    setApplying(true);
    try {
      await tagService.addTagsToNode(selectedNodeId, tags);
      toast.success('Tags applied');
      handleClose();
    } catch {
      toast.error('Failed to apply tags');
    } finally {
      setApplying(false);
    }
  };

  const handleApplyCategory = async () => {
    if (!selectedNodeId || !suggestedCategoryId) return;
    setApplying(true);
    try {
      await categoryService.addCategoryToNode(selectedNodeId, suggestedCategoryId);
      toast.success('Category applied');
      handleClose();
    } catch {
      toast.error('Failed to apply category');
    } finally {
      setApplying(false);
    }
  };

  return (
    <Dialog open={mlSuggestionsOpen} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle>ML Suggestions</DialogTitle>
      <DialogContent>
        {loading ? (
          <CircularProgress />
        ) : (
          <Stack spacing={2} sx={{ mt: 1 }}>
            <Box>
              <Typography variant="subtitle2">Suggested Category</Typography>
              {classification?.success && classification.suggestedCategory ? (
                <Stack direction="row" spacing={1} alignItems="center">
                  <Chip label={classification.suggestedCategory} color="primary" />
                  {typeof classification.confidence === 'number' && (
                    <Typography variant="caption" color="text.secondary">
                      {(classification.confidence * 100).toFixed(1)}%
                    </Typography>
                  )}
                </Stack>
              ) : (
                <Typography variant="body2" color="text.secondary">
                  No category suggestion
                </Typography>
              )}
            </Box>

            <Box>
              <Typography variant="subtitle2">Suggested Tags</Typography>
              {tags.length > 0 ? (
                <Stack direction="row" spacing={1} flexWrap="wrap">
                  {tags.map((tag) => (
                    <Chip key={tag} label={tag} sx={{ mb: 1 }} />
                  ))}
                </Stack>
              ) : (
                <Typography variant="body2" color="text.secondary">
                  No tag suggestions
                </Typography>
              )}
            </Box>
          </Stack>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>Close</Button>
        <Box sx={{ flexGrow: 1 }} />
        <Button
          onClick={handleApplyCategory}
          disabled={applying || !suggestedCategoryId}
        >
          Apply Category
        </Button>
        <Button
          variant="contained"
          onClick={handleApplyTags}
          disabled={applying || tags.length === 0}
        >
          Apply Tags
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default MLSuggestionsDialog;

