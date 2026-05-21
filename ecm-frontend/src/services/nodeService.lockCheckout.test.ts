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

const validLockInfo = {
  status: 'LOCK_OWNER',
  lockedBy: 'admin',
  lockedDate: '2026-05-21T10:00:00Z',
  lockLifetime: 'EPHEMERAL',
  lockExpiresAt: '2026-05-21T10:30:00Z',
  lockType: 'WRITE_LOCK',
  additionalInfo: 'review',
  lockDeep: false,
  remainingSeconds: 1800,
  lockAgeSeconds: 60,
  canUnlock: true,
};

const validCheckoutInfo = {
  status: 'CHECKED_OUT_BY_YOU',
  checkoutUser: 'admin',
  checkoutDate: '2026-05-21T09:00:00Z',
  checkoutAgeSeconds: 3600,
  canCheckout: false,
  canCheckIn: true,
  canCancelCheckout: true,
  canKeepCheckedOut: true,
  requiresNewVersionFile: false,
  blockingReason: null,
};

const validApiNodeDetails = {
  id: 'doc-1',
  name: 'Contract.pdf',
  path: '/Sites/Legal/Contract.pdf',
  nodeType: 'DOCUMENT',
  parentId: 'folder-1',
  size: 1024,
  contentType: 'application/pdf',
  currentVersionLabel: '1.0',
  createdBy: 'admin',
  createdDate: '2026-05-21T08:00:00Z',
  lastModifiedBy: 'admin',
  lastModifiedDate: '2026-05-21T08:30:00Z',
  properties: { 'cm:description': 'contract' },
  aspects: ['cm:versionable'],
};

const validApiVersion = {
  id: 'version-1',
  documentId: 'doc-1',
  versionLabel: '1.0',
  comment: 'initial',
  createdDate: '2026-05-21T08:00:00Z',
  creator: 'admin',
  size: 1024,
  major: true,
  mimeType: 'application/pdf',
  contentHash: 'abc',
  contentId: 'content-1',
  status: 'CURRENT',
  checkoutBaseline: true,
  checkoutCurrent: true,
};

const validLineage = {
  documentId: 'doc-1',
  checkout: validCheckoutInfo,
  baselineVersion: validApiVersion,
  currentVersion: validApiVersion,
};

beforeEach(() => {
  jest.clearAllMocks();
});

