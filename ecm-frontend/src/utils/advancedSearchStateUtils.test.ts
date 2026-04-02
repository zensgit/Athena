import {
  buildAdvancedSearchCriteriaKey,
  buildAdvancedSearchUrlSearch,
  buildSearchCriteriaFromAdvancedState,
  hasActiveAdvancedSearchCriteria,
  hasRestorableAdvancedSearchState,
  parseAdvancedSearchUrlState,
  resolveTemplateQueryState,
} from './advancedSearchStateUtils';

describe('advancedSearchStateUtils', () => {
  it('parses advanced search URL state with normalized preview status aliases', () => {
    const state = parseAdvancedSearchUrlState(
      '?q=alpha&page=3&previewStatus=waiting,error&checkoutState=checkedOut&checkoutUser=alice&dateRange=week&mimeTypes=application%2Fpdf,text%2Fplain&creators=admin&tags=t1,t2&categories=c1&minSize=1&maxSize=7'
    );

    expect(state).toEqual({
      query: 'alpha',
      page: 3,
      previewStatuses: ['QUEUED', 'FAILED'],
      lockState: 'all',
      lockOwner: '',
      checkoutState: 'checkedOut',
      checkoutUser: 'alice',
      dateRange: 'week',
      mimeTypes: ['application/pdf', 'text/plain'],
      creators: ['admin'],
      tags: ['t1', 't2'],
      categories: ['c1'],
      minSize: 1,
      maxSize: 7,
    });
  });

  it('serializes advanced search URL state with stable keys', () => {
    expect(
      buildAdvancedSearchUrlSearch({
        query: 'alpha',
        page: 2,
        previewStatuses: ['FAILED'],
        lockState: 'locked',
        lockOwner: 'carol',
        checkoutState: 'available',
        checkoutUser: 'bob',
        dateRange: 'month',
        mimeTypes: ['application/pdf'],
        creators: ['admin'],
        tags: ['tag-a'],
        categories: ['cat-a'],
        minSize: 5,
        maxSize: 8,
      })
    ).toBe(
      'q=alpha&page=2&previewStatus=FAILED&lockState=locked&lockOwner=carol&checkoutState=available&checkoutUser=bob&dateRange=month&mimeTypes=application%2Fpdf&creators=admin&tags=tag-a&categories=cat-a&minSize=5&maxSize=8'
    );
  });

  it('builds unified search criteria from advanced search state', () => {
    expect(
      buildSearchCriteriaFromAdvancedState(
        {
          query: 'alpha',
          previewStatuses: ['FAILED'],
          lockState: 'locked',
          lockOwner: 'carol',
          checkoutState: 'checkedOut',
          checkoutUser: 'alice',
          dateRange: 'today',
          mimeTypes: ['application/pdf'],
          creators: ['admin'],
          tags: ['tag-a'],
          categories: ['cat-a'],
          minSize: 2,
          maxSize: 9,
        },
        4,
        10
      )
    ).toMatchObject({
      name: 'alpha',
      previewStatuses: ['FAILED'],
      locked: true,
      lockedBy: 'carol',
      checkedOut: true,
      checkoutUser: 'alice',
      mimeTypes: ['application/pdf'],
      createdByList: ['admin'],
      tags: ['tag-a'],
      categories: ['cat-a'],
      minSize: 2,
      maxSize: 9,
      page: 3,
      size: 10,
    });
  });

  it('normalizes template query params into advanced search state', () => {
    expect(
      resolveTemplateQueryState({
        query: 'alpha',
        filters: {
          previewStatus: 'error,unsupported_mime',
          locked: 'true',
          lockedBy: 'carol',
          checkedOut: 'true',
          checkoutUser: 'alice',
          dateRange: 'week',
          mimeTypes: 'application/pdf,text/plain',
          createdByList: ['admin', 'editor'],
          tags: ['tag-a'],
          categories: 'cat-a,cat-b',
          minSize: '3',
          maxSize: 9,
        },
      })
    ).toEqual({
      query: 'alpha',
      previewStatuses: ['FAILED', 'UNSUPPORTED'],
      lockState: 'locked',
      lockOwner: 'carol',
      checkoutState: 'checkedOut',
      checkoutUser: 'alice',
      dateRange: 'week',
      mimeTypes: ['application/pdf', 'text/plain'],
      creators: ['admin', 'editor'],
      tags: ['tag-a'],
      categories: ['cat-a', 'cat-b'],
      minSize: 3,
      maxSize: 9,
    });
  });

  it('builds stable fallback keys and detects active/restorable state', () => {
    const criteria = {
      query: 'alpha',
      previewStatuses: ['FAILED', 'QUEUED'],
      lockState: 'all' as const,
      lockOwner: '',
      checkoutState: 'all' as const,
      checkoutUser: '',
      dateRange: 'all' as const,
      mimeTypes: ['text/plain', 'application/pdf'],
      creators: ['editor', 'admin'],
      tags: [],
      categories: [],
      minSize: undefined,
      maxSize: 10,
    };

    expect(buildAdvancedSearchCriteriaKey(criteria)).toBe(
      JSON.stringify({
        previewStatuses: ['FAILED', 'QUEUED'],
        lockState: 'all',
        lockOwner: '',
        checkoutState: 'all',
        checkoutUser: '',
        dateRange: 'all',
        mimeTypes: ['application/pdf', 'text/plain'],
        creators: ['admin', 'editor'],
        tags: [],
        categories: [],
        minSize: null,
        maxSize: 10,
      })
    );
    expect(hasActiveAdvancedSearchCriteria(criteria)).toBe(true);
    expect(
      hasRestorableAdvancedSearchState({
        ...criteria,
        page: 1,
      })
    ).toBe(true);
  });
});
