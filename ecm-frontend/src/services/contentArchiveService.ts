import api from './api';

export const CONTENT_ARCHIVE_UNEXPECTED_RESPONSE_MESSAGE =
  'Content archive endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

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

const ARCHIVE_STATUSES: ArchiveStatus[] = ['LIVE', 'ARCHIVED', 'RESTORING'];
const ARCHIVE_STORE_TIERS: ArchiveStoreTier[] = ['HOT', 'WARM', 'COLD', 'GLACIER'];

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

const isFiniteNumber = (value: unknown): value is number =>
  typeof value === 'number' && Number.isFinite(value);

const isNullableString = (value: unknown): value is string | null =>
  value === null || typeof value === 'string';

const isOptionalNullableString = (value: unknown): value is string | null | undefined =>
  value === undefined || isNullableString(value);

const isOptionalNullableNumber = (value: unknown): value is number | null | undefined =>
  value === undefined || value === null || isFiniteNumber(value);

const isArchiveStatus = (value: unknown): value is ArchiveStatus =>
  typeof value === 'string' && (ARCHIVE_STATUSES as string[]).includes(value);

const isArchiveStoreTier = (value: unknown): value is ArchiveStoreTier =>
  typeof value === 'string' && (ARCHIVE_STORE_TIERS as string[]).includes(value);

const isStringArray = (value: unknown): value is string[] =>
  Array.isArray(value) && value.every((entry) => typeof entry === 'string');

function assertContentArchiveResponse(condition: unknown): asserts condition {
  if (!condition) {
    throw new Error(CONTENT_ARCHIVE_UNEXPECTED_RESPONSE_MESSAGE);
  }
}

const assertArchiveMutationDto = (value: unknown): ArchiveMutationDto => {
  assertContentArchiveResponse(isRecord(value));
  assertContentArchiveResponse(typeof value.nodeId === 'string');
  assertContentArchiveResponse(typeof value.name === 'string');
  assertContentArchiveResponse(isArchiveStatus(value.archiveStatus));
  assertContentArchiveResponse(isArchiveStoreTier(value.archiveStoreTier));
  assertContentArchiveResponse(isOptionalNullableString(value.archivedDate));
  assertContentArchiveResponse(isOptionalNullableString(value.archivedBy));
  assertContentArchiveResponse(isFiniteNumber(value.affectedNodeCount));

  return value as unknown as ArchiveMutationDto;
};

const assertArchiveStatusDto = (value: unknown): ArchiveStatusDto => {
  assertContentArchiveResponse(isRecord(value));
  assertContentArchiveResponse(typeof value.nodeId === 'string');
  assertContentArchiveResponse(typeof value.name === 'string');
  assertContentArchiveResponse(typeof value.nodeType === 'string');
  assertContentArchiveResponse(typeof value.path === 'string');
  assertContentArchiveResponse(isArchiveStatus(value.archiveStatus));
  assertContentArchiveResponse(isArchiveStoreTier(value.archiveStoreTier));
  assertContentArchiveResponse(isOptionalNullableString(value.archivedDate));
  assertContentArchiveResponse(isOptionalNullableString(value.archivedBy));

  return value as unknown as ArchiveStatusDto;
};

const assertArchivedNodeDto = (value: unknown): ArchivedNodeDto => {
  assertContentArchiveResponse(isRecord(value));
  assertContentArchiveResponse(typeof value.nodeId === 'string');
  assertContentArchiveResponse(typeof value.name === 'string');
  assertContentArchiveResponse(typeof value.nodeType === 'string');
  assertContentArchiveResponse(typeof value.path === 'string');
  assertContentArchiveResponse(isOptionalNullableNumber(value.size));
  assertContentArchiveResponse(isOptionalNullableString(value.createdBy));
  assertContentArchiveResponse(isOptionalNullableString(value.createdDate));
  assertContentArchiveResponse(isArchiveStatus(value.archiveStatus));
  assertContentArchiveResponse(isArchiveStoreTier(value.archiveStoreTier));
  assertContentArchiveResponse(isOptionalNullableString(value.archivedDate));
  assertContentArchiveResponse(isOptionalNullableString(value.archivedBy));

  return value as unknown as ArchivedNodeDto;
};

