import api from './api';
import ldapService, {
  LDAP_UNEXPECTED_RESPONSE_MESSAGE,
  LdapConnectionStatus,
  LdapSyncResult,
} from './ldapService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    post: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const connectionStatus: LdapConnectionStatus = {
  reachable: true,
  userBaseDn: 'ou=users,dc=example,dc=com',
  groupBaseDn: 'ou=groups,dc=example,dc=com',
  message: 'LDAP connection successful',
};

const syncResult: LdapSyncResult = {
  trigger: 'manual',
  syncedAt: '2026-05-14T19:00:00',
  usersCreated: 1,
  usersUpdated: 2,
  usersDisabled: 0,
  usersSkipped: 3,
  groupsCreated: 4,
  groupsUpdated: 5,
  groupsDisabled: 0,
  groupsSkipped: 6,
  membershipsChanged: 7,
  unresolvedMembers: 8,
  warnings: ['Skipped LDAP user missing email'],
};

describe('ldapService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns guarded LDAP connection status and keeps endpoint path', async () => {
    mockedApi.post.mockResolvedValueOnce(connectionStatus);

    await expect(ldapService.testConnection()).resolves.toEqual(connectionStatus);

    expect(mockedApi.post).toHaveBeenCalledWith('/admin/ldap/test-connection');
  });

  it('accepts nullable LDAP connection fields', async () => {
    const nullableStatus: LdapConnectionStatus = {
      reachable: false,
      userBaseDn: null,
      groupBaseDn: null,
      message: null,
    };
    mockedApi.post.mockResolvedValueOnce(nullableStatus);

    await expect(ldapService.testConnection()).resolves.toEqual(nullableStatus);
  });

  it('rejects HTML fallback for LDAP connection status', async () => {
    mockedApi.post.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(ldapService.testConnection()).rejects.toThrow(
      LDAP_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects malformed LDAP connection status', async () => {
    mockedApi.post.mockResolvedValueOnce({
      ...connectionStatus,
      reachable: 'yes',
    });

    await expect(ldapService.testConnection()).rejects.toThrow(
      LDAP_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('returns guarded LDAP sync result and keeps endpoint path', async () => {
    mockedApi.post.mockResolvedValueOnce(syncResult);

    await expect(ldapService.syncNow()).resolves.toEqual(syncResult);

    expect(mockedApi.post).toHaveBeenCalledWith('/admin/ldap/sync');
  });

  it('accepts nullable LDAP sync trigger and timestamp', async () => {
    const nullableSyncResult: LdapSyncResult = {
      ...syncResult,
      trigger: null,
      syncedAt: null,
      warnings: [],
    };
    mockedApi.post.mockResolvedValueOnce(nullableSyncResult);

    await expect(ldapService.syncNow()).resolves.toEqual(nullableSyncResult);
  });

  it('rejects HTML fallback for LDAP sync result', async () => {
    mockedApi.post.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(ldapService.syncNow()).rejects.toThrow(
      LDAP_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects malformed LDAP sync counters', async () => {
    mockedApi.post.mockResolvedValueOnce({
      ...syncResult,
      usersCreated: '1',
    });

    await expect(ldapService.syncNow()).rejects.toThrow(
      LDAP_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects malformed LDAP sync warnings', async () => {
    mockedApi.post.mockResolvedValueOnce({
      ...syncResult,
      warnings: ['ok', 42],
    });

    await expect(ldapService.syncNow()).rejects.toThrow(
      LDAP_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });
});
