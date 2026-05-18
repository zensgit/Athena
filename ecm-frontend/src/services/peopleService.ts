import api from './api';
import { User } from 'types';
import { Group } from './userGroupService';

export const PEOPLE_UNEXPECTED_RESPONSE_MESSAGE =
  'People endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

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

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isFiniteNumber = (value: unknown): value is number => (
  typeof value === 'number' && Number.isFinite(value)
);

const isStringOrNullish = (value: unknown): value is string | null | undefined => (
  value === null || value === undefined || typeof value === 'string'
);

const isBooleanOrNullish = (value: unknown): value is boolean | null | undefined => (
  value === null || value === undefined || typeof value === 'boolean'
);

const isNumberOrNullish = (value: unknown): value is number | null | undefined => (
  value === null || value === undefined || isFiniteNumber(value)
);

const isStringArray = (value: unknown): value is string[] => (
  Array.isArray(value) && value.every((entry) => typeof entry === 'string')
);

const assertUnexpectedResponse = (): never => {
  throw new Error(PEOPLE_UNEXPECTED_RESPONSE_MESSAGE);
};

const assertObject = (value: unknown): Record<string, unknown> => (
  isObject(value) ? value : assertUnexpectedResponse()
);

const isUser = (value: unknown): value is User => {
  if (!isObject(value)) {
    return false;
  }

  return typeof value.id === 'string'
    && typeof value.username === 'string'
    && typeof value.email === 'string'
    && isStringArray(value.roles)
    && isStringOrNullish(value.firstName)
    && isStringOrNullish(value.lastName)
    && isBooleanOrNullish(value.enabled)
    && isBooleanOrNullish(value.locked);
};

const assertUser = (value: unknown): User => (
  isUser(value) ? value : assertUnexpectedResponse()
);

const isGroup = (value: unknown): value is Group => {
  if (!isObject(value) || typeof value.name !== 'string') {
    return false;
  }

  return isStringOrNullish(value.id)
    && isStringOrNullish(value.displayName)
    && isStringOrNullish(value.description)
    && isStringOrNullish(value.email)
    && isBooleanOrNullish(value.enabled)
    && isStringOrNullish(value.groupType)
    && (value.users === null || value.users === undefined || (Array.isArray(value.users) && value.users.every(isUser)));
};

const assertArray = <T>(value: unknown, itemGuard: (item: unknown) => item is T): T[] => {
  if (!Array.isArray(value) || !value.every(itemGuard)) {
    return assertUnexpectedResponse();
  }

  return value;
};

const assertPageResponse = <T>(
  value: unknown,
  itemGuard: (item: unknown) => item is T
): PageResponse<T> => {
  if (!isObject(value)
    || !Array.isArray(value.content)
    || !value.content.every(itemGuard)
    || !isFiniteNumber(value.totalElements)
    || !isFiniteNumber(value.totalPages)
    || !isFiniteNumber(value.number)
    || !isFiniteNumber(value.size)) {
    return assertUnexpectedResponse();
  }

  return value as unknown as PageResponse<T>;
};

const isFavoriteItem = (value: unknown): value is PersonFavoriteItem => (
  isObject(value)
  && typeof value.id === 'string'
  && typeof value.nodeId === 'string'
  && typeof value.nodeName === 'string'
  && (value.nodeType === 'FOLDER' || value.nodeType === 'DOCUMENT')
  && typeof value.createdAt === 'string'
);

const isPreferencesRecord = (value: unknown): value is Record<string, unknown> => isObject(value);

