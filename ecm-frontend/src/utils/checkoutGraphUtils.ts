import { NodeCheckoutGraph, NodeCheckoutGraphEdge, NodeCheckoutGraphNode } from 'services/nodeService';

export const formatCheckoutGraphSummary = (graph: NodeCheckoutGraph): string => {
  if (!graph.document) {
    return 'Checkout graph is only available for document nodes.';
  }
  if (!graph.checkedOut) {
    return 'Document is currently available and has no active checkout graph.';
  }

  const parts = [
    graph.workingCopyNode?.label || 'Working copy',
    graph.baselineVersion?.versionLabel ? `from v${graph.baselineVersion.versionLabel}` : null,
    graph.currentVersion?.versionLabel ? `to current v${graph.currentVersion.versionLabel}` : null,
    graph.destinationNode?.label ? `target ${graph.destinationNode.label}` : null,
    graph.canKeepCheckedOut ? 'keep-checked-out supported' : null,
  ].filter(Boolean) as string[];

  return parts.join(' • ');
};

export const formatCheckoutGraphEdgeLabel = (edge: NodeCheckoutGraphEdge): string => {
  const relationLabel = edge.label?.trim() || edge.relationType;
  return `${edge.relationType}: ${relationLabel}`;
};

export const getCheckoutGraphNodes = (graph: NodeCheckoutGraph): NodeCheckoutGraphNode[] => {
  if (graph.nodes.length > 0) {
    return graph.nodes;
  }

  return [
    graph.documentNode,
    graph.workingCopyNode,
    graph.destinationNode,
  ].filter(Boolean) as NodeCheckoutGraphNode[];
};

export const formatCheckoutGraphNodeLabel = (node: NodeCheckoutGraphNode): string => {
  switch (node.kind) {
    case 'DOCUMENT':
      return `Document: ${node.label}`;
    case 'WORKING_COPY':
      return `Working copy: ${node.label}`;
    case 'BASELINE_VERSION':
      return `Baseline: ${node.label}`;
    case 'CURRENT_VERSION':
      return `Current: ${node.label}`;
    case 'DESTINATION_FOLDER':
      return `Target: ${node.label}`;
    default:
      return `${node.kind}: ${node.label}`;
  }
};
