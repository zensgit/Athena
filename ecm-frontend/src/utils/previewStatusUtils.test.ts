import { getFailedPreviewMeta, isUnsupportedPreviewMimeType, normalizeMimeType } from './previewStatusUtils';

describe('previewStatusUtils', () => {
  it('normalizes mime types with charset suffix', () => {
    expect(normalizeMimeType('application/octet-stream; charset=utf-8')).toBe('application/octet-stream');
  });

  it('detects unsupported preview mime type', () => {
    expect(isUnsupportedPreviewMimeType('application/octet-stream')).toBe(true);
    expect(isUnsupportedPreviewMimeType('text/plain')).toBe(false);
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
});
