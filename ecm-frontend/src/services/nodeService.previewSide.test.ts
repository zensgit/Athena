import api from './api';
import nodeService, { NODE_UNEXPECTED_RESPONSE_MESSAGE } from './nodeService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const HTML_FALLBACK = '<!doctype html><html><body>app shell</body></html>';

// --- B group fixtures (single-node) ---
const validPreviewQueueStatus = {
  documentId: 'n1',
  previewStatus: 'PENDING',
  queued: true,
};
const validPreviewQueueCancelStatus = {
  documentId: 'n1',
  queueState: 'CANCELLED',
  cancelled: true,
  hadActiveTask: false,
  running: false,
};
const validPreviewRepairStatus = {
  documentId: 'n1',
  readinessState: null,
  readinessReason: null,
  invalidated: true,
  invalidationReason: null,
  queued: true,
  queueMessage: null,
};
const validOcrQueueStatus = {
  documentId: 'n1',
  ocrStatus: 'PENDING',
  queued: true,
};

// --- C group fixtures (by-search direct) ---
const validCapabilities = {
  defaultMaxDocuments: 100,
  maxMaxDocuments: 1000,
  scanPageSize: 100,
  scanLimit: 10000,
  defaultWorkerCount: 2,
  maxWorkerCount: 8,
};
const validReasonCount = { reason: 'TEMPORARY', count: 3 };
const validSkipCount = { reason: 'LOCKED', count: 1 };
const validBatchItem = {
  documentId: 'd1',
  outcome: 'QUEUED',
  message: null,
  previewStatus: 'PENDING',
  attempts: 0,
  nextAttemptAt: null,
};
const validDryRunItem = {
  documentId: 'd1',
  name: 'doc',
  previewStatus: 'FAILED',
  previewFailureReason: 'parser-timeout',
  previewFailureCategory: 'TEMPORARY',
};
const validBatchResult = {
  query: 'foo',
  reason: 'TEMPORARY',
  maxDocuments: 100,
  totalCandidates: 5,
  scanned: 5,
  matched: 3,
  truncated: false,
  reasonBreakdown: [validReasonCount],
  requested: 3,
  deduplicated: 0,
  queued: 3,
  skipped: 0,
  failed: 0,
  results: [validBatchItem],
};
const validDryRunResult = {
  query: 'foo',
  reason: 'TEMPORARY',
  maxDocuments: 100,
  totalCandidates: 5,
  scanned: 5,
  matched: 3,
  truncated: false,
  reasonBreakdown: [validReasonCount],
  sampleCount: 3,
  samples: [validDryRunItem],
};

// --- D group fixtures (by-search CSV-export async-tail) ---
// minimal Task/TaskStatus shape: all optional fields omitted (H2)
const validTaskShape = { taskId: 't1' };
const validTaskList = { count: 1, items: [validTaskShape] };
const validTaskSummary = {
  total: 5,
  queued: 1,
  running: 0,
  completed: 3,
  cancelled: 1,
  failed: 0,
  terminal: 4,
  active: 1,
};
// cleanup uses `status` (backend field name on this endpoint)
const validCleanupResult = {
  deletedCount: 2,
  remainingCount: 1,
  status: 'COMPLETED',
  message: 'done',
};
// cancel-active uses `statusFilter` (renamed DTO; backend differs from cleanup)
const validCancelActiveResult = {
  cancelledCount: 1,
  remainingActiveCount: 0,
  statusFilter: 'QUEUED',
  message: 'done',
};

beforeEach(() => {
  jest.clearAllMocks();
});

