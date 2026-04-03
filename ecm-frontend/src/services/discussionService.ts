import api from './api';

export type TopicStatus = 'OPEN' | 'CLOSED' | 'PINNED';

export interface TopicDto {
  id: string;
  siteId: string;
  title: string;
  content?: string | null;
  status: TopicStatus;
  tags: string[];
  createdBy: string;
  createdDate: string;
  replyCount: number;
}

export interface ReplyDto {
  id: string;
  topicId: string;
  parentReplyId?: string | null;
  content: string;
  createdBy: string;
  createdDate: string;
}

export interface TopicPage {
  content: TopicDto[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface ReplyPage {
  content: ReplyDto[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

class DiscussionService {
  async listTopics(siteId: string, page = 0, size = 20, status?: TopicStatus): Promise<TopicPage> {
    const params: Record<string, string | number> = { page, size };
    if (status) params.status = status;
    return api.get<TopicPage>(`/sites/${siteId}/discussions`, { params });
  }

  async getTopic(siteId: string, topicId: string): Promise<TopicDto> {
    return api.get<TopicDto>(`/sites/${siteId}/discussions/${topicId}`);
  }

  async createTopic(siteId: string, title: string, content?: string, tags?: string[]): Promise<TopicDto> {
    return api.post<TopicDto>(`/sites/${siteId}/discussions`, { title, content, tags });
  }

  async updateTopic(siteId: string, topicId: string, data: { title?: string; content?: string; status?: TopicStatus }): Promise<TopicDto> {
    return api.put<TopicDto>(`/sites/${siteId}/discussions/${topicId}`, data);
  }

  async deleteTopic(siteId: string, topicId: string): Promise<void> {
    return api.delete(`/sites/${siteId}/discussions/${topicId}`);
  }

  async listReplies(siteId: string, topicId: string, page = 0, size = 50): Promise<ReplyPage> {
    return api.get<ReplyPage>(`/sites/${siteId}/discussions/${topicId}/replies`, { params: { page, size } });
  }

  async createReply(siteId: string, topicId: string, content: string, parentReplyId?: string): Promise<ReplyDto> {
    return api.post<ReplyDto>(`/sites/${siteId}/discussions/${topicId}/replies`, { content, parentReplyId });
  }

  async updateReply(siteId: string, topicId: string, replyId: string, content: string): Promise<ReplyDto> {
    return api.put<ReplyDto>(`/sites/${siteId}/discussions/${topicId}/replies/${replyId}`, { content });
  }

  async deleteReply(siteId: string, topicId: string, replyId: string): Promise<void> {
    return api.delete(`/sites/${siteId}/discussions/${topicId}/replies/${replyId}`);
  }
}

const discussionService = new DiscussionService();
export default discussionService;
