import api from './api';
import { User } from 'types';

interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

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

class UserGroupService {
  async searchUsers(query: string): Promise<User[]> {
    const res = await api.get<PageResponse<User>>('/users', {
      params: { query, page: 0, size: 20 },
    });
    return res.content || [];
  }

  async listUsers(query = ''): Promise<User[]> {
    const res = await api.get<PageResponse<User>>('/users', {
      params: { query, page: 0, size: 100 },
    });
    return res.content || [];
  }

  async createUser(user: Partial<User> & { username: string; email: string; password: string }): Promise<User> {
    return api.post<User>('/users', user);
  }

  async updateUser(username: string, updates: Partial<User>): Promise<User> {
    return api.put<User>(`/users/${username}`, updates);
  }

  async listGroups(): Promise<Group[]> {
    const res = await api.get<PageResponse<Group>>('/groups', {
      params: { page: 0, size: 100 },
    });
    return res.content || [];
  }

  async createGroup(name: string, displayName?: string): Promise<Group> {
    return api.post<Group>('/groups', { name, displayName });
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

