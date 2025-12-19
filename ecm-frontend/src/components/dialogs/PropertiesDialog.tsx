import React, { useCallback, useEffect, useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  IconButton,
  TextField,
  Box,
  Typography,
  Chip,
  Grid,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Divider,
  CircularProgress,
  List,
  ListItem,
  ListItemText,
  Paper,
} from '@mui/material';
import {
  Close,
  Edit,
  Save,
  Cancel,
  Add,
  Delete,
} from '@mui/icons-material';
import { format } from 'date-fns';
import { Node } from 'types';
import { useAppDispatch, useAppSelector } from 'store';
import { setPropertiesDialogOpen } from 'store/slices/uiSlice';
import { updateNode } from 'store/slices/nodeSlice';
import nodeService from 'services/nodeService';
import correspondentService, { Correspondent } from 'services/correspondentService';
import { toast } from 'react-toastify';

interface PropertyField {
  key: string;
  value: string;
}

const PropertiesDialog: React.FC = () => {
  const dispatch = useAppDispatch();
  const { propertiesDialogOpen, selectedNodeId } = useAppSelector((state) => state.ui);
  const user = useAppSelector((state) => state.auth.user);
  const canWrite = Boolean(user?.roles?.includes('ROLE_ADMIN') || user?.roles?.includes('ROLE_EDITOR'));
  const [node, setNode] = useState<Node | null>(null);
  const [loading, setLoading] = useState(false);
  const [editMode, setEditMode] = useState(false);
  const [editedName, setEditedName] = useState('');
  const [customProperties, setCustomProperties] = useState<PropertyField[]>([]);
  const [newPropertyKey, setNewPropertyKey] = useState('');
  const [newPropertyValue, setNewPropertyValue] = useState('');
  const [availableCorrespondents, setAvailableCorrespondents] = useState<Correspondent[]>([]);
  const [correspondentId, setCorrespondentId] = useState('');
  const [correspondentLoading, setCorrespondentLoading] = useState(false);
  const correspondentLabelId = 'correspondent-select-label';
  const correspondentSelectId = 'correspondent-select';

  const loadNodeDetails = useCallback(async () => {
    if (!selectedNodeId) return;

    setLoading(true);
    try {
      const nodeData = await nodeService.getNode(selectedNodeId);
      setNode(nodeData);
      setEditedName(nodeData.name);
      setCorrespondentId(nodeData.correspondentId || '');
      
      // Extract custom properties
      const customProps = Object.entries(nodeData.properties || {})
        .filter(([key]) => !['name', 'description'].includes(key))
        .map(([key, value]) => ({ key, value: String(value) }));
      setCustomProperties(customProps);
    } catch (error) {
      toast.error('Failed to load node details');
    } finally {
      setLoading(false);
    }
  }, [selectedNodeId]);

  const loadCorrespondents = useCallback(async () => {
    setCorrespondentLoading(true);
    try {
      const correspondents = await correspondentService.list(0, 500);
      setAvailableCorrespondents(correspondents);
    } catch {
      toast.error('Failed to load correspondents');
    } finally {
      setCorrespondentLoading(false);
    }
  }, []);

  useEffect(() => {
    if (propertiesDialogOpen && selectedNodeId) {
      loadNodeDetails();
      loadCorrespondents();
    }
  }, [propertiesDialogOpen, selectedNodeId, loadNodeDetails, loadCorrespondents]);

  useEffect(() => {
    if (!canWrite && editMode) {
      setEditMode(false);
    }
  }, [canWrite, editMode]);

  const handleClose = () => {
    dispatch(setPropertiesDialogOpen(false));
    setNode(null);
    setEditMode(false);
    setCustomProperties([]);
    setNewPropertyKey('');
    setNewPropertyValue('');
    setAvailableCorrespondents([]);
    setCorrespondentId('');
  };

  const handleSave = async () => {
    if (!node || !selectedNodeId) return;

    try {
      const updatedProperties: Record<string, any> = {
        ...node.properties,
      };

      // Update custom properties
      customProperties.forEach(({ key, value }) => {
        updatedProperties[key] = value;
      });

      // Remove deleted properties
      Object.keys(node.properties || {}).forEach((key) => {
        if (!customProperties.find((p) => p.key === key) && !['name', 'description'].includes(key)) {
          updatedProperties[key] = null;
        }
      });

      const updates: Record<string, any> = { properties: updatedProperties };
      if (node.nodeType === 'DOCUMENT') {
        updates.correspondentId = correspondentId || null;
      }

      await dispatch(updateNode({ nodeId: selectedNodeId, updates })).unwrap();
      
      // Update name if changed
      if (editedName !== node.name) {
        // This would require a separate API endpoint for renaming
        // For now, we'll just update properties
      }

      toast.success('Properties updated successfully');
      setEditMode(false);
      await loadNodeDetails();
    } catch (error) {
      toast.error('Failed to update properties');
    }
  };

  const handleAddProperty = () => {
    if (newPropertyKey && newPropertyValue) {
      if (customProperties.find((p) => p.key === newPropertyKey)) {
        toast.warning('Property already exists');
        return;
      }

      setCustomProperties([...customProperties, { key: newPropertyKey, value: newPropertyValue }]);
      setNewPropertyKey('');
      setNewPropertyValue('');
    }
  };

  const handleRemoveProperty = (index: number) => {
    setCustomProperties(customProperties.filter((_, i) => i !== index));
  };

  const handlePropertyChange = (index: number, value: string) => {
    setCustomProperties(
      customProperties.map((prop, i) =>
        i === index ? { ...prop, value } : prop
      )
    );
  };

  const formatFileSize = (bytes?: number): string => {
    if (!bytes) return '-';
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return `${(bytes / Math.pow(1024, i)).toFixed(2)} ${sizes[i]}`;
  };

  if (!node) {
    return null;
  }

  return (
    <Dialog
      open={propertiesDialogOpen}
      onClose={handleClose}
      maxWidth="md"
      fullWidth
    >
      <DialogTitle>
        Properties
        <IconButton
          aria-label="close"
          onClick={handleClose}
          sx={{ position: 'absolute', right: 8, top: 8 }}
        >
          <Close />
        </IconButton>
      </DialogTitle>
      <DialogContent>
        {loading ? (
          <Box display="flex" justifyContent="center" p={4}>
            <CircularProgress />
          </Box>
        ) : (
          <>
            <Box mb={3}>
              <Typography variant="h6" gutterBottom>
                General Information
              </Typography>
              <Grid container spacing={2}>
                <Grid item xs={12}>
                  <TextField
                    label="Name"
                    value={editedName}
                    onChange={(e) => setEditedName(e.target.value)}
                    fullWidth
                    disabled={!editMode}
                    size="small"
                  />
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="body2" color="text.secondary">
                    Type
                  </Typography>
                  <Typography variant="body1">
                    {node.nodeType === 'FOLDER' ? 'Folder' : 'Document'}
                  </Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="body2" color="text.secondary">
                    Size
                  </Typography>
                  <Typography variant="body1">
                    {formatFileSize(node.size)}
                  </Typography>
                </Grid>
                {node.contentType && (
                  <Grid item xs={6}>
                    <Typography variant="body2" color="text.secondary">
                      Content Type
                    </Typography>
                    <Typography variant="body1">{node.contentType}</Typography>
                  </Grid>
                )}
                {node.currentVersionLabel && (
                  <Grid item xs={6}>
                    <Typography variant="body2" color="text.secondary">
                      Version
                    </Typography>
                    <Typography variant="body1">{node.currentVersionLabel}</Typography>
                  </Grid>
                )}
                {node.nodeType === 'DOCUMENT' && (
                  <Grid item xs={12}>
                    <FormControl fullWidth size="small" disabled={!editMode || correspondentLoading}>
                      <InputLabel id={correspondentLabelId}>Correspondent</InputLabel>
                      <Select
                        id={correspondentSelectId}
                        labelId={correspondentLabelId}
                        value={correspondentId}
                        label="Correspondent"
                        onChange={(e) => setCorrespondentId(e.target.value as string)}
                        renderValue={(selected) => {
                          if (!selected) {
                            return <em>None</em>;
                          }
                          const match = availableCorrespondents.find((c) => c.id === selected);
                          return match?.name || node.correspondent || selected;
                        }}
                      >
                        <MenuItem value="">
                          <em>None</em>
                        </MenuItem>
                        {availableCorrespondents.map((c) => (
                          <MenuItem key={c.id} value={c.id}>
                            {c.name}
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                  </Grid>
                )}
              </Grid>
            </Box>

            <Divider sx={{ my: 2 }} />

            <Box mb={3}>
              <Typography variant="h6" gutterBottom>
                System Properties
              </Typography>
              <Grid container spacing={2}>
                <Grid item xs={6}>
                  <Typography variant="body2" color="text.secondary">
                    Created
                  </Typography>
                  <Typography variant="body1">
                    {format(new Date(node.created), 'PPp')}
                  </Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="body2" color="text.secondary">
                    Created By
                  </Typography>
                  <Typography variant="body1">{node.creator}</Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="body2" color="text.secondary">
                    Modified
                  </Typography>
                  <Typography variant="body1">
                    {format(new Date(node.modified), 'PPp')}
                  </Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="body2" color="text.secondary">
                    Modified By
                  </Typography>
                  <Typography variant="body1">{node.modifier}</Typography>
                </Grid>
                <Grid item xs={12}>
                  <Typography variant="body2" color="text.secondary">
                    Path
                  </Typography>
                  <Typography variant="body1" sx={{ wordBreak: 'break-all' }}>
                    {node.path}
                  </Typography>
                </Grid>
              </Grid>
            </Box>

            {node.aspects && node.aspects.length > 0 && (
              <>
                <Divider sx={{ my: 2 }} />
                <Box mb={3}>
                  <Typography variant="h6" gutterBottom>
                    Aspects
                  </Typography>
                  <Box display="flex" gap={1} flexWrap="wrap">
                    {node.aspects.map((aspect) => (
                      <Chip key={aspect} label={aspect} size="small" />
                    ))}
                  </Box>
                </Box>
              </>
            )}

            <Divider sx={{ my: 2 }} />

            <Box>
              <Typography variant="h6" gutterBottom>
                Custom Properties
              </Typography>
              {editMode && (
                <Box display="flex" gap={1} mb={2}>
                  <TextField
                    label="Key"
                    value={newPropertyKey}
                    onChange={(e) => setNewPropertyKey(e.target.value)}
                    size="small"
                  />
                  <TextField
                    label="Value"
                    value={newPropertyValue}
                    onChange={(e) => setNewPropertyValue(e.target.value)}
                    size="small"
                    sx={{ flex: 1 }}
                  />
                  <IconButton onClick={handleAddProperty} color="primary">
                    <Add />
                  </IconButton>
                </Box>
              )}
              
              {customProperties.length > 0 ? (
                <Paper variant="outlined">
                  <List dense>
                    {customProperties.map((prop, index) => (
                      <ListItem
                        key={prop.key}
                        secondaryAction={
                          editMode && (
                            <IconButton
                              edge="end"
                              onClick={() => handleRemoveProperty(index)}
                            >
                              <Delete fontSize="small" />
                            </IconButton>
                          )
                        }
                      >
                        <ListItemText
                          primary={prop.key}
                          secondary={
                            editMode ? (
                              <TextField
                                value={prop.value}
                                onChange={(e) => handlePropertyChange(index, e.target.value)}
                                size="small"
                                fullWidth
                                variant="standard"
                              />
                            ) : (
                              prop.value
                            )
                          }
                        />
                      </ListItem>
                    ))}
                  </List>
                </Paper>
              ) : (
                <Typography variant="body2" color="text.secondary">
                  No custom properties
                </Typography>
              )}
            </Box>
          </>
        )}
      </DialogContent>
      <DialogActions>
        {editMode ? (
          <>
            <Button onClick={() => setEditMode(false)} startIcon={<Cancel />}>
              Cancel
            </Button>
            <Button onClick={handleSave} variant="contained" startIcon={<Save />}>
              Save
            </Button>
          </>
        ) : (
          <>
            <Button onClick={handleClose}>Close</Button>
            {canWrite && (
              <Button
                onClick={() => setEditMode(true)}
                variant="contained"
                startIcon={<Edit />}
              >
                Edit
              </Button>
            )}
          </>
        )}
      </DialogActions>
    </Dialog>
  );
};

export default PropertiesDialog;
