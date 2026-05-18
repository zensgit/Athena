import api from './api';
import previewDiagnosticsService, {
  PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE,
  PreviewQueueCancelActiveResult,
  PreviewQueueDeclinedExportTask,
  PreviewQueueDeclinedExportTaskCancelActiveResponse,
  PreviewQueueDeclinedExportTaskCleanupResponse,
  PreviewQueueDeclinedExportTaskList,
  PreviewQueueDeclinedExportTaskRetryTerminalDryRunResponse,
  PreviewQueueDeclinedExportTaskRetryTerminalResponse,
  PreviewQueueDeclinedExportTaskSummary,
  PreviewQueueDeclinedRequeueDryRunExportTask,
  PreviewQueueDeclinedRequeueDryRunExportTaskCancelActiveResponse,
  PreviewQueueDeclinedRequeueDryRunExportTaskCleanupResponse,
  PreviewQueueDeclinedRequeueDryRunExportTaskList,
  PreviewQueueDeclinedRequeueDryRunExportTaskRetryTerminalDryRunResponse,
  PreviewQueueDeclinedRequeueDryRunExportTaskRetryTerminalResponse,
  PreviewQueueDeclinedRequeueDryRunExportTaskSummary,
  PreviewRenditionResourcesExportTask,
  PreviewRenditionResourcesExportTaskCancelActiveResponse,
  PreviewRenditionResourcesExportTaskCleanupResponse,
  PreviewRenditionResourcesExportTaskRetryTerminalDryRunResponse,
  PreviewRenditionResourcesExportTaskRetryTerminalResponse,
  PreviewRenditionResourcesExportTaskSummary,
} from './previewDiagnosticsService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    getBlob: jest.fn(),
    downloadFile: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const renditionTask: PreviewRenditionResourcesExportTask = {
  taskId: 'rendition-task-1',
  status: 'QUEUED',
  message: 'queued',
  deduplicated: false,
  createdAt: '2026-05-18T00:00:00Z',
  filename: 'renditions.csv',
};

const queueDeclinedTask: PreviewQueueDeclinedExportTask = {
  taskId: 'queue-task-1',
  status: 'RUNNING',
  createdAt: '2026-05-18T00:00:00Z',
  categoryFilter: 'TEMPORARY',
  forceRequiredFilter: 'YES',
  queryFilter: 'pdf',
  windowHoursFilter: 24,
  limit: 50,
};

const requeueDryRunTask: PreviewQueueDeclinedRequeueDryRunExportTask = {
  ...queueDeclinedTask,
  taskId: 'requeue-task-1',
  force: true,
};

const taskList = <T>(task: T): { count: number; paging: { skipCount: number; maxItems: number; totalItems: number; hasMoreItems: boolean }; items: T[] } => ({
  count: 1,
  paging: { skipCount: 0, maxItems: 20, totalItems: 1, hasMoreItems: false },
  items: [task],
});

const summary: PreviewRenditionResourcesExportTaskSummary = {
  totalCount: 3,
  queuedCount: 1,
  runningCount: 1,
  completedCount: 1,
  cancelledCount: 0,
  failedCount: 0,
  timedOutCount: 0,
  expiredCount: 0,
  activeCount: 2,
  terminalCount: 1,
};

const cleanup: PreviewRenditionResourcesExportTaskCleanupResponse = {
  deletedCount: 1,
  remainingCount: 2,
  statusFilter: 'COMPLETED',
  message: 'cleaned',
};

const cancelActive: PreviewRenditionResourcesExportTaskCancelActiveResponse = {
  cancelledCount: 1,
  remainingActiveCount: 0,
  statusFilter: 'RUNNING',
  message: 'cancelled',
};

const retryTerminal: PreviewRenditionResourcesExportTaskRetryTerminalResponse = {
  requested: 1,
  retried: 1,
  reused: 0,
  skipped: 0,
  failed: 0,
  limit: 20,
  statusFilter: 'FAILED',
  message: 'retried',
  results: [
    {
      sourceTaskId: 'old-task',
      newTaskId: 'new-task',
      sourceStatus: 'FAILED',
      outcome: 'RETRIED',
      message: null,
    },
  ],
};

const retryDryRun: PreviewRenditionResourcesExportTaskRetryTerminalDryRunResponse = {
  requested: 1,
  retryable: 1,
  skipped: 0,
  limit: 20,
  statusFilter: 'FAILED',
  message: 'dry run',
  results: [
    {
      sourceTaskId: 'old-task',
      sourceStatus: 'FAILED',
      outcome: 'RETRYABLE',
      reasonCode: 'eligible',
      message: null,
    },
  ],
  reasonBreakdown: [{ reasonCode: 'eligible', outcome: 'RETRYABLE', count: 1 }],
};

