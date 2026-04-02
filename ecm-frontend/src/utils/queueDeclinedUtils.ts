import {
  PreviewQueueDeclinedClearResult,
  PreviewQueueDeclinedItem,
  PreviewQueueDeclinedRequeueResult,
  PreviewQueueDeclinedSummary,
} from 'services/previewDiagnosticsService';

export type PreviewQueueDeclinedLocalOverride = {
  previewStatus?: string | null;
  previewFailureReason?: string | null;
  previewFailureCategory?: string | null;
  previewLastUpdated?: string | null;
  hidden?: boolean;
};

export const applyQueueDeclinedLocalOverrides = (
  summary: PreviewQueueDeclinedSummary | null,
  overrides: Record<string, PreviewQueueDeclinedLocalOverride>
): PreviewQueueDeclinedSummary | null => {
  if (!summary) {
    return null;
  }
  if (!overrides || Object.keys(overrides).length === 0) {
    return summary;
  }

  const items = summary.items
    .filter((item) => {
      const documentId = item.documentId || '';
      return !documentId || !overrides[documentId]?.hidden;
    })
    .map((item) => applyQueueDeclinedItemOverride(item, overrides[item.documentId || '']));

  const categoryCountsMap = new Map<string, { count: number; forceRequiredCount: number }>();
  items.forEach((item) => {
    const category = (item.category || 'DECLINED').trim().toUpperCase() || 'DECLINED';
    const current = categoryCountsMap.get(category) || { count: 0, forceRequiredCount: 0 };
    current.count += 1;
    if (item.forceRequired) {
      current.forceRequiredCount += 1;
    }
    categoryCountsMap.set(category, current);
  });

  return {
    ...summary,
    items,
    filteredSampledItems: items.length,
    forceRequiredCount: items.filter((item) => item.forceRequired).length,
    categoryCounts: Array.from(categoryCountsMap.entries())
      .map(([category, counts]) => ({
        category,
        count: counts.count,
        forceRequiredCount: counts.forceRequiredCount,
      }))
      .sort((left, right) => {
        if (right.count !== left.count) {
          return right.count - left.count;
        }
        return left.category.localeCompare(right.category);
      }),
  };
};

export const buildQueueDeclinedOverridesFromRequeue = (
  result: PreviewQueueDeclinedRequeueResult | null | undefined
): Record<string, PreviewQueueDeclinedLocalOverride> => {
  if (!result?.results?.length) {
    return {};
  }
  return result.results.reduce<Record<string, PreviewQueueDeclinedLocalOverride>>((acc, item) => {
    const documentId = item.documentId?.trim();
    if (!documentId || !item.previewStatus) {
      return acc;
    }
    acc[documentId] = {
      previewStatus: item.previewStatus,
      previewFailureReason: item.previewFailureReason,
      previewFailureCategory: item.previewFailureCategory,
      previewLastUpdated: item.previewLastUpdated,
    };
    return acc;
  }, {});
};

export const buildQueueDeclinedOverridesFromClear = (
  result: PreviewQueueDeclinedClearResult | null | undefined
): Record<string, PreviewQueueDeclinedLocalOverride> => {
  if (!result?.results?.length) {
    return {};
  }
  return result.results.reduce<Record<string, PreviewQueueDeclinedLocalOverride>>((acc, item) => {
    const documentId = item.documentId?.trim();
    if (!documentId || item.outcome !== 'CLEARED') {
      return acc;
    }
    acc[documentId] = {
      hidden: true,
    };
    return acc;
  }, {});
};

const applyQueueDeclinedItemOverride = (
  item: PreviewQueueDeclinedItem,
  override?: PreviewQueueDeclinedLocalOverride
): PreviewQueueDeclinedItem => {
  if (!override) {
    return item;
  }
  return {
    ...item,
    previewStatus: override.previewStatus ?? item.previewStatus,
    previewFailureReason: override.previewFailureReason ?? item.previewFailureReason,
    previewFailureCategory: override.previewFailureCategory ?? item.previewFailureCategory,
    previewLastUpdated: override.previewLastUpdated ?? item.previewLastUpdated,
  };
};
