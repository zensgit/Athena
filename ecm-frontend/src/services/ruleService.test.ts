import api from './api';
import ruleService, { RULE_UNEXPECTED_RESPONSE_MESSAGE } from './ruleService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    put: jest.fn(),
    patch: jest.fn(),
    delete: jest.fn(),
    downloadFile: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const condition = { type: 'always' };
const action = { type: 'TAG', params: { tag: 'records' }, order: 1 };
const rule = {
  id: 'rule-1',
  name: 'Tag records',
  description: 'Apply a records tag',
  triggerType: 'DOCUMENT_CREATED',
  condition,
  actions: [action],
  priority: 10,
  enabled: true,
  scopeFolderId: null,
  stopOnMatch: false,
};

const page = <T>(content: T[]) => ({
  content,
  totalElements: content.length,
  totalPages: 1,
  number: 0,
  size: 20,
});

const dryRun = {
  scopeFolderId: 'folder-1',
  triggerType: 'DOCUMENT_CREATED',
  found: 2,
  scanned: 2,
  matched: 1,
  processable: 1,
  skipped: 1,
  errors: 0,
  skipReasons: { unsupported_action: 1 },
  results: [
    {
      ruleId: 'rule-1',
      ruleName: 'Tag records',
      priority: 10,
      matched: true,
      processable: true,
      unsupportedActions: [],
      error: null,
    },
  ],
};

const runRecord = {
  runId: 'run-1',
  ruleId: 'rule-1',
  ruleName: 'Tag records',
  documentId: 'doc-1',
  documentName: 'Plan.pdf',
  triggerType: 'DOCUMENT_CREATED',
  idempotencyKey: null,
  conditionMatched: true,
  success: true,
  successfulActions: 1,
  failedActions: 0,
  totalActions: 1,
  errorMessage: null,
  startedAt: '2026-05-18T00:00:00Z',
  completedAt: '2026-05-18T00:00:01Z',
  durationMs: 1000,
  actions: [
    {
      actionType: 'TAG',
      success: true,
      errorMessage: null,
      durationMs: 50,
      details: null,
    },
  ],
};

const expectUnexpectedResponse = async (promise: Promise<unknown>) => {
  await expect(promise).rejects.toThrow(RULE_UNEXPECTED_RESPONSE_MESSAGE);
};

