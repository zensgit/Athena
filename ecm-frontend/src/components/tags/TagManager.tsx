import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Box,
  Chip,
  IconButton,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Typography,
  Menu,
  MenuItem,
  ListItemIcon,
  ListItemText,
  CircularProgress,
  InputAdornment,
} from '@mui/material';
import {
  Close,
  Add,
  Edit,
  Delete,
  Search,
  Tag as TagIcon,
} from '@mui/icons-material';
import { HexColorPicker } from 'react-colorful';
import tagService from 'services/tagService';
import { toast } from 'react-toastify';

interface Tag {
  id: string;
  name: string;
  description?: string;
  color: string;
  usageCount: number;
  created: string;
  creator: string;
}

interface TagManagerProps {
  open: boolean;
  onClose: () => void;
  selectedNodeId?: string;
  onTagsUpdated?: () => void;
}

const TagManager: React.FC<TagManagerProps> = ({
  open,
  onClose,
  selectedNodeId,
  onTagsUpdated,
}) => {
  const [tags, setTags] = useState<Tag[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedTag, setSelectedTag] = useState<Tag | null>(null);
  const [editMode, setEditMode] = useState(false);
  const [createMode, setCreateMode] = useState(false);
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    color: '#1976d2',
  });
  const [colorPickerOpen, setColorPickerOpen] = useState(false);
  const [contextMenu, setContextMenu] = useState<{
    mouseX: number;
    mouseY: number;
    tag: Tag;
  } | null>(null);

  useEffect(() => {
    if (open) {
      loadTags();
    }
  }, [open]);

  const loadTags = async () => {
    setLoading(true);
    try {
      const allTags = await tagService.getAllTags();
      setTags(allTags);
    } catch (error) {
      toast.error('Failed to load tags');
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = async () => {
    if (!searchQuery.trim()) {
      loadTags();
      return;
    }

    setLoading(true);
    try {
      const results = await tagService.searchTags(searchQuery);
      setTags(results);
    } catch (error) {
      toast.error('Failed to search tags');
    } finally {
      setLoading(false);
    }
  };

  const handleCreateTag = () => {
    setCreateMode(true);
    setEditMode(false);
    setFormData({
      name: '',
      description: '',
      color: '#1976d2',
    });
  };

  const handleEditTag = (tag: Tag) => {
    setSelectedTag(tag);
    setEditMode(true);
    setCreateMode(false);
    setFormData({
      name: tag.name,
      description: tag.description || '',
      color: tag.color,
    });
    handleCloseContextMenu();
  };

  const handleDeleteTag = async (tag: Tag) => {
    if (window.confirm(`Delete tag "${tag.name}"? This action cannot be undone.`)) {
      try {
        await tagService.deleteTag(tag.id);
        toast.success('Tag deleted successfully');
        loadTags();
      } catch (error) {
        toast.error('Failed to delete tag');
      }
    }
    handleCloseContextMenu();
  };

  const handleSave = async () => {
    if (!formData.name.trim()) {
      toast.error('Tag name is required');
      return;
    }

    try {
      if (createMode) {
        await tagService.createTag(formData);
        toast.success('Tag created successfully');
      } else if (editMode && selectedTag) {
        await tagService.updateTag(selectedTag.id, formData);
        toast.success('Tag updated successfully');
      }

      setCreateMode(false);
      setEditMode(false);
      setSelectedTag(null);
      loadTags();
      
      if (onTagsUpdated) {
        onTagsUpdated();
      }
    } catch (error) {
      toast.error(createMode ? 'Failed to create tag' : 'Failed to update tag');
    }
  };

  const handleCancel = () => {
    setCreateMode(false);
    setEditMode(false);
    setSelectedTag(null);
    setFormData({
      name: '',
      description: '',
      color: '#1976d2',
    });
  };

  const handleContextMenu = (event: React.MouseEvent, tag: Tag) => {
    event.preventDefault();
    setContextMenu({
      mouseX: event.clientX + 2,
      mouseY: event.clientY - 6,
      tag,
    });
  };

  const handleCloseContextMenu = () => {
    setContextMenu(null);
  };

  const handleAddTagToNode = async (tag: Tag) => {
    if (!selectedNodeId) return;

    try {
      await tagService.addTagToNode(selectedNodeId, tag.name);
      toast.success(`Tag "${tag.name}" added to document`);
      if (onTagsUpdated) {
        onTagsUpdated();
      }
    } catch (error) {
      toast.error('Failed to add tag to document');
    }
  };

  const filteredTags = tags.filter((tag) =>
    tag.name.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>
        Tag Manager
        <IconButton
          aria-label="close"
          onClick={onClose}
          sx={{ position: 'absolute', right: 8, top: 8 }}
        >
          <Close />
        </IconButton>
      </DialogTitle>
      <DialogContent>
        <Box display="flex" gap={2} mb={2}>
          <TextField
            fullWidth
            placeholder="Search tags..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <Search />
                </InputAdornment>
              ),
            }}
          />
          <Button
            variant="contained"
            startIcon={<Add />}
            onClick={handleCreateTag}
            sx={{ whiteSpace: 'nowrap' }}
          >
            New Tag
          </Button>
        </Box>

        {(createMode || editMode) && (
          <Paper sx={{ p: 2, mb: 2 }}>
            <Typography variant="h6" gutterBottom>
              {createMode ? 'Create New Tag' : 'Edit Tag'}
            </Typography>
            <Box display="flex" gap={2} alignItems="flex-start">
              <Box flex={1}>
                <TextField
                  fullWidth
                  label="Tag Name"
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  margin="normal"
                  size="small"
                />
                <TextField
                  fullWidth
                  label="Description"
                  value={formData.description}
                  onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                  margin="normal"
                  size="small"
                  multiline
                  rows={2}
                />
              </Box>
              <Box>
                <Typography variant="body2" color="text.secondary" gutterBottom>
                  Color
                </Typography>
                <Box
                  sx={{
                    width: 40,
                    height: 40,
                    bgcolor: formData.color,
                    borderRadius: 1,
                    cursor: 'pointer',
                    border: '1px solid',
                    borderColor: 'divider',
                  }}
                  onClick={() => setColorPickerOpen(!colorPickerOpen)}
                />
                {colorPickerOpen && (
                  <Box sx={{ position: 'absolute', zIndex: 2, mt: 1 }}>
                    <Box
                      sx={{
                        position: 'fixed',
                        top: 0,
                        right: 0,
                        bottom: 0,
                        left: 0,
                      }}
                      onClick={() => setColorPickerOpen(false)}
                    />
                    <HexColorPicker
                      color={formData.color}
                      onChange={(color) => setFormData({ ...formData, color })}
                    />
                  </Box>
                )}
              </Box>
            </Box>
            <Box display="flex" gap={1} mt={2}>
              <Button variant="contained" onClick={handleSave}>
                Save
              </Button>
              <Button variant="outlined" onClick={handleCancel}>
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
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Tag</TableCell>
                  <TableCell>Description</TableCell>
                  <TableCell align="center">Usage</TableCell>
                  <TableCell>Created By</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {filteredTags.map((tag) => (
                  <TableRow
                    key={tag.id}
                    hover
                    onContextMenu={(e) => handleContextMenu(e, tag)}
                  >
                    <TableCell>
                      <Chip
                        icon={<TagIcon />}
                        label={tag.name}
                        size="small"
                        sx={{ bgcolor: tag.color, color: 'white' }}
                      />
                    </TableCell>
                    <TableCell>{tag.description || '-'}</TableCell>
                    <TableCell align="center">{tag.usageCount}</TableCell>
                    <TableCell>{tag.creator}</TableCell>
                    <TableCell align="right">
                      {selectedNodeId && (
                        <Button
                          size="small"
                          onClick={() => handleAddTagToNode(tag)}
                        >
                          Add to Document
                        </Button>
                      )}
                      <IconButton
                        size="small"
                        onClick={() => handleEditTag(tag)}
                      >
                        <Edit fontSize="small" />
                      </IconButton>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}

        <Menu
          open={contextMenu !== null}
          onClose={handleCloseContextMenu}
          anchorReference="anchorPosition"
          anchorPosition={
            contextMenu !== null
              ? { top: contextMenu.mouseY, left: contextMenu.mouseX }
              : undefined
          }
        >
          <MenuItem onClick={() => contextMenu && handleEditTag(contextMenu.tag)}>
            <ListItemIcon>
              <Edit fontSize="small" />
            </ListItemIcon>
            <ListItemText>Edit</ListItemText>
          </MenuItem>
          <MenuItem onClick={() => contextMenu && handleDeleteTag(contextMenu.tag)}>
            <ListItemIcon>
              <Delete fontSize="small" />
            </ListItemIcon>
            <ListItemText>Delete</ListItemText>
          </MenuItem>
        </Menu>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
};

export default TagManager;
