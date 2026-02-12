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

  it('supports legacy aliases for pathPrefix, createdFrom/to, and previewStatus', () => {
    const criteria = buildSearchCriteriaFromSavedSearch({
      id: 'saved-legacy-alias',
      userId: 'admin',
      name: 'Legacy aliases',
      createdAt: new Date().toISOString(),
      queryParams: {
        q: 'legacy-alias',
        filters: {
          createdFrom: '2026-02-05T00:00:00.000Z',
          createdTo: '2026-02-10T23:59:59.000Z',
          pathPrefix: '/Root/Documents/Legacy',
          previewStatus: 'failed, processing',
          creators: ['legacy-auditor'],
        },
      },
    });

    expect(criteria.name).toBe('legacy-alias');
    expect(criteria.createdFrom).toBe('2026-02-05T00:00:00.000Z');
    expect(criteria.createdTo).toBe('2026-02-10T23:59:59.000Z');
    expect(criteria.path).toBe('/Root/Documents/Legacy');
    expect(criteria.previewStatuses).toEqual(['FAILED', 'PROCESSING']);
    expect(criteria.createdBy).toBe('legacy-auditor');
  });

  it('normalizes string-based legacy list and boolean fields', () => {
    const criteria = buildSearchCriteriaFromSavedSearch({
      id: 'saved-legacy-string-shape',
      userId: 'admin',
      name: 'Legacy string fields',
      createdAt: new Date().toISOString(),
      queryParams: {
        queryString: 'legacy-string-query',
        filters: {
          mimeTypes: 'application/pdf, image/png',
          tags: 'alpha,beta',
          categories: 'cat-a, cat-b',
          correspondents: 'ops,qa',
          creators: 'legacy-user',
          includeChildren: 'false',
        },
      },
    });

    expect(criteria.name).toBe('legacy-string-query');
    expect(criteria.contentType).toBe('application/pdf');
    expect(criteria.mimeTypes).toEqual(['application/pdf', 'image/png']);
    expect(criteria.tags).toEqual(['alpha', 'beta']);
    expect(criteria.categories).toEqual(['cat-a', 'cat-b']);
    expect(criteria.correspondents).toEqual(['ops', 'qa']);
    expect(criteria.createdBy).toBe('legacy-user');
    expect(criteria.createdByList).toEqual(['legacy-user']);
    expect(criteria.includeChildren).toBe(false);
  });

  it('supports JSON-string queryParams and filter aliases with status normalization', () => {
    const criteria = buildSearchCriteriaFromSavedSearch({
      id: 'saved-json-payload',
      userId: 'admin',
      name: 'JSON payload',
      createdAt: new Date().toISOString(),
      queryParams: JSON.stringify({
        queryString: 'json-query',
        filters: JSON.stringify({
          mimeType: 'application/pdf',
          creator: 'json-user',
          includeChildren: '1',
          pathStartsWith: '/Root/Documents/Compat',
          previewStatus: 'unsupported_media_type, in_progress, error, unsupported',
          createdRange: {
            from: '2026-02-01T00:00:00.000Z',
            to: '2026-02-07T23:59:59.000Z',
          },
          modifiedRange: {
            from: '2026-02-08T00:00:00.000Z',
            to: '2026-02-11T23:59:59.000Z',
          },
        }),
      }),
    } as any);

    expect(criteria.name).toBe('json-query');
    expect(criteria.contentType).toBe('application/pdf');
    expect(criteria.mimeTypes).toEqual(['application/pdf']);
    expect(criteria.createdBy).toBe('json-user');
    expect(criteria.createdByList).toEqual(['json-user']);
    expect(criteria.includeChildren).toBe(true);
    expect(criteria.path).toBe('/Root/Documents/Compat');
    expect(criteria.createdFrom).toBe('2026-02-01T00:00:00.000Z');
    expect(criteria.createdTo).toBe('2026-02-07T23:59:59.000Z');
    expect(criteria.modifiedFrom).toBe('2026-02-08T00:00:00.000Z');
    expect(criteria.modifiedTo).toBe('2026-02-11T23:59:59.000Z');
    expect(criteria.previewStatuses).toEqual(['UNSUPPORTED', 'PROCESSING', 'FAILED']);
  });

  it('ignores malformed fields while preserving valid criteria values', () => {
    const criteria = buildSearchCriteriaFromSavedSearch({
      id: 'saved-malformed-fields',
      userId: 'admin',
      name: 'Malformed payload',
      createdAt: new Date().toISOString(),
      queryParams: {
        query: 'resilient-query',
        filters: {
          previewStatus: 'FAILED, UNKNOWN_STATUS, unsupported_media_type',
          createdFrom: 'not-a-date',
          createdTo: '2026-02-12T00:00:00.000Z',
          modifiedRange: {
            from: 'bad-date',
            to: '2026-02-13T10:00:00.000Z',
          },
          minSize: '-5',
          maxSize: 'abc',
          tags: ['stable-tag', 42, true, { nested: 'ignored' }, ''],
          folderId: '   ',
          includeChildren: 'yes',
          unknownField: 'ignored',
        },
      },
    } as any);

    expect(criteria.name).toBe('resilient-query');
    expect(criteria.previewStatuses).toEqual(['FAILED', 'UNSUPPORTED']);
    expect(criteria.createdFrom).toBeUndefined();
    expect(criteria.createdTo).toBe('2026-02-12T00:00:00.000Z');
    expect(criteria.modifiedFrom).toBeUndefined();
    expect(criteria.modifiedTo).toBe('2026-02-13T10:00:00.000Z');
    expect(criteria.minSize).toBeUndefined();
    expect(criteria.maxSize).toBeUndefined();
    expect(criteria.tags).toEqual(['stable-tag', '42', 'true']);
    expect(criteria.folderId).toBeUndefined();
    expect(criteria.includeChildren).toBe(true);
  });

  it('degrades gracefully when queryParams JSON is malformed', () => {
    const criteria = buildSearchCriteriaFromSavedSearch({
      id: 'saved-bad-json',
      userId: 'admin',
      name: 'Bad JSON',
      createdAt: new Date().toISOString(),
      queryParams: '{"query":"x",',
    } as any);

    expect(criteria.name).toBe('');
    expect(criteria.previewStatuses).toEqual([]);
    expect(criteria.tags).toEqual([]);
    expect(criteria.properties).toBeUndefined();
  });
});
