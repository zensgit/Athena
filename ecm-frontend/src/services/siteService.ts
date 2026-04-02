import api from './api';

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
  lastModifiedDate?: string;
  deleted: boolean;
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

class SiteService {
  async listSites(includeArchived = false): Promise<SiteDto[]> {
    return api.get<SiteDto[]>('/sites', { params: { includeArchived } });
  }

  async getSite(siteId: string): Promise<SiteDto> {
    return api.get<SiteDto>(`/sites/${siteId}`);
  }

  async createSite(request: CreateSiteRequest): Promise<SiteDto> {
    return api.post<SiteDto>('/sites', request);
  }

  async updateSite(siteId: string, request: UpdateSiteRequest): Promise<SiteDto> {
    return api.put<SiteDto>(`/sites/${siteId}`, request);
  }

  async deleteSite(siteId: string): Promise<void> {
    return api.delete(`/sites/${siteId}`);
  }

  // ---- membership requests (site-centric) ---------------------------------

  async getMembershipRequests(siteId: string): Promise<MembershipRequestDto[]> {
    return api.get<MembershipRequestDto[]>(`/sites/${siteId}/membership-requests`);
  }

  async requestMembership(siteId: string, request: CreateMembershipRequest): Promise<MembershipRequestDto> {
    return api.post<MembershipRequestDto>(`/sites/${siteId}/membership-requests`, request);
  }

  async approveMembershipRequest(siteId: string, username: string, comment?: string): Promise<MembershipRequestDto> {
    return api.post<MembershipRequestDto>(`/sites/${siteId}/membership-requests/${username}/approve`, comment ? { comment } : {});
  }

  async rejectMembershipRequest(siteId: string, username: string, comment?: string): Promise<MembershipRequestDto> {
    return api.post<MembershipRequestDto>(`/sites/${siteId}/membership-requests/${username}/reject`, comment ? { comment } : {});
  }

  async withdrawMembershipRequest(siteId: string): Promise<void> {
    return api.delete(`/sites/${siteId}/membership-requests`);
  }

  // ---- members (roster) ---------------------------------------------------

  async getMembers(siteId: string): Promise<SiteMemberDto[]> {
    return api.get<SiteMemberDto[]>(`/sites/${siteId}/members`);
  }

  async addMember(siteId: string, username: string, role: SiteMemberRole = 'CONSUMER'): Promise<SiteMemberDto> {
    return api.post<SiteMemberDto>(`/sites/${siteId}/members`, { username, role });
  }

  async updateMemberRole(siteId: string, username: string, role: SiteMemberRole): Promise<SiteMemberDto> {
    return api.put<SiteMemberDto>(`/sites/${siteId}/members/${username}`, { role });
  }

  async removeMember(siteId: string, username: string): Promise<void> {
    return api.delete(`/sites/${siteId}/members/${username}`);
  }
}

export interface MembershipRequestDto {
  username: string;
  siteId: string;
  siteTitle?: string;
  role: string;
  message?: string;
  status: string;
  requestedAt?: string;
  decisionBy?: string;
  decisionAt?: string;
  decisionComment?: string;
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
  joinedAt?: string;
}

const siteService = new SiteService();
export default siteService;
