import api from './api';
import { User } from 'types';
import { Group } from './userGroupService';

interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface PersonFavoriteItem {
  id: string;
  nodeId: string;
  nodeName: string;
  nodeType: 'FOLDER' | 'DOCUMENT';
  createdAt: string;
}

export interface PersonPreferenceEntry {
  key: string;
  value: any;
}

export interface PersonPreferences {
  username: string;
  displayName?: string;
  firstName?: string;
  lastName?: string;
  email: string;
  phone?: string;
  department?: string;
  jobTitle?: string;
  avatarUrl?: string;
  locale?: string;
  timezone?: string;
  enabled: boolean;
  locked: boolean;
  lastLoginDate?: string;
  lastPasswordChangeDate?: string;
  quotaSizeMb?: number;
  usedSizeMb?: number;
  preferences: Record<string, any>;
}

export interface PersonActivityItem {
  id: string;
  type: string;
  title: string;
  summary: string;
  occurredAt?: string;
  nodeId?: string;
  nodeName?: string;
  nodeType?: string;
  metadata?: Record<string, any>;
}

export interface PersonSiteItem {
  siteId: string;
  title: string;
  description?: string;
  role: string;
  visibility: string;
  memberCount?: number;
  createdAt?: string;
  lastModifiedAt?: string;
}

export interface PersonFavoriteSiteItem {
  siteId?: string;
  title: string;
  description?: string;
  folderType?: string;
  nodeType?: string;
  nodeId?: string;
  favoritedAt?: string;
  path?: string;
}

export interface PersonFavoriteWriteRequest {
  nodeId: string;
}

export interface PersonFavoriteSiteWriteRequest {
  nodeId: string;
}

export interface PersonSiteMembershipRequestItem {
  username?: string;
  siteId?: string;
  siteTitle: string;
  role?: string;
  status: string;
  message?: string;
  requestedAt?: string;
  decisionBy?: string;
  decisionAt?: string;
  decisionComment?: string;
  metadata?: Record<string, any>;
}

export interface PersonSiteMembershipRequestWriteRequest {
  siteId: string;
  siteTitle?: string;
  role?: string;
  message?: string;
}

export interface PersonProfileUpdateRequest {
  displayName?: string;
  firstName?: string;
  lastName?: string;
  phone?: string;
  department?: string;
  jobTitle?: string;
  avatarUrl?: string;
  locale?: string;
  timezone?: string;
}

class PeopleService {
  async search(query = '', page = 0, size = 20): Promise<PageResponse<User>> {
    return api.get<PageResponse<User>>('/people', {
      params: { query: query || undefined, page, size },
    });
  }

  async get(username: string): Promise<User> {
    return api.get<User>(`/people/${encodeURIComponent(username)}`);
  }

  async getGroups(username: string): Promise<Group[]> {
    return api.get<Group[]>(`/people/${encodeURIComponent(username)}/groups`);
  }

  async getFavorites(username: string, page = 0, size = 20): Promise<PageResponse<PersonFavoriteItem>> {
    return api.get<PageResponse<PersonFavoriteItem>>(
      `/people/${encodeURIComponent(username)}/favorites`,
      { params: { page, size } }
    );
  }

  async getFavorite(username: string, nodeId: string): Promise<PersonFavoriteItem> {
    return api.get<PersonFavoriteItem>(`/people/${encodeURIComponent(username)}/favorites/${encodeURIComponent(nodeId)}`);
  }

  async createFavorite(username: string, request: PersonFavoriteWriteRequest): Promise<PersonFavoriteItem> {
    return api.post<PersonFavoriteItem>(`/people/${encodeURIComponent(username)}/favorites`, request);
  }

  async deleteFavorite(username: string, nodeId: string): Promise<void> {
    await api.delete(`/people/${encodeURIComponent(username)}/favorites/${encodeURIComponent(nodeId)}`);
  }

  async getPreferences(username: string, filter?: string): Promise<PersonPreferences> {
    return api.get<PersonPreferences>(`/people/${encodeURIComponent(username)}/preferences`, {
      params: {
        filter: filter || undefined,
      },
    });
  }

  async getPreferenceNamespaces(username: string): Promise<string[]> {
    return api.get<string[]>(`/people/${encodeURIComponent(username)}/preferences/namespaces`);
  }

  async exportPreferences(username: string): Promise<Record<string, any>> {
    return api.get<Record<string, any>>(`/people/${encodeURIComponent(username)}/preferences/export`);
  }

  async importPreferences(username: string, preferences: Record<string, any>): Promise<PersonPreferences> {
    return api.post<PersonPreferences>(`/people/${encodeURIComponent(username)}/preferences/import`, {
      preferences,
    });
  }

  async getPreference(username: string, preferenceName: string): Promise<PersonPreferenceEntry> {
    return api.get<PersonPreferenceEntry>(
      `/people/${encodeURIComponent(username)}/preferences/${encodeURIComponent(preferenceName)}`
    );
  }

