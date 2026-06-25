import tenantService, {
  DEFAULT_TENANT_DOMAIN,
  TENANT_UNEXPECTED_RESPONSE_MESSAGE,
  StorageCapacityStatusDto,
  TenantDto,
  TenantMetricsDto,
} from './tenantService';
import api from './api';
import { TENANT_STORAGE_KEY } from 'utils/tenantContext';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    put: jest.fn(),
    delete: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const tenant: TenantDto = {
  id: 'tenant-1',
  tenantDomain: 'acme',
  tenantName: 'Acme Corp',
  enabled: true,
  rootNodeId: null,
  quotaBytes: null,
  systemDefault: false,
  createdDate: '2026-05-15T00:00:00Z',
  lastModifiedDate: null,
};

const metrics: TenantMetricsDto = {
  tenantDomain: 'acme',
  tenantName: 'Acme Corp',
  enabled: true,
  storageUsedBytes: 1024,
  quotaBytes: null,
  storageAvailableBytes: null,
  nodeCount: 10,
  documentCount: 6,
  folderCount: 4,
};

const capacity: StorageCapacityStatusDto = {
  backendType: 'filesystem',
  status: 'WARN',
  totalBytes: 1000,
  usableBytes: 150,
  usedBytes: 850,
  usedPercent: 85,
  warnPercent: 80,
  criticalPercent: 95,
  blockedMinFreeBytes: 104857600,
  rootPath: '/var/ecm/content',
  error: null,
};

describe('tenantService active tenant helpers', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    window.localStorage.removeItem(TENANT_STORAGE_KEY);
  });

  it('falls back to the default tenant when no active tenant is stored', () => {
    expect(tenantService.getActiveTenantDomain()).toBe(DEFAULT_TENANT_DOMAIN);
  });

  it('persists the active tenant in localStorage and normalizes casing', () => {
    tenantService.setActiveTenantDomain('Acme');
    expect(window.localStorage.getItem(TENANT_STORAGE_KEY)).toBe('acme');
    expect(tenantService.getActiveTenantDomain()).toBe('acme');
  });

  it('clears the active tenant when explicitly cleared', () => {
    tenantService.setActiveTenantDomain('beta');
    tenantService.clearActiveTenantDomain();
    expect(window.localStorage.getItem(TENANT_STORAGE_KEY)).toBeNull();
    expect(tenantService.getActiveTenantDomain()).toBe(DEFAULT_TENANT_DOMAIN);
  });
});

describe('tenantService api wrappers', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('lists tenants from the admin endpoint', async () => {
    mockedApi.get.mockResolvedValueOnce([tenant]);

    await expect(tenantService.listTenants()).resolves.toEqual([tenant]);

    expect(mockedApi.get).toHaveBeenCalledWith('/admin/tenants');
  });

  it('fetches the current request tenant', async () => {
    mockedApi.get.mockResolvedValueOnce(tenant);

    await expect(tenantService.getCurrentTenant()).resolves.toEqual(tenant);

    expect(mockedApi.get).toHaveBeenCalledWith('/admin/tenants/current');
  });

  it('fetches one tenant with an encoded tenant domain', async () => {
    mockedApi.get.mockResolvedValueOnce(tenant);

    await expect(tenantService.getTenant('acme corp')).resolves.toEqual(tenant);

    expect(mockedApi.get).toHaveBeenCalledWith('/admin/tenants/acme%20corp');
  });

  it('creates tenants through the admin endpoint and returns a guarded tenant', async () => {
    const payload = {
      tenantDomain: 'beta',
      tenantName: 'Beta',
      enabled: true,
      rootNodeId: 'root-1',
      quotaBytes: 2048,
    };
    const created: TenantDto = {
      ...tenant,
      tenantDomain: 'beta',
      tenantName: 'Beta',
      rootNodeId: 'root-1',
      quotaBytes: 2048,
    };
    mockedApi.post.mockResolvedValueOnce(created);

    await expect(tenantService.createTenant(payload)).resolves.toEqual(created);

    expect(mockedApi.post).toHaveBeenCalledWith('/admin/tenants', payload);
  });

  it('encodes tenant domain when updating', async () => {
    mockedApi.put.mockResolvedValueOnce(tenant);

    await expect(tenantService.updateTenant('acme corp', {
      tenantDomain: 'acme corp',
      tenantName: 'Acme Corp',
    })).resolves.toEqual(tenant);

    expect(mockedApi.put).toHaveBeenCalledWith('/admin/tenants/acme%20corp', {
      tenantDomain: 'acme corp',
      tenantName: 'Acme Corp',
    });
  });

  it('encodes tenant domain when deleting', async () => {
    mockedApi.delete.mockResolvedValueOnce(undefined);
    await tenantService.deleteTenant('acme corp');
    expect(mockedApi.delete).toHaveBeenCalledWith('/admin/tenants/acme%20corp');
  });

  it('fetches tenant metrics with an encoded tenant domain', async () => {
    mockedApi.get.mockResolvedValueOnce(metrics);

    await expect(tenantService.getTenantMetrics('acme corp')).resolves.toEqual(metrics);

    expect(mockedApi.get).toHaveBeenCalledWith('/admin/tenants/acme%20corp/metrics');
  });

  it('fetches physical storage capacity from the admin endpoint', async () => {
    mockedApi.get.mockResolvedValueOnce(capacity);

    await expect(tenantService.getStorageCapacity()).resolves.toEqual(capacity);

    expect(mockedApi.get).toHaveBeenCalledWith('/admin/storage/capacity');
  });

  it('rejects HTML fallback for tenant lists', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(tenantService.listTenants()).rejects.toThrow(
      TENANT_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('rejects malformed tenant list entries', async () => {
    mockedApi.get.mockResolvedValueOnce([{ ...tenant, enabled: 'true' }]);

    await expect(tenantService.listTenants()).rejects.toThrow(
      TENANT_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('rejects malformed tenant metrics', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...metrics,
      storageUsedBytes: '1024',
    });

    await expect(tenantService.getTenantMetrics('acme')).rejects.toThrow(
      TENANT_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('rejects malformed storage capacity status', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...capacity,
      status: 'FULL',
    });

    await expect(tenantService.getStorageCapacity()).rejects.toThrow(
      TENANT_UNEXPECTED_RESPONSE_MESSAGE
    );
  });
});
