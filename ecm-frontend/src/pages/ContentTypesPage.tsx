import React, { useEffect, useMemo, useState } from 'react';
import {
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
  IconButton,
  MenuItem,
  Paper,
  Select,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import { Add, Delete, Edit } from '@mui/icons-material';
import { toast } from 'react-toastify';
import contentTypeService, {
  ContentTypeDefinition,
  ContentTypePropertyDefinition,
  ContentTypePropertyType,
} from 'services/contentTypeService';

const PROPERTY_TYPES: ContentTypePropertyType[] = [
  'text',
  'long_text',
  'url',
  'integer',
  'float',
  'monetary',
  'number',
  'date',
  'boolean',
  'list',
  'select',
  'documentlink',
];

const PROPERTY_TYPE_LABELS: Record<ContentTypePropertyType, string> = {
  text: 'Text',
  long_text: 'Long Text',
  url: 'URL',
  integer: 'Integer',
  float: 'Float',
  monetary: 'Monetary',
  number: 'Number',
  date: 'Date',
  boolean: 'Boolean',
  list: 'List',
  select: 'Select',
  documentlink: 'Document Link',
};

const emptyProperty = (): ContentTypePropertyDefinition => ({
  name: '',
  title: '',
  type: 'text',
  required: false,
  searchable: false,
  defaultValue: '',
  options: [],
  regex: '',
});

const ContentTypesPage: React.FC = () => {
  const [types, setTypes] = useState<ContentTypeDefinition[]>([]);
  const [loading, setLoading] = useState(false);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingType, setEditingType] = useState<ContentTypeDefinition | null>(null);
  const [form, setForm] = useState<ContentTypeDefinition>({
    id: '',
    name: '',
    displayName: '',
    description: '',
    parentType: '',
    properties: [],
  });

  const loadTypes = async () => {
    setLoading(true);
    try {
      const data = await contentTypeService.listTypes();
      setTypes(data);
    } catch {
      toast.error('Failed to load content types');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadTypes();
  }, []);

  const openCreateDialog = () => {
    setEditingType(null);
    setForm({
      id: '',
      name: '',
      displayName: '',
      description: '',
      parentType: '',
      properties: [],
    });
    setDialogOpen(true);
  };

  const openEditDialog = (type: ContentTypeDefinition) => {
    setEditingType(type);
    setForm({
      ...type,
      description: type.description || '',
      parentType: type.parentType || '',
      properties: type.properties || [],
    });
    setDialogOpen(true);
  };

  const validateForm = () => {
    if (!form.name.trim()) {
      toast.warn('Type name is required');
      return false;
    }
    if (!form.displayName.trim()) {
      toast.warn('Display name is required');
      return false;
    }
    const names = form.properties.map((prop) => prop.name.trim()).filter(Boolean);
    const uniqueNames = new Set(names);
    if (names.length !== uniqueNames.size) {
      toast.warn('Property names must be unique');
      return false;
    }
    return true;
  };

  const handleSave = async () => {
    if (!validateForm()) {
      return;
    }

    try {
      const payload = {
        name: form.name.trim(),
        displayName: form.displayName.trim(),
        description: form.description?.trim() || undefined,
        parentType: form.parentType?.trim() || undefined,
        properties: form.properties.map((prop) => ({
          ...prop,
          name: prop.name.trim(),
          title: prop.title.trim(),
          defaultValue: prop.defaultValue?.trim() || undefined,
          regex: prop.regex?.trim() || undefined,
          options: (prop.options || []).map((opt) => opt.trim()).filter(Boolean),
        })),
      };

      if (editingType) {
        await contentTypeService.updateType(editingType.name, payload);
        toast.success('Content type updated');
      } else {
        await contentTypeService.createType(payload);
        toast.success('Content type created');
      }

      setDialogOpen(false);
      await loadTypes();
    } catch {
      toast.error('Failed to save content type');
    }
  };

  const handleDelete = async (typeName: string) => {
    if (!window.confirm('Delete this content type?')) {
      return;
    }
    try {
      await contentTypeService.deleteType(typeName);
      toast.success('Content type deleted');
      await loadTypes();
    } catch {
      toast.error('Failed to delete content type');
    }
  };

  const updateProperty = (index: number, updates: Partial<ContentTypePropertyDefinition>) => {
    setForm((prev) => {
      const nextProps = prev.properties.map((prop, i) =>
        i === index ? { ...prop, ...updates } : prop
      );
      return { ...prev, properties: nextProps };
    });
  };

  const addProperty = () => {
    setForm((prev) => ({
      ...prev,
      properties: [...prev.properties, emptyProperty()],
    }));
  };

  const removeProperty = (index: number) => {
    setForm((prev) => ({
      ...prev,
      properties: prev.properties.filter((_, i) => i !== index),
    }));
  };

  const propertyCountLabel = useMemo(() => {
    return `${types.length} type(s)`;
  }, [types.length]);

  return (
    <Box maxWidth={1100}>
      <Box display="flex" alignItems="center" justifyContent="space-between" mb={2}>
        <Box>
          <Typography variant="h5">Content Types</Typography>
          <Typography variant="body2" color="text.secondary">
            Manage structured metadata schemas for documents.
          </Typography>
        </Box>
        <Button variant="contained" startIcon={<Add />} onClick={openCreateDialog}>
          New Type
        </Button>
      </Box>

      <Card variant="outlined">
        <CardContent>
          <Box display="flex" alignItems="center" justifyContent="space-between" mb={2}>
            <Typography variant="h6">Defined Types</Typography>
            <Chip size="small" label={propertyCountLabel} variant="outlined" />
          </Box>
          <TableContainer component={Paper} variant="outlined">
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Name</TableCell>
                  <TableCell>Display Name</TableCell>
                  <TableCell>Parent</TableCell>
                  <TableCell>Properties</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {!loading && types.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={5} align="center">
                      No content types defined
                    </TableCell>
                  </TableRow>
                )}
                {types.map((type) => (
                  <TableRow key={type.name} hover>
                    <TableCell>{type.name}</TableCell>
                    <TableCell>{type.displayName}</TableCell>
                    <TableCell>{type.parentType || '—'}</TableCell>
                    <TableCell>{type.properties?.length || 0}</TableCell>
                    <TableCell align="right">
                      <IconButton size="small" onClick={() => openEditDialog(type)}>
                        <Edit fontSize="small" />
                      </IconButton>
                      <IconButton size="small" color="error" onClick={() => handleDelete(type.name)}>
                        <Delete fontSize="small" />
                      </IconButton>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </CardContent>
      </Card>

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle>{editingType ? 'Edit Content Type' : 'New Content Type'}</DialogTitle>
        <DialogContent>
          <Box display="flex" flexDirection="column" gap={2} mt={1}>
            <TextField
              label="System Name"
              value={form.name}
              onChange={(event) => setForm({ ...form, name: event.target.value })}
              size="small"
              fullWidth
              disabled={Boolean(editingType)}
              helperText="Immutable identifier, e.g. ecm:invoice"
            />
            <TextField
              label="Display Name"
              value={form.displayName}
              onChange={(event) => setForm({ ...form, displayName: event.target.value })}
              size="small"
              fullWidth
            />
            <TextField
              label="Description"
              value={form.description || ''}
              onChange={(event) => setForm({ ...form, description: event.target.value })}
              size="small"
              fullWidth
            />
            <TextField
              label="Parent Type"
              value={form.parentType || ''}
              onChange={(event) => setForm({ ...form, parentType: event.target.value })}
              size="small"
              fullWidth
              helperText="Optional parent type (e.g. ecm:document)"
            />
          </Box>

          <Box mt={3}>
            <Box display="flex" alignItems="center" justifyContent="space-between" mb={1}>
              <Typography variant="subtitle1">Property Definitions</Typography>
              <Button size="small" startIcon={<Add />} onClick={addProperty}>
                Add Property
              </Button>
            </Box>
            {form.properties.length === 0 && (
              <Typography variant="body2" color="text.secondary">
                No properties defined.
              </Typography>
            )}
            <Box display="flex" flexDirection="column" gap={2} mt={1}>
              {form.properties.map((prop, index) => (
                <Paper key={`prop-${index}`} variant="outlined" sx={{ p: 2 }}>
                  <Grid container spacing={2} alignItems="center">
                    <Grid item xs={12} sm={6} md={3}>
                      <TextField
                        label="Name"
                        value={prop.name}
                        onChange={(event) => updateProperty(index, { name: event.target.value })}
                        size="small"
                        fullWidth
                      />
                    </Grid>
                    <Grid item xs={12} sm={6} md={3}>
                      <TextField
                        label="Title"
                        value={prop.title}
                        onChange={(event) => updateProperty(index, { title: event.target.value })}
                        size="small"
                        fullWidth
                      />
                    </Grid>
                    <Grid item xs={12} sm={6} md={2}>
                      <Select
                        value={prop.type}
                        size="small"
                        fullWidth
                        onChange={(event) => updateProperty(index, { type: event.target.value as ContentTypePropertyType })}
                      >
                        {PROPERTY_TYPES.map((type) => (
                          <MenuItem key={type} value={type}>
                            {PROPERTY_TYPE_LABELS[type] ?? type}
                          </MenuItem>
                        ))}
                      </Select>
                    </Grid>
                    <Grid item xs={6} sm={3} md={2}>
                      <Select
                        value={prop.required ? 'yes' : 'no'}
                        size="small"
                        fullWidth
                        onChange={(event) => updateProperty(index, { required: event.target.value === 'yes' })}
                      >
                        <MenuItem value="no">Optional</MenuItem>
                        <MenuItem value="yes">Required</MenuItem>
                      </Select>
                    </Grid>
                    <Grid item xs={6} sm={3} md={2}>
                      <Select
                        value={prop.searchable ? 'yes' : 'no'}
                        size="small"
                        fullWidth
                        onChange={(event) => updateProperty(index, { searchable: event.target.value === 'yes' })}
                      >
                        <MenuItem value="no">Not searchable</MenuItem>
                        <MenuItem value="yes">Searchable</MenuItem>
                      </Select>
                    </Grid>
                    <Grid item xs={12} sm={6} md={3}>
                      <TextField
                        label="Default value"
                        value={prop.defaultValue || ''}
                        onChange={(event) => updateProperty(index, { defaultValue: event.target.value })}
                        size="small"
                        fullWidth
                      />
                    </Grid>
                    <Grid item xs={12} sm={6} md={4}>
                      <TextField
                        label="Options (comma separated)"
                        value={(prop.options || []).join(', ')}
                        onChange={(event) => updateProperty(index, {
                          options: event.target.value
                            .split(',')
                            .map((value) => value.trim())
                            .filter(Boolean),
                        })}
                        size="small"
                        fullWidth
                        disabled={prop.type !== 'list'}
                      />
                    </Grid>
                    <Grid item xs={12} sm={6} md={4}>
                      <TextField
                        label="Regex"
                        value={prop.regex || ''}
                        onChange={(event) => updateProperty(index, { regex: event.target.value })}
                        size="small"
                        fullWidth
                      />
                    </Grid>
                    <Grid item xs={12} sm={6} md={1}>
                      <IconButton size="small" color="error" onClick={() => removeProperty(index)}>
                        <Delete fontSize="small" />
                      </IconButton>
                    </Grid>
                  </Grid>
                </Paper>
              ))}
            </Box>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleSave}>Save</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default ContentTypesPage;
