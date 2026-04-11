import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { toast } from 'react-toastify';
import CmisExplorerPage from './CmisExplorerPage';
import cmisService from 'services/cmisService';

jest.mock('react-toastify', () => ({
  toast: {
    error: jest.fn(),
    warning: jest.fn(),
  },
}));

jest.mock('services/cmisService', () => ({
  __esModule: true,
  default: {
    getRepositoryInfo: jest.fn(),
    getTypeChildren: jest.fn(),
    query: jest.fn(),
  },
}));

const mockedCmisService = cmisService as jest.Mocked<typeof cmisService>;
const toastWarningMock = toast.warning as jest.Mock;

beforeEach(() => {
  jest.clearAllMocks();
  mockedCmisService.getRepositoryInfo.mockResolvedValue({
    repositoryId: 'athena',
    repositoryName: 'Athena Repository',
    vendorName: 'Athena',
    productName: 'Athena ECM',
    productVersion: '1.0',
    cmisVersionSupported: '1.1',
    rootFolderId: 'root-folder-1',
    capabilities: ['QUERY', 'ACL'],
  });
  mockedCmisService.getTypeChildren.mockResolvedValue({
    types: [
      {
        id: 'cmis:document',
        displayName: 'Document',
        baseTypeId: 'cmis:document',
        creatable: true,
        fileable: true,
        queryable: true,
        propertyIds: ['cmis:name'],
      },
    ],
    totalNumItems: 1,
    hasMoreItems: false,
  });
  mockedCmisService.query.mockResolvedValue({
    repositoryId: 'athena',
    statement: 'SELECT * FROM cmis:document',
    objects: [
      {
        'cmis:objectId': 'doc-1',
        'cmis:name': 'Contract.pdf',
      },
      {
        'cmis:objectId': 'doc-2',
        'cmis:name': 'Proposal.pdf',
      },
    ],
    skipCount: 0,
    maxItems: 50,
    totalNumItems: 2,
    hasMoreItems: true,
  });
});

test('renders repository info on first load', async () => {
  render(<CmisExplorerPage />);

  expect(await screen.findByText('Athena Repository')).toBeTruthy();
  expect(screen.getByText('athena')).toBeTruthy();
  expect(screen.getByText('root-folder-1')).toBeTruthy();
  expect(mockedCmisService.getRepositoryInfo).toHaveBeenCalledTimes(1);
});

test('loads type definitions when switching to the type browser tab', async () => {
  render(<CmisExplorerPage />);

  await screen.findByText('Athena Repository');

  fireEvent.click(screen.getByRole('tab', { name: 'Type Browser' }));

  expect(await screen.findByText('Document')).toBeTruthy();
  expect(mockedCmisService.getTypeChildren).toHaveBeenCalledTimes(1);
});

test('runs a CMIS query and renders results with hasMoreItems hint', async () => {
  render(<CmisExplorerPage />);

  await screen.findByText('Athena Repository');

  fireEvent.click(screen.getByRole('tab', { name: 'Query Console' }));
  fireEvent.click(screen.getByRole('button', { name: 'Run' }));

  await waitFor(() => expect(mockedCmisService.query).toHaveBeenCalledWith('SELECT * FROM cmis:document'));
  expect(await screen.findByText(/2 results \(more available\)/)).toBeTruthy();
  expect(screen.getByText('Contract.pdf')).toBeTruthy();
  expect(screen.getByText('Proposal.pdf')).toBeTruthy();
});

test('reloads repository info after tenant changes', async () => {
  mockedCmisService.getRepositoryInfo
    .mockResolvedValueOnce({
      repositoryId: 'tenant-a',
      repositoryName: 'Tenant A Repository',
      vendorName: 'Athena',
      productName: 'Athena ECM',
      productVersion: '1.0',
      cmisVersionSupported: '1.1',
      rootFolderId: 'tenant-a-root',
      capabilities: ['QUERY'],
    })
    .mockResolvedValueOnce({
      repositoryId: 'tenant-b',
      repositoryName: 'Tenant B Repository',
      vendorName: 'Athena',
      productName: 'Athena ECM',
      productVersion: '1.0',
      cmisVersionSupported: '1.1',
      rootFolderId: 'tenant-b-root',
      capabilities: ['QUERY', 'ACL'],
    });

  render(<CmisExplorerPage />);

  expect(await screen.findByText('Tenant A Repository')).toBeTruthy();

  act(() => {
    window.dispatchEvent(new CustomEvent('athena:tenant-changed'));
  });

  await waitFor(() => expect(mockedCmisService.getRepositoryInfo).toHaveBeenCalledTimes(2));
  expect(await screen.findByText('Tenant B Repository')).toBeTruthy();
});

test('does not execute a blank query', async () => {
  render(<CmisExplorerPage />);

  await screen.findByText('Athena Repository');

  fireEvent.click(screen.getByRole('tab', { name: 'Query Console' }));
  fireEvent.change(screen.getByLabelText('CMIS-QL Statement'), { target: { value: '   ' } });
  fireEvent.click(screen.getByRole('button', { name: 'Run' }));

  expect(mockedCmisService.query).not.toHaveBeenCalled();
  expect(toastWarningMock).toHaveBeenCalledWith('Enter a CMIS-QL statement');
});
