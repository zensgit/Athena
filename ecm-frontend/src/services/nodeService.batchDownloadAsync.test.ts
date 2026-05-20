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

const validTask = {
  taskId: 't1',
  name: 'archive',
  filename: 'archive.zip',
  status: 'QUEUED',
  nodeIds: ['n1', 'n2'],
  totalFiles: 2,
  filesAdded: 0,
  totalBytes: 100,
  bytesAdded: 0,
  createdAt: '2026-05-19T00:00:00Z',
  cleanupEligible: false,
  artifactPresent: false,
  cancellable: true,
  downloadReady: false,
};

const validPreflightItem = {
  outcome: 'INCLUDED',
  includedFiles: 1,
  includedBytes: 50,
  message: 'ok',
};

const validPreflight = {
  requestedCount: 2,
  distinctCount: 2,
  duplicateCount: 0,
  includedNodeIds: ['n1', 'n2'],
  includedNodeCount: 2,
  includedFileCount: 2,
  includedBytes: 100,
  missingCount: 0,
  deletedCount: 0,
  forbiddenCount: 0,
  emptyFolderCount: 0,
  skippedCount: 0,
  executable: true,
  decision: 'READY',
  primaryReason: 'NONE',
  message: 'ok',
  warnings: [],
  items: [validPreflightItem],
};

const validList = {
  items: [validTask],
  totalCount: 1,
  activeCount: 1,
};

const validPaging = { maxItems: 10, skipCount: 0, totalItems: 1, hasMoreItems: false };

const validSummary = {
  totalCount: 5,
  activeCount: 1,
  terminalCount: 4,
  queuedCount: 1,
  runningCount: 0,
  cancelRequestedCount: 0,
  cancelledCount: 1,
  completedCount: 3,
  failedCount: 0,
};

// Two valid cleanup shapes — gate correction 2: single guard, tests separate.
const validBulkCleanup = {
  deletedCount: 2,
  remainingCount: 1,
  statusFilter: 'COMPLETED',
  message: 'cleaned',
};
const validTaskCleanup = {
  taskId: 't1',
  deletedCount: 1,
  remainingCount: 0,
  message: 'removed',
};

const validCancelActive = {
  cancelledCount: 2,
  remainingActiveCount: 0,
  statusFilter: 'QUEUED',
  message: 'cancelled',
};

beforeEach(() => {
  jest.clearAllMocks();
});

