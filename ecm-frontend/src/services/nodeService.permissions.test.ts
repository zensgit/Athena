import api from './api';
import nodeService, { NODE_UNEXPECTED_RESPONSE_MESSAGE } from './nodeService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    delete: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const HTML_FALLBACK = '<!doctype html><html><body>app shell</body></html>';

const userPermission = {
  authority: 'alice',
  authorityType: 'USER',
  permission: 'READ',
  allowed: true,
  inherited: false,
  expiryDate: [2026, 5, 21, 10, 15, 30],
  notes: null,
};

const groupPermission = {
  authority: 'legal',
  authorityType: 'GROUP',
  permission: 'WRITE',
  allowed: false,
  inherited: true,
  expiryDate: null,
  notes: 'deny write',
};

const permissionDecision = {
  nodeId: 'doc-1',
  username: 'alice',
  permission: 'READ',
  allowed: true,
  reason: 'DIRECT_ALLOW',
  dynamicAuthority: null,
  allowedAuthorities: ['alice', 'GROUP_legal'],
  deniedAuthorities: [],
};

const permissionSetMetadata = {
  name: 'CONSUMER',
  label: 'Consumer',
  description: 'Read-only access',
  order: 10,
  permissions: ['READ'],
};

beforeEach(() => {
  jest.clearAllMocks();
});

describe('nodeService permission response shape guards', () => {
  it('guards getPermissions, normalizes expiryDate, and preserves grouping semantics', async () => {
    mockedApi.get.mockResolvedValueOnce([userPermission, groupPermission]);

    await expect(nodeService.getPermissions('doc-1')).resolves.toEqual({
      alice: [
        {
          ...userPermission,
          expiryDate: '2026-05-21T10:15:30',
        },
      ],
      GROUP_legal: [
        {
          ...groupPermission,
          expiryDate: undefined,
        },
      ],
    });
    expect(mockedApi.get).toHaveBeenLastCalledWith('/security/nodes/doc-1/permissions');
  });

  it('guards getPermissionDiagnostics and preserves username params', async () => {
    mockedApi.get.mockResolvedValueOnce(permissionDecision);

    await expect(
      nodeService.getPermissionDiagnostics('doc-1', 'READ', 'alice')
    ).resolves.toEqual(permissionDecision);
    expect(mockedApi.get).toHaveBeenLastCalledWith('/security/nodes/doc-1/permission-diagnostics', {
      params: { permissionType: 'READ', username: 'alice' },
    });

    mockedApi.get.mockResolvedValueOnce({ ...permissionDecision, username: null });
    await nodeService.getPermissionDiagnostics('doc-1', 'WRITE', '');
    expect(mockedApi.get).toHaveBeenLastCalledWith('/security/nodes/doc-1/permission-diagnostics', {
      params: { permissionType: 'WRITE', username: undefined },
    });
  });

  it('guards permission-set records and metadata arrays', async () => {
    mockedApi.get.mockResolvedValueOnce({
      CONSUMER: ['READ'],
      CONTRIBUTOR: ['READ', 'WRITE', 'CREATE_CHILDREN'],
    });
    await expect(nodeService.getPermissionSets()).resolves.toEqual({
      CONSUMER: ['READ'],
      CONTRIBUTOR: ['READ', 'WRITE', 'CREATE_CHILDREN'],
    });
    expect(mockedApi.get).toHaveBeenLastCalledWith('/security/permission-sets');

    mockedApi.get.mockResolvedValueOnce([
      permissionSetMetadata,
      { ...permissionSetMetadata, name: 'ADMIN', description: null, order: null, permissions: ['READ', 'WRITE'] },
    ]);
    await expect(nodeService.getPermissionSetMetadata()).resolves.toEqual([
      permissionSetMetadata,
      { ...permissionSetMetadata, name: 'ADMIN', description: null, order: null, permissions: ['READ', 'WRITE'] },
    ]);
    expect(mockedApi.get).toHaveBeenLastCalledWith('/security/permission-sets/metadata');
  });

  it('preserves void permission write endpoint and params shapes', async () => {
    mockedApi.post.mockResolvedValueOnce(undefined);
    await nodeService.setPermission('doc-1', 'alice', 'USER', 'READ', true);
    expect(mockedApi.post).toHaveBeenLastCalledWith('/security/nodes/doc-1/permissions', null, {
      params: { authority: 'alice', authorityType: 'USER', permissionType: 'READ', allowed: true },
    });

    mockedApi.post.mockResolvedValueOnce(undefined);
    await nodeService.applyPermissionSet('doc-1', 'legal', 'GROUP', 'CONSUMER', true);
    expect(mockedApi.post).toHaveBeenLastCalledWith('/security/nodes/doc-1/permission-sets', null, {
      params: { authority: 'legal', authorityType: 'GROUP', permissionSet: 'CONSUMER', replace: true },
    });

    mockedApi.post.mockResolvedValueOnce(undefined);
    await nodeService.setInheritPermissions('doc-1', false);
    expect(mockedApi.post).toHaveBeenLastCalledWith('/security/nodes/doc-1/inherit-permissions', null, {
      params: { inherit: false },
    });

    mockedApi.delete.mockResolvedValueOnce(undefined);
    await nodeService.removePermission('doc-1', 'alice', 'READ');
    expect(mockedApi.delete).toHaveBeenLastCalledWith('/security/nodes/doc-1/permissions', {
      params: { authority: 'alice', permissionType: 'READ' },
    });
  });

  it('throws the shared node sentinel for malformed permission reads', async () => {
    mockedApi.get.mockResolvedValueOnce(HTML_FALLBACK);
    await expect(nodeService.getPermissions('doc-1')).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);

    mockedApi.get.mockResolvedValueOnce([{ ...userPermission, allowed: 'true' }]);
    await expect(nodeService.getPermissions('doc-1')).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);

    mockedApi.get.mockResolvedValueOnce({ ...permissionDecision, allowedAuthorities: [42] });
    await expect(nodeService.getPermissionDiagnostics('doc-1', 'READ')).rejects.toThrow(
      NODE_UNEXPECTED_RESPONSE_MESSAGE
    );

    mockedApi.get.mockResolvedValueOnce({ CONSUMER: 'READ' });
    await expect(nodeService.getPermissionSets()).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);

    mockedApi.get.mockResolvedValueOnce([{ ...permissionSetMetadata, permissions: [42] }]);
    await expect(nodeService.getPermissionSetMetadata()).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);
  });
});
