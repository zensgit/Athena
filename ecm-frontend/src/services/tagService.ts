import api from './api';

interface Tag {
  id: string;
  name: string;
  description?: string;
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

interface TagCloudItem {
  name: string;
  count: number;
  color: string;
}

class TagService {
  async createTag(data: CreateTagRequest): Promise<Tag> {
    return api.post<Tag>('/tags', data);
  }

  async getAllTags(): Promise<Tag[]> {
    return api.get<Tag[]>('/tags');
  }

  async searchTags(query: string): Promise<Tag[]> {
    return api.get<Tag[]>('/tags/search', { params: { q: query } });
  }

  async getPopularTags(limit = 10): Promise<Tag[]> {
    return api.get<Tag[]>('/tags/popular', { params: { limit } });
  }

  async updateTag(tagId: string, data: UpdateTagRequest): Promise<Tag> {
    return api.put<Tag>(`/tags/${tagId}`, data);
  }

  async deleteTag(tagId: string): Promise<void> {
    return api.delete(`/tags/${tagId}`);
  }

  async mergeTags(sourceTagId: string, targetTagId: string): Promise<void> {
    return api.post(`/tags/${sourceTagId}/merge`, { targetTagId });
  }

  async getTagCloud(): Promise<TagCloudItem[]> {
    return api.get<TagCloudItem[]>('/tags/cloud');
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
    return api.get<Tag[]>(`/nodes/${nodeId}/tags`);
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
