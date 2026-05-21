import api from './api';
import nodeService, { NODE_UNEXPECTED_RESPONSE_MESSAGE } from './nodeService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    downloadFile: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const HTML_FALLBACK = '<!doctype html><html><body>app shell</body></html>';

const validApiVersion = {
  id: 'version-1',
  documentId: 'doc-1',
  versionLabel: '1.0',
  comment: 'initial upload',
  createdDate: '2026-05-21T08:00:00Z',
  creator: 'admin',
  size: 2048,
  major: true,
  mimeType: 'application/pdf',
  contentHash: 'hash-1',
  contentId: 'content-1',
  status: 'CURRENT',
  checkoutBaseline: false,
  checkoutCurrent: true,
};

const mappedVersion = {
  id: 'version-1',
  documentId: 'doc-1',
  versionLabel: '1.0',
  comment: 'initial upload',
  created: '2026-05-21T08:00:00Z',
  creator: 'admin',
  size: 2048,
  isMajor: true,
  mimeType: 'application/pdf',
  contentHash: 'hash-1',
  contentId: 'content-1',
  status: 'CURRENT',
  checkoutBaseline: false,
  checkoutCurrent: true,
};

const validNodeDetails = {
  id: 'doc-1',
  name: 'Contract.pdf',
  path: '/Sites/Legal/Contract.pdf',
  nodeType: 'DOCUMENT',
  parentId: 'folder-1',
  size: 2048,
  contentType: 'application/pdf',
  createdBy: 'admin',
  createdDate: '2026-05-21T07:00:00Z',
  lastModifiedBy: 'admin',
  lastModifiedDate: '2026-05-21T08:00:00Z',
};

const page = (content = [validApiVersion]) => ({
  content,
  totalElements: content.length,
  totalPages: 1,
  number: 0,
  size: 20,
});

beforeEach(() => {
  jest.clearAllMocks();
});

