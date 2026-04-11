import React from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { toast } from 'react-toastify';
import TransferReplicationPage from './TransferReplicationPage';
import transferReplicationService from 'services/transferReplicationService';

jest.mock('react-toastify', () => ({
  toast: {
    error: jest.fn(),
    success: jest.fn(),
  },
}));

jest.mock('services/transferReplicationService', () => ({
  __esModule: true,
  buildReplicationDefinitionRequest: jest.fn((draft) => draft),
  default: {
    listTargets: jest.fn(),
    listReceivers: jest.fn(),
    listDefinitions: jest.fn(),
    listJobs: jest.fn(),
    retryJob: jest.fn(),
    createTarget: jest.fn(),
    updateTarget: jest.fn(),
    deleteTarget: jest.fn(),
    verifyTarget: jest.fn(),
    createReceiver: jest.fn(),
    updateReceiver: jest.fn(),
    deleteReceiver: jest.fn(),
    createDefinition: jest.fn(),
    updateDefinition: jest.fn(),
    deleteDefinition: jest.fn(),
    runDefinition: jest.fn(),
  },
}));

const mockedTransferReplicationService = transferReplicationService as jest.Mocked<typeof transferReplicationService>;
const toastErrorMock = toast.error as jest.Mock;

beforeEach(() => {
  jest.clearAllMocks();
  mockedTransferReplicationService.listTargets.mockResolvedValue([]);
  mockedTransferReplicationService.listReceivers.mockResolvedValue([]);
  mockedTransferReplicationService.listDefinitions.mockResolvedValue([]);
  mockedTransferReplicationService.listJobs.mockResolvedValue({
    content: [
      {
        id: 'job-1',
        definitionId: 'definition-1',
        transferTargetId: 'target-1',
        sourceNodeId: 'source-root',
        retryOfJobId: null,
        attemptNumber: 1,
        scheduledFor: null,
        copiedNodeId: null,
        userId: 'admin',
        status: 'COMPLETED',
        lastMessage: 'Job completed',
        transportStatus: 'SUCCESS',
        transportMessage: 'Transferred',
        errorLog: null,
        reportTruncated: true,
        entryReport: {
          totalEntries: 2,
          successCount: 1,
          failureCount: 1,
          entries: [
            {
              sourceNodeId: 'node-1',
              sourcePath: '/Sites/demo/document.txt',
              sourceType: 'cm:content',
              targetNodeId: 'target-node-1',
              action: 'CREATED',
              message: 'Created successfully',
              startedAt: '2026-04-11T10:00:00Z',
              completedAt: '2026-04-11T10:00:01Z',
            },
            {
              sourceNodeId: 'node-1',
              sourcePath: '/Sites/demo/document.txt',
              sourceType: 'cm:content',
              targetNodeId: null,
              action: 'FAILED',
              message: 'Conflict policy skipped duplicate',
              startedAt: '2026-04-11T10:00:02Z',
              completedAt: '2026-04-11T10:00:03Z',
            },
          ],
        },
        lastAttemptedAt: '2026-04-11T10:00:03Z',
        startedAt: '2026-04-11T10:00:00Z',
        completedAt: '2026-04-11T10:00:03Z',
        createdAt: '2026-04-11T10:00:00Z',
        updatedAt: '2026-04-11T10:00:03Z',
      },
    ],
    totalElements: 1,
    totalPages: 1,
    number: 0,
    size: 10,
  });
});

test('expands a replication job row to show entry report details and truncation warning', async () => {
  render(<TransferReplicationPage />);

  await screen.findByText('Transfer Replication');
  await waitFor(() => expect(mockedTransferReplicationService.listJobs).toHaveBeenCalledWith(0, 10));

  fireEvent.click(screen.getByRole('button', { name: 'Expand job job-1' }));

  expect(screen.getByText('Entry report')).toBeTruthy();
  expect(screen.getByText('The entry report was truncated by the backend, so only the first returned entries are shown here.')).toBeTruthy();
  expect(screen.getByText('Total 2')).toBeTruthy();
  expect(screen.getByText('Succeeded 1')).toBeTruthy();
  expect(screen.getByText('Failed 1')).toBeTruthy();
  expect(screen.getByText('Created successfully')).toBeTruthy();
  expect(screen.getByText('Conflict policy skipped duplicate')).toBeTruthy();

  const entryTables = screen.getAllByRole('table');
  const entryTable = entryTables[entryTables.length - 1];
  const sourceOccurrences = within(entryTable).getAllByText('/Sites/demo/document.txt');
  expect(sourceOccurrences).toHaveLength(2);
});

test('shows a toast when the replication job list fails to load', async () => {
  mockedTransferReplicationService.listJobs.mockRejectedValueOnce(new Error('load failed'));

  render(<TransferReplicationPage />);

  await waitFor(() => expect(toastErrorMock).toHaveBeenCalledWith('Failed to load transfer replication data'));
});
