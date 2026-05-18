import peopleService, { PEOPLE_UNEXPECTED_RESPONSE_MESSAGE } from './peopleService';
import api from './api';

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

const user = {
  id: 'user-1',
  username: 'alice@example.com',
  email: 'alice@example.com',
  roles: ['USER'],
  enabled: true,
  locked: false,
};

const page = <T>(content: T[]) => ({
  content,
  totalElements: content.length,
  totalPages: 1,
  number: 0,
  size: 20,
});

const favorite = {
  id: 'fav-1',
  nodeId: 'node/1',
  nodeName: 'Plan.pdf',
  nodeType: 'DOCUMENT',
  createdAt: '2026-05-18T00:00:00Z',
} as const;

const preferences = {
  username: 'alice@example.com',
  email: 'alice@example.com',
  enabled: true,
  locked: false,
  preferences: { 'ui.theme': 'dark' },
};

const activity = {
  id: 'activity-1',
  type: 'VIEWED',
  title: 'Viewed document',
  summary: 'Alice viewed Plan.pdf',
  metadata: { nodeId: 'node-1' },
};

const site = {
  siteId: 'records',
  title: 'Records',
  role: 'Manager',
  visibility: 'PUBLIC',
  memberCount: 2,
};

const favoriteSite = {
  siteId: 'records',
  title: 'Records',
  nodeId: 'site-node',
};

const membershipRequest = {
  username: 'alice@example.com',
  siteId: 'records',
  siteTitle: 'Records',
  role: 'Contributor',
  status: 'PENDING',
  metadata: { source: 'directory' },
};

const expectUnexpectedResponse = async (promise: Promise<unknown>) => {
  await expect(promise).rejects.toThrow(PEOPLE_UNEXPECTED_RESPONSE_MESSAGE);
};

