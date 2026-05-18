import api from './api';

export const PROPERTY_ENCRYPTION_UNEXPECTED_RESPONSE_MESSAGE =
  'Property encryption endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

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

export type PropertyEncryptionJobStatus =
  | 'PLANNED'
  | 'RUNNING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCEL_REQUESTED'
  | 'CANCELLED';

export type BackfillJobStatus = PropertyEncryptionJobStatus;

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
  status: PropertyEncryptionJobStatus;
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

export interface RewrapKeyVersionCountSnapshot {
  keyVersion: string;
  encryptedPropertyValueCount: number;
}

export interface PropertyEncryptionRewrapJobDto {
  id: string;
  status: PropertyEncryptionJobStatus;
  targetKeyVersion?: string | null;
  requestedBy: string;
  requestedAt: string;
  startedAt?: string | null;
  finishedAt?: string | null;
  candidateNodeCount: number;
  encryptedPropertyValueCount: number;
  valuesAlreadyOnTargetKeyCount: number;
  valuesRequiringRewrapCount: number;
  unversionedOrMalformedValueCount: number;
  processedValueCount: number;
  rewrappedValueCount: number;
  skippedValueCount: number;
  failedValueCount: number;
  keyVersionCounts: RewrapKeyVersionCountSnapshot[];
  missingSourceKeyVersions: string[];
  warnings: string[];
  lastError?: string | null;
  createdAt: string;
  updatedAt?: string | null;
}

const BASE_URL = '/admin/property-encryption';

const PROPERTY_ENCRYPTION_JOB_STATUSES: PropertyEncryptionJobStatus[] = [
  'PLANNED',
  'RUNNING',
  'SUCCEEDED',
  'FAILED',
  'CANCEL_REQUESTED',
  'CANCELLED',
];

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

const isFiniteNumber = (value: unknown): value is number =>
  typeof value === 'number' && Number.isFinite(value);

const isNullableString = (value: unknown): value is string | null =>
  value === null || typeof value === 'string';

const isOptionalNullableString = (value: unknown): value is string | null | undefined =>
  value === undefined || isNullableString(value);

const isStringArray = (value: unknown): value is string[] =>
  Array.isArray(value) && value.every((item) => typeof item === 'string');

const isPropertyEncryptionJobStatus = (value: unknown): value is PropertyEncryptionJobStatus =>
  typeof value === 'string'
  && PROPERTY_ENCRYPTION_JOB_STATUSES.includes(value as PropertyEncryptionJobStatus);

function assertPropertyEncryptionResponse(condition: unknown): asserts condition {
  if (!condition) {
    throw new Error(PROPERTY_ENCRYPTION_UNEXPECTED_RESPONSE_MESSAGE);
  }
}

const assertPropertyEncryptionStatus = (value: unknown): PropertyEncryptionStatus => {
  assertPropertyEncryptionResponse(isRecord(value));
  assertPropertyEncryptionResponse(typeof value.secretCryptoEnabled === 'boolean');
  assertPropertyEncryptionResponse(isOptionalNullableString(value.activeKeyVersion));
  assertPropertyEncryptionResponse(typeof value.activeKeyConfigured === 'boolean');
  assertPropertyEncryptionResponse(isStringArray(value.configuredKeyVersions));
  assertPropertyEncryptionResponse(isFiniteNumber(value.encryptedPropertyDefinitionCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.encryptedTypePropertyDefinitionCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.encryptedAspectPropertyDefinitionCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.nodesWithEncryptedPropertiesCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.encryptedPropertyValueCount));
  assertPropertyEncryptionResponse(isStringArray(value.warnings));

  return value as unknown as PropertyEncryptionStatus;
};

const assertEncryptedPropertyDefinition = (
  value: unknown
): EncryptedPropertyDefinitionSummary => {
  assertPropertyEncryptionResponse(isRecord(value));
  assertPropertyEncryptionResponse(typeof value.id === 'string');
  assertPropertyEncryptionResponse(typeof value.qualifiedName === 'string');
  assertPropertyEncryptionResponse(typeof value.name === 'string');
  assertPropertyEncryptionResponse(isOptionalNullableString(value.title));
  assertPropertyEncryptionResponse(typeof value.ownerKind === 'string');
  assertPropertyEncryptionResponse(isOptionalNullableString(value.ownerQName));
  assertPropertyEncryptionResponse(typeof value.dataType === 'string');
  assertPropertyEncryptionResponse(typeof value.mandatory === 'boolean');
  assertPropertyEncryptionResponse(typeof value.multiValued === 'boolean');
  assertPropertyEncryptionResponse(typeof value.indexed === 'boolean');

  return value as unknown as EncryptedPropertyDefinitionSummary;
};

