import { buildSearchCriteriaFromSavedSearch } from './savedSearchUtils';

describe('savedSearchUtils', () => {
  it('maps previewStatuses, aspects, and properties from saved query filters into search criteria', () => {
    const criteria = buildSearchCriteriaFromSavedSearch({
      id: 'saved-1',
      userId: 'admin',
      name: 'Failed previews',
      createdAt: new Date().toISOString(),
      queryParams: {
        query: 'preview',
        filters: {
          previewStatuses: ['FAILED', 'PROCESSING'],
          aspects: ['cm:versionable', 'cm:taggable'],
          properties: {
            'mail:subject': 'hello',
            'mail:uid': 123,
          },
        },
      },
    });

    expect(criteria.name).toBe('preview');
    expect(criteria.previewStatuses).toEqual(['FAILED', 'PROCESSING']);
    expect(criteria.aspects).toEqual(['cm:versionable', 'cm:taggable']);
    expect(criteria.properties).toEqual({
      'mail:subject': 'hello',
      'mail:uid': 123,
    });
  });

  it('supports legacy saved-search shape with top-level queryParams fields', () => {
    const criteria = buildSearchCriteriaFromSavedSearch({
      id: 'saved-legacy',
      userId: 'admin',
      name: 'Legacy saved search',
      createdAt: new Date().toISOString(),
      queryParams: {
        q: 'legacy',
        mimeTypes: ['application/pdf'],
        createdByList: ['legacy-user'],
        aspects: ['cm:auditable'],
        properties: {
          'mail:subject': 'legacy-subject',
        },
        dateFrom: '2026-02-01T00:00:00.000Z',
        dateTo: '2026-02-11T23:59:59.000Z',
        tags: ['legacy-tag'],
        minSize: '12',
        maxSize: 34,
        folderId: '00000000-0000-4000-8000-000000000999',
        includeChildren: false,
      },
    });

    expect(criteria.name).toBe('legacy');
    expect(criteria.contentType).toBe('application/pdf');
    expect(criteria.createdBy).toBe('legacy-user');
    expect(criteria.aspects).toEqual(['cm:auditable']);
    expect(criteria.properties).toEqual({ 'mail:subject': 'legacy-subject' });
    expect(criteria.tags).toEqual(['legacy-tag']);
    expect(criteria.minSize).toBe(12);
    expect(criteria.maxSize).toBe(34);
    expect(criteria.folderId).toBe('00000000-0000-4000-8000-000000000999');
    expect(criteria.includeChildren).toBe(false);
  });
});
