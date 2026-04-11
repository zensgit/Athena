import React from 'react';
import { act, render, screen, waitFor } from '@testing-library/react';
import TenantAdminPage from './TenantAdminPage';
import tenantService from 'services/tenantService';

jest.mock('services/tenantService', () => ({
  __esModule: true,
  DEFAULT_TENANT_DOMAIN: 'default',
  default: {
    listTenants: jest.fn(),
    getCurrentTenant: jest.fn(),
    getTenantMetrics: jest.fn(),
    getActiveTenantDomain: jest.fn(() => 'default'),
    setActiveTenantDomain: jest.fn(),
    clearActiveTenantDomain: jest.fn(),
  },
}));

class MockIntersectionObserver {
  static instances: MockIntersectionObserver[] = [];

  observed = new Set<Element>();

  constructor(private readonly callback: IntersectionObserverCallback) {
    MockIntersectionObserver.instances.push(this);
  }

  observe = (element: Element) => {
    this.observed.add(element);
  };

  unobserve = (element: Element) => {
    this.observed.delete(element);
  };

  disconnect = () => {
    this.observed.clear();
  };

  trigger(element: Element, isIntersecting = true) {
    this.callback(
      [
        {
          isIntersecting,
          target: element,
        } as IntersectionObserverEntry,
      ],
      this as unknown as IntersectionObserver
    );
  }
}

const mockedTenantService = tenantService as jest.Mocked<typeof tenantService>;

const tenants = [
  {
    id: 'tenant-1',
    tenantDomain: 'acme',
    tenantName: 'Acme',
    enabled: true,
    rootNodeId: 'root-acme',
    quotaBytes: 1000,
    systemDefault: false,
    createdDate: '2024-01-01T00:00:00Z',
    lastModifiedDate: null,
  },
  {
    id: 'tenant-2',
    tenantDomain: 'beta',
    tenantName: 'Beta',
    enabled: true,
    rootNodeId: 'root-beta',
    quotaBytes: 2000,
    systemDefault: false,
    createdDate: '2024-01-02T00:00:00Z',
    lastModifiedDate: null,
  },
];

beforeAll(() => {
  Object.defineProperty(window, 'IntersectionObserver', {
    configurable: true,
    writable: true,
    value: MockIntersectionObserver,
  });
});

beforeEach(() => {
  jest.clearAllMocks();
  MockIntersectionObserver.instances = [];
  mockedTenantService.listTenants.mockResolvedValue(tenants);
  mockedTenantService.getCurrentTenant.mockResolvedValue(tenants[0]);
  mockedTenantService.getTenantMetrics.mockImplementation(async (tenantDomain: string) => {
    if (tenantDomain === 'acme') {
      return {
        tenantDomain: 'acme',
        tenantName: 'Acme',
        enabled: true,
        storageUsedBytes: 250,
        quotaBytes: 1000,
        storageAvailableBytes: 750,
        nodeCount: 10,
        documentCount: 4,
        folderCount: 6,
      };
    }

    return {
      tenantDomain: 'beta',
      tenantName: 'Beta',
      enabled: true,
      storageUsedBytes: 1000,
      quotaBytes: 2000,
      storageAvailableBytes: 1000,
      nodeCount: 20,
      documentCount: 8,
      folderCount: 12,
    };
  });
});

test('loads tenant metrics when a tenant card becomes visible', async () => {
  render(<TenantAdminPage />);

  await screen.findByText('Acme');

  await waitFor(() => {
    expect(MockIntersectionObserver.instances).toHaveLength(1);
    expect(MockIntersectionObserver.instances[0].observed.size).toBe(2);
  });

  const observer = MockIntersectionObserver.instances[0];
  const acmeCard = [...observer.observed].find(
    (element) => (element as HTMLElement).dataset.tenantDomain === 'acme'
  );
  expect(acmeCard).toBeTruthy();

  act(() => {
    observer.trigger(acmeCard as Element);
  });

  await waitFor(() => {
    expect(mockedTenantService.getTenantMetrics).toHaveBeenCalledWith('acme');
  });

  expect(screen.getByText('250 bytes')).toBeTruthy();
  expect(screen.getByText('1,000 bytes')).toBeTruthy();
  expect(screen.getByText('750 bytes')).toBeTruthy();
  expect(screen.getByText('25% used')).toBeTruthy();
  expect(mockedTenantService.getTenantMetrics).not.toHaveBeenCalledWith('beta');
});
