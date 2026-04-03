import api from './api';
import { BulkImportSelectionFile, getBulkImportRelativePath } from 'utils/bulkImportUtils';

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
    return api.postFormData<ImportJobDto>('/bulk-import', formData);
  }

  async getJob(jobId: string): Promise<ImportJobDto> {
    return api.get<ImportJobDto>(`/bulk-import/${jobId}`);
  }

  async listJobs(page = 0, size = 20): Promise<ImportJobPage> {
    return api.get<ImportJobPage>('/bulk-import', { params: { page, size } });
  }

  async cancelJob(jobId: string): Promise<ImportJobDto> {
    return api.delete<ImportJobDto>(`/bulk-import/${jobId}`);
  }
}

const bulkImportService = new BulkImportService();
export default bulkImportService;
