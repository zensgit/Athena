import api from './api';
import {
  clearActiveTenantDomain as clearStoredTenantDomain,
  DEFAULT_TENANT_DOMAIN,
  getActiveTenantDomain as getStoredTenantDomain,
  setActiveTenantDomain as setStoredTenantDomain,
} from 'utils/tenantContext';

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

class TenantService {
  async listTenants(): Promise<TenantDto[]> {
    return api.get<TenantDto[]>('/admin/tenants');
  }

  async getCurrentTenant(): Promise<TenantDto> {
    return api.get<TenantDto>('/admin/tenants/current');
  }

  async getTenant(tenantDomain: string): Promise<TenantDto> {
    return api.get<TenantDto>(`/admin/tenants/${encodeURIComponent(tenantDomain)}`);
  }

  async createTenant(payload: TenantMutationRequest): Promise<TenantDto> {
    return api.post<TenantDto>('/admin/tenants', payload);
  }

  async updateTenant(tenantDomain: string, payload: TenantMutationRequest): Promise<TenantDto> {
    return api.put<TenantDto>(`/admin/tenants/${encodeURIComponent(tenantDomain)}`, payload);
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
