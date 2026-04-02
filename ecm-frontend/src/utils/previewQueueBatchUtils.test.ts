import {
  applyQueueBatchResultToRenditionResources,
} from './previewQueueBatchUtils';

describe('applyQueueBatchResultToRenditionResources', () => {
  it('applies effective preview summary fields to matching rendition resources', () => {
    const resources = [
      {
        documentId: 'doc-1',
        name: 'doc-1.pdf',
        path: '/Root/Documents/doc-1.pdf',
        status: 'FAILED',
        mimeType: 'application/pdf',
        reason: 'Timeout contacting preview service',
        category: 'TEMPORARY',
        previewStatus: 'FAILED',
        updatedAt: '2026-03-29T09:00:00',
      },
      {
        documentId: 'doc-2',
        name: 'doc-2.bin',
        path: '/Root/Documents/doc-2.bin',
        status: 'UNSUPPORTED',
        mimeType: 'application/octet-stream',
        reason: 'Preview not supported',
        category: 'UNSUPPORTED',
        previewStatus: 'UNSUPPORTED',
        updatedAt: '2026-03-29T09:05:00',
      },
    ];

    const result = {
      requested: 1,
      deduplicated: 1,
      queued: 1,
      skipped: 0,
      failed: 0,
      results: [
        {
          documentId: 'doc-1',
          outcome: 'QUEUED',
          message: 'Preview queued',
          previewStatus: 'PROCESSING',
          previewFailureReason: null,
          previewFailureCategory: null,
          previewLastUpdated: '2026-03-29T10:15:00',
          attempts: 0,
          nextAttemptAt: null,
        },
      ],
    };

    const updated = applyQueueBatchResultToRenditionResources(resources, result);

    expect(updated[0]).toMatchObject({
      status: 'PROCESSING',
      reason: 'Timeout contacting preview service',
      category: 'TEMPORARY',
      previewStatus: 'PROCESSING',
      updatedAt: '2026-03-29T10:15:00',
    });
    expect(updated[1]).toEqual(resources[1]);
  });

  it('preserves existing reason/category when queue result only returns preview status', () => {
    const resources = [
      {
        documentId: 'doc-1',
        name: 'doc-1.pdf',
        path: '/Root/Documents/doc-1.pdf',
        status: 'FAILED',
        mimeType: 'application/pdf',
        reason: 'Timeout contacting preview service',
        category: 'TEMPORARY',
        previewStatus: 'FAILED',
        updatedAt: '2026-03-29T09:00:00',
      },
    ];

    const result = {
      requested: 1,
      deduplicated: 1,
      queued: 0,
      skipped: 1,
      failed: 0,
      results: [
        {
          documentId: 'doc-1',
          outcome: 'SKIPPED',
          message: 'Preview already queued',
          previewStatus: 'PROCESSING',
          previewFailureReason: null,
          previewFailureCategory: null,
          previewLastUpdated: null,
          attempts: 1,
          nextAttemptAt: null,
        },
      ],
    };

    const updated = applyQueueBatchResultToRenditionResources(resources, result);

    expect(updated[0]).toMatchObject({
      status: 'PROCESSING',
      reason: 'Timeout contacting preview service',
      category: 'TEMPORARY',
      previewStatus: 'PROCESSING',
      updatedAt: '2026-03-29T09:00:00',
    });
  });
});
