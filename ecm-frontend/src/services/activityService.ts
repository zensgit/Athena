import api from './api';

export interface ActivityDto {
  id: string;
  activityType: string;
  userId: string;
  siteId?: string | null;
  nodeId?: string | null;
  nodeName?: string | null;
  summary: Record<string, unknown>;
  postedAt: string;
}

export interface ActivityPage {
  content: ActivityDto[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

class ActivityService {
  async getGlobalFeed(page = 0, size = 20): Promise<ActivityPage> {
    return api.get<ActivityPage>('/activities', { params: { page, size } });
  }

  async getUserFeed(userId: string, page = 0, size = 20): Promise<ActivityPage> {
    return api.get<ActivityPage>(`/activities/users/${userId}`, { params: { page, size } });
  }

  async getSiteFeed(siteId: string, page = 0, size = 20): Promise<ActivityPage> {
    return api.get<ActivityPage>(`/activities/sites/${siteId}`, { params: { page, size } });
  }

  async getFollowingFeed(page = 0, size = 20): Promise<ActivityPage> {
    return api.get<ActivityPage>('/activities/following', { params: { page, size } });
  }

  async getNodeFeed(nodeId: string, page = 0, size = 20): Promise<ActivityPage> {
    return api.get<ActivityPage>(`/activities/nodes/${nodeId}`, { params: { page, size } });
  }
}

const activityService = new ActivityService();
export default activityService;
