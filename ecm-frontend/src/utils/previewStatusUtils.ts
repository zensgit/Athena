export type PreviewStatusChipColor = 'default' | 'success' | 'warning' | 'error' | 'info';

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

export const getFailedPreviewMeta = (
  mimeType?: string | null
): { label: string; color: PreviewStatusChipColor; unsupported: boolean } => {
  const unsupported = isUnsupportedPreviewMimeType(mimeType);
  if (unsupported) {
    return {
      label: 'Preview unsupported',
      color: 'default',
      unsupported: true,
    };
  }
  return {
    label: 'Preview failed',
    color: 'error',
    unsupported: false,
  };
};
