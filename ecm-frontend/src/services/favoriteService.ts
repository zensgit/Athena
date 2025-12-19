import api from './api';

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

class FavoriteService {
  async add(nodeId: string): Promise<void> {
    await api.post<void>(`/favorites/${nodeId}`);
  }

  async remove(nodeId: string): Promise<void> {
    await api.delete<void>(`/favorites/${nodeId}`);
  }

  async check(nodeId: string): Promise<boolean> {
    return api.get<boolean>(`/favorites/${nodeId}/check`);
  }

  async list(page = 0, size = 50): Promise<PageResponse<FavoriteItem>> {
    return api.get<PageResponse<FavoriteItem>>('/favorites', { params: { page, size } });
  }

  async checkBatch(nodeIds: string[]): Promise<Set<string>> {
    if (!nodeIds.length) {
      return new Set();
    }

    const response = await api.post<FavoriteBatchCheckResponse>('/favorites/batch/check', { nodeIds });
    return new Set(response.favoritedNodeIds || []);
  }
}

const favoriteService = new FavoriteService();
export default favoriteService;
