import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
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
import dictionaryService from 'services/dictionaryService';
import { AspectDefinition, PropertyDefinition } from 'services/contentModelService';
import { toast } from 'react-toastify';
import { formatConstraintLabel } from 'utils/contentModelConstraintUtils';
import { formatApiErrorMessage } from 'utils/apiErrorUtils';
import NodeRatingPanel from 'components/ratings/NodeRatingPanel';
import {
  buildAspectInitialPropertyValues,
  buildAspectPropertyPayload,
  getAspectPropertyListOptions,
} from 'utils/aspectPropertyFormUtils';
import {
  ENCRYPTED_PROPERTY_DISPLAY_VALUE,
  containsProtectedPropertyPayload,
  formatPropertyDisplayValue,
} from 'utils/propertyRedactionUtils';

export interface PropertyField {
  key: string;
  value: string;
  redacted?: boolean;
}

const resolveContentTypeValue = (value: unknown, fallback?: string) => {
  return formatPropertyDisplayValue(value, fallback ?? '');
};

const normalizeDateValue = (value: string) => {
  if (!value) return '';
  return value.includes('T') ? value.split('T')[0] : value;
};

export const derivePropertyState = (nodeData: Node, typeDefinition?: ContentTypeDefinition | null) => {
  const properties = nodeData.properties || {};
  const reservedKeys = new Set(['name', 'description']);
  const contentTypeKeys = new Set((typeDefinition?.properties || []).map((prop) => prop.name));

  const customProps = Object.entries(properties)
    .filter(([key]) => !reservedKeys.has(key) && !contentTypeKeys.has(key))
    .map(([key, value]) => ({
      key,
      value: resolveContentTypeValue(value),
      redacted: containsProtectedPropertyPayload(value) || value === ENCRYPTED_PROPERTY_DISPLAY_VALUE,
    }));

  const typeValues: Record<string, string> = {};
  if (typeDefinition) {
    typeDefinition.properties.forEach((prop) => {
      const value = properties[prop.name];
      typeValues[prop.name] = resolveContentTypeValue(
        value,
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
  const [availableAspects, setAvailableAspects] = useState<AspectDefinition[]>([]);
  const [aspectLoading, setAspectLoading] = useState(false);
  const [selectedAspectToAdd, setSelectedAspectToAdd] = useState('');
  const [aspectPropertyValues, setAspectPropertyValues] = useState<Record<string, string>>({});
  const [aspectMutating, setAspectMutating] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const correspondentLabelId = 'correspondent-select-label';
  const correspondentSelectId = 'correspondent-select';
  const contentTypeLabelId = 'content-type-select-label';
  const contentTypeSelectId = 'content-type-select';
  const aspectSelectLabelId = 'aspect-select-label';
  const aspectSelectId = 'aspect-select';

  const loadDialogData = useCallback(async () => {
    if (!selectedNodeId) return;

    setLoading(true);
    setCorrespondentLoading(true);
    setContentTypeLoading(true);
    setAspectLoading(true);

    try {
      const [nodeResult, correspondentsResult, typesResult, aspectsResult] = await Promise.allSettled([
        nodeService.getNode(selectedNodeId),
        correspondentService.list(0, 500),
        contentTypeService.listTypes(),
        dictionaryService.listAspects(),
      ]);

      if (nodeResult.status !== 'fulfilled') {
        toast.error('Failed to load node details');
        return;
      }

      const nodeData = nodeResult.value;
      setActionError(null);
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

      if (aspectsResult.status === 'fulfilled') {
        setAvailableAspects(aspectsResult.value);
      } else {
        setAvailableAspects([]);
        toast.error('Failed to load dictionary aspects');
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
      setAspectLoading(false);
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
    setAvailableAspects([]);
    setAspectLoading(false);
    setSelectedAspectToAdd('');
    setAspectPropertyValues({});
    setAspectMutating(false);
    setActionError(null);
  };

  const handleSave = async () => {
    if (!node || !selectedNodeId) return;

    try {
      setActionError(null);
      if (selectedContentType && selectedTypeDefinition) {
        const missingRequired: string[] = [];
        const payload: Record<string, any> = {};

        selectedTypeDefinition.properties.forEach((prop) => {
          const rawValue = contentTypeValues[prop.name];
          if (rawValue === ENCRYPTED_PROPERTY_DISPLAY_VALUE) {
            return;
          }
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
      customProperties.forEach(({ key, value, redacted }) => {
        if (redacted) {
          return;
        }
        updatedProperties[key] = value;
      });

      // Remove deleted properties
      const contentTypeKeys = new Set((selectedTypeDefinition?.properties || []).map((prop) => prop.name));
      Object.keys(node.properties || {}).forEach((key) => {
        if (['name', 'description'].includes(key) || contentTypeKeys.has(key)) {
          return;
        }
        if (containsProtectedPropertyPayload(node.properties[key]) || node.properties[key] === ENCRYPTED_PROPERTY_DISPLAY_VALUE) {
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
      setActionError(formatApiErrorMessage(error, 'Failed to update properties'));
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

  const aspectChoices = availableAspects.filter((aspect) => !node?.aspects?.includes(aspect.qualifiedName));
  const selectedAspectDefinition = useMemo(
    () => aspectChoices.find((aspect) => aspect.qualifiedName === selectedAspectToAdd) || null,
    [aspectChoices, selectedAspectToAdd]
  );

  useEffect(() => {
    setAspectPropertyValues(buildAspectInitialPropertyValues(selectedAspectDefinition));
    setActionError(null);
  }, [selectedAspectDefinition]);

  const resolveAspectLabel = (aspectName: string) => {
    const match = availableAspects.find((aspect) => aspect.qualifiedName === aspectName);
    if (!match) {
      return aspectName;
    }
    return match.title ? `${match.title} (${match.qualifiedName})` : match.qualifiedName;
  };

  const handleAddAspect = async () => {
    if (!selectedNodeId || !selectedAspectToAdd) {
      return;
    }
    setAspectMutating(true);
    try {
      setActionError(null);
      const aspectProperties = buildAspectPropertyPayload(selectedAspectDefinition, aspectPropertyValues);
      await nodeService.addAspect(
        selectedNodeId,
        selectedAspectToAdd,
        Object.keys(aspectProperties).length > 0 ? aspectProperties : undefined
      );
      toast.success(`Added ${selectedAspectToAdd}`);
      setSelectedAspectToAdd('');
      setAspectPropertyValues({});
      await loadDialogData();
    } catch (error) {
      setActionError(formatApiErrorMessage(error, 'Failed to add aspect'));
    } finally {
      setAspectMutating(false);
    }
  };

  const handleRemoveAspect = async (aspectName: string) => {
    if (!selectedNodeId) {
      return;
    }
    setAspectMutating(true);
    try {
      setActionError(null);
      await nodeService.removeAspect(selectedNodeId, aspectName);
      toast.success(`Removed ${aspectName}`);
      await loadDialogData();
    } catch (error) {
      setActionError(formatApiErrorMessage(error, 'Failed to remove aspect'));
    } finally {
      setAspectMutating(false);
    }
  };

  const handleAspectPropertyChange = (propertyName: string, value: string) => {
    setAspectPropertyValues((prev) => ({
      ...prev,
      [propertyName]: value,
    }));
  };

  const renderAspectPropertyField = (property: PropertyDefinition) => {
    const propertyName = property.qualifiedName || property.name;
    const value = aspectPropertyValues[propertyName] ?? '';
    const disabled = aspectLoading || aspectMutating;
    const label = property.title
      ? `${property.title} (${propertyName})`
      : propertyName;
    const options = getAspectPropertyListOptions(property);
    const helperParts = [
      property.defaultValue ? `Default: ${property.defaultValue}` : null,
      property.multiValued ? 'Use commas or new lines for multiple values' : null,
      property.constraints.length
        ? property.constraints.map((constraint) => formatConstraintLabel(constraint)).join(' | ')
        : null,
    ].filter(Boolean);
    const helperText = helperParts.length > 0 ? helperParts.join(' · ') : undefined;
    const isNumber = ['INT', 'LONG', 'FLOAT', 'DOUBLE'].includes(property.dataType);

    if (property.dataType === 'BOOLEAN') {
      return (
        <FormControl size="small" fullWidth disabled={disabled}>
          <InputLabel id={`aspect-property-${propertyName}-label`}>{label}</InputLabel>
          <Select
            labelId={`aspect-property-${propertyName}-label`}
            value={value}
            label={label}
            displayEmpty
            onChange={(event) => handleAspectPropertyChange(propertyName, event.target.value)}
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

    if (options.length > 0 && !property.multiValued) {
      return (
        <FormControl size="small" fullWidth disabled={disabled}>
          <InputLabel id={`aspect-property-${propertyName}-label`}>{label}</InputLabel>
          <Select
            labelId={`aspect-property-${propertyName}-label`}
            value={value}
            label={label}
            displayEmpty
            onChange={(event) => handleAspectPropertyChange(propertyName, event.target.value)}
          >
            <MenuItem value="">
              <em>Unset</em>
            </MenuItem>
            {options.map((option) => (
              <MenuItem key={option} value={option}>
                {option}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
      );
    }

    const inputType = property.dataType === 'DATE'
      ? 'date'
      : property.dataType === 'DATETIME'
        ? 'datetime-local'
        : isNumber
          ? 'number'
          : property.dataType === 'URI'
            ? 'url'
            : 'text';

    return (
      <TextField
        label={label}
        value={value}
        onChange={(event) => handleAspectPropertyChange(propertyName, event.target.value)}
        size="small"
        fullWidth
        disabled={disabled}
        type={inputType}
        multiline={property.multiValued}
        minRows={property.multiValued ? 3 : undefined}
        InputLabelProps={
          property.dataType === 'DATE' || property.dataType === 'DATETIME'
            ? { shrink: true }
            : undefined
        }
        required={property.mandatory}
        helperText={helperText}
      />
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

    if ((prop.type === 'list' || prop.type === 'select') && prop.options && prop.options.length > 0) {
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
    const isNumber =
      prop.type === 'number' || prop.type === 'integer' || prop.type === 'float' || prop.type === 'monetary';
    const inputType = prop.type === 'date'
      ? 'date'
      : prop.type === 'url'
        ? 'url'
        : isNumber
          ? 'number'
          : 'text';
    const step = prop.type === 'integer' ? 1 : prop.type === 'monetary' ? 0.01 : isNumber ? 'any' : undefined;
    const multiline = prop.type === 'long_text';
    const placeholder = prop.type === 'documentlink' ? 'Document ID or path' : undefined;

    return (
      <TextField
        label={label}
        value={resolvedValue}
        onChange={(event) => handleContentTypeValueChange(prop.name, event.target.value)}
        size="small"
        fullWidth
        disabled={disabled}
        type={inputType}
        inputProps={step ? { step } : undefined}
        multiline={multiline}
        minRows={multiline ? 3 : undefined}
        placeholder={placeholder}
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
            {actionError && (
              <Alert severity="error" sx={{ mb: 2, whiteSpace: 'pre-line' }}>
                {actionError}
              </Alert>
            )}
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

            {((node.aspects && node.aspects.length > 0) || (editMode && canWrite)) && (
              <>
                <Divider sx={{ my: 2 }} />
                <Box mb={3}>
                  <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
                    <Typography variant="h6">Aspects</Typography>
                    {aspectLoading && <CircularProgress size={18} />}
                  </Box>
                  <Box display="flex" gap={1} flexWrap="wrap">
                    {(node.aspects || []).map((aspect) => (
                      <Chip
                        key={aspect}
                        label={resolveAspectLabel(aspect)}
                        size="small"
                        onDelete={editMode && canWrite ? () => void handleRemoveAspect(aspect) : undefined}
                      />
                    ))}
                  </Box>
                  {editMode && canWrite && (
                    <Box mt={2}>
                      <Box display="flex" gap={1} alignItems="center" flexWrap="wrap">
                        <FormControl size="small" sx={{ minWidth: 260 }} disabled={aspectLoading || aspectMutating}>
                          <InputLabel id={aspectSelectLabelId}>Add Aspect</InputLabel>
                          <Select
                            id={aspectSelectId}
                            labelId={aspectSelectLabelId}
                            value={selectedAspectToAdd}
                            label="Add Aspect"
                            onChange={(event) => setSelectedAspectToAdd(event.target.value)}
                          >
                            <MenuItem value="">
                              <em>Select an aspect</em>
                            </MenuItem>
                            {aspectChoices.map((aspect) => (
                              <MenuItem key={aspect.id} value={aspect.qualifiedName}>
                                {resolveAspectLabel(aspect.qualifiedName)}
                              </MenuItem>
                            ))}
                          </Select>
                        </FormControl>
                        <Button
                          variant="outlined"
                          onClick={() => void handleAddAspect()}
                          disabled={!selectedAspectToAdd || aspectMutating}
                        >
                          Add
                        </Button>
                      </Box>
                      {selectedAspectDefinition && selectedAspectDefinition.properties.length > 0 && (
                        <Box mt={2}>
                          <Typography variant="subtitle2" gutterBottom>
                            Initial Aspect Properties
                          </Typography>
                          <Grid container spacing={2}>
                            {selectedAspectDefinition.properties.map((property) => (
                              <Grid item xs={12} sm={6} key={property.id}>
                                {renderAspectPropertyField(property)}
                              </Grid>
                            ))}
                          </Grid>
                        </Box>
                      )}
                    </Box>
                  )}
                </Box>
              </>
            )}

            <Divider sx={{ my: 2 }} />

            {selectedNodeId && (
              <Box mb={3}>
                <NodeRatingPanel nodeId={selectedNodeId} />
              </Box>
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
                              disabled={prop.redacted}
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
                                disabled={prop.redacted}
                                helperText={prop.redacted ? 'Encrypted payload redacted; update through the owning encrypted property workflow.' : undefined}
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
