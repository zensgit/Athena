import api from './api';
import cmisService, {
  CMIS_UNEXPECTED_RESPONSE_MESSAGE,
  CmisQueryResponse,
  CmisRepositoryInfo,
  CmisTypeChildrenResponse,
} from './cmisService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const repositoryInfo: CmisRepositoryInfo = {
  repositoryId: 'athena',
  repositoryName: 'Athena Repository',
  vendorName: 'Athena',
  productName: 'Athena ECM',
  productVersion: '1.0',
  cmisVersionSupported: '1.1',
  rootFolderId: 'root',
  capabilities: ['read', 'type-children'],
};

const typeChildren: CmisTypeChildrenResponse = {
  types: [
    {
      id: 'cmis:document',
      displayName: 'Document',
      baseTypeId: 'cmis:document',
      creatable: true,
      fileable: true,
      queryable: true,
      propertyIds: ['cmis:name', 'cmis:objectId'],
    },
  ],
  totalNumItems: 1,
  hasMoreItems: false,
};

const queryResponse: CmisQueryResponse = {
  repositoryId: 'athena',
  statement: 'SELECT * FROM cmis:document',
  objects: [
    {
      repositoryId: 'athena',
      objectId: 'doc-1',
      name: 'Contract.pdf',
      properties: { 'cmis:name': 'Contract.pdf' },
    },
  ],
  skipCount: 5,
  maxItems: 25,
  totalNumItems: 1,
  hasMoreItems: false,
};

describe('cmisService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('returns guarded repository info', async () => {
    mockedApi.get.mockResolvedValueOnce(repositoryInfo);

    const result = await cmisService.getRepositoryInfo();

    expect(result).toEqual(repositoryInfo);
    expect(mockedApi.get).toHaveBeenCalledWith('/cmis/browser', {
      params: { cmisselector: 'repositoryInfo' },
    });
  });

  test('rejects HTML fallback for repository info', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(cmisService.getRepositoryInfo()).rejects.toThrow(CMIS_UNEXPECTED_RESPONSE_MESSAGE);
  });

  test('rejects malformed repository capabilities', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...repositoryInfo,
      capabilities: 'read',
    });

    await expect(cmisService.getRepositoryInfo()).rejects.toThrow(CMIS_UNEXPECTED_RESPONSE_MESSAGE);
  });

  test('returns guarded type children', async () => {
    mockedApi.get.mockResolvedValueOnce(typeChildren);

    const result = await cmisService.getTypeChildren();

    expect(result).toEqual(typeChildren);
    expect(mockedApi.get).toHaveBeenCalledWith('/cmis/browser', {
      params: { cmisselector: 'typeChildren' },
    });
  });

  test('rejects malformed type definitions', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...typeChildren,
      types: [{ ...typeChildren.types[0], propertyIds: 'cmis:name' }],
    });

    await expect(cmisService.getTypeChildren()).rejects.toThrow(CMIS_UNEXPECTED_RESPONSE_MESSAGE);
  });

  test('returns guarded query responses with pagination params', async () => {
    mockedApi.get.mockResolvedValueOnce(queryResponse);

    const result = await cmisService.query('SELECT * FROM cmis:document', 5, 25);

    expect(result).toEqual(queryResponse);
    expect(mockedApi.get).toHaveBeenCalledWith('/cmis/browser', {
      params: {
        cmisselector: 'query',
        statement: 'SELECT * FROM cmis:document',
        skipCount: 5,
        maxItems: 25,
      },
    });
  });

  test('rejects HTML fallback for query responses', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(cmisService.query('SELECT * FROM cmis:document')).rejects.toThrow(
      CMIS_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  test('rejects malformed query result rows', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...queryResponse,
      objects: ['doc-1'],
    });

    await expect(cmisService.query('SELECT * FROM cmis:document')).rejects.toThrow(
      CMIS_UNEXPECTED_RESPONSE_MESSAGE
    );
  });
});
