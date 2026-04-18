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
import {
  Add,
  ArrowDownward,
  ArrowUpward,
  Delete,
  Edit,
  PlayArrow,
  LocalOffer,
  Approval,
} from '@mui/icons-material';
import ruleService, {
  CreateRuleRequest,
  CronValidationResult,
  FolderRuleDryRunResult,
  RuleAuditTimelineFilters,
  RuleAuditTimelineItem,
  RuleExecutionCommandResponse,
  RuleExecutionTimelineFilters,
  RuleRunRecord,
  RuleActionDefinition,
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

type TimelineSuccessFilter = 'ALL' | 'SUCCESS' | 'FAILED';

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

const DEFAULT_FOLDER_DRY_RUN_DATA = JSON.stringify(
  {
    name: 'folder-scope-dry-run.pdf',
    mimeType: 'application/pdf',
    size: 524288,
    path: '/Documents/folder-scope-dry-run.pdf',
  },
  null,
  2
);

const DEFAULT_SCHEDULED_TIMEZONE = 'UTC';
const DEFAULT_SCHEDULED_MAX_ITEMS_PER_RUN = 200;
const MIN_SCHEDULE_INTERVAL_MINUTES = 5;
const MIN_MANUAL_BACKFILL_MINUTES = 1;
const MAX_MANUAL_BACKFILL_MINUTES = 1440;

const buildScheduledDefaults = () => ({
  cronExpression: '',
  timezone: DEFAULT_SCHEDULED_TIMEZONE,
  maxItemsPerRun: DEFAULT_SCHEDULED_MAX_ITEMS_PER_RUN,
  manualBackfillMinutes: undefined as number | undefined,
});

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
  const [actionDefinitions, setActionDefinitions] = useState<RuleActionDefinition[]>([]);
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
    ...buildScheduledDefaults(),
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

  const [scopeFolderId, setScopeFolderId] = useState('');
  const [scopeTriggerType, setScopeTriggerType] = useState<TriggerType>('DOCUMENT_CREATED');
  const [scopeLimit, setScopeLimit] = useState(200);
  const [scopeDryRunDataText, setScopeDryRunDataText] = useState(DEFAULT_FOLDER_DRY_RUN_DATA);
  const [scopeRules, setScopeRules] = useState<RuleResponse[]>([]);
  const [scopeRulesLoading, setScopeRulesLoading] = useState(false);
  const [scopeReorderSaving, setScopeReorderSaving] = useState(false);
  const [scopeDryRunLoading, setScopeDryRunLoading] = useState(false);
  const [scopeDryRunResult, setScopeDryRunResult] = useState<FolderRuleDryRunResult | null>(null);

  const [manualRuleId, setManualRuleId] = useState('');
  const [manualDocumentId, setManualDocumentId] = useState('');
  const [manualTriggerType, setManualTriggerType] = useState<TriggerType>('DOCUMENT_UPDATED');
  const [manualIdempotencyKey, setManualIdempotencyKey] = useState('');
  const [timelineRuleIdFilter, setTimelineRuleIdFilter] = useState('');
  const [timelineActorFilter, setTimelineActorFilter] = useState('');
  const [timelineSuccessFilter, setTimelineSuccessFilter] =
    useState<TimelineSuccessFilter>('ALL');
  const [timelineLimit, setTimelineLimit] = useState(20);
  const [timelineFrom, setTimelineFrom] = useState('');
  const [timelineTo, setTimelineTo] = useState('');
  const [manualExecuting, setManualExecuting] = useState(false);
  const [runLedgerLoading, setRunLedgerLoading] = useState(false);
  const [runLedgerExporting, setRunLedgerExporting] = useState(false);
  const [runLedger, setRunLedger] = useState<RuleRunRecord[]>([]);
  const [lastExecutionResponse, setLastExecutionResponse] = useState<RuleExecutionCommandResponse | null>(null);
  const [auditEventTypeFilter, setAuditEventTypeFilter] = useState('');
  const [auditActorFilter, setAuditActorFilter] = useState('');
  const [auditNodeIdFilter, setAuditNodeIdFilter] = useState('');
  const [auditFrom, setAuditFrom] = useState('');
  const [auditTo, setAuditTo] = useState('');
  const [auditLimit, setAuditLimit] = useState(50);
  const [auditTimelineLoading, setAuditTimelineLoading] = useState(false);
  const [auditTimelineExporting, setAuditTimelineExporting] = useState(false);
  const [auditTimeline, setAuditTimeline] = useState<RuleAuditTimelineItem[]>([]);

  const loadAll = async () => {
    setLoading(true);
    try {
      const [ruleList, templateList, statsData, actionDefinitionList] = await Promise.all([
        ruleService.getAllRules(),
        ruleService.getTemplates().catch(() => []),
        ruleService.getStats().catch(() => null),
        ruleService.getActionDefinitions().catch(() => []),
      ]);
      setRules(ruleList);
      setTemplates(templateList);
      setStats(statsData);
      setActionDefinitions(actionDefinitionList);
    } catch (error) {
      toast.error('Failed to load rules');
    } finally {
      setLoading(false);
    }
  };

  // load once on page mount
  /* eslint-disable react-hooks/exhaustive-deps */
  useEffect(() => {
    loadAll();
    loadRunLedger();
    loadRuleAuditTimeline();
  }, []);
  /* eslint-enable react-hooks/exhaustive-deps */

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
      ...buildScheduledDefaults(),
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
      ...buildScheduledDefaults(),
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

  const updateTriggerType = (nextTriggerType: TriggerType) => {
    setForm((prev) => {
      if (nextTriggerType !== 'SCHEDULED') {
        return {
          ...prev,
          triggerType: nextTriggerType,
          ...buildScheduledDefaults(),
        };
      }
      return {
        ...prev,
        triggerType: nextTriggerType,
        cronExpression: prev.cronExpression || '',
        timezone: prev.timezone || DEFAULT_SCHEDULED_TIMEZONE,
        maxItemsPerRun:
          typeof prev.maxItemsPerRun === 'number' && prev.maxItemsPerRun > 0
            ? prev.maxItemsPerRun
            : DEFAULT_SCHEDULED_MAX_ITEMS_PER_RUN,
        manualBackfillMinutes: prev.manualBackfillMinutes,
      };
    });
    setCronValidation(null);
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
        form.timezone || DEFAULT_SCHEDULED_TIMEZONE
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
    const normalizedMaxItemsPerRun =
      typeof form.maxItemsPerRun === 'number' && !Number.isNaN(form.maxItemsPerRun)
        ? form.maxItemsPerRun
        : DEFAULT_SCHEDULED_MAX_ITEMS_PER_RUN;
    if (
      form.triggerType === 'SCHEDULED' &&
      normalizedBackfillMinutes !== undefined &&
      normalizedBackfillMinutes < MIN_MANUAL_BACKFILL_MINUTES
    ) {
      toast.error(`Manual backfill minutes must be at least ${MIN_MANUAL_BACKFILL_MINUTES}`);
      return;
    }
    if (
      form.triggerType === 'SCHEDULED' &&
      normalizedBackfillMinutes !== undefined &&
      normalizedBackfillMinutes > MAX_MANUAL_BACKFILL_MINUTES
    ) {
      toast.error(`Manual backfill minutes must be ${MAX_MANUAL_BACKFILL_MINUTES} or less`);
      return;
    }
    if (form.triggerType === 'SCHEDULED' && normalizedMaxItemsPerRun < 1) {
      toast.error('Max items per run must be at least 1');
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
      cronExpression:
        form.triggerType === 'SCHEDULED' ? form.cronExpression?.trim() || undefined : undefined,
      timezone:
        form.triggerType === 'SCHEDULED'
          ? form.timezone || DEFAULT_SCHEDULED_TIMEZONE
          : undefined,
      maxItemsPerRun:
        form.triggerType === 'SCHEDULED' ? normalizedMaxItemsPerRun : undefined,
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
    } catch (error: any) {
      // Axios interceptor already toasts server-provided messages.
      if (!error?.response?.data?.message) {
        toast.error(editingRule ? 'Failed to update rule' : 'Failed to create rule');
      }
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

  const loadScopeRules = async () => {
    const folderId = scopeFolderId.trim();
    if (!folderId) {
      toast.error('Scope folder ID is required');
      return;
    }
    setScopeRulesLoading(true);
    try {
      const scoped = await ruleService.getScopeFolderRules(folderId);
      setScopeRules(scoped);
      toast.success(`Loaded ${scoped.length} scoped rules`);
    } catch (error: any) {
      setScopeRules([]);
      if (!error?.response?.data?.message) {
        toast.error('Failed to load scoped rules');
      }
    } finally {
      setScopeRulesLoading(false);
    }
  };

  const moveScopeRule = (index: number, direction: -1 | 1) => {
    setScopeRules((prev) => {
      const targetIndex = index + direction;
      if (targetIndex < 0 || targetIndex >= prev.length) {
        return prev;
      }
      const next = [...prev];
      const temp = next[index];
      next[index] = next[targetIndex];
      next[targetIndex] = temp;
      return next;
    });
  };

  const saveScopeRuleOrder = async () => {
    const folderId = scopeFolderId.trim();
    if (!folderId) {
      toast.error('Scope folder ID is required');
      return;
    }
    if (scopeRules.length === 0) {
      toast.error('No scoped rules loaded');
      return;
    }
    setScopeReorderSaving(true);
    try {
      const response = await ruleService.reorderScopeFolderRules(folderId, {
        ruleIds: scopeRules.map((rule) => rule.id),
        basePriority: 100,
        step: 10,
      });
      setScopeRules(response.rules || []);
      toast.success(`Reordered ${response.updated ?? 0} scoped rules`);
      loadAll();
    } catch (error: any) {
      if (!error?.response?.data?.message) {
        toast.error('Failed to reorder scoped rules');
      }
    } finally {
      setScopeReorderSaving(false);
    }
  };

  const runScopeDryRun = async () => {
    const folderId = scopeFolderId.trim();
    if (!folderId) {
      toast.error('Scope folder ID is required');
      return;
    }
    let testData: Record<string, any> = {};
    try {
      testData = parseJsonOrThrow(scopeDryRunDataText, 'Folder dry-run test data');
    } catch (err: any) {
      toast.error(err.message || 'Invalid folder dry-run JSON');
      return;
    }

    setScopeDryRunLoading(true);
    try {
      const result = await ruleService.dryRunScopeFolderRules(folderId, {
        triggerType: scopeTriggerType,
        testData,
        limit: scopeLimit,
      });
      setScopeDryRunResult(result);
      toast.success(`Dry-run completed: matched ${result.matched}/${result.scanned}`);
    } catch (error: any) {
      if (!error?.response?.data?.message) {
        toast.error('Failed to run scoped dry-run');
      }
    } finally {
      setScopeDryRunLoading(false);
    }
  };

  const buildRunTimelineFilters = (): RuleExecutionTimelineFilters => {
    const normalizedLimit = Number.isFinite(timelineLimit)
      ? Math.max(1, Math.floor(timelineLimit))
      : 20;
    return {
      ruleId: timelineRuleIdFilter.trim() || undefined,
      actor: timelineActorFilter.trim() || undefined,
      success:
        timelineSuccessFilter === 'ALL' ? undefined : timelineSuccessFilter === 'SUCCESS',
      from: timelineFrom || undefined,
      to: timelineTo || undefined,
      limit: normalizedLimit,
    };
  };

  const loadRunLedger = async () => {
    setRunLedgerLoading(true);
    try {
      const rows = await ruleService.listRuleExecutionTimeline(buildRunTimelineFilters());
      setRunLedger(rows || []);
    } catch (error: any) {
      if (!error?.response?.data?.message) {
        toast.error('Failed to load execution ledger');
      }
    } finally {
      setRunLedgerLoading(false);
    }
  };

  const exportRunLedgerCsv = async () => {
    setRunLedgerExporting(true);
    try {
      await ruleService.exportRuleExecutionTimelineCsv(buildRunTimelineFilters());
    } catch (error: any) {
      if (!error?.response?.data?.message) {
        toast.error('Failed to export execution timeline CSV');
      }
    } finally {
      setRunLedgerExporting(false);
    }
  };

  const buildRuleAuditTimelineFilters = (): RuleAuditTimelineFilters => {
    const normalizedLimit = Number.isFinite(auditLimit)
      ? Math.max(1, Math.floor(auditLimit))
      : 50;
    return {
      eventType: auditEventTypeFilter.trim() || undefined,
      actor: auditActorFilter.trim() || undefined,
      nodeId: auditNodeIdFilter.trim() || undefined,
      from: auditFrom || undefined,
      to: auditTo || undefined,
      limit: normalizedLimit,
    };
  };

  const loadRuleAuditTimeline = async () => {
    setAuditTimelineLoading(true);
    try {
      const rows = await ruleService.listRuleAuditTimeline(buildRuleAuditTimelineFilters());
      setAuditTimeline(rows || []);
    } catch (error: any) {
      if (!error?.response?.data?.message) {
        toast.error('Failed to load rule audit timeline');
      }
    } finally {
      setAuditTimelineLoading(false);
    }
  };

  const exportRuleAuditTimelineCsv = async () => {
    setAuditTimelineExporting(true);
    try {
      await ruleService.exportRuleAuditTimelineCsv(buildRuleAuditTimelineFilters());
    } catch (error: any) {
      if (!error?.response?.data?.message) {
        toast.error('Failed to export rule audit timeline CSV');
      }
    } finally {
      setAuditTimelineExporting(false);
    }
  };

  const executeRuleManually = async () => {
    const ruleId = manualRuleId.trim();
    const documentId = manualDocumentId.trim();
    if (!ruleId) {
      toast.error('Rule ID is required');
      return;
    }
    if (!documentId) {
      toast.error('Document ID is required');
      return;
    }

    setManualExecuting(true);
    try {
      const response = await ruleService.executeRuleManually(ruleId, {
        documentId,
        triggerType: manualTriggerType,
        idempotencyKey: manualIdempotencyKey.trim() || undefined,
      });
      setLastExecutionResponse(response);
      if (response.deduplicated) {
        toast.info(`Execution reused from run ${response.deduplicatedFromRunId}`);
      } else {
        toast.success(`Execution created: ${response.runId}`);
      }
      loadRunLedger();
      loadRuleAuditTimeline();
      loadAll();
    } catch (error: any) {
      if (!error?.response?.data?.message) {
        toast.error('Manual execution failed');
      }
    } finally {
      setManualExecuting(false);
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

      <Box
        sx={{
          mb: 2,
          p: 2,
          border: '1px solid',
          borderColor: 'divider',
          borderRadius: 1,
          bgcolor: 'background.paper',
        }}
      >
        <Typography variant="subtitle1" sx={{ mb: 1 }}>
          Folder Rule Set (Dry-run & Reorder)
        </Typography>
        <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap', alignItems: 'center', mb: 1.5 }}>
          <TextField
            label="Scope Folder ID"
            size="small"
            value={scopeFolderId}
            onChange={(e) => setScopeFolderId(e.target.value)}
            sx={{ minWidth: 320 }}
          />
          <FormControl size="small" sx={{ minWidth: 190 }}>
            <InputLabel id="scope-trigger-label">Dry-run Trigger</InputLabel>
            <Select
              labelId="scope-trigger-label"
              label="Dry-run Trigger"
              value={scopeTriggerType}
              onChange={(e) => setScopeTriggerType(e.target.value as TriggerType)}
            >
              {TRIGGER_TYPES.map((tt) => (
                <MenuItem key={`scope-trigger-${tt}`} value={tt}>
                  {tt}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <TextField
            label="Dry-run Limit"
            type="number"
            size="small"
            value={scopeLimit}
            onChange={(e) => setScopeLimit(Number(e.target.value) || 200)}
            inputProps={{ min: 1, max: 1000 }}
            sx={{ width: 140 }}
          />
          <Button variant="outlined" onClick={loadScopeRules} disabled={scopeRulesLoading}>
            {scopeRulesLoading ? 'Loading…' : 'Load Scoped Rules'}
          </Button>
          <Button variant="outlined" onClick={runScopeDryRun} disabled={scopeDryRunLoading}>
            {scopeDryRunLoading ? 'Running…' : 'Run Dry-run'}
          </Button>
          <Button
            variant="contained"
            onClick={saveScopeRuleOrder}
            disabled={scopeReorderSaving || scopeRules.length === 0}
          >
            {scopeReorderSaving ? 'Saving…' : 'Save Order'}
          </Button>
        </Box>
        <TextField
          label="Dry-run Test Data (JSON)"
          value={scopeDryRunDataText}
          onChange={(e) => setScopeDryRunDataText(e.target.value)}
          fullWidth
          multiline
          minRows={4}
          sx={{ fontFamily: 'monospace', mb: 1.5 }}
        />

        {scopeDryRunResult && (
          <Box sx={{ mb: 1.5 }}>
            <Stack direction="row" spacing={1} sx={{ mb: 1, flexWrap: 'wrap' }}>
              <Chip label={`Found ${scopeDryRunResult.found}`} />
              <Chip label={`Scanned ${scopeDryRunResult.scanned}`} />
              <Chip color="success" label={`Matched ${scopeDryRunResult.matched}`} />
              <Chip color="info" label={`Processable ${scopeDryRunResult.processable}`} />
              <Chip label={`Skipped ${scopeDryRunResult.skipped}`} />
              <Chip color={scopeDryRunResult.errors > 0 ? 'warning' : 'default'} label={`Errors ${scopeDryRunResult.errors}`} />
            </Stack>
            {!!scopeDryRunResult.skipReasons &&
              Object.keys(scopeDryRunResult.skipReasons).length > 0 && (
                <Stack direction="row" spacing={1} sx={{ mb: 1, flexWrap: 'wrap' }}>
                  {Object.entries(scopeDryRunResult.skipReasons).map(([reason, count]) => (
                    <Chip
                      key={`scope-skip-${reason}`}
                      size="small"
                      variant="outlined"
                      label={`${reason}: ${count}`}
                    />
                  ))}
                </Stack>
              )}

            {scopeDryRunResult.results?.length > 0 && (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Rule</TableCell>
                    <TableCell>Priority</TableCell>
                    <TableCell>Matched</TableCell>
                    <TableCell>Processable</TableCell>
                    <TableCell>Skip Reason</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {scopeDryRunResult.results.slice(0, 20).map((item) => (
                    <TableRow key={`scope-dryrun-item-${item.ruleId}`}>
                      <TableCell>{item.ruleName}</TableCell>
                      <TableCell>{item.priority ?? '-'}</TableCell>
                      <TableCell>{item.matched ? 'Yes' : 'No'}</TableCell>
                      <TableCell>{item.processable ? 'Yes' : 'No'}</TableCell>
                      <TableCell>{item.skipReason || '-'}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Box>
        )}

        {scopeRules.length > 0 && (
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>#</TableCell>
                <TableCell>Name</TableCell>
                <TableCell>Priority</TableCell>
                <TableCell>Trigger</TableCell>
                <TableCell>Enabled</TableCell>
                <TableCell align="right">Order</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {scopeRules.map((rule, index) => (
                <TableRow key={`scope-rule-${rule.id}`}>
                  <TableCell>{index + 1}</TableCell>
                  <TableCell>{rule.name}</TableCell>
                  <TableCell>{rule.priority ?? '-'}</TableCell>
                  <TableCell>{rule.triggerType}</TableCell>
                  <TableCell>{rule.enabled ? 'Yes' : 'No'}</TableCell>
                  <TableCell align="right">
                    <IconButton
                      size="small"
                      onClick={() => moveScopeRule(index, -1)}
                      disabled={index === 0}
                      aria-label="Move up"
                    >
                      <ArrowUpward fontSize="small" />
                    </IconButton>
                    <IconButton
                      size="small"
                      onClick={() => moveScopeRule(index, 1)}
                      disabled={index === scopeRules.length - 1}
                      aria-label="Move down"
                    >
                      <ArrowDownward fontSize="small" />
                    </IconButton>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Box>

      <Box
        sx={{
          mb: 2,
          p: 2,
          border: '1px solid',
          borderColor: 'divider',
          borderRadius: 1,
          bgcolor: 'background.paper',
        }}
      >
        <Typography variant="subtitle1" sx={{ mb: 1 }}>
          Manual Execution Ledger (Idempotency)
        </Typography>
        <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap', alignItems: 'center', mb: 1.5 }}>
          <TextField
            label="Rule ID"
            size="small"
            value={manualRuleId}
            onChange={(e) => setManualRuleId(e.target.value)}
            sx={{ minWidth: 280 }}
          />
          <TextField
            label="Document ID"
            size="small"
            value={manualDocumentId}
            onChange={(e) => setManualDocumentId(e.target.value)}
            sx={{ minWidth: 280 }}
          />
          <FormControl size="small" sx={{ minWidth: 190 }}>
            <InputLabel id="manual-trigger-label">Trigger</InputLabel>
            <Select
              labelId="manual-trigger-label"
              label="Trigger"
              value={manualTriggerType}
              onChange={(e) => setManualTriggerType(e.target.value as TriggerType)}
            >
              {TRIGGER_TYPES.map((tt) => (
                <MenuItem key={`manual-trigger-${tt}`} value={tt}>
                  {tt}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <TextField
            label="Idempotency Key (optional)"
            size="small"
            value={manualIdempotencyKey}
            onChange={(e) => setManualIdempotencyKey(e.target.value)}
            sx={{ minWidth: 260 }}
          />
          <Button variant="contained" onClick={executeRuleManually} disabled={manualExecuting}>
            {manualExecuting ? 'Executing…' : 'Execute'}
          </Button>
        </Box>

        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
          Timeline Filters
        </Typography>
        <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap', alignItems: 'center', mb: 1.5 }}>
          <TextField
            label="Rule ID Filter"
            size="small"
            value={timelineRuleIdFilter}
            onChange={(e) => setTimelineRuleIdFilter(e.target.value)}
            sx={{ minWidth: 240 }}
          />
          <TextField
            label="Actor Filter"
            size="small"
            value={timelineActorFilter}
            onChange={(e) => setTimelineActorFilter(e.target.value)}
            sx={{ minWidth: 200 }}
          />
          <FormControl size="small" sx={{ minWidth: 170 }}>
            <InputLabel id="timeline-success-label">Success</InputLabel>
            <Select
              labelId="timeline-success-label"
              label="Success"
              value={timelineSuccessFilter}
              onChange={(e) => setTimelineSuccessFilter(e.target.value as TimelineSuccessFilter)}
            >
              <MenuItem value="ALL">ALL</MenuItem>
              <MenuItem value="SUCCESS">SUCCESS</MenuItem>
              <MenuItem value="FAILED">FAILED</MenuItem>
            </Select>
          </FormControl>
          <TextField
            label="Limit"
            type="number"
            size="small"
            value={timelineLimit}
            onChange={(e) => setTimelineLimit(Number(e.target.value) || 20)}
            inputProps={{ min: 1, max: 500 }}
            sx={{ width: 120 }}
          />
          <TextField
            label="From"
            type="datetime-local"
            size="small"
            value={timelineFrom}
            onChange={(e) => setTimelineFrom(e.target.value)}
            InputLabelProps={{ shrink: true }}
            sx={{ minWidth: 220 }}
          />
          <TextField
            label="To"
            type="datetime-local"
            size="small"
            value={timelineTo}
            onChange={(e) => setTimelineTo(e.target.value)}
            InputLabelProps={{ shrink: true }}
            sx={{ minWidth: 220 }}
          />
          <Button variant="outlined" onClick={loadRunLedger} disabled={runLedgerLoading}>
            {runLedgerLoading ? 'Refreshing…' : 'Refresh Timeline'}
          </Button>
          <Button variant="outlined" onClick={exportRunLedgerCsv} disabled={runLedgerExporting}>
            {runLedgerExporting ? 'Exporting…' : 'Export CSV'}
          </Button>
        </Box>

        {lastExecutionResponse && (
          <Stack direction="row" spacing={1} sx={{ mb: 1, flexWrap: 'wrap' }}>
            <Chip label={`Run ${lastExecutionResponse.runId}`} />
            <Chip
              color={lastExecutionResponse.deduplicated ? 'warning' : 'success'}
              label={lastExecutionResponse.deduplicated ? 'Reused' : 'Executed'}
            />
            {lastExecutionResponse.deduplicatedFromRunId && (
              <Chip label={`From ${lastExecutionResponse.deduplicatedFromRunId}`} />
            )}
          </Stack>
        )}

        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Run</TableCell>
              <TableCell>Rule</TableCell>
              <TableCell>Document</TableCell>
              <TableCell>Trigger</TableCell>
              <TableCell>Matched</TableCell>
              <TableCell>Success</TableCell>
              <TableCell>Actions</TableCell>
              <TableCell>Duration</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {runLedger.map((run) => (
              <TableRow key={`rule-run-${run.runId}`}>
                <TableCell>{run.runId.slice(0, 8)}</TableCell>
                <TableCell>{run.ruleName}</TableCell>
                <TableCell>{run.documentName}</TableCell>
                <TableCell>{run.triggerType}</TableCell>
                <TableCell>{run.conditionMatched ? 'Yes' : 'No'}</TableCell>
                <TableCell>{run.success ? 'Yes' : 'No'}</TableCell>
                <TableCell>
                  {run.successfulActions}/{run.totalActions}
                </TableCell>
                <TableCell>{run.durationMs != null ? `${run.durationMs}ms` : '-'}</TableCell>
              </TableRow>
            ))}
            {runLedger.length === 0 && (
              <TableRow>
                <TableCell colSpan={8}>
                  <Typography color="text.secondary">No execution runs found.</Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </Box>

      <Box
        sx={{
          mb: 2,
          p: 2,
          border: '1px solid',
          borderColor: 'divider',
          borderRadius: 1,
          bgcolor: 'background.paper',
        }}
      >
        <Typography variant="subtitle1" sx={{ mb: 1 }}>
          Rule Audit Timeline
        </Typography>
        <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap', alignItems: 'center', mb: 1.5 }}>
          <TextField
            label="Event Type"
            size="small"
            value={auditEventTypeFilter}
            onChange={(e) => setAuditEventTypeFilter(e.target.value)}
            sx={{ minWidth: 220 }}
          />
          <TextField
            label="Actor"
            size="small"
            value={auditActorFilter}
            onChange={(e) => setAuditActorFilter(e.target.value)}
            sx={{ minWidth: 180 }}
          />
          <TextField
            label="Node ID"
            size="small"
            value={auditNodeIdFilter}
            onChange={(e) => setAuditNodeIdFilter(e.target.value)}
            sx={{ minWidth: 260 }}
          />
          <TextField
            label="From"
            type="datetime-local"
            size="small"
            value={auditFrom}
            onChange={(e) => setAuditFrom(e.target.value)}
            InputLabelProps={{ shrink: true }}
            sx={{ minWidth: 220 }}
          />
          <TextField
            label="To"
            type="datetime-local"
            size="small"
            value={auditTo}
            onChange={(e) => setAuditTo(e.target.value)}
            InputLabelProps={{ shrink: true }}
            sx={{ minWidth: 220 }}
          />
          <TextField
            label="Limit"
            type="number"
            size="small"
            value={auditLimit}
            onChange={(e) => setAuditLimit(Number(e.target.value) || 50)}
            inputProps={{ min: 1, max: 1000 }}
            sx={{ width: 120 }}
          />
          <Button variant="outlined" onClick={loadRuleAuditTimeline} disabled={auditTimelineLoading}>
            {auditTimelineLoading ? 'Refreshing…' : 'Refresh Audit'}
          </Button>
          <Button variant="outlined" onClick={exportRuleAuditTimelineCsv} disabled={auditTimelineExporting}>
            {auditTimelineExporting ? 'Exporting…' : 'Export Audit CSV'}
          </Button>
        </Box>

        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Event Time</TableCell>
              <TableCell>Event Type</TableCell>
              <TableCell>Username</TableCell>
              <TableCell>Node</TableCell>
              <TableCell>Details</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {auditTimeline.map((item, index) => {
              const eventTimeRaw = item.eventTime || '';
              const parsedEventTime = eventTimeRaw ? new Date(eventTimeRaw) : null;
              const eventTime = parsedEventTime && !Number.isNaN(parsedEventTime.getTime())
                ? parsedEventTime.toLocaleString()
                : eventTimeRaw || '-';
              const nodeLabel = item.nodeName?.trim()
                ? `${item.nodeName} (${item.nodeId || '-'})`
                : item.nodeId || '-';
              const username = item.username || item.actor || '-';
              return (
                <TableRow key={`rule-audit-${item.eventTime || 'none'}-${item.eventType || 'none'}-${index}`}>
                  <TableCell>{eventTime}</TableCell>
                  <TableCell>{item.eventType || '-'}</TableCell>
                  <TableCell>{username}</TableCell>
                  <TableCell>{nodeLabel}</TableCell>
                  <TableCell>{item.details || '-'}</TableCell>
                </TableRow>
              );
            })}
            {auditTimeline.length === 0 && (
              <TableRow>
                <TableCell colSpan={5}>
                  <Typography color="text.secondary">No rule audit events found.</Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </Box>

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
                <TableCell>
                  <Typography variant="body2">{rule.triggerType}</Typography>
                  {rule.triggerType === 'SCHEDULED' && (
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                      Backfill:{' '}
                      {rule.manualBackfillMinutes ? `${rule.manualBackfillMinutes}m` : 'default'}
                    </Typography>
                  )}
                </TableCell>
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
                onChange={(e) => updateTriggerType(e.target.value as TriggerType)}
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
                  helperText={`Spring cron format: sec min hour day month weekday. Minimum interval: ${MIN_SCHEDULE_INTERVAL_MINUTES} minutes.`}
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
                  helperText="Maximum documents to process per execution. Minimum: 1."
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
          {actionDefinitions.length > 0 && (
            <Box>
              <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 0.75 }}>
                Available Action Definitions
              </Typography>
              <Box display="flex" flexWrap="wrap" gap={0.5}>
                {actionDefinitions.map((definition) => {
                  const required = definition.requiredParams?.length
                    ? ` required: ${definition.requiredParams.join(',')}`
                    : '';
                  const optional = definition.optionalParams?.length
                    ? ` optional: ${definition.optionalParams.join(',')}`
                    : '';
                  const constraints = definition.constraints?.length
                    ? ` (${definition.constraints.join('; ')})`
                    : '';
                  return (
                    <Chip
                      key={`action-definition-${definition.type}`}
                      size="small"
                      variant={definition.supported ? 'outlined' : 'filled'}
                      color={definition.supported ? 'default' : 'warning'}
                      label={`${definition.type}${required}${optional}${constraints}`}
                    />
                  );
                })}
              </Box>
            </Box>
          )}
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
