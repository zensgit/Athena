import api from './api';
import contentArchiveService, {
  ArchiveMutationDto,
  ArchivePolicyBatchExecutionDto,
  ArchivePolicyDto,
  ArchivePolicyDryRunDto,
  ArchivePolicyExecutionDto,
  ArchiveStatusDto,
  ArchivedNodeDto,
  ArchivedNodePage,
  CONTENT_ARCHIVE_UNEXPECTED_RESPONSE_MESSAGE,
} from './contentArchiveService';

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

const mutation: ArchiveMutationDto = {
  nodeId: 'node-1',
  name: 'Policy.pdf',
  archiveStatus: 'ARCHIVED',
  archiveStoreTier: 'COLD',
  archivedDate: '2026-05-17T08:00:00Z',
  archivedBy: 'admin',
  affectedNodeCount: 1,
};

const status: ArchiveStatusDto = {
  nodeId: 'node-1',
  name: 'Policy.pdf',
  nodeType: 'DOCUMENT',
  path: '/Sites/RM/Policy.pdf',
  archiveStatus: 'ARCHIVED',
  archiveStoreTier: 'COLD',
  archivedDate: null,
  archivedBy: null,
};

const archivedNode: ArchivedNodeDto = {
  nodeId: 'node-1',
  name: 'Policy.pdf',
  nodeType: 'DOCUMENT',
  path: '/Sites/RM/Policy.pdf',
  size: 1024,
  createdBy: 'admin',
  createdDate: '2026-05-16T08:00:00Z',
  archiveStatus: 'ARCHIVED',
  archiveStoreTier: 'COLD',
  archivedDate: '2026-05-17T08:00:00Z',
  archivedBy: 'admin',
};

const archivedPage: ArchivedNodePage = {
  content: [archivedNode],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 20,
};

const policy: ArchivePolicyDto = {
  policyId: 'policy-1',
  folderId: 'folder-1',
  folderName: 'Records',
  folderPath: '/Sites/RM/Records',
  enabled: true,
  inactivityDays: 30,
  storageTier: 'COLD',
  includeSubfolders: true,
  maxCandidatesPerRun: 100,
  lastDryRunAt: null,
  lastExecutedAt: null,
  lastCandidateCount: null,
  lastArchivedNodeCount: null,
  lastError: null,
};

const dryRun: ArchivePolicyDryRunDto = {
  folderId: 'folder-1',
  folderName: 'Records',
  cutoffDate: '2026-04-17T08:00:00Z',
  storageTier: 'COLD',
  includeSubfolders: true,
  maxCandidatesPerRun: 100,
  candidateCount: 1,
  candidates: [
    {
      nodeId: 'node-1',
      name: 'Policy.pdf',
      nodeType: 'DOCUMENT',
      path: '/Sites/RM/Records/Policy.pdf',
      activityDate: null,
    },
  ],
};

const execution: ArchivePolicyExecutionDto = {
  folderId: 'folder-1',
  folderName: 'Records',
  candidateCount: 1,
  archivedNodeCount: 1,
  failureCount: 0,
  failures: [],
  error: null,
};

const batch: ArchivePolicyBatchExecutionDto = {
  executedPolicies: 1,
  totalCandidates: 1,
  archivedNodeCount: 1,
  failureCount: 0,
  results: [execution],
};