describe('nodeService version/history response shape guards', () => {
  it('guards getVersionHistory and normalizes Jackson timestamp arrays', async () => {
    mockedApi.get.mockResolvedValueOnce([
      {
        ...validApiVersion,
        documentId: null,
        creator: null,
        createdDate: [2026, 5, 21, 8, 1, 2, 300_000_000],
      },
    ]);

    await expect(nodeService.getVersionHistory('doc-1')).resolves.toEqual([
      {
        ...mappedVersion,
        documentId: 'doc-1',
        creator: '',
        created: '2026-05-21T08:01:02.300',
      },
    ]);
    expect(mockedApi.get).toHaveBeenLastCalledWith('/documents/doc-1/versions');
  });

  it('guards getVersionHistoryPage and preserves paging params/output', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...page(),
      totalElements: 42,
      totalPages: 3,
      number: 2,
      size: 10,
    });

    await expect(nodeService.getVersionHistoryPage('doc-1', 2, 10, true)).resolves.toEqual({
      versions: [mappedVersion],
      page: 2,
      size: 10,
      totalElements: 42,
      totalPages: 3,
    });
    expect(mockedApi.get).toHaveBeenLastCalledWith('/documents/doc-1/versions/paged', {
      params: { page: 2, size: 10, majorOnly: true },
    });
  });

  it('createVersion preserves multipart payload and returns the guarded newest history item', async () => {
    const file = new File(['pdf'], 'contract.pdf', { type: 'application/pdf' });
    mockedApi.post.mockResolvedValueOnce(undefined);
    mockedApi.get.mockResolvedValueOnce([validApiVersion]);

    await expect(
      nodeService.createVersion('doc-1', file, 'ready', true, true)
    ).resolves.toEqual(mappedVersion);

    expect(mockedApi.post).toHaveBeenCalledTimes(1);
    const [url, formData, options] = mockedApi.post.mock.calls[0];
    expect(url).toBe('/documents/doc-1/checkin');
    expect(formData).toBeInstanceOf(FormData);
    expect((formData as FormData).get('file')).toBe(file);
    expect((formData as FormData).get('comment')).toBe('ready');
    expect((formData as FormData).get('majorVersion')).toBe('true');
    expect((formData as FormData).get('keepCheckedOut')).toBe('true');
    expect(options).toEqual({ headers: { 'Content-Type': 'multipart/form-data' } });
    expect(mockedApi.get).toHaveBeenLastCalledWith('/documents/doc-1/versions');
  });

  it('downloadVersion keeps downloadFile out of scope while using guarded node/history lookups', async () => {
    mockedApi.get
      .mockResolvedValueOnce(validNodeDetails)
      .mockResolvedValueOnce([validApiVersion]);
    mockedApi.downloadFile.mockResolvedValueOnce(undefined);

    await nodeService.downloadVersion('doc-1', 'version-1');

    expect(mockedApi.get).toHaveBeenNthCalledWith(1, '/nodes/doc-1');
    expect(mockedApi.get).toHaveBeenNthCalledWith(2, '/documents/doc-1/versions');
    expect(mockedApi.downloadFile).toHaveBeenLastCalledWith(
      '/documents/doc-1/versions/version-1/download',
      'Contract.pdf_v1.0'
    );
  });

  it('guards getVersionTextDiff while preserving no-diff fallback and params', async () => {
    mockedApi.get.mockResolvedValueOnce({
      textDiff: {
        available: true,
        truncated: false,
        reason: null,
        diff: '- old\n+ new',
      },
    });

    await expect(
      nodeService.getVersionTextDiff('doc-1', 'v1', 'v2', 1000, 100)
    ).resolves.toEqual({
      available: true,
      truncated: false,
      reason: null,
      diff: '- old\n+ new',
    });
    expect(mockedApi.get).toHaveBeenLastCalledWith('/documents/doc-1/versions/compare', {
      params: {
        fromVersionId: 'v1',
        toVersionId: 'v2',
        includeTextDiff: true,
        maxBytes: 1000,
        maxLines: 100,
      },
    });

    mockedApi.get.mockResolvedValueOnce({});
    await expect(nodeService.getVersionTextDiff('doc-1', 'v1', 'v2')).resolves.toEqual({
      available: false,
      truncated: false,
      reason: 'No diff available',
      diff: null,
    });
  });

  it('guards revertToVersion and preserves node mapping', async () => {
    mockedApi.post.mockResolvedValueOnce(validNodeDetails);

    await expect(nodeService.revertToVersion('doc-1', 'version-1')).resolves.toMatchObject({
      id: 'doc-1',
      name: 'Contract.pdf',
      nodeType: 'DOCUMENT',
      created: '2026-05-21T07:00:00Z',
    });
    expect(mockedApi.post).toHaveBeenLastCalledWith('/documents/doc-1/versions/version-1/revert');
  });

  it('throws the shared node sentinel for malformed version history responses', async () => {
    mockedApi.get.mockResolvedValueOnce(HTML_FALLBACK);
    await expect(nodeService.getVersionHistory('doc-1')).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);

    mockedApi.get.mockResolvedValueOnce([{ ...validApiVersion, size: null }]);
    await expect(nodeService.getVersionHistory('doc-1')).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);

    mockedApi.get.mockResolvedValueOnce({ ...page(), content: [{ ...validApiVersion, major: 'true' }] });
    await expect(nodeService.getVersionHistoryPage('doc-1')).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('throws the shared node sentinel for malformed diff and revert responses', async () => {
    mockedApi.get.mockResolvedValueOnce({ textDiff: { available: 'yes', truncated: false } });
    await expect(nodeService.getVersionTextDiff('doc-1', 'v1', 'v2')).rejects.toThrow(
      NODE_UNEXPECTED_RESPONSE_MESSAGE
    );

    mockedApi.get.mockResolvedValueOnce(null);
    await expect(nodeService.getVersionTextDiff('doc-1', 'v1', 'v2')).rejects.toThrow(
      NODE_UNEXPECTED_RESPONSE_MESSAGE
    );

    mockedApi.post.mockResolvedValueOnce({ ...validNodeDetails, name: undefined });
    await expect(nodeService.revertToVersion('doc-1', 'version-1')).rejects.toThrow(
      NODE_UNEXPECTED_RESPONSE_MESSAGE
    );
  });
});