const isPersonPreferences = (value: unknown): value is Record<string, unknown> => (
  isObject(value)
  && typeof value.username === 'string'
  && isStringOrNullish(value.displayName)
  && isStringOrNullish(value.firstName)
  && isStringOrNullish(value.lastName)
  && typeof value.email === 'string'
  && isStringOrNullish(value.phone)
  && isStringOrNullish(value.department)
  && isStringOrNullish(value.jobTitle)
  && isStringOrNullish(value.avatarUrl)
  && isStringOrNullish(value.locale)
  && isStringOrNullish(value.timezone)
  && typeof value.enabled === 'boolean'
  && typeof value.locked === 'boolean'
  && isStringOrNullish(value.lastLoginDate)
  && isStringOrNullish(value.lastPasswordChangeDate)
  && isNumberOrNullish(value.quotaSizeMb)
  && isNumberOrNullish(value.usedSizeMb)
  && isPreferencesRecord(value.preferences)
);

const assertPersonPreferences = (value: unknown): PersonPreferences => (
  isPersonPreferences(value)
    ? value as unknown as PersonPreferences
    : assertUnexpectedResponse()
);

const isPreferenceEntry = (value: unknown): value is PersonPreferenceEntry => (
  isObject(value) && typeof value.key === 'string' && Object.prototype.hasOwnProperty.call(value, 'value')
);

const isOptionalObject = (value: unknown): boolean => (
  value === null || value === undefined || isObject(value)
);

const isActivityItem = (value: unknown): value is PersonActivityItem => (
  isObject(value)
  && typeof value.id === 'string'
  && typeof value.type === 'string'
  && typeof value.title === 'string'
  && typeof value.summary === 'string'
  && isStringOrNullish(value.occurredAt)
  && isStringOrNullish(value.nodeId)
  && isStringOrNullish(value.nodeName)
  && isStringOrNullish(value.nodeType)
  && isOptionalObject(value.metadata)
);

const isSiteItem = (value: unknown): value is PersonSiteItem => (
  isObject(value)
  && typeof value.siteId === 'string'
  && typeof value.title === 'string'
  && isStringOrNullish(value.description)
  && typeof value.role === 'string'
  && typeof value.visibility === 'string'
  && isNumberOrNullish(value.memberCount)
  && isStringOrNullish(value.createdAt)
  && isStringOrNullish(value.lastModifiedAt)
);

const isFavoriteSiteItem = (value: unknown): value is PersonFavoriteSiteItem => (
  isObject(value)
  && isStringOrNullish(value.siteId)
  && typeof value.title === 'string'
  && isStringOrNullish(value.description)
  && isStringOrNullish(value.folderType)
  && isStringOrNullish(value.nodeType)
  && isStringOrNullish(value.nodeId)
  && isStringOrNullish(value.favoritedAt)
  && isStringOrNullish(value.path)
);

const isSiteMembershipRequestItem = (value: unknown): value is PersonSiteMembershipRequestItem => (
  isObject(value)
  && isStringOrNullish(value.username)
  && isStringOrNullish(value.siteId)
  && typeof value.siteTitle === 'string'
  && isStringOrNullish(value.role)
  && typeof value.status === 'string'
  && isStringOrNullish(value.message)
  && isStringOrNullish(value.requestedAt)
  && isStringOrNullish(value.decisionBy)
  && isStringOrNullish(value.decisionAt)
  && isStringOrNullish(value.decisionComment)
  && isOptionalObject(value.metadata)
);

class PeopleService {
  async search(query = '', page = 0, size = 20): Promise<PageResponse<User>> {
    const response = await api.get<unknown>('/people', {
      params: { query: query || undefined, page, size },
    });
    return assertPageResponse(response, isUser);
  }

  async get(username: string): Promise<User> {
    const response = await api.get<unknown>(`/people/${encodeURIComponent(username)}`);
    return assertUser(response);
  }

  async getGroups(username: string): Promise<Group[]> {
    const response = await api.get<unknown>(`/people/${encodeURIComponent(username)}/groups`);
    return assertArray(response, isGroup);
  }

  async getFavorites(username: string, page = 0, size = 20): Promise<PageResponse<PersonFavoriteItem>> {
    const response = await api.get<unknown>(
      `/people/${encodeURIComponent(username)}/favorites`,
      { params: { page, size } }
    );
    return assertPageResponse(response, isFavoriteItem);
  }

