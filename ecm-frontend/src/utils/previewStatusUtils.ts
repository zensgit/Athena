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
  unsupportedFailed: number;
  retryableReasons: Array<{ reason: string; count: number }>;
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
  const retryableReasonBuckets = new Map<string, number>();

  items.forEach((item) => {
    const previewStatus = (item.previewStatus || '').toUpperCase();
    if (previewStatus !== 'FAILED') {
      return;
    }

    totalFailed += 1;
    const unsupported = isUnsupportedPreviewFailure(
      item.previewFailureCategory,
      item.mimeType,
      item.previewFailureReason
    );
    if (unsupported) {
      unsupportedFailed += 1;
      return;
    }

    retryableFailed += 1;
    const reason = normalizePreviewFailureReason(item.previewFailureReason);
    retryableReasonBuckets.set(reason, (retryableReasonBuckets.get(reason) || 0) + 1);
  });

  const retryableReasons = Array.from(retryableReasonBuckets.entries())
    .map(([reason, count]) => ({ reason, count }))
    .sort((a, b) => b.count - a.count || a.reason.localeCompare(b.reason));

  return {
    totalFailed,
    retryableFailed,
    unsupportedFailed,
    retryableReasons,
  };
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
  if (normalizedCategory === 'TEMPORARY') {
    return {
      label: 'Preview failed (temporary)',
      color: 'warning',
      unsupported: false,
    };
  }
  return {
    label: 'Preview failed',
    color: 'error',
    unsupported: false,
  };
};
