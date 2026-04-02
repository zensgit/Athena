import { NodeRelationEdge } from 'services/nodeService';

export type NodeAssociationRole =
  | 'source'
  | 'target'
  | 'secondaryChild'
  | 'secondaryParent';

const resolveEdgeNodeLabel = (
  edge: NodeRelationEdge,
  role: NodeAssociationRole
): string => {
  const ref = role === 'source' || role === 'secondaryParent'
    ? edge.source
    : edge.target;
  return ref?.name || ref?.path || 'unknown';
};

export const summarizeNodeAssociationEdges = (
  edges: NodeRelationEdge[],
  role: NodeAssociationRole,
  limit: number
): string =>
  edges
    .slice(0, limit)
    .map((edge) => `${edge.relationType} -> ${resolveEdgeNodeLabel(edge, role)}`)
    .join(' • ');
