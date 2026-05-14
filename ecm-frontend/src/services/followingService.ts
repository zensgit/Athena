import api from './api';

export const FOLLOWING_UNEXPECTED_RESPONSE_MESSAGE =
  'Following endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

export type FollowTargetType = 'USER' | 'SITE' | 'NODE';

const isFollowTargetType = (value: unknown): value is FollowTargetType => (
  value === 'USER' || value === 'SITE' || value === 'NODE'
);

export interface FollowSubscriptionDto {
  id: string;
  userId: string;
  targetType: FollowTargetType;
  targetId: string;
  createdAt: string;
}

const assertUnexpectedResponse = (): never => {
  throw new Error(FOLLOWING_UNEXPECTED_RESPONSE_MESSAGE);
};

const isFollowSubscription = (value: unknown): value is FollowSubscriptionDto => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.userId === 'string'
    && isFollowTargetType(value.targetType)
    && typeof value.targetId === 'string'
    && typeof value.createdAt === 'string';
};

const assertFollowSubscription = (value: unknown): FollowSubscriptionDto => (
  isFollowSubscription(value) ? value : assertUnexpectedResponse()
);

const assertFollowSubscriptionList = (value: unknown): FollowSubscriptionDto[] => {
  if (!Array.isArray(value) || !value.every(isFollowSubscription)) {
    throw new Error(FOLLOWING_UNEXPECTED_RESPONSE_MESSAGE);
  }
  return value;
};

class FollowingService {
  async list(): Promise<FollowSubscriptionDto[]> {
    const result = await api.get<unknown>('/followings');
    return assertFollowSubscriptionList(result);
  }

  async check(targetType: FollowTargetType, targetId: string): Promise<boolean> {
    const result = await api.get<unknown>('/followings/check', { params: { targetType, targetId } });
    if (typeof result !== 'boolean') {
      throw new Error(FOLLOWING_UNEXPECTED_RESPONSE_MESSAGE);
    }
    return result;
  }

  async follow(targetType: FollowTargetType, targetId: string): Promise<FollowSubscriptionDto> {
    const result = await api.post<unknown>('/followings', { targetType, targetId });
    return assertFollowSubscription(result);
  }

  async unfollow(targetType: FollowTargetType, targetId: string): Promise<void> {
    return api.delete(`/followings/${targetType}/${encodeURIComponent(targetId)}`);
  }
}

const followingService = new FollowingService();
export default followingService;
