import api from './api';

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

class LdapService {
  async testConnection(): Promise<LdapConnectionStatus> {
    return api.post<LdapConnectionStatus>('/admin/ldap/test-connection');
  }

  async syncNow(): Promise<LdapSyncResult> {
    return api.post<LdapSyncResult>('/admin/ldap/sync');
  }
}

const ldapService = new LdapService();
export default ldapService;
