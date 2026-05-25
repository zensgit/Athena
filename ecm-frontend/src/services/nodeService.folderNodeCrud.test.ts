import api from './api';
import nodeService, { NODE_UNEXPECTED_RESPONSE_MESSAGE } from './nodeService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    put: jest.fn(),
    patch: jest.fn(),
    delete: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const HTML_FALLBACK = '<!doctype html><html><body>app shell</body></html>';

const validFolder = {
  id: 'folder-1',
  name: 'My Folder',
  path: '/Sites/Finance/My Folder',
  folderType: 'GENERAL',
  parentId: 'parent-1',
  description: 'desc',
  inheritPermissions: true,
  createdBy: 'admin',
  createdDate: '2026-04-14T10:00:00Z',
  lastModifiedBy: 'admin',
  lastModifiedDate: '2026-04-14T10:00:00Z',
};

const systemRoot = {
  id: 'root-1',
  name: 'Root',
  path: '/Root',
  folderType: 'SYSTEM',
  createdBy: 'system',
  createdDate: '2024-01-01T00:00:00Z',
};

const validApiNode = {
  id: 'doc-1',
  name: 'Contract.pdf',
  path: '/Sites/Legal/Contract.pdf',
  nodeType: 'DOCUMENT',
  parentId: 'folder-1',
  size: 1024,
  contentType: 'application/pdf',
  createdBy: 'admin',
  createdDate: '2026-04-17T10:00:00Z',
  lastModifiedBy: 'admin',
  lastModifiedDate: '2026-04-17T10:00:00Z',
  aspects: ['cm:auditable'],
};

const validApiNodeDetails = {
  ...validApiNode,
  properties: { 'cm:description': 'a doc' },
};

const validNodeRelationEdge = {
  relationId: 'rel-1',
  relationType: 'cm:references',
  source: { id: 'n-src', name: 'Src', path: '/Src', nodeType: 'DOCUMENT' },
  target: { id: 'n-tgt', name: 'Tgt', path: '/Tgt', nodeType: 'DOCUMENT' },
};

beforeEach(() => {
  jest.clearAllMocks();
});

