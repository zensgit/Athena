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

class BulkOperationService {
  async bulkDelete(ids: string[]): Promise<BulkOperationResult> {
    return api.post<BulkOperationResult>('/bulk/delete', { ids });
  }

  async listHistory(params?: BulkHistoryParams): Promise<BulkHistoryResult> {
    const page = Math.max(0, Math.floor(params?.page ?? 0));
    const size = normalizePositiveInt(params?.size ?? params?.limit, 20);

    const response = await api.get<BulkHistoryApiPage | BulkHistoryApiItem[]>('/bulk/history', {
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
      return {
        items: response.map(normalizeHistoryItem),
        total: response.length,
        page,
        size,
      };
    }

    const content = Array.isArray(response.items)
      ? response.items
      : (Array.isArray(response.content) ? response.content : []);
    return {
      items: content.map(normalizeHistoryItem),
      total: typeof response.total === 'number'
        ? response.total
        : (typeof response.totalElements === 'number' ? response.totalElements : content.length),
      page: typeof response.page === 'number'
        ? response.page
        : (typeof response.number === 'number' ? response.number : page),
      size: typeof response.size === 'number' ? response.size : size,
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
    const response = await api.get<BulkHistorySummaryApiResponse>('/bulk/history/summary', {
      params: {
        topN,
        eventType: params?.eventType || undefined,
        actor: params?.actor || undefined,
        from: params?.from || undefined,
        to: params?.to || undefined,
      },
    });

    const eventTypeApiItems = Array.isArray(response.eventTypeItems)
      ? response.eventTypeItems
      : (Array.isArray(response.items) ? response.items : []);
    const actorApiItems = Array.isArray(response.actorItems) ? response.actorItems : [];
    const eventTypeItems = eventTypeApiItems.map(normalizeHistorySummaryEventTypeItem);
    const actorItems = actorApiItems.map(normalizeHistorySummaryActorItem);
    const fallbackTotal = eventTypeItems.reduce((sum, item) => sum + item.count, 0);

    return {
      total: normalizeCount(
        typeof response.total === 'number'
          ? response.total
          : (typeof response.totalEvents === 'number' ? response.totalEvents : fallbackTotal)
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
    const response = await api.get<BulkHistoryTrendApiResponse>('/bulk/history/summary/trend', {
      params: {
        eventType: params?.eventType || undefined,
        actor: params?.actor || undefined,
        from: params?.from || undefined,
        to: params?.to || undefined,
      },
    });
    const trendItems = Array.isArray(response.items)
      ? response.items
      : (Array.isArray(response.trend) ? response.trend : []);
    return {
      items: trendItems.map(normalizeHistoryTrendItem),
      truncated: Boolean(response.truncated),
      scanLimit: typeof response.scanLimit === 'number' ? Math.max(0, Math.floor(response.scanLimit)) : null,
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
