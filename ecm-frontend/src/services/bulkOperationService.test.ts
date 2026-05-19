import api from './api';
import bulkOperationService, {
  BulkOperationResult,
  BULK_OPERATION_UNEXPECTED_RESPONSE_MESSAGE,
} from './bulkOperationService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    put: jest.fn(),
    delete: jest.fn(),
    downloadFile: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const validBulkResult: BulkOperationResult = {
  operation: 'DELETE',
  totalRequested: 3,
  successCount: 2,
  failureCount: 1,
  successfulIds: ['a', 'b'],
  failures: { c: 'locked' },
};

const validHistoryApiItem = {
  id: 'h1',
  eventType: 'BULK_DELETE',
  actor: 'admin',
  eventTime: '2026-05-18T00:00:00Z',
  details: 'removed 3',
};

const HTML_FALLBACK = '<!doctype html><html><body>app shell</body></html>';

beforeEach(() => {
  jest.clearAllMocks();
});

describe('bulkOperationService response shape guards', () => {
  // --- bulkDelete: tightened to throw on malformed body ---

  it('bulkDelete returns the result and preserves the request body when valid', async () => {
    mockedApi.post.mockResolvedValueOnce(validBulkResult);

    await expect(bulkOperationService.bulkDelete(['a', 'b', 'c'])).resolves.toEqual(validBulkResult);
    expect(mockedApi.post).toHaveBeenCalledWith('/bulk/delete', { ids: ['a', 'b', 'c'] });
  });

  it('bulkDelete throws the sentinel for HTML fallback, null, and field-level malformations', async () => {
    const malformed: unknown[] = [
      HTML_FALLBACK,
      null,
      undefined,
      // missing failures
      { operation: 'DELETE', totalRequested: 1, successCount: 1, failureCount: 0, successfulIds: ['a'] },
      // wrong numeric type
      { ...validBulkResult, successCount: '2' },
      // successfulIds carries a non-string
      { ...validBulkResult, successfulIds: ['a', 3] },
      // failures value is not a string
      { ...validBulkResult, failures: { c: 5 } },
    ];

    for (const body of malformed) {
      mockedApi.post.mockResolvedValueOnce(body);
      await expect(bulkOperationService.bulkDelete(['a'])).rejects.toThrow(
        BULK_OPERATION_UNEXPECTED_RESPONSE_MESSAGE
      );
    }
  });

  // --- listHistory: do-not-regress + deliberate improvements ---

  it('listHistory normalizes a valid array and a valid page object and preserves params', async () => {
    mockedApi.get.mockResolvedValueOnce([validHistoryApiItem, { id: 'h2' }]);
    await expect(bulkOperationService.listHistory({ page: 1, size: 5 })).resolves.toEqual({
      items: [
        { id: 'h1', eventType: 'BULK_DELETE', actor: 'admin', time: '2026-05-18T00:00:00Z', details: 'removed 3' },
        { id: 'h2', eventType: null, actor: null, time: null, details: null },
      ],
      total: 2,
      page: 1,
      size: 5,
    });
    expect(mockedApi.get).toHaveBeenCalledWith('/bulk/history', {
      params: { page: 1, size: 5, eventType: undefined, actor: undefined, from: undefined, to: undefined },
    });

    mockedApi.get.mockResolvedValueOnce({ content: [validHistoryApiItem], total: 57, page: 2, size: 10 });
    await expect(bulkOperationService.listHistory()).resolves.toEqual({
      items: [
        { id: 'h1', eventType: 'BULK_DELETE', actor: 'admin', time: '2026-05-18T00:00:00Z', details: 'removed 3' },
      ],
      total: 57,
      page: 2,
      size: 10,
    });
  });

  it('listHistory degrades HTML fallback and null to an empty page without throwing', async () => {
    mockedApi.get.mockResolvedValueOnce(HTML_FALLBACK);
    await expect(bulkOperationService.listHistory()).resolves.toEqual({
      items: [],
      total: 0,
      page: 0,
      size: 20,
    });

    // Deliberate improvement: this previously threw a TypeError on null.items.
    mockedApi.get.mockResolvedValueOnce(null);
    await expect(bulkOperationService.listHistory()).resolves.toEqual({
      items: [],
      total: 0,
      page: 0,
      size: 20,
    });
  });

  it('listHistory drops non-object array entries instead of crashing or emitting fake records', async () => {
    mockedApi.get.mockResolvedValueOnce([validHistoryApiItem, null, 'junk', 42, { id: 'h3' }]);
    await expect(bulkOperationService.listHistory()).resolves.toEqual({
      items: [
        { id: 'h1', eventType: 'BULK_DELETE', actor: 'admin', time: '2026-05-18T00:00:00Z', details: 'removed 3' },
        { id: 'h3', eventType: null, actor: null, time: null, details: null },
      ],
      total: 2,
      page: 0,
      size: 20,
    });
  });

  // --- listHistorySummary: do-not-regress + deliberate improvements ---

  it('listHistorySummary normalizes a valid response and filters non-object items', async () => {
    mockedApi.get.mockResolvedValueOnce({
      total: 9,
      eventTypeItems: [{ eventType: 'DELETE', count: 5 }, null, 'x'],
      actorItems: [{ actor: 'admin', count: 9 }],
    });
    await expect(bulkOperationService.listHistorySummary()).resolves.toEqual({
      total: 9,
      eventTypeItems: [{ eventType: 'DELETE', count: 5 }],
      actorItems: [{ actor: 'admin', count: 9 }],
    });
    expect(mockedApi.get).toHaveBeenCalledWith('/bulk/history/summary', {
      params: { topN: 10, eventType: undefined, actor: undefined, from: undefined, to: undefined },
    });
  });

  it('listHistorySummary degrades HTML fallback and null to a zero summary without throwing', async () => {
    mockedApi.get.mockResolvedValueOnce(HTML_FALLBACK);
    await expect(bulkOperationService.listHistorySummary()).resolves.toEqual({
      total: 0,
      eventTypeItems: [],
      actorItems: [],
    });

    mockedApi.get.mockResolvedValueOnce(null);
    await expect(bulkOperationService.listHistorySummary()).resolves.toEqual({
      total: 0,
      eventTypeItems: [],
      actorItems: [],
    });
  });

  // --- listHistoryTrend: do-not-regress + deliberate improvements ---

  it('listHistoryTrend normalizes a valid response and filters non-object items', async () => {
    mockedApi.get.mockResolvedValueOnce({
      trend: [{ date: '2026-05-18', count: 4 }, null, 7],
      truncated: true,
      scanLimit: 1000,
    });
    await expect(bulkOperationService.listHistoryTrend()).resolves.toEqual({
      items: [{ date: '2026-05-18', count: 4 }],
      truncated: true,
      scanLimit: 1000,
    });
    expect(mockedApi.get).toHaveBeenCalledWith('/bulk/history/summary/trend', {
      params: { eventType: undefined, actor: undefined, from: undefined, to: undefined },
    });
  });

  it('listHistoryTrend degrades HTML fallback and null to an empty trend without throwing', async () => {
    mockedApi.get.mockResolvedValueOnce(HTML_FALLBACK);
    await expect(bulkOperationService.listHistoryTrend()).resolves.toEqual({
      items: [],
      truncated: false,
      scanLimit: null,
    });

    mockedApi.get.mockResolvedValueOnce(null);
    await expect(bulkOperationService.listHistoryTrend()).resolves.toEqual({
      items: [],
      truncated: false,
      scanLimit: null,
    });
  });

  // B1: an empty object exercises the response-object fallback path (isObject
  // true, every Array.isArray branch false) — distinct from the non-object
  // short-circuit, and must stay status-quo equivalent (empty/zero, no throw).
  it('lists degrade an empty object via the response-object fallback path', async () => {
    mockedApi.get.mockResolvedValueOnce({});
    await expect(bulkOperationService.listHistory()).resolves.toEqual({
      items: [],
      total: 0,
      page: 0,
      size: 20,
    });

    mockedApi.get.mockResolvedValueOnce({});
    await expect(bulkOperationService.listHistorySummary()).resolves.toEqual({
      total: 0,
      eventTypeItems: [],
      actorItems: [],
    });

    mockedApi.get.mockResolvedValueOnce({});
    await expect(bulkOperationService.listHistoryTrend()).resolves.toEqual({
      items: [],
      truncated: false,
      scanLimit: null,
    });
  });
});
