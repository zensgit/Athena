import { buildSearchCriteriaFromSavedSearch } from './savedSearchUtils';

describe('savedSearchUtils', () => {
  it('maps previewStatuses from saved query filters into search criteria', () => {
    const criteria = buildSearchCriteriaFromSavedSearch({
      id: 'saved-1',
      userId: 'admin',
      name: 'Failed previews',
      createdAt: new Date().toISOString(),
      queryParams: {
        query: 'preview',
        filters: {
          previewStatuses: ['FAILED', 'PROCESSING'],
        },
      },
    });

    expect(criteria.name).toBe('preview');
    expect(criteria.previewStatuses).toEqual(['FAILED', 'PROCESSING']);
  });
});
