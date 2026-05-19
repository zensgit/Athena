import api from './api';

export interface BulkOperationResult {
  operation: string;
  totalRequested: number;
  successCount: number;
  failureCount: number;
  successfulIds: string[];
  failures: Record<string, string>;
}

export interface BulkHistoryParams {
  page?: number;
  size?: number;
  limit?: number;
  eventType?: string;
  actor?: string;
  from?: string;
  to?: string;
}

interface BulkHistoryApiItem {
  id?: string | null;
  eventType?: string | null;
  actor?: string | null;
  username?: string | null;
  eventTime?: string | null;
  time?: string | null;
  details?: string | null;
}

interface BulkHistoryApiPage {
  items?: BulkHistoryApiItem[];
  content?: BulkHistoryApiItem[];
  total?: number;
  totalElements?: number;
  page?: number;
  number?: number;
  size?: number;
}

export interface BulkHistoryItem {
  id: string | null;
  eventType: string | null;
  actor: string | null;
  time: string | null;
  details: string | null;
}

export interface BulkHistoryResult {
  items: BulkHistoryItem[];
  total: number;
  page: number;
  size: number;
}

interface BulkHistorySummaryApiEventTypeItem {
  eventType?: string | null;
  type?: string | null;
  count?: number | null;
  total?: number | null;
}

interface BulkHistorySummaryApiActorItem {
  actor?: string | null;
  username?: string | null;
  count?: number | null;
  total?: number | null;
}

interface BulkHistorySummaryApiResponse {
  total?: number;
  totalEvents?: number;
  eventTypeItems?: BulkHistorySummaryApiEventTypeItem[] | null;
  items?: BulkHistorySummaryApiEventTypeItem[] | null;
  actorItems?: BulkHistorySummaryApiActorItem[] | null;
}

interface BulkHistoryTrendApiItem {
  date?: string | null;
  day?: string | null;
  bucketDate?: string | null;
  count?: number | null;
  total?: number | null;
}

interface BulkHistoryTrendApiResponse {
  items?: BulkHistoryTrendApiItem[] | null;
  trend?: BulkHistoryTrendApiItem[] | null;
  truncated?: boolean;
  scanLimit?: number;
}

export interface BulkHistorySummaryEventTypeItem {
  eventType: string | null;
  count: number;
}

export interface BulkHistorySummaryActorItem {
  actor: string | null;
  count: number;
}

export interface BulkHistorySummaryResult {
  total: number;
  eventTypeItems: BulkHistorySummaryEventTypeItem[];
  actorItems: BulkHistorySummaryActorItem[];
}

export interface BulkHistoryTrendItem {
  date: string | null;
  count: number;
}

export interface BulkHistoryTrendResult {
  items: BulkHistoryTrendItem[];
  truncated: boolean;
  scanLimit: number | null;
}

const normalizePositiveInt = (value: number | undefined, fallback: number): number => {
  if (typeof value !== 'number' || !Number.isFinite(value) || value <= 0) {
    return fallback;
  }
  return Math.floor(value);
};

const normalizeHistoryItem = (item: BulkHistoryApiItem): BulkHistoryItem => ({
  id: item.id ?? null,
  eventType: item.eventType ?? null,
  actor: item.actor ?? item.username ?? null,
  time: item.eventTime ?? item.time ?? null,
  details: item.details ?? null,
});

const normalizeCount = (value: number | null | undefined): number => {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return 0;
  }
  return Math.max(0, Math.floor(value));
};

const normalizeHistorySummaryEventTypeItem = (
  item: BulkHistorySummaryApiEventTypeItem
): BulkHistorySummaryEventTypeItem => ({
  eventType: item.eventType ?? item.type ?? null,
  count: normalizeCount(item.count ?? item.total),
});

const normalizeHistorySummaryActorItem = (
  item: BulkHistorySummaryApiActorItem
): BulkHistorySummaryActorItem => ({
  actor: item.actor ?? item.username ?? null,
  count: normalizeCount(item.count ?? item.total),
});

const normalizeHistoryTrendItem = (
  item: BulkHistoryTrendApiItem
): BulkHistoryTrendItem => ({
  date: item.date ?? item.day ?? item.bucketDate ?? null,
  count: normalizeCount(item.count ?? item.total),
});