const assertArchivedNodePage = (value: unknown): ArchivedNodePage => {
  assertContentArchiveResponse(isRecord(value));
  assertContentArchiveResponse(Array.isArray(value.content));
  const content = value.content.map(assertArchivedNodeDto);
  assertContentArchiveResponse(isFiniteNumber(value.totalElements));
  assertContentArchiveResponse(isFiniteNumber(value.totalPages));
  assertContentArchiveResponse(isFiniteNumber(value.number));
  assertContentArchiveResponse(isFiniteNumber(value.size));

  return {
    ...value,
    content,
  } as ArchivedNodePage;
};

const assertArchivePolicyDto = (value: unknown): ArchivePolicyDto => {
  assertContentArchiveResponse(isRecord(value));
  assertContentArchiveResponse(typeof value.policyId === 'string');
  assertContentArchiveResponse(typeof value.folderId === 'string');
  assertContentArchiveResponse(typeof value.folderName === 'string');
  assertContentArchiveResponse(typeof value.folderPath === 'string');
  assertContentArchiveResponse(typeof value.enabled === 'boolean');
  assertContentArchiveResponse(isFiniteNumber(value.inactivityDays));
  assertContentArchiveResponse(isArchiveStoreTier(value.storageTier));
  assertContentArchiveResponse(typeof value.includeSubfolders === 'boolean');
  assertContentArchiveResponse(isFiniteNumber(value.maxCandidatesPerRun));
  assertContentArchiveResponse(isOptionalNullableString(value.lastDryRunAt));
  assertContentArchiveResponse(isOptionalNullableString(value.lastExecutedAt));
  assertContentArchiveResponse(isOptionalNullableNumber(value.lastCandidateCount));
  assertContentArchiveResponse(isOptionalNullableNumber(value.lastArchivedNodeCount));
  assertContentArchiveResponse(isOptionalNullableString(value.lastError));

  return value as unknown as ArchivePolicyDto;
};

const assertArchivePolicyList = (value: unknown): ArchivePolicyDto[] => {
  assertContentArchiveResponse(Array.isArray(value));
  return value.map(assertArchivePolicyDto);
};

const assertArchivePolicyCandidateDto = (value: unknown): ArchivePolicyCandidateDto => {
  assertContentArchiveResponse(isRecord(value));
  assertContentArchiveResponse(typeof value.nodeId === 'string');
  assertContentArchiveResponse(typeof value.name === 'string');
  assertContentArchiveResponse(typeof value.nodeType === 'string');
  assertContentArchiveResponse(typeof value.path === 'string');
  assertContentArchiveResponse(isOptionalNullableString(value.activityDate));

  return value as unknown as ArchivePolicyCandidateDto;
};

const assertArchivePolicyDryRunDto = (value: unknown): ArchivePolicyDryRunDto => {
  assertContentArchiveResponse(isRecord(value));
  assertContentArchiveResponse(typeof value.folderId === 'string');
  assertContentArchiveResponse(typeof value.folderName === 'string');
  assertContentArchiveResponse(typeof value.cutoffDate === 'string');
  assertContentArchiveResponse(isArchiveStoreTier(value.storageTier));
  assertContentArchiveResponse(typeof value.includeSubfolders === 'boolean');
  assertContentArchiveResponse(isFiniteNumber(value.maxCandidatesPerRun));
  assertContentArchiveResponse(isFiniteNumber(value.candidateCount));
  assertContentArchiveResponse(Array.isArray(value.candidates));
  const candidates = value.candidates.map(assertArchivePolicyCandidateDto);

  return {
    ...value,
    candidates,
  } as ArchivePolicyDryRunDto;
};