describe('peopleService response shape guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('guards people search pages and preserves query params', async () => {
    mockedApi.get.mockResolvedValueOnce(page([user]));

    await expect(peopleService.search('ali', 1, 5)).resolves.toEqual(page([user]));

    expect(mockedApi.get).toHaveBeenCalledWith('/people', {
      params: { query: 'ali', page: 1, size: 5 },
    });
  });

  it('guards user and group responses while preserving username encoding', async () => {
    mockedApi.get
      .mockResolvedValueOnce(user)
      .mockResolvedValueOnce([{ name: 'GROUP_records', users: [user] }]);

    await expect(peopleService.get('alice@example.com')).resolves.toEqual(user);
    await expect(peopleService.getGroups('alice@example.com')).resolves.toEqual([
      { name: 'GROUP_records', users: [user] },
    ]);

    expect(mockedApi.get).toHaveBeenNthCalledWith(1, '/people/alice%40example.com');
    expect(mockedApi.get).toHaveBeenNthCalledWith(2, '/people/alice%40example.com/groups');
  });

  it('guards favorite CRUD read responses and preserves node encoding', async () => {
    mockedApi.get
      .mockResolvedValueOnce(page([favorite]))
      .mockResolvedValueOnce(favorite);
    mockedApi.post.mockResolvedValueOnce(favorite);
    mockedApi.delete.mockResolvedValueOnce(undefined);

    await expect(peopleService.getFavorites('alice@example.com', 2, 10)).resolves.toEqual(page([favorite]));
    await expect(peopleService.getFavorite('alice@example.com', 'node/1')).resolves.toEqual(favorite);
    await expect(peopleService.createFavorite('alice@example.com', { nodeId: 'node/1' })).resolves.toEqual(favorite);
    await expect(peopleService.deleteFavorite('alice@example.com', 'node/1')).resolves.toBeUndefined();

    expect(mockedApi.get).toHaveBeenNthCalledWith(1, '/people/alice%40example.com/favorites', {
      params: { page: 2, size: 10 },
    });
    expect(mockedApi.get).toHaveBeenNthCalledWith(2, '/people/alice%40example.com/favorites/node%2F1');
    expect(mockedApi.post).toHaveBeenCalledWith('/people/alice%40example.com/favorites', { nodeId: 'node/1' });
    expect(mockedApi.delete).toHaveBeenCalledWith('/people/alice%40example.com/favorites/node%2F1');
  });

  it('guards preferences responses and preserves namespace endpoints', async () => {
    mockedApi.get
      .mockResolvedValueOnce(preferences)
      .mockResolvedValueOnce(['app', 'ui'])
      .mockResolvedValueOnce({ 'ui.theme': 'dark', compactMode: true })
      .mockResolvedValueOnce({ key: 'ui.theme', value: 'dark' });
    mockedApi.post.mockResolvedValueOnce(preferences);
    mockedApi.put.mockResolvedValueOnce(preferences);
    mockedApi.delete
      .mockResolvedValueOnce(preferences)
      .mockResolvedValueOnce(preferences);

    await expect(peopleService.getPreferences('alice', 'ui.')).resolves.toEqual(preferences);
    await expect(peopleService.getPreferenceNamespaces('alice')).resolves.toEqual(['app', 'ui']);
    await expect(peopleService.exportPreferences('alice')).resolves.toEqual({ 'ui.theme': 'dark', compactMode: true });
    await expect(peopleService.importPreferences('alice', { 'ui.theme': 'dark' })).resolves.toEqual(preferences);
    await expect(peopleService.getPreference('alice@example.com', 'org.athena.rm/reportPreset')).resolves.toEqual({
      key: 'ui.theme',
      value: 'dark',
    });
    await expect(peopleService.setPreference('alice@example.com', 'ui.theme', 'dark')).resolves.toEqual(preferences);
    await expect(peopleService.deletePreference('alice@example.com', 'ui.theme')).resolves.toEqual(preferences);
    await expect(peopleService.clearPreferences('alice@example.com')).resolves.toEqual(preferences);

    expect(mockedApi.get).toHaveBeenNthCalledWith(1, '/people/alice/preferences', {
      params: { filter: 'ui.' },
    });
    expect(mockedApi.get).toHaveBeenNthCalledWith(2, '/people/alice/preferences/namespaces');
    expect(mockedApi.get).toHaveBeenNthCalledWith(3, '/people/alice/preferences/export');
    expect(mockedApi.post).toHaveBeenCalledWith('/people/alice/preferences/import', {
      preferences: { 'ui.theme': 'dark' },
    });
    expect(mockedApi.get).toHaveBeenNthCalledWith(
      4,
      '/people/alice%40example.com/preferences/org.athena.rm%2FreportPreset'
    );
    expect(mockedApi.put).toHaveBeenCalledWith('/people/alice%40example.com/preferences/ui.theme', {
      value: 'dark',
    });
    expect(mockedApi.delete).toHaveBeenNthCalledWith(1, '/people/alice%40example.com/preferences/ui.theme');
    expect(mockedApi.delete).toHaveBeenNthCalledWith(2, '/people/alice%40example.com/preferences');
  });

  it('guards activity, site, favorite-site, and membership request list responses', async () => {
    mockedApi.get
      .mockResolvedValueOnce([activity])
      .mockResolvedValueOnce([site])
      .mockResolvedValueOnce([favoriteSite])
      .mockResolvedValueOnce([membershipRequest])
      .mockResolvedValueOnce(page([membershipRequest]));

    await expect(peopleService.getActivities('alice')).resolves.toEqual([activity]);
    await expect(peopleService.getSites('alice')).resolves.toEqual([site]);
    await expect(peopleService.getFavoriteSites('alice')).resolves.toEqual([favoriteSite]);
    await expect(peopleService.getSiteMembershipRequests('alice')).resolves.toEqual([membershipRequest]);
    await expect(
      peopleService.getVisibleSiteMembershipRequests({ siteId: 'records', status: 'PENDING', requester: 'alice' })
    ).resolves.toEqual(page([membershipRequest]));

    expect(mockedApi.get).toHaveBeenNthCalledWith(1, '/people/alice/activities');
    expect(mockedApi.get).toHaveBeenNthCalledWith(2, '/people/alice/sites');
    expect(mockedApi.get).toHaveBeenNthCalledWith(3, '/people/alice/favorite-sites');
    expect(mockedApi.get).toHaveBeenNthCalledWith(4, '/people/alice/site-membership-requests');
    expect(mockedApi.get).toHaveBeenNthCalledWith(5, '/people/site-membership-requests', {
      params: {
        siteId: 'records',
        status: 'PENDING',
        requester: 'alice',
        page: 0,
        size: 20,
      },
    });
  });

  it('guards favorite-site and membership request write responses', async () => {
    mockedApi.get.mockResolvedValueOnce(favoriteSite);
    mockedApi.post
      .mockResolvedValueOnce(favoriteSite)
      .mockResolvedValueOnce(membershipRequest)
      .mockResolvedValueOnce(membershipRequest)
      .mockResolvedValueOnce(membershipRequest);
    mockedApi.put.mockResolvedValueOnce(membershipRequest);
    mockedApi.delete.mockResolvedValueOnce(undefined);

    await expect(peopleService.getFavoriteSite('alice@example.com', 'records/site')).resolves.toEqual(favoriteSite);
    await expect(peopleService.createFavoriteSite('alice@example.com', { nodeId: 'site-node' })).resolves.toEqual(
      favoriteSite
    );
    await expect(
      peopleService.createSiteMembershipRequest('alice@example.com', { siteId: 'records', role: 'Contributor' })
    ).resolves.toEqual(membershipRequest);
    await expect(
      peopleService.updateSiteMembershipRequest('alice@example.com', 'records/site', { siteId: 'records' })
    ).resolves.toEqual(membershipRequest);
    await expect(peopleService.approveSiteMembershipRequest('alice@example.com', 'records/site')).resolves.toEqual(
      membershipRequest
    );
    await expect(
      peopleService.rejectSiteMembershipRequest('alice@example.com', 'records/site', { decisionComment: 'No' })
    ).resolves.toEqual(membershipRequest);
    await expect(peopleService.withdrawSiteMembershipRequest('alice@example.com', 'records/site')).resolves.toBeUndefined();

    expect(mockedApi.get).toHaveBeenCalledWith('/people/alice%40example.com/favorite-sites/records%2Fsite');
    expect(mockedApi.post).toHaveBeenNthCalledWith(1, '/people/alice%40example.com/favorite-sites', {
      nodeId: 'site-node',
    });
    expect(mockedApi.post).toHaveBeenNthCalledWith(
      2,
      '/people/alice%40example.com/site-membership-requests',
      { siteId: 'records', role: 'Contributor' }
    );
    expect(mockedApi.put).toHaveBeenCalledWith(
      '/people/alice%40example.com/site-membership-requests/records%2Fsite',
      { siteId: 'records' }
    );
    expect(mockedApi.post).toHaveBeenNthCalledWith(
      3,
      '/people/alice%40example.com/site-membership-requests/records%2Fsite/approve',
      {}
    );
    expect(mockedApi.post).toHaveBeenNthCalledWith(
      4,
      '/people/alice%40example.com/site-membership-requests/records%2Fsite/reject',
      { decisionComment: 'No' }
    );
    expect(mockedApi.delete).toHaveBeenCalledWith(
      '/people/alice%40example.com/site-membership-requests/records%2Fsite'
    );
  });

  it('guards profile and preference update responses', async () => {
    mockedApi.put
      .mockResolvedValueOnce(preferences)
      .mockResolvedValueOnce({ ...preferences, preferences: { compactMode: true } });

    await expect(peopleService.updateProfile('alice@example.com', { displayName: 'Alice' })).resolves.toEqual(
      preferences
    );
    await expect(peopleService.updatePreferences('alice@example.com', { compactMode: true })).resolves.toEqual({
      ...preferences,
      preferences: { compactMode: true },
    });

    expect(mockedApi.put).toHaveBeenNthCalledWith(1, '/people/alice%40example.com/profile', {
      displayName: 'Alice',
    });
    expect(mockedApi.put).toHaveBeenNthCalledWith(2, '/people/alice%40example.com/preferences', {
      preferences: { compactMode: true },
    });
  });

  it('rejects HTML fallback payloads before returning DTOs', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html>fallback</html>');

    await expectUnexpectedResponse(peopleService.get('alice'));
  });

  it('rejects malformed page envelopes and nested item arrays', async () => {
    mockedApi.get
      .mockResolvedValueOnce({ ...page([user]), totalElements: '1' })
      .mockResolvedValueOnce(page([[favorite]]));

    await expectUnexpectedResponse(peopleService.search('ali'));
    await expectUnexpectedResponse(peopleService.getFavorites('alice'));
  });

  it('rejects malformed object and list envelopes for preference APIs', async () => {
    mockedApi.get
      .mockResolvedValueOnce({ ...preferences, preferences: [] })
      .mockResolvedValueOnce(['ui', 3])
      .mockResolvedValueOnce(['not-an-export-object']);

    await expectUnexpectedResponse(peopleService.getPreferences('alice'));
    await expectUnexpectedResponse(peopleService.getPreferenceNamespaces('alice'));
    await expectUnexpectedResponse(peopleService.exportPreferences('alice'));
  });

  it('rejects malformed nested arrays in collection responses', async () => {
    mockedApi.get.mockResolvedValueOnce([[activity]]);

    await expectUnexpectedResponse(peopleService.getActivities('alice'));
  });
});
