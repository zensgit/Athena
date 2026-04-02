import { PreviewFailureSample } from 'services/previewDiagnosticsService';

import { PreviewQueueOverride } from './previewQueueOverrideUtils';

export type PreviewFailureSampleWithQueueOverride = PreviewFailureSample & {
  queueState?: string | null;
  attempts?: number;
  nextAttemptAt?: string | null;
  message?: string | null;
};

export const applyPreviewQueueLocalOverrides = (
  items: PreviewFailureSample[],
  overrides: Record<string, PreviewQueueOverride>
): PreviewFailureSampleWithQueueOverride[] => (
  items.map((item) => {
    const queueStatus = overrides[item.id];
    if (!queueStatus) {
      return item as PreviewFailureSampleWithQueueOverride;
    }
    return {
      ...item,
      previewStatus: queueStatus.previewStatus ?? item.previewStatus,
      previewFailureReason: queueStatus.previewFailureReason ?? item.previewFailureReason,
      previewFailureCategory: queueStatus.previewFailureCategory ?? item.previewFailureCategory,
      previewLastUpdated: queueStatus.previewLastUpdated ?? item.previewLastUpdated,
      queueState: queueStatus.queueState ?? null,
      attempts: queueStatus.attempts,
      nextAttemptAt: queueStatus.nextAttemptAt ?? null,
      message: queueStatus.message ?? null,
    };
  })
);
