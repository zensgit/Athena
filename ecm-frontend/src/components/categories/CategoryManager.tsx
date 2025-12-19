import React, { useEffect, useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Box,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  IconButton,
  Checkbox,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  CircularProgress,
  Typography,
} from '@mui/material';
import { Close, Add, Edit, Delete, Category as CategoryIcon } from '@mui/icons-material';
import categoryService, { CategoryTreeNode, CategoryResponse } from 'services/categoryService';
import { toast } from 'react-toastify';
import { useAppSelector } from 'store';
import authService from 'services/authService';

interface FlatCategory extends CategoryResponse {
  level: number;
}

interface CategoryManagerProps {
  open: boolean;
  onClose: () => void;
  selectedNodeId?: string;
  onCategoriesUpdated?: () => void;
}

const flattenCategories = (nodes: CategoryTreeNode[], acc: FlatCategory[] = []): FlatCategory[] => {
  nodes.forEach((node) => {
    acc.push({
      id: node.id,
      name: node.name,
      description: node.description,
      path: node.path,
      level: node.level,
    });
    if (node.children && node.children.length > 0) {
      flattenCategories(node.children, acc);
    }
  });
  return acc;
};

const CategoryManager: React.FC<CategoryManagerProps> = ({
  open,
  onClose,
  selectedNodeId,
  onCategoriesUpdated,
}) => {
  const { user } = useAppSelector((state) => state.auth);
  const effectiveUser = user ?? authService.getCurrentUser();
  const canWrite = Boolean(
    effectiveUser?.roles?.includes('ROLE_ADMIN') || effectiveUser?.roles?.includes('ROLE_EDITOR')
  );
  const [categories, setCategories] = useState<FlatCategory[]>([]);
  const [assignedCategoryIds, setAssignedCategoryIds] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(false);

  const [createMode, setCreateMode] = useState(false);
  const [editMode, setEditMode] = useState(false);
  const [selectedCategory, setSelectedCategory] = useState<FlatCategory | null>(null);
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    parentId: '',
  });

  const loadData = async () => {
    setLoading(true);
    try {
      const tree = await categoryService.getCategoryTree();
      const flat = flattenCategories(tree);
      setCategories(flat);

      if (selectedNodeId) {
        const nodeCategories = await categoryService.getNodeCategories(selectedNodeId);
        setAssignedCategoryIds(new Set(nodeCategories.map((c) => c.id)));
      } else {
        setAssignedCategoryIds(new Set());
      }
    } catch {
      toast.error('Failed to load categories');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (open) {
      loadData();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, selectedNodeId]);

  const resetForm = () => {
    setFormData({ name: '', description: '', parentId: '' });
    setCreateMode(false);
    setEditMode(false);
    setSelectedCategory(null);
  };

  const handleCreateCategory = () => {
    if (!canWrite) {
      toast.error('Requires write permission');
      return;
    }
    resetForm();
    setCreateMode(true);
  };

  const handleEditCategory = (category: FlatCategory) => {
    if (!canWrite) {
      toast.error('Requires write permission');
      return;
    }
    setSelectedCategory(category);
    setFormData({
      name: category.name,
      description: category.description || '',
      parentId: '',
    });
    setEditMode(true);
    setCreateMode(false);
  };

  const handleSave = async () => {
    if (!canWrite) {
      toast.error('Requires write permission');
      return;
    }
    if (!formData.name.trim()) {
      toast.error('Category name is required');
      return;
    }
    try {
      if (createMode) {
        await categoryService.createCategory({
          name: formData.name,
          description: formData.description || undefined,
          parentId: formData.parentId || null,
        });
        toast.success('Category created');
      } else if (editMode && selectedCategory) {
        await categoryService.updateCategory(selectedCategory.id, {
          name: formData.name,
          description: formData.description || undefined,
        });
        toast.success('Category updated');
      }
      resetForm();
      loadData();
    } catch {
      toast.error(createMode ? 'Failed to create category' : 'Failed to update category');
    }
  };

  const handleDeleteCategory = async (category: FlatCategory) => {
    if (!canWrite) {
      toast.error('Requires write permission');
      return;
    }
    const deleteChildren = window.confirm(
      `Delete category "${category.name}" and all subcategories?\nOK = delete recursively, Cancel = keep children.`
    );
    try {
      await categoryService.deleteCategory(category.id, deleteChildren);
      toast.success('Category deleted');
      loadData();
    } catch {
      toast.error('Failed to delete category');
    }
  };

  const toggleCategoryForNode = async (category: FlatCategory) => {
    if (!selectedNodeId) return;
    if (!canWrite) {
      toast.error('Requires write permission');
      return;
    }
    try {
      const next = new Set(assignedCategoryIds);
      if (next.has(category.id)) {
        await categoryService.removeCategoryFromNode(selectedNodeId, category.id);
        next.delete(category.id);
        toast.success('Category removed from document');
      } else {
        await categoryService.addCategoryToNode(selectedNodeId, category.id);
        next.add(category.id);
        toast.success('Category added to document');
      }
      setAssignedCategoryIds(next);
      if (onCategoriesUpdated) onCategoriesUpdated();
    } catch {
      toast.error('Failed to update document categories');
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>
        Category Manager
        <IconButton
          aria-label="close"
          onClick={() => {
            resetForm();
            onClose();
          }}
          sx={{ position: 'absolute', right: 8, top: 8 }}
        >
          <Close />
        </IconButton>
      </DialogTitle>
      <DialogContent>
        <Box display="flex" gap={2} mb={2}>
          <Button
            variant="contained"
            startIcon={<Add />}
            onClick={handleCreateCategory}
            sx={{ whiteSpace: 'nowrap' }}
            disabled={!canWrite}
          >
            New Category
          </Button>
        </Box>

        {canWrite && (createMode || editMode) && (
          <Paper sx={{ p: 2, mb: 2 }}>
            <Typography variant="h6" gutterBottom>
              {createMode ? 'Create New Category' : 'Edit Category'}
            </Typography>
            <Box display="flex" flexDirection="column" gap={2}>
              <TextField
                fullWidth
                label="Category Name"
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                size="small"
              />
              <TextField
                fullWidth
                label="Description"
                value={formData.description}
                onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                size="small"
                multiline
                rows={2}
              />
              {createMode && (
                <FormControl fullWidth size="small">
                  <InputLabel>Parent Category</InputLabel>
                  <Select
                    value={formData.parentId}
                    label="Parent Category"
                    onChange={(e) =>
                      setFormData({ ...formData, parentId: e.target.value })
                    }
                  >
                    <MenuItem value="">(root)</MenuItem>
                    {categories.map((c) => (
                      <MenuItem key={c.id} value={c.id}>
                        {c.name}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              )}
            </Box>
            <Box display="flex" gap={1} mt={2}>
              <Button variant="contained" onClick={handleSave}>
                Save
              </Button>
              <Button variant="outlined" onClick={resetForm}>
                Cancel
              </Button>
            </Box>
          </Paper>
        )}

        {loading ? (
          <Box display="flex" justifyContent="center" p={4}>
            <CircularProgress />
          </Box>
        ) : (
          <TableContainer component={Paper}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  {selectedNodeId && <TableCell width={60}>Assigned</TableCell>}
                  <TableCell>Category</TableCell>
                  <TableCell>Description</TableCell>
                  <TableCell>Path</TableCell>
                  <TableCell align="right" width={120}>Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {categories.map((category) => (
                  <TableRow key={category.id} hover>
                    {selectedNodeId && (
                      <TableCell padding="checkbox">
                        <Checkbox
                          checked={assignedCategoryIds.has(category.id)}
                          onChange={() => toggleCategoryForNode(category)}
                          disabled={!canWrite}
                          inputProps={{ 'aria-label': `Toggle category ${category.name}` }}
                        />
                      </TableCell>
                    )}
                    <TableCell>
                      <Box display="flex" alignItems="center" sx={{ pl: category.level * 2 }}>
                        <CategoryIcon fontSize="small" sx={{ mr: 1, color: 'primary.main' }} />
                        <Typography variant="body2">{category.name}</Typography>
                      </Box>
                    </TableCell>
                    <TableCell>{category.description || '-'}</TableCell>
                    <TableCell>{category.path}</TableCell>
                    <TableCell align="right">
                      {canWrite && (
                        <IconButton size="small" onClick={() => handleEditCategory(category)}>
                          <Edit fontSize="small" />
                        </IconButton>
                      )}
                      {canWrite && (
                        <IconButton
                          size="small"
                          color="error"
                          onClick={() => handleDeleteCategory(category)}
                        >
                          <Delete fontSize="small" />
                        </IconButton>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
                {categories.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={selectedNodeId ? 5 : 4} align="center">
                      No categories
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={() => { resetForm(); onClose(); }}>Close</Button>
      </DialogActions>
    </Dialog>
  );
};

export default CategoryManager;
