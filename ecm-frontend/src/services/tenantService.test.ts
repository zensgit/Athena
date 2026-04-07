import tenantService, { DEFAULT_TENANT_DOMAIN } from './tenantService';
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
    mockedApi.get.mockResolvedValueOnce([]);
    await tenantService.listTenants();
    expect(mockedApi.get).toHaveBeenCalledWith('/admin/tenants');
  });

  it('fetches the current request tenant', async () => {
    mockedApi.get.mockResolvedValueOnce({});
    await tenantService.getCurrentTenant();
    expect(mockedApi.get).toHaveBeenCalledWith('/admin/tenants/current');
  });

  it('encodes tenant domain when updating', async () => {
    mockedApi.put.mockResolvedValueOnce({});
    await tenantService.updateTenant('acme corp', {
      tenantDomain: 'acme corp',
      tenantName: 'Acme Corp',
    });
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
});
