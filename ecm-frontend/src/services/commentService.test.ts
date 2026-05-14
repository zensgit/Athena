import api from './api';
import commentService, {
  Comment,
  CommentStatistics,
  COMMENT_UNEXPECTED_RESPONSE_MESSAGE,
  PageResponse,
} from './commentService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    put: jest.fn(),
    delete: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const comment: Comment = {
  id: 'comment-1',
  content: 'Please review @bob',
  author: 'alice',
  nodeId: 'node-1',
  nodeName: 'Design Brief',
  nodeType: 'DOCUMENT',
  created: '2026-05-14T00:00:00Z',
  edited: null,
  editor: null,
  level: 0,
  reactions: [{ type: 'like', user: 'bob', date: '2026-05-14T00:01:00Z' }],
  mentionedUsers: ['bob'],
  replies: [],
};

const commentWithReply: Comment = {
  ...comment,
  replies: [
    {
      ...comment,
      id: 'comment-2',
      content: 'Reply',
      level: 1,
      replies: [],
    },
  ],
};

const page: PageResponse<Comment> = {
  content: [comment],
  totalElements: 1,
  totalPages: 1,
  size: 20,
  number: 0,
};

const statistics: CommentStatistics = {
  nodeId: 'node-1',
  totalComments: 2,
  uniqueCommenters: 2,
  topCommenters: {
    alice: 1,
    bob: 1,
  },
};

describe('commentService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns guarded add-comment readbacks', async () => {
    mockedApi.post.mockResolvedValueOnce(comment);

    await expect(commentService.addComment('node-1', 'Please review @bob', 'parent-1')).resolves.toEqual(comment);

    expect(mockedApi.post).toHaveBeenCalledWith('/nodes/node-1/comments', {
      content: 'Please review @bob',
      parentCommentId: 'parent-1',
    });
  });

  it('rejects malformed add-comment readbacks', async () => {
    mockedApi.post.mockResolvedValueOnce({ ...comment, reactions: [{ type: 'like', user: 'bob' }] });

    await expect(commentService.addComment('node-1', 'body')).rejects.toThrow(
      COMMENT_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('returns guarded node comment pages', async () => {
    mockedApi.get.mockResolvedValueOnce(page);

    await expect(commentService.getNodeComments('node-1', 1, 10)).resolves.toEqual(page);

    expect(mockedApi.get).toHaveBeenCalledWith('/nodes/node-1/comments', {
      params: { page: 1, size: 10 },
    });
  });

  it('rejects HTML fallback for comment pages', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(commentService.getNodeComments('node-1')).rejects.toThrow(
      COMMENT_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('rejects malformed comment page items', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...page,
      content: [{ ...comment, mentionedUsers: [42] }],
    });

    await expect(commentService.getNodeComments('node-1')).rejects.toThrow(
      COMMENT_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('returns guarded recursive comment trees', async () => {
    mockedApi.get.mockResolvedValueOnce([commentWithReply]);

    await expect(commentService.getCommentTree('node-1')).resolves.toEqual([commentWithReply]);

    expect(mockedApi.get).toHaveBeenCalledWith('/nodes/node-1/comments/tree');
  });

  it('rejects malformed recursive comment trees', async () => {
    mockedApi.get.mockResolvedValueOnce([{ ...commentWithReply, replies: [{ ...comment, level: '1' }] }]);

    await expect(commentService.getCommentTree('node-1')).rejects.toThrow(
      COMMENT_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('returns guarded edit-comment readbacks', async () => {
    mockedApi.put.mockResolvedValueOnce({ ...comment, content: 'Updated' });

    await expect(commentService.editComment('comment-1', 'Updated')).resolves.toEqual({
      ...comment,
      content: 'Updated',
    });

    expect(mockedApi.put).toHaveBeenCalledWith('/comments/comment-1', { content: 'Updated' });
  });

  it('returns guarded user comment pages', async () => {
    mockedApi.get.mockResolvedValueOnce(page).mockResolvedValueOnce(page);

    await expect(commentService.getUserComments('alice', 0, 6)).resolves.toEqual(page);
    await expect(commentService.getMentionedComments('alice', 0, 6)).resolves.toEqual(page);

    expect(mockedApi.get).toHaveBeenNthCalledWith(1, '/users/alice/comments', {
      params: { page: 0, size: 6 },
    });
    expect(mockedApi.get).toHaveBeenNthCalledWith(2, '/users/alice/mentioned-comments', {
      params: { page: 0, size: 6 },
    });
  });

  it('returns guarded search results', async () => {
    mockedApi.get.mockResolvedValueOnce([comment]);

    await expect(commentService.searchComments('node-1', 'review')).resolves.toEqual([comment]);

    expect(mockedApi.get).toHaveBeenCalledWith('/nodes/node-1/comments/search', {
      params: { q: 'review' },
    });
  });

  it('rejects malformed search results', async () => {
    mockedApi.get.mockResolvedValueOnce([{ ...comment, created: null }]);

    await expect(commentService.searchComments('node-1', 'review')).rejects.toThrow(
      COMMENT_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('returns guarded comment statistics', async () => {
    mockedApi.get.mockResolvedValueOnce(statistics);

    await expect(commentService.getCommentStatistics('node-1')).resolves.toEqual(statistics);

    expect(mockedApi.get).toHaveBeenCalledWith('/nodes/node-1/comments/statistics');
  });

  it('rejects malformed comment statistics', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...statistics,
      topCommenters: { alice: '1' },
    });

    await expect(commentService.getCommentStatistics('node-1')).rejects.toThrow(
      COMMENT_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('keeps no-content endpoint wiring unchanged', async () => {
    mockedApi.delete.mockResolvedValue(undefined);
    mockedApi.post.mockResolvedValueOnce(undefined);

    await commentService.deleteComment('comment-1');
    await commentService.addReaction('comment-1', 'like');
    await commentService.removeReaction('comment-1');

    expect(mockedApi.delete).toHaveBeenNthCalledWith(1, '/comments/comment-1');
    expect(mockedApi.post).toHaveBeenCalledWith('/comments/comment-1/reactions', { reactionType: 'like' });
    expect(mockedApi.delete).toHaveBeenNthCalledWith(2, '/comments/comment-1/reactions');
  });
});
