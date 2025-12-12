import api from './api';
import { Node, SearchCriteria, Version, Permission, PermissionType } from 'types';

interface CreateFolderRequest {
  name: string;
  properties?: Record<string, any>;
}

interface FolderResponse {
  id: string;
  name: string;
  description?: string;
  path: string;
  parentId?: string;
  folderType: string;
  inheritPermissions?: boolean;
  createdBy: string;
  createdDate: string;
  lastModifiedBy?: string;
  lastModifiedDate?: string;
}

// API response node structure (different from frontend Node type)
interface ApiNodeResponse {
  id: string;
  name: string;
  description?: string;
  path: string;
  nodeType: 'FOLDER' | 'DOCUMENT';
  parentId?: string;
  size?: number;
  createdBy: string;
  createdDate: string;
  lastModifiedBy?: string;
  lastModifiedDate?: string;
  contentType?: string;
}

class NodeService {
  private folderToNode(folder: FolderResponse): Node {
    return {
      id: folder.id,
      name: folder.name,
      path: folder.path,
      nodeType: 'FOLDER',
      parentId: folder.parentId,
      properties: { description: folder.description },
      aspects: [],
      created: folder.createdDate,
      modified: folder.lastModifiedDate || folder.createdDate,
      creator: folder.createdBy,
      modifier: folder.lastModifiedBy || folder.createdBy,
      inheritPermissions: folder.inheritPermissions,
    };
  }

  private apiNodeToNode(apiNode: ApiNodeResponse): Node {
    return {
      id: apiNode.id,
      name: apiNode.name,
      path: apiNode.path,
      nodeType: apiNode.nodeType,
      parentId: apiNode.parentId,
      properties: { description: apiNode.description },
      aspects: [],
      created: apiNode.createdDate,
      modified: apiNode.lastModifiedDate || apiNode.createdDate,
      creator: apiNode.createdBy,
      modifier: apiNode.lastModifiedBy || apiNode.createdBy,
      size: apiNode.size,
      contentType: apiNode.contentType,
    };
  }

  async getNode(nodeId: string): Promise<Node> {
    // Handle special "root" case by fetching first root folder
    if (nodeId === 'root') {
      const roots = await api.get<FolderResponse[]>('/folders/roots');
      if (roots && roots.length > 0) {
        return this.folderToNode(roots[0]);
      }
      throw new Error('No root folder found');
    }
    // For regular node IDs, try folder first, then fall back to node
    try {
      const folder = await api.get<FolderResponse>(`/folders/${nodeId}`);
      return this.folderToNode(folder);
    } catch {
      // Fall back to generic node endpoint if folder not found
      return api.get<Node>(`/nodes/${nodeId}`);
    }
  }

  async getChildren(nodeId: string, sortBy = 'name', ascending = true): Promise<Node[]> {
    // Handle special "root" case
    if (nodeId === 'root') {
      const roots = await api.get<FolderResponse[]>('/folders/roots');
      if (roots && roots.length > 0) {
        nodeId = roots[0].id;
      } else {
        return [];
      }
    }
    // Use folder contents endpoint
    try {
      const response = await api.get<{ content: ApiNodeResponse[] }>(`/folders/${nodeId}/contents`);
      const apiNodes = response.content || [];
      return apiNodes.map(node => this.apiNodeToNode(node));
    } catch {
      // Fall back to node children endpoint
      return api.get<Node[]>(`/nodes/${nodeId}/children`, {
        params: { sortBy, ascending },
      });
    }
  }

  async createFolder(parentId: string, request: CreateFolderRequest): Promise<Node> {
    // Handle special "root" case
    if (parentId === 'root') {
      const roots = await api.get<FolderResponse[]>('/folders/roots');
      if (roots && roots.length > 0) {
        parentId = roots[0].id;
      } else {
        throw new Error('No root folder found');
      }
    }
    const folder = await api.post<FolderResponse>('/folders', {
      name: request.name,
      parentId: parentId,
      folderType: 'GENERAL',
      inheritPermissions: true,
    });
    return this.folderToNode(folder);
  }

  async uploadDocument(parentId: string, file: File, properties?: Record<string, any>, onProgress?: (progress: number) => void): Promise<Node> {
    // Handle special "root" case
    if (parentId === 'root') {
      const roots = await api.get<FolderResponse[]>('/folders/roots');
      if (roots && roots.length > 0) {
        parentId = roots[0].id;
      } else {
        throw new Error('No root folder found');
      }
    }

    // Use the v1 upload endpoint with folderId parameter
    const url = `/documents/upload?folderId=${parentId}`;
    const response = await api.uploadFile(url, file, onProgress);

    // Map the upload response to Node format
    return {
      id: response.documentId,
      name: file.name,
      path: '',
      nodeType: 'DOCUMENT',
      parentId: parentId,
      properties: {},
      aspects: [],
      created: new Date().toISOString(),
      modified: new Date().toISOString(),
      creator: '',
      modifier: '',
      size: file.size,
      contentType: file.type,
    };
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
    // Use advanced search to leverage filters/highlights
    const payload = {
      query: criteria.name || criteria.contentType || '',
      filters: {
        mimeTypes: criteria.contentType ? [criteria.contentType] : undefined,
        createdBy: criteria.createdBy || undefined,
        tags: criteria.tags,
        categories: criteria.categories,
        minSize: criteria.minSize,
        maxSize: criteria.maxSize,
        dateFrom: criteria.createdFrom,
        dateTo: criteria.createdTo,
        modifiedFrom: criteria.modifiedFrom,
        modifiedTo: criteria.modifiedTo,
        path: criteria.path,
      },
      pageable: { page: 0, size: 50 },
    };

    const response = await api.post<{ content: any[] }>('/search/advanced', payload);
    return response.content.map((item) => ({
      id: item.id,
      name: item.name,
      path: item.path,
      nodeType: item.nodeType || 'DOCUMENT',
      parentId: item.parentId,
      properties: { description: item.description },
      aspects: [],
      created: item.createdDate,
      modified: item.lastModifiedDate || item.createdDate,
      creator: item.createdBy,
      modifier: item.lastModifiedBy || item.createdBy,
      size: item.fileSize,
      contentType: item.mimeType,
      description: item.description,
      highlights: item.highlights,
      tags: item.tags,
      categories: item.categories,
      score: item.score,
    } as Node));
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
    const perms = await api.get<Permission[]>(`/security/nodes/${nodeId}/permissions`);
    return perms.reduce<Record<string, Permission[]>>((acc, perm) => {
      const key = perm.authorityType === 'GROUP' ? `GROUP_${perm.authority}` : perm.authority;
      acc[key] = acc[key] || [];
      acc[key].push(perm);
      return acc;
    }, {});
  }

  async setPermission(
    nodeId: string,
    authority: string,
    authorityType: Permission['authorityType'],
    permissionType: PermissionType,
    allowed: boolean
  ): Promise<void> {
    return api.post(`/security/nodes/${nodeId}/permissions`, null, {
      params: { authority, authorityType, permissionType, allowed },
    });
  }

  async setInheritPermissions(nodeId: string, inherit: boolean): Promise<void> {
    return api.post(`/security/nodes/${nodeId}/inherit-permissions`, null, {
      params: { inherit },
    });
  }

  async removePermission(nodeId: string, authority: string, permissionType: PermissionType): Promise<void> {
    return api.delete(`/security/nodes/${nodeId}/permissions`, {
      params: { authority, permissionType },
    });
  }
}

const nodeService = new NodeService();
export default nodeService;
