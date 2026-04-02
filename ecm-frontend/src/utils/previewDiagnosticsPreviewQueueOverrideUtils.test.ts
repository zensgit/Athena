import { applyPreviewQueueLocalOverrides } from './previewDiagnosticsPreviewQueueOverrideUtils';

describe('previewDiagnosticsPreviewQueueOverrideUtils', () => {
  it('preserves the full queue mutation contract when applying local preview overrides', () => {
    expect(
      applyPreviewQueueLocalOverrides(
        [
          {
            id: 'doc-1',
            name: 'Doc 1',
            path: '/Root/Doc 1',
            mimeType: 'application/pdf',
            previewStatus: 'FAILED',
            previewFailureReason: 'unsupported source',
            previewFailureCategory: 'UNSUPPORTED',
            previewLastUpdated: '2026-04-01T01:00:00Z',
          },
        ],
        {
          'doc-1': {
            previewStatus: 'PROCESSING',
            previewFailureReason: 'queued for retry',
            previewFailureCategory: 'RETRYABLE',
            previewLastUpdated: '2026-04-01T01:01:00Z',
            queueState: 'QUEUED',
            attempts: 2,
            nextAttemptAt: '2026-04-01T01:05:00Z',
            message: 'queued',
          },
        }
      )
    ).toEqual([
      {
        id: 'doc-1',
        name: 'Doc 1',
        path: '/Root/Doc 1',
        mimeType: 'application/pdf',
        previewStatus: 'PROCESSING',
        previewFailureReason: 'queued for retry',
        previewFailureCategory: 'RETRYABLE',
        previewLastUpdated: '2026-04-01T01:01:00Z',
        queueState: 'QUEUED',
        attempts: 2,
        nextAttemptAt: '2026-04-01T01:05:00Z',
        message: 'queued',
      },
    ]);
  });
});
