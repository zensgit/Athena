import api from './api';
import favoriteService, {
  FavoriteBatchCheckResponse,
  FavoriteItem,
  FAVORITE_UNEXPECTED_RESPONSE_MESSAGE,
} from './favoriteService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    delete: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const favorite: FavoriteItem = {
  id: 'favorite-1',
  nodeId: 'node-1',
  nodeName: 'Contract.pdf',
  nodeType: 'DOCUMENT',
  createdAt: '2026-05-14T00:00:00',
};

const favoritePage = {
  content: [favorite],
  totalElements: 1,
  totalPages: 1,
  size: 50,
  number: 0,
};

const batchResponse: FavoriteBatchCheckResponse = {
  favoritedNodeIds: ['node-1', 'node-3'],
};

describe('favoriteService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns guarded favorite check responses', async () => {
    mockedApi.get.mockResolvedValueOnce(true);

    await expect(favoriteService.check('node-1')).resolves.toBe(true);

    expect(mockedApi.get).toHaveBeenCalledWith('/favorites/node-1/check');
  });

  it('rejects malformed favorite check responses', async () => {
    mockedApi.get.mockResolvedValueOnce('true');

    await expect(favoriteService.check('node-1')).rejects.toThrow(
      FAVORITE_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('returns guarded favorite pages', async () => {
    mockedApi.get.mockResolvedValueOnce(favoritePage);

    await expect(favoriteService.list(0, 50)).resolves.toEqual(favoritePage);

    expect(mockedApi.get).toHaveBeenCalledWith('/favorites', { params: { page: 0, size: 50 } });
  });

  it('rejects HTML fallback for favorite pages', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(favoriteService.list()).rejects.toThrow(FAVORITE_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('rejects malformed favorite page items', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...favoritePage,
      content: [{ ...favorite, nodeType: 'LINK' }],
    });

    await expect(favoriteService.list()).rejects.toThrow(FAVORITE_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('returns guarded batch check responses as a Set', async () => {
    mockedApi.post.mockResolvedValueOnce(batchResponse);

    await expect(favoriteService.checkBatch(['node-1', 'node-2', 'node-3'])).resolves.toEqual(
      new Set(['node-1', 'node-3'])
    );

    expect(mockedApi.post).toHaveBeenCalledWith('/favorites/batch/check', {
      nodeIds: ['node-1', 'node-2', 'node-3'],
    });
  });

  it('rejects malformed batch check responses', async () => {
    mockedApi.post.mockResolvedValueOnce({ favoritedNodeIds: [42] });

    await expect(favoriteService.checkBatch(['node-1'])).rejects.toThrow(
      FAVORITE_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('does not call the API for empty batch checks', async () => {
    await expect(favoriteService.checkBatch([])).resolves.toEqual(new Set());

    expect(mockedApi.post).not.toHaveBeenCalled();
  });

  it('keeps add and remove endpoint wiring unchanged', async () => {
    mockedApi.post.mockResolvedValueOnce(undefined);
    mockedApi.delete.mockResolvedValueOnce(undefined);

    await favoriteService.add('node-1');
    await favoriteService.remove('node-1');

    expect(mockedApi.post).toHaveBeenCalledWith('/favorites/node-1');
    expect(mockedApi.delete).toHaveBeenCalledWith('/favorites/node-1');
  });
});
