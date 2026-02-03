import api from './api';
import {
  Node,
  SearchCriteria,
  Version,
  Permission,
  PermissionType,
  PdfAnnotation,
  PdfAnnotationState,
} from 'types';

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
  correspondentId?: string;
  correspondentName?: string;
}

interface ApiNodeDetailsResponse {
  id: string;
  name: string;
  description?: string;
  path: string;
  nodeType: 'FOLDER' | 'DOCUMENT';
  parentId?: string;
  size?: number;
  contentType?: string;
  currentVersionLabel?: string;
  correspondentId?: string;
  correspondentName?: string;
  properties?: Record<string, any>;
  metadata?: Record<string, any>;
  aspects?: string[];
  tags?: string[];
  categories?: string[];
  inheritPermissions?: boolean;
  locked?: boolean;
  lockedBy?: string;
  createdBy: string;
  createdDate: string;
  lastModifiedBy?: string;
  lastModifiedDate?: string;
}

interface ApiVersionResponse {
  id: string;
  documentId?: string;
  versionLabel: string;
  comment?: string;
  createdDate: string;
  creator: string;
  size: number;
  major: boolean;
  mimeType?: string;
  contentHash?: string;
  contentId?: string;
  status?: string;
}

interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

interface UploadResponse {
  success: boolean;
  documentId?: string;
  contentId?: string;
  filename?: string;
  processingTimeMs?: number;
  errors?: Record<string, string>;
}

export interface SearchDiagnostics {
  username: string | null;
  admin: boolean;
  readFilterApplied: boolean;
  authorityCount: number;
  authoritySample: string[];
  note?: string | null;
  generatedAt?: string | null;
}

export interface PermissionSetMetadata {
  name: string;
  label: string;
  description?: string | null;
  order?: number | null;
  permissions: PermissionType[];
}

class NodeService {
  private pickPrimaryRoot(roots: FolderResponse[]): FolderResponse {
    if (!roots || roots.length === 0) {
      throw new Error('No root folder found');
    }

    const isSystemRoot = (folder: FolderResponse) => folder.folderType?.toUpperCase() === 'SYSTEM';
    const isRootPath = (folder: FolderResponse) => folder.path === '/Root' || folder.path === '/root';
    const isRootName = (folder: FolderResponse) => folder.name === 'Root' || folder.name === 'root';
    const createdDateAsc = (a: FolderResponse, b: FolderResponse) => a.createdDate.localeCompare(b.createdDate);

    const preferred = roots.filter(isSystemRoot).filter(isRootPath).filter(isRootName);
    if (preferred.length > 0) {
      return [...preferred].sort(createdDateAsc)[0];
    }

    const systemByPath = roots.filter(isSystemRoot).filter(isRootPath);
    if (systemByPath.length > 0) {
      return [...systemByPath].sort(createdDateAsc)[0];
    }

    const system = roots.filter(isSystemRoot);
    if (system.length > 0) {
      return [...system].sort(createdDateAsc)[0];
    }

    return [...roots].sort(createdDateAsc)[0];
  }

