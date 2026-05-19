import api from './api';
import nodeService, { NODE_UNEXPECTED_RESPONSE_MESSAGE } from './nodeService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const HTML_FALLBACK = '<!doctype html><html><body>app shell</body></html>';

const nodeRef = { id: 'n1', name: 'doc', path: '/a/doc', nodeType: 'cm:content' };
const relationsSummary = {
  nodeId: 'n1',
  nodeType: 'cm:content',
  parentCount: 1,
  childCount: 0,
  sourceRelationCount: 2,
  targetRelationCount: 0,
  versionCount: 3,
  previewStatus: null,
  renditionAvailable: true,
  checkedOut: false,
};
const relationEdge = {
  relationId: 'r1',
  relationType: 'cm:references',
  source: nodeRef,
  target: nodeRef,
};
const apiVersion = {
  id: 'v1',
  versionLabel: '1.0',
  createdDate: '2026-05-19T00:00:00Z',
  creator: 'admin',
  size: 123,
  major: true,
};
const mappedVersion = {
  id: 'v1',
  documentId: 'n1',
  versionLabel: '1.0',
  comment: undefined,
  created: '2026-05-19T00:00:00Z',
  creator: 'admin',
  size: 123,
  isMajor: true,
  mimeType: undefined,
  contentHash: undefined,
  contentId: undefined,
  status: undefined,
  checkoutBaseline: undefined,
  checkoutCurrent: undefined,
};
const checkoutRelation = {
  nodeId: 'n1',
  document: true,
  checkedOut: false,
  canCheckout: true,
  canCheckIn: false,
  canCancelCheckout: false,
  canKeepCheckedOut: false,
  requiresNewVersionFile: false,
};
const graphNode = { id: 'g1', kind: 'DOCUMENT', label: 'Doc', focus: true, virtualNode: false, available: true };
const graphEdge = { relationType: 'WORKING_COPY', sourceId: 'g1', targetId: 'g2', label: 'wc' };
const checkoutGraphRaw = {
  nodeId: 'n1',
  document: true,
  checkedOut: true,
  baselineVersion: apiVersion,
  currentVersion: apiVersion,
  nodes: [graphNode],
  edges: [graphEdge],
  canCheckIn: true,
  canCancelCheckout: true,
  canKeepCheckedOut: false,
};
const renditionRelationSummary = {
  nodeId: 'n1',
  document: true,
  previewStatus: 'AVAILABLE',
  renditionAvailable: true,
};
const renditionRelation = {
  nodeId: 'n1',
  renditionId: 'pdf',
  label: 'PDF',
  status: 'AVAILABLE',
  available: true,
  mimeType: 'application/pdf',
  url: '/r/pdf',
  downloadable: true,
};
const renditionDefinition = {
  nodeId: 'n1',
  renditionKey: 'pdf',
  label: 'PDF',
  targetMimeType: 'application/pdf',
  downloadable: true,
  sortOrder: 1,
  registered: true,
  applicable: true,
  available: true,
  canRequeue: true,
  canInvalidate: true,
};
const mutationResource = {
  id: 'res1',
  documentId: 'n1',
  renditionKey: 'pdf',
  label: 'PDF',
  mimeType: 'application/pdf',
  state: 'AVAILABLE',
  available: true,
  downloadable: true,
  applicable: true,
  sortOrder: 1,
};
const mutationResponse = {
  renditionKey: 'pdf',
  action: 'REQUEUE',
  invalidated: false,
  previewLinked: true,
  resource: mutationResource,
};
const page = <T>(content: T[]) => ({ content, totalElements: content.length, totalPages: 1, number: 0, size: 5 });

beforeEach(() => {
  jest.clearAllMocks();
});

