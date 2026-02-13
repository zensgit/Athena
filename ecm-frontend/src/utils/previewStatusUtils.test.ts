import {
  formatPreviewFailureReasonLabel,
  getFailedPreviewMeta,
  getEffectivePreviewStatus,
  isUnsupportedPreviewFailure,
  isUnsupportedPreviewReason,
  isUnsupportedPreviewMimeType,
  normalizePreviewFailureReason,
  normalizeMimeType,
  summarizeFailedPreviews,
} from './previewStatusUtils';

describe('previewStatusUtils', () => {
  it('normalizes mime types with charset suffix', () => {
    expect(normalizeMimeType('application/octet-stream; charset=utf-8')).toBe('application/octet-stream');
  });

  it('detects unsupported preview mime type', () => {
    expect(isUnsupportedPreviewMimeType('application/octet-stream')).toBe(true);
    expect(isUnsupportedPreviewMimeType('text/plain')).toBe(false);
  });

  it('prefers backend unsupported category when provided', () => {
    expect(isUnsupportedPreviewFailure('UNSUPPORTED', 'application/pdf')).toBe(true);
    expect(getFailedPreviewMeta('application/pdf', 'UNSUPPORTED')).toEqual({
      label: 'Preview unsupported',
      color: 'default',
      unsupported: true,
    });
  });

  it('returns unsupported meta for generic binary type', () => {
    expect(getFailedPreviewMeta('application/octet-stream')).toEqual({
      label: 'Preview unsupported',
      color: 'default',
      unsupported: true,
    });
  });

  it('returns failed meta for supported preview type', () => {
    expect(getFailedPreviewMeta('application/pdf')).toEqual({
      label: 'Preview failed',
      color: 'error',
      unsupported: false,
    });
  });

  it('returns temporary failed meta when backend category is temporary', () => {
    expect(getFailedPreviewMeta('application/pdf', 'TEMPORARY')).toEqual({
      label: 'Preview failed (temporary)',
      color: 'warning',
      unsupported: false,
    });
  });

  it('detects unsupported by failure reason when category is absent', () => {
    expect(isUnsupportedPreviewReason('Preview not supported for mime type: application/octet-stream')).toBe(true);
    expect(isUnsupportedPreviewFailure(undefined, undefined, 'Preview not supported for mime type: application/octet-stream')).toBe(true);
    expect(getFailedPreviewMeta(undefined, undefined, 'Preview not supported for mime type: application/octet-stream')).toEqual({
      label: 'Preview unsupported',
      color: 'default',
      unsupported: true,
    });
  });

  it('detects unsupported reason with irregular separators', () => {
    expect(isUnsupportedPreviewReason('Preview not-supported for mime type: application/octet-stream')).toBe(true);
    expect(isUnsupportedPreviewReason('Preview not   supported for mime type: application/octet-stream')).toBe(true);
    expect(isUnsupportedPreviewReason('Preview unsupported_media_type: application/octet-stream')).toBe(true);
  });

  it('treats unsupported category variants as unsupported', () => {
    expect(isUnsupportedPreviewFailure('UNSUPPORTED_MEDIA_TYPE', 'application/pdf')).toBe(true);
    expect(isUnsupportedPreviewFailure('unsupported_media_type', 'application/pdf')).toBe(true);
  });

  it('normalizes and formats preview failure reason labels', () => {
    expect(normalizePreviewFailureReason('  temporary   gateway timeout  ')).toBe('temporary gateway timeout');
    expect(normalizePreviewFailureReason('')).toBe('UNSPECIFIED');
    expect(formatPreviewFailureReasonLabel('')).toBe('Unspecified reason');
    expect(formatPreviewFailureReasonLabel('mime converter timeout')).toBe('mime converter timeout');
  });

  it('summarizes failed previews into retryable and unsupported buckets', () => {
    const summary = summarizeFailedPreviews([
      {
        previewStatus: 'FAILED',
        previewFailureCategory: 'UNSUPPORTED',
        previewFailureReason: 'unsupported media type',
        mimeType: 'application/octet-stream',
      },
      {
        previewStatus: 'FAILED',
        previewFailureCategory: 'TEMPORARY',
        previewFailureReason: 'timeout',
        mimeType: 'application/pdf',
      },
      {
        previewStatus: 'FAILED',
        previewFailureCategory: 'TEMPORARY',
        previewFailureReason: 'timeout',
        mimeType: 'application/pdf',
      },
      {
        previewStatus: 'READY',
        previewFailureCategory: null,
        previewFailureReason: null,
        mimeType: 'application/pdf',
      },
    ]);

    expect(summary.totalFailed).toBe(3);
    expect(summary.unsupportedFailed).toBe(1);
    expect(summary.retryableFailed).toBe(2);
    expect(summary.retryableReasons).toEqual([{ reason: 'timeout', count: 2 }]);
  });

  it('maps failed unsupported to UNSUPPORTED effective status', () => {
    expect(getEffectivePreviewStatus('FAILED', 'UNSUPPORTED', 'application/pdf', 'unsupported media type')).toBe('UNSUPPORTED');
    expect(getEffectivePreviewStatus('FAILED', undefined, 'application/octet-stream', 'Preview not supported')).toBe('UNSUPPORTED');
    expect(getEffectivePreviewStatus('UNSUPPORTED', undefined, 'application/pdf', 'Preview not supported')).toBe('UNSUPPORTED');
    expect(getEffectivePreviewStatus('FAILED', undefined, 'application/pdf', 'timeout')).toBe('FAILED');
  });
});