// Phase 5 Mocked harness can serve SPA index.html with HTTP 200 for unmocked
// routes, and a backend route may be absent entirely. bulkDelete consumers read
// outcome counts directly, so a malformed body must surface a recognizable
// synthetic error instead of silently rendering garbage. The history list /
// summary / trend panels are dashboard-style and intentionally degrade to an
// empty/zero result rather than an error toast (mirrors
// mailAutomationService.listProviderPresets).
export const BULK_OPERATION_UNEXPECTED_RESPONSE_MESSAGE =
  'Bulk operation endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isFiniteNumber = (value: unknown): value is number => (
  typeof value === 'number' && Number.isFinite(value)
);

const isStringArray = (value: unknown): value is string[] => (
  Array.isArray(value) && value.every((entry) => typeof entry === 'string')
);

const isStringRecord = (value: unknown): value is Record<string, string> => {
  if (!isObject(value)) {
    return false;
  }
  return Object.values(value).every((entry) => typeof entry === 'string');
};

const assertUnexpectedResponse = (): never => {
  throw new Error(BULK_OPERATION_UNEXPECTED_RESPONSE_MESSAGE);
};

const isBulkOperationResult = (value: unknown): value is BulkOperationResult => (
  isObject(value)
  && typeof value.operation === 'string'
  && isFiniteNumber(value.totalRequested)
  && isFiniteNumber(value.successCount)
  && isFiniteNumber(value.failureCount)
  && isStringArray(value.successfulIds)
  && isStringRecord(value.failures)
);

const assertBulkOperationResult = (value: unknown): BulkOperationResult => (
  isBulkOperationResult(value) ? value : assertUnexpectedResponse()
);

class BulkOperationService {
  async bulkDelete(ids: string[]): Promise<BulkOperationResult> {
    const response = await api.post<unknown>('/bulk/delete', { ids });
    return assertBulkOperationResult(response);
  }

  async listHistory(params?: BulkHistoryParams): Promise<BulkHistoryResult> {
    const page = Math.max(0, Math.floor(params?.page ?? 0));
    const size = normalizePositiveInt(params?.size ?? params?.limit, 20);

    const response = await api.get<unknown>('/bulk/history', {
      params: {
        page,
        size,
        eventType: params?.eventType || undefined,
        actor: params?.actor || undefined,
        from: params?.from || undefined,
        to: params?.to || undefined,
      },
    });

    if (Array.isArray(response)) {
      // Status-quo equivalent for an all-valid array; for an array carrying
      // null/primitive entries this drops them rather than crashing in
      // normalizeHistoryItem (was a TypeError on null) or emitting all-null
      // fake records (deliberate improvement, not status-quo equivalent).
      const items = response
        .filter(isObject)
        .map((item) => normalizeHistoryItem(item as BulkHistoryApiItem));
      return { items, total: items.length, page, size };
    }

    // Top-level null / string (HTML fallback) / any non-object: degrade to an
    // empty page. For HTML strings this matches the prior behavior; for null
    // it is a deliberate improvement over the prior TypeError.
    if (!isObject(response)) {
      return { items: [], total: 0, page, size };
    }

    const pageResponse = response as unknown as BulkHistoryApiPage;
    const rawContent = Array.isArray(pageResponse.items)
      ? pageResponse.items
      : (Array.isArray(pageResponse.content) ? pageResponse.content : []);
    const items = rawContent
      .filter(isObject)
      .map((item) => normalizeHistoryItem(item as BulkHistoryApiItem));
    return {
      items,
      total: typeof pageResponse.total === 'number'
        ? pageResponse.total
        : (typeof pageResponse.totalElements === 'number' ? pageResponse.totalElements : items.length),
      page: typeof pageResponse.page === 'number'
        ? pageResponse.page
        : (typeof pageResponse.number === 'number' ? pageResponse.number : page),
      size: typeof pageResponse.size === 'number' ? pageResponse.size : size,
    };
  }

  async exportHistoryCsv(params?: BulkHistoryParams): Promise<void> {
    const size = normalizePositiveInt(params?.size ?? params?.limit, 500);
    const filename = `bulk_governance_timeline_${new Date().toISOString().replace(/[:.]/g, '-')}.csv`;

    return api.downloadFile('/bulk/history/export', filename, {
      params: {
        limit: params?.limit ?? size,
        eventType: params?.eventType || undefined,
        actor: params?.actor || undefined,
        from: params?.from || undefined,
        to: params?.to || undefined,
      },
    });
  }