describe('nodeService preview-side response shape guards', () => {
  it('B group: locks /documents/{id} prefix and default/non-default branches', async () => {
    // queuePreview default force=false
    mockedApi.post.mockResolvedValueOnce(validPreviewQueueStatus);
    await expect(nodeService.queuePreview('n1')).resolves.toEqual(validPreviewQueueStatus);
    expect(mockedApi.post).toHaveBeenLastCalledWith(
      '/documents/n1/preview/queue', null, { params: { force: false } }
    );

    // queuePreview explicit force=true
    mockedApi.post.mockResolvedValueOnce(validPreviewQueueStatus);
    await nodeService.queuePreview('n1', true);
    expect(mockedApi.post).toHaveBeenLastCalledWith(
      '/documents/n1/preview/queue', null, { params: { force: true } }
    );

    // cancelQueuedPreview — single-arg POST
    mockedApi.post.mockResolvedValueOnce(validPreviewQueueCancelStatus);
    await expect(nodeService.cancelQueuedPreview('n1')).resolves.toEqual(
      validPreviewQueueCancelStatus
    );
    expect(mockedApi.post).toHaveBeenLastCalledWith('/documents/n1/preview/queue/cancel');

    // repairPreview — no options -> all three defaults true (??true semantics)
    mockedApi.post.mockResolvedValueOnce(validPreviewRepairStatus);
    await nodeService.repairPreview('n1');
    expect(mockedApi.post).toHaveBeenLastCalledWith(
      '/documents/n1/preview/repair', null,
      { params: { forceInvalidate: true, requeue: true, forceQueue: true } }
    );

    // repairPreview — only forceInvalidate:false -> others still default true
    mockedApi.post.mockResolvedValueOnce(validPreviewRepairStatus);
    await nodeService.repairPreview('n1', { forceInvalidate: false });
    expect(mockedApi.post).toHaveBeenLastCalledWith(
      '/documents/n1/preview/repair', null,
      { params: { forceInvalidate: false, requeue: true, forceQueue: true } }
    );

    // repairPreview — all explicit false
    mockedApi.post.mockResolvedValueOnce(validPreviewRepairStatus);
    await nodeService.repairPreview('n1', {
      forceInvalidate: false,
      requeue: false,
      forceQueue: false,
    });
    expect(mockedApi.post).toHaveBeenLastCalledWith(
      '/documents/n1/preview/repair', null,
      { params: { forceInvalidate: false, requeue: false, forceQueue: false } }
    );

    // queueOcr default + explicit
    mockedApi.post.mockResolvedValueOnce(validOcrQueueStatus);
    await nodeService.queueOcr('n1');
    expect(mockedApi.post).toHaveBeenLastCalledWith(
      '/documents/n1/ocr/queue', null, { params: { force: false } }
    );

    mockedApi.post.mockResolvedValueOnce(validOcrQueueStatus);
    await nodeService.queueOcr('n1', true);
    expect(mockedApi.post).toHaveBeenLastCalledWith(
      '/documents/n1/ocr/queue', null, { params: { force: true } }
    );
  });

  it('C group: capabilities single-arg GET and 2-arg POST body=payload', async () => {
    mockedApi.get.mockResolvedValueOnce(validCapabilities);
    await expect(nodeService.getPreviewQueueBySearchCapabilities()).resolves.toEqual(
      validCapabilities
    );
    expect(mockedApi.get).toHaveBeenLastCalledWith('/search/preview/queue-failed/capabilities');

    const batchPayload = { query: 'foo', reason: 'TEMPORARY', maxDocuments: 100, force: false };
    mockedApi.post.mockResolvedValueOnce(validBatchResult);
    await expect(nodeService.queueFailedPreviewsBySearch(batchPayload)).resolves.toEqual(
      validBatchResult
    );
    expect(mockedApi.post).toHaveBeenLastCalledWith('/search/preview/queue-failed', batchPayload);

    const dryPayload = { query: 'foo', reason: 'TEMPORARY', maxDocuments: 100 };
    mockedApi.post.mockResolvedValueOnce(validDryRunResult);
    await expect(nodeService.dryRunFailedPreviewsBySearch(dryPayload)).resolves.toEqual(
      validDryRunResult
    );
    expect(mockedApi.post).toHaveBeenLastCalledWith(
      '/search/preview/queue-failed/dry-run', dryPayload
    );
  });

  it('D group: locks all POST/GET shapes, including null body, {} body, status given/not, single-arg', async () => {
    const payload = { query: 'foo', maxDocuments: 100 };

    // 8 start: 2-arg POST(url, payload)
    mockedApi.post.mockResolvedValueOnce(validTaskShape);
    await nodeService.startDryRunFailedPreviewsCsvExportAsyncBySearch(payload);
    expect(mockedApi.post).toHaveBeenLastCalledWith(
      '/search/preview/queue-failed/dry-run/export-async', payload
    );

    // 9 getTask: single-arg GET(url)
    mockedApi.get.mockResolvedValueOnce(validTaskShape);
    await nodeService.getDryRunFailedPreviewsCsvExportAsyncBySearchTask('t 1');
    expect(mockedApi.get).toHaveBeenLastCalledWith(
      '/search/preview/queue-failed/dry-run/export-async/t%201'
    );

    // 10 list default limit=20 no status -> params{limit:20} (status spread to empty)
    mockedApi.get.mockResolvedValueOnce(validTaskList);
    await nodeService.listDryRunFailedPreviewsCsvExportAsyncBySearchTasks();
    expect(mockedApi.get).toHaveBeenLastCalledWith(
      '/search/preview/queue-failed/dry-run/export-async', { params: { limit: 20 } }
    );

    // 10 list with status -> params{limit, status}
    mockedApi.get.mockResolvedValueOnce(validTaskList);
    await nodeService.listDryRunFailedPreviewsCsvExportAsyncBySearchTasks(50, 'RUNNING');
    expect(mockedApi.get).toHaveBeenLastCalledWith(
      '/search/preview/queue-failed/dry-run/export-async',
      { params: { limit: 50, status: 'RUNNING' } }
    );

    // 11 summary no status -> {params: undefined}
    mockedApi.get.mockResolvedValueOnce(validTaskSummary);
    await nodeService.getDryRunFailedPreviewsCsvExportAsyncBySearchTasksSummary();
    expect(mockedApi.get).toHaveBeenLastCalledWith(
      '/search/preview/queue-failed/dry-run/export-async/summary', { params: undefined }
    );

    // 11 summary with status -> {params: {status}}
    mockedApi.get.mockResolvedValueOnce(validTaskSummary);
    await nodeService.getDryRunFailedPreviewsCsvExportAsyncBySearchTasksSummary('COMPLETED');
    expect(mockedApi.get).toHaveBeenLastCalledWith(
      '/search/preview/queue-failed/dry-run/export-async/summary',
      { params: { status: 'COMPLETED' } }
    );

    // 12 cleanup no status -> POST(url, null, {params: undefined})
    mockedApi.post.mockResolvedValueOnce(validCleanupResult);
    await expect(
      nodeService.cleanupDryRunFailedPreviewsCsvExportAsyncBySearchTasks()
    ).resolves.toEqual(validCleanupResult);
    expect(mockedApi.post).toHaveBeenLastCalledWith(
      '/search/preview/queue-failed/dry-run/export-async/cleanup', null, { params: undefined }
    );

    // 12 cleanup with status -> POST(url, null, {params: {status}})
    mockedApi.post.mockResolvedValueOnce(validCleanupResult);
    await nodeService.cleanupDryRunFailedPreviewsCsvExportAsyncBySearchTasks('FAILED');
    expect(mockedApi.post).toHaveBeenLastCalledWith(
      '/search/preview/queue-failed/dry-run/export-async/cleanup', null,
      { params: { status: 'FAILED' } }
    );

    // 13 cancelActive no status -> POST(url, {}, {params: undefined})
    mockedApi.post.mockResolvedValueOnce(validCancelActiveResult);
    await expect(
      nodeService.cancelActiveDryRunFailedPreviewsCsvExportAsyncBySearchTasks()
    ).resolves.toEqual(validCancelActiveResult);
    expect(mockedApi.post).toHaveBeenLastCalledWith(
      '/search/preview/queue-failed/dry-run/export-async/cancel-active', {}, { params: undefined }
    );

    // 13 cancelActive with status -> POST(url, {}, {params: {status}})
    mockedApi.post.mockResolvedValueOnce(validCancelActiveResult);
    await nodeService.cancelActiveDryRunFailedPreviewsCsvExportAsyncBySearchTasks('QUEUED');
    expect(mockedApi.post).toHaveBeenLastCalledWith(
      '/search/preview/queue-failed/dry-run/export-async/cancel-active', {},
      { params: { status: 'QUEUED' } }
    );

    // 14 cancelTask: single-arg POST(url)
    mockedApi.post.mockResolvedValueOnce(validTaskShape);
    await nodeService.cancelDryRunFailedPreviewsCsvExportAsyncBySearchTask('t1');
    expect(mockedApi.post).toHaveBeenLastCalledWith(
      '/search/preview/queue-failed/dry-run/export-async/t1/cancel'
    );
  });

  it('H2: Task/TaskStatus optional fields stay optional (minimal {taskId} passes)', async () => {
    // start: minimal Task with only taskId
    mockedApi.post.mockResolvedValueOnce(validTaskShape);
    await expect(
      nodeService.startDryRunFailedPreviewsCsvExportAsyncBySearch({ query: 'foo' })
    ).resolves.toEqual(validTaskShape);

    // get: minimal TaskStatus
    mockedApi.get.mockResolvedValueOnce(validTaskShape);
    await expect(
      nodeService.getDryRunFailedPreviewsCsvExportAsyncBySearchTask('t1')
    ).resolves.toEqual(validTaskShape);

    // cancel task: minimal TaskStatus
    mockedApi.post.mockResolvedValueOnce(validTaskShape);
    await expect(
      nodeService.cancelDryRunFailedPreviewsCsvExportAsyncBySearchTask('t1')
    ).resolves.toEqual(validTaskShape);

    // OcrQueueStatus minimal (attempts/nextAttemptAt/message optional)
    mockedApi.post.mockResolvedValueOnce({ documentId: 'n1', ocrStatus: null, queued: true });
    await expect(nodeService.queueOcr('n1')).resolves.toBeDefined();
  });

  it('H4: workerCount/scanSkipped finite when present; nested arrays deep-validated', async () => {
    // BatchResult with workerCount + scanSkipped present and finite
    mockedApi.post.mockResolvedValueOnce({
      ...validBatchResult,
      workerCount: 4,
      scanSkipped: 2,
      skipBreakdown: [validSkipCount],
    });
    await expect(
      nodeService.queueFailedPreviewsBySearch({})
    ).resolves.toMatchObject({ workerCount: 4, scanSkipped: 2 });

    // DryRunResult same
    mockedApi.post.mockResolvedValueOnce({
      ...validDryRunResult,
      workerCount: 4,
      scanSkipped: 1,
      skipBreakdown: [validSkipCount],
    });
    await expect(nodeService.dryRunFailedPreviewsBySearch({})).resolves.toBeDefined();

    // BatchResult workerCount: null -> throw (number, not nullable)
    mockedApi.post.mockResolvedValueOnce({ ...validBatchResult, workerCount: null });
    await expect(nodeService.queueFailedPreviewsBySearch({})).rejects.toThrow(
      NODE_UNEXPECTED_RESPONSE_MESSAGE
    );

    // BatchResult scanSkipped: 'x' -> throw
    mockedApi.post.mockResolvedValueOnce({ ...validBatchResult, scanSkipped: 'x' });
    await expect(nodeService.queueFailedPreviewsBySearch({})).rejects.toThrow(
      NODE_UNEXPECTED_RESPONSE_MESSAGE
    );

    // BatchResult.results bad nested item
    mockedApi.post.mockResolvedValueOnce({
      ...validBatchResult,
      results: [{ ...validBatchItem, attempts: 'lots' }],
    });
    await expect(nodeService.queueFailedPreviewsBySearch({})).rejects.toThrow(
      NODE_UNEXPECTED_RESPONSE_MESSAGE
    );

    // BatchResult.reasonBreakdown bad element (count not number)
    mockedApi.post.mockResolvedValueOnce({
      ...validBatchResult,
      reasonBreakdown: [{ reason: 'X', count: '3' }],
    });
    await expect(nodeService.queueFailedPreviewsBySearch({})).rejects.toThrow(
      NODE_UNEXPECTED_RESPONSE_MESSAGE
    );

    // BatchResult.skipBreakdown present-but-bad element
    mockedApi.post.mockResolvedValueOnce({
      ...validBatchResult,
      skipBreakdown: [null],
    });
    await expect(nodeService.queueFailedPreviewsBySearch({})).rejects.toThrow(
      NODE_UNEXPECTED_RESPONSE_MESSAGE
    );

    // DryRunResult.samples bad element
    mockedApi.post.mockResolvedValueOnce({
      ...validDryRunResult,
      samples: ['junk'],
    });
    await expect(nodeService.dryRunFailedPreviewsBySearch({})).rejects.toThrow(
      NODE_UNEXPECTED_RESPONSE_MESSAGE
    );

    // TaskList.items bad element
    mockedApi.get.mockResolvedValueOnce({ count: 1, items: [{ taskId: 42 }] });
    await expect(
      nodeService.listDryRunFailedPreviewsCsvExportAsyncBySearchTasks()
    ).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('throws the sentinel on HTML fallback / null / missing-field / cancelActive misnamed field', async () => {
    const expectThrow = async (fn: () => Promise<unknown>) =>
      expect(fn()).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);

    for (const bad of [HTML_FALLBACK, null]) {
      mockedApi.post.mockResolvedValueOnce(bad);
      await expectThrow(() => nodeService.queuePreview('n1'));
      mockedApi.post.mockResolvedValueOnce(bad);
      await expectThrow(() => nodeService.queueFailedPreviewsBySearch({}));
      mockedApi.get.mockResolvedValueOnce(bad);
      await expectThrow(() =>
        nodeService.listDryRunFailedPreviewsCsvExportAsyncBySearchTasks()
      );
      mockedApi.post.mockResolvedValueOnce(bad);
      await expectThrow(() =>
        nodeService.cancelActiveDryRunFailedPreviewsCsvExportAsyncBySearchTasks()
      );
    }

    // PreviewQueueStatus missing required `queued`
    mockedApi.post.mockResolvedValueOnce({ documentId: 'n1', previewStatus: null });
    await expectThrow(() => nodeService.queuePreview('n1'));

    // PreviewRepairStatus with wrong type on a strict-boolean field
    mockedApi.post.mockResolvedValueOnce({
      documentId: 'n1',
      readinessState: null,
      readinessReason: null,
      invalidated: 'no',
      invalidationReason: null,
      queued: true,
      queueMessage: null,
    });
    await expectThrow(() => nodeService.repairPreview('n1'));

    // TaskCleanupResult uses `status`, not `statusFilter` — putting `statusFilter` instead
    // should still pass because `status` is optional; but missing the required deletedCount fails.
    mockedApi.post.mockResolvedValueOnce({
      remainingCount: 0,
      status: 'COMPLETED',
      message: 'done',
    });
    await expectThrow(() =>
      nodeService.cleanupDryRunFailedPreviewsCsvExportAsyncBySearchTasks()
    );

    // CancelActiveResult: the renamed field is `statusFilter`; passing `status` with bad
    // type for the actual required field cancelledCount must throw.
    mockedApi.post.mockResolvedValueOnce({
      cancelledCount: '1',
      remainingActiveCount: 0,
      statusFilter: 'QUEUED',
      message: 'done',
    });
    await expectThrow(() =>
      nodeService.cancelActiveDryRunFailedPreviewsCsvExportAsyncBySearchTasks()
    );
  });
});