describe('nodeService folder/node CRUD response shape guards', () => {
  it('getRootFolder: valid /folders/roots array passes; normalized timestamps; pickPrimaryRoot string-comparable', async () => {
    // getNode('root') internally calls getRootFolder then folderToNode
    mockedApi.get.mockResolvedValueOnce([systemRoot]);
    const node = await nodeService.getNode('root');
    expect(node.id).toBe('root-1');
    expect(node.name).toBe('Root');
    expect(node.nodeType).toBe('FOLDER');
    expect(mockedApi.get).toHaveBeenLastCalledWith('/folders/roots');
  });

  // Regression lock for the real-backend `/folders/roots` shape captured in
  // CI run 26201381835 trace.zip: queryCriteria serializes as null (not
  // undefined or object). isFolderResponse must accept it.
  it('getRootFolder: real-backend shape with queryCriteria: null is accepted', async () => {
    const realBackendRoot = {
      id: '2e1fd1be-e291-4800-8456-98d5e836885d',
      name: 'Root',
      description: null,
      path: '/Root',
      parentId: null,
      folderType: 'SYSTEM',
      inheritPermissions: false,
      smart: false,
      queryCriteria: null,
      createdBy: 'system',
      createdDate: '2026-05-21T02:27:30.027808',
      lastModifiedBy: null,
      lastModifiedDate: null,
    };
    mockedApi.get.mockResolvedValueOnce([realBackendRoot]);
    const node = await nodeService.getNode('root');
    expect(node.id).toBe('2e1fd1be-e291-4800-8456-98d5e836885d');
    expect(node.nodeType).toBe('FOLDER');
  });

  it('getRootFolder: rejects HTML / null / bad array element', async () => {
    mockedApi.get.mockResolvedValueOnce(HTML_FALLBACK);
    await expect(nodeService.getNode('root')).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);

    mockedApi.get.mockResolvedValueOnce(null);
    await expect(nodeService.getNode('root')).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);

    // array with one element missing required `id`
    mockedApi.get.mockResolvedValueOnce([{ name: 'broken', path: '/x', folderType: 'SYSTEM' }]);
    await expect(nodeService.getNode('root')).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('getFolderByPath: valid → mapped Node; endpoint and params locked', async () => {
    mockedApi.get.mockResolvedValueOnce(validFolder);
    const node = await nodeService.getFolderByPath('/Sites/Finance/My Folder');
    expect(node.id).toBe('folder-1');
    expect(node.nodeType).toBe('FOLDER');
    expect(node.created).toBe('2026-04-14T10:00:00Z');
    expect(mockedApi.get).toHaveBeenLastCalledWith('/folders/path', {
      params: { path: '/Sites/Finance/My Folder' },
    });
  });

  it('getNode: regular id → GET /nodes/{id}; "root" → getRootFolder + folderToNode', async () => {
    mockedApi.get.mockResolvedValueOnce(validApiNodeDetails);
    const node = await nodeService.getNode('doc-1');
    expect(node.id).toBe('doc-1');
    expect(mockedApi.get).toHaveBeenLastCalledWith('/nodes/doc-1');

    mockedApi.get.mockResolvedValueOnce([systemRoot]);
    const rootNode = await nodeService.getNode('root');
    expect(rootNode.id).toBe('root-1');
    expect(rootNode.nodeType).toBe('FOLDER');
  });

  it('createFolder: normal path locks POST body; "root" parentId resolved via getRootFolder', async () => {
    mockedApi.post.mockResolvedValueOnce(validFolder);
    await nodeService.createFolder('parent-1', { name: 'Reports', description: 'd' });
    expect(mockedApi.post).toHaveBeenLastCalledWith('/folders', {
      name: 'Reports',
      description: 'd',
      parentId: 'parent-1',
      folderType: 'GENERAL',
      inheritPermissions: true,
      isSmart: undefined,
      queryCriteria: undefined,
    });

    // "root" branch: GET /folders/roots first, then POST /folders with resolved id
    mockedApi.get.mockResolvedValueOnce([systemRoot]);
    mockedApi.post.mockResolvedValueOnce(validFolder);
    await nodeService.createFolder('root', { name: 'X' });
    expect(mockedApi.get).toHaveBeenLastCalledWith('/folders/roots');
    expect(mockedApi.post).toHaveBeenLastCalledWith('/folders', expect.objectContaining({
      parentId: 'root-1',
    }));
  });

  it('updateNode: PATCH /nodes/{id} with updates body; valid response mapped', async () => {
    mockedApi.patch.mockResolvedValueOnce(validApiNodeDetails);
    const node = await nodeService.updateNode('doc-1', { name: 'New' });
    expect(node.id).toBe('doc-1');
    expect(mockedApi.patch).toHaveBeenLastCalledWith('/nodes/doc-1', { name: 'New' });
  });

  it('getFolder: GET /folders/{id} → FolderResponse mapped with smart + queryCriteria', async () => {
    const smartFolder = {
      ...validFolder,
      smart: true,
      queryCriteria: { query: 'type:contract', pathPrefix: '/Sites/legal' },
    };
    mockedApi.get.mockResolvedValueOnce(smartFolder);
    const node = await nodeService.getFolder('folder-1');
    expect(mockedApi.get).toHaveBeenLastCalledWith('/folders/folder-1');
    // folderToNode must carry smart + queryCriteria — the only source for edit prefill/detection.
    expect(node.smart).toBe(true);
    expect(node.queryCriteria).toEqual({ query: 'type:contract', pathPrefix: '/Sites/legal' });
  });

  it('getFolder: HTML/SPA fallback rejected with the unexpected-response sentinel', async () => {
    mockedApi.get.mockResolvedValueOnce(HTML_FALLBACK);
    await expect(nodeService.getFolder('folder-1')).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('updateFolder: PUT /folders/{id} locks the body and maps the FolderResponse', async () => {
    mockedApi.put.mockResolvedValueOnce({
      ...validFolder,
      smart: true,
      queryCriteria: { query: 'type:invoice' },
    });
    const node = await nodeService.updateFolder('folder-1', {
      isSmart: true,
      queryCriteria: { query: 'type:invoice' },
    });
    expect(mockedApi.put).toHaveBeenLastCalledWith('/folders/folder-1', {
      name: undefined,
      description: undefined,
      isSmart: true,
      queryCriteria: { query: 'type:invoice' },
    });
    expect(node.smart).toBe(true);
    expect(node.queryCriteria).toEqual({ query: 'type:invoice' });
  });

  it('moveNode / copyNode: POST endpoints + body shape locked', async () => {
    mockedApi.post.mockResolvedValueOnce(validApiNode);
    await nodeService.moveNode('doc-1', 'folder-2');
    expect(mockedApi.post).toHaveBeenLastCalledWith('/folders/folder-2/move', { nodeId: 'doc-1' });

    mockedApi.post.mockResolvedValueOnce(validApiNode);
    await nodeService.copyNode('doc-1', 'folder-2', false, 'CopyName');
    expect(mockedApi.post).toHaveBeenLastCalledWith('/folders/folder-2/copy', {
      nodeId: 'doc-1',
      newName: 'CopyName',
      deep: false,
    });
  });

  // Regression lock for the real-backend folder-item shape captured in
  // CI run 26204071473 trace.zip: folder nodes return size: null (folders
  // have no byte size). Both isApiNodeResponse and isApiNodeDetailsResponse
  // must accept it via isNullishOr(size, isFiniteNumber).
  it('getChildren: real-backend folder item with size: null passes primary + fallback', async () => {
    const realBackendFolderItem = {
      id: '5ce6f78b-e4f8-4c2b-bc37-4ebd1b4a81de',
      name: 'Archive',
      description: null,
      path: '/Root/Archive',
      nodeType: 'FOLDER',
      parentId: 'd712f22b-7190-425b-b713-c96a5b6a99c2',
      size: null,
      contentType: null,
      locked: false,
      lockedBy: null,
      createdBy: 'system',
      createdDate: '2026-05-21T03:58:00.274822',
      lastModifiedBy: null,
      lastModifiedDate: null,
    };
    mockedApi.get.mockResolvedValueOnce({ content: [realBackendFolderItem], totalElements: 1 });
    const nodes = await nodeService.getChildren('folder-1');
    expect(nodes).toHaveLength(1);
    expect(nodes[0].id).toBe('5ce6f78b-e4f8-4c2b-bc37-4ebd1b4a81de');
    expect(nodes[0].nodeType).toBe('FOLDER');

    // Fallback path (ApiNodeDetailsResponse) — same null-size shape must pass
    mockedApi.get.mockResolvedValueOnce(null); // primary throws (force fallback)
    mockedApi.get.mockResolvedValueOnce({ content: [{ ...realBackendFolderItem, properties: {}, metadata: {}, aspects: [] }] });
    const fbNodes = await nodeService.getChildren('folder-1');
    expect(fbNodes).toHaveLength(1);
    expect(fbNodes[0].id).toBe('5ce6f78b-e4f8-4c2b-bc37-4ebd1b4a81de');
  });

  it('getChildren: primary /folders/{id}/contents good → no fallback', async () => {
    mockedApi.get.mockResolvedValueOnce({ content: [validApiNode] });
    const nodes = await nodeService.getChildren('folder-1');
    expect(nodes).toHaveLength(1);
    expect(nodes[0].id).toBe('doc-1');
    expect(mockedApi.get).toHaveBeenCalledTimes(1);
    expect(mockedApi.get).toHaveBeenLastCalledWith('/folders/folder-1/contents', {
      params: { page: 0, size: 1000, sort: 'name,asc' },
    });
  });

  it('getChildren: primary bad → silent fallback to /nodes/{id}/children (gate H3a verification)', async () => {
    // primary returns malformed envelope (content not array)
    mockedApi.get.mockResolvedValueOnce({ content: 'oops' });
    // fallback returns valid envelope
    mockedApi.get.mockResolvedValueOnce({ content: [validApiNodeDetails] });
    const nodes = await nodeService.getChildren('folder-1');
    expect(nodes).toHaveLength(1);
    expect(nodes[0].id).toBe('doc-1');
    expect(mockedApi.get).toHaveBeenCalledTimes(2);
    expect(mockedApi.get).toHaveBeenNthCalledWith(2, '/nodes/folder-1/children', {
      params: { sortBy: 'name', ascending: true },
    });
  });

  it('getChildren: primary bad → fallback also bad → throw', async () => {
    mockedApi.get.mockResolvedValueOnce(HTML_FALLBACK);
    mockedApi.get.mockResolvedValueOnce(null);
    await expect(nodeService.getChildren('folder-1')).rejects.toThrow(
      NODE_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('getChildren("root"): resolves via getRootFolder then queries by resolved id', async () => {
    mockedApi.get.mockResolvedValueOnce([systemRoot]); // /folders/roots
    mockedApi.get.mockResolvedValueOnce({ content: [validApiNode] }); // primary contents
    const nodes = await nodeService.getChildren('root');
    expect(nodes).toHaveLength(1);
    expect(mockedApi.get).toHaveBeenNthCalledWith(1, '/folders/roots');
    expect(mockedApi.get).toHaveBeenNthCalledWith(2, '/folders/root-1/contents', expect.anything());
  });

  it('getChildrenPage: dual-endpoint behavior + total preserved; root resolves', async () => {
    mockedApi.get.mockResolvedValueOnce({
      content: [validApiNode],
      totalElements: 42,
    });
    const result = await nodeService.getChildrenPage('folder-1', 'name', true, 0, 50);
    expect(result.total).toBe(42);
    expect(result.nodes).toHaveLength(1);

    // primary bad → fallback good
    mockedApi.get.mockResolvedValueOnce(null);
    mockedApi.get.mockResolvedValueOnce({ content: [validApiNodeDetails] });
    const fb = await nodeService.getChildrenPage('folder-1');
    expect(fb.total).toBe(1);

    // root branch
    mockedApi.get.mockResolvedValueOnce([systemRoot]);
    mockedApi.get.mockResolvedValueOnce({ content: [], totalElements: 0 });
    const rootResult = await nodeService.getChildrenPage('root');
    expect(rootResult.total).toBe(0);
  });

  it('addAspect / removeAspect: lenient isNode (only id/name/nodeType/path required)', async () => {
    // valid minimal Node
    const minimalNode = { id: 'n1', name: 'X', nodeType: 'DOCUMENT', path: '/X' };
    mockedApi.post.mockResolvedValueOnce(minimalNode);
    await expect(nodeService.addAspect('n1', 'cm:auditable')).resolves.toMatchObject(minimalNode);
    expect(mockedApi.post).toHaveBeenLastCalledWith('/nodes/n1/aspects/cm:auditable', undefined);

    mockedApi.delete.mockResolvedValueOnce(minimalNode);
    await expect(nodeService.removeAspect('n1', 'cm:auditable')).resolves.toBeDefined();

    // missing required path → throw
    mockedApi.post.mockResolvedValueOnce({ id: 'n1', name: 'X', nodeType: 'DOCUMENT' });
    await expect(nodeService.addAspect('n1', 'cm:auditable')).rejects.toThrow(
      NODE_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('Association methods reuse isNodeRelationEdge: getTarget/Source/SecondaryChildren/Parents + create/addSecondary', async () => {
    mockedApi.get.mockResolvedValueOnce([validNodeRelationEdge]);
    await expect(nodeService.getTargetAssociations('n1')).resolves.toEqual([
      validNodeRelationEdge,
    ]);
    expect(mockedApi.get).toHaveBeenLastCalledWith('/nodes/n1/targets', { params: {} });

    mockedApi.post.mockResolvedValueOnce(validNodeRelationEdge);
    await expect(
      nodeService.createTargetAssociation('n1', 't1', 'cm:references')
    ).resolves.toEqual(validNodeRelationEdge);
    expect(mockedApi.post).toHaveBeenLastCalledWith('/nodes/n1/targets', null, {
      params: { targetId: 't1', assocType: 'cm:references' },
    });

    mockedApi.get.mockResolvedValueOnce([validNodeRelationEdge]);
    await nodeService.getSourceAssociations('n1', 'cm:related');
    expect(mockedApi.get).toHaveBeenLastCalledWith('/nodes/n1/sources', {
      params: { assocType: 'cm:related' },
    });

    mockedApi.post.mockResolvedValueOnce(validNodeRelationEdge);
    await nodeService.addSecondaryChild('parent-1', 'child-1');
    expect(mockedApi.post).toHaveBeenLastCalledWith('/nodes/parent-1/secondary-children', null, {
      params: { childId: 'child-1' },
    });

    mockedApi.get.mockResolvedValueOnce([validNodeRelationEdge]);
    await nodeService.getSecondaryChildren('n1');
    expect(mockedApi.get).toHaveBeenLastCalledWith('/nodes/n1/secondary-children');

    mockedApi.get.mockResolvedValueOnce([validNodeRelationEdge]);
    await nodeService.getSecondaryParents('n1');
    expect(mockedApi.get).toHaveBeenLastCalledWith('/nodes/n1/secondary-parents');

    // malformed edge → throw
    mockedApi.get.mockResolvedValueOnce([{ relationId: 'r', relationType: 't' /* missing source/target */ }]);
    await expect(nodeService.getTargetAssociations('n1')).rejects.toThrow(
      NODE_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('Jackson timestamp array createdDate → mapper output is ISO string (normalization sandwich)', async () => {
    const folderWithArrayTs = {
      ...validFolder,
      createdDate: [2026, 5, 20, 3, 30, 45, 500_000_000],
      lastModifiedDate: [2026, 5, 21, 12, 0, 0],
    };
    mockedApi.get.mockResolvedValueOnce(folderWithArrayTs);
    const node = await nodeService.getFolderByPath('/x');
    expect(node.created).toBe('2026-05-20T03:30:45.500');
    expect(node.modified).toBe('2026-05-21T12:00:00');

    // Same for ApiNodeResponse via moveNode
    const apiNodeWithArrayTs = {
      ...validApiNode,
      createdDate: [2026, 5, 20, 3, 30, 0],
    };
    mockedApi.post.mockResolvedValueOnce(apiNodeWithArrayTs);
    const moved = await nodeService.moveNode('doc-1', 'folder-2');
    expect(moved.created).toBe('2026-05-20T03:30:00');
  });

  it('Lenient: createdBy null and lastModifiedDate null accepted; mapper output preserved', async () => {
    const folderWithNullish = {
      ...validFolder,
      createdBy: null,
      lastModifiedBy: null,
      lastModifiedDate: null,
    };
    mockedApi.get.mockResolvedValueOnce(folderWithNullish);
    const node = await nodeService.getFolderByPath('/x');
    expect(node.id).toBe('folder-1');
    // mapper writes creator from createdBy → null; modifier falls back to creator
    expect(node.creator).toBeNull();
  });

  it('throws on missing required fields (id/name/path) across all three Node-flavored DTOs', async () => {
    // FolderResponse missing path
    mockedApi.get.mockResolvedValueOnce({ ...validFolder, path: undefined });
    await expect(nodeService.getFolderByPath('/x')).rejects.toThrow(
      NODE_UNEXPECTED_RESPONSE_MESSAGE
    );

    // ApiNodeDetailsResponse missing id
    mockedApi.get.mockResolvedValueOnce({ ...validApiNodeDetails, id: undefined });
    await expect(nodeService.getNode('doc-1')).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);

    // ApiNodeResponse missing name
    mockedApi.post.mockResolvedValueOnce({ ...validApiNode, name: undefined });
    await expect(nodeService.moveNode('doc-1', 'folder-2')).rejects.toThrow(
      NODE_UNEXPECTED_RESPONSE_MESSAGE
    );
  });
});
