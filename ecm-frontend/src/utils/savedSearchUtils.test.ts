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
});
