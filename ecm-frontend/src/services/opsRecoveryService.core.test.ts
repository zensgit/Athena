import api from './api';
import opsRecoveryService, {
  OPS_RECOVERY_UNEXPECTED_RESPONSE_MESSAGE,
  RecoveryBatchItem,
  RecoveryBatchResult,
  RecoveryDryRunItem,
  RecoveryDryRunResult,
  RecoveryHistoryItem,
  RecoveryHistoryResult,
  RecoveryHistorySummaryResult,
  RecoveryHistoryTrendResult,
  RecoveryHistorySummaryCompareResult,
  RecoveryHistorySummaryCompareActorsResult,
  RecoveryHistorySummaryCompareBreakdownResult,
} from './opsRecoveryService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const batchItem: RecoveryBatchItem = {
  documentId: 'doc-1',
  jobState: 'QUEUED',
  outcome: 'QUEUED',
  message: null,
  previewStatus: 'FAILED',
  failureCategory: 'TEMPORARY',
  previewFailureReason: 'parser-timeout',
  previewFailureCategory: 'TEMPORARY',
  previewLastUpdated: '2026-05-17T08:00:00Z',
  attempts: 2,
  nextAttemptAt: '2026-05-17T08:05:00Z',
};

const batchResult: RecoveryBatchResult = {
  domain: 'PREVIEW',
  mode: 'QUEUE_BY_REASON',
  windowDays: 7,
  maxDocuments: 100,
  totalCandidates: 42,
  scanned: 42,
  matched: 8,
  truncated: false,
  requested: 8,
  deduplicated: 8,
  queued: 7,
  skipped: 1,
  failed: 0,
  results: [batchItem],
  error: null,
};

const dryRunItem: RecoveryDryRunItem = {
  documentId: 'doc-2',
  name: 'doc.pdf',
  path: '/Sites/preview/doc.pdf',
  mimeType: 'application/pdf',
  previewStatus: 'FAILED',
  failureCategory: 'TEMPORARY',
  previewFailureReason: 'parser-timeout',
  previewFailureCategory: 'TEMPORARY',
  previewLastUpdated: '2026-05-17T08:00:00Z',
  predictedState: 'QUEUED',
  predictedOutcome: 'QUEUED',
  predictedReason: 'eligible',
};

const dryRunResult: RecoveryDryRunResult = {
  domain: 'PREVIEW',
  mode: 'QUEUE_BY_WINDOW',
  windowDays: 7,
  maxDocuments: 100,
  totalCandidates: 12,
  scanned: 12,
  matched: 5,
  truncated: false,
  estimatedQueued: 5,
  estimatedSkipped: 0,
  estimatedFailed: 0,
  samples: [dryRunItem],
  error: null,
};

const historyItem: RecoveryHistoryItem = {
  id: 'audit-1',
  nodeId: null,
  nodeName: null,
  eventType: 'OPS_RECOVERY_QUEUE_BY_REASON',
  mode: 'QUEUE_BY_REASON',
  actor: 'admin',
  eventTime: '2026-05-17T08:00:00Z',
  details: '{"queued":5}',
  previewStatus: null,
  previewFailureReason: null,
  previewFailureCategory: null,
  previewLastUpdated: null,
};

const historyResult: RecoveryHistoryResult = {
  domain: 'PREVIEW',
  windowDays: 7,
  limit: 20,
  page: 0,
  totalPages: 1,
  total: 1,
  modeFilter: null,
  actorFilter: null,
  eventTypeFilter: null,
  items: [historyItem],
};

const summaryResult: RecoveryHistorySummaryResult = {
  domain: 'PREVIEW',
  windowDays: 7,
  modeFilter: null,
  actorFilter: null,
  eventTypeFilter: null,
  total: 7,
  items: [
    {
      eventType: 'OPS_RECOVERY_QUEUE_BY_REASON',
      mode: 'QUEUE_BY_REASON',
      count: 5,
    },
  ],
  actorItems: [
    {
      actor: 'admin',
      count: 7,
    },
  ],
};

