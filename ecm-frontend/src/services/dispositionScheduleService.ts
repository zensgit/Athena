import api from './api';

// ── DTO types ──────────────────────────────────────────────────────────────────

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
  archiveStorageTier: string | null;
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
  actionType: string;
  eligibleAt: string;
  blockedByHoldNames: string | null;
}

export interface DispositionDryRunDto {
  folderId: string;
  folderName: string;
  includeSubfolders: boolean;
  archiveStorageTier: string;
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
  actionType: string;
  status: string;
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

// ── service ────────────────────────────────────────────────────────────────────

class DispositionScheduleService {
  listSchedules(): Promise<DispositionScheduleDto[]> {
    return api.get<DispositionScheduleDto[]>('/disposition-schedules');
  }

  getSchedule(folderId: string): Promise<DispositionScheduleDto> {
    return api.get<DispositionScheduleDto>(`/folders/${folderId}/disposition-schedule`);
  }

  upsertSchedule(folderId: string, data: DispositionScheduleUpsertRequest): Promise<DispositionScheduleDto> {
    return api.put<DispositionScheduleDto>(`/folders/${folderId}/disposition-schedule`, data);
  }

  deleteSchedule(folderId: string): Promise<void> {
    return api.delete<void>(`/folders/${folderId}/disposition-schedule`);
  }

  dryRun(folderId: string, data?: DispositionScheduleUpsertRequest): Promise<DispositionDryRunDto> {
    return api.post<DispositionDryRunDto>(`/folders/${folderId}/disposition-schedule/dry-run`, data ?? {});
  }

  execute(folderId: string): Promise<DispositionExecutionDto> {
    return api.post<DispositionExecutionDto>(`/folders/${folderId}/disposition-schedule/execute`, {});
  }

  listExecutions(
    folderId: string,
    page = 0,
    size = 10,
  ): Promise<DispositionPage<DispositionActionExecutionDto>> {
    return api.get<DispositionPage<DispositionActionExecutionDto>>(
      `/folders/${folderId}/disposition-schedule/executions`,
      { params: { page, size } },
    );
  }

  runAll(): Promise<DispositionBatchExecutionDto> {
    return api.post<DispositionBatchExecutionDto>('/disposition-schedules/run', {});
  }
}

const dispositionScheduleService = new DispositionScheduleService();
export default dispositionScheduleService;
