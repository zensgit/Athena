import api from './api';
import discussionService, {
  DISCUSSION_UNEXPECTED_RESPONSE_MESSAGE,
  ReplyDto,
  ReplyPage,
  TopicDto,
  TopicPage,
} from './discussionService';

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

const topic: TopicDto = {
  id: 'topic-1',
  siteId: 'engineering',
  title: 'Welcome',
  content: 'Greetings',
  status: 'OPEN',
  tags: ['intro'],
  createdBy: 'alice',
  createdDate: '2026-05-14T00:00:00',
  replyCount: 2,
};

const topicWithNullContent: TopicDto = {
  ...topic,
  content: null,
};

const topicPage: TopicPage = {
  content: [topic],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 20,
};

const reply: ReplyDto = {
  id: 'reply-1',
  topicId: 'topic-1',
  parentReplyId: null,
  content: 'Hello world',
  createdBy: 'bob',
  createdDate: '2026-05-14T00:01:00',
};

const replyPage: ReplyPage = {
  content: [reply],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 50,
};

describe('discussionService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns guarded topic pages without status filter', async () => {
    mockedApi.get.mockResolvedValueOnce(topicPage);

    await expect(discussionService.listTopics('engineering')).resolves.toEqual(topicPage);

    expect(mockedApi.get).toHaveBeenCalledWith('/sites/engineering/discussions', {
      params: { page: 0, size: 20 },
    });
  });

  it('passes status filter through to listTopics params', async () => {
    mockedApi.get.mockResolvedValueOnce(topicPage);

    await expect(discussionService.listTopics('engineering', 2, 10, 'PINNED')).resolves.toEqual(topicPage);

    expect(mockedApi.get).toHaveBeenCalledWith('/sites/engineering/discussions', {
      params: { page: 2, size: 10, status: 'PINNED' },
    });
  });

  it('accepts topics with null content in page items', async () => {
    const pageWithNullContent: TopicPage = {
      ...topicPage,
      content: [topicWithNullContent],
    };
    mockedApi.get.mockResolvedValueOnce(pageWithNullContent);

    await expect(discussionService.listTopics('engineering')).resolves.toEqual(pageWithNullContent);
  });

  it('rejects HTML fallback for topic pages', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(discussionService.listTopics('engineering')).rejects.toThrow(
      DISCUSSION_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects malformed topic page items', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...topicPage,
      content: [{ ...topic, tags: 'not-a-list' }],
    });

    await expect(discussionService.listTopics('engineering')).rejects.toThrow(
      DISCUSSION_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('returns guarded topic readbacks for getTopic', async () => {
    mockedApi.get.mockResolvedValueOnce(topic);

    await expect(discussionService.getTopic('engineering', 'topic-1')).resolves.toEqual(topic);

    expect(mockedApi.get).toHaveBeenCalledWith('/sites/engineering/discussions/topic-1');
  });

  it('rejects malformed getTopic readbacks', async () => {
    mockedApi.get.mockResolvedValueOnce({ ...topic, status: 'ARCHIVED' });

    await expect(discussionService.getTopic('engineering', 'topic-1')).rejects.toThrow(
      DISCUSSION_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('returns guarded createTopic readbacks and forwards payload', async () => {
    mockedApi.post.mockResolvedValueOnce(topic);

    await expect(
      discussionService.createTopic('engineering', 'Welcome', 'Greetings', ['intro']),
    ).resolves.toEqual(topic);

    expect(mockedApi.post).toHaveBeenCalledWith('/sites/engineering/discussions', {
      title: 'Welcome',
      content: 'Greetings',
      tags: ['intro'],
    });
  });

  it('rejects malformed createTopic readbacks', async () => {
    mockedApi.post.mockResolvedValueOnce({ ...topic, replyCount: 'two' });

    await expect(
      discussionService.createTopic('engineering', 'Welcome'),
    ).rejects.toThrow(DISCUSSION_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('returns guarded updateTopic readbacks and forwards payload', async () => {
    const updated: TopicDto = { ...topic, status: 'CLOSED' };
    mockedApi.put.mockResolvedValueOnce(updated);

    await expect(
      discussionService.updateTopic('engineering', 'topic-1', { status: 'CLOSED' }),
    ).resolves.toEqual(updated);

    expect(mockedApi.put).toHaveBeenCalledWith('/sites/engineering/discussions/topic-1', {
      status: 'CLOSED',
    });
  });

  it('rejects malformed updateTopic readbacks', async () => {
    mockedApi.put.mockResolvedValueOnce({ ...topic, tags: [42] });

    await expect(
      discussionService.updateTopic('engineering', 'topic-1', { title: 'New' }),
    ).rejects.toThrow(DISCUSSION_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('keeps deleteTopic wiring as a no-content endpoint', async () => {
    mockedApi.delete.mockResolvedValueOnce(undefined);

    await discussionService.deleteTopic('engineering', 'topic-1');

    expect(mockedApi.delete).toHaveBeenCalledWith('/sites/engineering/discussions/topic-1');
  });

  it('returns guarded reply pages and forwards pagination params', async () => {
    mockedApi.get.mockResolvedValueOnce(replyPage);

    await expect(discussionService.listReplies('engineering', 'topic-1', 1, 25)).resolves.toEqual(replyPage);

    expect(mockedApi.get).toHaveBeenCalledWith('/sites/engineering/discussions/topic-1/replies', {
      params: { page: 1, size: 25 },
    });
  });

  it('rejects malformed reply page items', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...replyPage,
      content: [{ ...reply, content: 42 }],
    });

    await expect(
      discussionService.listReplies('engineering', 'topic-1'),
    ).rejects.toThrow(DISCUSSION_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('rejects HTML fallback for reply pages', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(
      discussionService.listReplies('engineering', 'topic-1'),
    ).rejects.toThrow(DISCUSSION_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('returns guarded createReply readbacks and forwards payload', async () => {
    mockedApi.post.mockResolvedValueOnce(reply);

    await expect(
      discussionService.createReply('engineering', 'topic-1', 'Hello world', 'reply-parent'),
    ).resolves.toEqual(reply);

    expect(mockedApi.post).toHaveBeenCalledWith('/sites/engineering/discussions/topic-1/replies', {
      content: 'Hello world',
      parentReplyId: 'reply-parent',
    });
  });

  it('rejects malformed createReply readbacks', async () => {
    mockedApi.post.mockResolvedValueOnce({ ...reply, content: null });

    await expect(
      discussionService.createReply('engineering', 'topic-1', 'Hello'),
    ).rejects.toThrow(DISCUSSION_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('returns guarded updateReply readbacks and forwards payload', async () => {
    const updated: ReplyDto = { ...reply, content: 'Edited' };
    mockedApi.put.mockResolvedValueOnce(updated);

    await expect(
      discussionService.updateReply('engineering', 'topic-1', 'reply-1', 'Edited'),
    ).resolves.toEqual(updated);

    expect(mockedApi.put).toHaveBeenCalledWith(
      '/sites/engineering/discussions/topic-1/replies/reply-1',
      { content: 'Edited' },
    );
  });

  it('rejects malformed updateReply readbacks', async () => {
    mockedApi.put.mockResolvedValueOnce({ ...reply, createdDate: 12345 });

    await expect(
      discussionService.updateReply('engineering', 'topic-1', 'reply-1', 'Edited'),
    ).rejects.toThrow(DISCUSSION_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('keeps deleteReply wiring as a no-content endpoint', async () => {
    mockedApi.delete.mockResolvedValueOnce(undefined);

    await discussionService.deleteReply('engineering', 'topic-1', 'reply-1');

    expect(mockedApi.delete).toHaveBeenCalledWith(
      '/sites/engineering/discussions/topic-1/replies/reply-1',
    );
  });
});