const trendResult: RecoveryHistoryTrendResult = {
  domain: 'PREVIEW',
  windowDays: 7,
  modeFilter: null,
  actorFilter: null,
  eventTypeFilter: null,
  total: 7,
  truncated: false,
  items: [
    {
      day: '2026-05-17',
      count: 3,
    },
  ],
};

const compareResult: RecoveryHistorySummaryCompareResult = {
  domain: 'PREVIEW',
  windowDays: 7,
  previousWindowDays: 7,
  modeFilter: null,
  actorFilter: null,
  eventTypeFilter: null,
  currentTotal: 10,
  previousTotal: 6,
  delta: 4,
  deltaPercent: 66.6,
  compareAvailable: true,
  truncated: false,
};

const compareBreakdownResult: RecoveryHistorySummaryCompareBreakdownResult = {
  domain: 'PREVIEW',
  windowDays: 7,
  previousWindowDays: 7,
  modeFilter: null,
  actorFilter: null,
  eventTypeFilter: null,
  compareAvailable: true,
  truncated: false,
  sortBy: 'DELTA_ABS_DESC',
  requestedLimit: 10,
  totalItems: 1,
  limited: false,
  items: [
    {
      eventType: 'OPS_RECOVERY_QUEUE_BY_REASON',
      mode: 'QUEUE_BY_REASON',
      currentCount: 6,
      previousCount: 2,
      delta: 4,
      deltaPercent: 200,
    },
  ],
};

const compareActorsResult: RecoveryHistorySummaryCompareActorsResult = {
  domain: 'PREVIEW',
  windowDays: 7,
  previousWindowDays: 7,
  modeFilter: null,
  actorFilter: null,
  eventTypeFilter: null,
  compareAvailable: true,
  truncated: false,
  sortBy: 'DELTA_ABS_DESC',
  requestedLimit: 10,
  totalItems: 1,
  limited: false,
  items: [
    {
      actor: 'admin',
      currentCount: 6,
      previousCount: 2,
      delta: 4,
      deltaPercent: 200,
    },
  ],
};

