export type PreviewStatusChipColor = 'default' | 'success' | 'warning' | 'error' | 'info';
export type PreviewFailureCategory = 'UNSUPPORTED' | 'TEMPORARY' | 'PERMANENT' | string | null | undefined;
export type PreviewFailureLike = {
  previewStatus?: string | null;
  previewFailureCategory?: PreviewFailureCategory;
  previewFailureReason?: string | null;
  mimeType?: string | null;
};

export type PreviewFailureSummary = {
  totalFailed: number;
  retryableFailed: number;
  permanentFailed: number;
  unsupportedFailed: number;
  retryableReasons: Array<{ reason: string; count: number }>;
};

export type PreviewBatchOperationProgress = {
  processed: number;
  total: number;
  queued: number;
  skipped: number;
  failed: number;
};

const PREVIEW_UNSUPPORTED_MIME_TYPES = new Set([
  'application/octet-stream',
  'binary/octet-stream',
  'application/x-empty',
]);

export const normalizeMimeType = (mimeType?: string | null): string | null => {
  if (!mimeType) {
    return null;
  }
  const normalized = mimeType.split(';')[0]?.trim().toLowerCase();
  return normalized || null;
};

export const isUnsupportedPreviewMimeType = (mimeType?: string | null): boolean => {
  const normalized = normalizeMimeType(mimeType);
  return Boolean(normalized && PREVIEW_UNSUPPORTED_MIME_TYPES.has(normalized));
};

export const isUnsupportedPreviewReason = (failureReason?: string | null): boolean => {
  const normalized = (failureReason || '')
    .trim()
    .toLowerCase()
    .replace(/[_-]+/g, ' ')
    .replace(/\s+/g, ' ');
  if (!normalized) {
    return false;
  }
  return normalized.includes('not supported')
    || normalized.includes('unsupported mime')
    || normalized.includes('unsupported media type')
    || normalized.includes('unsupported file type')
    || normalized.includes('unsupported format');
};

export const isTemporaryPreviewReason = (failureReason?: string | null): boolean => {
  const normalized = (failureReason || '')
    .trim()
    .toLowerCase()
    .replace(/\s+/g, ' ');
  if (!normalized) {
    return false;
  }
  return normalized.includes('timeout')
    || normalized.includes('timed out')
    || normalized.includes('temporar')
    || normalized.includes('connection reset')
    || normalized.includes('connection refused')
    || normalized.includes('service unavailable')
    || normalized.includes('502')
    || normalized.includes('503')
    || normalized.includes('504');
};

export const isUnsupportedPreviewFailure = (
  failureCategory?: PreviewFailureCategory,
  mimeType?: string | null,
  failureReason?: string | null
): boolean => {
  const normalizedCategory = (failureCategory || '').toUpperCase();
  if (normalizedCategory === 'UNSUPPORTED' || normalizedCategory.includes('UNSUPPORTED')) {
    return true;
  }
  if (isUnsupportedPreviewReason(failureReason)) {
    return true;
  }
  return isUnsupportedPreviewMimeType(mimeType);
};

export const isRetryablePreviewFailure = (
  failureCategory?: PreviewFailureCategory,
  mimeType?: string | null,
  failureReason?: string | null
): boolean => {
  if (isUnsupportedPreviewFailure(failureCategory, mimeType, failureReason)) {
    return false;
  }
  const normalizedCategory = (failureCategory || '').toUpperCase().trim();
  if (normalizedCategory === 'TEMPORARY') {
    return true;
  }
  // Backwards/edge-case fallback: when category is missing or unknown, only allow retry if
  // the reason contains transient hints (safe-by-default).
  return isTemporaryPreviewReason(failureReason);
};

export const getEffectivePreviewStatus = (
  previewStatus?: string | null,
  failureCategory?: PreviewFailureCategory,
  mimeType?: string | null,
  failureReason?: string | null
): string => {
  const normalized = (previewStatus || '').toUpperCase().trim();
  if (!normalized) {
    return 'PENDING';
  }
  if (normalized === 'FAILED') {
    return isUnsupportedPreviewFailure(failureCategory, mimeType, failureReason)
      ? 'UNSUPPORTED'
      : 'FAILED';
  }
  return normalized;
};

