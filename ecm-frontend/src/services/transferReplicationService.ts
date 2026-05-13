import api from './api';

export type TransportType = 'LOOPBACK' | 'ATHENA_HTTP';
export type AuthType = 'NONE' | 'BASIC' | 'BEARER';
export type VerificationStatus = 'NEVER_VERIFIED' | 'VERIFIED' | 'FAILED';
export type ReplicationJobStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELED';
export type ReplicationTransportStatus = 'NEVER_RUN' | 'RUNNING' | 'SUCCESS' | 'FAILED';
export type ReceiverAccessStatus = 'NEVER_USED' | 'SUCCESS' | 'FAILED';
export type ReplicationConflictPolicy = 'SKIP' | 'RENAME' | 'OVERWRITE';

export const TRANSFER_REPLICATION_UNEXPECTED_RESPONSE_MESSAGE =
  'Transfer replication endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isStringOrNullish = (value: unknown): value is string | null | undefined => (
  value === null || value === undefined || typeof value === 'string'
);

const isNumber = (value: unknown): value is number => (
  typeof value === 'number' && Number.isFinite(value)
);

const hasCoreTimestamps = (value: Record<string, unknown>) => (
  typeof value.createdAt === 'string' && isStringOrNullish(value.updatedAt)
);

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
  remoteRepositoryId?: string | null;
  lastVerifiedAt?: string | null;
  createdAt: string;
  updatedAt?: string | null;
}

export interface ReplicationJobEntryReportItem {
  sourceNodeId?: string | null;
  sourcePath?: string | null;
  sourceType?: string | null;
  targetNodeId?: string | null;
  action?: string | null;
  message?: string | null;
  startedAt?: string | null;
  completedAt?: string | null;
}

export interface ReplicationJobEntryReport {
  totalEntries: number;
  successCount: number;
  failureCount: number;
  entries: ReplicationJobEntryReportItem[];
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
  conflictPolicy?: ReplicationConflictPolicy | null;
  cronExpression?: string | null;
  scheduleTimezone?: string | null;
  nextRunAt?: string | null;
  autoRetryEnabled: boolean;
  maxRetryAttempts: number;
  retryBackoffMinutes: number;
  jobRetentionDays: number;
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
  conflictPolicy?: ReplicationConflictPolicy;
  cronExpression?: string;
  scheduleTimezone?: string;
  autoRetryEnabled?: boolean;
  maxRetryAttempts?: number;
  retryBackoffMinutes?: number;
  jobRetentionDays?: number;
}

export interface ReplicationDefinitionDraft {
  name: string;
  description?: string;
  sourceNodeId: string;
  transferTargetId: string;
  includeChildren?: boolean;
  enabled?: boolean;
  conflictPolicy?: ReplicationConflictPolicy;
  cronExpression?: string;
  scheduleTimezone?: string;
  autoRetryEnabled?: boolean;
  maxRetryAttempts?: string | number | null;
  retryBackoffMinutes?: string | number | null;
  jobRetentionDays?: string | number | null;
}

const toOptionalNumber = (value: string | number | null | undefined): number | undefined => {
  if (value === null || value === undefined) {
    return undefined;
  }
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : undefined;
  }
  const trimmed = value.trim();
  if (!trimmed) {
    return undefined;
  }
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : undefined;
};

export const buildReplicationDefinitionRequest = (
  draft: ReplicationDefinitionDraft
): ReplicationDefinitionMutationRequest => {
  const payload: ReplicationDefinitionMutationRequest = {
    name: draft.name.trim(),
    description: draft.description?.trim() || undefined,
    sourceNodeId: draft.sourceNodeId.trim(),
    transferTargetId: draft.transferTargetId,
    includeChildren: draft.includeChildren,
    enabled: draft.enabled,
    conflictPolicy: draft.conflictPolicy ?? 'RENAME',
    cronExpression: draft.cronExpression?.trim() || undefined,
    scheduleTimezone: draft.scheduleTimezone?.trim() || undefined,
    autoRetryEnabled: draft.autoRetryEnabled,
    maxRetryAttempts: toOptionalNumber(draft.maxRetryAttempts),
    retryBackoffMinutes: toOptionalNumber(draft.retryBackoffMinutes),
    jobRetentionDays: toOptionalNumber(draft.jobRetentionDays),
  };

  return payload;
};

