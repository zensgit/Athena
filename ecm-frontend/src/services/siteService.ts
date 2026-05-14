import api from './api';

export const SITE_UNEXPECTED_RESPONSE_MESSAGE =
  'Site endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

export type SiteVisibility = 'PUBLIC' | 'MODERATED' | 'PRIVATE';
export type SiteStatus = 'ACTIVE' | 'ARCHIVED';
export type SiteMemberRole = 'MANAGER' | 'COLLABORATOR' | 'CONTRIBUTOR' | 'CONSUMER';

export interface SiteDto {
  id: string;
  siteId: string;
  title: string;
  description?: string | null;
  visibility: SiteVisibility;
  status: SiteStatus;
  rootFolderId?: string | null;
  rootFolderTitle?: string | null;
  rootFolderPath?: string | null;
  createdBy: string;
  createdDate: string;
  lastModifiedDate?: string | null;
  deleted: boolean;
  deletedAt?: string | null;
  deletedBy?: string | null;
}

export interface CreateSiteRequest {
  siteId: string;
  title: string;
  description?: string;
  visibility?: SiteVisibility;
  rootFolderId?: string;
}

export interface UpdateSiteRequest {
  title?: string;
  description?: string;
  visibility?: SiteVisibility;
  status?: SiteStatus;
  rootFolderId?: string;
}

export interface MembershipRequestDto {
  username: string;
  siteId: string;
  siteTitle?: string | null;
  role: string;
  message?: string | null;
  status: string;
  requestedAt?: string | null;
  decisionBy?: string | null;
  decisionAt?: string | null;
  decisionComment?: string | null;
}

export interface CreateMembershipRequest {
  siteTitle?: string;
  role?: SiteMemberRole;
  message?: string;
}

export interface SiteMemberDto {
  id: string;
  siteId: string;
  username: string;
  role: SiteMemberRole;
  joinedAt?: string | null;
}

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isBoolean = (value: unknown): value is boolean => typeof value === 'boolean';

const isStringOrNullish = (value: unknown): value is string | null | undefined => (
  value === null || value === undefined || typeof value === 'string'
);

const SITE_VISIBILITIES: SiteVisibility[] = ['PUBLIC', 'MODERATED', 'PRIVATE'];
const SITE_STATUSES: SiteStatus[] = ['ACTIVE', 'ARCHIVED'];
const SITE_MEMBER_ROLES: SiteMemberRole[] = ['MANAGER', 'COLLABORATOR', 'CONTRIBUTOR', 'CONSUMER'];

const isSiteVisibility = (value: unknown): value is SiteVisibility => (
  typeof value === 'string' && (SITE_VISIBILITIES as string[]).includes(value)
);

const isSiteStatus = (value: unknown): value is SiteStatus => (
  typeof value === 'string' && (SITE_STATUSES as string[]).includes(value)
);

const isSiteMemberRole = (value: unknown): value is SiteMemberRole => (
  typeof value === 'string' && (SITE_MEMBER_ROLES as string[]).includes(value)
);

const assertUnexpectedResponse = (): never => {
  throw new Error(SITE_UNEXPECTED_RESPONSE_MESSAGE);
};

const isSiteDto = (value: unknown): value is SiteDto => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.siteId === 'string'
    && typeof value.title === 'string'
    && isSiteVisibility(value.visibility)
    && isSiteStatus(value.status)
    && typeof value.createdBy === 'string'
    && typeof value.createdDate === 'string'
    && isBoolean(value.deleted)
    && isStringOrNullish(value.description)
    && isStringOrNullish(value.rootFolderId)
    && isStringOrNullish(value.rootFolderTitle)
    && isStringOrNullish(value.rootFolderPath)
    && isStringOrNullish(value.lastModifiedDate)
    && isStringOrNullish(value.deletedAt)
    && isStringOrNullish(value.deletedBy);
};

const assertSiteDto = (value: unknown): SiteDto => (
  isSiteDto(value) ? value : assertUnexpectedResponse()
);

const assertSiteDtoArray = (value: unknown): SiteDto[] => {
  if (!Array.isArray(value) || !value.every(isSiteDto)) {
    return assertUnexpectedResponse();
  }
  return value;
};

