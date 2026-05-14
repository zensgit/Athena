import api from './api';

export const TAG_UNEXPECTED_RESPONSE_MESSAGE =
  'Tag endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isNumber = (value: unknown): value is number => (
  typeof value === 'number' && Number.isFinite(value)
);

const isStringOrNullish = (value: unknown): value is string | null | undefined => (
  value === null || value === undefined || typeof value === 'string'
);

export interface Tag {
  id: string;
  name: string;
  description?: string | null;
  color: string;
  usageCount: number;
  created: string;
  creator: string;
}

interface CreateTagRequest {
  name: string;
  description?: string;
  color?: string;
}

interface UpdateTagRequest {
  name: string;
  description?: string;
  color: string;
}

export interface TagCloudItem {
  name: string;
  count: number;
  color: string;
}

const assertUnexpectedResponse = (): never => {
  throw new Error(TAG_UNEXPECTED_RESPONSE_MESSAGE);
};

const isTag = (value: unknown): value is Tag => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.name === 'string'
    && isStringOrNullish(value.description)
    && typeof value.color === 'string'
    && isNumber(value.usageCount)
    && typeof value.created === 'string'
    && typeof value.creator === 'string';
};

const assertTag = (value: unknown): Tag => (
  isTag(value) ? value : assertUnexpectedResponse()
);

const assertTagList = (value: unknown): Tag[] => {
  if (!Array.isArray(value) || !value.every(isTag)) {
    throw new Error(TAG_UNEXPECTED_RESPONSE_MESSAGE);
  }
  return value;
};

const isTagCloudItem = (value: unknown): value is TagCloudItem => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.name === 'string'
    && isNumber(value.count)
    && typeof value.color === 'string';
};

const assertTagCloud = (value: unknown): TagCloudItem[] => {
  if (!Array.isArray(value) || !value.every(isTagCloudItem)) {
    throw new Error(TAG_UNEXPECTED_RESPONSE_MESSAGE);
  }
  return value;
};

class TagService {
  async createTag(data: CreateTagRequest): Promise<Tag> {
    const result = await api.post<unknown>('/tags', data);
    return assertTag(result);
  }

  async getAllTags(): Promise<Tag[]> {
    const result = await api.get<unknown>('/tags');
    return assertTagList(result);
  }

  async searchTags(query: string): Promise<Tag[]> {
    const result = await api.get<unknown>('/tags/search', { params: { q: query } });
    return assertTagList(result);
  }

  async getPopularTags(limit = 10): Promise<Tag[]> {
    const result = await api.get<unknown>('/tags/popular', { params: { limit } });
    return assertTagList(result);
  }

  async updateTag(tagId: string, data: UpdateTagRequest): Promise<Tag> {
    const result = await api.put<unknown>(`/tags/${tagId}`, data);
    return assertTag(result);
  }

  async deleteTag(tagId: string): Promise<void> {
    return api.delete(`/tags/${tagId}`);
  }

  async mergeTags(sourceTagId: string, targetTagId: string): Promise<void> {
    return api.post(`/tags/${sourceTagId}/merge`, { targetTagId });
  }

  async getTagCloud(): Promise<TagCloudItem[]> {
    const result = await api.get<unknown>('/tags/cloud');
    return assertTagCloud(result);
  }

  async addTagToNode(nodeId: string, tagName: string): Promise<void> {
    return api.post(`/nodes/${nodeId}/tags`, { tagName });
  }

  async addTagsToNode(nodeId: string, tagNames: string[]): Promise<void> {
    return api.post(`/nodes/${nodeId}/tags/batch`, { tagNames });
  }

  async removeTagFromNode(nodeId: string, tagName: string): Promise<void> {
    return api.delete(`/nodes/${nodeId}/tags/${tagName}`);
  }

  async getNodeTags(nodeId: string): Promise<Tag[]> {
    const result = await api.get<unknown>(`/nodes/${nodeId}/tags`);
    return assertTagList(result);
  }

  async findNodesByTag(tagName: string, page = 0, size = 20): Promise<any> {
    return api.get('/nodes/by-tag', {
      params: { tagName, page, size },
    });
  }

  async findNodesByTags(tagNames: string[]): Promise<any[]> {
    return api.post('/nodes/by-tags', { tagNames });
  }
}

const tagService = new TagService();
export default tagService;
