import api from './api';

export const COMMENT_UNEXPECTED_RESPONSE_MESSAGE =
  'Comment endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isStringArray = (value: unknown): value is string[] => (
  Array.isArray(value) && value.every((item) => typeof item === 'string')
);

const isStringOrNullish = (value: unknown): value is string | null | undefined => (
  value === null || value === undefined || typeof value === 'string'
);

const isNumber = (value: unknown): value is number => (
  typeof value === 'number' && Number.isFinite(value)
);

export interface Comment {
  id: string;
  content: string;
  author: string;
  nodeId?: string | null;
  nodeName?: string | null;
  nodeType?: 'FOLDER' | 'DOCUMENT' | string | null;
  created: string;
  edited?: string;
  editor?: string | null;
  level: number;
  reactions: Array<{ type: string; user: string; date: string }>;
  mentionedUsers: string[];
  replies?: Comment[];
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface CommentStatistics {
  nodeId: string;
  totalComments: number;
  uniqueCommenters: number;
  topCommenters: Record<string, number>;
}

const assertUnexpectedResponse = (): never => {
  throw new Error(COMMENT_UNEXPECTED_RESPONSE_MESSAGE);
};

const isCommentReaction = (value: unknown): value is { type: string; user: string; date: string } => (
  isObject(value)
  && typeof value.type === 'string'
  && typeof value.user === 'string'
  && typeof value.date === 'string'
);

const isComment = (value: unknown): value is Comment => {
  if (!isObject(value)) {
    return false;
  }
  const replies = value.replies;
  return typeof value.id === 'string'
    && typeof value.content === 'string'
    && typeof value.author === 'string'
    && isStringOrNullish(value.nodeId)
    && isStringOrNullish(value.nodeName)
    && isStringOrNullish(value.nodeType)
    && typeof value.created === 'string'
    && isStringOrNullish(value.edited)
    && isStringOrNullish(value.editor)
    && isNumber(value.level)
    && Array.isArray(value.reactions)
    && value.reactions.every(isCommentReaction)
    && isStringArray(value.mentionedUsers)
    && (replies === undefined || (Array.isArray(replies) && replies.every(isComment)));
};

const assertComment = (value: unknown): Comment => (
  isComment(value) ? value : assertUnexpectedResponse()
);

const isCommentArray = (value: unknown): value is Comment[] => (
  Array.isArray(value) && value.every(isComment)
);

const assertCommentArray = (value: unknown): Comment[] => (
  isCommentArray(value) ? value : assertUnexpectedResponse()
);

const isCommentPage = (value: unknown): value is PageResponse<Comment> => {
  if (!isObject(value) || !Array.isArray(value.content)) {
    return false;
  }
  return value.content.every(isComment)
    && isNumber(value.totalElements)
    && isNumber(value.totalPages)
    && isNumber(value.size)
    && isNumber(value.number);
};

const assertCommentPage = (value: unknown): PageResponse<Comment> => (
  isCommentPage(value) ? value : assertUnexpectedResponse()
);

const isNumberRecord = (value: unknown): value is Record<string, number> => (
  isObject(value) && Object.values(value).every(isNumber)
);

const isCommentStatistics = (value: unknown): value is CommentStatistics => (
  isObject(value)
  && typeof value.nodeId === 'string'
  && isNumber(value.totalComments)
  && isNumber(value.uniqueCommenters)
  && isNumberRecord(value.topCommenters)
);

const assertCommentStatistics = (value: unknown): CommentStatistics => (
  isCommentStatistics(value) ? value : assertUnexpectedResponse()
);

class CommentService {
  async addComment(nodeId: string, content: string, parentCommentId?: string): Promise<Comment> {
    const result = await api.post<unknown>(`/nodes/${nodeId}/comments`, {
      content,
      parentCommentId,
    });
    return assertComment(result);
  }

  async getNodeComments(nodeId: string, page = 0, size = 20): Promise<PageResponse<Comment>> {
    const result = await api.get<unknown>(`/nodes/${nodeId}/comments`, {
      params: { page, size },
    });
    return assertCommentPage(result);
  }

  async getCommentTree(nodeId: string): Promise<Comment[]> {
    const result = await api.get<unknown>(`/nodes/${nodeId}/comments/tree`);
    return assertCommentArray(result);
  }

  async editComment(commentId: string, content: string): Promise<Comment> {
    const result = await api.put<unknown>(`/comments/${commentId}`, { content });
    return assertComment(result);
  }

  async deleteComment(commentId: string): Promise<void> {
    return api.delete(`/comments/${commentId}`);
  }

  async addReaction(commentId: string, reactionType: string): Promise<void> {
    return api.post(`/comments/${commentId}/reactions`, { reactionType });
  }

  async removeReaction(commentId: string): Promise<void> {
    return api.delete(`/comments/${commentId}/reactions`);
  }

  async getUserComments(username: string, page = 0, size = 20): Promise<PageResponse<Comment>> {
    const result = await api.get<unknown>(`/users/${username}/comments`, {
      params: { page, size },
    });
    return assertCommentPage(result);
  }

  async getMentionedComments(username: string, page = 0, size = 20): Promise<PageResponse<Comment>> {
    const result = await api.get<unknown>(`/users/${username}/mentioned-comments`, {
      params: { page, size },
    });
    return assertCommentPage(result);
  }

  async searchComments(nodeId: string, query: string): Promise<Comment[]> {
    const result = await api.get<unknown>(`/nodes/${nodeId}/comments/search`, {
      params: { q: query },
    });
    return assertCommentArray(result);
  }

  async getCommentStatistics(nodeId: string): Promise<CommentStatistics> {
    const result = await api.get<unknown>(`/nodes/${nodeId}/comments/statistics`);
    return assertCommentStatistics(result);
  }
}

const commentService = new CommentService();
export default commentService;
