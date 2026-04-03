import api from './api';

export type ArchiveStatus = 'LIVE' | 'ARCHIVED' | 'RESTORING';
export type ArchiveStoreTier = 'HOT' | 'WARM' | 'COLD' | 'GLACIER';

export interface ArchiveMutationDto {
  nodeId: string;
  name: string;
  archiveStatus: ArchiveStatus;
  archiveStoreTier: ArchiveStoreTier;
  archivedDate?: string | null;
  archivedBy?: string | null;
  affectedNodeCount: number;
}

export interface ArchiveStatusDto {
  nodeId: string;
  name: string;
  nodeType: 'FOLDER' | 'DOCUMENT' | string;
  path: string;
  archiveStatus: ArchiveStatus;
  archiveStoreTier: ArchiveStoreTier;
  archivedDate?: string | null;
  archivedBy?: string | null;
}

export interface ArchivedNodeDto {
  nodeId: string;
  name: string;
  nodeType: 'FOLDER' | 'DOCUMENT' | string;
  path: string;
  size?: number | null;
  createdBy?: string | null;
  createdDate?: string | null;
  archiveStatus: ArchiveStatus;
  archiveStoreTier: ArchiveStoreTier;
  archivedDate?: string | null;
  archivedBy?: string | null;
}

export interface ArchivedNodePage {
  content: ArchivedNodeDto[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

class ContentArchiveService {
  async archiveNode(nodeId: string, storageTier: ArchiveStoreTier = 'COLD'): Promise<ArchiveMutationDto> {
    return api.post<ArchiveMutationDto>(`/nodes/${nodeId}/archive`, { storageTier });
  }

  async restoreNode(nodeId: string): Promise<ArchiveMutationDto> {
    return api.post<ArchiveMutationDto>(`/nodes/${nodeId}/restore`);
  }

  async getArchiveStatus(nodeId: string): Promise<ArchiveStatusDto> {
    return api.get<ArchiveStatusDto>(`/nodes/${nodeId}/archive-status`);
  }

  async listArchivedNodes(page = 0, size = 20): Promise<ArchivedNodePage> {
    return api.get<ArchivedNodePage>('/nodes/archived', { params: { page, size } });
  }
}

const contentArchiveService = new ContentArchiveService();
export default contentArchiveService;
