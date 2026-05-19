import api from './api';
import opsRecoveryService, {
  OPS_RECOVERY_UNEXPECTED_RESPONSE_MESSAGE,
  RecoveryHistoryExportAsyncCreateResponse,
  RecoveryHistoryExportAsyncTaskStatus,
  RecoveryHistoryExportAsyncTaskList,
  RecoveryHistoryExportAsyncTaskSummary,
  RecoveryHistoryExportAsyncRetryTerminalResponse,
  RecoveryHistoryExportAsyncRetryTerminalDryRunResponse,
  RecoveryHistoryExportAsyncTaskCancelActiveResponse,
  RecoveryHistoryExportAsyncTaskCleanupResponse,
} from './opsRecoveryService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const HTML_FALLBACK = '<!doctype html><html><body>app shell</body></html>';

const validCreate: RecoveryHistoryExportAsyncCreateResponse = {
  taskId: 't1',
  exportType: 'HISTORY',
  status: 'QUEUED',
  createdAt: '2026-05-18T00:00:00Z',
};

const validTaskStatus: RecoveryHistoryExportAsyncTaskStatus = {
  taskId: 't1',
  exportType: 'HISTORY',
  status: 'RUNNING',
  error: null,
  createdAt: null,
  finishedAt: null,
  filename: null,
};

const validTaskList: RecoveryHistoryExportAsyncTaskList = {
  count: 1,
  items: [validTaskStatus],
};

const validSummary: RecoveryHistoryExportAsyncTaskSummary = {
  totalCount: 3,
  queuedCount: 1,
  runningCount: 0,
  completedCount: 2,
  cancelledCount: 0,
  failedCount: 0,
  activeCount: 1,
  terminalCount: 2,
};

const validRetryTerminal: RecoveryHistoryExportAsyncRetryTerminalResponse = {
  requested: 2,
  retried: 1,
  reused: 1,
  skipped: 0,
  failed: 0,
  limit: 20,
  message: 'ok',
  results: [{ sourceTaskId: 's1', outcome: 'RETRIED' }],
};

const validDryRun: RecoveryHistoryExportAsyncRetryTerminalDryRunResponse = {
  requested: 2,
  retryable: 1,
  skipped: 1,
  limit: 20,
  message: 'ok',
  results: [{ sourceTaskId: 's1', outcome: 'RETRYABLE' }],
};

const validCancelActive: RecoveryHistoryExportAsyncTaskCancelActiveResponse = {
  cancelledCount: 1,
  remainingActiveCount: 0,
  message: 'done',
};

const validCleanup: RecoveryHistoryExportAsyncTaskCleanupResponse = {
  deletedCount: 2,
  remainingCount: 5,
  message: 'cleaned',
};

beforeEach(() => {
  jest.clearAllMocks();
});

