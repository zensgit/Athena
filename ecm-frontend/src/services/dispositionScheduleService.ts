import api from './api';

export const DISPOSITION_SCHEDULE_UNEXPECTED_RESPONSE_MESSAGE =
  'Disposition schedule endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

// DTO types

export type DispositionArchiveStorageTier = 'HOT' | 'WARM' | 'COLD' | 'GLACIER';
export type DispositionActionType = 'CUTOFF' | 'ARCHIVE' | 'DESTROY';
export type DispositionExecutionStatus = 'SUCCESS' | 'BLOCKED' | 'FAILED';

export interface DispositionScheduleDto {
  id: string;
  folderId: string;
  folderName: string;
  folderPath: string;
  enabled: boolean;
  includeSubfolders: boolean;
  cutoffAfterDays: number | null;
  archiveAfterCutoffDays: number | null;
  destroyAfterArchiveDays: number | null;
  archiveStorageTier: DispositionArchiveStorageTier | null;
  maxCandidatesPerAction: number | null;
  lastDryRunAt: string | null;
  lastExecutedAt: string | null;
  lastError: string | null;
}

export interface DispositionScheduleUpsertRequest {
  enabled?: boolean;
  includeSubfolders?: boolean;
  cutoffAfterDays?: number | null;
  archiveAfterCutoffDays?: number | null;
  destroyAfterArchiveDays?: number | null;
  archiveStorageTier?: string | null;
  maxCandidatesPerAction?: number | null;
}

export interface DispositionCandidateDto {
  nodeId: string;
  name: string;
  nodeType: string;
  path: string;
  actionType: DispositionActionType;
  eligibleAt: string;
  blockedByHoldNames: string | null;
}

export interface DispositionDryRunDto {
  folderId: string;
  folderName: string;
  includeSubfolders: boolean;
  archiveStorageTier: DispositionArchiveStorageTier;
  maxCandidatesPerAction: number;
  cutoffCount: number;
  archiveCount: number;
  destroyCount: number;
  candidates: DispositionCandidateDto[];
}

export interface DispositionExecutionDto {
  folderId: string;
  folderName: string;
  cutoffCount: number;
  archiveCandidateCount: number;
  archivedNodeCount: number;
  destroyCandidateCount: number;
  destroyedNodeCount: number;
  failureCount: number;
  blockedCount: number;
  failures: string[];
  error: string | null;
}

export interface DispositionBatchExecutionDto {
  executedSchedules: number;
  cutoffCount: number;
  archivedNodeCount: number;
  destroyedNodeCount: number;
  blockedCount: number;
  failureCount: number;
  results: DispositionExecutionDto[];
}

export interface DispositionActionExecutionDto {
  id: string;
  actionType: DispositionActionType;
  status: DispositionExecutionStatus;
  nodeId: string;
  nodeName: string;
  nodeType: string;
  nodePath: string;
  affectedNodeCount: number;
  details: string | null;
  actor: string;
  executedAt: string;
}

export interface DispositionPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

// Response shape guards

const ARCHIVE_STORAGE_TIERS: DispositionArchiveStorageTier[] = ['HOT', 'WARM', 'COLD', 'GLACIER'];
const ACTION_TYPES: DispositionActionType[] = ['CUTOFF', 'ARCHIVE', 'DESTROY'];
const EXECUTION_STATUSES: DispositionExecutionStatus[] = ['SUCCESS', 'BLOCKED', 'FAILED'];

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isFiniteNumber = (value: unknown): value is number => (
  typeof value === 'number' && Number.isFinite(value)
);

const isNumberOrNull = (value: unknown): value is number | null => (
  value === null || isFiniteNumber(value)
);

const isStringOrNull = (value: unknown): value is string | null => (
  value === null || typeof value === 'string'
);

const isArchiveStorageTier = (value: unknown): value is DispositionArchiveStorageTier => (
  typeof value === 'string' && (ARCHIVE_STORAGE_TIERS as string[]).includes(value)
);

const isArchiveStorageTierOrNull = (value: unknown): value is DispositionArchiveStorageTier | null => (
  value === null || isArchiveStorageTier(value)
);

const isActionType = (value: unknown): value is DispositionActionType => (
  typeof value === 'string' && (ACTION_TYPES as string[]).includes(value)
);

