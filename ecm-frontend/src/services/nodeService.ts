import api from './api';
import { Node, SearchCriteria, Version, Permission } from '@/types';

interface CreateFolderRequest {
  name: string;
  properties?: Record<string, any>;
}

interface UpdateNodeRequest {
  properties: Record<string, any>;
}

interface MoveNodeRequest {
  targetParentId: string;
}

interface CopyNodeRequest {
  targetParentId: string;
  deepCopy?: boolean;
}

class NodeService {
  async getNode(nodeId: string): Promise<Node> {
    return api.get<Node>(`/nodes/${nodeId}`);
  }

  async getChildren(nodeId: string, sortBy = 'name', ascending = true): Promise<Node[]> {
    return api.get<Node[]>(`/nodes/${nodeId}/children`, {
      params: { sortBy, ascending },
    });
  }

  async createFolder(parentId: string, request: CreateFolderRequest): Promise<Node> {
    return api.post<Node>(`/nodes/${parentId}/folders`, request);
  }

  async uploadDocument(parentId: string, file: File, properties?: Record<string, any>, onProgress?: (progress: number) => void): Promise<Node> {
    const formData = new FormData();
    formData.append('file', file);
    
    if (properties) {
      Object.entries(properties).forEach(([key, value]) => {
        formData.append(key, value);
      });
    }

    return api.uploadFile(`/nodes/${parentId}/documents`, file, onProgress);
  }

  async downloadDocument(nodeId: string): Promise<void> {
    const node = await this.getNode(nodeId);
    return api.downloadFile(`/nodes/${nodeId}/content`, node.name);
  }

  async updateNode(nodeId: string, properties: Record<string, any>): Promise<Node> {
    return api.put<Node>(`/nodes/${nodeId}`, { properties });
  }

  async moveNode(nodeId: string, targetParentId: string): Promise<Node> {
    return api.post<Node>(`/nodes/${nodeId}/move`, { targetParentId });
  }

  async copyNode(nodeId: string, targetParentId: string, deepCopy = true): Promise<Node> {
    return api.post<Node>(`/nodes/${nodeId}/copy`, { targetParentId, deepCopy });
  }

  async deleteNode(nodeId: string): Promise<void> {
    return api.delete(`/nodes/${nodeId}`);
  }

  async searchNodes(criteria: SearchCriteria): Promise<Node[]> {
    return api.post<Node[]>('/nodes/search', criteria);
  }

  async addAspect(nodeId: string, aspect: string, properties?: Record<string, any>): Promise<Node> {
    return api.post<Node>(`/nodes/${nodeId}/aspects/${aspect}`, properties);
  }

  async removeAspect(nodeId: string, aspect: string): Promise<Node> {
    return api.delete<Node>(`/nodes/${nodeId}/aspects/${aspect}`);
  }

  async getVersionHistory(nodeId: string): Promise<Version[]> {
    return api.get<Version[]>(`/nodes/${nodeId}/versions`);
  }

  async createVersion(nodeId: string, file: File, comment?: string, major = false): Promise<Version> {
    const formData = new FormData();
    formData.append('file', file);
    if (comment) formData.append('comment', comment);
    formData.append('major', major.toString());

    return api.post<Version>(`/nodes/${nodeId}/versions`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  }

  async downloadVersion(nodeId: string, versionId: string): Promise<void> {
    const version = await api.get<Version>(`/nodes/${nodeId}/versions/${versionId}`);
    const node = await this.getNode(nodeId);
    const filename = `${node.name}_v${version.versionLabel}`;
    return api.downloadFile(`/nodes/${nodeId}/versions/${versionId}/content`, filename);
  }

  async revertToVersion(nodeId: string, versionId: string): Promise<Node> {
    return api.post<Node>(`/nodes/${nodeId}/versions/${versionId}/revert`);
  }

  async getPermissions(nodeId: string): Promise<Record<string, Permission[]>> {
    return api.get<Record<string, Permission[]>>(`/nodes/${nodeId}/permissions`);
  }

  async setPermission(nodeId: string, principal: string, permission: string, allowed: boolean): Promise<void> {
    return api.post(`/nodes/${nodeId}/permissions`, { principal, permission, allowed });
  }

  async setInheritPermissions(nodeId: string, inherit: boolean): Promise<void> {
    return api.put(`/nodes/${nodeId}/permissions/inherit`, null, {
      params: { inherit },
    });
  }
}

export default new NodeService();