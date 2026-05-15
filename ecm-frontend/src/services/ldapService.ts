import api from './api';

export const LDAP_UNEXPECTED_RESPONSE_MESSAGE =
  'LDAP admin endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

export interface LdapConnectionStatus {
  reachable: boolean;
  userBaseDn: string | null;
  groupBaseDn: string | null;
  message: string | null;
}

export interface LdapSyncResult {
  trigger: string | null;
  syncedAt: string | null;
  usersCreated: number;
  usersUpdated: number;
  usersDisabled: number;
  usersSkipped: number;
  groupsCreated: number;
  groupsUpdated: number;
  groupsDisabled: number;
  groupsSkipped: number;
  membershipsChanged: number;
  unresolvedMembers: number;
  warnings: string[];
}

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isStringOrNullish = (value: unknown): value is string | null | undefined => (
  value === null || value === undefined || typeof value === 'string'
);

const isFiniteNumber = (value: unknown): value is number => (
  typeof value === 'number' && Number.isFinite(value)
);

const isStringArray = (value: unknown): value is string[] => (
  Array.isArray(value) && value.every((item) => typeof item === 'string')
);

const assertUnexpectedResponse = (): never => {
  throw new Error(LDAP_UNEXPECTED_RESPONSE_MESSAGE);
};

const isLdapConnectionStatus = (value: unknown): value is LdapConnectionStatus => (
  isObject(value)
    && typeof value.reachable === 'boolean'
    && isStringOrNullish(value.userBaseDn)
    && isStringOrNullish(value.groupBaseDn)
    && isStringOrNullish(value.message)
);

const assertLdapConnectionStatus = (value: unknown): LdapConnectionStatus => (
  isLdapConnectionStatus(value) ? value : assertUnexpectedResponse()
);

const isLdapSyncResult = (value: unknown): value is LdapSyncResult => (
  isObject(value)
    && isStringOrNullish(value.trigger)
    && isStringOrNullish(value.syncedAt)
    && isFiniteNumber(value.usersCreated)
    && isFiniteNumber(value.usersUpdated)
    && isFiniteNumber(value.usersDisabled)
    && isFiniteNumber(value.usersSkipped)
    && isFiniteNumber(value.groupsCreated)
    && isFiniteNumber(value.groupsUpdated)
    && isFiniteNumber(value.groupsDisabled)
    && isFiniteNumber(value.groupsSkipped)
    && isFiniteNumber(value.membershipsChanged)
    && isFiniteNumber(value.unresolvedMembers)
    && isStringArray(value.warnings)
);

const assertLdapSyncResult = (value: unknown): LdapSyncResult => (
  isLdapSyncResult(value) ? value : assertUnexpectedResponse()
);

class LdapService {
  async testConnection(): Promise<LdapConnectionStatus> {
    const result = await api.post<unknown>('/admin/ldap/test-connection');
    return assertLdapConnectionStatus(result);
  }

  async syncNow(): Promise<LdapSyncResult> {
    const result = await api.post<unknown>('/admin/ldap/sync');
    return assertLdapSyncResult(result);
  }
}

const ldapService = new LdapService();
export default ldapService;
