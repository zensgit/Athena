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

export interface ArchivePolicyDto {
  policyId: string;
  folderId: string;
  folderName: string;
  folderPath: string;
  enabled: boolean;
  inactivityDays: number;
  storageTier: ArchiveStoreTier;
  includeSubfolders: boolean;
  maxCandidatesPerRun: number;
  lastDryRunAt?: string | null;
  lastExecutedAt?: string | null;
  lastCandidateCount?: number | null;
  lastArchivedNodeCount?: number | null;
  lastError?: string | null;
}

export interface ArchivePolicyRequest {
  enabled: boolean;
  inactivityDays: number;
  storageTier: ArchiveStoreTier;
  includeSubfolders: boolean;
  maxCandidatesPerRun: number;
}

export interface ArchivePolicyCandidateDto {
  nodeId: string;
  name: string;
  nodeType: string;
  path: string;
  activityDate?: string | null;
}

export interface ArchivePolicyDryRunDto {
  folderId: string;
  folderName: string;
  cutoffDate: string;
  storageTier: ArchiveStoreTier;
  includeSubfolders: boolean;
  maxCandidatesPerRun: number;
  candidateCount: number;
  candidates: ArchivePolicyCandidateDto[];
}

export interface ArchivePolicyExecutionDto {
  folderId: string;
  folderName: string;
  candidateCount: number;
  archivedNodeCount: number;
  failureCount: number;
  failures: string[];
  error?: string | null;
}

export interface ArchivePolicyBatchExecutionDto {
  executedPolicies: number;
  totalCandidates: number;
  archivedNodeCount: number;
  failureCount: number;
  results: ArchivePolicyExecutionDto[];
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

  async getArchivePolicy(folderId: string): Promise<ArchivePolicyDto> {
    return api.get<ArchivePolicyDto>(`/folders/${folderId}/archive-policy`);
  }

  async upsertArchivePolicy(folderId: string, payload: ArchivePolicyRequest): Promise<ArchivePolicyDto> {
    return api.put<ArchivePolicyDto>(`/folders/${folderId}/archive-policy`, payload);
  }

  async deleteArchivePolicy(folderId: string): Promise<void> {
    await api.delete<void>(`/folders/${folderId}/archive-policy`);
  }

  async dryRunArchivePolicy(folderId: string, payload?: ArchivePolicyRequest): Promise<ArchivePolicyDryRunDto> {
    return api.post<ArchivePolicyDryRunDto>(`/folders/${folderId}/archive-policy/dry-run`, payload ?? {});
  }

  async executeArchivePolicy(folderId: string): Promise<ArchivePolicyExecutionDto> {
    return api.post<ArchivePolicyExecutionDto>(`/folders/${folderId}/archive-policy/execute`);
  }

  async listArchivePolicies(): Promise<ArchivePolicyDto[]> {
    return api.get<ArchivePolicyDto[]>('/archive-policies');
  }

  async runArchivePolicies(): Promise<ArchivePolicyBatchExecutionDto> {
    return api.post<ArchivePolicyBatchExecutionDto>('/archive-policies/run');
  }
}

const contentArchiveService = new ContentArchiveService();
export default contentArchiveService;
