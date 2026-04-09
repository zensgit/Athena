import api from './api';

export type TransportType = 'LOOPBACK' | 'ATHENA_HTTP';
export type AuthType = 'NONE' | 'BASIC' | 'BEARER';
export type VerificationStatus = 'NEVER_VERIFIED' | 'VERIFIED' | 'FAILED';
export type ReplicationJobStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';

export interface TransferTargetDto {
  id: string;
  name: string;
  description?: string | null;
  transportType: TransportType;
  targetFolderId: string;
  targetFolderName?: string | null;
  endpointUrl?: string | null;
  endpointPath?: string | null;
  authType: AuthType;
  authUsername?: string | null;
  authSecretConfigured: boolean;
  enabled: boolean;
  verificationStatus: VerificationStatus;
  verificationMessage?: string | null;
  lastVerifiedAt?: string | null;
  createdAt: string;
  updatedAt?: string | null;
}

export interface TransferTargetMutationRequest {
  name: string;
  description?: string;
  transportType: TransportType;
  targetFolderId: string;
  endpointUrl?: string;
  endpointPath?: string;
  authType?: AuthType;
  authUsername?: string;
  authSecret?: string;
  enabled?: boolean;
}

export interface ReplicationDefinitionDto {
  id: string;
  name: string;
  description?: string | null;
  sourceNodeId: string;
  sourceNodeName?: string | null;
  transferTargetId: string;
  transferTargetName?: string | null;
  includeChildren: boolean;
  enabled: boolean;
  lastRunAt?: string | null;
  createdAt: string;
  updatedAt?: string | null;
}

export interface ReplicationDefinitionMutationRequest {
  name: string;
  description?: string;
  sourceNodeId: string;
  transferTargetId: string;
  includeChildren?: boolean;
  enabled?: boolean;
}

export interface ReplicationJobDto {
  id: string;
  definitionId: string;
  transferTargetId: string;
  sourceNodeId: string;
  copiedNodeId?: string | null;
  userId: string;
  status: ReplicationJobStatus;
  lastMessage?: string | null;
  errorLog?: string | null;
  startedAt?: string | null;
  completedAt?: string | null;
  createdAt: string;
  updatedAt?: string | null;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

class TransferReplicationService {
  async listTargets(): Promise<TransferTargetDto[]> {
    return api.get<TransferTargetDto[]>('/transfer/targets');
  }

  async createTarget(payload: TransferTargetMutationRequest): Promise<TransferTargetDto> {
    return api.post<TransferTargetDto>('/transfer/targets', payload);
  }

  async updateTarget(targetId: string, payload: TransferTargetMutationRequest): Promise<TransferTargetDto> {
    return api.put<TransferTargetDto>(`/transfer/targets/${targetId}`, payload);
  }

  async verifyTarget(targetId: string): Promise<TransferTargetDto> {
    return api.post<TransferTargetDto>(`/transfer/targets/${targetId}/verify`);
  }

  async deleteTarget(targetId: string): Promise<void> {
    return api.delete(`/transfer/targets/${targetId}`);
  }

  async listDefinitions(): Promise<ReplicationDefinitionDto[]> {
    return api.get<ReplicationDefinitionDto[]>('/replication/definitions');
  }

  async createDefinition(payload: ReplicationDefinitionMutationRequest): Promise<ReplicationDefinitionDto> {
    return api.post<ReplicationDefinitionDto>('/replication/definitions', payload);
  }

  async updateDefinition(
    definitionId: string,
    payload: ReplicationDefinitionMutationRequest
  ): Promise<ReplicationDefinitionDto> {
    return api.put<ReplicationDefinitionDto>(`/replication/definitions/${definitionId}`, payload);
  }

  async deleteDefinition(definitionId: string): Promise<void> {
    return api.delete(`/replication/definitions/${definitionId}`);
  }

  async runDefinition(definitionId: string): Promise<ReplicationJobDto> {
    return api.post<ReplicationJobDto>(`/replication/definitions/${definitionId}/run`);
  }

  async listJobs(page = 0, size = 10): Promise<PageResponse<ReplicationJobDto>> {
    return api.get<PageResponse<ReplicationJobDto>>('/replication/jobs', { params: { page, size } });
  }
}

const transferReplicationService = new TransferReplicationService();
export default transferReplicationService;
