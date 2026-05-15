import api from './api';
import bulkImportService, {
  BULK_IMPORT_UNEXPECTED_RESPONSE_MESSAGE,
  ImportJobDto,
  ImportJobPage,
} from './bulkImportService';
import { BulkImportSelectionFile } from 'utils/bulkImportUtils';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    delete: jest.fn(),
    postFormData: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const job: ImportJobDto = {
  id: 'job-1',
  userId: 'admin',
  status: 'RUNNING',
  targetFolderId: 'folder-1',
  conflictPolicy: 'SKIP',
  totalFiles: 10,
  processedFiles: 4,
  importedFiles: 3,
  skippedFiles: 1,
  failedFiles: 0,
  currentItemPath: 'docs/a.pdf',
  lastMessage: 'processing',
  errorLog: null,
  startedAt: '2026-05-15T10:00:00',
  completedAt: null,
  createdAt: '2026-05-15T09:59:00',
  updatedAt: '2026-05-15T10:00:30',
};

const nullableJob: ImportJobDto = {
  id: 'job-2',
  userId: 'editor',
  status: 'PENDING',
  conflictPolicy: 'OVERWRITE',
  totalFiles: 0,
  processedFiles: 0,
  importedFiles: 0,
  skippedFiles: 0,
  failedFiles: 0,
  createdAt: '2026-05-15T11:00:00',
};

const page = (content: ImportJobDto[] = [job]): ImportJobPage => ({
  content,
  totalElements: content.length,
  totalPages: 1,
  number: 0,
  size: 20,
});

const makeSelectionFile = (
  name: string,
  webkitRelativePath: string
): BulkImportSelectionFile => {
  const file = new File(['x'], name, { type: 'text/plain' }) as BulkImportSelectionFile;
  Object.defineProperty(file, 'webkitRelativePath', {
    value: webkitRelativePath,
    configurable: true,
  });
  return file;
};

