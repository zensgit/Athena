import api from './api';

export const TRASH_UNEXPECTED_RESPONSE_MESSAGE =
  'Trash endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isNumber = (value: unknown): value is number => (
  typeof value === 'number' && Number.isFinite(value)
);

const isStringOrNullish = (value: unknown): value is string | null | undefined => (
  value === null || value === undefined || typeof value === 'string'
);

const isNumberOrNullish = (value: unknown): value is number | null | undefined => (
  value === null || value === undefined || (typeof value === 'number' && Number.isFinite(value))
);

const isNodeType = (value: unknown): value is TrashItem['nodeType'] => (
  value === 'FOLDER' || value === 'DOCUMENT'
);

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

export interface EmptyTrashResponse {
  deletedCount: number;
}

const assertUnexpectedResponse = (): never => {
  throw new Error(TRASH_UNEXPECTED_RESPONSE_MESSAGE);
};

const isTrashItem = (value: unknown): value is TrashItem => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.name === 'string'
    && typeof value.path === 'string'
    && isNodeType(value.nodeType)
    && isNumberOrNullish(value.size)
    && isStringOrNullish(value.deletedBy)
    && isStringOrNullish(value.deletedAt)
    && isStringOrNullish(value.createdBy)
    && isStringOrNullish(value.createdDate)
    && typeof value.isFolder === 'boolean';
};

const assertTrashItems = (value: unknown): TrashItem[] => {
  if (!Array.isArray(value) || !value.every(isTrashItem)) {
    throw new Error(TRASH_UNEXPECTED_RESPONSE_MESSAGE);
  }
  return value;
};

const isEmptyTrashResponse = (value: unknown): value is EmptyTrashResponse => (
  isObject(value) && isNumber(value.deletedCount)
);

const assertEmptyTrashResponse = (value: unknown): EmptyTrashResponse => (
  isEmptyTrashResponse(value) ? value : assertUnexpectedResponse()
);

class TrashService {
  async getTrashItems(): Promise<TrashItem[]> {
    const result = await api.get<unknown>('/trash');
    return assertTrashItems(result);
  }

  async restore(nodeId: string): Promise<void> {
    return api.post(`/trash/${nodeId}/restore`);
  }

  async permanentDelete(nodeId: string): Promise<void> {
    return api.delete(`/trash/${nodeId}`);
  }

  async emptyTrash(): Promise<EmptyTrashResponse> {
    const result = await api.delete<unknown>('/trash/empty');
    return assertEmptyTrashResponse(result);
  }
}

const trashService = new TrashService();
export default trashService;
