import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { toast } from 'react-toastify';
import TenantMetricsDashboardPage from './TenantMetricsDashboardPage';
import tenantService from 'services/tenantService';

jest.mock('react-toastify', () => ({
  toast: {
    error: jest.fn(),
    warn: jest.fn(),
  },
}));

jest.mock('services/tenantService', () => ({
  __esModule: true,
  default: {
    listTenants: jest.fn(),
    getTenantMetrics: jest.fn(),
    getStorageCapacity: jest.fn(),
  },
}));

jest.mock('recharts', () => ({
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  BarChart: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  Bar: () => <div />,
  XAxis: () => <div />,
  YAxis: () => <div />,
  CartesianGrid: () => <div />,
  Tooltip: () => <div />,
  Legend: () => <div />,
  PieChart: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  Pie: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  Cell: () => <div />,
}));

const mockedTenantService = tenantService as jest.Mocked<typeof tenantService>;
const toastWarnMock = toast.warn as jest.Mock;

const tenants = [
  {
    id: 'tenant-1',
    tenantDomain: 'acme',
    tenantName: 'Acme',
    enabled: true,
    rootNodeId: 'root-acme',
    quotaBytes: 1000,
    systemDefault: false,
    createdDate: '2026-01-01T00:00:00Z',
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
    createdDate: '2026-01-02T00:00:00Z',
    lastModifiedDate: null,
  },
];

beforeEach(() => {
  jest.clearAllMocks();
  mockedTenantService.listTenants.mockResolvedValue(tenants);
  mockedTenantService.getTenantMetrics.mockImplementation(async (tenantDomain: string) => {
    if (tenantDomain === 'acme') {
      return {
        tenantDomain: 'acme',
        tenantName: 'Acme',
        enabled: true,
        storageUsedBytes: 1024,
        quotaBytes: 2048,
        storageAvailableBytes: 1024,
        nodeCount: 3,
        documentCount: 2,
        folderCount: 1,
      };
    }

    return {
      tenantDomain: 'beta',
      tenantName: 'Beta',
      enabled: true,
      storageUsedBytes: 512,
      quotaBytes: 2048,
      storageAvailableBytes: 1536,
      nodeCount: 4,
      documentCount: 1,
      folderCount: 3,
    };
  });
  mockedTenantService.getStorageCapacity.mockResolvedValue({
    backendType: 'filesystem',
    status: 'OK',
    totalBytes: 1024 * 1024,
    usableBytes: 900 * 1024,
    usedBytes: 124 * 1024,
    usedPercent: 12.109,
    warnPercent: 80,
    criticalPercent: 95,
    blockedMinFreeBytes: 104857600,
    rootPath: '/var/ecm/content',
    error: null,
  });
});

test('renders physical filesystem capacity separately from tenant logical usage', async () => {
  render(<TenantMetricsDashboardPage />);

  expect(await screen.findByText('Tenant Logical Usage')).toBeTruthy();
  expect(screen.getByText('1.5 KB')).toBeTruthy();
  expect(screen.getByText('Content Store Filesystem (Disk)')).toBeTruthy();
  expect(screen.getByText('900.0 KB free')).toBeTruthy();
  expect(screen.getByText('OK')).toBeTruthy();
  expect(screen.getByText('12.1% used of 1.0 MB')).toBeTruthy();
  expect(screen.getByText('Disk backing content root; upload-block signal, separate from tenant logical quota.')).toBeTruthy();

  await waitFor(() => expect(mockedTenantService.getStorageCapacity).toHaveBeenCalledTimes(1));
  expect(mockedTenantService.getTenantMetrics).toHaveBeenCalledWith('acme');
  expect(mockedTenantService.getTenantMetrics).toHaveBeenCalledWith('beta');
});

test('keeps tenant metrics visible when capacity fetch fails', async () => {
  mockedTenantService.getStorageCapacity.mockRejectedValueOnce(new Error('capacity unavailable'));

  render(<TenantMetricsDashboardPage />);

  expect(await screen.findByText('Tenant Logical Usage')).toBeTruthy();
  expect(screen.getByText('1.5 KB')).toBeTruthy();
  expect(screen.getByText('Content Store Filesystem (Disk)')).toBeTruthy();
  expect(screen.getByText('Unavailable')).toBeTruthy();
  expect(screen.getByText('Storage capacity unavailable. Tenant logical metrics are still shown.')).toBeTruthy();
  expect(toastWarnMock).toHaveBeenCalledWith('Failed to load storage capacity');
});
