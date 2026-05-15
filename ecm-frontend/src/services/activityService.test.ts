import api from './api';
import activityService, {
  ACTIVITY_UNEXPECTED_RESPONSE_MESSAGE,
  ActivityDto,
  ActivityPage,
} from './activityService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const activity: ActivityDto = {
  id: 'activity-1',
  activityType: 'NODE_CREATED',
  userId: 'admin',
  siteId: 'site-a',
  nodeId: 'node-1',
  nodeName: 'Contract.pdf',
  summary: {
    action: 'created',
  },
  postedAt: '2026-05-14T10:00:00',
};

const nullableActivity: ActivityDto = {
  id: 'activity-2',
  activityType: 'LOGIN',
  userId: 'editor',
  siteId: null,
  nodeId: null,
  nodeName: null,
  summary: {},
  postedAt: '2026-05-14T10:01:00',
};

const page = (content: ActivityDto[] = [activity]): ActivityPage => ({
  content,
  totalElements: content.length,
  totalPages: 1,
  number: 0,
  size: 20,
});

describe('activityService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns guarded global activity pages and forwards paging params', async () => {
    const response = page([activity, nullableActivity]);
    mockedApi.get.mockResolvedValueOnce(response);

    await expect(activityService.getGlobalFeed(2, 5)).resolves.toEqual(response);

    expect(mockedApi.get).toHaveBeenCalledWith('/activities', {
      params: { page: 2, size: 5 },
    });
  });

  it('returns guarded user activity pages and keeps the user route', async () => {
    const response = page();
    mockedApi.get.mockResolvedValueOnce(response);

    await expect(activityService.getUserFeed('admin', 1, 10)).resolves.toEqual(response);

    expect(mockedApi.get).toHaveBeenCalledWith('/activities/users/admin', {
      params: { page: 1, size: 10 },
    });
  });

  it('returns guarded site activity pages and keeps the site route', async () => {
    const response = page();
    mockedApi.get.mockResolvedValueOnce(response);

    await expect(activityService.getSiteFeed('site-a', 3, 15)).resolves.toEqual(response);

    expect(mockedApi.get).toHaveBeenCalledWith('/activities/sites/site-a', {
      params: { page: 3, size: 15 },
    });
  });

  it('returns guarded following activity pages and keeps default paging', async () => {
    const response = page();
    mockedApi.get.mockResolvedValueOnce(response);

    await expect(activityService.getFollowingFeed()).resolves.toEqual(response);

    expect(mockedApi.get).toHaveBeenCalledWith('/activities/following', {
      params: { page: 0, size: 20 },
    });
  });

  it('returns guarded node activity pages and keeps the node route', async () => {
    const response = page();
    mockedApi.get.mockResolvedValueOnce(response);

    await expect(activityService.getNodeFeed('node-uuid', 4, 25)).resolves.toEqual(response);

    expect(mockedApi.get).toHaveBeenCalledWith('/activities/nodes/node-uuid', {
      params: { page: 4, size: 25 },
    });
  });

  it('accepts nullable backend activity fields', async () => {
    const response = page([nullableActivity]);
    mockedApi.get.mockResolvedValueOnce(response);

    await expect(activityService.getGlobalFeed()).resolves.toEqual(response);
  });

  it('rejects HTML fallback for activity pages', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(activityService.getGlobalFeed()).rejects.toThrow(
      ACTIVITY_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects malformed activity page envelopes', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...page(),
      totalElements: '1',
    });

    await expect(activityService.getGlobalFeed()).rejects.toThrow(
      ACTIVITY_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects malformed activity page entries', async () => {
    mockedApi.get.mockResolvedValueOnce(page([
      {
        ...activity,
        summary: [] as unknown as Record<string, unknown>,
      },
    ]));

    await expect(activityService.getGlobalFeed()).rejects.toThrow(
      ACTIVITY_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });
});
