import {
  PreviewQueueCancelActiveResult,
  PreviewQueueDiagnosticsItem,
  PreviewQueueDiagnosticsSummary,
} from 'services/previewDiagnosticsService';

const isCancelRequestedQueueState = (queueState?: string | null) => (queueState || '').toUpperCase() === 'CANCEL_REQUESTED';
const isRunningQueueState = (queueState?: string | null) => (queueState || '').toUpperCase() === 'RUNNING';

const applyQueueCancelResultToItem = (
  item: PreviewQueueDiagnosticsItem,
  resultByDocumentId: Map<string, PreviewQueueCancelActiveResult['results'][number]>
): PreviewQueueDiagnosticsItem => {
  if (!item.documentId) {
    return item;
  }
  const result = resultByDocumentId.get(item.documentId);
  if (!result || result.outcome !== 'CANCELLED') {
    return item;
  }
  const nextQueueState = result.queueState || 'CANCEL_REQUESTED';
  return {
    ...item,
    previewStatus: result.previewStatus ?? item.previewStatus,
    previewFailureReason: result.previewFailureReason ?? item.previewFailureReason,
    previewFailureCategory: result.previewFailureCategory ?? item.previewFailureCategory,
    previewLastUpdated: result.previewLastUpdated ?? item.previewLastUpdated,
    queueState: nextQueueState,
    running: isRunningQueueState(nextQueueState),
    cancelRequested: isCancelRequestedQueueState(nextQueueState),
  };
};

export const applyQueueCancelActiveResultToQueueDiagnosticsSummary = (
  summary: PreviewQueueDiagnosticsSummary | null,
  result: PreviewQueueCancelActiveResult
): PreviewQueueDiagnosticsSummary | null => {
  if (!summary || !Array.isArray(summary.items) || summary.items.length === 0) {
    return summary;
  }
  const resultByDocumentId = new Map(
    result.results
      .filter((item) => item.documentId)
      .map((item) => [item.documentId as string, item])
  );
  if (resultByDocumentId.size === 0) {
    return summary;
  }
  const items = summary.items.map((item) => applyQueueCancelResultToItem(item, resultByDocumentId));
  return {
    ...summary,
    runningCount: items.filter((item) => item.running || isRunningQueueState(item.queueState)).length,
    cancellationRequestedCount: items.filter((item) => item.cancelRequested || isCancelRequestedQueueState(item.queueState)).length,
    items,
  };
};