const isMembershipRequestDto = (value: unknown): value is MembershipRequestDto => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.username === 'string'
    && typeof value.siteId === 'string'
    && typeof value.role === 'string'
    && typeof value.status === 'string'
    && isStringOrNullish(value.siteTitle)
    && isStringOrNullish(value.message)
    && isStringOrNullish(value.requestedAt)
    && isStringOrNullish(value.decisionBy)
    && isStringOrNullish(value.decisionAt)
    && isStringOrNullish(value.decisionComment);
};

const assertMembershipRequestDto = (value: unknown): MembershipRequestDto => (
  isMembershipRequestDto(value) ? value : assertUnexpectedResponse()
);

const assertMembershipRequestDtoArray = (value: unknown): MembershipRequestDto[] => {
  if (!Array.isArray(value) || !value.every(isMembershipRequestDto)) {
    return assertUnexpectedResponse();
  }
  return value;
};

const isSiteMemberDto = (value: unknown): value is SiteMemberDto => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.siteId === 'string'
    && typeof value.username === 'string'
    && isSiteMemberRole(value.role)
    && isStringOrNullish(value.joinedAt);
};

const assertSiteMemberDto = (value: unknown): SiteMemberDto => (
  isSiteMemberDto(value) ? value : assertUnexpectedResponse()
);

const assertSiteMemberDtoArray = (value: unknown): SiteMemberDto[] => {
  if (!Array.isArray(value) || !value.every(isSiteMemberDto)) {
    return assertUnexpectedResponse();
  }
  return value;
};

class SiteService {
  async listSites(includeArchived = false): Promise<SiteDto[]> {
    const result = await api.get<unknown>('/sites', { params: { includeArchived } });
    return assertSiteDtoArray(result);
  }

  async getSite(siteId: string): Promise<SiteDto> {
    const result = await api.get<unknown>(`/sites/${siteId}`);
    return assertSiteDto(result);
  }

  async createSite(request: CreateSiteRequest): Promise<SiteDto> {
    const result = await api.post<unknown>('/sites', request);
    return assertSiteDto(result);
  }

  async updateSite(siteId: string, request: UpdateSiteRequest): Promise<SiteDto> {
    const result = await api.put<unknown>(`/sites/${siteId}`, request);
    return assertSiteDto(result);
  }

  async deleteSite(siteId: string): Promise<void> {
    return api.delete(`/sites/${siteId}`);
  }

  // ---- membership requests (site-centric) ---------------------------------

  async getMembershipRequests(siteId: string): Promise<MembershipRequestDto[]> {
    const result = await api.get<unknown>(`/sites/${siteId}/membership-requests`);
    return assertMembershipRequestDtoArray(result);
  }

  async requestMembership(siteId: string, request: CreateMembershipRequest): Promise<MembershipRequestDto> {
    const result = await api.post<unknown>(`/sites/${siteId}/membership-requests`, request);
    return assertMembershipRequestDto(result);
  }

  async approveMembershipRequest(siteId: string, username: string, comment?: string): Promise<MembershipRequestDto> {
    const result = await api.post<unknown>(
      `/sites/${siteId}/membership-requests/${username}/approve`,
      comment ? { comment } : {},
    );
    return assertMembershipRequestDto(result);
  }

  async rejectMembershipRequest(siteId: string, username: string, comment?: string): Promise<MembershipRequestDto> {
    const result = await api.post<unknown>(
      `/sites/${siteId}/membership-requests/${username}/reject`,
      comment ? { comment } : {},
    );
    return assertMembershipRequestDto(result);
  }

  async withdrawMembershipRequest(siteId: string): Promise<void> {
    return api.delete(`/sites/${siteId}/membership-requests`);
  }

  // ---- members (roster) ---------------------------------------------------

  async getMembers(siteId: string): Promise<SiteMemberDto[]> {
    const result = await api.get<unknown>(`/sites/${siteId}/members`);
    return assertSiteMemberDtoArray(result);
  }

  async addMember(siteId: string, username: string, role: SiteMemberRole = 'CONSUMER'): Promise<SiteMemberDto> {
    const result = await api.post<unknown>(`/sites/${siteId}/members`, { username, role });
    return assertSiteMemberDto(result);
  }

  async updateMemberRole(siteId: string, username: string, role: SiteMemberRole): Promise<SiteMemberDto> {
    const result = await api.put<unknown>(`/sites/${siteId}/members/${username}`, { role });
    return assertSiteMemberDto(result);
  }

  async removeMember(siteId: string, username: string): Promise<void> {
    return api.delete(`/sites/${siteId}/members/${username}`);
  }
}

const siteService = new SiteService();
export default siteService;