const assertEncryptedPropertyDefinitions = (
  value: unknown
): EncryptedPropertyDefinitionSummary[] => {
  assertPropertyEncryptionResponse(Array.isArray(value));
  return value.map(assertEncryptedPropertyDefinition);
};

const assertKeyVersionValueCount = (value: unknown): KeyVersionValueCount => {
  assertPropertyEncryptionResponse(isRecord(value));
  assertPropertyEncryptionResponse(typeof value.keyVersion === 'string');
  assertPropertyEncryptionResponse(isFiniteNumber(value.encryptedPropertyValueCount));

  return value as unknown as KeyVersionValueCount;
};

const assertKeyVersionValueCounts = (value: unknown): KeyVersionValueCount[] => {
  assertPropertyEncryptionResponse(Array.isArray(value));
  return value.map(assertKeyVersionValueCount);
};

const assertBackfillCount = (value: unknown): PropertyBackfillCount => {
  assertPropertyEncryptionResponse(isRecord(value));
  assertPropertyEncryptionResponse(typeof value.qualifiedName === 'string');
  assertPropertyEncryptionResponse(typeof value.ownerKind === 'string');
  assertPropertyEncryptionResponse(isOptionalNullableString(value.ownerQName));
  assertPropertyEncryptionResponse(isFiniteNumber(value.plaintextValueCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.alreadyEncryptedValueCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.dualStorageConflictValueCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.readyValueCount));

  return value as unknown as PropertyBackfillCount;
};

const assertBackfillCounts = (value: unknown): PropertyBackfillCount[] => {
  assertPropertyEncryptionResponse(Array.isArray(value));
  return value.map(assertBackfillCount);
};

const assertBackfillDefinitionCountSnapshot = (
  value: unknown
): BackfillDefinitionCountSnapshot => {
  assertPropertyEncryptionResponse(isRecord(value));
  assertPropertyEncryptionResponse(typeof value.qualifiedName === 'string');
  assertPropertyEncryptionResponse(typeof value.ownerKind === 'string');
  assertPropertyEncryptionResponse(isOptionalNullableString(value.ownerQName));
  assertPropertyEncryptionResponse(isFiniteNumber(value.plaintextValueCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.alreadyEncryptedValueCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.dualStorageConflictValueCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.readyValueCount));

  return value as unknown as BackfillDefinitionCountSnapshot;
};

const assertBackfillDefinitionCountSnapshots = (
  value: unknown
): BackfillDefinitionCountSnapshot[] => {
  assertPropertyEncryptionResponse(Array.isArray(value));
  return value.map(assertBackfillDefinitionCountSnapshot);
};

const assertRewrapKeyVersionCountSnapshot = (
  value: unknown
): RewrapKeyVersionCountSnapshot => {
  assertPropertyEncryptionResponse(isRecord(value));
  assertPropertyEncryptionResponse(typeof value.keyVersion === 'string');
  assertPropertyEncryptionResponse(isFiniteNumber(value.encryptedPropertyValueCount));

  return value as unknown as RewrapKeyVersionCountSnapshot;
};

const assertRewrapKeyVersionCountSnapshots = (
  value: unknown
): RewrapKeyVersionCountSnapshot[] => {
  assertPropertyEncryptionResponse(Array.isArray(value));
  return value.map(assertRewrapKeyVersionCountSnapshot);
};

const assertRewrapDryRunResult = (
  value: unknown
): PropertyEncryptionRewrapDryRunResult => {
  assertPropertyEncryptionResponse(isRecord(value));
  assertPropertyEncryptionResponse(isOptionalNullableString(value.targetKeyVersion));
  assertPropertyEncryptionResponse(typeof value.targetKeyConfigured === 'boolean');
  assertPropertyEncryptionResponse(typeof value.secretCryptoEnabled === 'boolean');
  assertPropertyEncryptionResponse(isFiniteNumber(value.candidateNodeCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.encryptedPropertyValueCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.valuesAlreadyOnTargetKeyCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.valuesRequiringRewrapCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.unversionedOrMalformedValueCount));
  const keyVersionCounts = assertKeyVersionValueCounts(value.keyVersionCounts);
  assertPropertyEncryptionResponse(isStringArray(value.missingSourceKeyVersions));
  assertPropertyEncryptionResponse(isStringArray(value.warnings));
  assertPropertyEncryptionResponse(typeof value.executable === 'boolean');

  return {
    ...value,
    keyVersionCounts,
  } as PropertyEncryptionRewrapDryRunResult;
};

