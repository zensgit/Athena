import api from './api';

export interface TrashItem {
  id: string;
  name: string;
  path: string;
  nodeType: 'FOLDER' | 'DOCUMENT';
  size?: number;
  deletedBy?: string;
  deletedAt?: string;
  createdBy?: string;
  createdDate?: string;
  isFolder: boolean;
}

class TrashService {
  async getTrashItems(): Promise<TrashItem[]> {
    return api.get<TrashItem[]>('/trash');
  }

  async restore(nodeId: string): Promise<void> {
    return api.post(`/trash/${nodeId}/restore`);
  }

  async permanentDelete(nodeId: string): Promise<void> {
    return api.delete(`/trash/${nodeId}`);
  }

  async emptyTrash(): Promise<{ deletedCount: number }> {
    return api.delete<{ deletedCount: number }>('/trash/empty');
  }
}

const trashService = new TrashService();
export default trashService;
