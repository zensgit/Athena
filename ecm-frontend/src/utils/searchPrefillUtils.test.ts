import {
  buildSearchPrefillFromAdvancedSearchUrl,
  normalizePreviewStatusTokens,
} from './searchPrefillUtils';

describe('searchPrefillUtils', () => {
  it('maps /search URL params into search prefill fields', () => {
    const prefill = buildSearchPrefillFromAdvancedSearchUrl(
      '/search',
      '?q=alpha&previewStatus=failed,unsupported_media_type&mimeTypes=application%2Fpdf&creators=admin&tags=t1,t2&categories=c1&minSize=1&maxSize=7&dateRange=week'
    );

    expect(prefill.name).toBe('alpha');
    expect(prefill.previewStatuses).toEqual(['FAILED', 'UNSUPPORTED']);
    expect(prefill.contentType).toBe('application/pdf');
    expect(prefill.createdBy).toBe('admin');
    expect(prefill.tags).toEqual(['t1', 't2']);
    expect(prefill.categories).toEqual(['c1']);
    expect(prefill.minSize).toBe(1);
    expect(prefill.maxSize).toBe(7);
    expect(prefill.modifiedFrom).toBeTruthy();
  });

  it('ignores non-search routes', () => {
    const prefill = buildSearchPrefillFromAdvancedSearchUrl(
      '/browse/root',
      '?q=alpha&previewStatus=FAILED'
    );
    expect(prefill).toEqual({});
  });

  it('only maps singular mimeTypes and creators to basic dialog fields', () => {
    const prefill = buildSearchPrefillFromAdvancedSearchUrl(
      '/search',
      '?mimeTypes=application%2Fpdf,text%2Fplain&creators=admin,editor'
    );
    expect(prefill.contentType).toBeUndefined();
    expect(prefill.createdBy).toBeUndefined();
  });

  it('normalizes preview status aliases from csv tokens', () => {
    expect(
      normalizePreviewStatusTokens('waiting,in_progress,error,unsupported_mime,unknown')
    ).toEqual(['QUEUED', 'PROCESSING', 'FAILED', 'UNSUPPORTED']);
  });
});