const assertArchivePolicyExecutionDto = (value: unknown): ArchivePolicyExecutionDto => {
  assertContentArchiveResponse(isRecord(value));
  assertContentArchiveResponse(typeof value.folderId === 'string');
  assertContentArchiveResponse(typeof value.folderName === 'string');
  assertContentArchiveResponse(isFiniteNumber(value.candidateCount));
  assertContentArchiveResponse(isFiniteNumber(value.archivedNodeCount));
  assertContentArchiveResponse(isFiniteNumber(value.failureCount));
  assertContentArchiveResponse(isStringArray(value.failures));
  assertContentArchiveResponse(isOptionalNullableString(value.error));

  return value as unknown as ArchivePolicyExecutionDto;
};

const assertArchivePolicyBatchExecutionDto = (value: unknown): ArchivePolicyBatchExecutionDto => {
  assertContentArchiveResponse(isRecord(value));
  assertContentArchiveResponse(isFiniteNumber(value.executedPolicies));
  assertContentArchiveResponse(isFiniteNumber(value.totalCandidates));
  assertContentArchiveResponse(isFiniteNumber(value.archivedNodeCount));
  assertContentArchiveResponse(isFiniteNumber(value.failureCount));
  assertContentArchiveResponse(Array.isArray(value.results));
  const results = value.results.map(assertArchivePolicyExecutionDto);

  return {
    ...value,
    results,
  } as ArchivePolicyBatchExecutionDto;
};

class ContentArchiveService {
  async archiveNode(nodeId: string, storageTier: ArchiveStoreTier = 'COLD'): Promise<ArchiveMutationDto> {
    const result = await api.post<unknown>(`/nodes/${nodeId}/archive`, { storageTier });
    return assertArchiveMutationDto(result);
  }

  async restoreNode(nodeId: string): Promise<ArchiveMutationDto> {
    const result = await api.post<unknown>(`/nodes/${nodeId}/restore`);
    return assertArchiveMutationDto(result);
  }

  async getArchiveStatus(nodeId: string): Promise<ArchiveStatusDto> {
    const result = await api.get<unknown>(`/nodes/${nodeId}/archive-status`);
    return assertArchiveStatusDto(result);
  }

  async listArchivedNodes(page = 0, size = 20): Promise<ArchivedNodePage> {
    const result = await api.get<unknown>('/nodes/archived', { params: { page, size } });
    return assertArchivedNodePage(result);
  }

  async getArchivePolicy(folderId: string): Promise<ArchivePolicyDto> {
    const result = await api.get<unknown>(`/folders/${folderId}/archive-policy`);
    return assertArchivePolicyDto(result);
  }

  async upsertArchivePolicy(folderId: string, payload: ArchivePolicyRequest): Promise<ArchivePolicyDto> {
    const result = await api.put<unknown>(`/folders/${folderId}/archive-policy`, payload);
    return assertArchivePolicyDto(result);
  }

  async deleteArchivePolicy(folderId: string): Promise<void> {
    await api.delete<void>(`/folders/${folderId}/archive-policy`);
  }

  async dryRunArchivePolicy(folderId: string, payload?: ArchivePolicyRequest): Promise<ArchivePolicyDryRunDto> {
    const result = await api.post<unknown>(`/folders/${folderId}/archive-policy/dry-run`, payload ?? {});
    return assertArchivePolicyDryRunDto(result);
  }

  async executeArchivePolicy(folderId: string): Promise<ArchivePolicyExecutionDto> {
    const result = await api.post<unknown>(`/folders/${folderId}/archive-policy/execute`);
    return assertArchivePolicyExecutionDto(result);
  }

  async listArchivePolicies(): Promise<ArchivePolicyDto[]> {
    const result = await api.get<unknown>('/archive-policies');
    return assertArchivePolicyList(result);
  }

  async runArchivePolicies(): Promise<ArchivePolicyBatchExecutionDto> {
    const result = await api.post<unknown>('/archive-policies/run');
    return assertArchivePolicyBatchExecutionDto(result);
  }
}

const contentArchiveService = new ContentArchiveService();
export default contentArchiveService;
