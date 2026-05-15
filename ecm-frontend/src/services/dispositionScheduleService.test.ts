import api from './api';
import dispositionScheduleService, {
  DISPOSITION_SCHEDULE_UNEXPECTED_RESPONSE_MESSAGE,
  DispositionActionExecutionDto,
  DispositionBatchExecutionDto,
  DispositionCandidateDto,
  DispositionDryRunDto,
  DispositionExecutionDto,
  DispositionPage,
  DispositionScheduleDto,
} from './dispositionScheduleService';

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

const FOLDER_ID = 'folder-1';

const schedule: DispositionScheduleDto = {
  id: 'sched-1',
  folderId: FOLDER_ID,
  folderName: 'Records',
  folderPath: '/sites/rm/records',
  enabled: true,
  includeSubfolders: false,
  cutoffAfterDays: 30,
  archiveAfterCutoffDays: 60,
  destroyAfterArchiveDays: 365,
  archiveStorageTier: 'COLD',
  maxCandidatesPerAction: 100,
  lastDryRunAt: '2026-05-14T12:00:00',
  lastExecutedAt: '2026-05-14T13:00:00',
  lastError: null,
};

const minimalSchedule: DispositionScheduleDto = {
  id: 'sched-2',
  folderId: 'folder-2',
  folderName: 'Empty',
  folderPath: '/sites/rm/empty',
  enabled: false,
  includeSubfolders: false,
  cutoffAfterDays: null,
  archiveAfterCutoffDays: null,
  destroyAfterArchiveDays: null,
  archiveStorageTier: null,
  maxCandidatesPerAction: null,
  lastDryRunAt: null,
  lastExecutedAt: null,
  lastError: null,
};

const candidate: DispositionCandidateDto = {
  nodeId: 'node-1',
  name: 'doc.pdf',
  nodeType: 'document',
  path: '/sites/rm/records/doc.pdf',
  actionType: 'CUTOFF',
  eligibleAt: '2026-05-14T12:00:00',
  blockedByHoldNames: null,
};

const dryRun: DispositionDryRunDto = {
  folderId: FOLDER_ID,
  folderName: 'Records',
  includeSubfolders: false,
  archiveStorageTier: 'COLD',
  maxCandidatesPerAction: 100,
  cutoffCount: 1,
  archiveCount: 0,
  destroyCount: 0,
  candidates: [candidate],
};

const execution: DispositionExecutionDto = {
  folderId: FOLDER_ID,
  folderName: 'Records',
  cutoffCount: 1,
  archiveCandidateCount: 0,
  archivedNodeCount: 0,
  destroyCandidateCount: 0,
  destroyedNodeCount: 0,
  failureCount: 0,
  blockedCount: 0,
  failures: [],
  error: null,
};

const batch: DispositionBatchExecutionDto = {
  executedSchedules: 1,
  cutoffCount: 1,
  archivedNodeCount: 0,
  destroyedNodeCount: 0,
  blockedCount: 0,
  failureCount: 0,
  results: [execution],
};

const actionExecution: DispositionActionExecutionDto = {
  id: 'exec-1',
  actionType: 'CUTOFF',
  status: 'SUCCESS',
  nodeId: 'node-1',
  nodeName: 'doc.pdf',
  nodeType: 'document',
  nodePath: '/sites/rm/records/doc.pdf',
  affectedNodeCount: 1,
  details: null,
  actor: 'admin',
  executedAt: '2026-05-14T13:00:00',
};

const page = (
  content: DispositionActionExecutionDto[] = [actionExecution],
): DispositionPage<DispositionActionExecutionDto> => ({
  content,
  totalElements: content.length,
  totalPages: 1,
  number: 0,
  size: 10,
});

