import api from './api';
import {
  clearActiveTenantDomain as clearStoredTenantDomain,
  DEFAULT_TENANT_DOMAIN,
  getActiveTenantDomain as getStoredTenantDomain,
  setActiveTenantDomain as setStoredTenantDomain,
} from 'utils/tenantContext';

export const TENANT_UNEXPECTED_RESPONSE_MESSAGE =
  'Tenant admin endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

export interface TenantDto {
  id: string;
  tenantDomain: string;
  tenantName: string;
  enabled: boolean;
  rootNodeId?: string | null;
  quotaBytes?: number | null;
  systemDefault: boolean;
  createdDate: string;
  lastModifiedDate?: string | null;
}

export interface TenantMutationRequest {
  tenantDomain: string;
  tenantName: string;
  enabled?: boolean;
  rootNodeId?: string | null;
  quotaBytes?: number | null;
}

export interface TenantMetricsDto {
  tenantDomain: string;
  tenantName: string;
  enabled: boolean;
  storageUsedBytes: number;
  quotaBytes: number | null;
  storageAvailableBytes: number | null;
  nodeCount: number;
  documentCount: number;
  folderCount: number;
}

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

const isNullableString = (value: unknown): value is string | null | undefined =>
  value === null || value === undefined || typeof value === 'string';

const isNullableNumber = (value: unknown): value is number | null | undefined =>
  value === null || value === undefined || typeof value === 'number';

function assertTenantResponse(condition: unknown): asserts condition {
  if (!condition) {
    throw new Error(TENANT_UNEXPECTED_RESPONSE_MESSAGE);
  }
}

const assertTenantDto = (value: unknown): TenantDto => {
  assertTenantResponse(isRecord(value));
  assertTenantResponse(typeof value.id === 'string');
  assertTenantResponse(typeof value.tenantDomain === 'string');
  assertTenantResponse(typeof value.tenantName === 'string');
  assertTenantResponse(typeof value.enabled === 'boolean');
  assertTenantResponse(isNullableString(value.rootNodeId));
  assertTenantResponse(isNullableNumber(value.quotaBytes));
  assertTenantResponse(typeof value.systemDefault === 'boolean');
  assertTenantResponse(typeof value.createdDate === 'string');
  assertTenantResponse(isNullableString(value.lastModifiedDate));

  return value as unknown as TenantDto;
};

const assertTenantList = (value: unknown): TenantDto[] => {
  assertTenantResponse(Array.isArray(value));
  return value.map(assertTenantDto);
};

const assertTenantMetrics = (value: unknown): TenantMetricsDto => {
  assertTenantResponse(isRecord(value));
  assertTenantResponse(typeof value.tenantDomain === 'string');
  assertTenantResponse(typeof value.tenantName === 'string');
  assertTenantResponse(typeof value.enabled === 'boolean');
  assertTenantResponse(typeof value.storageUsedBytes === 'number');
  assertTenantResponse(isNullableNumber(value.quotaBytes));
  assertTenantResponse(isNullableNumber(value.storageAvailableBytes));
  assertTenantResponse(typeof value.nodeCount === 'number');
  assertTenantResponse(typeof value.documentCount === 'number');
  assertTenantResponse(typeof value.folderCount === 'number');

  return value as unknown as TenantMetricsDto;
};

class TenantService {
  async listTenants(): Promise<TenantDto[]> {
    const result = await api.get<unknown>('/admin/tenants');
    return assertTenantList(result);
  }

  async getCurrentTenant(): Promise<TenantDto> {
    const result = await api.get<unknown>('/admin/tenants/current');
    return assertTenantDto(result);
  }

  async getTenant(tenantDomain: string): Promise<TenantDto> {
    const result = await api.get<unknown>(`/admin/tenants/${encodeURIComponent(tenantDomain)}`);
    return assertTenantDto(result);
  }

  async getTenantMetrics(tenantDomain: string): Promise<TenantMetricsDto> {
    const result = await api.get<unknown>(`/admin/tenants/${encodeURIComponent(tenantDomain)}/metrics`);
    return assertTenantMetrics(result);
  }

  async createTenant(payload: TenantMutationRequest): Promise<TenantDto> {
    const result = await api.post<unknown>('/admin/tenants', payload);
    return assertTenantDto(result);
  }

  async updateTenant(tenantDomain: string, payload: TenantMutationRequest): Promise<TenantDto> {
    const result = await api.put<unknown>(`/admin/tenants/${encodeURIComponent(tenantDomain)}`, payload);
    return assertTenantDto(result);
  }

  async deleteTenant(tenantDomain: string): Promise<void> {
    return api.delete<void>(`/admin/tenants/${encodeURIComponent(tenantDomain)}`);
  }

  getActiveTenantDomain(): string {
    return getStoredTenantDomain();
  }

  setActiveTenantDomain(tenantDomain: string): void {
    setStoredTenantDomain(tenantDomain);
  }

  clearActiveTenantDomain(): void {
    clearStoredTenantDomain();
  }
}

export { DEFAULT_TENANT_DOMAIN };
const tenantService = new TenantService();
export default tenantService;
