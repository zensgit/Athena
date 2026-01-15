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
  SelectChangeEvent,
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
import contentTypeService, { ContentTypeDefinition, ContentTypePropertyDefinition } from 'services/contentTypeService';
import { toast } from 'react-toastify';

interface PropertyField {
  key: string;
  value: string;
}

const resolveContentTypeValue = (value: unknown, fallback?: string) => {
  if (value === null || value === undefined || value === '') {
    return fallback ?? '';
  }
  return String(value);
};

const normalizeDateValue = (value: string) => {
  if (!value) return '';
  return value.includes('T') ? value.split('T')[0] : value;
};

const derivePropertyState = (nodeData: Node, typeDefinition?: ContentTypeDefinition | null) => {
  const properties = nodeData.properties || {};
  const reservedKeys = new Set(['name', 'description']);
  const contentTypeKeys = new Set((typeDefinition?.properties || []).map((prop) => prop.name));

  const customProps = Object.entries(properties)
    .filter(([key]) => !reservedKeys.has(key) && !contentTypeKeys.has(key))
    .map(([key, value]) => ({ key, value: resolveContentTypeValue(value) }));

  const typeValues: Record<string, string> = {};
  if (typeDefinition) {
    typeDefinition.properties.forEach((prop) => {
      typeValues[prop.name] = resolveContentTypeValue(
        properties[prop.name],
        prop.defaultValue ?? ''
      );
    });
  }

  return { customProps, typeValues };
};

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
  const [contentTypes, setContentTypes] = useState<ContentTypeDefinition[]>([]);
  const [contentTypeLoading, setContentTypeLoading] = useState(false);
  const [selectedContentType, setSelectedContentType] = useState('');
  const [contentTypeValues, setContentTypeValues] = useState<Record<string, string>>({});
  const correspondentLabelId = 'correspondent-select-label';
  const correspondentSelectId = 'correspondent-select';
  const contentTypeLabelId = 'content-type-select-label';
  const contentTypeSelectId = 'content-type-select';

  const loadDialogData = useCallback(async () => {
    if (!selectedNodeId) return;

    setLoading(true);
    setCorrespondentLoading(true);
    setContentTypeLoading(true);

    try {
      const [nodeResult, correspondentsResult, typesResult] = await Promise.allSettled([
        nodeService.getNode(selectedNodeId),
        correspondentService.list(0, 500),
        contentTypeService.listTypes(),
      ]);

      if (nodeResult.status !== 'fulfilled') {
        toast.error('Failed to load node details');
        return;
      }

      const nodeData = nodeResult.value;
      setNode(nodeData);
      setEditedName(nodeData.name);
      setCorrespondentId(nodeData.correspondentId || '');

      if (correspondentsResult.status === 'fulfilled') {
        setAvailableCorrespondents(correspondentsResult.value);
      } else {
        toast.error('Failed to load correspondents');
      }

      if (typesResult.status === 'fulfilled') {
        setContentTypes(typesResult.value);
      } else {
        setContentTypes([]);
        toast.error('Failed to load content types');
      }

      const metadataType = nodeData.metadata?.['ecm:contentType'];
      const initialType = typeof metadataType === 'string' ? metadataType : '';
      const typeDefinition =
        typesResult.status === 'fulfilled'
          ? typesResult.value.find((type) => type.name === initialType)
          : null;

      setSelectedContentType(initialType);
      const { customProps, typeValues } = derivePropertyState(nodeData, typeDefinition);
      setCustomProperties(customProps);
      setContentTypeValues(typeValues);
    } finally {
      setLoading(false);
      setCorrespondentLoading(false);
      setContentTypeLoading(false);
    }
  }, [selectedNodeId]);

  useEffect(() => {
    if (propertiesDialogOpen && selectedNodeId) {
      loadDialogData();
    }
  }, [propertiesDialogOpen, selectedNodeId, loadDialogData]);

  useEffect(() => {
    if (!canWrite && editMode) {
      setEditMode(false);
    }
  }, [canWrite, editMode]);

  const selectedTypeDefinition =
    contentTypes.find((type) => type.name === selectedContentType) || null;

  const handleContentTypeChange = (event: SelectChangeEvent<string>) => {
    const nextType = event.target.value;
    setSelectedContentType(nextType);

    if (!node) {
      setContentTypeValues({});
      return;
    }

    const typeDefinition = contentTypes.find((type) => type.name === nextType) || null;
    const { customProps, typeValues } = derivePropertyState(node, typeDefinition);
    setCustomProperties(customProps);
    setContentTypeValues(typeValues);
  };

  const handleContentTypeValueChange = (name: string, value: string) => {
    setContentTypeValues((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  const handleClose = () => {
    dispatch(setPropertiesDialogOpen(false));
    setNode(null);
    setEditMode(false);
    setCustomProperties([]);
    setNewPropertyKey('');
    setNewPropertyValue('');
    setAvailableCorrespondents([]);
    setCorrespondentId('');
    setContentTypes([]);
    setSelectedContentType('');
    setContentTypeValues({});
    setContentTypeLoading(false);
  };

  const handleSave = async () => {
    if (!node || !selectedNodeId) return;

    try {
      if (selectedContentType && selectedTypeDefinition) {
        const missingRequired: string[] = [];
        const payload: Record<string, any> = {};

        selectedTypeDefinition.properties.forEach((prop) => {
          const rawValue = contentTypeValues[prop.name];
          const trimmedValue = typeof rawValue === 'string' ? rawValue.trim() : rawValue;
          const normalizedValue =
            prop.type === 'date' && typeof trimmedValue === 'string'
              ? normalizeDateValue(trimmedValue)
              : trimmedValue;

          if (normalizedValue === undefined || normalizedValue === null || normalizedValue === '') {
            if (prop.required) {
              missingRequired.push(prop.title || prop.name);
            }
            return;
          }
          payload[prop.name] = normalizedValue;
        });

        if (missingRequired.length > 0) {
          toast.warn(`Missing required fields: ${missingRequired.join(', ')}`);
          return;
        }

        await contentTypeService.applyType(selectedNodeId, selectedContentType, payload);
      }

      const updatedProperties: Record<string, any> = {};

      // Update custom properties
      customProperties.forEach(({ key, value }) => {
        updatedProperties[key] = value;
      });

      // Remove deleted properties
      const contentTypeKeys = new Set((selectedTypeDefinition?.properties || []).map((prop) => prop.name));
      Object.keys(node.properties || {}).forEach((key) => {
        if (['name', 'description'].includes(key) || contentTypeKeys.has(key)) {
          return;
        }
        if (!customProperties.find((p) => p.key === key)) {
          updatedProperties[key] = null;
        }
      });

      const updates: Record<string, any> = {};
      if (Object.keys(updatedProperties).length > 0) {
        updates.properties = updatedProperties;
      }
      if (node.nodeType === 'DOCUMENT') {
        updates.correspondentId = correspondentId || null;
      }

      if (Object.keys(updates).length > 0) {
        await dispatch(updateNode({ nodeId: selectedNodeId, updates })).unwrap();
      }
      
      // Update name if changed
      if (editedName !== node.name) {
        // This would require a separate API endpoint for renaming
        // For now, we'll just update properties
      }

      toast.success('Properties updated successfully');
      setEditMode(false);
      await loadDialogData();
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

  const renderContentTypeField = (prop: ContentTypePropertyDefinition) => {
    const label = prop.title || prop.name;
    const value = contentTypeValues[prop.name] ?? '';
    const disabled = !editMode;
    const selectLabelId = `content-type-${prop.name}-label`;
    const selectId = `content-type-${prop.name}`;

    if (prop.type === 'boolean') {
      return (
        <FormControl size="small" fullWidth disabled={disabled}>
          <InputLabel id={selectLabelId}>{label}</InputLabel>
          <Select
            id={selectId}
            labelId={selectLabelId}
            value={value}
            label={label}
            displayEmpty
            onChange={(event) => handleContentTypeValueChange(prop.name, event.target.value)}
          >
            <MenuItem value="">
              <em>Unset</em>
            </MenuItem>
            <MenuItem value="true">True</MenuItem>
            <MenuItem value="false">False</MenuItem>
          </Select>
        </FormControl>
      );
    }

    if (prop.type === 'list' && prop.options && prop.options.length > 0) {
      return (
        <FormControl size="small" fullWidth disabled={disabled}>
          <InputLabel id={selectLabelId}>{label}</InputLabel>
          <Select
            id={selectId}
            labelId={selectLabelId}
            value={value}
            label={label}
            displayEmpty
            onChange={(event) => handleContentTypeValueChange(prop.name, event.target.value)}
          >
            <MenuItem value="">
              <em>None</em>
            </MenuItem>
            {prop.options.map((option) => (
              <MenuItem key={option} value={option}>
                {option}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
      );
    }

    const resolvedValue = prop.type === 'date' ? normalizeDateValue(value) : value;
    const inputType =
      prop.type === 'number' ? 'number' : prop.type === 'date' ? 'date' : 'text';

    return (
      <TextField
        label={label}
        value={resolvedValue}
        onChange={(event) => handleContentTypeValueChange(prop.name, event.target.value)}
        size="small"
        fullWidth
        disabled={disabled}
        type={inputType}
        InputLabelProps={prop.type === 'date' ? { shrink: true } : undefined}
        required={prop.required}
      />
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

            <Box mb={3}>
              <Typography variant="h6" gutterBottom>
                Content Type
              </Typography>
              {contentTypeLoading ? (
                <Box display="flex" justifyContent="center" p={2}>
                  <CircularProgress size={20} />
                </Box>
              ) : contentTypes.length === 0 ? (
                <Typography variant="body2" color="text.secondary">
                  No content types defined.
                </Typography>
              ) : (
                <Box display="flex" flexDirection="column" gap={2}>
                  <FormControl size="small" fullWidth disabled={!editMode || contentTypeLoading}>
                    <InputLabel id={contentTypeLabelId}>Type</InputLabel>
                    <Select
                      id={contentTypeSelectId}
                      labelId={contentTypeLabelId}
                      value={selectedContentType}
                      label="Type"
                      displayEmpty
                      onChange={handleContentTypeChange}
                      renderValue={(selected) => {
                        const selectedValue = selected as string;
                        if (!selectedValue) {
                          return <em>None</em>;
                        }
                        const match = contentTypes.find((type) => type.name === selectedValue);
                        return match ? `${match.displayName} (${match.name})` : selectedValue;
                      }}
                    >
                      <MenuItem value="">
                        <em>None</em>
                      </MenuItem>
                      {contentTypes.map((type) => (
                        <MenuItem key={type.name} value={type.name}>
                          {type.displayName} ({type.name})
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>

                  {selectedTypeDefinition ? (
                    selectedTypeDefinition.properties.length > 0 ? (
                      <Grid container spacing={2}>
                        {selectedTypeDefinition.properties.map((prop) => (
                          <Grid item xs={12} sm={6} md={4} key={prop.name}>
                            {renderContentTypeField(prop)}
                          </Grid>
                        ))}
                      </Grid>
                    ) : (
                      <Typography variant="body2" color="text.secondary">
                        This content type has no custom fields.
                      </Typography>
                    )
                  ) : (
                    <Typography variant="body2" color="text.secondary">
                      Select a content type to edit its fields.
                    </Typography>
                  )}
                </Box>
              )}
            </Box>

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
