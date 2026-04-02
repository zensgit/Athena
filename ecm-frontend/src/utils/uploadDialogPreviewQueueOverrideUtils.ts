import { Node } from 'types';

import { PreviewQueueOverride } from './previewQueueOverrideUtils';

export type UploadedNodeWithQueueOverride = Node & {
  queueState?: string | null;
  attempts?: number;
  nextAttemptAt?: string | null;
  message?: string | null;
};

export const applyUploadPreviewQueueLocalOverrides = (
  items: Node[],
  overrides: Record<string, PreviewQueueOverride>
): UploadedNodeWithQueueOverride[] => (
  items.map((item) => {
    const queueStatus = overrides[item.id];
    if (!queueStatus) {
      return item as UploadedNodeWithQueueOverride;
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
