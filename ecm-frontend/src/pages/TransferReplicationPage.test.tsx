import React from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { toast } from 'react-toastify';
import TransferReplicationPage from './TransferReplicationPage';
import transferReplicationService, {
  buildReplicationDefinitionRequest,
} from 'services/transferReplicationService';

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

jest.mock('components/browser/FolderTree', () => ({
  __esModule: true,
  default: ({ onNodeSelect, selectedNodeId }: any) => (
    <div data-testid="folder-tree">
      <span data-testid="folder-tree-selected">{selectedNodeId || ''}</span>
      <button
        type="button"
        onClick={() => onNodeSelect?.({ id: 'doc-1', name: 'Document', nodeType: 'DOCUMENT' })}
      >
        Pick document
      </button>
      <button
        type="button"
        onClick={() =>
          onNodeSelect?.({ id: 'folder-local-target', name: 'Local Replica Root', nodeType: 'FOLDER' })
        }
      >
        Pick local target folder
      </button>
      <button
        type="button"
        onClick={() =>
          onNodeSelect?.({ id: 'folder-source-root', name: 'Source Folder', nodeType: 'FOLDER' })
        }
      >
        Pick source folder
      </button>
      <button
        type="button"
        onClick={() =>
          onNodeSelect?.({ id: 'folder-receiver-root', name: 'Inbound Root', nodeType: 'FOLDER' })
        }
      >
        Pick receiver folder
      </button>
    </div>
  ),
}));

const mockedTransferReplicationService = transferReplicationService as jest.Mocked<typeof transferReplicationService>;
const mockedBuildReplicationDefinitionRequest = buildReplicationDefinitionRequest as jest.Mock;
const toastErrorMock = toast.error as jest.Mock;

beforeEach(() => {
  jest.clearAllMocks();
  mockedBuildReplicationDefinitionRequest.mockImplementation((draft) => draft);
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

test('creates a loopback transfer target with the local folder picker selection', async () => {
  mockedTransferReplicationService.createTarget.mockResolvedValueOnce({} as any);

  render(<TransferReplicationPage />);

  await screen.findByText('Transfer Replication');
  fireEvent.click(screen.getByRole('button', { name: 'New Target' }));

  fireEvent.change(screen.getByLabelText('Name'), {
    target: { value: 'Loopback target' },
  });
  fireEvent.click(screen.getByRole('button', { name: 'Pick document' }));

  const targetFolderInput = screen.getByLabelText('Target Folder ID') as HTMLInputElement;
  expect(targetFolderInput.value).toBe('');

  fireEvent.click(screen.getByRole('button', { name: 'Pick local target folder' }));

  expect(targetFolderInput.value).toBe('folder-local-target');
  expect(screen.getByText('Selected: Local Replica Root')).toBeTruthy();

  fireEvent.click(screen.getByRole('button', { name: 'Create Target' }));

  await waitFor(() =>
    expect(mockedTransferReplicationService.createTarget).toHaveBeenCalledWith({
      name: 'Loopback target',
      description: undefined,
      transportType: 'LOOPBACK',
      targetFolderId: 'folder-local-target',
      enabled: true,
    })
  );
});

test('creates a replication definition with the source folder picker selection', async () => {
  mockedTransferReplicationService.listTargets.mockResolvedValueOnce([
    {
      id: 'target-loopback',
      name: 'Loopback target',
      description: null,
      transportType: 'LOOPBACK',
      targetFolderId: 'folder-local-target',
      targetFolderName: 'Local Replica Root',
      endpointUrl: null,
      endpointPath: null,
      authType: 'NONE',
      authUsername: null,
      authSecretConfigured: false,
      enabled: true,
      verificationStatus: 'VERIFIED',
      verificationMessage: null,
      remoteRepositoryId: null,
      lastVerifiedAt: null,
      createdAt: '2026-05-13T00:00:00Z',
      updatedAt: null,
    },
  ]);
  mockedTransferReplicationService.createDefinition.mockResolvedValueOnce({} as any);

  render(<TransferReplicationPage />);

  await screen.findByText('Loopback target');
  fireEvent.click(screen.getByRole('button', { name: 'New Definition' }));

  fireEvent.change(screen.getByLabelText('Name'), {
    target: { value: 'Replicate source folder' },
  });
  fireEvent.click(screen.getByRole('button', { name: 'Pick document' }));

  const sourceNodeInput = screen.getByLabelText('Source Node ID') as HTMLInputElement;
  expect(sourceNodeInput.value).toBe('');

  fireEvent.click(screen.getByRole('button', { name: 'Pick source folder' }));

  expect(sourceNodeInput.value).toBe('folder-source-root');
  expect(screen.getByText('Selected: Source Folder')).toBeTruthy();

  fireEvent.click(screen.getByRole('button', { name: 'Create Definition' }));

  await waitFor(() =>
    expect(mockedTransferReplicationService.createDefinition).toHaveBeenCalledWith({
      name: 'Replicate source folder',
      description: '',
      sourceNodeId: 'folder-source-root',
      transferTargetId: 'target-loopback',
      includeChildren: true,
      enabled: true,
      conflictPolicy: 'RENAME',
      cronExpression: '',
      scheduleTimezone: 'UTC',
      autoRetryEnabled: false,
      maxRetryAttempts: '',
      retryBackoffMinutes: '',
      jobRetentionDays: '30',
    })
  );
});

test('creates a receiver registry entry with the folder picker root selection', async () => {
  mockedTransferReplicationService.createReceiver.mockResolvedValueOnce({} as any);

  render(<TransferReplicationPage />);

  await screen.findByText('Transfer Replication');
  fireEvent.click(screen.getByRole('button', { name: 'New Receiver' }));

  fireEvent.change(screen.getByLabelText('Name'), {
    target: { value: 'Inbound receiver' },
  });
  fireEvent.click(screen.getByRole('button', { name: 'Pick document' }));

  const rootFolderInput = screen.getByLabelText('Root Folder ID') as HTMLInputElement;
  expect(rootFolderInput.value).toBe('');

  fireEvent.click(screen.getByRole('button', { name: 'Pick receiver folder' }));

  expect(rootFolderInput.value).toBe('folder-receiver-root');
  expect(screen.getByText('Selected: Inbound Root')).toBeTruthy();

  fireEvent.click(screen.getByRole('button', { name: 'Create Receiver' }));

  await waitFor(() =>
    expect(mockedTransferReplicationService.createReceiver).toHaveBeenCalledWith({
      name: 'Inbound receiver',
      description: undefined,
      rootFolderId: 'folder-receiver-root',
      authType: 'NONE',
      authUsername: undefined,
      authSecret: undefined,
      enabled: true,
    })
  );
});
