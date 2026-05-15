import api from './api';

export const BULK_METADATA_UNEXPECTED_RESPONSE_MESSAGE =
  'Bulk metadata endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

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

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

const isStringArray = (value: unknown): value is string[] =>
  Array.isArray(value) && value.every((item) => typeof item === 'string');

const isStringRecord = (value: unknown): value is Record<string, string> =>
  isRecord(value)
  && Object.values(value).every((item) => typeof item === 'string');

const assertBulkMetadataResponse = (value: unknown): BulkMetadataResult => {
  if (!isRecord(value)
    || typeof value.operation !== 'string'
    || typeof value.totalRequested !== 'number'
    || typeof value.successCount !== 'number'
    || typeof value.failureCount !== 'number'
    || !isStringArray(value.successfulIds)
    || !isStringRecord(value.failures)) {
    throw new Error(BULK_METADATA_UNEXPECTED_RESPONSE_MESSAGE);
  }

  return value as unknown as BulkMetadataResult;
};

class BulkMetadataService {
  async applyMetadata(request: BulkMetadataRequest): Promise<BulkMetadataResult> {
    const result = await api.post<unknown>('/bulk/metadata', request);
    return assertBulkMetadataResponse(result);
  }
}

const bulkMetadataService = new BulkMetadataService();
export default bulkMetadataService;
