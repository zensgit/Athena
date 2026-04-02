import { NodeCheckoutGraph } from 'services/nodeService';
import {
  formatCheckoutGraphEdgeLabel,
  formatCheckoutGraphNodeLabel,
  formatCheckoutGraphSummary,
  getCheckoutGraphNodes,
} from './checkoutGraphUtils';

const baseGraph: NodeCheckoutGraph = {
  nodeId: 'doc-1',
  document: true,
  checkedOut: true,
  checkoutUser: 'alice',
  checkoutDate: '2026-03-27T00:00:00Z',
  documentNode: { id: 'doc-1', kind: 'DOCUMENT', label: 'Contract.docx', focus: true, virtualNode: false, available: true },
  workingCopyNode: { id: 'working-copy:doc-1', kind: 'WORKING_COPY', label: 'alice working copy', focus: false, virtualNode: true, available: true },
  destinationNode: { id: 'folder-1', kind: 'DESTINATION_FOLDER', label: '/workspace/contracts', focus: false, virtualNode: false, available: true },
  baselineVersion: {
    id: 'ver-1',
    documentId: 'doc-1',
    versionLabel: '1.2',
    comment: '',
    created: '2026-03-20T00:00:00Z',
    creator: 'alice',
    size: 10,
    isMajor: false,
    mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    contentHash: null,
    contentId: null,
    status: 'ACTIVE',
    checkoutBaseline: true,
    checkoutCurrent: false,
  },
  currentVersion: {
    id: 'ver-2',
    documentId: 'doc-1',
    versionLabel: '1.3',
    comment: '',
    created: '2026-03-27T00:00:00Z',
    creator: 'alice',
    size: 11,
    isMajor: false,
    mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    contentHash: null,
    contentId: null,
    status: 'ACTIVE',
    checkoutBaseline: false,
    checkoutCurrent: true,
  },
  nodes: [
    { id: 'doc-1', kind: 'DOCUMENT', label: 'Contract.docx', focus: true, virtualNode: false, available: true },
    { id: 'working-copy:doc-1', kind: 'WORKING_COPY', label: 'alice working copy', focus: false, virtualNode: true, available: true },
    { id: 'ver-1', kind: 'BASELINE_VERSION', label: 'v1.2', focus: false, virtualNode: false, available: true },
    { id: 'ver-2', kind: 'CURRENT_VERSION', label: 'v1.3', focus: false, virtualNode: false, available: true },
    { id: 'folder-1', kind: 'DESTINATION_FOLDER', label: '/workspace/contracts', focus: false, virtualNode: false, available: true },
  ],
  edges: [
    { relationType: 'HAS_WORKING_COPY', sourceId: 'doc-1', targetId: 'working-copy:doc-1', label: 'active checkout' },
  ],
  canCheckIn: true,
  canCancelCheckout: true,
  canKeepCheckedOut: true,
  blockingReason: null,
};

describe('checkoutGraphUtils', () => {
  it('formats an active checkout graph summary', () => {
    expect(formatCheckoutGraphSummary(baseGraph)).toBe(
      'alice working copy • from v1.2 • to current v1.3 • target /workspace/contracts • keep-checked-out supported',
    );
  });

  it('formats an available document summary when there is no active checkout', () => {
    expect(formatCheckoutGraphSummary({ ...baseGraph, checkedOut: false })).toBe(
      'Document is currently available and has no active checkout graph.',
    );
  });

  it('formats edge labels with relation type and label', () => {
    expect(formatCheckoutGraphEdgeLabel(baseGraph.edges[0])).toBe('HAS_WORKING_COPY: active checkout');
  });

  it('returns explicit graph nodes when provided', () => {
    expect(getCheckoutGraphNodes(baseGraph)).toHaveLength(5);
    expect(getCheckoutGraphNodes(baseGraph)[2].kind).toBe('BASELINE_VERSION');
  });

  it('formats graph node labels by node kind', () => {
    expect(formatCheckoutGraphNodeLabel(baseGraph.nodes[3])).toBe('Current: v1.3');
  });
});
