import { NodeRenditionDefinitionStatus, NodeRenditionMutationResponse } from 'services/nodeService';

const normalizeState = (state?: string | null): string => (state || '').trim().toUpperCase();

export const formatRenditionGenerationMode = (generationMode?: string | null): string => {
  switch (normalizeState(generationMode)) {
    case 'PREVIEW_PIPELINE':
      return 'preview pipeline';
    case 'PREVIEW_DERIVED':
      return 'preview-derived';
    default:
      return generationMode?.trim() || 'unknown generation mode';
  }
};

export const getRenditionDefinitionDisplayState = (
  definition: Pick<NodeRenditionDefinitionStatus, 'registered' | 'applicable' | 'currentState'>
): string => {
  if (!definition.registered) {
    return 'unregistered';
  }
  if (!definition.applicable) {
    return 'not applicable';
  }
  switch (normalizeState(definition.currentState)) {
    case '':
    case 'REGISTERED':
      return 'pending';
    case 'READY':
      return 'ready';
    case 'PROCESSING':
      return 'processing';
    case 'FAILED':
      return 'failed';
    case 'UNSUPPORTED':
      return 'unsupported';
    case 'STALE':
      return 'stale';
    default:
      return (definition.currentState || '').trim().toLowerCase() || 'unknown';
  }
};

const sortDefinitions = (
  definitions: NodeRenditionDefinitionStatus[]
): NodeRenditionDefinitionStatus[] => (
  [...definitions].sort((left, right) => {
    if (left.sortOrder !== right.sortOrder) {
      return left.sortOrder - right.sortOrder;
    }
    return left.label.localeCompare(right.label);
  })
);

export const formatRenditionDefinitionLine = (definition: NodeRenditionDefinitionStatus): string => {
  const parts: string[] = [
    `${definition.label} ${getRenditionDefinitionDisplayState(definition)}`,
  ];

  if (definition.generationMode) {
    parts.push(`via ${formatRenditionGenerationMode(definition.generationMode)}`);
  }
  if (definition.dependencyRenditionKey) {
    parts.push(`depends on ${definition.dependencyRenditionKey}`);
  }
  if (!definition.applicable && definition.applicabilityReason) {
    parts.push(definition.applicabilityReason);
  }

  return parts.join(' • ');
};

export const getRenditionDefinitionLines = (
  definitions: NodeRenditionDefinitionStatus[]
): string[] => sortDefinitions(definitions).map(formatRenditionDefinitionLine);

export const summarizeRenditionDefinitions = (
  definitions: NodeRenditionDefinitionStatus[],
  maxItems = 2
): string => {
  const lines = getRenditionDefinitionLines(definitions);
  if (lines.length === 0) {
    return '';
  }
  const visible = lines.slice(0, Math.max(1, maxItems));
  const remaining = lines.length - visible.length;
  return remaining > 0
    ? `${visible.join(' • ')} • +${remaining} more`
    : visible.join(' • ');
};

export const applyRenditionMutationToDefinitions = (
  definitions: NodeRenditionDefinitionStatus[],
  mutation: Pick<NodeRenditionMutationResponse, 'renditionKey' | 'resource'>
): NodeRenditionDefinitionStatus[] => definitions.map((definition) => {
  if (definition.renditionKey !== mutation.renditionKey) {
    return definition;
  }
  return {
    ...definition,
    currentState: mutation.resource.state,
    available: mutation.resource.available,
    applicable: mutation.resource.applicable,
    applicabilityReason: mutation.resource.applicabilityReason ?? definition.applicabilityReason,
    contentUrl: mutation.resource.contentUrl ?? definition.contentUrl,
  };
});

export const formatRenditionMutationSummary = (
  mutation: Pick<NodeRenditionMutationResponse, 'message' | 'previewSummary'>
): string => {
  const parts: string[] = [];
  if (mutation.message?.trim()) {
    parts.push(mutation.message.trim());
  }
  const previewSummary = mutation.previewSummary;
  if (previewSummary?.previewStatus) {
    let previewPart = `Effective preview ${previewSummary.previewStatus.toLowerCase()}`;
    if (previewSummary.previewFailureReason?.trim()) {
      previewPart += `: ${previewSummary.previewFailureReason.trim()}`;
    }
    parts.push(previewPart);
  }
  return parts.join(' • ');
};