export interface ReplicationJobDto {
  id: string;
  definitionId: string;
  transferTargetId: string;
  sourceNodeId: string;
  retryOfJobId?: string | null;
  attemptNumber: number;
  scheduledFor?: string | null;
  copiedNodeId?: string | null;
  userId: string;
  status: ReplicationJobStatus;
  lastMessage?: string | null;
  transportStatus: ReplicationTransportStatus;
  transportMessage?: string | null;
  errorLog?: string | null;
  entryReport?: ReplicationJobEntryReport | null;
  reportTruncated: boolean;
  lastAttemptedAt?: string | null;
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

export interface TransferReceiverDto {
  id: string;
  name: string;
  description?: string | null;
  rootFolderId: string;
  rootFolderName?: string | null;
  authType: AuthType;
  authUsername?: string | null;
  authSecretConfigured: boolean;
  enabled: boolean;
  verificationStatus: VerificationStatus;
  verificationMessage?: string | null;
  lastVerifiedAt?: string | null;
  lastAccessStatus: ReceiverAccessStatus;
  lastAccessMessage?: string | null;
  lastAccessedAt?: string | null;
  createdAt: string;
  updatedAt?: string | null;
}

export interface TransferReceiverMutationRequest {
  name: string;
  description?: string;
  rootFolderId: string;
  authType?: AuthType;
  authUsername?: string;
  authSecret?: string;
  enabled?: boolean;
}

const isTransferTargetDto = (value: unknown): value is TransferTargetDto => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.name === 'string'
    && isStringOrNullish(value.description)
    && typeof value.transportType === 'string'
    && typeof value.targetFolderId === 'string'
    && isStringOrNullish(value.targetFolderName)
    && isStringOrNullish(value.endpointUrl)
    && isStringOrNullish(value.endpointPath)
    && typeof value.authType === 'string'
    && isStringOrNullish(value.authUsername)
    && typeof value.authSecretConfigured === 'boolean'
    && typeof value.enabled === 'boolean'
    && typeof value.verificationStatus === 'string'
    && isStringOrNullish(value.verificationMessage)
    && isStringOrNullish(value.remoteRepositoryId)
    && isStringOrNullish(value.lastVerifiedAt)
    && hasCoreTimestamps(value);
};

const isReplicationDefinitionDto = (value: unknown): value is ReplicationDefinitionDto => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.name === 'string'
    && isStringOrNullish(value.description)
    && typeof value.sourceNodeId === 'string'
    && isStringOrNullish(value.sourceNodeName)
    && typeof value.transferTargetId === 'string'
    && isStringOrNullish(value.transferTargetName)
    && typeof value.includeChildren === 'boolean'
    && typeof value.enabled === 'boolean'
    && isStringOrNullish(value.conflictPolicy)
    && isStringOrNullish(value.cronExpression)
    && isStringOrNullish(value.scheduleTimezone)
    && isStringOrNullish(value.nextRunAt)
    && typeof value.autoRetryEnabled === 'boolean'
    && isNumber(value.maxRetryAttempts)
    && isNumber(value.retryBackoffMinutes)
    && isNumber(value.jobRetentionDays)
    && isStringOrNullish(value.lastRunAt)
    && hasCoreTimestamps(value);
};

const isTransferReceiverDto = (value: unknown): value is TransferReceiverDto => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.name === 'string'
    && isStringOrNullish(value.description)
    && typeof value.rootFolderId === 'string'
    && isStringOrNullish(value.rootFolderName)
    && typeof value.authType === 'string'
    && isStringOrNullish(value.authUsername)
    && typeof value.authSecretConfigured === 'boolean'
    && typeof value.enabled === 'boolean'
    && typeof value.verificationStatus === 'string'
    && isStringOrNullish(value.verificationMessage)
    && isStringOrNullish(value.lastVerifiedAt)
    && typeof value.lastAccessStatus === 'string'
    && isStringOrNullish(value.lastAccessMessage)
    && isStringOrNullish(value.lastAccessedAt)
    && hasCoreTimestamps(value);
};

const isReplicationJobEntryReport = (value: unknown): value is ReplicationJobEntryReport | null | undefined => {
  if (value === null || value === undefined) {
    return true;
  }
  return isObject(value);
};

const isReplicationJobDto = (value: unknown): value is ReplicationJobDto => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.definitionId === 'string'
    && typeof value.transferTargetId === 'string'
    && typeof value.sourceNodeId === 'string'
    && isStringOrNullish(value.retryOfJobId)
    && isNumber(value.attemptNumber)
    && isStringOrNullish(value.scheduledFor)
    && isStringOrNullish(value.copiedNodeId)
    && typeof value.userId === 'string'
    && typeof value.status === 'string'
    && isStringOrNullish(value.lastMessage)
    && typeof value.transportStatus === 'string'
    && isStringOrNullish(value.transportMessage)
    && isStringOrNullish(value.errorLog)
    && isReplicationJobEntryReport(value.entryReport)
    && typeof value.reportTruncated === 'boolean'
    && isStringOrNullish(value.lastAttemptedAt)
    && isStringOrNullish(value.startedAt)
    && isStringOrNullish(value.completedAt)
    && hasCoreTimestamps(value);
};

const assertArrayResponse = <T>(
  value: unknown,
  predicate: (item: unknown) => item is T
): T[] => {
  if (!Array.isArray(value)) {
    throw new Error(TRANSFER_REPLICATION_UNEXPECTED_RESPONSE_MESSAGE);
  }
  if (!value.every(predicate)) {
    throw new Error(TRANSFER_REPLICATION_UNEXPECTED_RESPONSE_MESSAGE);
  }
  return value;
};

