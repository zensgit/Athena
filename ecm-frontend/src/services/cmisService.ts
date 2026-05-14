import api from './api';

export const CMIS_UNEXPECTED_RESPONSE_MESSAGE =
  'CMIS browser endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isNumber = (value: unknown): value is number => (
  typeof value === 'number' && Number.isFinite(value)
);

const isStringArray = (value: unknown): value is string[] => (
  Array.isArray(value) && value.every((item) => typeof item === 'string')
);

export type CmisRepositoryInfo = {
  repositoryId: string;
  repositoryName: string;
  vendorName: string;
  productName: string;
  productVersion: string;
  cmisVersionSupported: string;
  rootFolderId: string;
  capabilities: string[];
};

export type CmisTypeDefinition = {
  id: string;
  displayName: string;
  baseTypeId: string;
  creatable: boolean;
  fileable: boolean;
  queryable: boolean;
  propertyIds: string[];
};

export type CmisTypeChildrenResponse = {
  types: CmisTypeDefinition[];
  totalNumItems: number;
  hasMoreItems: boolean;
};

export type CmisQueryRow = Record<string, unknown>;

export type CmisQueryResponse = {
  repositoryId: string;
  statement: string;
  objects: CmisQueryRow[];
  skipCount: number;
  maxItems: number;
  totalNumItems: number;
  hasMoreItems: boolean;
};

const isCmisRepositoryInfo = (value: unknown): value is CmisRepositoryInfo => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.repositoryId === 'string'
    && typeof value.repositoryName === 'string'
    && typeof value.vendorName === 'string'
    && typeof value.productName === 'string'
    && typeof value.productVersion === 'string'
    && typeof value.cmisVersionSupported === 'string'
    && typeof value.rootFolderId === 'string'
    && isStringArray(value.capabilities);
};

const assertCmisRepositoryInfo = (value: unknown): CmisRepositoryInfo => {
  if (!isCmisRepositoryInfo(value)) {
    throw new Error(CMIS_UNEXPECTED_RESPONSE_MESSAGE);
  }
  return value;
};

const isCmisTypeDefinition = (value: unknown): value is CmisTypeDefinition => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.displayName === 'string'
    && typeof value.baseTypeId === 'string'
    && typeof value.creatable === 'boolean'
    && typeof value.fileable === 'boolean'
    && typeof value.queryable === 'boolean'
    && isStringArray(value.propertyIds);
};

const isCmisTypeChildrenResponse = (value: unknown): value is CmisTypeChildrenResponse => {
  if (!isObject(value)) {
    return false;
  }
  return Array.isArray(value.types)
    && value.types.every(isCmisTypeDefinition)
    && isNumber(value.totalNumItems)
    && typeof value.hasMoreItems === 'boolean';
};

const assertCmisTypeChildrenResponse = (value: unknown): CmisTypeChildrenResponse => {
  if (!isCmisTypeChildrenResponse(value)) {
    throw new Error(CMIS_UNEXPECTED_RESPONSE_MESSAGE);
  }
  return value;
};

const isCmisQueryRow = (value: unknown): value is CmisQueryRow => isObject(value);

const isCmisQueryResponse = (value: unknown): value is CmisQueryResponse => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.repositoryId === 'string'
    && typeof value.statement === 'string'
    && Array.isArray(value.objects)
    && value.objects.every(isCmisQueryRow)
    && isNumber(value.skipCount)
    && isNumber(value.maxItems)
    && isNumber(value.totalNumItems)
    && typeof value.hasMoreItems === 'boolean';
};

const assertCmisQueryResponse = (value: unknown): CmisQueryResponse => {
  if (!isCmisQueryResponse(value)) {
    throw new Error(CMIS_UNEXPECTED_RESPONSE_MESSAGE);
  }
  return value;
};

class CmisService {
  async getRepositoryInfo(): Promise<CmisRepositoryInfo> {
    const result = await api.get<unknown>('/cmis/browser', {
      params: { cmisselector: 'repositoryInfo' },
    });
    return assertCmisRepositoryInfo(result);
  }

  async getTypeChildren(): Promise<CmisTypeChildrenResponse> {
    const result = await api.get<unknown>('/cmis/browser', {
      params: { cmisselector: 'typeChildren' },
    });
    return assertCmisTypeChildrenResponse(result);
  }

  async query(statement: string, skipCount = 0, maxItems = 50): Promise<CmisQueryResponse> {
    const result = await api.get<unknown>('/cmis/browser', {
      params: { cmisselector: 'query', statement, skipCount, maxItems },
    });
    return assertCmisQueryResponse(result);
  }
}

const cmisService = new CmisService();
export default cmisService;
