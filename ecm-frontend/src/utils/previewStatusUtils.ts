export type PreviewStatusChipColor = 'default' | 'success' | 'warning' | 'error' | 'info';
export type PreviewFailureCategory = 'UNSUPPORTED' | 'TEMPORARY' | 'PERMANENT' | string | null | undefined;

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
  const normalized = (failureReason || '').trim().toLowerCase();
  if (!normalized) {
    return false;
  }
  return normalized.includes('not supported')
    || normalized.includes('unsupported mime')
    || normalized.includes('unsupported file type');
};

export const isUnsupportedPreviewFailure = (
  failureCategory?: PreviewFailureCategory,
  mimeType?: string | null,
  failureReason?: string | null
): boolean => {
  if ((failureCategory || '').toUpperCase() === 'UNSUPPORTED') {
    return true;
  }
  if (isUnsupportedPreviewReason(failureReason)) {
    return true;
  }
  return isUnsupportedPreviewMimeType(mimeType);
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
