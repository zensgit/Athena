import api from './api';
import followingService, {
  FOLLOWING_UNEXPECTED_RESPONSE_MESSAGE,
  FollowSubscriptionDto,
} from './followingService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    delete: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const subscription: FollowSubscriptionDto = {
  id: 'subscription-1',
  userId: 'alice',
  targetType: 'SITE',
  targetId: 'engineering',
  createdAt: '2026-05-14T00:00:00',
};

describe('followingService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns guarded subscription lists', async () => {
    mockedApi.get.mockResolvedValueOnce([subscription]);

    await expect(followingService.list()).resolves.toEqual([subscription]);

    expect(mockedApi.get).toHaveBeenCalledWith('/followings');
  });

  it('rejects HTML fallback for subscription lists', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(followingService.list()).rejects.toThrow(FOLLOWING_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('rejects malformed subscription list items', async () => {
    mockedApi.get.mockResolvedValueOnce([{ ...subscription, targetType: 'PROJECT' }]);

    await expect(followingService.list()).rejects.toThrow(FOLLOWING_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('returns guarded follow check responses', async () => {
    mockedApi.get.mockResolvedValueOnce(true);

    await expect(followingService.check('SITE', 'engineering')).resolves.toBe(true);

    expect(mockedApi.get).toHaveBeenCalledWith('/followings/check', {
      params: { targetType: 'SITE', targetId: 'engineering' },
    });
  });

  it('rejects malformed follow check responses', async () => {
    mockedApi.get.mockResolvedValueOnce('true');

    await expect(followingService.check('SITE', 'engineering')).rejects.toThrow(
      FOLLOWING_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('returns guarded follow mutation readbacks', async () => {
    mockedApi.post.mockResolvedValueOnce(subscription);

    await expect(followingService.follow('SITE', 'engineering')).resolves.toEqual(subscription);

    expect(mockedApi.post).toHaveBeenCalledWith('/followings', {
      targetType: 'SITE',
      targetId: 'engineering',
    });
  });

  it('rejects malformed follow mutation readbacks', async () => {
    mockedApi.post.mockResolvedValueOnce({ ...subscription, targetId: null });

    await expect(followingService.follow('SITE', 'engineering')).rejects.toThrow(
      FOLLOWING_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('keeps unfollow endpoint wiring unchanged', async () => {
    mockedApi.delete.mockResolvedValueOnce(undefined);

    await followingService.unfollow('USER', 'alice@example.com');

    expect(mockedApi.delete).toHaveBeenCalledWith('/followings/USER/alice%40example.com');
  });
});
