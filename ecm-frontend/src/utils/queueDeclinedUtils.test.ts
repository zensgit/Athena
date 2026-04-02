import {
  applyQueueDeclinedLocalOverrides,
  buildQueueDeclinedOverridesFromClear,
  buildQueueDeclinedOverridesFromRequeue,
} from './queueDeclinedUtils';

describe('queueDeclinedUtils', () => {
  it('applies preview-status overrides and recalculates summary counts', () => {
    const summary = applyQueueDeclinedLocalOverrides(
      {
        queueEnabled: true,
        totalDeclined: 2,
        sampleLimit: 20,
        sampleTruncated: false,
        categoryFilter: 'ANY',
        forceRequiredFilter: 'ANY',
        windowHoursFilter: 24,
        queryFilter: null,
        forceRequiredCount: 1,
        totalSampledItems: 2,
        filteredSampledItems: 2,
        categoryCounts: [
          { category: 'QUIET_PERIOD', count: 1, forceRequiredCount: 0 },
          { category: 'PERMANENT_FAILURE', count: 1, forceRequiredCount: 1 },
        ],
        items: [
          {
            documentId: 'doc-1',
            name: 'one.pdf',
            path: '/Root/one.pdf',
            mimeType: 'application/pdf',
            previewStatus: 'FAILED',
            previewFailureReason: 'timed out',
            previewFailureCategory: 'TEMPORARY',
            previewLastUpdated: '2026-03-28T12:00:00Z',
            reason: 'quiet',
            category: 'QUIET_PERIOD',
            governanceKey: 'gov-1',
            declinedAt: '2026-03-28T12:00:00Z',
            nextEligibleAt: '2026-03-28T12:05:00Z',
            forceRequired: false,
          },
          {
            documentId: 'doc-2',
            name: 'two.pdf',
            path: '/Root/two.pdf',
            mimeType: 'application/pdf',
            previewStatus: 'FAILED',
            previewFailureReason: 'unsupported source',
            previewFailureCategory: 'UNSUPPORTED',
            previewLastUpdated: '2026-03-28T12:01:00Z',
            reason: 'permanent',
            category: 'PERMANENT_FAILURE',
            governanceKey: 'gov-2',
            declinedAt: '2026-03-28T12:01:00Z',
            nextEligibleAt: null,
            forceRequired: true,
          },
        ],
      },
      {
        'doc-1': {
          previewStatus: 'UNSUPPORTED',
          previewFailureReason: 'definition missing',
          previewFailureCategory: 'UNSUPPORTED',
          previewLastUpdated: '2026-03-28T12:10:00Z',
        },
        'doc-2': { hidden: true },
      }
    );

    expect(summary?.filteredSampledItems).toBe(1);
    expect(summary?.forceRequiredCount).toBe(0);
    expect(summary?.items).toHaveLength(1);
    expect(summary?.items[0].previewStatus).toBe('UNSUPPORTED');
    expect(summary?.items[0].previewFailureReason).toBe('definition missing');
    expect(summary?.items[0].previewFailureCategory).toBe('UNSUPPORTED');
    expect(summary?.items[0].previewLastUpdated).toBe('2026-03-28T12:10:00Z');
    expect(summary?.categoryCounts).toEqual([
      { category: 'QUIET_PERIOD', count: 1, forceRequiredCount: 0 },
    ]);
  });

  it('builds requeue overrides from preview statuses', () => {
    expect(buildQueueDeclinedOverridesFromRequeue({
      categoryFilter: 'ANY',
      forceRequiredFilter: 'ANY',
      windowHoursFilter: 24,
      queryFilter: null,
      limit: 200,
      force: true,
      requested: 2,
      queued: 1,
      skipped: 1,
      failed: 0,
      results: [
        {
          documentId: 'doc-1',
          category: 'QUIET_PERIOD',
          outcome: 'QUEUED',
          message: 'queued',
          previewStatus: 'UNSUPPORTED',
          previewFailureReason: 'definition missing',
          previewFailureCategory: 'UNSUPPORTED',
          previewLastUpdated: '2026-03-28T12:10:00Z',
        },
        {
          documentId: 'doc-2',
          category: 'QUIET_PERIOD',
          outcome: 'SKIPPED',
          message: 'skipped',
          previewStatus: null,
        },
      ],
    })).toEqual({
      'doc-1': {
        previewStatus: 'UNSUPPORTED',
        previewFailureReason: 'definition missing',
        previewFailureCategory: 'UNSUPPORTED',
        previewLastUpdated: '2026-03-28T12:10:00Z',
      },
    });
  });

  it('builds clear overrides only for cleared items', () => {
    expect(buildQueueDeclinedOverridesFromClear({
      categoryFilter: 'ANY',
      forceRequiredFilter: 'ANY',
      windowHoursFilter: 24,
      queryFilter: null,
      limit: 200,
      requested: 2,
      cleared: 1,
      skipped: 0,
      failed: 1,
      results: [
        {
          documentId: 'doc-1',
          category: 'QUIET_PERIOD',
          outcome: 'CLEARED',
          message: 'cleared',
        },
        {
          documentId: 'doc-2',
          category: 'QUIET_PERIOD',
          outcome: 'FAILED',
          message: 'failed',
        },
      ],
    })).toEqual({
      'doc-1': { hidden: true },
    });
  });
});
