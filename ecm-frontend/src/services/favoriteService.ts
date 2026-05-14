import api from './api';

export const FAVORITE_UNEXPECTED_RESPONSE_MESSAGE =
  'Favorite endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isNumber = (value: unknown): value is number => (
  typeof value === 'number' && Number.isFinite(value)
);

const isNodeType = (value: unknown): value is FavoriteItem['nodeType'] => (
  value === 'FOLDER' || value === 'DOCUMENT'
);

export interface FavoriteItem {
  id: string;
  nodeId: string;
  nodeName: string;
  nodeType: 'FOLDER' | 'DOCUMENT';
  createdAt: string;
}

interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface FavoriteBatchCheckResponse {
  favoritedNodeIds: string[];
}

const assertUnexpectedResponse = (): never => {
  throw new Error(FAVORITE_UNEXPECTED_RESPONSE_MESSAGE);
};

const isFavoriteItem = (value: unknown): value is FavoriteItem => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.nodeId === 'string'
    && typeof value.nodeName === 'string'
    && isNodeType(value.nodeType)
    && typeof value.createdAt === 'string';
};

const isFavoritePage = (value: unknown): value is PageResponse<FavoriteItem> => {
  if (!isObject(value) || !Array.isArray(value.content)) {
    return false;
  }
  return value.content.every(isFavoriteItem)
    && isNumber(value.totalElements)
    && isNumber(value.totalPages)
    && isNumber(value.size)
    && isNumber(value.number);
};

const assertFavoritePage = (value: unknown): PageResponse<FavoriteItem> => (
  isFavoritePage(value) ? value : assertUnexpectedResponse()
);

const isFavoriteBatchCheckResponse = (value: unknown): value is FavoriteBatchCheckResponse => {
  if (!isObject(value) || !Array.isArray(value.favoritedNodeIds)) {
    return false;
  }
  return value.favoritedNodeIds.every((nodeId) => typeof nodeId === 'string');
};

const assertFavoriteBatchCheckResponse = (value: unknown): FavoriteBatchCheckResponse => (
  isFavoriteBatchCheckResponse(value) ? value : assertUnexpectedResponse()
);

class FavoriteService {
  async add(nodeId: string): Promise<void> {
    await api.post<void>(`/favorites/${nodeId}`);
  }

  async remove(nodeId: string): Promise<void> {
    await api.delete<void>(`/favorites/${nodeId}`);
  }

  async check(nodeId: string): Promise<boolean> {
    const result = await api.get<unknown>(`/favorites/${nodeId}/check`);
    if (typeof result !== 'boolean') {
      throw new Error(FAVORITE_UNEXPECTED_RESPONSE_MESSAGE);
    }
    return result;
  }

  async list(page = 0, size = 50): Promise<PageResponse<FavoriteItem>> {
    const result = await api.get<unknown>('/favorites', { params: { page, size } });
    return assertFavoritePage(result);
  }

  async checkBatch(nodeIds: string[]): Promise<Set<string>> {
    if (!nodeIds.length) {
      return new Set();
    }

    const response = await api.post<unknown>('/favorites/batch/check', { nodeIds });
    return new Set(assertFavoriteBatchCheckResponse(response).favoritedNodeIds);
  }
}

const favoriteService = new FavoriteService();
export default favoriteService;
