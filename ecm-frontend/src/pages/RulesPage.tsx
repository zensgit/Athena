import React, { useEffect, useMemo, useState } from 'react';
import {
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  IconButton,
  InputLabel,
  MenuItem,
  Select,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
  Chip,
  Stack,
} from '@mui/material';
import { Add, Delete, Edit, PlayArrow, LocalOffer, Approval } from '@mui/icons-material';
import ruleService, {
  CreateRuleRequest,
  CronValidationResult,
  RuleResponse,
  RuleTemplate,
  TriggerType,
} from 'services/ruleService';
import { toast } from 'react-toastify';

const TRIGGER_TYPES: TriggerType[] = [
  'DOCUMENT_CREATED',
  'DOCUMENT_UPDATED',
  'DOCUMENT_TAGGED',
  'DOCUMENT_MOVED',
  'DOCUMENT_CATEGORIZED',
  'VERSION_CREATED',
  'COMMENT_ADDED',
  'SCHEDULED',
];

const DEFAULT_CONDITION = JSON.stringify({ type: 'ALWAYS_TRUE' }, null, 2);
const DEFAULT_ACTIONS = JSON.stringify(
  [
    {
      type: 'ADD_TAG',
      params: { tagName: 'important' },
      continueOnError: true,
      order: 0,
    },
  ],
  null,
  2
);

const DEFAULT_TEST_DATA = JSON.stringify(
  {
    name: 'invoice.pdf',
    mimeType: 'application/pdf',
    size: 1048576,
  },
  null,
  2
);

// Cron presets for common schedules
const CRON_PRESETS = [
  { label: 'Every minute', value: '0 * * * * *' },
  { label: 'Every 5 minutes', value: '0 */5 * * * *' },
  { label: 'Every 15 minutes', value: '0 */15 * * * *' },
  { label: 'Every hour', value: '0 0 * * * *' },
  { label: 'Every day at midnight', value: '0 0 0 * * *' },
  { label: 'Every day at 9 AM', value: '0 0 9 * * *' },
  { label: 'Every Monday at 9 AM', value: '0 0 9 * * MON' },
  { label: 'Every weekday at 9 AM', value: '0 0 9 * * MON-FRI' },
  { label: 'First day of month at midnight', value: '0 0 0 1 * *' },
];

// Common timezones
const TIMEZONES = [
  'UTC',
  'America/New_York',
  'America/Chicago',
  'America/Denver',
  'America/Los_Angeles',
  'Europe/London',
  'Europe/Paris',
  'Europe/Berlin',
  'Asia/Tokyo',
  'Asia/Shanghai',
  'Asia/Singapore',
  'Australia/Sydney',
];

// Built-in quick templates for common rule patterns
const QUICK_TEMPLATES = {
  autoTagOnUpload: {
    name: 'Auto-tag on Upload',
    description: 'Automatically add a tag when a document is uploaded',
    triggerType: 'DOCUMENT_CREATED' as TriggerType,
    condition: { type: 'ALWAYS_TRUE' },
    actions: [
      {
        type: 'ADD_TAG',
        params: { tagName: 'new-upload' },
        continueOnError: true,
        order: 0,
      },
    ],
  },
  autoApprovalOnUpload: {
    name: 'Auto-start Approval on Upload',
    description: 'Automatically start approval workflow when a document is uploaded',
    triggerType: 'DOCUMENT_CREATED' as TriggerType,
    condition: { type: 'ALWAYS_TRUE' },
    actions: [
      {
        type: 'START_WORKFLOW',
        params: {
          workflowKey: 'documentApproval',
          approvers: ['admin'],
          comment: 'Auto-started by upload rule',
        },
        continueOnError: false,
        order: 0,
      },
    ],
  },
};

