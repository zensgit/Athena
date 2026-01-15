import api from './api';

export interface BulkMetadataRequest {
  ids: string[];
  tagNames?: string[];
  categoryIds?: string[];
  correspondentId?: string | null;
  clearCorrespondent?: boolean;
}

export interface BulkMetadataResult {
  operation: string;
  totalRequested: number;
  successCount: number;
  failureCount: number;
  successfulIds: string[];
  failures: Record<string, string>;
}

class BulkMetadataService {
  async applyMetadata(request: BulkMetadataRequest): Promise<BulkMetadataResult> {
    return api.post<BulkMetadataResult>('/bulk/metadata', request);
  }
}

const bulkMetadataService = new BulkMetadataService();
export default bulkMetadataService;
