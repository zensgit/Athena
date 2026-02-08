import {
  getFailedPreviewMeta,
  isUnsupportedPreviewFailure,
  isUnsupportedPreviewReason,
  isUnsupportedPreviewMimeType,
  normalizeMimeType,
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
});