describe('ruleService response shape guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('guards paged rule lists and preserves query params', async () => {
    mockedApi.get
      .mockResolvedValueOnce(page([rule]))
      .mockResolvedValueOnce(page([rule]))
      .mockResolvedValueOnce(page([rule]))
      .mockResolvedValueOnce(page([rule]));

    await expect(ruleService.getAllRules(1, 5)).resolves.toEqual([rule]);
    await expect(ruleService.getMyRules(2, 10)).resolves.toEqual([rule]);
    await expect(ruleService.searchRules('retention', 3, 15)).resolves.toEqual([rule]);
    await expect(ruleService.getScopeFolderRules('folder-1', 4, 20)).resolves.toEqual([rule]);

    expect(mockedApi.get).toHaveBeenNthCalledWith(1, '/rules', {
      params: { page: 1, size: 5, sort: 'priority,asc' },
    });
    expect(mockedApi.get).toHaveBeenNthCalledWith(2, '/rules/my', {
      params: { page: 2, size: 10, sort: 'priority,asc' },
    });
    expect(mockedApi.get).toHaveBeenNthCalledWith(3, '/rules/search', {
      params: { q: 'retention', page: 3, size: 15 },
    });
    expect(mockedApi.get).toHaveBeenNthCalledWith(4, '/rules/folders/folder-1', {
      params: { page: 4, size: 20, sort: 'priority,asc' },
    });
  });

  it('guards rule CRUD and enable responses while preserving endpoints', async () => {
    mockedApi.get.mockResolvedValueOnce(rule);
    mockedApi.post.mockResolvedValueOnce(rule);
    mockedApi.put.mockResolvedValueOnce({ ...rule, name: 'Updated rule' });
    mockedApi.patch.mockResolvedValueOnce({ ...rule, enabled: false });
    mockedApi.delete.mockResolvedValueOnce(undefined);

    await expect(ruleService.getRule('rule-1')).resolves.toEqual(rule);
    await expect(ruleService.createRule(rule)).resolves.toEqual(rule);
    await expect(ruleService.updateRule('rule-1', { ...rule, name: 'Updated rule' })).resolves.toMatchObject({
      name: 'Updated rule',
    });
    await expect(ruleService.setEnabled('rule-1', false)).resolves.toMatchObject({ enabled: false });
    await expect(ruleService.deleteRule('rule-1')).resolves.toBeUndefined();

    expect(mockedApi.get).toHaveBeenCalledWith('/rules/rule-1');
    expect(mockedApi.post).toHaveBeenCalledWith('/rules', rule);
    expect(mockedApi.put).toHaveBeenCalledWith('/rules/rule-1', { ...rule, name: 'Updated rule' });
    expect(mockedApi.patch).toHaveBeenCalledWith('/rules/rule-1/disable');
    expect(mockedApi.delete).toHaveBeenCalledWith('/rules/rule-1');
  });

  it('guards folder dry-run and manual execution ledger responses', async () => {
    mockedApi.post
      .mockResolvedValueOnce({ scopeFolderId: 'folder-1', updated: 1, rules: [rule] })
      .mockResolvedValueOnce(dryRun)
      .mockResolvedValueOnce({ runId: 'run-1', deduplicated: false, deduplicatedFromRunId: null, run: runRecord })
      .mockResolvedValueOnce({ ruleId: 'rule-1', ruleName: 'Tag records', matched: true, message: 'Condition matched' });
    mockedApi.get
      .mockResolvedValueOnce([runRecord])
      .mockResolvedValueOnce([runRecord])
      .mockResolvedValueOnce(runRecord);

    await expect(ruleService.reorderScopeFolderRules('folder-1', { ruleIds: ['rule-1'] })).resolves.toMatchObject({
      updated: 1,
    });
    await expect(ruleService.dryRunScopeFolderRules('folder-1', { triggerType: 'DOCUMENT_CREATED' })).resolves.toEqual(
      dryRun
    );
    await expect(
      ruleService.executeRuleManually('rule-1', { documentId: 'doc-1', idempotencyKey: 'run-key' })
    ).resolves.toMatchObject({ runId: 'run-1' });
    await expect(ruleService.testRule('rule-1', { nodeId: 'doc-1' })).resolves.toMatchObject({ matched: true });
    await expect(ruleService.listRuleExecutions('rule-1', 5)).resolves.toEqual([runRecord]);
    await expect(ruleService.listRuleExecutionTimeline({ success: true, actor: 'admin', limit: 10 })).resolves.toEqual([
      runRecord,
    ]);
    await expect(ruleService.getRuleExecution('run-1')).resolves.toEqual(runRecord);

    expect(mockedApi.post).toHaveBeenNthCalledWith(1, '/rules/folders/folder-1/reorder', { ruleIds: ['rule-1'] });
    expect(mockedApi.post).toHaveBeenNthCalledWith(2, '/rules/folders/folder-1/dry-run', {
      triggerType: 'DOCUMENT_CREATED',
    });
    expect(mockedApi.post).toHaveBeenNthCalledWith(3, '/rules/rule-1/execute', {
      documentId: 'doc-1',
      idempotencyKey: 'run-key',
    });
    expect(mockedApi.post).toHaveBeenNthCalledWith(4, '/rules/rule-1/test', { testData: { nodeId: 'doc-1' } });
    expect(mockedApi.get).toHaveBeenNthCalledWith(1, '/rules/executions', {
      params: { ruleId: 'rule-1', limit: 5 },
    });
    expect(mockedApi.get).toHaveBeenNthCalledWith(2, '/rules/executions/timeline', {
      params: {
        ruleId: undefined,
        documentId: undefined,
        triggerType: undefined,
        success: true,
        from: undefined,
        to: undefined,
        actor: 'admin',
        limit: 10,
      },
    });
    expect(mockedApi.get).toHaveBeenNthCalledWith(3, '/rules/executions/run-1');
  });

  it('guards rule metadata, stats, validation, cron, and audit responses', async () => {
    mockedApi.get
      .mockResolvedValueOnce([{ ...rule, id: 'template-1' }])
      .mockResolvedValueOnce({
        actions: [
          {
            type: 'TAG',
            supported: true,
            requiredParams: ['tag'],
            optionalParams: [],
            constraints: [],
          },
        ],
      })
      .mockResolvedValueOnce({ totalRules: 1, enabledRules: 1 })
      .mockResolvedValueOnce({ ruleId: 'rule-1', successes: 10 })
      .mockResolvedValueOnce([{ eventType: 'RULE_UPDATED', actor: 'admin', details: 'Updated' }]);
    mockedApi.post
      .mockResolvedValueOnce({ valid: true, message: 'Condition is valid', error: null })
      .mockResolvedValueOnce({ valid: true, nextExecutions: ['2026-05-18T00:00:00'], error: null });
    mockedApi.downloadFile.mockResolvedValueOnce(undefined).mockResolvedValueOnce(undefined);

    await expect(ruleService.getTemplates()).resolves.toMatchObject([{ id: 'template-1' }]);
    await expect(ruleService.getActionDefinitions()).resolves.toMatchObject([{ type: 'TAG' }]);
    await expect(ruleService.getStats()).resolves.toEqual({ totalRules: 1, enabledRules: 1 });
    await expect(ruleService.getRuleStats('rule-1')).resolves.toEqual({ ruleId: 'rule-1', successes: 10 });
    await expect(ruleService.listRuleAuditTimeline({ eventType: 'RULE_UPDATED', limit: 5 })).resolves.toEqual([
      { eventType: 'RULE_UPDATED', actor: 'admin', details: 'Updated' },
    ]);
    await expect(ruleService.validateCondition(condition)).resolves.toMatchObject({ valid: true });
    await expect(ruleService.validateCronExpression('0 0 * * *', 'UTC')).resolves.toMatchObject({ valid: true });
    await expect(ruleService.exportRuleExecutionTimelineCsv({ limit: 10 })).resolves.toBeUndefined();
    await expect(ruleService.exportRuleAuditTimelineCsv({ actor: 'admin' })).resolves.toBeUndefined();

    expect(mockedApi.get).toHaveBeenNthCalledWith(1, '/rules/templates');
    expect(mockedApi.get).toHaveBeenNthCalledWith(2, '/rules/actions/definitions');
    expect(mockedApi.get).toHaveBeenNthCalledWith(3, '/rules/stats');
    expect(mockedApi.get).toHaveBeenNthCalledWith(4, '/rules/rule-1/stats');
    expect(mockedApi.get).toHaveBeenNthCalledWith(5, '/rules/executions/audit', {
      params: {
        eventType: 'RULE_UPDATED',
        actor: undefined,
        nodeId: undefined,
        from: undefined,
        to: undefined,
        limit: 5,
      },
    });
    expect(mockedApi.post).toHaveBeenNthCalledWith(1, '/rules/validate', condition);
    expect(mockedApi.post).toHaveBeenNthCalledWith(2, '/rules/validate-cron', {
      cronExpression: '0 0 * * *',
      timezone: 'UTC',
    });
    expect(mockedApi.downloadFile).toHaveBeenNthCalledWith(
      1,
      '/rules/executions/timeline/export',
      expect.stringMatching(/^rule_execution_timeline_.*\.csv$/),
      {
        params: {
          ruleId: undefined,
          documentId: undefined,
          triggerType: undefined,
          success: undefined,
          from: undefined,
          to: undefined,
          actor: undefined,
          limit: 10,
        },
      }
    );
    expect(mockedApi.downloadFile).toHaveBeenNthCalledWith(
      2,
      '/rules/executions/audit/export',
      expect.stringMatching(/^rule_audit_timeline_.*\.csv$/),
      {
        params: {
          eventType: undefined,
          actor: 'admin',
          nodeId: undefined,
          from: undefined,
          to: undefined,
          limit: undefined,
        },
      }
    );
  });

  it('rejects HTML fallback and malformed nested responses', async () => {
    mockedApi.get
      .mockResolvedValueOnce('<!doctype html>')
      .mockResolvedValueOnce({ content: [{ ...rule, actions: [{ params: {} }] }], totalElements: 1, totalPages: 1, number: 0, size: 20 })
      .mockResolvedValueOnce([{ ...runRecord, actions: [{ actionType: 'TAG' }] }])
      .mockResolvedValueOnce({ actions: [{ type: 'TAG', supported: true }] });
    mockedApi.post
      .mockResolvedValueOnce({ ...dryRun, results: [{ ...dryRun.results[0], matched: 'yes' }] })
      .mockResolvedValueOnce({ valid: 'yes', message: 'bad' });

    await expectUnexpectedResponse(ruleService.getRule('rule-1'));
    await expectUnexpectedResponse(ruleService.getAllRules());
    await expectUnexpectedResponse(ruleService.listRuleExecutions());
    await expectUnexpectedResponse(ruleService.getActionDefinitions());
    await expectUnexpectedResponse(ruleService.dryRunScopeFolderRules('folder-1', {}));
    await expectUnexpectedResponse(ruleService.validateCondition(condition));
  });
});