const assertBackfillDryRunResult = (
  value: unknown
): PropertyEncryptionBackfillDryRunResult => {
  assertPropertyEncryptionResponse(isRecord(value));
  assertPropertyEncryptionResponse(isOptionalNullableString(value.targetKeyVersion));
  assertPropertyEncryptionResponse(typeof value.targetKeyConfigured === 'boolean');
  assertPropertyEncryptionResponse(typeof value.secretCryptoEnabled === 'boolean');
  assertPropertyEncryptionResponse(isFiniteNumber(value.encryptedPropertyDefinitionCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.plaintextValueCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.alreadyEncryptedValueCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.dualStorageConflictValueCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.readyValueCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.orphanEncryptedValueCount));
  const definitionCounts = assertBackfillCounts(value.definitionCounts);
  assertPropertyEncryptionResponse(isStringArray(value.warnings));
  assertPropertyEncryptionResponse(typeof value.executable === 'boolean');

  return {
    ...value,
    definitionCounts,
  } as PropertyEncryptionBackfillDryRunResult;
};

const assertRewrapJob = (value: unknown): PropertyEncryptionRewrapJobDto => {
  assertPropertyEncryptionResponse(isRecord(value));
  assertPropertyEncryptionResponse(typeof value.id === 'string');
  assertPropertyEncryptionResponse(isPropertyEncryptionJobStatus(value.status));
  assertPropertyEncryptionResponse(isOptionalNullableString(value.targetKeyVersion));
  assertPropertyEncryptionResponse(typeof value.requestedBy === 'string');
  assertPropertyEncryptionResponse(typeof value.requestedAt === 'string');
  assertPropertyEncryptionResponse(isOptionalNullableString(value.startedAt));
  assertPropertyEncryptionResponse(isOptionalNullableString(value.finishedAt));
  assertPropertyEncryptionResponse(isFiniteNumber(value.candidateNodeCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.encryptedPropertyValueCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.valuesAlreadyOnTargetKeyCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.valuesRequiringRewrapCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.unversionedOrMalformedValueCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.processedValueCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.rewrappedValueCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.skippedValueCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.failedValueCount));
  const keyVersionCounts = assertRewrapKeyVersionCountSnapshots(value.keyVersionCounts);
  assertPropertyEncryptionResponse(isStringArray(value.missingSourceKeyVersions));
  assertPropertyEncryptionResponse(isStringArray(value.warnings));
  assertPropertyEncryptionResponse(isOptionalNullableString(value.lastError));
  assertPropertyEncryptionResponse(typeof value.createdAt === 'string');
  assertPropertyEncryptionResponse(isOptionalNullableString(value.updatedAt));

  return {
    ...value,
    keyVersionCounts,
  } as PropertyEncryptionRewrapJobDto;
};

const assertRewrapJobs = (value: unknown): PropertyEncryptionRewrapJobDto[] => {
  assertPropertyEncryptionResponse(Array.isArray(value));
  return value.map(assertRewrapJob);
};

const assertBackfillJob = (value: unknown): PropertyEncryptionBackfillJobDto => {
  assertPropertyEncryptionResponse(isRecord(value));
  assertPropertyEncryptionResponse(typeof value.id === 'string');
  assertPropertyEncryptionResponse(isPropertyEncryptionJobStatus(value.status));
  assertPropertyEncryptionResponse(isOptionalNullableString(value.targetKeyVersion));
  assertPropertyEncryptionResponse(typeof value.requestedBy === 'string');
  assertPropertyEncryptionResponse(typeof value.requestedAt === 'string');
  assertPropertyEncryptionResponse(isOptionalNullableString(value.startedAt));
  assertPropertyEncryptionResponse(isOptionalNullableString(value.finishedAt));
  assertPropertyEncryptionResponse(isFiniteNumber(value.encryptedPropertyDefinitionCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.plaintextValueCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.alreadyEncryptedValueCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.dualStorageConflictValueCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.readyValueCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.orphanEncryptedValueCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.processedValueCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.migratedValueCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.skippedValueCount));
  assertPropertyEncryptionResponse(isFiniteNumber(value.failedValueCount));
  assertPropertyEncryptionResponse(isStringArray(value.warnings));
  const definitionCounts = assertBackfillDefinitionCountSnapshots(value.definitionCounts);
  assertPropertyEncryptionResponse(isOptionalNullableString(value.lastError));
  assertPropertyEncryptionResponse(typeof value.createdAt === 'string');
  assertPropertyEncryptionResponse(isOptionalNullableString(value.updatedAt));

  return {
    ...value,
    definitionCounts,
  } as PropertyEncryptionBackfillJobDto;
};

