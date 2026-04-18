import api from './api';
import nodeService from './nodeService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

describe('nodeService RM record projection', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('preserves rm properties and aspects when loading folder children', async () => {
    mockedApi.get.mockResolvedValueOnce({
      content: [
        {
          id: 'doc-1',
          name: 'Contract.pdf',
          path: '/Sites/Legal/Contract.pdf',
          nodeType: 'DOCUMENT',
          parentId: 'folder-1',
          size: 1024,
          contentType: 'application/pdf',
          currentVersionLabel: '1.3',
          properties: {
            'rm:declaredBy': 'records-admin',
            'rm:declaredAt': '2026-04-17T10:00:00',
            'rm:recordCategoryPath': '/Records Management/Contracts',
          },
          aspects: ['rm:record'],
          createdBy: 'records-admin',
          createdDate: '2026-04-17T10:00:00Z',
          lastModifiedBy: 'records-admin',
          lastModifiedDate: '2026-04-17T10:00:00Z',
        },
      ],
    } as any);

    const children = await nodeService.getChildren('folder-1');

    expect(children).toHaveLength(1);
    expect(children[0].record).toBe(true);
    expect(children[0].currentVersionLabel).toBe('1.3');
    expect(children[0].aspects).toContain('rm:record');
    expect(children[0].recordCategoryPath).toBe('/Records Management/Contracts');
  });

  it('maps RM record projection from full-text search results', async () => {
    mockedApi.get.mockResolvedValueOnce({
      content: [
        {
          id: 'doc-2',
          name: 'Invoice.pdf',
          path: '/Sites/Finance/Invoice.pdf',
          nodeType: 'DOCUMENT',
          parentId: 'folder-2',
          mimeType: 'application/pdf',
          fileSize: 2048,
          createdBy: 'alice',
          createdDate: '2026-04-17T10:00:00Z',
          lastModifiedBy: 'alice',
          lastModifiedDate: '2026-04-17T10:00:00Z',
          currentVersionLabel: '2.1',
          record: true,
          declaredBy: 'records-admin',
          declaredAt: '2026-04-12T09:00:00',
          declaredVersionLabel: '2.0',
          declarationComment: 'Quarter close',
          recordCategoryId: 'cat-2',
          recordCategoryName: 'Finance',
          recordCategoryPath: '/Records Management/Finance',
        },
      ],
      totalElements: 1,
    } as any);

    const result = await nodeService.searchNodes({ name: 'Invoice' });

    expect(result.nodes).toHaveLength(1);
    expect(result.nodes[0].record).toBe(true);
    expect(result.nodes[0].declaredBy).toBe('records-admin');
    expect(result.nodes[0].declaredVersionLabel).toBe('2.0');
    expect(result.nodes[0].recordCategoryName).toBe('Finance');
    expect(result.nodes[0].aspects).toContain('rm:record');
  });

  it('sends recordOnly through the advanced search filter payload', async () => {
    mockedApi.post.mockResolvedValueOnce({
      results: {
        content: [],
        totalElements: 0,
      },
    } as any);

    await nodeService.searchNodes({
      name: 'Invoice',
      recordOnly: true,
    });

    expect(mockedApi.post).toHaveBeenCalledWith('/search/query', expect.objectContaining({
      query: 'Invoice',
      filters: expect.objectContaining({
        recordOnly: true,
      }),
    }));
  });

  it('sends recordCategoryPaths through the advanced search filter payload', async () => {
    mockedApi.post.mockResolvedValueOnce({
      results: {
        content: [],
        totalElements: 0,
      },
    } as any);

    await nodeService.searchNodes({
      name: 'Invoice',
      recordCategoryPaths: ['/Records Management/Finance'],
    });

    expect(mockedApi.post).toHaveBeenCalledWith('/search/query', expect.objectContaining({
      query: 'Invoice',
      filters: expect.objectContaining({
        recordCategoryPaths: ['/Records Management/Finance'],
      }),
    }));
  });
});