describe('nodeService relations/renditions response shape guards', () => {
  it('returns valid DTOs and preserves endpoints/params/payloads', async () => {
    mockedApi.get.mockResolvedValueOnce(relationsSummary);
    await expect(nodeService.getNodeRelationsSummary('n1')).resolves.toEqual(relationsSummary);
    expect(mockedApi.get).toHaveBeenLastCalledWith('/nodes/n1/relations/summary');

    mockedApi.get.mockResolvedValueOnce([nodeRef]);
    await expect(nodeService.getNodeRelationParents('n1', 7)).resolves.toEqual([nodeRef]);
    expect(mockedApi.get).toHaveBeenLastCalledWith('/nodes/n1/relations/parents', {
      params: { maxDepth: 7 },
    });

    mockedApi.get.mockResolvedValueOnce(page([relationEdge]));
    await expect(nodeService.getNodeRelationSources('n1', 2, 5, 'cm:x')).resolves.toEqual(
      page([relationEdge])
    );
    expect(mockedApi.get).toHaveBeenLastCalledWith('/nodes/n1/relations/sources', {
      params: { page: 2, size: 5, relationType: 'cm:x' },
    });

    mockedApi.get.mockResolvedValueOnce(page([relationEdge]));
    await nodeService.getNodeRelationTargets('n1');
    expect(mockedApi.get).toHaveBeenLastCalledWith('/nodes/n1/relations/targets', {
      params: { page: 0, size: 5, relationType: undefined },
    });

    mockedApi.get.mockResolvedValueOnce(checkoutRelation);
    await expect(nodeService.getNodeRelationCheckout('n1')).resolves.toEqual(checkoutRelation);

    mockedApi.get.mockResolvedValueOnce(renditionRelation);
    await expect(nodeService.getNodeRelationRendition('n1', 'pdf')).resolves.toEqual(renditionRelation);
    expect(mockedApi.get).toHaveBeenLastCalledWith('/nodes/n1/relations/renditions/pdf');

    mockedApi.get.mockResolvedValueOnce(renditionRelationSummary);
    await expect(nodeService.getNodeRenditionRelationSummary('n1')).resolves.toEqual(
      renditionRelationSummary
    );

    mockedApi.get.mockResolvedValueOnce([renditionDefinition]);
    await expect(nodeService.getNodeRenditionDefinitions('n1')).resolves.toEqual([renditionDefinition]);

    mockedApi.post.mockResolvedValueOnce(mutationResponse);
    await expect(nodeService.requeueNodeRendition('n1', 'pdf', true)).resolves.toEqual(mutationResponse);
    expect(mockedApi.post).toHaveBeenLastCalledWith(
      '/nodes/n1/renditions/pdf/requeue',
      null,
      { params: { force: true } }
    );

    mockedApi.post.mockResolvedValueOnce(mutationResponse);
    await nodeService.invalidateNodeRendition('n1', 'pdf', { reason: 'stale', requeue: true });
    expect(mockedApi.post).toHaveBeenLastCalledWith(
      '/nodes/n1/renditions/pdf/invalidate',
      null,
      { params: { reason: 'stale', requeue: true, forceQueue: true } }
    );

    mockedApi.post.mockResolvedValueOnce(mutationResponse);
    await nodeService.invalidateNodeRendition('n1', 'pdf');
    expect(mockedApi.post).toHaveBeenLastCalledWith(
      '/nodes/n1/renditions/pdf/invalidate',
      null,
      { params: { reason: undefined, requeue: false, forceQueue: true } }
    );
  });

  it('preserves the client-side mappings byte-for-byte for valid raw input', async () => {
    // getNodeRelationVersions: PageResponse<ApiVersionResponse> -> Version[]
    mockedApi.get.mockResolvedValueOnce(page([apiVersion]));
    await expect(nodeService.getNodeRelationVersions('n1', 0, 5, true)).resolves.toEqual([mappedVersion]);
    expect(mockedApi.get).toHaveBeenLastCalledWith('/nodes/n1/relations/versions', {
      params: { page: 0, size: 5, majorOnly: true },
    });

    // getNodeRelationCheckoutGraph: raw inline -> NodeCheckoutGraph (incl. version mapping)
    mockedApi.get.mockResolvedValueOnce(checkoutGraphRaw);
    await expect(nodeService.getNodeRelationCheckoutGraph('n1')).resolves.toEqual({
      nodeId: 'n1',
      document: true,
      checkedOut: true,
      checkoutUser: undefined,
      checkoutDate: undefined,
      documentNode: null,
      workingCopyNode: null,
      destinationNode: null,
      baselineVersion: mappedVersion,
      currentVersion: mappedVersion,
      nodes: [graphNode],
      edges: [graphEdge],
      canCheckIn: true,
      canCancelCheckout: true,
      canKeepCheckedOut: false,
      blockingReason: undefined,
    });

    // getNodeRelationRenditions: PageResponse<NodeRenditionRelation> -> content
    mockedApi.get.mockResolvedValueOnce(page([renditionRelation]));
    await expect(nodeService.getNodeRelationRenditions('n1', 1, 5)).resolves.toEqual([renditionRelation]);
    expect(mockedApi.get).toHaveBeenLastCalledWith('/nodes/n1/relations/renditions', {
      params: { page: 1, size: 5 },
    });
  });

  it('throws the sentinel on HTML fallback / null / missing-field / bad-nested-item', async () => {
    const expectThrow = async (fn: () => Promise<unknown>) =>
      expect(fn()).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);

    for (const bad of [HTML_FALLBACK, null]) {
      mockedApi.get.mockResolvedValueOnce(bad);
      await expectThrow(() => nodeService.getNodeRelationsSummary('n1'));
      mockedApi.get.mockResolvedValueOnce(bad);
      await expectThrow(() => nodeService.getNodeRelationParents('n1'));
      // D2: these previously degraded silently to []/[] -> now throw
      mockedApi.get.mockResolvedValueOnce(bad);
      await expectThrow(() => nodeService.getNodeRelationVersions('n1'));
      mockedApi.get.mockResolvedValueOnce(bad);
      await expectThrow(() => nodeService.getNodeRelationCheckoutGraph('n1'));
      mockedApi.get.mockResolvedValueOnce(bad);
      await expectThrow(() => nodeService.getNodeRelationRenditions('n1'));
    }

    // missing required field
    mockedApi.get.mockResolvedValueOnce({ ...relationsSummary, parentCount: '1' });
    await expectThrow(() => nodeService.getNodeRelationsSummary('n1'));

    // PageResponse missing content (D2 tightening for sources/targets/versions/renditions)
    mockedApi.get.mockResolvedValueOnce({ totalElements: 0, totalPages: 0, number: 0, size: 5 });
    await expectThrow(() => nodeService.getNodeRelationSources('n1'));

    // bad nested item: edge.source not a node ref
    mockedApi.get.mockResolvedValueOnce(page([{ ...relationEdge, source: { id: 1 } }]));
    await expectThrow(() => nodeService.getNodeRelationTargets('n1'));

    // bad nested array element in checkout graph
    mockedApi.get.mockResolvedValueOnce({ ...checkoutGraphRaw, nodes: [{ id: 'g1' }] });
    await expectThrow(() => nodeService.getNodeRelationCheckoutGraph('n1'));

    // mutation response with malformed nested resource
    mockedApi.post.mockResolvedValueOnce({ ...mutationResponse, resource: { id: 'x' } });
    await expectThrow(() => nodeService.requeueNodeRendition('n1', 'pdf'));

    mockedApi.post.mockResolvedValueOnce(HTML_FALLBACK);
    await expectThrow(() => nodeService.invalidateNodeRendition('n1', 'pdf'));

    mockedApi.get.mockResolvedValueOnce([{ ...renditionDefinition, sortOrder: null }]);
    await expectThrow(() => nodeService.getNodeRenditionDefinitions('n1'));
  });

  it('accepts omitted optional/nullable fields and present optional nested objects', async () => {
    // checkout graph with present documentNode and a queue-less mutation
    mockedApi.get.mockResolvedValueOnce({
      ...checkoutGraphRaw,
      documentNode: graphNode,
      checkoutUser: 'alice',
      blockingReason: null,
    });
    const graph = await nodeService.getNodeRelationCheckoutGraph('n1');
    expect(graph.documentNode).toEqual(graphNode);
    expect(graph.checkoutUser).toBe('alice');

    mockedApi.post.mockResolvedValueOnce({
      ...mutationResponse,
      message: 'queued',
      queueStatus: { documentId: 'n1', queued: true, attempts: 0 },
      previewSummary: renditionRelationSummary,
    });
    await expect(nodeService.requeueNodeRendition('n1', 'pdf')).resolves.toMatchObject({
      message: 'queued',
      queueStatus: { documentId: 'n1', queued: true, attempts: 0 },
    });
  });
});
