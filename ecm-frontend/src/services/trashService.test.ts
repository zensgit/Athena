import api from './api';
import trashService, {
  EmptyTrashResponse,
  TRASH_UNEXPECTED_RESPONSE_MESSAGE,
  TrashItem,
} from './trashService';

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

const folderItem: TrashItem = {
  id: 'node-1',
  name: 'Reports',
  path: '/Sites/engineering/Reports',
  nodeType: 'FOLDER',
  deletedBy: 'alice',
  deletedAt: '2026-05-14T00:00:00',
  createdBy: 'alice',
  createdDate: '2026-05-13T23:00:00',
  isFolder: true,
};

const documentItem: TrashItem = {
  id: 'node-2',
  name: 'Plan.pdf',
  path: '/Sites/engineering/Plan.pdf',
  nodeType: 'DOCUMENT',
  size: 4096,
  deletedBy: 'bob',
  deletedAt: '2026-05-14T00:01:00',
  createdBy: 'bob',
  createdDate: '2026-05-13T22:00:00',
  isFolder: false,
};

const emptyResponse: EmptyTrashResponse = { deletedCount: 3 };

describe('trashService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns guarded trash items for getTrashItems', async () => {
    mockedApi.get.mockResolvedValueOnce([folderItem, documentItem]);

    await expect(trashService.getTrashItems()).resolves.toEqual([folderItem, documentItem]);

    expect(mockedApi.get).toHaveBeenCalledWith('/trash');
  });

  it('rejects HTML fallback for getTrashItems', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(trashService.getTrashItems()).rejects.toThrow(
      TRASH_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects malformed trash items', async () => {
    mockedApi.get.mockResolvedValueOnce([{ ...folderItem, nodeType: 'LINK' }]);

    await expect(trashService.getTrashItems()).rejects.toThrow(
      TRASH_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('returns guarded deletedCount for emptyTrash', async () => {
    mockedApi.delete.mockResolvedValueOnce(emptyResponse);

    await expect(trashService.emptyTrash()).resolves.toEqual(emptyResponse);

    expect(mockedApi.delete).toHaveBeenCalledWith('/trash/empty');
  });

  it('rejects malformed deletedCount in emptyTrash', async () => {
    mockedApi.delete.mockResolvedValueOnce({ deletedCount: 'three' });

    await expect(trashService.emptyTrash()).rejects.toThrow(
      TRASH_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('keeps restore wiring as a no-content endpoint', async () => {
    mockedApi.post.mockResolvedValueOnce(undefined);

    await trashService.restore('node-1');

    expect(mockedApi.post).toHaveBeenCalledWith('/trash/node-1/restore');
  });

  it('keeps permanentDelete wiring as a no-content endpoint', async () => {
    mockedApi.delete.mockResolvedValueOnce(undefined);

    await trashService.permanentDelete('node-1');

    expect(mockedApi.delete).toHaveBeenCalledWith('/trash/node-1');
  });
});
