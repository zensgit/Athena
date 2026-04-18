import nodeService from './nodeService';
import api from './api';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

describe('nodeService createFolder smart authoring', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('sends description and smart-folder payload to the folder API', async () => {
    mockedApi.post.mockResolvedValue({
      id: 'folder-1',
      name: 'Smart Invoices',
      description: 'Query-backed folder',
      path: '/Sites/Finance/Smart Invoices',
      parentId: 'parent-1',
      folderType: 'GENERAL',
      inheritPermissions: true,
      smart: true,
      queryCriteria: {
        query: 'invoice',
        pathPrefix: '/Sites/Finance',
      },
      createdBy: 'admin',
      createdDate: '2026-04-14T10:00:00Z',
      lastModifiedBy: 'admin',
      lastModifiedDate: '2026-04-14T10:00:00Z',
    });

    const folder = await nodeService.createFolder('parent-1', {
      name: 'Smart Invoices',
      description: 'Query-backed folder',
      isSmart: true,
      queryCriteria: {
        query: 'invoice',
        pathPrefix: '/Sites/Finance',
      },
    });

    expect(mockedApi.post).toHaveBeenCalledWith('/folders', {
      name: 'Smart Invoices',
      description: 'Query-backed folder',
      parentId: 'parent-1',
      folderType: 'GENERAL',
      inheritPermissions: true,
      isSmart: true,
      queryCriteria: {
        query: 'invoice',
        pathPrefix: '/Sites/Finance',
      },
    });
    expect(folder.smart).toBe(true);
    expect(folder.queryCriteria).toEqual({
      query: 'invoice',
      pathPrefix: '/Sites/Finance',
    });
    expect(folder.description).toBe('Query-backed folder');
  });
});