describe('opsRecoveryService core response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('batch actions', () => {
    it('queues by reason and guards the response', async () => {
      mockedApi.post.mockResolvedValueOnce(batchResult);
      const payload = {
        domain: 'PREVIEW',
        reason: 'parser-timeout',
        retryable: true,
      };

      await expect(opsRecoveryService.queueByReason(payload)).resolves.toEqual(batchResult);

      expect(mockedApi.post).toHaveBeenCalledWith('/ops/recovery/queue-by-reason', payload);
    });

    it('queues by window', async () => {
      mockedApi.post.mockResolvedValueOnce({ ...batchResult, mode: 'QUEUE_BY_WINDOW' });
      const payload = { domain: 'PREVIEW', days: 14 };

      await expect(opsRecoveryService.queueByWindow(payload)).resolves.toMatchObject({
        mode: 'QUEUE_BY_WINDOW',
      });

      expect(mockedApi.post).toHaveBeenCalledWith('/ops/recovery/queue-by-window', payload);
    });

    it('replays a batch', async () => {
      mockedApi.post.mockResolvedValueOnce({ ...batchResult, mode: 'REPLAY_BATCH' });
      const payload = { documentIds: ['doc-1'] };

      await expect(opsRecoveryService.replayBatch(payload)).resolves.toMatchObject({
        mode: 'REPLAY_BATCH',
      });

      expect(mockedApi.post).toHaveBeenCalledWith('/ops/recovery/replay-batch', payload);
    });

    it('clears a batch', async () => {
      mockedApi.post.mockResolvedValueOnce({ ...batchResult, mode: 'CLEAR_BATCH' });
      const payload = { entryKeys: ['key-1'] };

      await expect(opsRecoveryService.clearBatch(payload)).resolves.toMatchObject({
        mode: 'CLEAR_BATCH',
      });

      expect(mockedApi.post).toHaveBeenCalledWith('/ops/recovery/clear-batch', payload);
    });

    it('clears by filter', async () => {
      mockedApi.post.mockResolvedValueOnce({ ...batchResult, mode: 'CLEAR_BY_FILTER' });
      const payload = { reason: 'parser-timeout', days: 7 };

      await expect(opsRecoveryService.clearByFilter(payload)).resolves.toMatchObject({
        mode: 'CLEAR_BY_FILTER',
      });

      expect(mockedApi.post).toHaveBeenCalledWith('/ops/recovery/clear-by-filter', payload);
    });

    it('replays by filter', async () => {
      mockedApi.post.mockResolvedValueOnce({ ...batchResult, mode: 'REPLAY_BY_FILTER' });
      const payload = { reason: 'parser-timeout', force: true };

      await expect(opsRecoveryService.replayByFilter(payload)).resolves.toMatchObject({
        mode: 'REPLAY_BY_FILTER',
      });

      expect(mockedApi.post).toHaveBeenCalledWith('/ops/recovery/replay-by-filter', payload);
    });

    it('rejects HTML fallback on a POST endpoint', async () => {
      mockedApi.post.mockResolvedValueOnce('<!doctype html><html></html>');

      await expect(
        opsRecoveryService.queueByReason({ reason: 'parser-timeout' })
      ).rejects.toThrow(OPS_RECOVERY_UNEXPECTED_RESPONSE_MESSAGE);
    });

    it('rejects malformed nested result items', async () => {
      mockedApi.post.mockResolvedValueOnce({
        ...batchResult,
        results: [{ ...batchItem, attempts: 'two' }],
      });

      await expect(
        opsRecoveryService.queueByReason({ reason: 'parser-timeout' })
      ).rejects.toThrow(OPS_RECOVERY_UNEXPECTED_RESPONSE_MESSAGE);
    });
  });

  describe('dryRun', () => {
    it('forwards payload and guards the response', async () => {
      mockedApi.post.mockResolvedValueOnce(dryRunResult);
      const payload = { mode: 'QUEUE_BY_WINDOW', days: 7 };

      await expect(opsRecoveryService.dryRun(payload)).resolves.toEqual(dryRunResult);

      expect(mockedApi.post).toHaveBeenCalledWith('/ops/recovery/dry-run', payload);
    });

    it('rejects malformed dry-run samples', async () => {
      mockedApi.post.mockResolvedValueOnce({
        ...dryRunResult,
        samples: [{ ...dryRunItem, predictedState: 42 }],
      });

      await expect(opsRecoveryService.dryRun({})).rejects.toThrow(
        OPS_RECOVERY_UNEXPECTED_RESPONSE_MESSAGE
      );
    });
  });

  describe('getHistory', () => {
    it('forwards default params and guards the response', async () => {
      mockedApi.get.mockResolvedValueOnce(historyResult);

      await expect(opsRecoveryService.getHistory()).resolves.toEqual(historyResult);

      expect(mockedApi.get).toHaveBeenCalledWith('/ops/recovery/history', {
        params: {
          limit: 20,
          days: 7,
          page: 0,
          mode: undefined,
          actor: undefined,
          eventType: undefined,
        },
      });
    });

    it('forwards custom params', async () => {
      mockedApi.get.mockResolvedValueOnce(historyResult);

      await expect(
        opsRecoveryService.getHistory(
          50,
          14,
          'QUEUE_BY_REASON',
          2,
          'admin',
          'OPS_RECOVERY_QUEUE_BY_REASON'
        )
      ).resolves.toEqual(historyResult);

      expect(mockedApi.get).toHaveBeenCalledWith('/ops/recovery/history', {
        params: {
          limit: 50,
          days: 14,
          page: 2,
          mode: 'QUEUE_BY_REASON',
          actor: 'admin',
          eventType: 'OPS_RECOVERY_QUEUE_BY_REASON',
        },
      });
    });

    it('rejects HTML fallback on a GET endpoint', async () => {
      mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

      await expect(opsRecoveryService.getHistory()).rejects.toThrow(
        OPS_RECOVERY_UNEXPECTED_RESPONSE_MESSAGE
      );
    });

    it('rejects malformed history items', async () => {
      mockedApi.get.mockResolvedValueOnce({
        ...historyResult,
        items: [{ ...historyItem, eventType: 42 }],
      });

      await expect(opsRecoveryService.getHistory()).rejects.toThrow(
        OPS_RECOVERY_UNEXPECTED_RESPONSE_MESSAGE
      );
    });
  });

  describe('getHistorySummary', () => {
    it('forwards default params and guards the response', async () => {
      mockedApi.get.mockResolvedValueOnce(summaryResult);

      await expect(opsRecoveryService.getHistorySummary()).resolves.toEqual(summaryResult);

      expect(mockedApi.get).toHaveBeenCalledWith('/ops/recovery/history/summary', {
        params: {
          days: 7,
          mode: undefined,
          actor: undefined,
          eventType: undefined,
        },
      });
    });

    it('forwards custom params', async () => {
      mockedApi.get.mockResolvedValueOnce(summaryResult);

      await expect(
        opsRecoveryService.getHistorySummary(
          30,
          'REPLAY_BATCH',
          'admin',
          'OPS_RECOVERY_REPLAY_BATCH'
        )
      ).resolves.toEqual(summaryResult);

      expect(mockedApi.get).toHaveBeenCalledWith('/ops/recovery/history/summary', {
        params: {
          days: 30,
          mode: 'REPLAY_BATCH',
          actor: 'admin',
          eventType: 'OPS_RECOVERY_REPLAY_BATCH',
        },
      });
    });

    it('rejects malformed summary count values', async () => {
      mockedApi.get.mockResolvedValueOnce({
        ...summaryResult,
        items: [
          {
            eventType: 'OPS_RECOVERY_QUEUE_BY_REASON',
            mode: 'QUEUE_BY_REASON',
            count: Number.NaN,
          },
        ],
      });

      await expect(opsRecoveryService.getHistorySummary()).rejects.toThrow(
        OPS_RECOVERY_UNEXPECTED_RESPONSE_MESSAGE
      );
    });
  });

  describe('getHistorySummaryTrend', () => {
    it('forwards default params and guards the response', async () => {
      mockedApi.get.mockResolvedValueOnce(trendResult);

      await expect(opsRecoveryService.getHistorySummaryTrend()).resolves.toEqual(trendResult);

      expect(mockedApi.get).toHaveBeenCalledWith('/ops/recovery/history/summary/trend', {
        params: {
          days: 7,
          mode: undefined,
          actor: undefined,
          eventType: undefined,
        },
      });
    });

    it('rejects malformed trend items', async () => {
      mockedApi.get.mockResolvedValueOnce({
        ...trendResult,
        items: [{ day: 42, count: 1 }],
      });

      await expect(opsRecoveryService.getHistorySummaryTrend()).rejects.toThrow(
        OPS_RECOVERY_UNEXPECTED_RESPONSE_MESSAGE
      );
    });
  });

  describe('getHistorySummaryCompare', () => {
    it('forwards default params and guards the response', async () => {
      mockedApi.get.mockResolvedValueOnce(compareResult);

      await expect(opsRecoveryService.getHistorySummaryCompare()).resolves.toEqual(compareResult);

      expect(mockedApi.get).toHaveBeenCalledWith('/ops/recovery/history/summary/compare', {
        params: {
          days: 7,
          mode: undefined,
          actor: undefined,
          eventType: undefined,
        },
      });
    });

    it('rejects malformed compare deltaPercent', async () => {
      mockedApi.get.mockResolvedValueOnce({ ...compareResult, deltaPercent: 'fast' });

      await expect(opsRecoveryService.getHistorySummaryCompare()).rejects.toThrow(
        OPS_RECOVERY_UNEXPECTED_RESPONSE_MESSAGE
      );
    });
  });

  describe('getHistorySummaryCompareBreakdown', () => {
    it('forwards default params and guards the response', async () => {
      mockedApi.get.mockResolvedValueOnce(compareBreakdownResult);

      await expect(opsRecoveryService.getHistorySummaryCompareBreakdown()).resolves.toEqual(
        compareBreakdownResult
      );

      expect(mockedApi.get).toHaveBeenCalledWith(
        '/ops/recovery/history/summary/compare/breakdown',
        {
          params: {
            days: 7,
            mode: undefined,
            actor: undefined,
            eventType: undefined,
            limit: 10,
            sort: 'DELTA_ABS_DESC',
          },
        }
      );
    });

    it('forwards custom limit and sort', async () => {
      mockedApi.get.mockResolvedValueOnce(compareBreakdownResult);

      await expect(
        opsRecoveryService.getHistorySummaryCompareBreakdown(
          14,
          'QUEUE_BY_REASON',
          'admin',
          'OPS_RECOVERY_QUEUE_BY_REASON',
          25,
          'DELTA_DESC'
        )
      ).resolves.toEqual(compareBreakdownResult);

      expect(mockedApi.get).toHaveBeenCalledWith(
        '/ops/recovery/history/summary/compare/breakdown',
        {
          params: {
            days: 14,
            mode: 'QUEUE_BY_REASON',
            actor: 'admin',
            eventType: 'OPS_RECOVERY_QUEUE_BY_REASON',
            limit: 25,
            sort: 'DELTA_DESC',
          },
        }
      );
    });

    it('rejects malformed breakdown items', async () => {
      mockedApi.get.mockResolvedValueOnce({
        ...compareBreakdownResult,
        items: [{ ...compareBreakdownResult.items[0], delta: 'big' }],
      });

      await expect(opsRecoveryService.getHistorySummaryCompareBreakdown()).rejects.toThrow(
        OPS_RECOVERY_UNEXPECTED_RESPONSE_MESSAGE
      );
    });
  });

  describe('getHistorySummaryCompareActors', () => {
    it('forwards default params and guards the response', async () => {
      mockedApi.get.mockResolvedValueOnce(compareActorsResult);

      await expect(opsRecoveryService.getHistorySummaryCompareActors()).resolves.toEqual(
        compareActorsResult
      );

      expect(mockedApi.get).toHaveBeenCalledWith(
        '/ops/recovery/history/summary/compare/actors',
        {
          params: {
            days: 7,
            mode: undefined,
            actor: undefined,
            eventType: undefined,
            limit: 10,
            sort: 'DELTA_ABS_DESC',
          },
        }
      );
    });

    it('forwards custom limit and sort', async () => {
      mockedApi.get.mockResolvedValueOnce(compareActorsResult);

      await expect(
        opsRecoveryService.getHistorySummaryCompareActors(
          14,
          'QUEUE_BY_REASON',
          'admin',
          'OPS_RECOVERY_QUEUE_BY_REASON',
          5,
          'CURRENT_DESC'
        )
      ).resolves.toEqual(compareActorsResult);

      expect(mockedApi.get).toHaveBeenCalledWith(
        '/ops/recovery/history/summary/compare/actors',
        {
          params: {
            days: 14,
            mode: 'QUEUE_BY_REASON',
            actor: 'admin',
            eventType: 'OPS_RECOVERY_QUEUE_BY_REASON',
            limit: 5,
            sort: 'CURRENT_DESC',
          },
        }
      );
    });

    it('rejects malformed compare actor items', async () => {
      mockedApi.get.mockResolvedValueOnce({
        ...compareActorsResult,
        items: [{ ...compareActorsResult.items[0], currentCount: Number.NaN }],
      });

      await expect(opsRecoveryService.getHistorySummaryCompareActors()).rejects.toThrow(
        OPS_RECOVERY_UNEXPECTED_RESPONSE_MESSAGE
      );
    });
  });
});
