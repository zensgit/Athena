import api from './api';

export type PropertyDataType =
  | 'TEXT'
  | 'MLTEXT'
  | 'INT'
  | 'LONG'
  | 'FLOAT'
  | 'DOUBLE'
  | 'BOOLEAN'
  | 'DATE'
  | 'DATETIME'
  | 'ANY'
  | string;

export type BackfillJobStatus =
  | 'PLANNED'
  | 'RUNNING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCEL_REQUESTED'
  | 'CANCELLED';

export interface PropertyEncryptionStatus {
  secretCryptoEnabled: boolean;
  activeKeyVersion?: string | null;
  activeKeyConfigured: boolean;
  configuredKeyVersions: string[];
  encryptedPropertyDefinitionCount: number;
  encryptedTypePropertyDefinitionCount: number;
  encryptedAspectPropertyDefinitionCount: number;
  nodesWithEncryptedPropertiesCount: number;
  encryptedPropertyValueCount: number;
  warnings: string[];
}

export interface EncryptedPropertyDefinitionSummary {
  id: string;
  qualifiedName: string;
  name: string;
  title?: string | null;
  ownerKind: string;
  ownerQName?: string | null;
  dataType: PropertyDataType;
  mandatory: boolean;
  multiValued: boolean;
  indexed: boolean;
}

export interface KeyVersionValueCount {
  keyVersion: string;
  encryptedPropertyValueCount: number;
}

export interface PropertyEncryptionRewrapDryRunResult {
  targetKeyVersion?: string | null;
  targetKeyConfigured: boolean;
  secretCryptoEnabled: boolean;
  candidateNodeCount: number;
  encryptedPropertyValueCount: number;
  valuesAlreadyOnTargetKeyCount: number;
  valuesRequiringRewrapCount: number;
  unversionedOrMalformedValueCount: number;
  keyVersionCounts: KeyVersionValueCount[];
  missingSourceKeyVersions: string[];
  warnings: string[];
  executable: boolean;
}

export interface PropertyBackfillCount {
  qualifiedName: string;
  ownerKind: string;
  ownerQName?: string | null;
  plaintextValueCount: number;
  alreadyEncryptedValueCount: number;
  dualStorageConflictValueCount: number;
  readyValueCount: number;
}

export interface PropertyEncryptionBackfillDryRunResult {
  targetKeyVersion?: string | null;
  targetKeyConfigured: boolean;
  secretCryptoEnabled: boolean;
  encryptedPropertyDefinitionCount: number;
  plaintextValueCount: number;
  alreadyEncryptedValueCount: number;
  dualStorageConflictValueCount: number;
  readyValueCount: number;
  orphanEncryptedValueCount: number;
  definitionCounts: PropertyBackfillCount[];
  warnings: string[];
  executable: boolean;
}

export interface BackfillDefinitionCountSnapshot {
  qualifiedName: string;
  ownerKind: string;
  ownerQName?: string | null;
  plaintextValueCount: number;
  alreadyEncryptedValueCount: number;
  dualStorageConflictValueCount: number;
  readyValueCount: number;
}

export interface PropertyEncryptionBackfillJobDto {
  id: string;
  status: BackfillJobStatus;
  targetKeyVersion?: string | null;
  requestedBy: string;
  requestedAt: string;
  startedAt?: string | null;
  finishedAt?: string | null;
  encryptedPropertyDefinitionCount: number;
  plaintextValueCount: number;
  alreadyEncryptedValueCount: number;
  dualStorageConflictValueCount: number;
  readyValueCount: number;
  orphanEncryptedValueCount: number;
  processedValueCount: number;
  migratedValueCount: number;
  skippedValueCount: number;
  failedValueCount: number;
  warnings: string[];
  definitionCounts: BackfillDefinitionCountSnapshot[];
  lastError?: string | null;
  createdAt: string;
  updatedAt?: string | null;
}

const BASE_URL = '/admin/property-encryption';

class PropertyEncryptionService {
  async getStatus(): Promise<PropertyEncryptionStatus> {
    return api.get<PropertyEncryptionStatus>(`${BASE_URL}/status`);
  }

  async listDefinitions(): Promise<EncryptedPropertyDefinitionSummary[]> {
    return api.get<EncryptedPropertyDefinitionSummary[]>(`${BASE_URL}/definitions`);
  }

  async dryRunRewrap(targetKeyVersion?: string): Promise<PropertyEncryptionRewrapDryRunResult> {
    return api.post<PropertyEncryptionRewrapDryRunResult>(`${BASE_URL}/rewrap-jobs/dry-run`, {
      targetKeyVersion: targetKeyVersion?.trim() || undefined,
    });
  }

  async dryRunBackfill(targetKeyVersion?: string): Promise<PropertyEncryptionBackfillDryRunResult> {
    return api.post<PropertyEncryptionBackfillDryRunResult>(`${BASE_URL}/backfill-jobs/dry-run`, {
      targetKeyVersion: targetKeyVersion?.trim() || undefined,
    });
  }

  async planBackfillJob(targetKeyVersion?: string): Promise<PropertyEncryptionBackfillJobDto> {
    return api.post<PropertyEncryptionBackfillJobDto>(`${BASE_URL}/backfill-jobs/plan`, {
      targetKeyVersion: targetKeyVersion?.trim() || undefined,
    });
  }

  async listBackfillJobs(limit = 10): Promise<PropertyEncryptionBackfillJobDto[]> {
    return api.get<PropertyEncryptionBackfillJobDto[]>(`${BASE_URL}/backfill-jobs`, {
      params: { limit },
    });
  }

  async runBackfillJob(jobId: string, batchSize?: number): Promise<PropertyEncryptionBackfillJobDto> {
    return api.post<PropertyEncryptionBackfillJobDto>(`${BASE_URL}/backfill-jobs/${jobId}/run`, {
      batchSize,
    });
  }

  async cancelBackfillJob(jobId: string): Promise<PropertyEncryptionBackfillJobDto> {
    return api.post<PropertyEncryptionBackfillJobDto>(`${BASE_URL}/backfill-jobs/${jobId}/cancel`);
  }
}

export const propertyEncryptionService = new PropertyEncryptionService();
export default propertyEncryptionService;