describe('dispositionScheduleService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('listSchedules', () => {
    it('forwards GET and returns guarded schedules', async () => {
      mockedApi.get.mockResolvedValueOnce([schedule, minimalSchedule]);

      await expect(dispositionScheduleService.listSchedules()).resolves.toEqual([
        schedule,
        minimalSchedule,
      ]);
      expect(mockedApi.get).toHaveBeenCalledWith('/disposition-schedules');
    });

    it('rejects HTML fallback', async () => {
      mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

      await expect(dispositionScheduleService.listSchedules()).rejects.toThrow(
        DISPOSITION_SCHEDULE_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });

    it('rejects a schedule whose archiveStorageTier is outside the closed union', async () => {
      mockedApi.get.mockResolvedValueOnce([
        { ...schedule, archiveStorageTier: 'FROZEN' as unknown as string },
      ]);

      await expect(dispositionScheduleService.listSchedules()).rejects.toThrow(
        DISPOSITION_SCHEDULE_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });
  });

  describe('getSchedule', () => {
    it('forwards the folder path and returns a guarded schedule', async () => {
      mockedApi.get.mockResolvedValueOnce(schedule);

      await expect(dispositionScheduleService.getSchedule(FOLDER_ID)).resolves.toEqual(schedule);
      expect(mockedApi.get).toHaveBeenCalledWith(`/folders/${FOLDER_ID}/disposition-schedule`);
    });

    it('accepts a schedule whose archiveStorageTier is null', async () => {
      mockedApi.get.mockResolvedValueOnce(minimalSchedule);

      await expect(dispositionScheduleService.getSchedule('folder-2')).resolves.toEqual(
        minimalSchedule,
      );
    });

    it('rejects HTML fallback', async () => {
      mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

      await expect(dispositionScheduleService.getSchedule(FOLDER_ID)).rejects.toThrow(
        DISPOSITION_SCHEDULE_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });

    it('rejects a malformed schedule where cutoffAfterDays is a string', async () => {
      mockedApi.get.mockResolvedValueOnce({ ...schedule, cutoffAfterDays: '30' });

      await expect(dispositionScheduleService.getSchedule(FOLDER_ID)).rejects.toThrow(
        DISPOSITION_SCHEDULE_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });
  });

  describe('upsertSchedule', () => {
    it('forwards PUT with the upsert payload and returns the guarded schedule', async () => {
      mockedApi.put.mockResolvedValueOnce(schedule);

      const payload = { enabled: true, archiveStorageTier: 'COLD' as const };
      await expect(
        dispositionScheduleService.upsertSchedule(FOLDER_ID, payload),
      ).resolves.toEqual(schedule);
      expect(mockedApi.put).toHaveBeenCalledWith(
        `/folders/${FOLDER_ID}/disposition-schedule`,
        payload,
      );
    });

    it('rejects HTML fallback on upsert', async () => {
      mockedApi.put.mockResolvedValueOnce('<!doctype html><html></html>');

      await expect(
        dispositionScheduleService.upsertSchedule(FOLDER_ID, {}),
      ).rejects.toThrow(DISPOSITION_SCHEDULE_UNEXPECTED_RESPONSE_MESSAGE);
    });
  });

  describe('deleteSchedule', () => {
    it('forwards DELETE and resolves to void for a no-content response', async () => {
      mockedApi.delete.mockResolvedValueOnce(undefined as unknown as void);

      await expect(dispositionScheduleService.deleteSchedule(FOLDER_ID)).resolves.toBeUndefined();
      expect(mockedApi.delete).toHaveBeenCalledWith(`/folders/${FOLDER_ID}/disposition-schedule`);
    });
  });

  describe('dryRun', () => {
    it('forwards POST with an empty body when no payload is provided', async () => {
      mockedApi.post.mockResolvedValueOnce(dryRun);

      await expect(dispositionScheduleService.dryRun(FOLDER_ID)).resolves.toEqual(dryRun);
      expect(mockedApi.post).toHaveBeenCalledWith(
        `/folders/${FOLDER_ID}/disposition-schedule/dry-run`,
        {},
      );
    });

    it('forwards POST with the provided payload', async () => {
      mockedApi.post.mockResolvedValueOnce(dryRun);

      const payload = { includeSubfolders: true };
      await dispositionScheduleService.dryRun(FOLDER_ID, payload);
      expect(mockedApi.post).toHaveBeenCalledWith(
        `/folders/${FOLDER_ID}/disposition-schedule/dry-run`,
        payload,
      );
    });

    it('rejects HTML fallback', async () => {
      mockedApi.post.mockResolvedValueOnce('<!doctype html><html></html>');

      await expect(dispositionScheduleService.dryRun(FOLDER_ID)).rejects.toThrow(
        DISPOSITION_SCHEDULE_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });

    it('rejects a dry-run whose candidate actionType is outside the closed union', async () => {
      mockedApi.post.mockResolvedValueOnce({
        ...dryRun,
        candidates: [{ ...candidate, actionType: 'REVIEW' as DispositionCandidateDto['actionType'] }],
      });

      await expect(dispositionScheduleService.dryRun(FOLDER_ID)).rejects.toThrow(
        DISPOSITION_SCHEDULE_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });

    it('rejects a dry-run whose archiveStorageTier is missing', async () => {
      mockedApi.post.mockResolvedValueOnce({ ...dryRun, archiveStorageTier: null });

      await expect(dispositionScheduleService.dryRun(FOLDER_ID)).rejects.toThrow(
        DISPOSITION_SCHEDULE_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });
  });

  describe('execute', () => {
    it('forwards POST to the execute path and returns a guarded execution', async () => {
      mockedApi.post.mockResolvedValueOnce(execution);

      await expect(dispositionScheduleService.execute(FOLDER_ID)).resolves.toEqual(execution);
      expect(mockedApi.post).toHaveBeenCalledWith(
        `/folders/${FOLDER_ID}/disposition-schedule/execute`,
        {},
      );
    });

    it('rejects HTML fallback', async () => {
      mockedApi.post.mockResolvedValueOnce('<!doctype html><html></html>');

      await expect(dispositionScheduleService.execute(FOLDER_ID)).rejects.toThrow(
        DISPOSITION_SCHEDULE_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });

    it('rejects an execution whose failures array contains non-string entries', async () => {
      mockedApi.post.mockResolvedValueOnce({
        ...execution,
        failures: ['ok', 42 as unknown as string],
      });

      await expect(dispositionScheduleService.execute(FOLDER_ID)).rejects.toThrow(
        DISPOSITION_SCHEDULE_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });
  });

  describe('listExecutions', () => {
    it('forwards default paging and returns a guarded page', async () => {
      const response = page();
      mockedApi.get.mockResolvedValueOnce(response);

      await expect(dispositionScheduleService.listExecutions(FOLDER_ID)).resolves.toEqual(response);
      expect(mockedApi.get).toHaveBeenCalledWith(
        `/folders/${FOLDER_ID}/disposition-schedule/executions`,
        { params: { page: 0, size: 10 } },
      );
    });

    it('forwards custom paging', async () => {
      mockedApi.get.mockResolvedValueOnce(page());

      await dispositionScheduleService.listExecutions(FOLDER_ID, 2, 25);
      expect(mockedApi.get).toHaveBeenCalledWith(
        `/folders/${FOLDER_ID}/disposition-schedule/executions`,
        { params: { page: 2, size: 25 } },
      );
    });

    it('rejects HTML fallback', async () => {
      mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

      await expect(dispositionScheduleService.listExecutions(FOLDER_ID)).rejects.toThrow(
        DISPOSITION_SCHEDULE_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });

    it('rejects a page item whose actor is not a string', async () => {
      mockedApi.get.mockResolvedValueOnce(page([
        { ...actionExecution, actor: 7 as unknown as string },
      ]));

      await expect(dispositionScheduleService.listExecutions(FOLDER_ID)).rejects.toThrow(
        DISPOSITION_SCHEDULE_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });

    it('rejects a page item whose status is outside the closed union', async () => {
      mockedApi.get.mockResolvedValueOnce(page([
        { ...actionExecution, status: 'PENDING' as DispositionActionExecutionDto['status'] },
      ]));

      await expect(dispositionScheduleService.listExecutions(FOLDER_ID)).rejects.toThrow(
        DISPOSITION_SCHEDULE_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });

    it('rejects a page envelope whose totalPages is a string', async () => {
      mockedApi.get.mockResolvedValueOnce({ ...page(), totalPages: '1' });

      await expect(dispositionScheduleService.listExecutions(FOLDER_ID)).rejects.toThrow(
        DISPOSITION_SCHEDULE_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });
  });

  describe('runAll', () => {
    it('forwards POST to /disposition-schedules/run and returns a guarded batch', async () => {
      mockedApi.post.mockResolvedValueOnce(batch);

      await expect(dispositionScheduleService.runAll()).resolves.toEqual(batch);
      expect(mockedApi.post).toHaveBeenCalledWith('/disposition-schedules/run', {});
    });

    it('rejects HTML fallback', async () => {
      mockedApi.post.mockResolvedValueOnce('<!doctype html><html></html>');

      await expect(dispositionScheduleService.runAll()).rejects.toThrow(
        DISPOSITION_SCHEDULE_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });

    it('rejects a batch whose nested execution is malformed', async () => {
      mockedApi.post.mockResolvedValueOnce({
        ...batch,
        results: [{ ...execution, failures: 'oops' as unknown as string[] }],
      });

      await expect(dispositionScheduleService.runAll()).rejects.toThrow(
        DISPOSITION_SCHEDULE_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });
  });
});