const RulesPage: React.FC = () => {
  const [rules, setRules] = useState<RuleResponse[]>([]);
  const [templates, setTemplates] = useState<RuleTemplate[]>([]);
  const [stats, setStats] = useState<Record<string, any> | null>(null);
  const [loading, setLoading] = useState(false);

  const [editorOpen, setEditorOpen] = useState(false);
  const [editingRule, setEditingRule] = useState<RuleResponse | null>(null);
  const [selectedTemplateId, setSelectedTemplateId] = useState<string>('');

  const [form, setForm] = useState<CreateRuleRequest>({
    name: '',
    description: '',
    triggerType: 'DOCUMENT_CREATED',
    priority: 100,
    enabled: true,
    stopOnMatch: false,
    scopeMimeTypes: '',
    scopeFolderId: null,
    condition: { type: 'ALWAYS_TRUE' },
    actions: [],
    // Scheduled rule fields
    cronExpression: '',
    timezone: 'UTC',
    maxItemsPerRun: 200,
    manualBackfillMinutes: undefined,
  });
  const [conditionText, setConditionText] = useState(DEFAULT_CONDITION);
  const [actionsText, setActionsText] = useState(DEFAULT_ACTIONS);
  const [cronValidation, setCronValidation] = useState<CronValidationResult | null>(null);
  const [validatingCron, setValidatingCron] = useState(false);

  const [testOpen, setTestOpen] = useState(false);
  const [testRule, setTestRule] = useState<RuleResponse | null>(null);
  const [testDataText, setTestDataText] = useState(DEFAULT_TEST_DATA);
  const [testResult, setTestResult] = useState<Record<string, any> | null>(null);
  const [testing, setTesting] = useState(false);

  const loadAll = async () => {
    setLoading(true);
    try {
      const [ruleList, templateList, statsData] = await Promise.all([
        ruleService.getAllRules(),
        ruleService.getTemplates().catch(() => []),
        ruleService.getStats().catch(() => null),
      ]);
      setRules(ruleList);
      setTemplates(templateList);
      setStats(statsData);
    } catch (error) {
      toast.error('Failed to load rules');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadAll();
  }, []);

  const templateMap = useMemo(() => {
    const map: Record<string, RuleTemplate> = {};
    templates.forEach((t) => (map[t.id] = t));
    return map;
  }, [templates]);

  const openNewEditor = () => {
    setEditingRule(null);
    setSelectedTemplateId('');
    setForm({
      name: '',
      description: '',
      triggerType: 'DOCUMENT_CREATED',
      priority: 100,
      enabled: true,
      stopOnMatch: false,
      scopeMimeTypes: '',
      scopeFolderId: null,
      condition: { type: 'ALWAYS_TRUE' },
      actions: [],
      cronExpression: '',
      timezone: 'UTC',
      maxItemsPerRun: 200,
      manualBackfillMinutes: undefined,
    });
    setConditionText(DEFAULT_CONDITION);
    setActionsText(DEFAULT_ACTIONS);
    setCronValidation(null);
    setEditorOpen(true);
  };

  const applyQuickTemplate = (templateKey: keyof typeof QUICK_TEMPLATES) => {
    const template = QUICK_TEMPLATES[templateKey];
    setEditingRule(null);
    setSelectedTemplateId('');
    setForm({
      name: template.name,
      description: template.description,
      triggerType: template.triggerType,
      priority: 100,
      enabled: true,
      stopOnMatch: false,
      scopeMimeTypes: '',
      scopeFolderId: null,
      condition: template.condition,
      actions: template.actions,
      cronExpression: '',
      timezone: 'UTC',
      maxItemsPerRun: 200,
      manualBackfillMinutes: undefined,
    });
    setConditionText(JSON.stringify(template.condition, null, 2));
    setActionsText(JSON.stringify(template.actions, null, 2));
    setCronValidation(null);
    setEditorOpen(true);
  };

  const openEditEditor = (rule: RuleResponse) => {
    setEditingRule(rule);
    setSelectedTemplateId('');
    setForm({
      name: rule.name,
      description: rule.description || '',
      triggerType: rule.triggerType,
      priority: rule.priority ?? 100,
      enabled: rule.enabled ?? true,
      stopOnMatch: rule.stopOnMatch ?? false,
      scopeMimeTypes: rule.scopeMimeTypes || '',
      scopeFolderId: rule.scopeFolderId || null,
      condition: rule.condition,
      actions: rule.actions || [],
      cronExpression: rule.cronExpression || '',
      timezone: rule.timezone || 'UTC',
      maxItemsPerRun: rule.maxItemsPerRun ?? 200,
      manualBackfillMinutes: rule.manualBackfillMinutes ?? undefined,
    });
    setConditionText(JSON.stringify(rule.condition ?? { type: 'ALWAYS_TRUE' }, null, 2));
    setActionsText(JSON.stringify(rule.actions ?? [], null, 2));
    setCronValidation(null);
    setEditorOpen(true);
  };

  const applyTemplate = (templateId: string) => {
    setSelectedTemplateId(templateId);
    const template = templateMap[templateId];
    if (!template) return;
    setForm((prev) => ({
      ...prev,
      triggerType: template.triggerType,
      condition: template.condition,
      actions: template.actions,
    }));
    setConditionText(JSON.stringify(template.condition, null, 2));
    setActionsText(JSON.stringify(template.actions, null, 2));
  };

  const parseJsonOrThrow = (text: string, label: string) => {
    try {
      return JSON.parse(text);
    } catch (err) {
      throw new Error(`${label} JSON is invalid`);
    }
  };

  const handleValidateCron = async () => {
    if (!form.cronExpression?.trim()) {
      toast.error('Cron expression is required');
      return;
    }
    setValidatingCron(true);
    try {
      const result = await ruleService.validateCronExpression(
        form.cronExpression,
        form.timezone
      );
      setCronValidation(result);
      if (result.valid) {
        toast.success('Cron expression is valid');
      } else {
        toast.error(result.error || 'Invalid cron expression');
      }
    } catch (err: any) {
      toast.error('Failed to validate cron expression');
    } finally {
      setValidatingCron(false);
    }
  };

  const handleSave = async () => {
    if (!form.name.trim()) {
      toast.error('Rule name is required');
      return;
    }

    // Validate scheduled rule fields
    if (form.triggerType === 'SCHEDULED') {
      if (!form.cronExpression?.trim()) {
        toast.error('Cron expression is required for scheduled rules');
        return;
      }
    }

    const normalizedBackfillMinutes =
      typeof form.manualBackfillMinutes === 'number' && !Number.isNaN(form.manualBackfillMinutes)
        ? form.manualBackfillMinutes
        : undefined;
    if (
      form.triggerType === 'SCHEDULED' &&
      normalizedBackfillMinutes !== undefined &&
      normalizedBackfillMinutes < 1
    ) {
      toast.error('Manual backfill minutes must be at least 1');
      return;
    }

    let condition;
    let actions;
    try {
      condition = parseJsonOrThrow(conditionText, 'Condition');
      actions = parseJsonOrThrow(actionsText, 'Actions');
    } catch (err: any) {
      toast.error(err.message || 'Invalid JSON');
      return;
    }

    const payload: CreateRuleRequest = {
      ...form,
      condition,
      actions,
      scopeFolderId: form.scopeFolderId || null,
      // Only include scheduled fields if trigger type is SCHEDULED
      cronExpression: form.triggerType === 'SCHEDULED' ? form.cronExpression : undefined,
      timezone: form.triggerType === 'SCHEDULED' ? form.timezone : undefined,
      maxItemsPerRun: form.triggerType === 'SCHEDULED' ? form.maxItemsPerRun : undefined,
      manualBackfillMinutes:
        form.triggerType === 'SCHEDULED' ? normalizedBackfillMinutes : undefined,
    };

    try {
      if (editingRule) {
        await ruleService.updateRule(editingRule.id, payload);
        toast.success('Rule updated');
      } else {
        await ruleService.createRule(payload);
        toast.success('Rule created');
      }
      setEditorOpen(false);
      loadAll();
    } catch (error) {
      toast.error(editingRule ? 'Failed to update rule' : 'Failed to create rule');
    }
  };

  const handleValidate = async () => {
    try {
      const condition = parseJsonOrThrow(conditionText, 'Condition');
      const result = await ruleService.validateCondition(condition);
      if (result.valid) {
        toast.success(result.message || 'Condition valid');
      } else {
        toast.error(result.error || result.message || 'Condition invalid');
      }
    } catch (err: any) {
      toast.error(err.message || 'Invalid condition JSON');
    }
  };

  const handleToggleEnabled = async (rule: RuleResponse, enabled: boolean) => {
    try {
      await ruleService.setEnabled(rule.id, enabled);
      setRules((prev) =>
        prev.map((r) => (r.id === rule.id ? { ...r, enabled } : r))
      );
    } catch {
      toast.error('Failed to update rule status');
    }
  };

  const handleDelete = async (rule: RuleResponse) => {
    if (!window.confirm(`Delete rule "${rule.name}"?`)) return;
    try {
      await ruleService.deleteRule(rule.id);
      toast.success('Rule deleted');
      loadAll();
    } catch {
      toast.error('Failed to delete rule');
    }
  };

  const openTestDialog = (rule: RuleResponse) => {
    setTestRule(rule);
    setTestDataText(DEFAULT_TEST_DATA);
    setTestResult(null);
    setTestOpen(true);
  };

  const runTest = async () => {
    if (!testRule) return;
    let testData;
    try {
      testData = parseJsonOrThrow(testDataText, 'Test data');
    } catch (err: any) {
      toast.error(err.message || 'Invalid test data JSON');
      return;
    }
    setTesting(true);
    try {
      const result = await ruleService.testRule(testRule.id, testData);
      setTestResult(result as any);
    } catch {
      toast.error('Rule test failed');
    } finally {
      setTesting(false);
    }
  };

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
        <Typography variant="h5" sx={{ flexGrow: 1 }}>
          Automation Rules
        </Typography>
        <Stack direction="row" spacing={1}>
          <Button
            startIcon={<LocalOffer />}
            variant="outlined"
            size="small"
            onClick={() => applyQuickTemplate('autoTagOnUpload')}
          >
            Auto-tag Template
          </Button>
          <Button
            startIcon={<Approval />}
            variant="outlined"
            size="small"
            onClick={() => applyQuickTemplate('autoApprovalOnUpload')}
          >
            Auto-approval Template
          </Button>
          <Button startIcon={<Add />} variant="contained" onClick={openNewEditor}>
            New Rule
          </Button>
        </Stack>
      </Box>

      {stats && (
        <Stack direction="row" spacing={1} sx={{ mb: 2, flexWrap: 'wrap' }}>
          <Chip label={`Total: ${stats.totalRules ?? 0}`} />
          <Chip color="success" label={`Enabled: ${stats.enabledRules ?? 0}`} />
          <Chip color="default" label={`Disabled: ${stats.disabledRules ?? 0}`} />
          <Chip label={`Executions: ${stats.totalExecutions ?? 0}`} />
          <Chip label={`Failures: ${stats.totalFailures ?? 0}`} />
          {typeof stats.successRate === 'number' && (
            <Chip label={`Success: ${stats.successRate.toFixed(1)}%`} />
          )}
        </Stack>
      )}

      {loading ? (
        <CircularProgress />
      ) : (
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Name</TableCell>
              <TableCell>Trigger</TableCell>
              <TableCell>Priority</TableCell>
              <TableCell>Enabled</TableCell>
              <TableCell>Owner</TableCell>
              <TableCell>Exec / Fail</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rules.map((rule) => (
              <TableRow key={rule.id}>
                <TableCell>
                  <Typography variant="subtitle2">{rule.name}</Typography>
                  {rule.description && (
                    <Typography variant="caption" color="text.secondary">
                      {rule.description}
                    </Typography>
                  )}
                </TableCell>
                <TableCell>{rule.triggerType}</TableCell>
                <TableCell>{rule.priority ?? '-'}</TableCell>
                <TableCell>
                  <Switch
                    checked={!!rule.enabled}
                    onChange={(e) => handleToggleEnabled(rule, e.target.checked)}
                  />
                </TableCell>
                <TableCell>{rule.owner || '-'}</TableCell>
                <TableCell>
                  {(rule.executionCount ?? 0).toString()} / {(rule.failureCount ?? 0).toString()}
                </TableCell>
                <TableCell align="right">
                  <IconButton size="small" onClick={() => openTestDialog(rule)} aria-label="Test">
                    <PlayArrow fontSize="small" />
                  </IconButton>
                  <IconButton size="small" onClick={() => openEditEditor(rule)} aria-label="Edit">
                    <Edit fontSize="small" />
                  </IconButton>
                  <IconButton size="small" onClick={() => handleDelete(rule)} aria-label="Delete">
                    <Delete fontSize="small" />
                  </IconButton>
                </TableCell>
              </TableRow>
            ))}
            {rules.length === 0 && (
              <TableRow>
                <TableCell colSpan={7}>
                  <Typography color="text.secondary">No rules found.</Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      )}

      <Dialog open={editorOpen} onClose={() => setEditorOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle>{editingRule ? 'Edit Rule' : 'New Rule'}</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
          {!editingRule && templates.length > 0 && (
            <FormControl fullWidth>
              <InputLabel id="template-label">Template</InputLabel>
              <Select
                labelId="template-label"
                value={selectedTemplateId}
                label="Template"
                onChange={(e) => applyTemplate(e.target.value as string)}
              >
                <MenuItem value="">None</MenuItem>
                {templates.map((t) => (
                  <MenuItem key={t.id} value={t.id}>
                    {t.name}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          )}

          <TextField
            label="Name"
            value={form.name}
            onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))}
            fullWidth
            required
          />
          <TextField
            label="Description"
            value={form.description}
            onChange={(e) => setForm((prev) => ({ ...prev, description: e.target.value }))}
            fullWidth
            multiline
            minRows={2}
          />

          <Box sx={{ display: 'flex', gap: 2 }}>
            <FormControl fullWidth>
              <InputLabel id="trigger-label">Trigger</InputLabel>
              <Select
                labelId="trigger-label"
                value={form.triggerType}
                label="Trigger"
                onChange={(e) =>
                  setForm((prev) => ({ ...prev, triggerType: e.target.value as TriggerType }))
                }
              >
                {TRIGGER_TYPES.map((tt) => (
                  <MenuItem key={tt} value={tt}>
                    {tt}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            <TextField
              label="Priority"
              type="number"
              value={form.priority ?? 100}
              onChange={(e) =>
                setForm((prev) => ({ ...prev, priority: Number(e.target.value) }))
              }
              fullWidth
            />
          </Box>

          {/* Scheduled Rule Fields - only visible when trigger type is SCHEDULED */}
          {form.triggerType === 'SCHEDULED' && (
            <Box sx={{
              p: 2,
              border: '1px solid',
              borderColor: 'divider',
              borderRadius: 1,
              bgcolor: 'action.hover'
            }}>
              <Typography variant="subtitle2" sx={{ mb: 2 }}>
                Schedule Configuration
              </Typography>

              <Box sx={{ display: 'flex', gap: 2, mb: 2 }}>
                <FormControl sx={{ minWidth: 200 }}>
                  <InputLabel id="cron-preset-label">Cron Preset</InputLabel>
                  <Select
                    labelId="cron-preset-label"
                    value=""
                    label="Cron Preset"
                    onChange={(e) => {
                      if (e.target.value) {
                        setForm((prev) => ({ ...prev, cronExpression: e.target.value as string }));
                        setCronValidation(null);
                      }
                    }}
                  >
                    <MenuItem value="">Custom</MenuItem>
                    {CRON_PRESETS.map((preset) => (
                      <MenuItem key={preset.value} value={preset.value}>
                        {preset.label}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>

                <TextField
                  label="Cron Expression"
                  value={form.cronExpression || ''}
                  onChange={(e) => {
                    setForm((prev) => ({ ...prev, cronExpression: e.target.value }));
                    setCronValidation(null);
                  }}
                  placeholder="0 0 * * * *"
                  helperText="Spring cron format: sec min hour day month weekday"
                  fullWidth
                  required
                />

                <Button
                  variant="outlined"
                  onClick={handleValidateCron}
                  disabled={validatingCron || !form.cronExpression?.trim()}
                  sx={{ minWidth: 120, alignSelf: 'flex-start', mt: 1 }}
                >
                  {validatingCron ? 'Validating...' : 'Validate'}
                </Button>
              </Box>

              <Box sx={{ display: 'flex', gap: 2, mb: 2 }}>
                <FormControl sx={{ minWidth: 200 }}>
                  <InputLabel id="timezone-label">Timezone</InputLabel>
                  <Select
                    labelId="timezone-label"
                    value={form.timezone || 'UTC'}
                    label="Timezone"
                    onChange={(e) =>
                      setForm((prev) => ({ ...prev, timezone: e.target.value as string }))
                    }
                  >
                    {TIMEZONES.map((tz) => (
                      <MenuItem key={tz} value={tz}>
                        {tz}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>

                <TextField
                  label="Max Items Per Run"
                  type="number"
                  value={form.maxItemsPerRun ?? 200}
                  onChange={(e) =>
                    setForm((prev) => ({ ...prev, maxItemsPerRun: Number(e.target.value) }))
                  }
                  helperText="Maximum documents to process per execution"
                  inputProps={{ min: 1, max: 10000 }}
                  sx={{ width: 200 }}
                />

                <TextField
                  label="Manual Trigger Backfill (minutes)"
                  type="number"
                  value={form.manualBackfillMinutes ?? ''}
                  onChange={(e) => {
                    const value = e.target.value;
                    setForm((prev) => ({
                      ...prev,
                      manualBackfillMinutes: value === '' ? undefined : Number(value),
                    }));
                  }}
                  helperText="Optional: include recently modified docs on manual trigger"
                  inputProps={{ min: 1, max: 1440 }}
                  sx={{ width: 260 }}
                />
              </Box>

              {cronValidation && cronValidation.valid && cronValidation.nextExecutions && (
                <Box sx={{ mt: 1 }}>
                  <Typography variant="caption" color="text.secondary">
                    Next scheduled executions:
                  </Typography>
                  <Stack direction="row" spacing={1} sx={{ mt: 0.5, flexWrap: 'wrap' }}>
                    {cronValidation.nextExecutions.slice(0, 5).map((time, idx) => (
                      <Chip key={idx} label={time} size="small" variant="outlined" />
                    ))}
                  </Stack>
                </Box>
              )}

              {editingRule?.nextRunAt && (
                <Box sx={{ mt: 2 }}>
                  <Typography variant="caption" color="text.secondary">
                    Next Run: {new Date(editingRule.nextRunAt).toLocaleString()}
                  </Typography>
                  {editingRule.lastRunAt && (
                    <Typography variant="caption" color="text.secondary" sx={{ ml: 2 }}>
                      Last Run: {new Date(editingRule.lastRunAt).toLocaleString()}
                    </Typography>
                  )}
                </Box>
              )}
            </Box>
          )}

          <Box sx={{ display: 'flex', gap: 2 }}>
            <TextField
              label="Scope MIME Types (comma-separated)"
              value={form.scopeMimeTypes || ''}
              onChange={(e) =>
                setForm((prev) => ({ ...prev, scopeMimeTypes: e.target.value }))
              }
              fullWidth
            />
            <TextField
              label="Scope Folder ID (UUID)"
              value={form.scopeFolderId || ''}
              onChange={(e) =>
                setForm((prev) => ({ ...prev, scopeFolderId: e.target.value || null }))
              }
              fullWidth
            />
          </Box>

          <Box sx={{ display: 'flex', gap: 2 }}>
            <FormControl>
              <Typography variant="body2">Enabled</Typography>
              <Switch
                checked={!!form.enabled}
                onChange={(e) => setForm((prev) => ({ ...prev, enabled: e.target.checked }))}
              />
            </FormControl>
            <FormControl>
              <Typography variant="body2">Stop On Match</Typography>
              <Switch
                checked={!!form.stopOnMatch}
                onChange={(e) => setForm((prev) => ({ ...prev, stopOnMatch: e.target.checked }))}
              />
            </FormControl>
          </Box>

          <TextField
            label="Condition (JSON)"
            value={conditionText}
            onChange={(e) => setConditionText(e.target.value)}
            fullWidth
            multiline
            minRows={6}
            sx={{ fontFamily: 'monospace' }}
          />
          <TextField
            label="Actions (JSON array)"
            value={actionsText}
            onChange={(e) => setActionsText(e.target.value)}
            fullWidth
            multiline
            minRows={6}
            sx={{ fontFamily: 'monospace' }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={handleValidate}>Validate Condition</Button>
          <Box sx={{ flexGrow: 1 }} />
          <Button onClick={() => setEditorOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleSave}>
            Save
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={testOpen} onClose={() => setTestOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Test Rule {testRule?.name}</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
          <TextField
            label="Test Data (JSON)"
            value={testDataText}
            onChange={(e) => setTestDataText(e.target.value)}
            fullWidth
            multiline
            minRows={6}
            sx={{ fontFamily: 'monospace' }}
          />
          {testResult && (
            <Box>
              <Typography variant="subtitle2">
                Result: {testResult.matched ? 'Matched' : 'Not matched'}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {testResult.message}
              </Typography>
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setTestOpen(false)}>Close</Button>
          <Button variant="contained" onClick={runTest} disabled={testing}>
            {testing ? 'Testing…' : 'Run Test'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default RulesPage;
