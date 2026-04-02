import { resolveDocumentPreviewQueueState } from './documentPreviewQueueState';

describe('documentPreviewQueueState', () => {
  it('prefers explicit queue status and preserves queue metadata', () => {
    const resolved = resolveDocumentPreviewQueueState(
      {
        documentId: 'doc-1',
        previewStatus: 'PROCESSING',
        previewFailureReason: 'Preview queued',
        previewFailureCategory: 'TEMPORARY',
        previewLastUpdated: '2026-04-01T10:00:00Z',
        queued: true,
        attempts: 2,
        nextAttemptAt: '2026-04-01T10:05:00Z',
        message: 'Preview queued',
      },
      {
        previewStatus: 'FAILED',
        previewFailureReason: 'old reason',
        previewFailureCategory: 'PERMANENT',
        previewLastUpdated: '2026-04-01T09:00:00Z',
      }
    );

    expect(resolved).toEqual({
      previewStatus: 'PROCESSING',
      previewFailureReason: 'Preview queued',
      previewFailureCategory: 'TEMPORARY',
      previewLastUpdated: '2026-04-01T10:00:00Z',
      queued: true,
      attempts: 2,
      nextAttemptAt: '2026-04-01T10:05:00Z',
      message: 'Preview queued',
    });
  });

  it('backfills sparse queue status from fallback preview state', () => {
    const resolved = resolveDocumentPreviewQueueState(
      {
        documentId: 'doc-2',
        previewStatus: null,
        queued: false,
        attempts: 0,
        nextAttemptAt: null,
        message: 'Preview already up to date',
      },
      {
        previewStatus: 'UNSUPPORTED',
        previewFailureReason: 'Preview definition is not registered for generic binary sources',
        previewFailureCategory: 'UNSUPPORTED',
        previewLastUpdated: '2026-04-01T09:30:00Z',
      }
    );

    expect(resolved).toEqual({
      previewStatus: 'UNSUPPORTED',
      previewFailureReason: 'Preview definition is not registered for generic binary sources',
      previewFailureCategory: 'UNSUPPORTED',
      previewLastUpdated: '2026-04-01T09:30:00Z',
      queued: false,
      attempts: 0,
      nextAttemptAt: null,
      message: 'Preview already up to date',
    });
  });
});
