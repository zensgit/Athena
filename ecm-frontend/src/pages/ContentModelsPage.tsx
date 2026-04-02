import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  MenuItem,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import { Add, DeleteOutline, Edit, Refresh } from '@mui/icons-material';
import { toast } from 'react-toastify';
import contentModelService, {
  AspectDefinition,
  ContentModelDefinition,
  ConstraintDefinition,
  ConstraintType,
  CreateContentModelRequest,
  PropertyDataType,
  PropertyDefinition,
  TypeDefinition,
} from 'services/contentModelService';
import dictionaryService from 'services/dictionaryService';
import {
  buildConstraintParameters,
  ConstraintFormState,
  formatConstraintLabel,
  getConstraintValidationMessage,
} from 'utils/contentModelConstraintUtils';

const emptyCreateForm = (): CreateContentModelRequest => ({
  namespaceUri: '',
  prefix: '',
  name: '',
  description: '',
  author: '',
  versionLabel: '1.0',
});

const emptyConstraintForm = (): ConstraintFormState => ({
  pattern: '',
  values: '',
  min: '',
  max: '',
});

const ContentModelsPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [models, setModels] = useState<ContentModelDefinition[]>([]);
  const [dictionaryTypes, setDictionaryTypes] = useState<TypeDefinition[]>([]);
  const [dictionaryAspects, setDictionaryAspects] = useState<AspectDefinition[]>([]);
  const [selectedModelId, setSelectedModelId] = useState<string>('');
  const [selectedTypeName, setSelectedTypeName] = useState<string>('');
  const [selectedAspectName, setSelectedAspectName] = useState<string>('');
  const [selectedType, setSelectedType] = useState<TypeDefinition | null>(null);
  const [selectedTypeHierarchy, setSelectedTypeHierarchy] = useState<string[]>([]);
  const [selectedMandatoryAspects, setSelectedMandatoryAspects] = useState<string[]>([]);
  const [selectedAspect, setSelectedAspect] = useState<AspectDefinition | null>(null);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [createForm, setCreateForm] = useState<CreateContentModelRequest>(emptyCreateForm);
  const [saving, setSaving] = useState(false);

  // authoring dialogs
  const [addTypeOpen, setAddTypeOpen] = useState(false);
  const [addTypeForm, setAddTypeForm] = useState({ name: '', title: '', description: '', parentName: '' });
  const [addAspectOpen, setAddAspectOpen] = useState(false);
  const [addAspectForm, setAddAspectForm] = useState({ name: '', title: '', description: '', parentName: '' });
  const [addPropertyOpen, setAddPropertyOpen] = useState(false);
  const [addPropertyTarget, setAddPropertyTarget] = useState<{ id: string; kind: 'type' | 'aspect'; label: string } | null>(null);
  const [addPropertyForm, setAddPropertyForm] = useState({ name: '', title: '', dataType: 'TEXT' as PropertyDataType, mandatory: false, multiValued: false, defaultValue: '' });
  const [editTypeOpen, setEditTypeOpen] = useState(false);
  const [editTypeForm, setEditTypeForm] = useState({ id: '', qualifiedName: '', title: '', description: '', parentName: '' });
  const [editAspectOpen, setEditAspectOpen] = useState(false);
  const [editAspectForm, setEditAspectForm] = useState({ id: '', qualifiedName: '', title: '', description: '', parentName: '' });
  const [addConstraintOpen, setAddConstraintOpen] = useState(false);
  const [addConstraintTarget, setAddConstraintTarget] = useState<PropertyDefinition | null>(null);
  const [addConstraintForm, setAddConstraintForm] = useState({ constraintType: 'REGEX' as ConstraintType, values: emptyConstraintForm() });

  const loadPage = async () => {
    setLoading(true);
    try {
      const [modelsData, typesData, aspectsData] = await Promise.all([
        contentModelService.listModels(),
        dictionaryService.listTypes(),
        dictionaryService.listAspects(),
      ]);
      setModels(modelsData);
      setDictionaryTypes(typesData);
      setDictionaryAspects(aspectsData);
      setSelectedModelId((current) => current || modelsData[0]?.id || '');
      setSelectedTypeName((current) => current || typesData[0]?.qualifiedName || '');
      setSelectedAspectName((current) => current || aspectsData[0]?.qualifiedName || '');
    } catch {
      toast.error('Failed to load content model registry');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadPage();
  }, []);

  useEffect(() => {
    if (!selectedTypeName) {
      setSelectedType(null);
      setSelectedTypeHierarchy([]);
      setSelectedMandatoryAspects([]);
      return;
    }

    let active = true;
    const loadType = async () => {
      try {
        const [type, hierarchy, mandatoryAspects] = await Promise.all([
          dictionaryService.getType(selectedTypeName),
          dictionaryService.getTypeHierarchy(selectedTypeName),
          dictionaryService.getMandatoryAspects(selectedTypeName),
        ]);
        if (!active) {
          return;
        }
        setSelectedType(type);
        setSelectedTypeHierarchy(hierarchy);
        setSelectedMandatoryAspects(mandatoryAspects);
      } catch {
        if (active) {
          toast.error('Failed to load type dictionary details');
        }
      }
    };

    void loadType();
    return () => {
      active = false;
    };
  }, [selectedTypeName]);

  useEffect(() => {
    if (!selectedAspectName) {
      setSelectedAspect(null);
      return;
    }

    let active = true;
    const loadAspect = async () => {
      try {
        const aspect = await dictionaryService.getAspect(selectedAspectName);
        if (active) {
          setSelectedAspect(aspect);
        }
      } catch {
        if (active) {
          toast.error('Failed to load aspect dictionary details');
        }
      }
    };

    void loadAspect();
    return () => {
      active = false;
    };
  }, [selectedAspectName]);

  const selectedModel = useMemo(
    () => models.find((model) => model.id === selectedModelId) || null,
    [models, selectedModelId]
  );
  const selectedModelType = useMemo(
    () => selectedModel?.types.find((type) => type.qualifiedName === selectedType?.qualifiedName) || null,
    [selectedModel, selectedType]
  );
  const selectedModelAspect = useMemo(
    () => selectedModel?.aspects.find((aspect) => aspect.qualifiedName === selectedAspect?.qualifiedName) || null,
    [selectedModel, selectedAspect]
  );

  const handleActivateToggle = async (model: ContentModelDefinition) => {
    setSaving(true);
    try {
      if (model.status === 'ACTIVE') {
        await contentModelService.deactivateModel(model.id);
        toast.success(`Deactivated ${model.prefix}:${model.name}`);
      } else {
        await contentModelService.activateModel(model.id);
        toast.success(`Activated ${model.prefix}:${model.name}`);
      }
      await loadPage();
    } catch {
      toast.error('Failed to update model status');
    } finally {
      setSaving(false);
    }
  };

  const handleCreateModel = async () => {
    if (!createForm.prefix.trim() || !createForm.namespaceUri.trim() || !createForm.name.trim()) {
      toast.warn('Prefix, namespace URI, and name are required');
      return;
    }

    setSaving(true);
    try {
      const created = await contentModelService.createModel({
        ...createForm,
        prefix: createForm.prefix.trim(),
        namespaceUri: createForm.namespaceUri.trim(),
        name: createForm.name.trim(),
        description: createForm.description?.trim() || undefined,
        author: createForm.author?.trim() || undefined,
        versionLabel: createForm.versionLabel?.trim() || undefined,
      });
      setCreateDialogOpen(false);
      setCreateForm(emptyCreateForm());
      toast.success(`Created ${created.prefix}:${created.name}`);
      await loadPage();
      setSelectedModelId(created.id);
    } catch {
      toast.error('Failed to create content model');
    } finally {
      setSaving(false);
    }
  };

  const handleAddType = async () => {
    if (!selectedModelId || !addTypeForm.name.trim()) { toast.warn('Type name is required'); return; }
    setSaving(true);
    try {
      await contentModelService.addType(selectedModelId, {
        name: addTypeForm.name.trim(),
        title: addTypeForm.title.trim() || undefined,
        description: addTypeForm.description.trim() || undefined,
        parentName: addTypeForm.parentName.trim() || undefined,
      });
      setAddTypeOpen(false);
      setAddTypeForm({ name: '', title: '', description: '', parentName: '' });
      toast.success('Type added');
      await loadPage();
    } catch { toast.error('Failed to add type'); } finally { setSaving(false); }
  };

  const handleAddAspect = async () => {
    if (!selectedModelId || !addAspectForm.name.trim()) { toast.warn('Aspect name is required'); return; }
    setSaving(true);
    try {
      await contentModelService.addAspect(selectedModelId, {
        name: addAspectForm.name.trim(),
        title: addAspectForm.title.trim() || undefined,
        description: addAspectForm.description.trim() || undefined,
        parentName: addAspectForm.parentName.trim() || undefined,
      });
      setAddAspectOpen(false);
      setAddAspectForm({ name: '', title: '', description: '', parentName: '' });
      toast.success('Aspect added');
      await loadPage();
    } catch { toast.error('Failed to add aspect'); } finally { setSaving(false); }
  };

  const handleAddProperty = async () => {
    if (!addPropertyTarget || !addPropertyForm.name.trim()) { toast.warn('Property name is required'); return; }
    setSaving(true);
    try {
      const payload = {
        name: addPropertyForm.name.trim(),
        title: addPropertyForm.title.trim() || undefined,
        dataType: addPropertyForm.dataType,
        mandatory: addPropertyForm.mandatory,
        multiValued: addPropertyForm.multiValued,
        defaultValue: addPropertyForm.defaultValue.trim() || undefined,
      };
      if (addPropertyTarget.kind === 'type') {
        await contentModelService.addPropertyToType(addPropertyTarget.id, payload);
      } else {
        await contentModelService.addPropertyToAspect(addPropertyTarget.id, payload);
      }
      setAddPropertyOpen(false);
      setAddPropertyForm({ name: '', title: '', dataType: 'TEXT', mandatory: false, multiValued: false, defaultValue: '' });
      setAddPropertyTarget(null);
      toast.success('Property added');
      await loadPage();
    } catch { toast.error('Failed to add property'); } finally { setSaving(false); }
  };

  const openAddProperty = (id: string, kind: 'type' | 'aspect', label: string) => {
    setAddPropertyTarget({ id, kind, label });
    setAddPropertyForm({ name: '', title: '', dataType: 'TEXT', mandatory: false, multiValued: false, defaultValue: '' });
    setAddPropertyOpen(true);
  };

  const openEditType = (type: TypeDefinition) => {
    setEditTypeForm({
      id: type.id,
      qualifiedName: type.qualifiedName,
      title: type.title || '',
      description: type.description || '',
      parentName: type.parentName || '',
    });
    setEditTypeOpen(true);
  };

  const openEditAspect = (aspect: AspectDefinition) => {
    setEditAspectForm({
      id: aspect.id,
      qualifiedName: aspect.qualifiedName,
      title: aspect.title || '',
      description: aspect.description || '',
      parentName: aspect.parentName || '',
    });
    setEditAspectOpen(true);
  };

  const handleEditType = async () => {
    if (!editTypeForm.id) {
      return;
    }
    setSaving(true);
    try {
      await contentModelService.updateType(editTypeForm.id, {
        title: editTypeForm.title.trim() || undefined,
        description: editTypeForm.description.trim() || undefined,
        parentName: editTypeForm.parentName.trim() || undefined,
      });
      setEditTypeOpen(false);
      toast.success('Type updated');
      await loadPage();
      setSelectedTypeName(editTypeForm.qualifiedName);
    } catch {
      toast.error('Failed to update type');
    } finally {
      setSaving(false);
    }
  };

  const handleEditAspect = async () => {
    if (!editAspectForm.id) {
      return;
    }
    setSaving(true);
    try {
      await contentModelService.updateAspect(editAspectForm.id, {
        title: editAspectForm.title.trim() || undefined,
        description: editAspectForm.description.trim() || undefined,
        parentName: editAspectForm.parentName.trim() || undefined,
      });
      setEditAspectOpen(false);
      toast.success('Aspect updated');
      await loadPage();
      setSelectedAspectName(editAspectForm.qualifiedName);
    } catch {
      toast.error('Failed to update aspect');
    } finally {
      setSaving(false);
    }
  };

  const handleDeleteType = async (type: TypeDefinition) => {
    if (!window.confirm(`Delete type ${type.qualifiedName}?`)) {
      return;
    }
    setSaving(true);
    try {
      await contentModelService.deleteType(type.id);
      setSelectedTypeName('');
      toast.success('Type deleted');
      await loadPage();
    } catch {
      toast.error('Failed to delete type');
    } finally {
      setSaving(false);
    }
  };

  const handleDeleteAspect = async (aspect: AspectDefinition) => {
    if (!window.confirm(`Delete aspect ${aspect.qualifiedName}?`)) {
      return;
    }
    setSaving(true);
    try {
      await contentModelService.deleteAspect(aspect.id);
      setSelectedAspectName('');
      toast.success('Aspect deleted');
      await loadPage();
    } catch {
      toast.error('Failed to delete aspect');
    } finally {
      setSaving(false);
    }
  };

  const openAddConstraint = (property: PropertyDefinition) => {
    setAddConstraintTarget(property);
    setAddConstraintForm({ constraintType: 'REGEX', values: emptyConstraintForm() });
    setAddConstraintOpen(true);
  };

  const handleAddConstraint = async () => {
    if (!addConstraintTarget) {
      return;
    }
    const validationMessage = getConstraintValidationMessage(addConstraintForm.constraintType, addConstraintForm.values);
    if (validationMessage) {
      toast.warn(validationMessage);
      return;
    }
    setSaving(true);
    try {
      await contentModelService.addConstraint(addConstraintTarget.id, {
        constraintType: addConstraintForm.constraintType,
        parameters: buildConstraintParameters(addConstraintForm.constraintType, addConstraintForm.values),
      });
      setAddConstraintOpen(false);
      setAddConstraintTarget(null);
      setAddConstraintForm({ constraintType: 'REGEX', values: emptyConstraintForm() });
      toast.success('Constraint added');
      await loadPage();
    } catch {
      toast.error('Failed to add constraint');
    } finally {
      setSaving(false);
    }
  };

  const handleDeleteProperty = async (property: PropertyDefinition) => {
    if (!window.confirm(`Delete property ${property.qualifiedName}?`)) {
      return;
    }
    setSaving(true);
    try {
      await contentModelService.deleteProperty(property.id);
      toast.success('Property deleted');
      await loadPage();
    } catch {
      toast.error('Failed to delete property');
    } finally {
      setSaving(false);
    }
  };

  const handleDeleteConstraint = async (constraint: ConstraintDefinition) => {
    if (!window.confirm(`Delete ${constraint.constraintType} constraint?`)) {
      return;
    }
    setSaving(true);
    try {
      await contentModelService.deleteConstraint(constraint.id);
      toast.success('Constraint deleted');
      await loadPage();
    } catch {
      toast.error('Failed to delete constraint');
    } finally {
      setSaving(false);
    }
  };

  const DATA_TYPES: PropertyDataType[] = ['TEXT','MLTEXT','INT','LONG','FLOAT','DOUBLE','DATE','DATETIME','BOOLEAN','URI','NODEREF','QNAME','CATEGORY','LOCALE','CONTENT'];

  const renderPropertyTable = (
    properties: PropertyDefinition[],
    canMutate: boolean
  ) => (
    <TableContainer component={Paper} variant="outlined">
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Property</TableCell>
            <TableCell>Type</TableCell>
            <TableCell>Flags</TableCell>
            <TableCell>Constraints</TableCell>
            <TableCell align="right">Actions</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {properties.length === 0 ? (
            <TableRow>
              <TableCell colSpan={5} align="center">
                No properties defined
              </TableCell>
            </TableRow>
          ) : (
            properties.map((property) => (
              <TableRow key={property.id}>
                <TableCell>
                  <Stack spacing={0.5}>
                    <Typography variant="body2">{property.title || property.name}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      {property.qualifiedName}
                    </Typography>
                  </Stack>
                </TableCell>
                <TableCell>{property.dataType}</TableCell>
                <TableCell>
                  <Stack direction="row" spacing={0.5} flexWrap="wrap">
                    {property.mandatory && <Chip size="small" label="Mandatory" color="error" variant="outlined" />}
                    {property.multiValued && <Chip size="small" label="Multi" variant="outlined" />}
                    {property.indexed && <Chip size="small" label="Indexed" color="success" variant="outlined" />}
                    {property.protectedField && <Chip size="small" label="Protected" color="warning" variant="outlined" />}
                  </Stack>
                </TableCell>
                <TableCell>
                  <Stack direction="row" spacing={0.5} flexWrap="wrap">
                    {property.constraints.length === 0 ? (
                      <Typography variant="caption" color="text.secondary">
                        None
                      </Typography>
                    ) : (
                      property.constraints.map((constraint) => (
                        <Chip
                          key={constraint.id}
                          size="small"
                          label={formatConstraintLabel(constraint)}
                          variant="outlined"
                          onDelete={canMutate ? () => void handleDeleteConstraint(constraint) : undefined}
                        />
                      ))
                    )}
                  </Stack>
                </TableCell>
                <TableCell align="right">
                  {canMutate ? (
                    <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                      <Button size="small" variant="outlined" onClick={() => openAddConstraint(property)}>
                        Constraint
                      </Button>
                      <Button
                        size="small"
                        color="error"
                        variant="outlined"
                        onClick={() => void handleDeleteProperty(property)}
                      >
                        Delete
                      </Button>
                    </Stack>
                  ) : (
                    <Typography variant="caption" color="text.secondary">
                      View only
                    </Typography>
                  )}
                </TableCell>
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>
    </TableContainer>
  );

  return (
    <Box maxWidth={1400}>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
        <Box>
          <Typography variant="h5">Content Models</Typography>
          <Typography variant="body2" color="text.secondary">
            Inspect registered models, type hierarchies, aspect definitions, and live dictionary metadata.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button
            variant="outlined"
            startIcon={<Refresh />}
            onClick={() => void loadPage()}
            disabled={loading || saving}
          >
            Refresh
          </Button>
          <Button variant="contained" startIcon={<Add />} onClick={() => setCreateDialogOpen(true)}>
            New Model
          </Button>
        </Stack>
      </Box>

      <Grid container spacing={2}>
        <Grid item xs={12} md={7}>
          <Card variant="outlined">
            <CardContent>
              <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
                <Typography variant="h6">Model Registry</Typography>
                <Chip label={`${models.length} model(s)`} size="small" variant="outlined" />
              </Box>
              <TableContainer component={Paper} variant="outlined">
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Qualified Name</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell>Types</TableCell>
                      <TableCell>Aspects</TableCell>
                      <TableCell align="right">Action</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {!loading && models.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={5} align="center">
                          No content models registered
                        </TableCell>
                      </TableRow>
                    )}
                    {models.map((model) => (
                      <TableRow
                        key={model.id}
                        hover
                        selected={model.id === selectedModelId}
                        sx={{ cursor: 'pointer' }}
                        onClick={() => setSelectedModelId(model.id)}
                      >
                        <TableCell>
                          <Stack spacing={0.5}>
                            <Typography variant="body2">{model.prefix}:{model.name}</Typography>
                            <Typography variant="caption" color="text.secondary">
                              {model.namespaceUri}
                            </Typography>
                          </Stack>
                        </TableCell>
                        <TableCell>
                          <Chip
                            size="small"
                            label={model.status}
                            color={model.status === 'ACTIVE' ? 'success' : model.status === 'DISABLED' ? 'default' : 'warning'}
                            variant="outlined"
                          />
                        </TableCell>
                        <TableCell>{model.types.length}</TableCell>
                        <TableCell>{model.aspects.length}</TableCell>
                        <TableCell align="right">
                          <Button
                            size="small"
                            variant="outlined"
                            onClick={(event) => {
                              event.stopPropagation();
                              void handleActivateToggle(model);
                            }}
                            disabled={saving}
                          >
                            {model.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={5}>
          <Card variant="outlined" sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Selected Model
              </Typography>
              {selectedModel ? (
                <Stack spacing={2}>
                  <Alert severity={selectedModel.status === 'ACTIVE' ? 'success' : 'info'}>
                    {selectedModel.prefix}:{selectedModel.name} is currently {selectedModel.status.toLowerCase()}.
                  </Alert>
                  <Box>
                    <Typography variant="body2" color="text.secondary">Description</Typography>
                    <Typography variant="body1">{selectedModel.description || 'No description'}</Typography>
                  </Box>
                  <Box>
                    <Box display="flex" justifyContent="space-between" alignItems="center">
                      <Typography variant="body2" color="text.secondary">Types</Typography>
                      <Button size="small" startIcon={<Add />} onClick={() => setAddTypeOpen(true)} disabled={saving}>Add Type</Button>
                    </Box>
                    <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap mt={0.5}>
                      {selectedModel.types.length === 0 ? (
                        <Typography variant="body2" color="text.secondary">No types</Typography>
                      ) : (
                        selectedModel.types.map((type) => (
                          <Chip
                            key={type.id}
                            label={type.qualifiedName}
                            size="small"
                            variant="outlined"
                            onClick={() => setSelectedTypeName(type.qualifiedName)}
                            onDelete={() => openAddProperty(type.id, 'type', type.qualifiedName)}
                            deleteIcon={<Add />}
                          />
                        ))
                      )}
                    </Stack>
                  </Box>
                  <Box>
                    <Box display="flex" justifyContent="space-between" alignItems="center">
                      <Typography variant="body2" color="text.secondary">Aspects</Typography>
                      <Button size="small" startIcon={<Add />} onClick={() => setAddAspectOpen(true)} disabled={saving}>Add Aspect</Button>
                    </Box>
                    <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap mt={0.5}>
                      {selectedModel.aspects.length === 0 ? (
                        <Typography variant="body2" color="text.secondary">No aspects</Typography>
                      ) : (
                        selectedModel.aspects.map((aspect) => (
                          <Chip
                            key={aspect.id}
                            label={aspect.qualifiedName}
                            size="small"
                            variant="outlined"
                            onClick={() => setSelectedAspectName(aspect.qualifiedName)}
                            onDelete={() => openAddProperty(aspect.id, 'aspect', aspect.qualifiedName)}
                            deleteIcon={<Add />}
                          />
                        ))
                      )}
                    </Stack>
                  </Box>
                </Stack>
              ) : (
                <Typography variant="body2" color="text.secondary">
                  Select a content model to inspect its types and aspects.
                </Typography>
              )}
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={6}>
          <Card variant="outlined">
            <CardContent>
              <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
                <Typography variant="h6">Dictionary Type Explorer</Typography>
                {selectedModelType && (
                  <Stack direction="row" spacing={1}>
                    <Button
                      size="small"
                      startIcon={<Add />}
                      onClick={() => openAddProperty(selectedModelType.id, 'type', selectedModelType.qualifiedName)}
                    >
                      Property
                    </Button>
                    <Button size="small" startIcon={<Edit />} onClick={() => openEditType(selectedModelType)}>
                      Edit
                    </Button>
                    <Button
                      size="small"
                      color="error"
                      startIcon={<DeleteOutline />}
                      onClick={() => void handleDeleteType(selectedModelType)}
                    >
                      Delete
                    </Button>
                  </Stack>
                )}
              </Box>
              <Select
                fullWidth
                size="small"
                value={selectedTypeName}
                onChange={(event) => setSelectedTypeName(event.target.value)}
                displayEmpty
              >
                <MenuItem value="">
                  <em>Select a type</em>
                </MenuItem>
                {dictionaryTypes.map((type) => (
                  <MenuItem key={type.id} value={type.qualifiedName}>
                    {type.qualifiedName}
                  </MenuItem>
                ))}
              </Select>

              {selectedType && (
                <Stack spacing={2} mt={2}>
                  {!selectedModelType && (
                    <Alert severity="info">
                      Select the owning model to edit this type. Current view is dictionary-only.
                    </Alert>
                  )}
                  <Box>
                    <Typography variant="body2" color="text.secondary">Hierarchy</Typography>
                    <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap mt={0.5}>
                      {selectedTypeHierarchy.map((entry) => (
                        <Chip key={entry} size="small" label={entry} variant="outlined" />
                      ))}
                    </Stack>
                  </Box>
                  <Box>
                    <Typography variant="body2" color="text.secondary">Mandatory Aspects</Typography>
                    <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap mt={0.5}>
                      {selectedMandatoryAspects.length === 0 ? (
                        <Typography variant="body2" color="text.secondary">None</Typography>
                      ) : (
                        selectedMandatoryAspects.map((aspect) => (
                          <Chip key={aspect} size="small" label={aspect} color="warning" variant="outlined" />
                        ))
                      )}
                    </Stack>
                  </Box>
                  {renderPropertyTable(selectedType.properties, !!selectedModelType)}
                </Stack>
              )}
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={6}>
          <Card variant="outlined">
            <CardContent>
              <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
                <Typography variant="h6">Dictionary Aspect Explorer</Typography>
                {selectedModelAspect && (
                  <Stack direction="row" spacing={1}>
                    <Button
                      size="small"
                      startIcon={<Add />}
                      onClick={() => openAddProperty(selectedModelAspect.id, 'aspect', selectedModelAspect.qualifiedName)}
                    >
                      Property
                    </Button>
                    <Button size="small" startIcon={<Edit />} onClick={() => openEditAspect(selectedModelAspect)}>
                      Edit
                    </Button>
                    <Button
                      size="small"
                      color="error"
                      startIcon={<DeleteOutline />}
                      onClick={() => void handleDeleteAspect(selectedModelAspect)}
                    >
                      Delete
                    </Button>
                  </Stack>
                )}
              </Box>
              <Select
                fullWidth
                size="small"
                value={selectedAspectName}
                onChange={(event) => setSelectedAspectName(event.target.value)}
                displayEmpty
              >
                <MenuItem value="">
                  <em>Select an aspect</em>
                </MenuItem>
                {dictionaryAspects.map((aspect) => (
                  <MenuItem key={aspect.id} value={aspect.qualifiedName}>
                    {aspect.qualifiedName}
                  </MenuItem>
                ))}
              </Select>

              {selectedAspect && (
                <Stack spacing={2} mt={2}>
                  {!selectedModelAspect && (
                    <Alert severity="info">
                      Select the owning model to edit this aspect. Current view is dictionary-only.
                    </Alert>
                  )}
                  <Box>
                    <Typography variant="body2" color="text.secondary">Description</Typography>
                    <Typography variant="body1">{selectedAspect.description || 'No description'}</Typography>
                  </Box>
                  {renderPropertyTable(selectedAspect.properties, !!selectedModelAspect)}
                </Stack>
              )}
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Dialog open={createDialogOpen} onClose={() => setCreateDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Create Content Model</DialogTitle>
        <DialogContent>
          <Stack spacing={2} mt={1}>
            <TextField
              label="Prefix"
              value={createForm.prefix}
              onChange={(event) => setCreateForm((prev) => ({ ...prev, prefix: event.target.value }))}
              required
              fullWidth
            />
            <TextField
              label="Name"
              value={createForm.name}
              onChange={(event) => setCreateForm((prev) => ({ ...prev, name: event.target.value }))}
              required
              fullWidth
            />
            <TextField
              label="Namespace URI"
              value={createForm.namespaceUri}
              onChange={(event) => setCreateForm((prev) => ({ ...prev, namespaceUri: event.target.value }))}
              required
              fullWidth
            />
            <TextField
              label="Description"
              value={createForm.description}
              onChange={(event) => setCreateForm((prev) => ({ ...prev, description: event.target.value }))}
              fullWidth
              multiline
              minRows={2}
            />
            <Grid container spacing={2}>
              <Grid item xs={12} md={6}>
                <TextField
                  label="Author"
                  value={createForm.author}
                  onChange={(event) => setCreateForm((prev) => ({ ...prev, author: event.target.value }))}
                  fullWidth
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  label="Version Label"
                  value={createForm.versionLabel}
                  onChange={(event) => setCreateForm((prev) => ({ ...prev, versionLabel: event.target.value }))}
                  fullWidth
                />
              </Grid>
            </Grid>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void handleCreateModel()} disabled={saving}>
            Create
          </Button>
        </DialogActions>
      </Dialog>

      {/* Add Type dialog */}
      <Dialog open={addTypeOpen} onClose={() => setAddTypeOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Add Type to {selectedModel?.prefix}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} mt={1}>
            <TextField label="Name" value={addTypeForm.name} onChange={(e) => setAddTypeForm((p) => ({ ...p, name: e.target.value }))} required fullWidth />
            <TextField label="Title" value={addTypeForm.title} onChange={(e) => setAddTypeForm((p) => ({ ...p, title: e.target.value }))} fullWidth />
            <TextField label="Description" value={addTypeForm.description} onChange={(e) => setAddTypeForm((p) => ({ ...p, description: e.target.value }))} fullWidth multiline minRows={2} />
            <TextField label="Parent Type (qualified name)" value={addTypeForm.parentName} onChange={(e) => setAddTypeForm((p) => ({ ...p, parentName: e.target.value }))} fullWidth helperText="e.g. cm:content" />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAddTypeOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void handleAddType()} disabled={saving}>Add</Button>
        </DialogActions>
      </Dialog>

      {/* Add Aspect dialog */}
      <Dialog open={addAspectOpen} onClose={() => setAddAspectOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Add Aspect to {selectedModel?.prefix}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} mt={1}>
            <TextField label="Name" value={addAspectForm.name} onChange={(e) => setAddAspectForm((p) => ({ ...p, name: e.target.value }))} required fullWidth />
            <TextField label="Title" value={addAspectForm.title} onChange={(e) => setAddAspectForm((p) => ({ ...p, title: e.target.value }))} fullWidth />
            <TextField label="Description" value={addAspectForm.description} onChange={(e) => setAddAspectForm((p) => ({ ...p, description: e.target.value }))} fullWidth multiline minRows={2} />
            <TextField label="Parent Aspect (qualified name)" value={addAspectForm.parentName} onChange={(e) => setAddAspectForm((p) => ({ ...p, parentName: e.target.value }))} fullWidth />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAddAspectOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void handleAddAspect()} disabled={saving}>Add</Button>
        </DialogActions>
      </Dialog>

      {/* Add Property dialog */}
      <Dialog open={addPropertyOpen} onClose={() => { setAddPropertyOpen(false); setAddPropertyTarget(null); }} maxWidth="sm" fullWidth>
        <DialogTitle>Add Property to {addPropertyTarget?.label}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} mt={1}>
            <TextField label="Name" value={addPropertyForm.name} onChange={(e) => setAddPropertyForm((p) => ({ ...p, name: e.target.value }))} required fullWidth />
            <TextField label="Title" value={addPropertyForm.title} onChange={(e) => setAddPropertyForm((p) => ({ ...p, title: e.target.value }))} fullWidth />
            <Select
              fullWidth
              size="small"
              value={addPropertyForm.dataType}
              onChange={(e) => setAddPropertyForm((p) => ({ ...p, dataType: e.target.value as PropertyDataType }))}
            >
              {DATA_TYPES.map((dt) => <MenuItem key={dt} value={dt}>{dt}</MenuItem>)}
            </Select>
            <Grid container spacing={2}>
              <Grid item xs={6}>
                <Button
                  variant={addPropertyForm.mandatory ? 'contained' : 'outlined'}
                  size="small"
                  fullWidth
                  onClick={() => setAddPropertyForm((p) => ({ ...p, mandatory: !p.mandatory }))}
                >
                  Mandatory: {addPropertyForm.mandatory ? 'Yes' : 'No'}
                </Button>
              </Grid>
              <Grid item xs={6}>
                <Button
                  variant={addPropertyForm.multiValued ? 'contained' : 'outlined'}
                  size="small"
                  fullWidth
                  onClick={() => setAddPropertyForm((p) => ({ ...p, multiValued: !p.multiValued }))}
                >
                  Multi-valued: {addPropertyForm.multiValued ? 'Yes' : 'No'}
                </Button>
              </Grid>
            </Grid>
            <TextField label="Default Value" value={addPropertyForm.defaultValue} onChange={(e) => setAddPropertyForm((p) => ({ ...p, defaultValue: e.target.value }))} fullWidth />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => { setAddPropertyOpen(false); setAddPropertyTarget(null); }}>Cancel</Button>
          <Button variant="contained" onClick={() => void handleAddProperty()} disabled={saving}>Add</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={editTypeOpen} onClose={() => setEditTypeOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Edit Type {editTypeForm.qualifiedName}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} mt={1}>
            <TextField label="Title" value={editTypeForm.title} onChange={(e) => setEditTypeForm((p) => ({ ...p, title: e.target.value }))} fullWidth />
            <TextField label="Description" value={editTypeForm.description} onChange={(e) => setEditTypeForm((p) => ({ ...p, description: e.target.value }))} fullWidth multiline minRows={2} />
            <TextField label="Parent Type (qualified name)" value={editTypeForm.parentName} onChange={(e) => setEditTypeForm((p) => ({ ...p, parentName: e.target.value }))} fullWidth helperText="Leave blank to clear inheritance" />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditTypeOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void handleEditType()} disabled={saving}>Save</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={editAspectOpen} onClose={() => setEditAspectOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Edit Aspect {editAspectForm.qualifiedName}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} mt={1}>
            <TextField label="Title" value={editAspectForm.title} onChange={(e) => setEditAspectForm((p) => ({ ...p, title: e.target.value }))} fullWidth />
            <TextField label="Description" value={editAspectForm.description} onChange={(e) => setEditAspectForm((p) => ({ ...p, description: e.target.value }))} fullWidth multiline minRows={2} />
            <TextField label="Parent Aspect (qualified name)" value={editAspectForm.parentName} onChange={(e) => setEditAspectForm((p) => ({ ...p, parentName: e.target.value }))} fullWidth helperText="Leave blank to clear inheritance" />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditAspectOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void handleEditAspect()} disabled={saving}>Save</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={addConstraintOpen} onClose={() => { setAddConstraintOpen(false); setAddConstraintTarget(null); }} maxWidth="sm" fullWidth>
        <DialogTitle>Add Constraint to {addConstraintTarget?.qualifiedName}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} mt={1}>
            <Select
              fullWidth
              size="small"
              value={addConstraintForm.constraintType}
              onChange={(e) => setAddConstraintForm((prev) => ({ ...prev, constraintType: e.target.value as ConstraintType, values: emptyConstraintForm() }))}
            >
              {(['REGEX', 'LIST', 'RANGE', 'LENGTH'] as ConstraintType[]).map((constraintType) => (
                <MenuItem key={constraintType} value={constraintType}>{constraintType}</MenuItem>
              ))}
            </Select>
            {addConstraintForm.constraintType === 'REGEX' && (
              <TextField
                label="Regex Pattern"
                value={addConstraintForm.values.pattern}
                onChange={(e) => setAddConstraintForm((prev) => ({ ...prev, values: { ...prev.values, pattern: e.target.value } }))}
                fullWidth
                helperText="Example: ^cm:.*$"
              />
            )}
            {addConstraintForm.constraintType === 'LIST' && (
              <TextField
                label="Allowed Values"
                value={addConstraintForm.values.values}
                onChange={(e) => setAddConstraintForm((prev) => ({ ...prev, values: { ...prev.values, values: e.target.value } }))}
                fullWidth
                multiline
                minRows={3}
                helperText="Comma or newline separated values"
              />
            )}
            {(addConstraintForm.constraintType === 'RANGE' || addConstraintForm.constraintType === 'LENGTH') && (
              <Grid container spacing={2}>
                <Grid item xs={12} md={6}>
                  <TextField
                    label="Minimum"
                    value={addConstraintForm.values.min}
                    onChange={(e) => setAddConstraintForm((prev) => ({ ...prev, values: { ...prev.values, min: e.target.value } }))}
                    fullWidth
                  />
                </Grid>
                <Grid item xs={12} md={6}>
                  <TextField
                    label="Maximum"
                    value={addConstraintForm.values.max}
                    onChange={(e) => setAddConstraintForm((prev) => ({ ...prev, values: { ...prev.values, max: e.target.value } }))}
                    fullWidth
                  />
                </Grid>
              </Grid>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => { setAddConstraintOpen(false); setAddConstraintTarget(null); }}>Cancel</Button>
          <Button variant="contained" onClick={() => void handleAddConstraint()} disabled={saving}>Add</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default ContentModelsPage;
