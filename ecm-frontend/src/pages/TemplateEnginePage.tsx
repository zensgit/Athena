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
import templateService, {
  TemplateDefinitionDto,
  TemplateExecutionResult,
  TemplateMutationRequest,
} from 'services/templateService';
import {
  DEFAULT_INLINE_TEMPLATE,
  DEFAULT_TEMPLATE_MODEL_INPUT,
  parseTemplateModelInput,
  parseTemplateTagsInput,
} from 'utils/templateUtils';

const EMPTY_FORM: TemplateMutationRequest = {
  name: '',
  templatePath: '',
  description: '',
  content: '',
  tags: [],
  active: true,
};

const TemplateEnginePage: React.FC = () => {
  const [templates, setTemplates] = useState<TemplateDefinitionDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedTemplateId, setSelectedTemplateId] = useState<string | null>(null);
  const [form, setForm] = useState<TemplateMutationRequest>(EMPTY_FORM);
  const [tagsInput, setTagsInput] = useState('');
  const [previewModelInput, setPreviewModelInput] = useState(DEFAULT_TEMPLATE_MODEL_INPUT);
  const [previewResult, setPreviewResult] = useState<TemplateExecutionResult | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [inlineTemplate, setInlineTemplate] = useState(DEFAULT_INLINE_TEMPLATE);
  const [inlineResult, setInlineResult] = useState<TemplateExecutionResult | null>(null);
  const [inlineLoading, setInlineLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);

  const loadTemplates = async () => {
    setLoading(true);
    try {
      const data = await templateService.listTemplates();
      setTemplates(data);
      if (!selectedTemplateId && data.length > 0) {
        selectTemplate(data[0]);
      } else if (selectedTemplateId) {
        const selected = data.find((template) => template.id === selectedTemplateId);
        if (selected) {
          selectTemplate(selected);
        }
      }
    } catch {
      toast.error('Failed to load templates');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadTemplates();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const selectTemplate = (template: TemplateDefinitionDto) => {
    setSelectedTemplateId(template.id);
    setForm({
      name: template.name,
      templatePath: template.templatePath,
      description: template.description || '',
      content: template.content,
      tags: template.tags,
      active: template.active,
    });
    setTagsInput(template.tags.join(', '));
  };

  const openCreateDialog = () => {
    setCreateOpen(true);
    setSelectedTemplateId(null);
    setForm(EMPTY_FORM);
    setTagsInput('');
  };

  const handleSave = async () => {
    try {
      const payload: TemplateMutationRequest = {
        ...form,
        tags: parseTemplateTagsInput(tagsInput),
      };
      let saved: TemplateDefinitionDto;
      if (selectedTemplateId) {
        saved = await templateService.updateTemplate(selectedTemplateId, payload);
        toast.success('Template updated');
      } else {
        saved = await templateService.createTemplate(payload);
        toast.success('Template created');
        setCreateOpen(false);
      }
      await loadTemplates();
      setSelectedTemplateId(saved.id);
    } catch (error: any) {
      toast.error(error?.response?.data?.message || 'Failed to save template');
    }
  };

  const handleDelete = async () => {
    if (!selectedTemplateId || !window.confirm('Delete this template?')) {
      return;
    }
    try {
      await templateService.deleteTemplate(selectedTemplateId);
      toast.success('Template deleted');
      setSelectedTemplateId(null);
      setForm(EMPTY_FORM);
      setTagsInput('');
      setPreviewResult(null);
      await loadTemplates();
    } catch {
      toast.error('Failed to delete template');
    }
  };

  const executeSelectedTemplate = async () => {
    if (!selectedTemplateId || !form.templatePath) {
      toast.warn('Select a template first');
      return;
    }
    setPreviewLoading(true);
    try {
      const result = await templateService.executeTemplate({
        templatePath: form.templatePath,
        model: parseTemplateModelInput(previewModelInput),
      });
      setPreviewResult(result);
      toast.success('Template executed');
    } catch (error: any) {
      toast.error(error?.response?.data?.message || error?.message || 'Failed to execute template');
    } finally {
      setPreviewLoading(false);
    }
  };

  const executeInlineTemplate = async () => {
    setInlineLoading(true);
    try {
      const result = await templateService.executeTemplate({
        templateContent: inlineTemplate,
        model: parseTemplateModelInput(previewModelInput),
      });
      setInlineResult(result);
      toast.success('Inline template executed');
    } catch (error: any) {
      toast.error(error?.response?.data?.message || error?.message || 'Failed to execute inline template');
    } finally {
      setInlineLoading(false);
    }
  };

  const renderEditor = () => (
    <Stack spacing={2}>
      <TextField
        label="Name"
        value={form.name}
        onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
        fullWidth
      />
      <TextField
        label="Template Path"
        value={form.templatePath}
        onChange={(event) => setForm((current) => ({ ...current, templatePath: event.target.value }))}
        helperText="Example: mail/welcome.ftl"
        fullWidth
      />
      <TextField
        label="Description"
        value={form.description || ''}
        onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))}
        fullWidth
      />
      <TextField
        label="Tags"
        value={tagsInput}
        onChange={(event) => setTagsInput(event.target.value)}
        helperText="Comma-separated"
        fullWidth
      />
      <TextField
        label="Template Content"
        value={form.content}
        onChange={(event) => setForm((current) => ({ ...current, content: event.target.value }))}
        fullWidth
        multiline
        minRows={10}
      />
    </Stack>
  );

  return (
    <Box maxWidth={1280}>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
        <Box>
          <Typography variant="h5">Template Engine</Typography>
          <Typography variant="body2" color="text.secondary">
            Manage stored FreeMarker templates and preview execution against JSON models.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button variant="outlined" startIcon={<Refresh />} onClick={() => void loadTemplates()} disabled={loading}>
            Refresh
          </Button>
          <Button variant="contained" startIcon={<Add />} onClick={openCreateDialog}>
            New Template
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
              Managed Templates
            </Typography>
            <Stack spacing={1}>
              {templates.map((template) => (
                <Card
                  key={template.id}
                  variant="outlined"
                  sx={{
                    cursor: 'pointer',
                    borderColor: selectedTemplateId === template.id ? 'primary.main' : undefined,
                    borderWidth: selectedTemplateId === template.id ? 2 : 1,
                  }}
                  onClick={() => selectTemplate(template)}
                >
                  <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
                    <Stack spacing={0.5}>
                      <Box display="flex" justifyContent="space-between" alignItems="center">
                        <Typography variant="subtitle2">{template.name}</Typography>
                        <Chip size="small" label={template.active ? 'Active' : 'Disabled'} color={template.active ? 'success' : 'default'} />
                      </Box>
                      <Typography variant="caption" color="text.secondary">
                        {template.templatePath}
                      </Typography>
                      {template.tags.length > 0 && (
                        <Box>
                          {template.tags.map((tag) => (
                            <Chip key={tag} size="small" label={tag} sx={{ mr: 0.5, mt: 0.5 }} />
                          ))}
                        </Box>
                      )}
                    </Stack>
                  </CardContent>
                </Card>
              ))}
              {templates.length === 0 && (
                <Paper variant="outlined" sx={{ p: 3, textAlign: 'center' }}>
                  <Typography color="text.secondary">No templates defined.</Typography>
                </Paper>
              )}
            </Stack>
          </Paper>

          <Stack spacing={2} flex={1}>
            <Paper variant="outlined" sx={{ p: 2 }}>
              <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
                <Box>
                  <Typography variant="subtitle1">Template Editor</Typography>
                  <Typography variant="caption" color="text.secondary">
                    Stored templates execute by path through `/api/v1/templates/execute`.
                  </Typography>
                </Box>
                <Stack direction="row" spacing={1}>
                  <Button variant="contained" startIcon={<Save />} onClick={() => void handleSave()}>
                    {selectedTemplateId ? 'Save Changes' : 'Create Template'}
                  </Button>
                  <Button
                    variant="outlined"
                    color="error"
                    startIcon={<Delete />}
                    onClick={() => void handleDelete()}
                    disabled={!selectedTemplateId}
                  >
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
                  value={previewModelInput}
                  onChange={(event) => setPreviewModelInput(event.target.value)}
                  multiline
                  minRows={8}
                  fullWidth
                />
                <Stack direction="row" spacing={1}>
                  <Button
                    variant="contained"
                    startIcon={<PlayArrow />}
                    onClick={() => void executeSelectedTemplate()}
                    disabled={!selectedTemplateId || previewLoading}
                  >
                    Execute Stored Template
                  </Button>
                  <Button
                    variant="outlined"
                    startIcon={<PlayArrow />}
                    onClick={() => void executeInlineTemplate()}
                    disabled={inlineLoading}
                  >
                    Execute Inline Template
                  </Button>
                </Stack>
                <Divider />
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    Stored Template Output
                  </Typography>
                  <Paper variant="outlined" sx={{ p: 2, minHeight: 120, bgcolor: 'grey.50', whiteSpace: 'pre-wrap' }}>
                    <Typography variant="body2">
                      {previewResult?.rendered || 'Execute a stored template to preview output.'}
                    </Typography>
                  </Paper>
                </Box>
                <TextField
                  label="Inline Template"
                  value={inlineTemplate}
                  onChange={(event) => setInlineTemplate(event.target.value)}
                  multiline
                  minRows={6}
                  fullWidth
                />
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    Inline Template Output
                  </Typography>
                  <Paper variant="outlined" sx={{ p: 2, minHeight: 120, bgcolor: 'grey.50', whiteSpace: 'pre-wrap' }}>
                    <Typography variant="body2">
                      {inlineResult?.rendered || 'Execute an inline template string to preview output.'}
                    </Typography>
                  </Paper>
                </Box>
              </Stack>
            </Paper>
          </Stack>
        </Stack>
      )}

      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>New Template</DialogTitle>
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

export default TemplateEnginePage;
