import api from './api';
import { User } from 'types';

export const USER_GROUP_UNEXPECTED_RESPONSE_MESSAGE =
  'User/Group endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isNumber = (value: unknown): value is number => (
  typeof value === 'number' && Number.isFinite(value)
);

const isStringOrNullish = (value: unknown): value is string | null | undefined => (
  value === null || value === undefined || typeof value === 'string'
);

const isBooleanOrNullish = (value: unknown): value is boolean | null | undefined => (
  value === null || value === undefined || typeof value === 'boolean'
);

const isStringArray = (value: unknown): value is string[] => (
  Array.isArray(value) && value.every((entry) => typeof entry === 'string')
);

export interface Group {
  id?: string;
  name: string;
  displayName?: string;
  description?: string;
  email?: string;
  enabled?: boolean;
  groupType?: string;
  users?: User[];
}

export interface UserPage {
  content: User[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface GroupPage {
  content: Group[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

const assertUnexpectedResponse = (): never => {
  throw new Error(USER_GROUP_UNEXPECTED_RESPONSE_MESSAGE);
};

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
  if (!isObject(value)) {
    return false;
  }
  if (typeof value.name !== 'string') {
    return false;
  }
  if (!isStringOrNullish(value.id)
    || !isStringOrNullish(value.displayName)
    || !isStringOrNullish(value.description)
    || !isStringOrNullish(value.email)
    || !isStringOrNullish(value.groupType)
    || !isBooleanOrNullish(value.enabled)) {
    return false;
  }
  if (value.users !== null && value.users !== undefined) {
    if (!Array.isArray(value.users) || !value.users.every(isUser)) {
      return false;
    }
  }
  return true;
};

const assertGroup = (value: unknown): Group => (
  isGroup(value) ? value : assertUnexpectedResponse()
);

const isUserPage = (value: unknown): value is UserPage => {
  if (!isObject(value) || !Array.isArray(value.content)) {
    return false;
  }
  return value.content.every(isUser)
    && isNumber(value.totalElements)
    && isNumber(value.totalPages)
    && isNumber(value.number)
    && isNumber(value.size);
};

const assertUserPage = (value: unknown): UserPage => (
  isUserPage(value) ? value : assertUnexpectedResponse()
);

const isGroupPage = (value: unknown): value is GroupPage => {
  if (!isObject(value) || !Array.isArray(value.content)) {
    return false;
  }
  return value.content.every(isGroup)
    && isNumber(value.totalElements)
    && isNumber(value.totalPages)
    && isNumber(value.number)
    && isNumber(value.size);
};

const assertGroupPage = (value: unknown): GroupPage => (
  isGroupPage(value) ? value : assertUnexpectedResponse()
);

class UserGroupService {
  async searchUsers(query: string): Promise<User[]> {
    const result = await api.get<unknown>('/users', {
      params: { query, page: 0, size: 20 },
    });
    return assertUserPage(result).content;
  }

  async listUsers(query = ''): Promise<User[]> {
    const result = await api.get<unknown>('/users', {
      params: { query, page: 0, size: 100 },
    });
    return assertUserPage(result).content;
  }

  async createUser(user: Partial<User> & { username: string; email: string; password: string }): Promise<User> {
    const result = await api.post<unknown>('/users', user);
    return assertUser(result);
  }

  async updateUser(username: string, updates: Partial<User>): Promise<User> {
    const result = await api.put<unknown>(`/users/${username}`, updates);
    return assertUser(result);
  }

  async listGroups(): Promise<Group[]> {
    const result = await api.get<unknown>('/groups', {
      params: { page: 0, size: 100 },
    });
    return assertGroupPage(result).content;
  }

  async createGroup(name: string, displayName?: string): Promise<Group> {
    const result = await api.post<unknown>('/groups', { name, displayName });
    return assertGroup(result);
  }

  async deleteGroup(name: string): Promise<void> {
    return api.delete<void>(`/groups/${name}`);
  }

  async addUserToGroup(groupName: string, username: string): Promise<void> {
    return api.post<void>(`/groups/${groupName}/members/${username}`);
  }

  async removeUserFromGroup(groupName: string, username: string): Promise<void> {
    return api.delete<void>(`/groups/${groupName}/members/${username}`);
  }
}

const userGroupService = new UserGroupService();
export default userGroupService;
