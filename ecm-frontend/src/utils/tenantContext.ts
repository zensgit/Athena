export const TENANT_STORAGE_KEY = 'ecm_active_tenant';
export const DEFAULT_TENANT_DOMAIN = 'default';

export const getActiveTenantDomain = (): string => {
  if (typeof window === 'undefined') {
    return DEFAULT_TENANT_DOMAIN;
  }
  const stored = window.localStorage.getItem(TENANT_STORAGE_KEY);
  if (!stored || !stored.trim()) {
    return DEFAULT_TENANT_DOMAIN;
  }
  return stored.trim().toLowerCase();
};

export const setActiveTenantDomain = (tenantDomain: string): void => {
  if (typeof window === 'undefined') {
    return;
  }
  const normalized = tenantDomain.trim().toLowerCase();
  if (!normalized) {
    window.localStorage.removeItem(TENANT_STORAGE_KEY);
    return;
  }
  window.localStorage.setItem(TENANT_STORAGE_KEY, normalized);
};

export const clearActiveTenantDomain = (): void => {
  if (typeof window === 'undefined') {
    return;
  }
  window.localStorage.removeItem(TENANT_STORAGE_KEY);
};
