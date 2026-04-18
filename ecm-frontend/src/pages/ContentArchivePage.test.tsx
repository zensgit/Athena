/* eslint-disable testing-library/no-node-access, testing-library/no-wait-for-multiple-assertions */
import React from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { toast } from 'react-toastify';
import ContentArchivePage from './ContentArchivePage';
import contentArchiveService from 'services/contentArchiveService';

jest.mock('react-toastify', () => ({
  toast: {
    error: jest.fn(),
    success: jest.fn(),
  },
}));

jest.mock('services/contentArchiveService', () => ({
  __esModule: true,
  default: {
    listArchivedNodes: jest.fn(),
    listArchivePolicies: jest.fn(),
    getArchiveStatus: jest.fn(),
    archiveNode: jest.fn(),
    restoreNode: jest.fn(),
    getArchivePolicy: jest.fn(),
    upsertArchivePolicy: jest.fn(),
    deleteArchivePolicy: jest.fn(),
    dryRunArchivePolicy: jest.fn(),
    executeArchivePolicy: jest.fn(),
    runArchivePolicies: jest.fn(),
  },
}));

const mockedContentArchiveService = contentArchiveService as jest.Mocked<typeof contentArchiveService>;
const toastSuccessMock = toast.success as jest.Mock;

describe('ContentArchivePage', () => {
  beforeEach(() => {
    jest.clearAllMocks();

    mockedContentArchiveService.listArchivedNodes.mockResolvedValue({
      content: [
        {
          nodeId: 'node-1',
          name: 'Archived Contract',
          nodeType: 'DOCUMENT',
          path: '/Archive/Contracts/Archived Contract.pdf',
          size: 1024,
          createdBy: 'admin',
          archiveStatus: 'ARCHIVED',
          archiveStoreTier: 'COLD',
          archivedDate: '2026-04-14T09:00:00',
          archivedBy: 'admin',
        },
      ],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 10,
    } as any);
    mockedContentArchiveService.listArchivePolicies.mockResolvedValue([]);
    mockedContentArchiveService.restoreNode.mockResolvedValue({
      nodeId: 'node-1',
      name: 'Archived Contract',
      archiveStatus: 'LIVE',
      archiveStoreTier: 'HOT',
      affectedNodeCount: 1,
    } as any);
    mockedContentArchiveService.getArchiveStatus.mockResolvedValue({
      nodeId: 'node-1',
      name: 'Archived Contract',
      nodeType: 'DOCUMENT',
      path: '/Archive/Contracts/Archived Contract.pdf',
      archiveStatus: 'LIVE',
      archiveStoreTier: 'HOT',
    } as any);
    mockedContentArchiveService.archiveNode.mockResolvedValue({ affectedNodeCount: 1, archiveStoreTier: 'COLD' } as any);
    mockedContentArchiveService.getArchivePolicy.mockResolvedValue({} as any);
    mockedContentArchiveService.upsertArchivePolicy.mockResolvedValue({} as any);
    mockedContentArchiveService.deleteArchivePolicy.mockResolvedValue(undefined as any);
    mockedContentArchiveService.dryRunArchivePolicy.mockResolvedValue({
      folderId: 'folder-1',
      folderName: 'Archive Folder',
      cutoffDate: '2026-01-01T00:00:00',
      storageTier: 'COLD',
      includeSubfolders: true,
      maxCandidatesPerRun: 100,
      candidateCount: 0,
      candidates: [],
    } as any);
    mockedContentArchiveService.executeArchivePolicy.mockResolvedValue({
      folderId: 'folder-1',
      folderName: 'Archive Folder',
      candidateCount: 0,
      archivedNodeCount: 0,
      failureCount: 0,
      failures: [],
    } as any);
    mockedContentArchiveService.runArchivePolicies.mockResolvedValue({
      executedPolicies: 0,
      totalCandidates: 0,
      archivedNodeCount: 0,
      failureCount: 0,
      results: [],
    } as any);
  });

  it('renders reopen copy only on the archive operator surface', async () => {
    render(<ContentArchivePage />);

    expect(await screen.findByRole('heading', { name: 'Content Archive' })).toBeTruthy();
    expect(await screen.findByText('Archived Contract')).toBeTruthy();
    expect(screen.getByText('Move nodes into archive storage tiers and reopen them back to HOT storage when needed.')).toBeTruthy();
    expect(screen.getByText(/reopen flow/i)).toBeTruthy();
    expect(screen.getAllByRole('button', { name: 'Reopen' }).length).toBe(2);
    expect(screen.queryByRole('button', { name: 'Restore' })).toBeNull();
  });

  it('reopens archived content through the existing restore service path', async () => {
    render(<ContentArchivePage />);

    const row = await screen.findByText('Archived Contract');
    const tableRow = row.closest('tr');
    expect(tableRow).toBeTruthy();

    fireEvent.click(within(tableRow as HTMLTableRowElement).getByRole('button', { name: 'Reopen' }));

    await waitFor(() => {
      expect(mockedContentArchiveService.restoreNode).toHaveBeenCalledWith('node-1');
    });
    expect(toastSuccessMock).toHaveBeenCalledWith('Reopened 1 node(s) to HOT storage');
  });
});
