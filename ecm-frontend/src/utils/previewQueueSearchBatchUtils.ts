import { PreviewQueueSearchBatchItem } from 'services/nodeService';

export type PreviewQueueSearchOverride = {
  previewStatus?: string | null;
  previewFailureReason?: string | null;
  previewFailureCategory?: string | null;
  previewLastUpdated?: string | null;
  attempts?: number;
  nextAttemptAt?: string;
  queueState?: string;
  message?: string | null;
};

export const applyPreviewQueueSearchBatchResultToOverrides = (
  previous: Record<string, PreviewQueueSearchOverride>,
  results: PreviewQueueSearchBatchItem[]
): Record<string, PreviewQueueSearchOverride> => {
  if (!Array.isArray(results) || results.length === 0) {
    return previous;
  }
  const next = { ...previous };
  results.forEach((item) => {
    if (!item.documentId || item.outcome === 'FAILED') {
      return;
    }
    next[item.documentId] = {
      ...next[item.documentId],
      previewStatus: item.previewStatus,
      previewFailureReason: item.previewFailureReason ?? null,
      previewFailureCategory: item.previewFailureCategory ?? null,
      previewLastUpdated: item.previewLastUpdated ?? null,
      attempts: item.attempts,
      nextAttemptAt: item.nextAttemptAt || undefined,
      queueState: item.queueState || item.outcome || undefined,
      message: item.message || null,
    };
  });
  return next;
};
