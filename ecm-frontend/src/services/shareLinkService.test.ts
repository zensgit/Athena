import api from './api';
import shareLinkService, {
  AccessLogEntry,
  AccessStats,
  SHARE_LINK_UNEXPECTED_RESPONSE_MESSAGE,
  ShareLink,
} from './shareLinkService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    put: jest.fn(),
    delete: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const shareLink: ShareLink = {
  id: 'share-1',
  token: 'token-1',
  nodeId: 'node-1',
  nodeName: 'Plan.pdf',
  createdBy: 'alice',
  createdAt: '2026-05-14T00:00:00',
  expiryDate: null,
  maxAccessCount: null,
  accessCount: 0,
  active: true,
  name: null,
  permissionLevel: 'VIEW',
  lastAccessedAt: null,
  passwordProtected: false,
  hasIpRestrictions: false,
  isValid: true,
};

const accessLogEntry: AccessLogEntry = {
  id: 'log-1',
  accessedAt: '2026-05-14T00:01:00',
  clientIp: null,
  userAgent: null,
  success: false,
  failureReason: 'Password required',
};

const accessStats: AccessStats = {
  totalAccesses: 2,
  successfulAccesses: 1,
  failedAccesses: 1,
};

describe('shareLinkService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns guarded share links for node and my-link lists', async () => {
    mockedApi.get.mockResolvedValueOnce([shareLink]).mockResolvedValueOnce([shareLink]);

    await expect(shareLinkService.getLinksForNode('node-1')).resolves.toEqual([shareLink]);
    await expect(shareLinkService.getMyLinks()).resolves.toEqual([shareLink]);

    expect(mockedApi.get).toHaveBeenNthCalledWith(1, '/share/nodes/node-1');
    expect(mockedApi.get).toHaveBeenNthCalledWith(2, '/share/my');
  });

  it('rejects HTML fallback for share link lists', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(shareLinkService.getLinksForNode('node-1')).rejects.toThrow(
      SHARE_LINK_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('rejects malformed share link list items', async () => {
    mockedApi.get.mockResolvedValueOnce([{ ...shareLink, permissionLevel: 'OWNER' }]);

    await expect(shareLinkService.getLinksForNode('node-1')).rejects.toThrow(
      SHARE_LINK_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('returns guarded create-link readbacks and forwards payload', async () => {
    const request = {
      name: 'External review',
      permissionLevel: 'COMMENT' as const,
      expiryDate: '2026-05-15T00:00:00Z',
      maxAccessCount: 5,
      password: 'secret',
      allowedIps: '127.0.0.1',
    };
    mockedApi.post.mockResolvedValueOnce({ ...shareLink, ...request, passwordProtected: true, hasIpRestrictions: true });

    await expect(shareLinkService.createLink('node-1', request)).resolves.toEqual({
      ...shareLink,
      ...request,
      passwordProtected: true,
      hasIpRestrictions: true,
    });

    expect(mockedApi.post).toHaveBeenCalledWith('/share/nodes/node-1', request);
  });

  it('rejects malformed create-link readbacks', async () => {
    mockedApi.post.mockResolvedValueOnce({ ...shareLink, accessCount: '0' });

    await expect(shareLinkService.createLink('node-1', {})).rejects.toThrow(
      SHARE_LINK_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('returns guarded update-link and reactivate readbacks', async () => {
    const inactiveLink = { ...shareLink, active: false, isValid: false };
    mockedApi.put.mockResolvedValueOnce(inactiveLink);
    mockedApi.post.mockResolvedValueOnce(shareLink);

    await expect(shareLinkService.updateLink('token-1', { active: false })).resolves.toEqual(inactiveLink);
    await expect(shareLinkService.reactivateLink('token-1')).resolves.toEqual(shareLink);

    expect(mockedApi.put).toHaveBeenCalledWith('/share/token-1', { active: false });
    expect(mockedApi.post).toHaveBeenCalledWith('/share/token-1/reactivate');
  });

  it('returns guarded admin share link lists', async () => {
    mockedApi.get.mockResolvedValueOnce([shareLink]);

    await expect(shareLinkService.listAllLinks()).resolves.toEqual([shareLink]);

    expect(mockedApi.get).toHaveBeenCalledWith('/share/admin/all');
  });

  it('returns guarded access logs with nullable backend fields', async () => {
    mockedApi.get.mockResolvedValueOnce([accessLogEntry]);

    await expect(shareLinkService.getAccessLog('token-1')).resolves.toEqual([accessLogEntry]);

    expect(mockedApi.get).toHaveBeenCalledWith('/share/token-1/access-log');
  });

  it('rejects malformed access log entries', async () => {
    mockedApi.get.mockResolvedValueOnce([{ ...accessLogEntry, success: 'false' }]);

    await expect(shareLinkService.getAccessLog('token-1')).rejects.toThrow(
      SHARE_LINK_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('returns guarded access stats', async () => {
    mockedApi.get.mockResolvedValueOnce(accessStats);

    await expect(shareLinkService.getAccessStats('token-1')).resolves.toEqual(accessStats);

    expect(mockedApi.get).toHaveBeenCalledWith('/share/token-1/access-stats');
  });

  it('rejects malformed access stats', async () => {
    mockedApi.get.mockResolvedValueOnce({ ...accessStats, failedAccesses: '1' });

    await expect(shareLinkService.getAccessStats('token-1')).rejects.toThrow(
      SHARE_LINK_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('keeps no-content endpoint wiring unchanged', async () => {
    mockedApi.post.mockResolvedValueOnce(undefined);
    mockedApi.delete.mockResolvedValueOnce(undefined);

    await shareLinkService.deactivateLink('token-1');
    await shareLinkService.deleteLink('token-1');

    expect(mockedApi.post).toHaveBeenCalledWith('/share/token-1/deactivate');
    expect(mockedApi.delete).toHaveBeenCalledWith('/share/token-1');
  });
});

describe('shareLinkService.bulkCreateLinks', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  const request = { nodeIds: ['node-1', 'node-2'], permissionLevel: 'VIEW' as const };

  const wrap = (rows: unknown[]) => ({ bulkShareLinkCreateResults: { rows } });

  it('posts to /bulk/share-links and parses a mixed CREATED/FAILED result', async () => {
    const payload = wrap([
      { nodeId: 'node-1', status: 'CREATED', shareLink, errorCategory: null, message: null },
      { nodeId: 'node-2', status: 'FAILED', shareLink: null, errorCategory: 'NO_PERMISSION', message: 'No permission to share the target node.' },
    ]);
    mockedApi.post.mockResolvedValueOnce(payload);

    await expect(shareLinkService.bulkCreateLinks(request)).resolves.toEqual(payload);
    expect(mockedApi.post).toHaveBeenCalledWith('/bulk/share-links', request);
  });

  it('rejects an HTML/SPA fallback with the sentinel', async () => {
    mockedApi.post.mockResolvedValueOnce('<!doctype html><html></html>');
    await expect(shareLinkService.bulkCreateLinks(request)).rejects.toThrow(SHARE_LINK_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('rejects a missing wrapper / non-array rows', async () => {
    mockedApi.post.mockResolvedValueOnce({ rows: [] });
    await expect(shareLinkService.bulkCreateLinks(request)).rejects.toThrow(SHARE_LINK_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('rejects a CREATED row without a shareLink', async () => {
    mockedApi.post.mockResolvedValueOnce(wrap([
      { nodeId: 'node-1', status: 'CREATED', shareLink: null, errorCategory: null, message: null },
    ]));
    await expect(shareLinkService.bulkCreateLinks(request)).rejects.toThrow(SHARE_LINK_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('rejects a FAILED row without an errorCategory', async () => {
    mockedApi.post.mockResolvedValueOnce(wrap([
      { nodeId: 'node-1', status: 'FAILED', shareLink: null, errorCategory: null, message: 'boom' },
    ]));
    await expect(shareLinkService.bulkCreateLinks(request)).rejects.toThrow(SHARE_LINK_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('rejects an unknown status', async () => {
    mockedApi.post.mockResolvedValueOnce(wrap([
      { nodeId: 'node-1', status: 'TELEPORTED', shareLink: null, errorCategory: null, message: null },
    ]));
    await expect(shareLinkService.bulkCreateLinks(request)).rejects.toThrow(SHARE_LINK_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('rejects an unknown errorCategory on a FAILED row', async () => {
    mockedApi.post.mockResolvedValueOnce(wrap([
      { nodeId: 'node-1', status: 'FAILED', shareLink: null, errorCategory: 'WAT', message: 'boom' },
    ]));
    await expect(shareLinkService.bulkCreateLinks(request)).rejects.toThrow(SHARE_LINK_UNEXPECTED_RESPONSE_MESSAGE);
  });
});
