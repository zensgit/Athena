import api from './api';

export type FollowTargetType = 'USER' | 'SITE' | 'NODE';

export interface FollowSubscriptionDto {
  id: string;
  userId: string;
  targetType: FollowTargetType;
  targetId: string;
  createdAt: string;
}

class FollowingService {
  async list(): Promise<FollowSubscriptionDto[]> {
    return api.get<FollowSubscriptionDto[]>('/followings');
  }

  async check(targetType: FollowTargetType, targetId: string): Promise<boolean> {
    return api.get<boolean>('/followings/check', { params: { targetType, targetId } });
  }

  async follow(targetType: FollowTargetType, targetId: string): Promise<FollowSubscriptionDto> {
    return api.post<FollowSubscriptionDto>('/followings', { targetType, targetId });
  }

  async unfollow(targetType: FollowTargetType, targetId: string): Promise<void> {
    return api.delete(`/followings/${targetType}/${encodeURIComponent(targetId)}`);
  }
}

const followingService = new FollowingService();
export default followingService;