describe('bulkImportService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('startImport', () => {
    it('forwards FormData with files, relativePaths, targetFolderId and conflictPolicy', async () => {
      mockedApi.postFormData.mockResolvedValueOnce(job);

      const file = makeSelectionFile('a.pdf', 'folder/a.pdf');
      await expect(
        bulkImportService.startImport([file], 'folder-1', 'OVERWRITE')
      ).resolves.toEqual(job);

      expect(mockedApi.postFormData).toHaveBeenCalledTimes(1);
      const [url, formData] = mockedApi.postFormData.mock.calls[0];
      expect(url).toBe('/bulk-import');
      expect(formData).toBeInstanceOf(FormData);
      const fd = formData as FormData;
      expect(fd.getAll('files')).toHaveLength(1);
      expect(fd.getAll('files')[0]).toBe(file);
      expect(fd.getAll('relativePaths')).toEqual(['folder/a.pdf']);
      expect(fd.get('targetFolderId')).toBe('folder-1');
      expect(fd.get('conflictPolicy')).toBe('OVERWRITE');
    });

    it('omits targetFolderId from FormData when none is provided and defaults conflictPolicy to SKIP', async () => {
      mockedApi.postFormData.mockResolvedValueOnce(nullableJob);

      const file = makeSelectionFile('b.txt', 'b.txt');
      await expect(bulkImportService.startImport([file])).resolves.toEqual(nullableJob);

      const [, formData] = mockedApi.postFormData.mock.calls[0];
      const fd = formData as FormData;
      expect(fd.get('targetFolderId')).toBeNull();
      expect(fd.get('conflictPolicy')).toBe('SKIP');
    });

    it('rejects HTML fallback', async () => {
      mockedApi.postFormData.mockResolvedValueOnce('<!doctype html><html></html>');

      const file = makeSelectionFile('a.pdf', 'a.pdf');
      await expect(bulkImportService.startImport([file])).rejects.toThrow(
        BULK_IMPORT_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });

    it('rejects a malformed job missing numeric counters', async () => {
      mockedApi.postFormData.mockResolvedValueOnce({
        ...job,
        importedFiles: '3',
      });

      const file = makeSelectionFile('a.pdf', 'a.pdf');
      await expect(bulkImportService.startImport([file])).rejects.toThrow(
        BULK_IMPORT_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });

    it('rejects a job with a status outside the closed union', async () => {
      mockedApi.postFormData.mockResolvedValueOnce({ ...job, status: 'QUEUED' });

      const file = makeSelectionFile('a.pdf', 'a.pdf');
      await expect(bulkImportService.startImport([file])).rejects.toThrow(
        BULK_IMPORT_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });

    it('rejects a job with a conflictPolicy outside the closed union', async () => {
      mockedApi.postFormData.mockResolvedValueOnce({ ...job, conflictPolicy: 'MERGE' });

      const file = makeSelectionFile('a.pdf', 'a.pdf');
      await expect(bulkImportService.startImport([file])).rejects.toThrow(
        BULK_IMPORT_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });
  });

  describe('getJob', () => {
    it('returns a guarded job and keeps the job path', async () => {
      mockedApi.get.mockResolvedValueOnce(job);

      await expect(bulkImportService.getJob('job-1')).resolves.toEqual(job);
      expect(mockedApi.get).toHaveBeenCalledWith('/bulk-import/job-1');
    });

    it('accepts nullable optional fields (missing/null timestamps and folder id)', async () => {
      mockedApi.get.mockResolvedValueOnce(nullableJob);

      await expect(bulkImportService.getJob('job-2')).resolves.toEqual(nullableJob);
    });

    it('rejects a job whose createdAt is not a string', async () => {
      mockedApi.get.mockResolvedValueOnce({ ...job, createdAt: 1747299600000 });

      await expect(bulkImportService.getJob('job-1')).rejects.toThrow(
        BULK_IMPORT_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });

    it('rejects HTML fallback', async () => {
      mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

      await expect(bulkImportService.getJob('job-1')).rejects.toThrow(
        BULK_IMPORT_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });
  });

  describe('listJobs', () => {
    it('forwards paging params and returns a guarded page', async () => {
      const response = page([job, nullableJob]);
      mockedApi.get.mockResolvedValueOnce(response);

      await expect(bulkImportService.listJobs(2, 5)).resolves.toEqual(response);
      expect(mockedApi.get).toHaveBeenCalledWith('/bulk-import', {
        params: { page: 2, size: 5 },
      });
    });

    it('uses default paging when none is provided', async () => {
      mockedApi.get.mockResolvedValueOnce(page());

      await expect(bulkImportService.listJobs()).resolves.toEqual(page());
      expect(mockedApi.get).toHaveBeenCalledWith('/bulk-import', {
        params: { page: 0, size: 20 },
      });
    });

    it('rejects a page envelope whose totalElements is a string', async () => {
      mockedApi.get.mockResolvedValueOnce({ ...page(), totalElements: '1' });

      await expect(bulkImportService.listJobs()).rejects.toThrow(
        BULK_IMPORT_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });

    it('rejects a page entry with a malformed nullable field type', async () => {
      mockedApi.get.mockResolvedValueOnce(page([
        { ...job, currentItemPath: 42 as unknown as string },
      ]));

      await expect(bulkImportService.listJobs()).rejects.toThrow(
        BULK_IMPORT_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });

    it('rejects a page entry with an invalid status enum', async () => {
      mockedApi.get.mockResolvedValueOnce(page([
        { ...job, status: 'queued' as ImportJobDto['status'] },
      ]));

      await expect(bulkImportService.listJobs()).rejects.toThrow(
        BULK_IMPORT_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });

    it('rejects HTML fallback', async () => {
      mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

      await expect(bulkImportService.listJobs()).rejects.toThrow(
        BULK_IMPORT_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });
  });

  describe('cancelJob', () => {
    it('returns a guarded job and keeps the DELETE path', async () => {
      mockedApi.delete.mockResolvedValueOnce({ ...job, status: 'CANCELED' });

      await expect(bulkImportService.cancelJob('job-1')).resolves.toEqual({
        ...job,
        status: 'CANCELED',
      });
      expect(mockedApi.delete).toHaveBeenCalledWith('/bulk-import/job-1');
    });

    it('rejects HTML fallback on cancel', async () => {
      mockedApi.delete.mockResolvedValueOnce('<!doctype html><html></html>');

      await expect(bulkImportService.cancelJob('job-1')).rejects.toThrow(
        BULK_IMPORT_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });

    it('rejects a job whose userId is not a string', async () => {
      mockedApi.delete.mockResolvedValueOnce({ ...job, userId: 123 });

      await expect(bulkImportService.cancelJob('job-1')).rejects.toThrow(
        BULK_IMPORT_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });
  });
});