describe('contentArchiveService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('archives nodes through the existing archive endpoint', async () => {
    mockedApi.post.mockResolvedValueOnce(mutation);

    await expect(contentArchiveService.archiveNode('node-1')).resolves.toEqual(mutation);

    expect(mockedApi.post).toHaveBeenCalledWith('/nodes/node-1/archive', { storageTier: 'COLD' });
  });

  it('restores archived nodes through the existing restore endpoint', async () => {
    mockedApi.post.mockResolvedValueOnce(mutation);

    await expect(contentArchiveService.restoreNode('node-1')).resolves.toEqual(mutation);

    expect(mockedApi.post).toHaveBeenCalledWith('/nodes/node-1/restore');
  });

  it('rejects HTML fallback for mutation responses', async () => {
    mockedApi.post.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(contentArchiveService.restoreNode('node-1')).rejects.toThrow(
      CONTENT_ARCHIVE_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('rejects invalid archive status enum values', async () => {
    mockedApi.post.mockResolvedValueOnce({ ...mutation, archiveStatus: 'DELETED' });

    await expect(contentArchiveService.archiveNode('node-1')).rejects.toThrow(
      CONTENT_ARCHIVE_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('gets archive status with guarded status data', async () => {
    mockedApi.get.mockResolvedValueOnce(status);

    await expect(contentArchiveService.getArchiveStatus('node-1')).resolves.toEqual(status);

    expect(mockedApi.get).toHaveBeenCalledWith('/nodes/node-1/archive-status');
  });

  it('lists archived nodes with paging params', async () => {
    mockedApi.get.mockResolvedValueOnce(archivedPage);

    await expect(contentArchiveService.listArchivedNodes()).resolves.toEqual(archivedPage);

    expect(mockedApi.get).toHaveBeenCalledWith('/nodes/archived', {
      params: { page: 0, size: 20 },
    });
  });

  it('rejects malformed archived page content', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...archivedPage,
      content: [{ ...archivedNode, size: '1024' }],
    });

    await expect(contentArchiveService.listArchivedNodes()).rejects.toThrow(
      CONTENT_ARCHIVE_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('gets and upserts archive policies', async () => {
    mockedApi.get.mockResolvedValueOnce(policy);
    mockedApi.put.mockResolvedValueOnce(policy);

    await expect(contentArchiveService.getArchivePolicy('folder-1')).resolves.toEqual(policy);
    await expect(contentArchiveService.upsertArchivePolicy('folder-1', {
      enabled: true,
      inactivityDays: 30,
      storageTier: 'COLD',
      includeSubfolders: true,
      maxCandidatesPerRun: 100,
    })).resolves.toEqual(policy);

    expect(mockedApi.get).toHaveBeenCalledWith('/folders/folder-1/archive-policy');
    expect(mockedApi.put).toHaveBeenCalledWith('/folders/folder-1/archive-policy', {
      enabled: true,
      inactivityDays: 30,
      storageTier: 'COLD',
      includeSubfolders: true,
      maxCandidatesPerRun: 100,
    });
  });

  it('rejects malformed archive policies', async () => {
    mockedApi.get.mockResolvedValueOnce({ ...policy, storageTier: 'FROZEN' });

    await expect(contentArchiveService.getArchivePolicy('folder-1')).rejects.toThrow(
      CONTENT_ARCHIVE_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('deletes archive policies without guarding a no-content response', async () => {
    mockedApi.delete.mockResolvedValueOnce(undefined);

    await expect(contentArchiveService.deleteArchivePolicy('folder-1')).resolves.toBeUndefined();

    expect(mockedApi.delete).toHaveBeenCalledWith('/folders/folder-1/archive-policy');
  });

  it('dry-runs archive policies with a default empty payload', async () => {
    mockedApi.post.mockResolvedValueOnce(dryRun);

    await expect(contentArchiveService.dryRunArchivePolicy('folder-1')).resolves.toEqual(dryRun);

    expect(mockedApi.post).toHaveBeenCalledWith('/folders/folder-1/archive-policy/dry-run', {});
  });

  it('rejects malformed dry-run candidates', async () => {
    mockedApi.post.mockResolvedValueOnce({
      ...dryRun,
      candidates: [{ ...dryRun.candidates[0], activityDate: 42 }],
    });

    await expect(contentArchiveService.dryRunArchivePolicy('folder-1')).rejects.toThrow(
      CONTENT_ARCHIVE_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('executes archive policies without changing the existing no-body post call', async () => {
    mockedApi.post.mockResolvedValueOnce(execution);

    await expect(contentArchiveService.executeArchivePolicy('folder-1')).resolves.toEqual(execution);

    expect(mockedApi.post).toHaveBeenCalledWith('/folders/folder-1/archive-policy/execute');
  });

  it('rejects malformed execution failures arrays', async () => {
    mockedApi.post.mockResolvedValueOnce({ ...execution, failures: ['ok', 7] });

    await expect(contentArchiveService.executeArchivePolicy('folder-1')).rejects.toThrow(
      CONTENT_ARCHIVE_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('lists archive policies and runs policy batches', async () => {
    mockedApi.get.mockResolvedValueOnce([policy]);
    mockedApi.post.mockResolvedValueOnce(batch);

    await expect(contentArchiveService.listArchivePolicies()).resolves.toEqual([policy]);
    await expect(contentArchiveService.runArchivePolicies()).resolves.toEqual(batch);

    expect(mockedApi.get).toHaveBeenCalledWith('/archive-policies');
    expect(mockedApi.post).toHaveBeenCalledWith('/archive-policies/run');
  });

  it('rejects malformed batch execution results', async () => {
    mockedApi.post.mockResolvedValueOnce({
      ...batch,
      results: [{ ...execution, archivedNodeCount: '1' }],
    });

    await expect(contentArchiveService.runArchivePolicies()).rejects.toThrow(
      CONTENT_ARCHIVE_UNEXPECTED_RESPONSE_MESSAGE
    );
  });
});
