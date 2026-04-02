import {
  PreviewQueueCancelActiveResult,
  PreviewQueueDiagnosticsSummary,
} from 'services/previewDiagnosticsService';
import { applyQueueCancelActiveResultToQueueDiagnosticsSummary } from './previewQueueDiagnosticsUtils';

describe('previewQueueDiagnosticsUtils', () => {
  it('projects cancelled queue results back onto queue diagnostics rows', () => {
    const summary: PreviewQueueDiagnosticsSummary = {
      backend: 'MEMORY',
      queueEnabled: true,
      scheduledCount: 2,
      governanceCount: 2,
      runningCount: 1,
      runningCountAccurate: true,
      cancellationRequestedCount: 0,
      sampleLimit: 20,
      sampleTruncated: false,
      stateFilter: 'ALL',
      queryFilter: null,
      totalSampledItems: 2,
      filteredSampledItems: 2,
      items: [
        {
          documentId: 'doc-1',
          name: 'running.bin',
          path: '/Root/running.bin',
          mimeType: 'application/octet-stream',
          previewStatus: 'FAILED',
          previewFailureReason: 'timeout',
          previewFailureCategory: 'TEMPORARY',
          previewLastUpdated: '2026-03-29T10:00:00',
          queueState: 'RUNNING',
          governanceKey: 'doc-1|preview|hash-a',
          attempts: 1,
          nextAttemptAt: '2026-03-29T10:05:00Z',
          running: true,
          cancelRequested: false,
        },
        {
          documentId: 'doc-2',
          name: 'queued.bin',
          path: '/Root/queued.bin',
          mimeType: 'application/pdf',
          previewStatus: 'PROCESSING',
          previewFailureReason: null,
          previewFailureCategory: null,
          previewLastUpdated: null,
          queueState: 'QUEUED',
          governanceKey: 'doc-2|preview|hash-b',
          attempts: 0,
          nextAttemptAt: null,
          running: false,
          cancelRequested: false,
        },
      ],
    };

    const result: PreviewQueueCancelActiveResult = {
      stateFilter: 'ALL',
      queryFilter: null,
      limit: 20,
      requested: 2,
      cancelled: 1,
      skipped: 1,
      failed: 0,
      results: [
        {
          documentId: 'doc-1',
          previewStatus: 'PROCESSING',
          previewFailureReason: null,
          previewFailureCategory: null,
          previewLastUpdated: '2026-03-29T10:10:00',
          queueState: 'CANCEL_REQUESTED',
          outcome: 'CANCELLED',
          message: 'cancel requested',
        },
        { documentId: 'doc-2', queueState: 'QUEUED', outcome: 'SKIPPED', message: 'not running' },
      ],
    };

    const updated = applyQueueCancelActiveResultToQueueDiagnosticsSummary(summary, result);

    expect(updated?.runningCount).toBe(0);
    expect(updated?.cancellationRequestedCount).toBe(1);
    expect(updated?.items[0].queueState).toBe('CANCEL_REQUESTED');
    expect(updated?.items[0].running).toBe(false);
    expect(updated?.items[0].cancelRequested).toBe(true);
    expect(updated?.items[0].previewLastUpdated).toBe('2026-03-29T10:10:00');
    expect(updated?.items[1].queueState).toBe('QUEUED');
  });

  it('leaves summary unchanged when cancel result has no matching items', () => {
    const summary: PreviewQueueDiagnosticsSummary = {
      backend: 'MEMORY',
      queueEnabled: true,
      scheduledCount: 1,
      governanceCount: 1,
      runningCount: 1,
      runningCountAccurate: true,
      cancellationRequestedCount: 0,
      sampleLimit: 20,
      sampleTruncated: false,
      stateFilter: 'ALL',
      queryFilter: null,
      totalSampledItems: 1,
      filteredSampledItems: 1,
      items: [
        {
          documentId: 'doc-1',
          name: 'running.bin',
          path: '/Root/running.bin',
          mimeType: 'application/octet-stream',
          previewStatus: 'FAILED',
          previewFailureReason: 'timeout',
          previewFailureCategory: 'TEMPORARY',
          previewLastUpdated: '2026-03-29T10:00:00',
          queueState: 'RUNNING',
          governanceKey: 'doc-1|preview|hash-a',
          attempts: 1,
          nextAttemptAt: '2026-03-29T10:05:00Z',
          running: true,
          cancelRequested: false,
        },
      ],
    };

    const result: PreviewQueueCancelActiveResult = {
      stateFilter: 'ALL',
      queryFilter: null,
      limit: 20,
      requested: 1,
      cancelled: 0,
      skipped: 1,
      failed: 0,
      results: [{ documentId: 'doc-2', queueState: 'QUEUED', outcome: 'SKIPPED', message: 'not running' }],
    };

    expect(applyQueueCancelActiveResultToQueueDiagnosticsSummary(summary, result)).toEqual(summary);
  });
});
