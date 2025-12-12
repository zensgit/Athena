import api from './api';

export type SharePermission = 'VIEW' | 'COMMENT' | 'EDIT';

export interface ShareLink {
  id: string;
  token: string;
  nodeId: string;
  nodeName: string;
  createdBy: string;
  createdAt: string;
  expiryDate?: string;
  maxAccessCount?: number;
  accessCount: number;
  active: boolean;
  name?: string;
  permissionLevel: SharePermission;
  lastAccessedAt?: string;
  passwordProtected: boolean;
  hasIpRestrictions: boolean;
  isValid: boolean;
}

export interface CreateShareLinkRequest {
  name?: string;
  expiryDate?: string | null;
  maxAccessCount?: number | null;
  permissionLevel?: SharePermission;
  password?: string | null;
  allowedIps?: string | null;
}

export interface UpdateShareLinkRequest extends CreateShareLinkRequest {
  active?: boolean;
}

class ShareLinkService {
  async getLinksForNode(nodeId: string): Promise<ShareLink[]> {
    return api.get<ShareLink[]>(`/share/nodes/${nodeId}`);
  }

  async getMyLinks(): Promise<ShareLink[]> {
    return api.get<ShareLink[]>('/share/my');
  }

  async createLink(nodeId: string, data: CreateShareLinkRequest): Promise<ShareLink> {
    return api.post<ShareLink>(`/share/nodes/${nodeId}`, data);
  }

  async updateLink(token: string, data: UpdateShareLinkRequest): Promise<ShareLink> {
    return api.put<ShareLink>(`/share/${token}`, data);
  }

  async deactivateLink(token: string): Promise<void> {
    await api.post<void>(`/share/${token}/deactivate`);
  }

  async deleteLink(token: string): Promise<void> {
    await api.delete<void>(`/share/${token}`);
  }
}

const shareLinkService = new ShareLinkService();
export default shareLinkService;