const assertBackfillJobs = (value: unknown): PropertyEncryptionBackfillJobDto[] => {
  assertPropertyEncryptionResponse(Array.isArray(value));
  return value.map(assertBackfillJob);
};

class PropertyEncryptionService {
  async getStatus(): Promise<PropertyEncryptionStatus> {
    const response = await api.get<unknown>(`${BASE_URL}/status`);
    return assertPropertyEncryptionStatus(response);
  }

  async listDefinitions(): Promise<EncryptedPropertyDefinitionSummary[]> {
    const response = await api.get<unknown>(`${BASE_URL}/definitions`);
    return assertEncryptedPropertyDefinitions(response);
  }

  async dryRunRewrap(targetKeyVersion?: string): Promise<PropertyEncryptionRewrapDryRunResult> {
    const response = await api.post<unknown>(`${BASE_URL}/rewrap-jobs/dry-run`, {
      targetKeyVersion: targetKeyVersion?.trim() || undefined,
    });
    return assertRewrapDryRunResult(response);
  }

  async dryRunBackfill(targetKeyVersion?: string): Promise<PropertyEncryptionBackfillDryRunResult> {
    const response = await api.post<unknown>(`${BASE_URL}/backfill-jobs/dry-run`, {
      targetKeyVersion: targetKeyVersion?.trim() || undefined,
    });
    return assertBackfillDryRunResult(response);
  }

  async planRewrapJob(targetKeyVersion?: string): Promise<PropertyEncryptionRewrapJobDto> {
    const response = await api.post<unknown>(`${BASE_URL}/rewrap-jobs/plan`, {
      targetKeyVersion: targetKeyVersion?.trim() || undefined,
    });
    return assertRewrapJob(response);
  }

  async listRewrapJobs(limit = 10): Promise<PropertyEncryptionRewrapJobDto[]> {
    const response = await api.get<unknown>(`${BASE_URL}/rewrap-jobs`, {
      params: { limit },
    });
    return assertRewrapJobs(response);
  }

  async runRewrapJob(jobId: string, batchSize?: number): Promise<PropertyEncryptionRewrapJobDto> {
    const response = await api.post<unknown>(`${BASE_URL}/rewrap-jobs/${jobId}/run`, {
      batchSize,
    });
    return assertRewrapJob(response);
  }

  async cancelRewrapJob(jobId: string): Promise<PropertyEncryptionRewrapJobDto> {
    const response = await api.post<unknown>(`${BASE_URL}/rewrap-jobs/${jobId}/cancel`);
    return assertRewrapJob(response);
  }

  async planBackfillJob(targetKeyVersion?: string): Promise<PropertyEncryptionBackfillJobDto> {
    const response = await api.post<unknown>(`${BASE_URL}/backfill-jobs/plan`, {
      targetKeyVersion: targetKeyVersion?.trim() || undefined,
    });
    return assertBackfillJob(response);
  }

  async listBackfillJobs(limit = 10): Promise<PropertyEncryptionBackfillJobDto[]> {
    const response = await api.get<unknown>(`${BASE_URL}/backfill-jobs`, {
      params: { limit },
    });
    return assertBackfillJobs(response);
  }

  async runBackfillJob(jobId: string, batchSize?: number): Promise<PropertyEncryptionBackfillJobDto> {
    const response = await api.post<unknown>(`${BASE_URL}/backfill-jobs/${jobId}/run`, {
      batchSize,
    });
    return assertBackfillJob(response);
  }

  async cancelBackfillJob(jobId: string): Promise<PropertyEncryptionBackfillJobDto> {
    const response = await api.post<unknown>(`${BASE_URL}/backfill-jobs/${jobId}/cancel`);
    return assertBackfillJob(response);
  }
}

export const propertyEncryptionService = new PropertyEncryptionService();
export default propertyEncryptionService;