describe('nodeService lock/checkout response shape guards', () => {
  it('getLockInfo and lockNodeTyped guard LockInfo and preserve endpoints/params', async () => {
    mockedApi.get.mockResolvedValueOnce(validLockInfo);
    await expect(nodeService.getLockInfo('doc-1')).resolves.toEqual(validLockInfo);
    expect(mockedApi.get).toHaveBeenLastCalledWith('/nodes/doc-1/lock-info');

    mockedApi.post.mockResolvedValueOnce({
      ...validLockInfo,
      lockedDate: [2026, 5, 21, 10, 1, 2, 300_000_000],
      lockExpiresAt: [2026, 5, 21, 10, 31, 2],
    });
    await expect(
      nodeService.lockNodeTyped('doc-1', {
        lockType: 'READ_ONLY_LOCK',
        lifetime: 'EPHEMERAL',
        durationSeconds: 1800,
        deep: true,
        additionalInfo: '  reason  ',
      })
    ).resolves.toMatchObject({
      lockedDate: '2026-05-21T10:01:02.300',
      lockExpiresAt: '2026-05-21T10:31:02',
    });
    expect(mockedApi.post).toHaveBeenLastCalledWith('/nodes/doc-1/lock-typed', null, {
      params: {
        lockType: 'READ_ONLY_LOCK',
        lifetime: 'EPHEMERAL',
        durationSeconds: 1800,
        deep: true,
        additionalInfo: 'reason',
      },
    });
  });

  it('lockNodeTyped preserves default params and omitted empty additionalInfo', async () => {
    mockedApi.post.mockResolvedValueOnce(validLockInfo);
    await nodeService.lockNodeTyped('doc-1', {});
    expect(mockedApi.post).toHaveBeenLastCalledWith('/nodes/doc-1/lock-typed', null, {
      params: {
        lockType: 'WRITE_LOCK',
        lifetime: 'PERSISTENT',
        durationSeconds: undefined,
        deep: false,
        additionalInfo: undefined,
      },
    });
  });

  it('getCheckoutInfo guards CheckoutInfo and normalizes Jackson timestamp arrays', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...validCheckoutInfo,
      checkoutDate: [2026, 5, 21, 9, 30, 15],
    });
    await expect(nodeService.getCheckoutInfo('doc-1')).resolves.toMatchObject({
      checkoutDate: '2026-05-21T09:30:15',
    });
    expect(mockedApi.get).toHaveBeenLastCalledWith('/documents/doc-1/checkout-info');
  });

  it('getCheckoutLineage guards nested checkout and versions while preserving mapping', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...validLineage,
      baselineVersion: {
        ...validApiVersion,
        createdDate: [2026, 5, 21, 8, 0, 1],
      },
    });

    const lineage = await nodeService.getCheckoutLineage('doc-1');

    expect(mockedApi.get).toHaveBeenLastCalledWith('/documents/doc-1/checkout-lineage');
    expect(lineage.documentId).toBe('doc-1');
    expect(lineage.checkout.checkoutDate).toBe('2026-05-21T09:00:00Z');
    expect(lineage.baselineVersion).toMatchObject({
      id: 'version-1',
      created: '2026-05-21T08:00:01',
      isMajor: true,
      checkoutBaseline: true,
      checkoutCurrent: true,
    });
  });

  it('checkout/cancel/checkin guard NodeDto responses and preserve mapping', async () => {
    mockedApi.post.mockResolvedValueOnce(validApiNodeDetails);
    await expect(nodeService.checkoutDocument('doc-1')).resolves.toMatchObject({
      id: 'doc-1',
      nodeType: 'DOCUMENT',
      created: '2026-05-21T08:00:00Z',
    });
    expect(mockedApi.post).toHaveBeenLastCalledWith('/documents/doc-1/checkout');

    mockedApi.post.mockResolvedValueOnce(validApiNodeDetails);
    await expect(nodeService.cancelCheckoutDocument('doc-1')).resolves.toMatchObject({
      id: 'doc-1',
    });
    expect(mockedApi.post).toHaveBeenLastCalledWith('/documents/doc-1/cancel-checkout');
  });

  it('checkinDocument preserves multipart FormData, header, and boolean-string payload', async () => {
    const file = new File(['pdf'], 'contract.pdf', { type: 'application/pdf' });
    mockedApi.post.mockResolvedValueOnce(validApiNodeDetails);

    await nodeService.checkinDocument('doc-1', {
      file,
      comment: '  ready  ',
      majorVersion: true,
      keepCheckedOut: true,
    });

    expect(mockedApi.post).toHaveBeenCalledTimes(1);
    const [url, formData, options] = mockedApi.post.mock.calls[0];
    expect(url).toBe('/documents/doc-1/checkin');
    expect(formData).toBeInstanceOf(FormData);
    expect((formData as FormData).get('file')).toBe(file);
    expect((formData as FormData).get('comment')).toBe('ready');
    expect((formData as FormData).get('majorVersion')).toBe('true');
    expect((formData as FormData).get('keepCheckedOut')).toBe('true');
    expect(options).toEqual({ headers: { 'Content-Type': 'multipart/form-data' } });
  });

  it('checkinDocument omits blank comment and sends false boolean strings by default', async () => {
    mockedApi.post.mockResolvedValueOnce(validApiNodeDetails);

    await nodeService.checkinDocument('doc-1', { comment: '   ' });

    const [, formData] = mockedApi.post.mock.calls[0];
    expect((formData as FormData).get('comment')).toBeNull();
    expect((formData as FormData).get('majorVersion')).toBe('false');
    expect((formData as FormData).get('keepCheckedOut')).toBe('false');
  });

  it('throws the shared node sentinel for malformed lock and checkout info responses', async () => {
    mockedApi.get.mockResolvedValueOnce(HTML_FALLBACK);
    await expect(nodeService.getLockInfo('doc-1')).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);

    mockedApi.post.mockResolvedValueOnce({ status: 'LOCK_OWNER' }); // missing canUnlock
    await expect(nodeService.lockNodeTyped('doc-1', {})).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);

    mockedApi.get.mockResolvedValueOnce(null);
    await expect(nodeService.getCheckoutInfo('doc-1')).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);

    mockedApi.get.mockResolvedValueOnce({ ...validCheckoutInfo, canCheckIn: 'yes' });
    await expect(nodeService.getCheckoutInfo('doc-1')).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('throws for malformed checkout lineage, nested checkout, and nested versions', async () => {
    mockedApi.get.mockResolvedValueOnce({ ...validLineage, documentId: 123 });
    await expect(nodeService.getCheckoutLineage('doc-1')).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);

    mockedApi.get.mockResolvedValueOnce({
      ...validLineage,
      checkout: { status: 'CHECKED_OUT_BY_YOU' },
    });
    await expect(nodeService.getCheckoutLineage('doc-1')).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);

    mockedApi.get.mockResolvedValueOnce({
      ...validLineage,
      currentVersion: { ...validApiVersion, size: null },
    });
    await expect(nodeService.getCheckoutLineage('doc-1')).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('throws for malformed checkout mutation NodeDto responses', async () => {
    mockedApi.post.mockResolvedValueOnce({ ...validApiNodeDetails, path: undefined });
    await expect(nodeService.checkoutDocument('doc-1')).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);

    mockedApi.post.mockResolvedValueOnce(HTML_FALLBACK);
    await expect(nodeService.cancelCheckoutDocument('doc-1')).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);

    mockedApi.post.mockResolvedValueOnce(null);
    await expect(nodeService.checkinDocument('doc-1')).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);
  });
});