  async listHistorySummary(params?: BulkHistoryParams): Promise<BulkHistorySummaryResult> {
    const topN = normalizePositiveInt(params?.limit ?? params?.size, 10);
    const response = await api.get<unknown>('/bulk/history/summary', {
      params: {
        topN,
        eventType: params?.eventType || undefined,
        actor: params?.actor || undefined,
        from: params?.from || undefined,
        to: params?.to || undefined,
      },
    });

    // Top-level null / string (HTML fallback) / any non-object: degrade to a
    // zero summary. HTML strings already produced this; null previously threw.
    if (!isObject(response)) {
      return { total: 0, eventTypeItems: [], actorItems: [] };
    }

    const summaryResponse = response as unknown as BulkHistorySummaryApiResponse;
    const eventTypeApiItems = (Array.isArray(summaryResponse.eventTypeItems)
      ? summaryResponse.eventTypeItems
      : (Array.isArray(summaryResponse.items) ? summaryResponse.items : []))
      .filter(isObject);
    const actorApiItems = (Array.isArray(summaryResponse.actorItems)
      ? summaryResponse.actorItems
      : [])
      .filter(isObject);
    const eventTypeItems = eventTypeApiItems.map(
      (item) => normalizeHistorySummaryEventTypeItem(item as BulkHistorySummaryApiEventTypeItem)
    );
    const actorItems = actorApiItems.map(
      (item) => normalizeHistorySummaryActorItem(item as BulkHistorySummaryApiActorItem)
    );
    const fallbackTotal = eventTypeItems.reduce((sum, item) => sum + item.count, 0);

    return {
      total: normalizeCount(
        typeof summaryResponse.total === 'number'
          ? summaryResponse.total
          : (typeof summaryResponse.totalEvents === 'number' ? summaryResponse.totalEvents : fallbackTotal)
      ),
      eventTypeItems,
      actorItems,
    };
  }

  async exportHistorySummaryCsv(params?: BulkHistoryParams): Promise<void> {
    const topN = normalizePositiveInt(params?.limit ?? params?.size, 20);
    const filename = `bulk_governance_summary_${new Date().toISOString().replace(/[:.]/g, '-')}.csv`;

    return api.downloadFile('/bulk/history/summary/export', filename, {
      params: {
        topN,
        eventType: params?.eventType || undefined,
        actor: params?.actor || undefined,
        from: params?.from || undefined,
        to: params?.to || undefined,
      },
    });
  }

  async listHistoryTrend(params?: BulkHistoryParams): Promise<BulkHistoryTrendResult> {
    const response = await api.get<unknown>('/bulk/history/summary/trend', {
      params: {
        eventType: params?.eventType || undefined,
        actor: params?.actor || undefined,
        from: params?.from || undefined,
        to: params?.to || undefined,
      },
    });

    // Top-level null / string (HTML fallback) / any non-object: degrade to an
    // empty trend. HTML strings already produced this; null previously threw.
    if (!isObject(response)) {
      return { items: [], truncated: false, scanLimit: null };
    }

    const trendResponse = response as unknown as BulkHistoryTrendApiResponse;
    const trendItems = (Array.isArray(trendResponse.items)
      ? trendResponse.items
      : (Array.isArray(trendResponse.trend) ? trendResponse.trend : []))
      .filter(isObject);
    return {
      items: trendItems.map((item) => normalizeHistoryTrendItem(item as BulkHistoryTrendApiItem)),
      truncated: Boolean(trendResponse.truncated),
      scanLimit: typeof trendResponse.scanLimit === 'number'
        ? Math.max(0, Math.floor(trendResponse.scanLimit))
        : null,
    };
  }

  async exportHistoryTrendCsv(params?: BulkHistoryParams): Promise<void> {
    const filename = `bulk_governance_trend_${new Date().toISOString().replace(/[:.]/g, '-')}.csv`;
    return api.downloadFile('/bulk/history/summary/trend/export', filename, {
      params: {
        eventType: params?.eventType || undefined,
        actor: params?.actor || undefined,
        from: params?.from || undefined,
        to: params?.to || undefined,
      },
    });
  }
}

const bulkOperationService = new BulkOperationService();
export default bulkOperationService;
