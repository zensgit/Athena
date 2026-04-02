import { summarizeNodeAssociationEdges } from './nodeAssociationUtils';

const relationEdge = {
  relationId: 'rel-1',
  relationType: 'cm:references',
  source: {
    id: 'source-1',
    name: 'Source Doc',
    path: '/Sites/source',
    nodeType: 'DOCUMENT',
    parentId: null,
  },
  target: {
    id: 'target-1',
    name: 'Target Doc',
    path: '/Sites/target',
    nodeType: 'DOCUMENT',
    parentId: null,
  },
  createdDate: '2026-03-30T12:00:00Z',
};

describe('nodeAssociationUtils', () => {
  it('summarizes target associations using target node labels', () => {
    expect(summarizeNodeAssociationEdges([relationEdge], 'target', 5)).toBe(
      'cm:references -> Target Doc'
    );
  });

  it('summarizes source associations using source node labels', () => {
    expect(summarizeNodeAssociationEdges([relationEdge], 'source', 5)).toBe(
      'cm:references -> Source Doc'
    );
  });

  it('reuses target labels for secondary children', () => {
    expect(summarizeNodeAssociationEdges([relationEdge], 'secondaryChild', 5)).toBe(
      'cm:references -> Target Doc'
    );
  });

  it('reuses source labels for secondary parents', () => {
    expect(summarizeNodeAssociationEdges([relationEdge], 'secondaryParent', 5)).toBe(
      'cm:references -> Source Doc'
    );
  });

  it('limits the rendered edge count', () => {
    expect(
      summarizeNodeAssociationEdges([relationEdge, { ...relationEdge, relationId: 'rel-2' }], 'target', 1)
    ).toBe('cm:references -> Target Doc');
  });
});