  async getFavorite(username: string, nodeId: string): Promise<PersonFavoriteItem> {
    const response = await api.get<unknown>(`/people/${encodeURIComponent(username)}/favorites/${encodeURIComponent(nodeId)}`);
    return isFavoriteItem(response) ? response : assertUnexpectedResponse();
  }

  async createFavorite(username: string, request: PersonFavoriteWriteRequest): Promise<PersonFavoriteItem> {
    const response = await api.post<unknown>(`/people/${encodeURIComponent(username)}/favorites`, request);
    return isFavoriteItem(response) ? response : assertUnexpectedResponse();
  }

  async deleteFavorite(username: string, nodeId: string): Promise<void> {
    await api.delete(`/people/${encodeURIComponent(username)}/favorites/${encodeURIComponent(nodeId)}`);
  }

  async getPreferences(username: string, filter?: string): Promise<PersonPreferences> {
    const response = await api.get<unknown>(`/people/${encodeURIComponent(username)}/preferences`, {
      params: {
        filter: filter || undefined,
      },
    });
    return assertPersonPreferences(response);
  }

  async getPreferenceNamespaces(username: string): Promise<string[]> {
    const response = await api.get<unknown>(`/people/${encodeURIComponent(username)}/preferences/namespaces`);
    return assertArray(response, (value): value is string => typeof value === 'string');
  }

  async exportPreferences(username: string): Promise<Record<string, any>> {
    const response = await api.get<unknown>(`/people/${encodeURIComponent(username)}/preferences/export`);
    return assertObject(response) as unknown as Record<string, any>;
  }

  async importPreferences(username: string, preferences: Record<string, any>): Promise<PersonPreferences> {
    const response = await api.post<unknown>(`/people/${encodeURIComponent(username)}/preferences/import`, {
      preferences,
    });
    return assertPersonPreferences(response);
  }

  async getPreference(username: string, preferenceName: string): Promise<PersonPreferenceEntry> {
    const response = await api.get<unknown>(
      `/people/${encodeURIComponent(username)}/preferences/${encodeURIComponent(preferenceName)}`
    );
    return isPreferenceEntry(response) ? response : assertUnexpectedResponse();
  }

  async setPreference(username: string, preferenceName: string, value: any): Promise<PersonPreferences> {
    const response = await api.put<unknown>(
      `/people/${encodeURIComponent(username)}/preferences/${encodeURIComponent(preferenceName)}`,
      { value }
    );
    return assertPersonPreferences(response);
  }

  async deletePreference(username: string, preferenceName: string): Promise<PersonPreferences> {
    const response = await api.delete<unknown>(
      `/people/${encodeURIComponent(username)}/preferences/${encodeURIComponent(preferenceName)}`
    );
    return assertPersonPreferences(response);
  }

  async clearPreferences(username: string): Promise<PersonPreferences> {
    const response = await api.delete<unknown>(`/people/${encodeURIComponent(username)}/preferences`);
    return assertPersonPreferences(response);
  }

  async getActivities(username: string): Promise<PersonActivityItem[]> {
    const response = await api.get<unknown>(`/people/${encodeURIComponent(username)}/activities`);
    return assertArray(response, isActivityItem);
  }

  async getSites(username: string): Promise<PersonSiteItem[]> {
    const response = await api.get<unknown>(`/people/${encodeURIComponent(username)}/sites`);
    return assertArray(response, isSiteItem);
  }

  async getFavoriteSites(username: string): Promise<PersonFavoriteSiteItem[]> {
    const response = await api.get<unknown>(`/people/${encodeURIComponent(username)}/favorite-sites`);
    return assertArray(response, isFavoriteSiteItem);
  }

  async getFavoriteSite(username: string, siteId: string): Promise<PersonFavoriteSiteItem> {
    const response = await api.get<unknown>(
      `/people/${encodeURIComponent(username)}/favorite-sites/${encodeURIComponent(siteId)}`
    );
    return isFavoriteSiteItem(response) ? response : assertUnexpectedResponse();
  }

