import { RecoveryHistoryExportAsyncRequestSnapshot } from 'services/opsRecoveryService';

const normalizeValue = (value?: string | null): string | null => {
  const normalized = String(value || '').trim();
  return normalized.length > 0 ? normalized : null;
};

export const formatRecoveryHistoryExportAsyncRequestPrimary = (
  request?: RecoveryHistoryExportAsyncRequestSnapshot | null,
  fallbackExportType?: string | null
): string => {
  const exportType = normalizeValue(request?.exportType) || normalizeValue(fallbackExportType) || 'HISTORY';
  const parts = [exportType];

  if (typeof request?.days === 'number' && Number.isFinite(request.days) && request.days > 0) {
    parts.push(`${request.days}d`);
  }
  if (typeof request?.limit === 'number' && Number.isFinite(request.limit) && request.limit > 0) {
    parts.push(`${request.limit} rows`);
  }

  if (exportType === 'HISTORY_COMPARE_BREAKDOWN'
    && typeof request?.compareBreakdownLimit === 'number'
    && Number.isFinite(request.compareBreakdownLimit)
    && request.compareBreakdownLimit > 0) {
    parts.push(`breakdown ${request.compareBreakdownLimit}`);
  }
  if (exportType === 'HISTORY_COMPARE_ACTORS'
    && typeof request?.compareActorLimit === 'number'
    && Number.isFinite(request.compareActorLimit)
    && request.compareActorLimit > 0) {
    parts.push(`actors ${request.compareActorLimit}`);
  }

  return parts.join(' · ');
};

export const formatRecoveryHistoryExportAsyncRequestDetails = (
  request?: RecoveryHistoryExportAsyncRequestSnapshot | null
): string | null => {
  if (!request) {
    return null;
  }
  const parts = [
    normalizeValue(request.mode) ? `mode=${request.mode}` : null,
    normalizeValue(request.actor) ? `actor=${request.actor}` : null,
    normalizeValue(request.eventType) ? `event=${request.eventType}` : null,
    normalizeValue(request.compareBreakdownSort) ? `breakdownSort=${request.compareBreakdownSort}` : null,
    normalizeValue(request.compareActorSort) ? `actorSort=${request.compareActorSort}` : null,
  ].filter((value): value is string => Boolean(value));

  return parts.length > 0 ? parts.join(' · ') : null;
};
