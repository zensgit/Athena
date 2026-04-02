import { buildPreviewQueueOverride } from './previewQueueOverrideUtils';

describe('previewQueueOverrideUtils', () => {
  it('maps queued preview status into a richer override', () => {
    expect(buildPreviewQueueOverride({
      documentId: 'doc-1',
      previewStatus: 'PROCESSING',
      previewFailureReason: null,
      previewFailureCategory: null,
      previewLastUpdated: '2026-03-29T11:30:00',
      queued: true,
      attempts: 1,
      nextAttemptAt: '2026-03-29T11:35:00Z',
      message: 'queued',
    })).toEqual({
      previewStatus: 'PROCESSING',
      previewFailureReason: null,
      previewFailureCategory: null,
      previewLastUpdated: '2026-03-29T11:30:00',
      attempts: 1,
      nextAttemptAt: '2026-03-29T11:35:00Z',
      queueState: 'QUEUED',
      message: 'queued',
    });
  });

  it('maps declined preview status into a declined override', () => {
    expect(buildPreviewQueueOverride({
      documentId: 'doc-2',
      previewStatus: 'UNSUPPORTED',
      previewFailureReason: 'unsupported source',
      previewFailureCategory: 'UNSUPPORTED',
      previewLastUpdated: null,
      queued: false,
      attempts: 0,
      nextAttemptAt: undefined,
      message: 'Preview unsupported',
    })).toEqual({
      previewStatus: 'UNSUPPORTED',
      previewFailureReason: 'unsupported source',
      previewFailureCategory: 'UNSUPPORTED',
      previewLastUpdated: null,
      attempts: 0,
      nextAttemptAt: undefined,
      queueState: 'DECLINED',
      message: 'Preview unsupported',
    });
  });
});