const isExecutionStatus = (value: unknown): value is DispositionExecutionStatus => (
  typeof value === 'string' && (EXECUTION_STATUSES as string[]).includes(value)
);

const isStringArray = (value: unknown): value is string[] => (
  Array.isArray(value) && value.every((entry) => typeof entry === 'string')
);

const assertUnexpectedResponse = (): never => {
  throw new Error(DISPOSITION_SCHEDULE_UNEXPECTED_RESPONSE_MESSAGE);
};

const isDispositionScheduleDto = (value: unknown): value is DispositionScheduleDto => {
  if (!isObject(value)) return false;
  if (typeof value.id !== 'string'
    || typeof value.folderId !== 'string'
    || typeof value.folderName !== 'string'
    || typeof value.folderPath !== 'string') {
    return false;
  }
  if (typeof value.enabled !== 'boolean' || typeof value.includeSubfolders !== 'boolean') {
    return false;
  }
  if (!isNumberOrNull(value.cutoffAfterDays)
    || !isNumberOrNull(value.archiveAfterCutoffDays)
    || !isNumberOrNull(value.destroyAfterArchiveDays)
    || !isNumberOrNull(value.maxCandidatesPerAction)) {
    return false;
  }
  if (!isArchiveStorageTierOrNull(value.archiveStorageTier)) {
    return false;
  }
  if (!isStringOrNull(value.lastDryRunAt)
    || !isStringOrNull(value.lastExecutedAt)
    || !isStringOrNull(value.lastError)) {
    return false;
  }
  return true;
};

const assertDispositionScheduleDto = (value: unknown): DispositionScheduleDto => (
  isDispositionScheduleDto(value) ? value : assertUnexpectedResponse()
);

const isDispositionScheduleList = (value: unknown): value is DispositionScheduleDto[] => (
  Array.isArray(value) && value.every(isDispositionScheduleDto)
);

const assertDispositionScheduleList = (value: unknown): DispositionScheduleDto[] => (
  isDispositionScheduleList(value) ? value : assertUnexpectedResponse()
);

const isDispositionCandidateDto = (value: unknown): value is DispositionCandidateDto => {
  if (!isObject(value)) return false;
  if (typeof value.nodeId !== 'string'
    || typeof value.name !== 'string'
    || typeof value.nodeType !== 'string'
    || typeof value.path !== 'string'
    || typeof value.eligibleAt !== 'string') {
    return false;
  }
  if (!isActionType(value.actionType)) return false;
  if (!isStringOrNull(value.blockedByHoldNames)) return false;
  return true;
};

const isDispositionDryRunDto = (value: unknown): value is DispositionDryRunDto => {
  if (!isObject(value)) return false;
  if (typeof value.folderId !== 'string' || typeof value.folderName !== 'string') {
    return false;
  }
  if (typeof value.includeSubfolders !== 'boolean') return false;
  if (!isArchiveStorageTier(value.archiveStorageTier)) return false;
  if (!isFiniteNumber(value.maxCandidatesPerAction)
    || !isFiniteNumber(value.cutoffCount)
    || !isFiniteNumber(value.archiveCount)
    || !isFiniteNumber(value.destroyCount)) {
    return false;
  }
  if (!Array.isArray(value.candidates) || !value.candidates.every(isDispositionCandidateDto)) {
    return false;
  }
  return true;
};

const assertDispositionDryRunDto = (value: unknown): DispositionDryRunDto => (
  isDispositionDryRunDto(value) ? value : assertUnexpectedResponse()
);

const isDispositionExecutionDto = (value: unknown): value is DispositionExecutionDto => {
  if (!isObject(value)) return false;
  if (typeof value.folderId !== 'string' || typeof value.folderName !== 'string') {
    return false;
  }
  if (!isFiniteNumber(value.cutoffCount)
    || !isFiniteNumber(value.archiveCandidateCount)
    || !isFiniteNumber(value.archivedNodeCount)
    || !isFiniteNumber(value.destroyCandidateCount)
    || !isFiniteNumber(value.destroyedNodeCount)
    || !isFiniteNumber(value.failureCount)
    || !isFiniteNumber(value.blockedCount)) {
    return false;
  }
  if (!isStringArray(value.failures)) return false;
  if (!isStringOrNull(value.error)) return false;
  return true;
};

