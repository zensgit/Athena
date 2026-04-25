import peopleService from './peopleService';
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

describe('peopleService preferences namespace consumption', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('passes a namespace filter when fetching preferences', async () => {
    mockedApi.get.mockResolvedValueOnce({ preferences: { 'ui.theme': 'dark' } });

    await peopleService.getPreferences('alice', 'ui.');

    expect(mockedApi.get).toHaveBeenCalledWith('/people/alice/preferences', {
      params: { filter: 'ui.' },
    });
  });

  it('fetches preference namespaces from the discovery endpoint', async () => {
    mockedApi.get.mockResolvedValueOnce(['app', 'ui']);

    await peopleService.getPreferenceNamespaces('alice');

    expect(mockedApi.get).toHaveBeenCalledWith('/people/alice/preferences/namespaces');
  });

  it('exports preferences as a raw JSON payload', async () => {
    mockedApi.get.mockResolvedValueOnce({ 'ui.theme': 'dark', compactMode: true });

    await peopleService.exportPreferences('alice');

    expect(mockedApi.get).toHaveBeenCalledWith('/people/alice/preferences/export');
  });

  it('imports preferences through the dedicated import endpoint', async () => {
    mockedApi.post.mockResolvedValueOnce({
      username: 'alice',
      email: 'alice@example.com',
      enabled: true,
      locked: false,
      preferences: { 'ui.theme': 'dark' },
    });

    await peopleService.importPreferences('alice', { 'ui.theme': 'dark' });

    expect(mockedApi.post).toHaveBeenCalledWith('/people/alice/preferences/import', {
      preferences: { 'ui.theme': 'dark' },
    });
  });

  it('fetches an encoded preference key for a user', async () => {
    mockedApi.get.mockResolvedValueOnce({
      key: 'org.athena.rm.reportPreset.delivery.notifyOnSuccess',
      value: true,
    });

    await peopleService.getPreference(
      'alice@example.com',
      'org.athena.rm.reportPreset.delivery.notifyOnSuccess'
    );

    expect(mockedApi.get).toHaveBeenCalledWith(
      '/people/alice%40example.com/preferences/org.athena.rm.reportPreset.delivery.notifyOnSuccess'
    );
  });

  it('sets an encoded preference key with a value payload', async () => {
    mockedApi.put.mockResolvedValueOnce({
      username: 'alice@example.com',
      email: 'alice@example.com',
      enabled: true,
      locked: false,
      preferences: {
        'org.athena.rm.reportPreset.delivery.notifyOnFailure': false,
      },
    });

    await peopleService.setPreference(
      'alice@example.com',
      'org.athena.rm.reportPreset.delivery.notifyOnFailure',
      false
    );

    expect(mockedApi.put).toHaveBeenCalledWith(
      '/people/alice%40example.com/preferences/org.athena.rm.reportPreset.delivery.notifyOnFailure',
      { value: false }
    );
  });

  it('deletes an encoded preference key for a user', async () => {
    mockedApi.delete.mockResolvedValueOnce({
      username: 'alice@example.com',
      email: 'alice@example.com',
      enabled: true,
      locked: false,
      preferences: {},
    });

    await peopleService.deletePreference('alice@example.com', 'org.athena.rm/reportPreset');

    expect(mockedApi.delete).toHaveBeenCalledWith(
      '/people/alice%40example.com/preferences/org.athena.rm%2FreportPreset'
    );
  });
});