describe('nodeService batch-download async response shape guards', () => {
  it('returns valid DTOs and preserves endpoints/params/payloads', async () => {
    // start: POST with {nodeIds, name} body
    mockedApi.post.mockResolvedValueOnce(validTask);
    await expect(nodeService.startBatchDownloadAsync(['n1', 'n2'])).resolves.toEqual(validTask);
    expect(mockedApi.post).toHaveBeenLastCalledWith('/nodes/download/batch-async', {
      nodeIds: ['n1', 'n2'],
      name: 'archive',
    });

    mockedApi.post.mockResolvedValueOnce(validTask);
    await nodeService.startBatchDownloadAsync(['n1'], 'custom.zip');
    expect(mockedApi.post).toHaveBeenLastCalledWith('/nodes/download/batch-async', {
      nodeIds: ['n1'],
      name: 'custom.zip',
    });

    // preflight: POST with {nodeIds, name} body
    mockedApi.post.mockResolvedValueOnce(validPreflight);
    await expect(nodeService.preflightBatchDownloadAsync(['n1'])).resolves.toEqual(validPreflight);
    expect(mockedApi.post).toHaveBeenLastCalledWith('/nodes/download/batch-async/preflight', {
      nodeIds: ['n1'],
      name: 'archive',
    });

    // list: defaults
    mockedApi.get.mockResolvedValueOnce(validList);
    await expect(nodeService.listBatchDownloadAsyncTasks()).resolves.toEqual(validList);
    expect(mockedApi.get).toHaveBeenLastCalledWith('/nodes/download/batch-async', {
      params: {
        maxItems: 10,
        skipCount: 0,
        status: undefined,
        q: undefined,
        owner: undefined,
      },
    });

    // list: full args (locks param mapping limit->maxItems, query->q)
    mockedApi.get.mockResolvedValueOnce(validList);
    await nodeService.listBatchDownloadAsyncTasks(20, 'COMPLETED', 5, 'q1', 'alice');
    expect(mockedApi.get).toHaveBeenLastCalledWith('/nodes/download/batch-async', {
      params: { maxItems: 20, skipCount: 5, status: 'COMPLETED', q: 'q1', owner: 'alice' },
    });

    // get task
    mockedApi.get.mockResolvedValueOnce(validTask);
    await expect(nodeService.getBatchDownloadAsyncTask('t 1')).resolves.toEqual(validTask);
    expect(mockedApi.get).toHaveBeenLastCalledWith('/nodes/download/batch-async/t%201');

    // summary (no params, single-arg get)
    mockedApi.get.mockResolvedValueOnce(validSummary);
    await expect(nodeService.getBatchDownloadAsyncSummary()).resolves.toEqual(validSummary);
    expect(mockedApi.get).toHaveBeenLastCalledWith('/nodes/download/batch-async/summary');

    // cancel one task (single-arg post)
    mockedApi.post.mockResolvedValueOnce(validTask);
    await nodeService.cancelBatchDownloadAsyncTask('t1');
    expect(mockedApi.post).toHaveBeenLastCalledWith('/nodes/download/batch-async/t1/cancel');

    // cleanup bulk: 3-arg post(url, undefined, { params }) — gate correction 4
    mockedApi.post.mockResolvedValueOnce(validBulkCleanup);
    await expect(nodeService.cleanupBatchDownloadAsyncTasks('FAILED')).resolves.toEqual(
      validBulkCleanup
    );
    expect(mockedApi.post).toHaveBeenLastCalledWith(
      '/nodes/download/batch-async/cleanup',
      undefined,
      { params: { status: 'FAILED' } }
    );

    mockedApi.post.mockResolvedValueOnce(validBulkCleanup);
    await nodeService.cleanupBatchDownloadAsyncTasks();
    expect(mockedApi.post).toHaveBeenLastCalledWith(
      '/nodes/download/batch-async/cleanup',
      undefined,
      { params: { status: undefined } }
    );

    // cancel-active: 3-arg post(url, undefined, { params })
    mockedApi.post.mockResolvedValueOnce(validCancelActive);
    await expect(
      nodeService.cancelActiveBatchDownloadAsyncTasks('QUEUED')
    ).resolves.toEqual(validCancelActive);
    expect(mockedApi.post).toHaveBeenLastCalledWith(
      '/nodes/download/batch-async/cancel-active',
      undefined,
      { params: { status: 'QUEUED' } }
    );

    // cleanup single task: single-arg post(url) — no body, no options
    mockedApi.post.mockResolvedValueOnce(validTaskCleanup);
    await nodeService.cleanupBatchDownloadAsyncTask('t1');
    expect(mockedApi.post).toHaveBeenLastCalledWith('/nodes/download/batch-async/t1/cleanup');
  });

  it('cleanup guard tolerates both bulk and single-task response shapes', async () => {
    // bulk shape (statusFilter, no taskId)
    mockedApi.post.mockResolvedValueOnce(validBulkCleanup);
    await expect(nodeService.cleanupBatchDownloadAsyncTasks('COMPLETED')).resolves.toEqual(
      validBulkCleanup
    );

    // single-task shape (taskId, no statusFilter)
    mockedApi.post.mockResolvedValueOnce(validTaskCleanup);
    await expect(nodeService.cleanupBatchDownloadAsyncTask('t1')).resolves.toEqual(validTaskCleanup);
  });

  it('accepts an absent or valid paging block on the list response', async () => {
    mockedApi.get.mockResolvedValueOnce(validList); // paging absent
    await expect(nodeService.listBatchDownloadAsyncTasks()).resolves.toEqual(validList);

    mockedApi.get.mockResolvedValueOnce({ ...validList, paging: validPaging });
    await expect(nodeService.listBatchDownloadAsyncTasks()).resolves.toEqual({
      ...validList,
      paging: validPaging,
    });
  });

  it('throws the sentinel on HTML fallback / null / missing-field / bad-nested-item', async () => {
    const expectThrow = async (fn: () => Promise<unknown>) =>
      expect(fn()).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);

    for (const bad of [HTML_FALLBACK, null]) {
      mockedApi.post.mockResolvedValueOnce(bad);
      await expectThrow(() => nodeService.startBatchDownloadAsync(['n1']));
      mockedApi.post.mockResolvedValueOnce(bad);
      await expectThrow(() => nodeService.preflightBatchDownloadAsync(['n1']));
      mockedApi.get.mockResolvedValueOnce(bad);
      await expectThrow(() => nodeService.listBatchDownloadAsyncTasks());
      mockedApi.get.mockResolvedValueOnce(bad);
      await expectThrow(() => nodeService.getBatchDownloadAsyncTask('t1'));
      mockedApi.get.mockResolvedValueOnce(bad);
      await expectThrow(() => nodeService.getBatchDownloadAsyncSummary());
      mockedApi.post.mockResolvedValueOnce(bad);
      await expectThrow(() => nodeService.cancelBatchDownloadAsyncTask('t1'));
      mockedApi.post.mockResolvedValueOnce(bad);
      await expectThrow(() => nodeService.cleanupBatchDownloadAsyncTasks('FAILED'));
      mockedApi.post.mockResolvedValueOnce(bad);
      await expectThrow(() => nodeService.cancelActiveBatchDownloadAsyncTasks());
      mockedApi.post.mockResolvedValueOnce(bad);
      await expectThrow(() => nodeService.cleanupBatchDownloadAsyncTask('t1'));
    }

    // task with non-string-array nodeIds (G4 explicit)
    mockedApi.post.mockResolvedValueOnce({ ...validTask, nodeIds: ['n1', 2] });
    await expectThrow(() => nodeService.startBatchDownloadAsync(['n1']));

    // task missing required boolean
    mockedApi.get.mockResolvedValueOnce({ ...validTask, cleanupEligible: undefined });
    await expectThrow(() => nodeService.getBatchDownloadAsyncTask('t1'));

    // preflight with malformed item (bad nested element)
    mockedApi.post.mockResolvedValueOnce({
      ...validPreflight,
      items: [{ ...validPreflightItem, includedFiles: 'lots' }],
    });
    await expectThrow(() => nodeService.preflightBatchDownloadAsync(['n1']));

    // list with malformed task item
    mockedApi.get.mockResolvedValueOnce({
      ...validList,
      items: [{ ...validTask, status: 42 }],
    });
    await expectThrow(() => nodeService.listBatchDownloadAsyncTasks());

    // list with present-but-malformed paging
    mockedApi.get.mockResolvedValueOnce({
      ...validList,
      paging: { maxItems: 10, skipCount: 0, totalItems: 'x', hasMoreItems: false },
    });
    await expectThrow(() => nodeService.listBatchDownloadAsyncTasks());

    // summary with wrong numeric type
    mockedApi.get.mockResolvedValueOnce({ ...validSummary, queuedCount: '1' });
    await expectThrow(() => nodeService.getBatchDownloadAsyncSummary());

    // cancel-active missing required message
    mockedApi.post.mockResolvedValueOnce({ ...validCancelActive, message: undefined });
    await expectThrow(() => nodeService.cancelActiveBatchDownloadAsyncTasks());
  });
});
