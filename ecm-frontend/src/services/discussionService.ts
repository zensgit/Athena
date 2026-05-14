import api from './api';

export const DISCUSSION_UNEXPECTED_RESPONSE_MESSAGE =
  'Discussion endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isNumber = (value: unknown): value is number => (
  typeof value === 'number' && Number.isFinite(value)
);

const isStringOrNullish = (value: unknown): value is string | null | undefined => (
  value === null || value === undefined || typeof value === 'string'
);

export type TopicStatus = 'OPEN' | 'CLOSED' | 'PINNED';

const isTopicStatus = (value: unknown): value is TopicStatus => (
  value === 'OPEN' || value === 'CLOSED' || value === 'PINNED'
);

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

const assertUnexpectedResponse = (): never => {
  throw new Error(DISCUSSION_UNEXPECTED_RESPONSE_MESSAGE);
};

const isTopicDto = (value: unknown): value is TopicDto => {
  if (!isObject(value)) {
    return false;
  }
  if (!Array.isArray(value.tags) || !value.tags.every((tag) => typeof tag === 'string')) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.siteId === 'string'
    && typeof value.title === 'string'
    && isStringOrNullish(value.content)
    && isTopicStatus(value.status)
    && typeof value.createdBy === 'string'
    && typeof value.createdDate === 'string'
    && isNumber(value.replyCount);
};

const assertTopicDto = (value: unknown): TopicDto => (
  isTopicDto(value) ? value : assertUnexpectedResponse()
);

const isReplyDto = (value: unknown): value is ReplyDto => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.topicId === 'string'
    && isStringOrNullish(value.parentReplyId)
    && typeof value.content === 'string'
    && typeof value.createdBy === 'string'
    && typeof value.createdDate === 'string';
};

const assertReplyDto = (value: unknown): ReplyDto => (
  isReplyDto(value) ? value : assertUnexpectedResponse()
);

const isTopicPage = (value: unknown): value is TopicPage => {
  if (!isObject(value) || !Array.isArray(value.content)) {
    return false;
  }
  return value.content.every(isTopicDto)
    && isNumber(value.totalElements)
    && isNumber(value.totalPages)
    && isNumber(value.number)
    && isNumber(value.size);
};

const assertTopicPage = (value: unknown): TopicPage => (
  isTopicPage(value) ? value : assertUnexpectedResponse()
);

const isReplyPage = (value: unknown): value is ReplyPage => {
  if (!isObject(value) || !Array.isArray(value.content)) {
    return false;
  }
  return value.content.every(isReplyDto)
    && isNumber(value.totalElements)
    && isNumber(value.totalPages)
    && isNumber(value.number)
    && isNumber(value.size);
};

const assertReplyPage = (value: unknown): ReplyPage => (
  isReplyPage(value) ? value : assertUnexpectedResponse()
);

class DiscussionService {
  async listTopics(siteId: string, page = 0, size = 20, status?: TopicStatus): Promise<TopicPage> {
    const params: Record<string, string | number> = { page, size };
    if (status) params.status = status;
    const result = await api.get<unknown>(`/sites/${siteId}/discussions`, { params });
    return assertTopicPage(result);
  }

  async getTopic(siteId: string, topicId: string): Promise<TopicDto> {
    const result = await api.get<unknown>(`/sites/${siteId}/discussions/${topicId}`);
    return assertTopicDto(result);
  }

  async createTopic(siteId: string, title: string, content?: string, tags?: string[]): Promise<TopicDto> {
    const result = await api.post<unknown>(`/sites/${siteId}/discussions`, { title, content, tags });
    return assertTopicDto(result);
  }

  async updateTopic(siteId: string, topicId: string, data: { title?: string; content?: string; status?: TopicStatus }): Promise<TopicDto> {
    const result = await api.put<unknown>(`/sites/${siteId}/discussions/${topicId}`, data);
    return assertTopicDto(result);
  }

  async deleteTopic(siteId: string, topicId: string): Promise<void> {
    return api.delete(`/sites/${siteId}/discussions/${topicId}`);
  }

  async listReplies(siteId: string, topicId: string, page = 0, size = 50): Promise<ReplyPage> {
    const result = await api.get<unknown>(`/sites/${siteId}/discussions/${topicId}/replies`, { params: { page, size } });
    return assertReplyPage(result);
  }

  async createReply(siteId: string, topicId: string, content: string, parentReplyId?: string): Promise<ReplyDto> {
    const result = await api.post<unknown>(`/sites/${siteId}/discussions/${topicId}/replies`, { content, parentReplyId });
    return assertReplyDto(result);
  }

  async updateReply(siteId: string, topicId: string, replyId: string, content: string): Promise<ReplyDto> {
    const result = await api.put<unknown>(`/sites/${siteId}/discussions/${topicId}/replies/${replyId}`, { content });
    return assertReplyDto(result);
  }

  async deleteReply(siteId: string, topicId: string, replyId: string): Promise<void> {
    return api.delete(`/sites/${siteId}/discussions/${topicId}/replies/${replyId}`);
  }
}

const discussionService = new DiscussionService();
export default discussionService;