  private async getRootFolder(): Promise<FolderResponse> {
    const roots = await api.get<FolderResponse[]>('/folders/roots');
    return this.pickPrimaryRoot(roots);
  }

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
      correspondentId: apiNode.correspondentId,
      correspondent: apiNode.correspondentName,
    };
  }

  private apiNodeDetailsToNode(apiNode: ApiNodeDetailsResponse): Node {
    const createdBy = apiNode.createdBy || '';
    const createdDate = apiNode.createdDate || new Date().toISOString();
    return {
      id: apiNode.id,
      name: apiNode.name,
      path: apiNode.path,
      nodeType: apiNode.nodeType,
      parentId: apiNode.parentId,
      properties: apiNode.properties || { description: apiNode.description },
      metadata: apiNode.metadata || {},
      aspects: apiNode.aspects || [],
      created: createdDate,
      modified: apiNode.lastModifiedDate || createdDate,
      creator: createdBy,
      modifier: apiNode.lastModifiedBy || createdBy,
      size: apiNode.size,
      contentType: apiNode.contentType,
      currentVersionLabel: apiNode.currentVersionLabel,
      description: apiNode.description,
      tags: apiNode.tags,
      categories: apiNode.categories,
      correspondentId: apiNode.correspondentId,
      correspondent: apiNode.correspondentName,
      inheritPermissions: apiNode.inheritPermissions,
    };
  }

  async getFolderByPath(path: string): Promise<Node> {
    const folder = await api.get<FolderResponse>('/folders/path', { params: { path } });
    return this.folderToNode(folder);
  }

  async getNode(nodeId: string): Promise<Node> {
    // Handle special "root" case by fetching first root folder
    if (nodeId === 'root') {
      const root = await this.getRootFolder();
      return this.folderToNode(root);
    }
    // /nodes/{id} works for both folders and documents; avoid noisy 404s from probing /folders/{id}.
    const node = await api.get<ApiNodeDetailsResponse>(`/nodes/${nodeId}`);
    return this.apiNodeDetailsToNode(node);
  }

  async getChildren(nodeId: string, sortBy = 'name', ascending = true): Promise<Node[]> {
    // Handle special "root" case
    if (nodeId === 'root') {
      const root = await this.getRootFolder();
      nodeId = root.id;
    }
    // Use folder contents endpoint (paginated in backend; request a large page size for tree/list views)
    try {
      const sortDirection = ascending ? 'asc' : 'desc';
      const response = await api.get<{ content: ApiNodeResponse[] }>(`/folders/${nodeId}/contents`, {
        params: {
          page: 0,
          size: 1000,
          sort: `${sortBy},${sortDirection}`,
        },
      });
      const apiNodes = response.content || [];
      return apiNodes.map(node => this.apiNodeToNode(node));
    } catch {
      // Fall back to node children endpoint
      const response = await api.get<{ content: ApiNodeDetailsResponse[] }>(`/nodes/${nodeId}/children`, {
        params: { sortBy, ascending },
      });
      const apiNodes = response.content || [];
      return apiNodes.map((node) => this.apiNodeDetailsToNode(node));
    }
  }

  async getChildrenPage(
    nodeId: string,
    sortBy = 'name',
    ascending = true,
    page = 0,
    size = 50
  ): Promise<{ nodes: Node[]; total: number }> {
    if (nodeId === 'root') {
      const root = await this.getRootFolder();
      nodeId = root.id;
    }
    try {
      const sortDirection = ascending ? 'asc' : 'desc';
      const response = await api.get<{
        content: ApiNodeResponse[];
        totalElements?: number;
        number?: number;
        size?: number;
      }>(`/folders/${nodeId}/contents`, {
        params: {
          page,
          size,
          sort: `${sortBy},${sortDirection}`,
        },
      });
      const apiNodes = response.content || [];
      const total = response.totalElements ?? apiNodes.length;
      return { nodes: apiNodes.map((node) => this.apiNodeToNode(node)), total };
    } catch {
      const response = await api.get<{ content: ApiNodeDetailsResponse[] }>(`/nodes/${nodeId}/children`, {
        params: { sortBy, ascending },
      });
      const apiNodes = response.content || [];
      return { nodes: apiNodes.map((node) => this.apiNodeDetailsToNode(node)), total: apiNodes.length };
    }
  }

  async createFolder(parentId: string, request: CreateFolderRequest): Promise<Node> {
    // Handle special "root" case
    if (parentId === 'root') {
      const root = await this.getRootFolder();
      parentId = root.id;
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
      const root = await this.getRootFolder();
      parentId = root.id;
    }

    const url = `/documents/upload?folderId=${encodeURIComponent(parentId)}`;
    const response = (await api.uploadFile(url, file, onProgress)) as UploadResponse;
    if (!response?.success || !response.documentId) {
      throw new Error('Upload failed');
    }
    return this.getNode(response.documentId);
  }

  async downloadDocument(nodeId: string): Promise<void> {
    const node = await this.getNode(nodeId);
    return api.downloadFile(`/nodes/${nodeId}/content`, node.name);
  }

  async downloadNodesAsZip(nodeIds: string[], name = 'archive'): Promise<void> {
    if (!nodeIds.length) {
      return;
    }

    const idsParam = nodeIds.map((id) => encodeURIComponent(id)).join(',');
    const safeName = name || 'archive';
    const url = `/nodes/download/batch?ids=${idsParam}&name=${encodeURIComponent(safeName)}`;
    const filename = `${safeName}.zip`;
    return api.downloadFile(url, filename);
  }

  async updateNode(nodeId: string, updates: Record<string, any>): Promise<Node> {
    const updated = await api.patch<ApiNodeDetailsResponse>(`/nodes/${nodeId}`, updates);
    return this.apiNodeDetailsToNode(updated);
  }

  async moveNode(nodeId: string, targetParentId: string): Promise<Node> {
    const moved = await api.post<ApiNodeResponse>(`/folders/${targetParentId}/move`, {
      nodeId,
    });
    return this.apiNodeToNode(moved);
  }

  async copyNode(nodeId: string, targetParentId: string, deepCopy = true, newName?: string): Promise<Node> {
    const copied = await api.post<ApiNodeResponse>(`/folders/${targetParentId}/copy`, {
      nodeId,
      newName: newName || null,
      deep: deepCopy,
    });
    return this.apiNodeToNode(copied);
  }

  async deleteNode(nodeId: string): Promise<void> {
    return api.delete(`/nodes/${nodeId}`);
  }

  async searchNodes(criteria: SearchCriteria): Promise<{ nodes: Node[]; total: number }> {
    const query = (criteria.name || '').trim();
    const filters: Record<string, any> = {};
    const page = criteria.page ?? 0;
    const size = criteria.size ?? 50;

    if (criteria.mimeTypes?.length) {
      filters.mimeTypes = criteria.mimeTypes;
    } else if (criteria.contentType) {
      filters.mimeTypes = [criteria.contentType];
    }
    if (criteria.createdByList?.length) {
      filters.createdByList = criteria.createdByList;
    } else if (criteria.createdBy) {
      filters.createdBy = criteria.createdBy;
    }
    if (criteria.tags?.length) {
      filters.tags = criteria.tags;
    }
    if (criteria.categories?.length) {
      filters.categories = criteria.categories;
    }
    if (criteria.correspondents?.length) {
      filters.correspondents = criteria.correspondents;
    }
    if (criteria.minSize !== undefined) {
      filters.minSize = criteria.minSize;
    }
    if (criteria.maxSize !== undefined) {
      filters.maxSize = criteria.maxSize;
    }
    if (criteria.createdFrom) {
      filters.dateFrom = criteria.createdFrom;
    }
    if (criteria.createdTo) {
      filters.dateTo = criteria.createdTo;
    }
    if (criteria.modifiedFrom) {
      filters.modifiedFrom = criteria.modifiedFrom;
    }
    if (criteria.modifiedTo) {
      filters.modifiedTo = criteria.modifiedTo;
    }
    if (criteria.path) {
      filters.path = criteria.path;
    }

    const hasFilters = Object.keys(filters).length > 0;

    // Fast path: for simple name-only searches, use the dedicated full-text endpoint.
    // It handles punctuation (e.g. hyphens) more reliably than the Criteria-based advanced endpoint.
    const response = !hasFilters && query
      ? await api.get<{ content: any[]; totalElements?: number }>('/search', {
          params: {
            q: query,
            page,
            size,
            sortBy: criteria.sortBy,
            sortDirection: criteria.sortDirection,
          },
        })
      : await api.post<{ content: any[]; totalElements?: number }>('/search/advanced', {
          query,
          filters,
          sortBy: criteria.sortBy,
          sortDirection: criteria.sortDirection,
          pageable: { page, size },
        });

    const nodes = (response.content || []).map((item) => this.mapSearchItemToNode(item));

    return { nodes, total: response.totalElements ?? nodes.length };
  }

  async findSimilar(documentId: string, maxResults = 5): Promise<Node[]> {
    const results = await api.get<any[]>(`/search/similar/${documentId}`, {
      params: { maxResults },
    });
    return (results || []).map((item) => this.mapSearchItemToNode(item));
  }

  async getSearchFacets(query = ''): Promise<Record<string, { value: string; count: number }[]>> {
    return api.get<Record<string, { value: string; count: number }[]>>('/search/facets', {
      params: { q: query },
    });
  }

  async getSuggestedFilters(
    query = ''
  ): Promise<Array<{ field: string; label: string; value: string; count?: number }>> {
    return api.get<Array<{ field: string; label: string; value: string; count?: number }>>(
      '/search/filters/suggested',
      { params: { q: query } }
    );
  }

  async getSpellcheckSuggestions(query = '', limit = 5): Promise<string[]> {
    return api.get<string[]>('/search/spellcheck', { params: { q: query, limit } });
  }

  async getSearchDiagnostics(): Promise<SearchDiagnostics> {
    return api.get<SearchDiagnostics>('/search/diagnostics');
  }

  private mapSearchItemToNode(item: any): Node {
    const inferredNodeType = item.mimeType || item.fileSize
      ? 'DOCUMENT'
      : (item.nodeType === 'FOLDER' || item.nodeType === 'DOCUMENT' ? item.nodeType : 'FOLDER');

    return ({
      id: item.id,
      name: item.name,
      path: item.path,
      nodeType: inferredNodeType,
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
      correspondent: item.correspondent,
      previewStatus: item.previewStatus,
      previewFailureReason: item.previewFailureReason,
      score: item.score,
    } as Node);
  }

  async addAspect(nodeId: string, aspect: string, properties?: Record<string, any>): Promise<Node> {
    return api.post<Node>(`/nodes/${nodeId}/aspects/${aspect}`, properties);
  }

  async removeAspect(nodeId: string, aspect: string): Promise<Node> {
    return api.delete<Node>(`/nodes/${nodeId}/aspects/${aspect}`);
  }

  async getVersionHistory(nodeId: string): Promise<Version[]> {
    const versions = await api.get<ApiVersionResponse[]>(`/documents/${nodeId}/versions`);
    return versions.map((version) => ({
      id: version.id,
      documentId: version.documentId || nodeId,
      versionLabel: version.versionLabel,
      comment: version.comment,
      created: version.createdDate,
      creator: version.creator,
      size: version.size,
      isMajor: version.major,
      mimeType: version.mimeType,
      contentHash: version.contentHash,
      contentId: version.contentId,
      status: version.status,
    }));
  }

  async getVersionHistoryPage(
    nodeId: string,
    page = 0,
    size = 20,
    majorOnly = false
  ): Promise<{
    versions: Version[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
  }> {
    const response = await api.get<PageResponse<ApiVersionResponse>>(
      `/documents/${nodeId}/versions/paged`,
      { params: { page, size, majorOnly } }
    );
    const versions = response.content.map((version) => ({
      id: version.id,
      documentId: version.documentId || nodeId,
      versionLabel: version.versionLabel,
      comment: version.comment,
      created: version.createdDate,
      creator: version.creator,
      size: version.size,
      isMajor: version.major,
      mimeType: version.mimeType,
      contentHash: version.contentHash,
      contentId: version.contentId,
      status: version.status,
    }));
    return {
      versions,
      page: response.number,
      size: response.size,
      totalElements: response.totalElements,
      totalPages: response.totalPages,
    };
  }

  async createVersion(nodeId: string, file: File, comment?: string, major = false): Promise<Version> {
    const formData = new FormData();
    formData.append('file', file);
    if (comment) formData.append('comment', comment);
    formData.append('majorVersion', major.toString());

    // Reuse check-in endpoint to create a new version (backend persists via VersionService).
    await api.post(`/documents/${nodeId}/checkin`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    const history = await this.getVersionHistory(nodeId);
    if (history.length === 0) {
      throw new Error('Version creation succeeded but version history is empty');
    }
    return history[0];
  }

  async downloadVersion(nodeId: string, versionId: string): Promise<void> {
    const node = await this.getNode(nodeId);
    const versions = await this.getVersionHistory(nodeId);
    const version = versions.find((v) => v.id === versionId);
    const suffix = version?.versionLabel ? `_v${version.versionLabel}` : `_v${versionId}`;
    return api.downloadFile(`/documents/${nodeId}/versions/${versionId}/download`, `${node.name}${suffix}`);
  }

  async revertToVersion(nodeId: string, versionId: string): Promise<Node> {
    const node = await api.post<ApiNodeDetailsResponse>(`/documents/${nodeId}/versions/${versionId}/revert`);
    return this.apiNodeDetailsToNode(node);
  }

  async getPdfAnnotations(nodeId: string): Promise<PdfAnnotationState> {
    return api.get<PdfAnnotationState>(`/documents/${nodeId}/annotations`);
  }

  async savePdfAnnotations(nodeId: string, annotations: PdfAnnotation[]): Promise<PdfAnnotationState> {
    return api.post<PdfAnnotationState>(`/documents/${nodeId}/annotations`, { annotations });
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

  async getPermissionSets(): Promise<Record<string, PermissionType[]>> {
    return api.get<Record<string, PermissionType[]>>('/security/permission-sets');
  }

  async getPermissionSetMetadata(): Promise<PermissionSetMetadata[]> {
    return api.get<PermissionSetMetadata[]>('/security/permission-sets/metadata');
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

  async applyPermissionSet(
    nodeId: string,
    authority: string,
    authorityType: Permission['authorityType'],
    permissionSet: string,
    replace = false
  ): Promise<void> {
    return api.post(`/security/nodes/${nodeId}/permission-sets`, null, {
      params: { authority, authorityType, permissionSet, replace },
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