const assertDispositionExecutionDto = (value: unknown): DispositionExecutionDto => (
  isDispositionExecutionDto(value) ? value : assertUnexpectedResponse()
);

const isDispositionBatchExecutionDto = (value: unknown): value is DispositionBatchExecutionDto => {
  if (!isObject(value)) return false;
  if (!isFiniteNumber(value.executedSchedules)
    || !isFiniteNumber(value.cutoffCount)
    || !isFiniteNumber(value.archivedNodeCount)
    || !isFiniteNumber(value.destroyedNodeCount)
    || !isFiniteNumber(value.blockedCount)
    || !isFiniteNumber(value.failureCount)) {
    return false;
  }
  if (!Array.isArray(value.results) || !value.results.every(isDispositionExecutionDto)) {
    return false;
  }
  return true;
};

const assertDispositionBatchExecutionDto = (value: unknown): DispositionBatchExecutionDto => (
  isDispositionBatchExecutionDto(value) ? value : assertUnexpectedResponse()
);

const isDispositionActionExecutionDto = (value: unknown): value is DispositionActionExecutionDto => {
  if (!isObject(value)) return false;
  if (typeof value.id !== 'string'
    || typeof value.nodeId !== 'string'
    || typeof value.nodeName !== 'string'
    || typeof value.nodeType !== 'string'
    || typeof value.nodePath !== 'string'
    || typeof value.actor !== 'string'
    || typeof value.executedAt !== 'string') {
    return false;
  }
  if (!isActionType(value.actionType)) return false;
  if (!isExecutionStatus(value.status)) return false;
  if (!isFiniteNumber(value.affectedNodeCount)) return false;
  if (!isStringOrNull(value.details)) return false;
  return true;
};

const isDispositionActionExecutionPage = (
  value: unknown,
): value is DispositionPage<DispositionActionExecutionDto> => (
  isObject(value)
    && Array.isArray(value.content)
    && value.content.every(isDispositionActionExecutionDto)
    && isFiniteNumber(value.totalElements)
    && isFiniteNumber(value.totalPages)
    && isFiniteNumber(value.number)
    && isFiniteNumber(value.size)
);

const assertDispositionActionExecutionPage = (
  value: unknown,
): DispositionPage<DispositionActionExecutionDto> => (
  isDispositionActionExecutionPage(value) ? value : assertUnexpectedResponse()
);

// Service

class DispositionScheduleService {
  async listSchedules(): Promise<DispositionScheduleDto[]> {
    const result = await api.get<unknown>('/disposition-schedules');
    return assertDispositionScheduleList(result);
  }

  async getSchedule(folderId: string): Promise<DispositionScheduleDto> {
    const result = await api.get<unknown>(`/folders/${folderId}/disposition-schedule`);
    return assertDispositionScheduleDto(result);
  }

  async upsertSchedule(folderId: string, data: DispositionScheduleUpsertRequest): Promise<DispositionScheduleDto> {
    const result = await api.put<unknown>(`/folders/${folderId}/disposition-schedule`, data);
    return assertDispositionScheduleDto(result);
  }

  async deleteSchedule(folderId: string): Promise<void> {
    await api.delete(`/folders/${folderId}/disposition-schedule`);
  }

  async dryRun(folderId: string, data?: DispositionScheduleUpsertRequest): Promise<DispositionDryRunDto> {
    const result = await api.post<unknown>(`/folders/${folderId}/disposition-schedule/dry-run`, data ?? {});
    return assertDispositionDryRunDto(result);
  }

  async execute(folderId: string): Promise<DispositionExecutionDto> {
    const result = await api.post<unknown>(`/folders/${folderId}/disposition-schedule/execute`, {});
    return assertDispositionExecutionDto(result);
  }

  async listExecutions(
    folderId: string,
    page = 0,
    size = 10,
  ): Promise<DispositionPage<DispositionActionExecutionDto>> {
    const result = await api.get<unknown>(
      `/folders/${folderId}/disposition-schedule/executions`,
      { params: { page, size } },
    );
    return assertDispositionActionExecutionPage(result);
  }

  async runAll(): Promise<DispositionBatchExecutionDto> {
    const result = await api.post<unknown>('/disposition-schedules/run', {});
    return assertDispositionBatchExecutionDto(result);
  }
}

const dispositionScheduleService = new DispositionScheduleService();
export default dispositionScheduleService;
