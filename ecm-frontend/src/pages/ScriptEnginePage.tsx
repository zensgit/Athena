import React, { useEffect, useState } from 'react';
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { Add, Delete, PlayArrow, Refresh, Save } from '@mui/icons-material';
import { toast } from 'react-toastify';
import scriptService, {
  ScriptDefinitionDto,
  ScriptExecutionResult,
  ScriptMutationRequest,
} from 'services/scriptService';
import { DEFAULT_TEMPLATE_MODEL_INPUT, parseTemplateModelInput } from 'utils/templateUtils';
import { DEFAULT_INLINE_SCRIPT, formatScriptResult, parseScriptTagsInput } from 'utils/scriptUtils';

const EMPTY_FORM: ScriptMutationRequest = {
  name: '',
  scriptPath: '',
  description: '',
  content: '',
  tags: [],
  active: true,
};

const ScriptEnginePage: React.FC = () => {
  const [scripts, setScripts] = useState<ScriptDefinitionDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedScriptId, setSelectedScriptId] = useState<string | null>(null);
  const [form, setForm] = useState<ScriptMutationRequest>(EMPTY_FORM);
  const [tagsInput, setTagsInput] = useState('');
  const [modelInput, setModelInput] = useState(DEFAULT_TEMPLATE_MODEL_INPUT);
  const [inlineScript, setInlineScript] = useState(DEFAULT_INLINE_SCRIPT);
  const [storedResult, setStoredResult] = useState<ScriptExecutionResult | null>(null);
  const [inlineResult, setInlineResult] = useState<ScriptExecutionResult | null>(null);
  const [storedLoading, setStoredLoading] = useState(false);
  const [inlineLoading, setInlineLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);

  const selectScript = (script: ScriptDefinitionDto) => {
    setSelectedScriptId(script.id);
    setForm({
      name: script.name,
      scriptPath: script.scriptPath,
      description: script.description || '',
      content: script.content,
      tags: script.tags,
      active: script.active,
    });
    setTagsInput(script.tags.join(', '));
  };

  const loadScripts = async () => {
    setLoading(true);
    try {
      const data = await scriptService.listScripts();
      setScripts(data);
      if (!selectedScriptId && data.length > 0) {
        selectScript(data[0]);
      } else if (selectedScriptId) {
        const selected = data.find((script) => script.id === selectedScriptId);
        if (selected) {
          selectScript(selected);
        }
      }
    } catch {
      toast.error('Failed to load scripts');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadScripts();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const openCreateDialog = () => {
    setCreateOpen(true);
    setSelectedScriptId(null);
    setForm(EMPTY_FORM);
    setTagsInput('');
  };

  const handleSave = async () => {
    try {
      const payload: ScriptMutationRequest = {
        ...form,
        tags: parseScriptTagsInput(tagsInput),
      };
      let saved: ScriptDefinitionDto;
      if (selectedScriptId) {
        saved = await scriptService.updateScript(selectedScriptId, payload);
        toast.success('Script updated');
      } else {
        saved = await scriptService.createScript(payload);
        toast.success('Script created');
        setCreateOpen(false);
      }
      await loadScripts();
      setSelectedScriptId(saved.id);
    } catch (error: any) {
      toast.error(error?.response?.data?.message || 'Failed to save script');
    }
  };

  const handleDelete = async () => {
    if (!selectedScriptId || !window.confirm('Delete this script?')) {
      return;
    }
    try {
      await scriptService.deleteScript(selectedScriptId);
      toast.success('Script deleted');
      setSelectedScriptId(null);
      setForm(EMPTY_FORM);
      setTagsInput('');
      setStoredResult(null);
      await loadScripts();
    } catch {
      toast.error('Failed to delete script');
    }
  };

  const executeStoredScript = async () => {
    if (!selectedScriptId || !form.scriptPath) {
      toast.warn('Select a script first');
      return;
    }
    setStoredLoading(true);
    try {
      const result = await scriptService.executeScript({
        scriptPath: form.scriptPath,
        model: parseTemplateModelInput(modelInput),
        timeoutMs: 2000,
      });
      setStoredResult(result);
      toast.success('Stored script executed');
    } catch (error: any) {
      toast.error(error?.response?.data?.message || error?.message || 'Failed to execute stored script');
    } finally {
      setStoredLoading(false);
    }
  };

  const executeInlineScript = async () => {
    setInlineLoading(true);
    try {
      const result = await scriptService.executeScript({
        scriptContent: inlineScript,
        model: parseTemplateModelInput(modelInput),
        timeoutMs: 2000,
      });
      setInlineResult(result);
      toast.success('Inline script executed');
    } catch (error: any) {
      toast.error(error?.response?.data?.message || error?.message || 'Failed to execute inline script');
    } finally {
      setInlineLoading(false);
    }
  };

  const renderEditor = () => (
    <Stack spacing={2}>
      <TextField label="Name" value={form.name} onChange={(e) => setForm((c) => ({ ...c, name: e.target.value }))} fullWidth />
      <TextField
        label="Script Path"
        value={form.scriptPath}
        onChange={(e) => setForm((c) => ({ ...c, scriptPath: e.target.value }))}
        helperText="Example: scripts/notify-site.js"
        fullWidth
      />
      <TextField
        label="Description"
        value={form.description || ''}
        onChange={(e) => setForm((c) => ({ ...c, description: e.target.value }))}
        fullWidth
      />
      <TextField label="Tags" value={tagsInput} onChange={(e) => setTagsInput(e.target.value)} helperText="Comma-separated" fullWidth />
      <TextField
        label="Script Content"
        value={form.content}
        onChange={(e) => setForm((c) => ({ ...c, content: e.target.value }))}
        multiline
        minRows={10}
        fullWidth
      />
    </Stack>
  );

  const renderResult = (title: string, result: ScriptExecutionResult | null, fallback: string) => (
    <Box>
      <Typography variant="caption" color="text.secondary">
        {title}
      </Typography>
      <Paper variant="outlined" sx={{ p: 2, minHeight: 120, bgcolor: 'grey.50', whiteSpace: 'pre-wrap' }}>
        <Typography variant="body2">{result ? formatScriptResult(result.result) : fallback}</Typography>
      </Paper>
      <Typography variant="caption" color="text.secondary" display="block" mt={1}>
        Logs
      </Typography>
      <Paper variant="outlined" sx={{ p: 2, minHeight: 80, bgcolor: 'grey.50', whiteSpace: 'pre-wrap' }}>
        <Typography variant="body2">{result?.logs?.length ? result.logs.join('\n') : 'No log output.'}</Typography>
      </Paper>
    </Box>
  );

  return (
    <Box maxWidth={1280}>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
        <Box>
          <Typography variant="h5">Script Engine</Typography>
          <Typography variant="body2" color="text.secondary">
            Manage admin-only GraalJS scripts and execute them in a host-restricted sandbox.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button variant="outlined" startIcon={<Refresh />} onClick={() => void loadScripts()} disabled={loading}>
            Refresh
          </Button>
          <Button variant="contained" startIcon={<Add />} onClick={openCreateDialog}>
            New Script
          </Button>
        </Stack>
      </Box>

      {loading ? (
        <Box display="flex" justifyContent="center" p={6}>
          <CircularProgress />
        </Box>
      ) : (
        <Stack direction={{ xs: 'column', lg: 'row' }} spacing={2} alignItems="stretch">
          <Paper variant="outlined" sx={{ flex: '0 0 340px', p: 2 }}>
            <Typography variant="subtitle1" gutterBottom>
              Managed Scripts
            </Typography>
            <Stack spacing={1}>
              {scripts.map((script) => (
                <Card
                  key={script.id}
                  variant="outlined"
                  sx={{
                    cursor: 'pointer',
                    borderColor: selectedScriptId === script.id ? 'primary.main' : undefined,
                    borderWidth: selectedScriptId === script.id ? 2 : 1,
                  }}
                  onClick={() => selectScript(script)}
                >
                  <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
                    <Stack spacing={0.5}>
                      <Box display="flex" justifyContent="space-between" alignItems="center">
                        <Typography variant="subtitle2">{script.name}</Typography>
                        <Chip size="small" label={script.active ? 'Active' : 'Disabled'} color={script.active ? 'success' : 'default'} />
                      </Box>
                      <Typography variant="caption" color="text.secondary">
                        {script.scriptPath}
                      </Typography>
                      {script.tags.length > 0 && (
                        <Box>
                          {script.tags.map((tag) => (
                            <Chip key={tag} size="small" label={tag} sx={{ mr: 0.5, mt: 0.5 }} />
                          ))}
                        </Box>
                      )}
                    </Stack>
                  </CardContent>
                </Card>
              ))}
              {scripts.length === 0 && (
                <Paper variant="outlined" sx={{ p: 3, textAlign: 'center' }}>
                  <Typography color="text.secondary">No scripts defined.</Typography>
                </Paper>
              )}
            </Stack>
          </Paper>

          <Stack spacing={2} flex={1}>
            <Paper variant="outlined" sx={{ p: 2 }}>
              <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
                <Box>
                  <Typography variant="subtitle1">Script Editor</Typography>
                  <Typography variant="caption" color="text.secondary">
                    Stored scripts execute by path through `/api/v1/scripts/execute`.
                  </Typography>
                </Box>
                <Stack direction="row" spacing={1}>
                  <Button variant="contained" startIcon={<Save />} onClick={() => void handleSave()}>
                    {selectedScriptId ? 'Save Changes' : 'Create Script'}
                  </Button>
                  <Button variant="outlined" color="error" startIcon={<Delete />} onClick={() => void handleDelete()} disabled={!selectedScriptId}>
                    Delete
                  </Button>
                </Stack>
              </Box>
              {renderEditor()}
            </Paper>

            <Paper variant="outlined" sx={{ p: 2 }}>
              <Typography variant="subtitle1" gutterBottom>
                Execution Preview
              </Typography>
              <Stack spacing={2}>
                <TextField
                  label="Model JSON"
                  value={modelInput}
                  onChange={(e) => setModelInput(e.target.value)}
                  multiline
                  minRows={8}
                  fullWidth
                />
                <Stack direction="row" spacing={1}>
                  <Button variant="contained" startIcon={<PlayArrow />} onClick={() => void executeStoredScript()} disabled={!selectedScriptId || storedLoading}>
                    Execute Stored Script
                  </Button>
                  <Button variant="outlined" startIcon={<PlayArrow />} onClick={() => void executeInlineScript()} disabled={inlineLoading}>
                    Execute Inline Script
                  </Button>
                </Stack>
                <Divider />
                {renderResult('Stored Script Output', storedResult, 'Execute a stored script to preview output and logs.')}
                <TextField
                  label="Inline Script"
                  value={inlineScript}
                  onChange={(e) => setInlineScript(e.target.value)}
                  multiline
                  minRows={8}
                  fullWidth
                />
                {renderResult('Inline Script Output', inlineResult, 'Execute an inline script string to preview output and logs.')}
              </Stack>
            </Paper>
          </Stack>
        </Stack>
      )}

      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>New Script</DialogTitle>
        <DialogContent>
          <Box mt={1}>{renderEditor()}</Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void handleSave()}>
            Create
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default ScriptEnginePage;