  async createFavoriteSite(username: string, request: PersonFavoriteSiteWriteRequest): Promise<PersonFavoriteSiteItem> {
    const response = await api.post<unknown>(`/people/${encodeURIComponent(username)}/favorite-sites`, request);
    return isFavoriteSiteItem(response) ? response : assertUnexpectedResponse();
  }

  async deleteFavoriteSite(username: string, siteId: string): Promise<void> {
    await api.delete(`/people/${encodeURIComponent(username)}/favorite-sites/${encodeURIComponent(siteId)}`);
  }

  async getSiteMembershipRequests(username: string): Promise<PersonSiteMembershipRequestItem[]> {
    const response = await api.get<unknown>(
      `/people/${encodeURIComponent(username)}/site-membership-requests`
    );
    return assertArray(response, isSiteMembershipRequestItem);
  }

  async getVisibleSiteMembershipRequests(params: {
    siteId?: string;
    status?: string;
    requester?: string;
    page?: number;
    size?: number;
  } = {}): Promise<PageResponse<PersonSiteMembershipRequestItem>> {
    const response = await api.get<unknown>('/people/site-membership-requests', {
      params: {
        siteId: params.siteId || undefined,
        status: params.status || undefined,
        requester: params.requester || undefined,
        page: params.page ?? 0,
        size: params.size ?? 20,
      },
    });
    return assertPageResponse(response, isSiteMembershipRequestItem);
  }

  async createSiteMembershipRequest(
    username: string,
    request: PersonSiteMembershipRequestWriteRequest
  ): Promise<PersonSiteMembershipRequestItem> {
    const response = await api.post<unknown>(
      `/people/${encodeURIComponent(username)}/site-membership-requests`,
      request
    );
    return isSiteMembershipRequestItem(response) ? response : assertUnexpectedResponse();
  }

  async updateSiteMembershipRequest(
    username: string,
    siteId: string,
    request: PersonSiteMembershipRequestWriteRequest
  ): Promise<PersonSiteMembershipRequestItem> {
    const response = await api.put<unknown>(
      `/people/${encodeURIComponent(username)}/site-membership-requests/${encodeURIComponent(siteId)}`,
      request
    );
    return isSiteMembershipRequestItem(response) ? response : assertUnexpectedResponse();
  }

  async approveSiteMembershipRequest(
    username: string,
    siteId: string,
    payload?: { decisionComment?: string }
  ): Promise<PersonSiteMembershipRequestItem> {
    const response = await api.post<unknown>(
      `/people/${encodeURIComponent(username)}/site-membership-requests/${encodeURIComponent(siteId)}/approve`,
      payload || {}
    );
    return isSiteMembershipRequestItem(response) ? response : assertUnexpectedResponse();
  }

  async rejectSiteMembershipRequest(
    username: string,
    siteId: string,
    payload?: { decisionComment?: string }
  ): Promise<PersonSiteMembershipRequestItem> {
    const response = await api.post<unknown>(
      `/people/${encodeURIComponent(username)}/site-membership-requests/${encodeURIComponent(siteId)}/reject`,
      payload || {}
    );
    return isSiteMembershipRequestItem(response) ? response : assertUnexpectedResponse();
  }

  async withdrawSiteMembershipRequest(username: string, siteId: string): Promise<void> {
    await api.delete(`/people/${encodeURIComponent(username)}/site-membership-requests/${encodeURIComponent(siteId)}`);
  }

  async updateProfile(username: string, payload: PersonProfileUpdateRequest): Promise<PersonPreferences> {
    const response = await api.put<unknown>(`/people/${encodeURIComponent(username)}/profile`, payload);
    return assertPersonPreferences(response);
  }

  async updatePreferences(username: string, preferences: Record<string, any>): Promise<PersonPreferences> {
    const response = await api.put<unknown>(`/people/${encodeURIComponent(username)}/preferences`, { preferences });
    return assertPersonPreferences(response);
  }
}

const peopleService = new PeopleService();
export default peopleService;
