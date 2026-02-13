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
import { Add, Delete, Edit, History } from '@mui/icons-material';
import { toast } from 'react-toastify';
import nodeService, { PermissionSetMetadata } from 'services/nodeService';
import permissionTemplateService, {
  PermissionTemplate,
  PermissionTemplateEntry,
  PermissionTemplateVersion,
  PermissionTemplateVersionDetail,
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
  const [historyOpen, setHistoryOpen] = useState(false);
  const [historyTemplate, setHistoryTemplate] = useState<PermissionTemplate | null>(null);
  const [versions, setVersions] = useState<PermissionTemplateVersion[]>([]);
  const [loadingVersions, setLoadingVersions] = useState(false);
  const [restoringVersionId, setRestoringVersionId] = useState<string | null>(null);
  const [compareOpen, setCompareOpen] = useState(false);
  const [compareCurrent, setCompareCurrent] = useState<PermissionTemplateVersionDetail | null>(null);
  const [comparePrevious, setComparePrevious] = useState<PermissionTemplateVersionDetail | null>(null);
  const [compareLoading, setCompareLoading] = useState(false);
  const [exportingCompare, setExportingCompare] = useState<'csv' | 'json' | null>(null);

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

  const openHistoryDialog = async (template: PermissionTemplate) => {
    setHistoryTemplate(template);
    setHistoryOpen(true);
    setVersions([]);
    try {
      setLoadingVersions(true);
      const data = await permissionTemplateService.listVersions(template.id);
      setVersions(data ?? []);
    } catch {
      toast.error('Failed to load template history');
    } finally {
      setLoadingVersions(false);
    }
  };

  const handleRollback = async (version: PermissionTemplateVersion) => {
    if (!historyTemplate) {
      return;
    }
    if (!window.confirm(`Restore "${historyTemplate.name}" to version ${version.versionNumber}?`)) {
      return;
    }
    try {
      setRestoringVersionId(version.id);
      await permissionTemplateService.rollbackVersion(historyTemplate.id, version.id);
      toast.success('Permission template restored');
      await loadTemplates();
      const data = await permissionTemplateService.listVersions(historyTemplate.id);
      setVersions(data ?? []);
    } catch {
      toast.error('Failed to restore template');
    } finally {
      setRestoringVersionId(null);
    }
  };

  const handleCompare = async (current: PermissionTemplateVersion, previous: PermissionTemplateVersion) => {
    if (!historyTemplate) {
      return;
    }
    try {
      setCompareLoading(true);
      const [currentDetail, previousDetail] = await Promise.all([
        permissionTemplateService.getVersionDetail(historyTemplate.id, current.id),
        permissionTemplateService.getVersionDetail(historyTemplate.id, previous.id),
      ]);
      setCompareCurrent(currentDetail);
      setComparePrevious(previousDetail);
      setCompareOpen(true);
    } catch {
      toast.error('Failed to load version comparison');
    } finally {
      setCompareLoading(false);
    }
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

  const formatDate = (value?: string) => {
    if (!value) {
      return '—';
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return value;
    }
    return date.toLocaleString();
  };

  const buildEntryKey = (entry: PermissionTemplateEntry) => (
    `${entry.authorityType}:${entry.authority}:${entry.permissionSet}`
  );

  const buildIdentityKey = (entry: PermissionTemplateEntry) => (
    `${entry.authorityType}:${entry.authority}`
  );

  const computeEntryDiff = (
    current: PermissionTemplateEntry[],
    previous: PermissionTemplateEntry[],
  ) => {
    const currentByIdentity = new Map<string, PermissionTemplateEntry>();
    const previousByIdentity = new Map<string, PermissionTemplateEntry>();
    current.forEach((entry) => currentByIdentity.set(buildIdentityKey(entry), entry));
    previous.forEach((entry) => previousByIdentity.set(buildIdentityKey(entry), entry));

    const identities = new Set<string>([
      ...Array.from(currentByIdentity.keys()),
      ...Array.from(previousByIdentity.keys()),
    ]);

    const added: PermissionTemplateEntry[] = [];
    const removed: PermissionTemplateEntry[] = [];
    const changed: Array<{ before: PermissionTemplateEntry; after: PermissionTemplateEntry }> = [];

    identities.forEach((key) => {
      const currentEntry = currentByIdentity.get(key);
      const previousEntry = previousByIdentity.get(key);
      if (currentEntry && !previousEntry) {
        added.push(currentEntry);
      } else if (!currentEntry && previousEntry) {
        removed.push(previousEntry);
      } else if (currentEntry && previousEntry && currentEntry.permissionSet !== previousEntry.permissionSet) {
        changed.push({ before: previousEntry, after: currentEntry });
      }
    });

    const unchanged = current
      .filter((entry) => previous.some((prev) => buildEntryKey(prev) === buildEntryKey(entry)));

    return { added, removed, changed, unchanged };
  };

  const exportCompare = async (format: 'csv' | 'json') => {
    if (!compareCurrent || !comparePrevious || !historyTemplate) {
      toast.info('Select versions to compare');
      return;
    }

    const diff = computeEntryDiff(compareCurrent.entries ?? [], comparePrevious.entries ?? []);
    const hasChanges = diff.added.length > 0 || diff.removed.length > 0 || diff.changed.length > 0;
    if (!hasChanges) {
      toast.info('No differences to export');
      return;
    }

    try {
      setExportingCompare(format);
      const blob = await permissionTemplateService.exportVersionDiff(
        historyTemplate.id,
        comparePrevious.id,
        compareCurrent.id,
        format,
      );
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      const baseNameRaw = historyTemplate.name ? historyTemplate.name.replace(/\s+/g, '-') : 'template';
      const baseName = baseNameRaw.replace(/[^A-Za-z0-9._-]/g, '') || 'template';
      anchor.href = url;
      anchor.download = `${baseName}-diff-${comparePrevious.versionNumber}-to-${compareCurrent.versionNumber}.${format}`;
      anchor.click();
      URL.revokeObjectURL(url);
    } catch {
      toast.error('Failed to export version diff');
    } finally {
      setExportingCompare(null);
    }
  };

  const exportCompareCsv = () => exportCompare('csv');
  const exportCompareJson = () => exportCompare('json');

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
                  <IconButton
                    size="small"
                    aria-label={`View history for ${template.name}`}
                    data-testid={`permission-template-history-${template.id}`}
                    onClick={() => openHistoryDialog(template)}
                  >
                    <History fontSize="small" />
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

      <Dialog open={historyOpen} onClose={() => setHistoryOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle>Template History</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <Typography variant="subtitle2">
              {historyTemplate?.name ?? 'Template'} history
            </Typography>
            <Paper variant="outlined">
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Version</TableCell>
                    <TableCell>Entries</TableCell>
                    <TableCell>Name</TableCell>
                    <TableCell>Description</TableCell>
                    <TableCell>Created By</TableCell>
                    <TableCell>Created At</TableCell>
                    <TableCell align="right">Action</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {versions.map((version, index) => {
                    const previous = versions[index + 1];
                    return (
                    <TableRow key={version.id} hover>
                      <TableCell>{version.versionNumber}</TableCell>
                      <TableCell>{version.entryCount}</TableCell>
                      <TableCell>{version.name}</TableCell>
                      <TableCell>{version.description || '—'}</TableCell>
                      <TableCell>{version.createdBy || '—'}</TableCell>
                      <TableCell>{formatDate(version.createdDate)}</TableCell>
                      <TableCell align="right">
                        <Button
                          size="small"
                          variant="outlined"
                          sx={{ mr: 1, mb: { xs: 1, md: 0 } }}
                          onClick={() => previous && handleCompare(version, previous)}
                          data-testid={`permission-template-compare-${version.id}`}
                          disabled={!previous || compareLoading}
                        >
                          Compare
                        </Button>
                        <Button
                          size="small"
                          variant="outlined"
                          onClick={() => handleRollback(version)}
                          disabled={restoringVersionId === version.id}
                        >
                          Restore
                        </Button>
                      </TableCell>
                    </TableRow>
                  );
                  })}
                  {!loadingVersions && versions.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={7}>
                        <Typography variant="body2" color="text.secondary">
                          No versions available yet.
                        </Typography>
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </Paper>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setHistoryOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>

      <Dialog
        open={compareOpen}
        onClose={() => setCompareOpen(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>Template Version Comparison</DialogTitle>
        <DialogContent>
          {compareCurrent && comparePrevious ? (
            (() => {
              const currentEntries = compareCurrent.entries ?? [];
              const previousEntries = comparePrevious.entries ?? [];
              const diff = computeEntryDiff(currentEntries, previousEntries);
              const summaryItems = [
                diff.added.length > 0 ? `Added ${diff.added.length}` : null,
                diff.removed.length > 0 ? `Removed ${diff.removed.length}` : null,
                diff.changed.length > 0 ? `Changed ${diff.changed.length}` : null,
              ].filter(Boolean) as string[];

              return (
                <Stack spacing={2} sx={{ mt: 1 }}>
                  <Typography variant="subtitle2">
                    Version {comparePrevious.versionNumber} → {compareCurrent.versionNumber}
                  </Typography>
                  <Box>
                    <Typography variant="subtitle2">Change Summary</Typography>
                    <Box display="flex" gap={1} flexWrap="wrap" mt={1}>
                      {(summaryItems.length > 0 ? summaryItems : ['No entry changes']).map((label) => (
                        <Chip key={label} label={label} size="small" variant="outlined" />
                      ))}
                    </Box>
                  </Box>

                  <Paper variant="outlined">
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>Status</TableCell>
                          <TableCell>Authority</TableCell>
                          <TableCell>Type</TableCell>
                          <TableCell>Previous Set</TableCell>
                          <TableCell>Current Set</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {diff.added.map((entry) => (
                          <TableRow key={`added-${buildEntryKey(entry)}`}>
                            <TableCell>
                              <Chip label="Added" size="small" color="success" variant="outlined" />
                            </TableCell>
                            <TableCell>{entry.authority}</TableCell>
                            <TableCell>{entry.authorityType}</TableCell>
                            <TableCell>—</TableCell>
                            <TableCell>{entry.permissionSet}</TableCell>
                          </TableRow>
                        ))}
                        {diff.removed.map((entry) => (
                          <TableRow key={`removed-${buildEntryKey(entry)}`}>
                            <TableCell>
                              <Chip label="Removed" size="small" color="error" variant="outlined" />
                            </TableCell>
                            <TableCell>{entry.authority}</TableCell>
                            <TableCell>{entry.authorityType}</TableCell>
                            <TableCell>{entry.permissionSet}</TableCell>
                            <TableCell>—</TableCell>
                          </TableRow>
                        ))}
                        {diff.changed.map((change) => (
                          <TableRow key={`changed-${buildIdentityKey(change.after)}`}>
                            <TableCell>
                              <Chip label="Changed" size="small" color="warning" variant="outlined" />
                            </TableCell>
                            <TableCell>{change.after.authority}</TableCell>
                            <TableCell>{change.after.authorityType}</TableCell>
                            <TableCell>{change.before.permissionSet}</TableCell>
                            <TableCell>{change.after.permissionSet}</TableCell>
                          </TableRow>
                        ))}
                        {diff.added.length === 0 && diff.removed.length === 0 && diff.changed.length === 0 && (
                          <TableRow>
                            <TableCell colSpan={5}>
                              <Typography variant="body2" color="text.secondary">
                                No permission entry differences.
                              </Typography>
                            </TableCell>
                          </TableRow>
                        )}
                      </TableBody>
                    </Table>
                  </Paper>
                </Stack>
              );
            })()
          ) : (
            <Typography variant="body2" color="text.secondary">
              Select a version to compare.
            </Typography>
          )}
        </DialogContent>
        <DialogActions>
          <Button
            variant="outlined"
            onClick={exportCompareCsv}
            disabled={!compareCurrent || !comparePrevious || exportingCompare !== null}
          >
            {exportingCompare === 'csv' ? 'Exporting...' : 'Export CSV'}
          </Button>
          <Button
            variant="outlined"
            onClick={exportCompareJson}
            disabled={!compareCurrent || !comparePrevious || exportingCompare !== null}
          >
            {exportingCompare === 'json' ? 'Exporting...' : 'Export JSON'}
          </Button>
          <Button onClick={() => setCompareOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default PermissionTemplatesPage;
