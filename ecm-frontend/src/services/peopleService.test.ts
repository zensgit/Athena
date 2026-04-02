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
});