export const normalizePreviewFailureReason = (failureReason?: string | null): string => {
  const normalized = (failureReason || '').replace(/\s+/g, ' ').trim();
  return normalized || 'UNSPECIFIED';
};

export const formatPreviewFailureReasonLabel = (failureReason?: string | null): string => {
  const normalized = normalizePreviewFailureReason(failureReason);
  if (normalized === 'UNSPECIFIED') {
    return 'Unspecified reason';
  }
  return normalized;
};

export const summarizeFailedPreviews = (items: PreviewFailureLike[]): PreviewFailureSummary => {
  let totalFailed = 0;
  let retryableFailed = 0;
  let unsupportedFailed = 0;
  let permanentFailed = 0;
  const retryableReasonBuckets = new Map<string, number>();

  items.forEach((item) => {
    const effectiveStatus = getEffectivePreviewStatus(
      item.previewStatus,
      item.previewFailureCategory,
      item.mimeType,
      item.previewFailureReason
    );
    if (effectiveStatus !== 'FAILED' && effectiveStatus !== 'UNSUPPORTED') {
      return;
    }

    totalFailed += 1;
    if (effectiveStatus === 'UNSUPPORTED') {
      unsupportedFailed += 1;
      return;
    }

    if (isRetryablePreviewFailure(item.previewFailureCategory, item.mimeType, item.previewFailureReason)) {
      retryableFailed += 1;
      const reason = normalizePreviewFailureReason(item.previewFailureReason);
      retryableReasonBuckets.set(reason, (retryableReasonBuckets.get(reason) || 0) + 1);
      return;
    }

    permanentFailed += 1;
  });

  const retryableReasons = Array.from(retryableReasonBuckets.entries())
    .map(([reason, count]) => ({ reason, count }))
    .sort((a, b) => b.count - a.count || a.reason.localeCompare(b.reason));

  return {
    totalFailed,
    retryableFailed,
    permanentFailed,
    unsupportedFailed,
    retryableReasons,
  };
};

export const formatPreviewBatchOperationProgress = (progress: PreviewBatchOperationProgress): string => {
  const total = Math.max(0, Math.floor(progress.total));
  const processed = Math.max(0, Math.min(total, Math.floor(progress.processed)));
  const queued = Math.max(0, Math.floor(progress.queued));
  const skipped = Math.max(0, Math.floor(progress.skipped));
  const failed = Math.max(0, Math.floor(progress.failed));
  return `${processed}/${total} processed • queued ${queued} • skipped ${skipped} • failed ${failed}`;
};

export const buildNonRetryablePreviewSummaryMessage = (summary: PreviewFailureSummary): string => {
  if (summary.retryableFailed > 0) {
    return '';
  }
  const unsupported = summary.unsupportedFailed;
  const permanent = summary.permanentFailed;

  if (unsupported > 0 && permanent > 0) {
    return 'All preview issues on this page are permanent or unsupported; retry actions are hidden.';
  }
  if (unsupported > 0) {
    return 'All preview issues on this page are unsupported; retry actions are hidden.';
  }
  if (permanent > 0) {
    return 'All preview issues on this page are permanent; retry actions are hidden.';
  }
  return 'No retryable preview issues on this page.';
};

export const getFailedPreviewMeta = (
  mimeType?: string | null,
  failureCategory?: PreviewFailureCategory,
  failureReason?: string | null
): { label: string; color: PreviewStatusChipColor; unsupported: boolean } => {
  const normalizedCategory = (failureCategory || '').toUpperCase();
  const unsupported = isUnsupportedPreviewFailure(normalizedCategory, mimeType, failureReason);
  if (unsupported) {
    return {
      label: 'Preview unsupported',
      color: 'default',
      unsupported: true,
    };
  }
  if (normalizedCategory === 'TEMPORARY' || isTemporaryPreviewReason(failureReason)) {
    return {
      label: 'Preview failed (temporary)',
      color: 'warning',
      unsupported: false,
    };
  }
  if (normalizedCategory === 'PERMANENT') {
    return {
      label: 'Preview failed (permanent)',
      color: 'error',
      unsupported: false,
    };
  }
  return {
    label: 'Preview failed',
    color: 'error',
    unsupported: false,
  };
};
