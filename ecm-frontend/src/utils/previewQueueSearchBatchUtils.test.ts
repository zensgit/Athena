import { applyPreviewQueueSearchBatchResultToOverrides } from './previewQueueSearchBatchUtils';

describe('previewQueueSearchBatchUtils', () => {
  it('applies queued batch results and clears stale failure detail', () => {
    const next = applyPreviewQueueSearchBatchResultToOverrides(
      {
        'doc-1': {
          previewStatus: 'FAILED',
          previewFailureReason: 'preview service timeout',
          previewFailureCategory: 'TEMPORARY',
          queueState: 'FAILED',
          attempts: 2,
        },
      },
      [
        {
          documentId: 'doc-1',
          outcome: 'QUEUED',
          message: 'queued',
          previewStatus: 'PROCESSING',
          previewFailureReason: null,
          previewFailureCategory: null,
          previewLastUpdated: '2026-03-29T10:30:00',
          queueState: 'QUEUED',
          attempts: 1,
          nextAttemptAt: '2026-03-29T10:31:00Z',
        },
      ]
    );

    expect(next['doc-1']).toEqual({
      previewStatus: 'PROCESSING',
      previewFailureReason: null,
      previewFailureCategory: null,
      previewLastUpdated: '2026-03-29T10:30:00',
      attempts: 1,
      nextAttemptAt: '2026-03-29T10:31:00Z',
      queueState: 'QUEUED',
      message: 'queued',
    });
  });

  it('preserves skipped items with effective failure detail', () => {
    const next = applyPreviewQueueSearchBatchResultToOverrides(
      {},
      [
        {
          documentId: 'doc-2',
          outcome: 'SKIPPED',
          message: 'Preview unsupported',
          previewStatus: 'FAILED',
          previewFailureReason: 'preview service timeout',
          previewFailureCategory: 'TEMPORARY',
          previewLastUpdated: null,
          queueState: 'DECLINED',
          attempts: 0,
          nextAttemptAt: null,
        },
      ]
    );

    expect(next['doc-2']).toEqual({
      previewStatus: 'FAILED',
      previewFailureReason: 'preview service timeout',
      previewFailureCategory: 'TEMPORARY',
      previewLastUpdated: null,
      attempts: 0,
      nextAttemptAt: undefined,
      queueState: 'DECLINED',
      message: 'Preview unsupported',
    });
  });
});
