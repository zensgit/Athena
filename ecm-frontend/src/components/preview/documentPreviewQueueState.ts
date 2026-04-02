import { PreviewQueueStatus } from 'services/nodeService';

export interface DocumentPreviewQueueFallbackState {
  previewStatus: string | null;
  previewFailureReason: string | null;
  previewFailureCategory: string | null;
  previewLastUpdated: string | null;
}

export interface DocumentPreviewQueueResolvedState extends DocumentPreviewQueueFallbackState {
  queued: boolean | null;
  attempts: number | null;
  nextAttemptAt: string | null;
  message: string | null;
}

export const resolveDocumentPreviewQueueState = (
  queueStatus: PreviewQueueStatus | null,
  fallback: DocumentPreviewQueueFallbackState
): DocumentPreviewQueueResolvedState => ({
  previewStatus: queueStatus?.previewStatus ?? fallback.previewStatus,
  previewFailureReason: queueStatus?.previewFailureReason ?? fallback.previewFailureReason,
  previewFailureCategory: queueStatus?.previewFailureCategory ?? fallback.previewFailureCategory,
  previewLastUpdated: queueStatus?.previewLastUpdated ?? fallback.previewLastUpdated,
  queued: queueStatus?.queued ?? null,
  attempts: queueStatus?.attempts ?? null,
  nextAttemptAt: queueStatus?.nextAttemptAt ?? null,
  message: queueStatus?.message ?? null,
});
