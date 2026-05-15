import api from './api';
import { BulkImportSelectionFile, getBulkImportRelativePath } from 'utils/bulkImportUtils';

export const BULK_IMPORT_UNEXPECTED_RESPONSE_MESSAGE =
  'Bulk import endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

export type ImportJobStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELED';
export type ConflictPolicy = 'SKIP' | 'RENAME' | 'OVERWRITE';

export interface ImportJobDto {
  id: string;
  userId: string;
  status: ImportJobStatus;
  targetFolderId?: string | null;
  conflictPolicy: ConflictPolicy;
  totalFiles: number;
  processedFiles: number;
  importedFiles: number;
  skippedFiles: number;
  failedFiles: number;
  currentItemPath?: string | null;
  lastMessage?: string | null;
  errorLog?: string | null;
  startedAt?: string | null;
  completedAt?: string | null;
  createdAt: string;
  updatedAt?: string | null;
}

export interface ImportJobPage {
  content: ImportJobDto[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

const IMPORT_JOB_STATUSES: ImportJobStatus[] = [
  'PENDING',
  'RUNNING',
  'COMPLETED',
  'FAILED',
  'CANCELED',
];

const CONFLICT_POLICIES: ConflictPolicy[] = ['SKIP', 'RENAME', 'OVERWRITE'];

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isStringOrNullish = (value: unknown): value is string | null | undefined => (
  value === null || value === undefined || typeof value === 'string'
);

const isFiniteNumber = (value: unknown): value is number => (
  typeof value === 'number' && Number.isFinite(value)
);

const isImportJobStatus = (value: unknown): value is ImportJobStatus => (
  typeof value === 'string' && (IMPORT_JOB_STATUSES as string[]).includes(value)
);

const isConflictPolicy = (value: unknown): value is ConflictPolicy => (
  typeof value === 'string' && (CONFLICT_POLICIES as string[]).includes(value)
);

const assertUnexpectedResponse = (): never => {
  throw new Error(BULK_IMPORT_UNEXPECTED_RESPONSE_MESSAGE);
};

const isImportJobDto = (value: unknown): value is ImportJobDto => {
  if (!isObject(value)) {
    return false;
  }
  if (typeof value.id !== 'string' || typeof value.userId !== 'string') {
    return false;
  }
  if (!isImportJobStatus(value.status) || !isConflictPolicy(value.conflictPolicy)) {
    return false;
  }
  if (!isFiniteNumber(value.totalFiles)
    || !isFiniteNumber(value.processedFiles)
    || !isFiniteNumber(value.importedFiles)
    || !isFiniteNumber(value.skippedFiles)
    || !isFiniteNumber(value.failedFiles)) {
    return false;
  }
  if (typeof value.createdAt !== 'string') {
    return false;
  }
  if (!isStringOrNullish(value.targetFolderId)
    || !isStringOrNullish(value.currentItemPath)
    || !isStringOrNullish(value.lastMessage)
    || !isStringOrNullish(value.errorLog)
    || !isStringOrNullish(value.startedAt)
    || !isStringOrNullish(value.completedAt)
    || !isStringOrNullish(value.updatedAt)) {
    return false;
  }
  return true;
};

const assertImportJobDto = (value: unknown): ImportJobDto => (
  isImportJobDto(value) ? value : assertUnexpectedResponse()
);

const isImportJobPage = (value: unknown): value is ImportJobPage => (
  isObject(value)
    && Array.isArray(value.content)
    && value.content.every(isImportJobDto)
    && isFiniteNumber(value.totalElements)
    && isFiniteNumber(value.totalPages)
    && isFiniteNumber(value.number)
    && isFiniteNumber(value.size)
);

const assertImportJobPage = (value: unknown): ImportJobPage => (
  isImportJobPage(value) ? value : assertUnexpectedResponse()
);

class BulkImportService {
  async startImport(
    files: BulkImportSelectionFile[],
    targetFolderId?: string,
    conflictPolicy: ConflictPolicy = 'SKIP'
  ): Promise<ImportJobDto> {
    const formData = new FormData();
    files.forEach((file) => {
      formData.append('files', file);
      formData.append('relativePaths', getBulkImportRelativePath(file));
    });
    if (targetFolderId) {
      formData.append('targetFolderId', targetFolderId);
    }
    formData.append('conflictPolicy', conflictPolicy);
    const result = await api.postFormData<unknown>('/bulk-import', formData);
    return assertImportJobDto(result);
  }

  async getJob(jobId: string): Promise<ImportJobDto> {
    const result = await api.get<unknown>(`/bulk-import/${jobId}`);
    return assertImportJobDto(result);
  }

  async listJobs(page = 0, size = 20): Promise<ImportJobPage> {
    const result = await api.get<unknown>('/bulk-import', { params: { page, size } });
    return assertImportJobPage(result);
  }

  async cancelJob(jobId: string): Promise<ImportJobDto> {
    const result = await api.delete<unknown>(`/bulk-import/${jobId}`);
    return assertImportJobDto(result);
  }
}

const bulkImportService = new BulkImportService();
export default bulkImportService;
