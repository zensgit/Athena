import React, { useEffect, useMemo, useState } from 'react';
import {
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControl,
  IconButton,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
  Autocomplete,
  Chip,
} from '@mui/material';
import { Add, Delete, Edit } from '@mui/icons-material';
import { toast } from 'react-toastify';
import nodeService, { PermissionSetMetadata } from 'services/nodeService';
import permissionTemplateService, {
  PermissionTemplate,
  PermissionTemplateEntry,
} from 'services/permissionTemplateService';
import userGroupService from 'services/userGroupService';

const emptyEntry = (permissionSet: string): PermissionTemplateEntry => ({
  authority: '',
  authorityType: 'USER',
  permissionSet,
});

const PermissionTemplatesPage: React.FC = () => {
  const [templates, setTemplates] = useState<PermissionTemplate[]>([]);
  const [permissionSets, setPermissionSets] = useState<PermissionSetMetadata[]>([]);
  const [users, setUsers] = useState<string[]>([]);
  const [groups, setGroups] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingTemplate, setEditingTemplate] = useState<PermissionTemplate | null>(null);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [entries, setEntries] = useState<PermissionTemplateEntry[]>([]);

  const permissionSetOptions = useMemo(() => {
    return [...permissionSets].sort((a, b) => {
      const left = a.order ?? 0;
      const right = b.order ?? 0;
      if (left !== right) {
        return left - right;
      }
      return a.name.localeCompare(b.name);
    });
  }, [permissionSets]);

  const loadTemplates = async () => {
    try {
      setLoading(true);
      const data = await permissionTemplateService.list();
      setTemplates(data ?? []);
    } catch {
      toast.error('Failed to load permission templates');
    } finally {
      setLoading(false);
    }
  };

  const loadOptions = async () => {
    try {
      const [meta, usersList, groupList] = await Promise.all([
        nodeService.getPermissionSetMetadata().catch(() => []),
        userGroupService.listUsers().catch(() => []),
        userGroupService.listGroups().catch(() => []),
      ]);
      setPermissionSets(meta ?? []);
      setUsers(usersList.map((u) => u.username));
      setGroups(groupList.map((g) => g.name));
    } catch {
      // optional
    }
  };

  useEffect(() => {
    loadOptions();
    loadTemplates();
  }, []);

  const openCreateDialog = () => {
    setEditingTemplate(null);
    setName('');
    setDescription('');
    const defaultSet = permissionSetOptions[0]?.name ?? 'CONSUMER';
    setEntries([emptyEntry(defaultSet)]);
    setDialogOpen(true);
  };

  const openEditDialog = (template: PermissionTemplate) => {
    setEditingTemplate(template);
    setName(template.name ?? '');
    setDescription(template.description ?? '');
    setEntries(template.entries ?? []);
    setDialogOpen(true);
  };

  const handleSave = async () => {
    if (!name.trim()) {
      toast.error('Template name is required');
      return;
    }
    const cleanedEntries = entries
      .map((entry) => ({
        ...entry,
        authority: entry.authority.trim(),
        permissionSet: entry.permissionSet,
      }))
      .filter((entry) => entry.authority && entry.permissionSet);

    if (cleanedEntries.length === 0) {
      toast.error('Add at least one permission entry');
      return;
    }

    try {
      if (editingTemplate) {
        await permissionTemplateService.update(editingTemplate.id, {
          name: name.trim(),
          description: description.trim() || undefined,
          entries: cleanedEntries,
        });
        toast.success('Permission template updated');
      } else {
        await permissionTemplateService.create({
          name: name.trim(),
          description: description.trim() || undefined,
          entries: cleanedEntries,
        });
        toast.success('Permission template created');
      }
      setDialogOpen(false);
      await loadTemplates();
    } catch {
      toast.error('Failed to save permission template');
    }
  };

  const handleDelete = async (template: PermissionTemplate) => {
    if (!window.confirm(`Delete permission template "${template.name}"?`)) {
      return;
    }
    try {
      await permissionTemplateService.remove(template.id);
      toast.success('Permission template deleted');
      await loadTemplates();
    } catch {
      toast.error('Failed to delete permission template');
    }
  };

  const updateEntry = (index: number, updates: Partial<PermissionTemplateEntry>) => {
    setEntries((prev) => prev.map((entry, i) => (i === index ? { ...entry, ...updates } : entry)));
  };

  const addEntry = () => {
    const defaultSet = permissionSetOptions[0]?.name ?? 'CONSUMER';
    setEntries((prev) => [...prev, emptyEntry(defaultSet)]);
  };

  const removeEntry = (index: number) => {
    setEntries((prev) => prev.filter((_, i) => i !== index));
  };

  return (
    <Box maxWidth={1100}>
      <Box display="flex" alignItems="center" justifyContent="space-between" mb={2}>
        <Box>
          <Typography variant="h5">Permission Templates</Typography>
          <Typography variant="body2" color="text.secondary">
            Reusable ACL presets for users and groups.
          </Typography>
        </Box>
        <Button variant="contained" startIcon={<Add />} onClick={openCreateDialog}>
          New Template
        </Button>
      </Box>

      <Paper variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Name</TableCell>
              <TableCell>Description</TableCell>
              <TableCell>Entries</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {templates.map((template) => (
              <TableRow key={template.id} hover>
                <TableCell>{template.name}</TableCell>
                <TableCell>{template.description || '—'}</TableCell>
                <TableCell>
                  <Box display="flex" flexWrap="wrap" gap={0.5}>
                    {(template.entries || []).map((entry, idx) => (
                      <Chip
                        key={`${template.id}-${idx}`}
                        size="small"
                        label={`${entry.authority} · ${entry.permissionSet}`}
                        variant="outlined"
                      />
                    ))}
                    {(template.entries || []).length === 0 && (
                      <Typography variant="caption" color="text.secondary">No entries</Typography>
                    )}
                  </Box>
                </TableCell>
                <TableCell align="right">
                  <IconButton size="small" onClick={() => openEditDialog(template)}>
                    <Edit fontSize="small" />
                  </IconButton>
                  <IconButton size="small" color="error" onClick={() => handleDelete(template)}>
                    <Delete fontSize="small" />
                  </IconButton>
                </TableCell>
              </TableRow>
            ))}
            {!loading && templates.length === 0 && (
              <TableRow>
                <TableCell colSpan={4}>
                  <Typography variant="body2" color="text.secondary">
                    No permission templates defined.
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </Paper>

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle>{editingTemplate ? 'Edit Permission Template' : 'New Permission Template'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Name"
              value={name}
              onChange={(event) => setName(event.target.value)}
              fullWidth
            />
            <TextField
              label="Description"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              fullWidth
            />
            <Divider />
            <Typography variant="subtitle2">Entries</Typography>
            <Stack spacing={2}>
              {entries.map((entry, index) => (
                <Paper key={`entry-${index}`} variant="outlined" sx={{ p: 2 }}>
                  <Stack spacing={2} direction={{ xs: 'column', md: 'row' }} alignItems="center">
                    <FormControl size="small" sx={{ minWidth: 140 }}>
                      <InputLabel id={`entry-type-${index}`}>Type</InputLabel>
                      <Select
                        labelId={`entry-type-${index}`}
                        label="Type"
                        value={entry.authorityType}
                        onChange={(event) => updateEntry(index, { authorityType: event.target.value as 'USER' | 'GROUP' })}
                      >
                        <MenuItem value="USER">User</MenuItem>
                        <MenuItem value="GROUP">Group</MenuItem>
                      </Select>
                    </FormControl>
                    <Autocomplete
                      freeSolo
                      options={entry.authorityType === 'GROUP' ? groups : users}
                      value={entry.authority}
                      onInputChange={(_, value) => updateEntry(index, { authority: value })}
                      renderInput={(params) => (
                        <TextField {...params} label="Authority" size="small" fullWidth />
                      )}
                      sx={{ flex: 1 }}
                    />
                    <FormControl size="small" sx={{ minWidth: 200 }}>
                      <InputLabel id={`entry-set-${index}`}>Permission Set</InputLabel>
                      <Select
                        labelId={`entry-set-${index}`}
                        label="Permission Set"
                        value={entry.permissionSet}
                        onChange={(event) => updateEntry(index, { permissionSet: String(event.target.value) })}
                      >
                        {permissionSetOptions.map((option) => (
                          <MenuItem key={option.name} value={option.name}>
                            {option.label || option.name}
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                    <IconButton size="small" color="error" onClick={() => removeEntry(index)}>
                      <Delete fontSize="small" />
                    </IconButton>
                  </Stack>
                </Paper>
              ))}
            </Stack>
            <Button variant="outlined" startIcon={<Add />} onClick={addEntry}>
              Add Entry
            </Button>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleSave}>
            {editingTemplate ? 'Save Changes' : 'Create Template'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default PermissionTemplatesPage;
