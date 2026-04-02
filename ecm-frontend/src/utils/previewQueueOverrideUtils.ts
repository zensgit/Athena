import { PreviewQueueStatus } from 'services/nodeService';

export type PreviewQueueOverride = {
  previewStatus?: string | null;
  previewFailureReason?: string | null;
  previewFailureCategory?: string | null;
  previewLastUpdated?: string | null;
  attempts?: number;
  nextAttemptAt?: string;
  queueState?: string;
  message?: string | null;
};

export const buildPreviewQueueOverride = (status?: PreviewQueueStatus | null): PreviewQueueOverride => ({
  previewStatus: status?.previewStatus,
  previewFailureReason: status?.previewFailureReason,
  previewFailureCategory: status?.previewFailureCategory,
  previewLastUpdated: status?.previewLastUpdated,
  attempts: status?.attempts,
  nextAttemptAt: status?.nextAttemptAt,
  queueState: status ? (status.queued ? 'QUEUED' : 'DECLINED') : undefined,
  message: status?.message || null,
});