const queueCancelActive: PreviewQueueCancelActiveResult = {
  stateFilter: 'RUNNING',
  queryFilter: 'pdf',
  limit: 20,
  requested: 1,
  cancelled: 1,
  skipped: 0,
  failed: 0,
  results: [
    {
      documentId: 'doc-1',
      previewStatus: 'CANCEL_REQUESTED',
      previewFailureReason: null,
      previewFailureCategory: null,
      previewLastUpdated: '2026-05-18T00:00:00Z',
      queueState: 'CANCEL_REQUESTED',
      outcome: 'CANCELLED',
      message: null,
    },
  ],
};

describe('previewDiagnosticsService async export response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('guards the rendition resources export task lifecycle', async () => {
    mockedApi.post
      .mockResolvedValueOnce(renditionTask)
      .mockResolvedValueOnce(cleanup)
      .mockResolvedValueOnce(cancelActive)
      .mockResolvedValueOnce({ ...renditionTask, status: 'CANCELLED' })
      .mockResolvedValueOnce({ ...renditionTask, status: 'QUEUED' })
      .mockResolvedValueOnce(retryTerminal)
      .mockResolvedValueOnce(retryDryRun)
      .mockResolvedValueOnce(retryTerminal);
    mockedApi.get
      .mockResolvedValueOnce(taskList(renditionTask))
      .mockResolvedValueOnce(summary)
      .mockResolvedValueOnce(renditionTask);

    await expect(previewDiagnosticsService.startRenditionResourcesExportTask(14, 250)).resolves.toEqual(renditionTask);
    await expect(previewDiagnosticsService.listRenditionResourcesExportTasks(10, 'RUNNING', 5)).resolves.toMatchObject({ count: 1 });
    await expect(previewDiagnosticsService.getRenditionResourcesExportTaskSummary('FAILED')).resolves.toEqual(summary);
    await expect(previewDiagnosticsService.cleanupRenditionResourcesExportTasks('COMPLETED')).resolves.toEqual(cleanup);
    await expect(previewDiagnosticsService.cancelActiveRenditionResourcesExportTasks('RUNNING')).resolves.toEqual(cancelActive);
    await expect(previewDiagnosticsService.getRenditionResourcesExportTask('task/1')).resolves.toEqual(renditionTask);
    await expect(previewDiagnosticsService.cancelRenditionResourcesExportTask('task/1')).resolves.toMatchObject({ status: 'CANCELLED' });
    await expect(previewDiagnosticsService.retryRenditionResourcesExportTask('task/1')).resolves.toMatchObject({ status: 'QUEUED' });
    await expect(previewDiagnosticsService.retryTerminalRenditionResourcesExportTasks('FAILED', 10)).resolves.toEqual(retryTerminal);
    await expect(previewDiagnosticsService.dryRunRetryTerminalRenditionResourcesExportTasks('FAILED', 10)).resolves.toEqual(retryDryRun);
    await expect(previewDiagnosticsService.retryTerminalRenditionResourcesExportTasksByTaskIds([' old-task ', 'old-task', '', 'new-task'])).resolves.toEqual(retryTerminal);

    expect(mockedApi.post).toHaveBeenNthCalledWith(
      1,
      '/preview/diagnostics/renditions/resources/export-async',
      { days: 14, limit: 250 }
    );
    expect(mockedApi.get).toHaveBeenNthCalledWith(
      1,
      '/preview/diagnostics/renditions/resources/export-async',
      { params: { maxItems: 10, limit: 10, skipCount: 5, status: 'RUNNING' } }
    );
    expect(mockedApi.get).toHaveBeenNthCalledWith(
      3,
      '/preview/diagnostics/renditions/resources/export-async/task%2F1'
    );
    expect(mockedApi.post).toHaveBeenNthCalledWith(
      8,
      '/preview/diagnostics/renditions/resources/export-async/retry-terminal/by-task-ids',
      { sourceTaskIds: ['old-task', 'new-task'] }
    );
  });

  it('guards queue declined export task lifecycle responses', async () => {
    const queueSummary: PreviewQueueDeclinedExportTaskSummary = summary;
    const queueCleanup: PreviewQueueDeclinedExportTaskCleanupResponse = cleanup;
    const queueCancel: PreviewQueueDeclinedExportTaskCancelActiveResponse = cancelActive;
    const queueRetry: PreviewQueueDeclinedExportTaskRetryTerminalResponse = retryTerminal;
    const queueDryRun: PreviewQueueDeclinedExportTaskRetryTerminalDryRunResponse = retryDryRun;
    const queueList: PreviewQueueDeclinedExportTaskList = taskList(queueDeclinedTask);

    mockedApi.post
      .mockResolvedValueOnce(queueDeclinedTask)
      .mockResolvedValueOnce(queueCleanup)
      .mockResolvedValueOnce(queueCancel)
      .mockResolvedValueOnce({ ...queueDeclinedTask, status: 'CANCELLED' })
      .mockResolvedValueOnce({ ...queueDeclinedTask, status: 'QUEUED' })
      .mockResolvedValueOnce(queueRetry)
      .mockResolvedValueOnce(queueDryRun)
      .mockResolvedValueOnce(queueRetry);
    mockedApi.get
      .mockResolvedValueOnce(queueList)
      .mockResolvedValueOnce(queueSummary)
      .mockResolvedValueOnce(queueDeclinedTask);

    await expect(previewDiagnosticsService.startQueueDeclinedExportTask(50, 'TEMPORARY', 'YES', ' pdf ', 24)).resolves.toEqual(queueDeclinedTask);
    await expect(previewDiagnosticsService.listQueueDeclinedExportTasks(10, 'RUNNING', 2)).resolves.toEqual(queueList);
    await expect(previewDiagnosticsService.getQueueDeclinedExportTaskSummary('FAILED')).resolves.toEqual(queueSummary);
    await expect(previewDiagnosticsService.cleanupQueueDeclinedExportTasks('COMPLETED')).resolves.toEqual(queueCleanup);
    await expect(previewDiagnosticsService.cancelActiveQueueDeclinedExportTasks('RUNNING')).resolves.toEqual(queueCancel);
    await expect(previewDiagnosticsService.getQueueDeclinedExportTask('task/2')).resolves.toEqual(queueDeclinedTask);
    await expect(previewDiagnosticsService.cancelQueueDeclinedExportTask('task/2')).resolves.toMatchObject({ status: 'CANCELLED' });
    await expect(previewDiagnosticsService.retryQueueDeclinedExportTask('task/2')).resolves.toMatchObject({ status: 'QUEUED' });
    await expect(previewDiagnosticsService.retryTerminalQueueDeclinedExportTasks('FAILED', 9)).resolves.toEqual(queueRetry);
    await expect(previewDiagnosticsService.dryRunRetryTerminalQueueDeclinedExportTasks('FAILED', 9)).resolves.toEqual(queueDryRun);
    await expect(previewDiagnosticsService.retryTerminalQueueDeclinedExportTasksByTaskIds(['task-a', ' task-a ', 'task-b'])).resolves.toEqual(queueRetry);

    expect(mockedApi.post).toHaveBeenNthCalledWith(
      1,
      '/preview/diagnostics/queue/declined/export-async',
      {
        limit: 50,
        category: 'TEMPORARY',
        forceRequired: 'YES',
        windowHours: 24,
        query: 'pdf',
      }
    );
  });

  it('guards queue declined requeue dry-run export task lifecycle responses', async () => {
    const requeueSummary: PreviewQueueDeclinedRequeueDryRunExportTaskSummary = summary;
    const requeueCleanup: PreviewQueueDeclinedRequeueDryRunExportTaskCleanupResponse = cleanup;
    const requeueCancel: PreviewQueueDeclinedRequeueDryRunExportTaskCancelActiveResponse = cancelActive;
    const requeueRetry: PreviewQueueDeclinedRequeueDryRunExportTaskRetryTerminalResponse = retryTerminal;
    const requeueDryRun: PreviewQueueDeclinedRequeueDryRunExportTaskRetryTerminalDryRunResponse = retryDryRun;
    const requeueList: PreviewQueueDeclinedRequeueDryRunExportTaskList = taskList(requeueDryRunTask);

    mockedApi.post
      .mockResolvedValueOnce(requeueDryRunTask)
      .mockResolvedValueOnce(requeueCleanup)
      .mockResolvedValueOnce(requeueCancel)
      .mockResolvedValueOnce({ ...requeueDryRunTask, status: 'CANCELLED' })
      .mockResolvedValueOnce({ ...requeueDryRunTask, status: 'QUEUED' })
      .mockResolvedValueOnce(requeueRetry)
      .mockResolvedValueOnce(requeueDryRun)
      .mockResolvedValueOnce(requeueRetry);
    mockedApi.get
      .mockResolvedValueOnce(requeueList)
      .mockResolvedValueOnce(requeueSummary)
      .mockResolvedValueOnce(requeueDryRunTask);

    await expect(previewDiagnosticsService.startQueueDeclinedRequeueDryRunExportTask(50, 'TEMPORARY', 'YES', ' pdf ', false, 24)).resolves.toEqual(requeueDryRunTask);
    await expect(previewDiagnosticsService.listQueueDeclinedRequeueDryRunExportTasks(10, 'RUNNING', 2)).resolves.toEqual(requeueList);
    await expect(previewDiagnosticsService.getQueueDeclinedRequeueDryRunExportTaskSummary('FAILED')).resolves.toEqual(requeueSummary);
    await expect(previewDiagnosticsService.cleanupQueueDeclinedRequeueDryRunExportTasks('COMPLETED')).resolves.toEqual(requeueCleanup);
    await expect(previewDiagnosticsService.cancelActiveQueueDeclinedRequeueDryRunExportTasks('RUNNING')).resolves.toEqual(requeueCancel);
    await expect(previewDiagnosticsService.getQueueDeclinedRequeueDryRunExportTask('task/3')).resolves.toEqual(requeueDryRunTask);
    await expect(previewDiagnosticsService.cancelQueueDeclinedRequeueDryRunExportTask('task/3')).resolves.toMatchObject({ status: 'CANCELLED' });
    await expect(previewDiagnosticsService.retryQueueDeclinedRequeueDryRunExportTask('task/3')).resolves.toMatchObject({ status: 'QUEUED' });
    await expect(previewDiagnosticsService.retryTerminalQueueDeclinedRequeueDryRunExportTasks('FAILED', 9)).resolves.toEqual(requeueRetry);
    await expect(previewDiagnosticsService.dryRunRetryTerminalQueueDeclinedRequeueDryRunExportTasks('FAILED', 9)).resolves.toEqual(requeueDryRun);
    await expect(previewDiagnosticsService.retryTerminalQueueDeclinedRequeueDryRunExportTasksByTaskIds(['task-a', ' task-a ', 'task-b'])).resolves.toEqual(requeueRetry);

    expect(mockedApi.post).toHaveBeenNthCalledWith(
      1,
      '/preview/diagnostics/queue/declined/requeue/dry-run/export-async',
      {
        limit: 50,
        category: 'TEMPORARY',
        forceRequired: 'YES',
        windowHours: 24,
        query: 'pdf',
        force: false,
      }
    );
  });

  it('guards cancelQueueDiagnosticsActive responses', async () => {
    mockedApi.post.mockResolvedValueOnce(queueCancelActive);

    await expect(previewDiagnosticsService.cancelQueueDiagnosticsActive(20, 'RUNNING', ' pdf ')).resolves.toEqual(queueCancelActive);

    expect(mockedApi.post).toHaveBeenCalledWith(
      '/preview/diagnostics/queue/cancel-active',
      undefined,
      { params: { limit: 20, state: 'RUNNING', query: 'pdf' } }
    );
  });

  it('rejects HTML fallback async task responses', async () => {
    mockedApi.post.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(previewDiagnosticsService.startRenditionResourcesExportTask()).rejects.toThrow(
      PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('rejects malformed async task list paging', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...taskList(renditionTask),
      paging: { skipCount: 0, maxItems: 20, totalItems: 1, hasMoreItems: 'no' },
    });

    await expect(previewDiagnosticsService.listRenditionResourcesExportTasks()).rejects.toThrow(
      PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('rejects malformed async task summary counts', async () => {
    mockedApi.get.mockResolvedValueOnce({ ...summary, failedCount: '0' });

    await expect(previewDiagnosticsService.getQueueDeclinedExportTaskSummary()).rejects.toThrow(
      PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('rejects malformed retry dry-run reason breakdowns', async () => {
    mockedApi.post.mockResolvedValueOnce({
      ...retryDryRun,
      reasonBreakdown: [{ reasonCode: 'eligible', outcome: 'RETRYABLE', count: '1' }],
    });

    await expect(
      previewDiagnosticsService.dryRunRetryTerminalQueueDeclinedRequeueDryRunExportTasks()
    ).rejects.toThrow(PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('rejects malformed queue cancel active items', async () => {
    mockedApi.post.mockResolvedValueOnce({
      ...queueCancelActive,
      results: [{ ...queueCancelActive.results[0], documentId: 42 }],
    });

    await expect(previewDiagnosticsService.cancelQueueDiagnosticsActive()).rejects.toThrow(
      PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE
    );
  });
});