describe('opsRecoveryService async export-tail response shape guards', () => {
  it('returns valid DTOs unchanged across all twelve async-tail methods', async () => {
    mockedApi.post.mockResolvedValueOnce(validCreate);
    await expect(
      opsRecoveryService.startHistoryExportAsync({ exportType: 'HISTORY' })
    ).resolves.toEqual(validCreate);

    mockedApi.get.mockResolvedValueOnce(validTaskList);
    await expect(opsRecoveryService.listHistoryExportAsyncTasks()).resolves.toEqual(validTaskList);

    mockedApi.get.mockResolvedValueOnce(validTaskStatus);
    await expect(opsRecoveryService.getHistoryExportAsyncTask('t1')).resolves.toEqual(validTaskStatus);

    mockedApi.get.mockResolvedValueOnce(validSummary);
    await expect(opsRecoveryService.getHistoryExportAsyncTaskSummary()).resolves.toEqual(validSummary);

    mockedApi.get.mockResolvedValueOnce(validSummary);
    await expect(
      opsRecoveryService.getHistoryExportAsyncTaskSummaryFiltered('HISTORY', 'FAILED')
    ).resolves.toEqual(validSummary);

    mockedApi.post.mockResolvedValueOnce(validTaskStatus);
    await expect(opsRecoveryService.cancelHistoryExportAsyncTask('t1')).resolves.toEqual(validTaskStatus);

    mockedApi.post.mockResolvedValueOnce(validCreate);
    await expect(opsRecoveryService.retryHistoryExportAsyncTask('t1')).resolves.toEqual(validCreate);

    mockedApi.post.mockResolvedValueOnce(validRetryTerminal);
    await expect(opsRecoveryService.retryTerminalHistoryExportAsyncTasks()).resolves.toEqual(
      validRetryTerminal
    );

    mockedApi.post.mockResolvedValueOnce(validDryRun);
    await expect(opsRecoveryService.dryRunRetryTerminalHistoryExportAsyncTasks()).resolves.toEqual(
      validDryRun
    );

    mockedApi.post.mockResolvedValueOnce(validRetryTerminal);
    await expect(
      opsRecoveryService.retryTerminalHistoryExportAsyncTasksByTaskIds(['s1'])
    ).resolves.toEqual(validRetryTerminal);

    mockedApi.post.mockResolvedValueOnce(validCancelActive);
    await expect(opsRecoveryService.cancelActiveHistoryExportAsyncTasks()).resolves.toEqual(
      validCancelActive
    );

    mockedApi.post.mockResolvedValueOnce(validCleanup);
    await expect(opsRecoveryService.cleanupHistoryExportAsyncTasks()).resolves.toEqual(validCleanup);
  });

  it('throws the shared sentinel on HTML fallback and null for representative GET/POST/array methods', async () => {
    for (const body of [HTML_FALLBACK, null]) {
      mockedApi.get.mockResolvedValueOnce(body);
      await expect(opsRecoveryService.getHistoryExportAsyncTask('t1')).rejects.toThrow(
        OPS_RECOVERY_UNEXPECTED_RESPONSE_MESSAGE
      );

      mockedApi.post.mockResolvedValueOnce(body);
      await expect(opsRecoveryService.startHistoryExportAsync({})).rejects.toThrow(
        OPS_RECOVERY_UNEXPECTED_RESPONSE_MESSAGE
      );

      mockedApi.get.mockResolvedValueOnce(body);
      await expect(opsRecoveryService.listHistoryExportAsyncTasks()).rejects.toThrow(
        OPS_RECOVERY_UNEXPECTED_RESPONSE_MESSAGE
      );
    }
  });

  it('throws on missing required fields, wrong types, and malformed nested array entries', async () => {
    // missing required createdAt
    mockedApi.post.mockResolvedValueOnce({ taskId: 't1', exportType: 'HISTORY', status: 'QUEUED' });
    await expect(opsRecoveryService.startHistoryExportAsync({})).rejects.toThrow(
      OPS_RECOVERY_UNEXPECTED_RESPONSE_MESSAGE
    );

    // wrong numeric type on summary
    mockedApi.get.mockResolvedValueOnce({ ...validSummary, totalCount: '3' });
    await expect(opsRecoveryService.getHistoryExportAsyncTaskSummary()).rejects.toThrow(
      OPS_RECOVERY_UNEXPECTED_RESPONSE_MESSAGE
    );

    // task list with a non-object item
    mockedApi.get.mockResolvedValueOnce({ count: 1, items: [validTaskStatus, null] });
    await expect(opsRecoveryService.listHistoryExportAsyncTasks()).rejects.toThrow(
      OPS_RECOVERY_UNEXPECTED_RESPONSE_MESSAGE
    );

    // retry-terminal results entry with a wrong-typed field
    mockedApi.post.mockResolvedValueOnce({
      ...validRetryTerminal,
      results: [{ sourceTaskId: 42 }],
    });
    await expect(opsRecoveryService.retryTerminalHistoryExportAsyncTasks()).rejects.toThrow(
      OPS_RECOVERY_UNEXPECTED_RESPONSE_MESSAGE
    );

    // paging present but malformed
    mockedApi.get.mockResolvedValueOnce({
      count: 0,
      items: [],
      paging: { skipCount: 0, maxItems: 20, totalItems: 'x', hasMoreItems: false },
    });
    await expect(opsRecoveryService.listHistoryExportAsyncTasks()).rejects.toThrow(
      OPS_RECOVERY_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('accepts omitted optional/nullable fields and an optional reasonBreakdown', async () => {
    // TaskStatus with only required fields, all optionals omitted
    mockedApi.get.mockResolvedValueOnce({
      taskId: 't1',
      exportType: 'HISTORY',
      status: 'COMPLETED',
      error: null,
      createdAt: null,
      finishedAt: null,
      filename: null,
    });
    await expect(opsRecoveryService.getHistoryExportAsyncTask('t1')).resolves.toBeDefined();

    // dry-run with reasonBreakdown present and valid
    const dryRunWithBreakdown = {
      ...validDryRun,
      reasonBreakdown: [{ reasonCode: 'LOCKED', outcome: 'SKIPPED', count: 2 }],
    };
    mockedApi.post.mockResolvedValueOnce(dryRunWithBreakdown);
    await expect(
      opsRecoveryService.dryRunRetryTerminalHistoryExportAsyncTasks()
    ).resolves.toEqual(dryRunWithBreakdown);

    // dry-run with reasonBreakdown present but malformed -> throws
    mockedApi.post.mockResolvedValueOnce({ ...validDryRun, reasonBreakdown: [{ count: 'x' }] });
    await expect(
      opsRecoveryService.dryRunRetryTerminalHistoryExportAsyncTasks()
    ).rejects.toThrow(OPS_RECOVERY_UNEXPECTED_RESPONSE_MESSAGE);
  });

  describe('request snapshot lightweight deep validation (gate ruling)', () => {
    it('accepts undefined, null, and a well-typed request', async () => {
      mockedApi.post.mockResolvedValueOnce(validCreate); // request absent
      await expect(opsRecoveryService.startHistoryExportAsync({})).resolves.toEqual(validCreate);

      mockedApi.post.mockResolvedValueOnce({ ...validCreate, request: null });
      await expect(opsRecoveryService.startHistoryExportAsync({})).resolves.toBeDefined();

      mockedApi.get.mockResolvedValueOnce({
        ...validTaskStatus,
        request: { mode: 'BACKUP', limit: 5, actor: null, compareActorSort: null },
      });
      await expect(opsRecoveryService.getHistoryExportAsyncTask('t1')).resolves.toBeDefined();
    });

    it('rejects a request with an object where a string field is expected', async () => {
      mockedApi.post.mockResolvedValueOnce({ ...validCreate, request: { mode: {} } });
      await expect(opsRecoveryService.startHistoryExportAsync({})).rejects.toThrow(
        OPS_RECOVERY_UNEXPECTED_RESPONSE_MESSAGE
      );
    });

    it('rejects a request whose numeric field is null (numbers are not nullable)', async () => {
      mockedApi.get.mockResolvedValueOnce({ ...validTaskStatus, request: { limit: null } });
      await expect(opsRecoveryService.getHistoryExportAsyncTask('t1')).rejects.toThrow(
        OPS_RECOVERY_UNEXPECTED_RESPONSE_MESSAGE
      );
    });
  });

  describe('endpoint and request invariants are unchanged (gate constraint)', () => {
    it('summary and filtered-summary hit the same endpoint; only the filtered call carries params', async () => {
      mockedApi.get.mockResolvedValueOnce(validSummary);
      await opsRecoveryService.getHistoryExportAsyncTaskSummary();
      expect(mockedApi.get).toHaveBeenLastCalledWith('/ops/recovery/history/export-async/summary');

      mockedApi.get.mockResolvedValueOnce(validSummary);
      await opsRecoveryService.getHistoryExportAsyncTaskSummaryFiltered('HISTORY', 'FAILED');
      expect(mockedApi.get).toHaveBeenLastCalledWith(
        '/ops/recovery/history/export-async/summary',
        { params: { exportType: 'HISTORY', status: 'FAILED' } }
      );
    });

    it('listHistoryExportAsyncTasks normalizes skipCount and defaults maxItems/limit unchanged', async () => {
      mockedApi.get.mockResolvedValueOnce(validTaskList);
      await opsRecoveryService.listHistoryExportAsyncTasks();
      expect(mockedApi.get).toHaveBeenLastCalledWith('/ops/recovery/history/export-async', {
        params: { maxItems: 20, limit: 20, skipCount: 0, exportType: undefined, status: undefined },
      });

      mockedApi.get.mockResolvedValueOnce(validTaskList);
      await opsRecoveryService.listHistoryExportAsyncTasks(20, undefined, undefined, -3.7);
      expect(mockedApi.get).toHaveBeenLastCalledWith('/ops/recovery/history/export-async', {
        params: { maxItems: 20, limit: 20, skipCount: 0, exportType: undefined, status: undefined },
      });

      mockedApi.get.mockResolvedValueOnce(validTaskList);
      await opsRecoveryService.listHistoryExportAsyncTasks(20, undefined, undefined, 5.9);
      expect(mockedApi.get).toHaveBeenLastCalledWith('/ops/recovery/history/export-async', {
        params: { maxItems: 20, limit: 20, skipCount: 5, exportType: undefined, status: undefined },
      });
    });

    it('retryTerminalHistoryExportAsyncTasksByTaskIds keeps trim/dedupe/empty-filter payload', async () => {
      mockedApi.post.mockResolvedValueOnce(validRetryTerminal);
      await opsRecoveryService.retryTerminalHistoryExportAsyncTasksByTaskIds(
        [' a ', 'a', '', '   ', 'b', 'b '],
        'HISTORY'
      );
      expect(mockedApi.post).toHaveBeenLastCalledWith(
        '/ops/recovery/history/export-async/retry-terminal/by-task-ids',
        { sourceTaskIds: ['a', 'b'] },
        { params: { exportType: 'HISTORY' } }
      );
    });
  });
});