  async setPreference(username: string, preferenceName: string, value: any): Promise<PersonPreferences> {
    return api.put<PersonPreferences>(
      `/people/${encodeURIComponent(username)}/preferences/${encodeURIComponent(preferenceName)}`,
      { value }
    );
  }

  async deletePreference(username: string, preferenceName: string): Promise<PersonPreferences> {
    return api.delete<PersonPreferences>(
      `/people/${encodeURIComponent(username)}/preferences/${encodeURIComponent(preferenceName)}`
    );
  }

  async clearPreferences(username: string): Promise<PersonPreferences> {
    return api.delete<PersonPreferences>(`/people/${encodeURIComponent(username)}/preferences`);
  }

  async getActivities(username: string): Promise<PersonActivityItem[]> {
    return api.get<PersonActivityItem[]>(`/people/${encodeURIComponent(username)}/activities`);
  }

  async getSites(username: string): Promise<PersonSiteItem[]> {
    return api.get<PersonSiteItem[]>(`/people/${encodeURIComponent(username)}/sites`);
  }

  async getFavoriteSites(username: string): Promise<PersonFavoriteSiteItem[]> {
    return api.get<PersonFavoriteSiteItem[]>(`/people/${encodeURIComponent(username)}/favorite-sites`);
  }

  async getFavoriteSite(username: string, siteId: string): Promise<PersonFavoriteSiteItem> {
    return api.get<PersonFavoriteSiteItem>(
      `/people/${encodeURIComponent(username)}/favorite-sites/${encodeURIComponent(siteId)}`
    );
  }

  async createFavoriteSite(username: string, request: PersonFavoriteSiteWriteRequest): Promise<PersonFavoriteSiteItem> {
    return api.post<PersonFavoriteSiteItem>(`/people/${encodeURIComponent(username)}/favorite-sites`, request);
  }

  async deleteFavoriteSite(username: string, siteId: string): Promise<void> {
    await api.delete(`/people/${encodeURIComponent(username)}/favorite-sites/${encodeURIComponent(siteId)}`);
  }

  async getSiteMembershipRequests(username: string): Promise<PersonSiteMembershipRequestItem[]> {
    return api.get<PersonSiteMembershipRequestItem[]>(
      `/people/${encodeURIComponent(username)}/site-membership-requests`
    );
  }

  async getVisibleSiteMembershipRequests(params: {
    siteId?: string;
    status?: string;
    requester?: string;
    page?: number;
    size?: number;
  } = {}): Promise<PageResponse<PersonSiteMembershipRequestItem>> {
    return api.get<PageResponse<PersonSiteMembershipRequestItem>>('/people/site-membership-requests', {
      params: {
        siteId: params.siteId || undefined,
        status: params.status || undefined,
        requester: params.requester || undefined,
        page: params.page ?? 0,
        size: params.size ?? 20,
      },
    });
  }

  async createSiteMembershipRequest(
    username: string,
    request: PersonSiteMembershipRequestWriteRequest
  ): Promise<PersonSiteMembershipRequestItem> {
    return api.post<PersonSiteMembershipRequestItem>(
      `/people/${encodeURIComponent(username)}/site-membership-requests`,
      request
    );
  }

  async updateSiteMembershipRequest(
    username: string,
    siteId: string,
    request: PersonSiteMembershipRequestWriteRequest
  ): Promise<PersonSiteMembershipRequestItem> {
    return api.put<PersonSiteMembershipRequestItem>(
      `/people/${encodeURIComponent(username)}/site-membership-requests/${encodeURIComponent(siteId)}`,
      request
    );
  }

  async approveSiteMembershipRequest(
    username: string,
    siteId: string,
    payload?: { decisionComment?: string }
  ): Promise<PersonSiteMembershipRequestItem> {
    return api.post<PersonSiteMembershipRequestItem>(
      `/people/${encodeURIComponent(username)}/site-membership-requests/${encodeURIComponent(siteId)}/approve`,
      payload || {}
    );
  }

  async rejectSiteMembershipRequest(
    username: string,
    siteId: string,
    payload?: { decisionComment?: string }
  ): Promise<PersonSiteMembershipRequestItem> {
    return api.post<PersonSiteMembershipRequestItem>(
      `/people/${encodeURIComponent(username)}/site-membership-requests/${encodeURIComponent(siteId)}/reject`,
      payload || {}
    );
  }

  async withdrawSiteMembershipRequest(username: string, siteId: string): Promise<void> {
    await api.delete(`/people/${encodeURIComponent(username)}/site-membership-requests/${encodeURIComponent(siteId)}`);
  }

  async updateProfile(username: string, payload: PersonProfileUpdateRequest): Promise<PersonPreferences> {
    return api.put<PersonPreferences>(`/people/${encodeURIComponent(username)}/profile`, payload);
  }

  async updatePreferences(username: string, preferences: Record<string, any>): Promise<PersonPreferences> {
    return api.put<PersonPreferences>(`/people/${encodeURIComponent(username)}/preferences`, { preferences });
  }
}

const peopleService = new PeopleService();
export default peopleService;
