import {
  PreviewQueueBatchResult,
  PreviewRenditionResource,
} from 'services/previewDiagnosticsService';

export const applyQueueBatchResultToRenditionResources = (
  resources: PreviewRenditionResource[],
  result: PreviewQueueBatchResult | null | undefined
): PreviewRenditionResource[] => {
  if (!Array.isArray(resources) || resources.length === 0 || !result?.results?.length) {
    return resources;
  }

  const byDocumentId = new Map(
    result.results
      .filter((item) => item?.documentId)
      .map((item) => [item.documentId, item] as const)
  );

  if (byDocumentId.size === 0) {
    return resources;
  }

  return resources.map((resource) => {
    const documentId = resource.documentId || '';
    const queuedItem = documentId ? byDocumentId.get(documentId) : undefined;
    if (!queuedItem) {
      return resource;
    }
    return {
      ...resource,
      status: queuedItem.previewStatus ?? resource.status,
      reason: queuedItem.previewFailureReason ?? resource.reason,
      category: queuedItem.previewFailureCategory ?? resource.category,
      previewStatus: queuedItem.previewStatus ?? resource.previewStatus,
      updatedAt: queuedItem.previewLastUpdated ?? resource.updatedAt,
    };
  });
};
