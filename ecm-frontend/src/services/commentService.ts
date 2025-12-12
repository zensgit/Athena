import api from './api';

interface Comment {
  id: string;
  content: string;
  author: string;
  created: string;
  edited?: string;
  editor?: string;
  level: number;
  reactions: Array<{ type: string; user: string; date: string }>;
  mentionedUsers: string[];
  replies?: Comment[];
}

interface CommentStatistics {
  nodeId: string;
  totalComments: number;
  uniqueCommenters: number;
  topCommenters: Record<string, number>;
}

class CommentService {
  async addComment(nodeId: string, content: string, parentCommentId?: string): Promise<Comment> {
    return api.post<Comment>(`/nodes/${nodeId}/comments`, {
      content,
      parentCommentId,
    });
  }

  async getNodeComments(nodeId: string, page = 0, size = 20): Promise<any> {
    return api.get(`/nodes/${nodeId}/comments`, {
      params: { page, size },
    });
  }

  async getCommentTree(nodeId: string): Promise<Comment[]> {
    return api.get<Comment[]>(`/nodes/${nodeId}/comments/tree`);
  }

  async editComment(commentId: string, content: string): Promise<Comment> {
    return api.put<Comment>(`/comments/${commentId}`, { content });
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

  async getUserComments(username: string, page = 0, size = 20): Promise<any> {
    return api.get(`/users/${username}/comments`, {
      params: { page, size },
    });
  }

  async getMentionedComments(username: string, page = 0, size = 20): Promise<any> {
    return api.get(`/users/${username}/mentioned-comments`, {
      params: { page, size },
    });
  }

  async searchComments(nodeId: string, query: string): Promise<Comment[]> {
    return api.get<Comment[]>(`/nodes/${nodeId}/comments/search`, {
      params: { q: query },
    });
  }

  async getCommentStatistics(nodeId: string): Promise<CommentStatistics> {
    return api.get<CommentStatistics>(`/nodes/${nodeId}/comments/statistics`);
  }
}

const commentService = new CommentService();
export default commentService;