const assertResponse = <T>(
  value: unknown,
  predicate: (item: unknown) => item is T
): T => {
  if (!predicate(value)) {
    throw new Error(TRANSFER_REPLICATION_UNEXPECTED_RESPONSE_MESSAGE);
  }
  return value;
};

const assertPageResponse = <T>(
  value: unknown,
  predicate: (item: unknown) => item is T
): PageResponse<T> => {
  if (!isObject(value)
    || !Array.isArray(value.content)
    || !isNumber(value.totalElements)
    || !isNumber(value.totalPages)
    || !isNumber(value.number)
    || !isNumber(value.size)
    || !value.content.every(predicate)) {
    throw new Error(TRANSFER_REPLICATION_UNEXPECTED_RESPONSE_MESSAGE);
  }
  return {
    content: value.content,
    totalElements: value.totalElements,
    totalPages: value.totalPages,
    number: value.number,
    size: value.size,
  };
};

class TransferReplicationService {
  async listTargets(): Promise<TransferTargetDto[]> {
    const result = await api.get<unknown>('/transfer/targets');
    return assertArrayResponse(result, isTransferTargetDto);
  }

  async createTarget(payload: TransferTargetMutationRequest): Promise<TransferTargetDto> {
    const result = await api.post<unknown>('/transfer/targets', payload);
    return assertResponse(result, isTransferTargetDto);
  }

  async updateTarget(targetId: string, payload: TransferTargetMutationRequest): Promise<TransferTargetDto> {
    const result = await api.put<unknown>(`/transfer/targets/${targetId}`, payload);
    return assertResponse(result, isTransferTargetDto);
  }

  async verifyTarget(targetId: string): Promise<TransferTargetDto> {
    const result = await api.post<unknown>(`/transfer/targets/${targetId}/verify`);
    return assertResponse(result, isTransferTargetDto);
  }

  async deleteTarget(targetId: string): Promise<void> {
    return api.delete(`/transfer/targets/${targetId}`);
  }

  async listDefinitions(): Promise<ReplicationDefinitionDto[]> {
    const result = await api.get<unknown>('/replication/definitions');
    return assertArrayResponse(result, isReplicationDefinitionDto);
  }

  async createDefinition(payload: ReplicationDefinitionMutationRequest): Promise<ReplicationDefinitionDto> {
    const result = await api.post<unknown>('/replication/definitions', payload);
    return assertResponse(result, isReplicationDefinitionDto);
  }

  async updateDefinition(
    definitionId: string,
    payload: ReplicationDefinitionMutationRequest
  ): Promise<ReplicationDefinitionDto> {
    const result = await api.put<unknown>(`/replication/definitions/${definitionId}`, payload);
    return assertResponse(result, isReplicationDefinitionDto);
  }

  async deleteDefinition(definitionId: string): Promise<void> {
    return api.delete(`/replication/definitions/${definitionId}`);
  }

  async runDefinition(definitionId: string): Promise<ReplicationJobDto> {
    const result = await api.post<unknown>(`/replication/definitions/${definitionId}/run`);
    return assertResponse(result, isReplicationJobDto);
  }

  async listJobs(page = 0, size = 10): Promise<PageResponse<ReplicationJobDto>> {
    const result = await api.get<unknown>('/replication/jobs', { params: { page, size } });
    return assertPageResponse(result, isReplicationJobDto);
  }

  async retryJob(jobId: string): Promise<ReplicationJobDto> {
    const result = await api.post<unknown>(`/replication/jobs/${jobId}/retry`);
    return assertResponse(result, isReplicationJobDto);
  }

  async listReceivers(): Promise<TransferReceiverDto[]> {
    const result = await api.get<unknown>('/transfer/receivers');
    return assertArrayResponse(result, isTransferReceiverDto);
  }

  async createReceiver(payload: TransferReceiverMutationRequest): Promise<TransferReceiverDto> {
    const result = await api.post<unknown>('/transfer/receivers', payload);
    return assertResponse(result, isTransferReceiverDto);
  }

  async updateReceiver(receiverId: string, payload: TransferReceiverMutationRequest): Promise<TransferReceiverDto> {
    const result = await api.put<unknown>(`/transfer/receivers/${receiverId}`, payload);
    return assertResponse(result, isTransferReceiverDto);
  }

  async verifyReceiver(receiverId: string): Promise<TransferReceiverDto> {
    const result = await api.post<unknown>(`/transfer/receivers/${receiverId}/verify`);
    return assertResponse(result, isTransferReceiverDto);
  }

  async deleteReceiver(receiverId: string): Promise<void> {
    return api.delete(`/transfer/receivers/${receiverId}`);
  }
}

export const transferReplicationService = new TransferReplicationService();
export default transferReplicationService;
