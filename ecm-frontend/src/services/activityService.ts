import api from './api';

export const ACTIVITY_UNEXPECTED_RESPONSE_MESSAGE =
  'Activity endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

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

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isStringOrNullish = (value: unknown): value is string | null | undefined => (
  value === null || value === undefined || typeof value === 'string'
);

const isFiniteNumber = (value: unknown): value is number => (
  typeof value === 'number' && Number.isFinite(value)
);

const assertUnexpectedResponse = (): never => {
  throw new Error(ACTIVITY_UNEXPECTED_RESPONSE_MESSAGE);
};

const isActivityDto = (value: unknown): value is ActivityDto => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.activityType === 'string'
    && typeof value.userId === 'string'
    && isStringOrNullish(value.siteId)
    && isStringOrNullish(value.nodeId)
    && isStringOrNullish(value.nodeName)
    && isObject(value.summary)
    && typeof value.postedAt === 'string';
};

const isActivityPage = (value: unknown): value is ActivityPage => (
  isObject(value)
    && Array.isArray(value.content)
    && value.content.every(isActivityDto)
    && isFiniteNumber(value.totalElements)
    && isFiniteNumber(value.totalPages)
    && isFiniteNumber(value.number)
    && isFiniteNumber(value.size)
);

const assertActivityPage = (value: unknown): ActivityPage => (
  isActivityPage(value) ? value : assertUnexpectedResponse()
);

class ActivityService {
  async getGlobalFeed(page = 0, size = 20): Promise<ActivityPage> {
    const result = await api.get<unknown>('/activities', { params: { page, size } });
    return assertActivityPage(result);
  }

  async getUserFeed(userId: string, page = 0, size = 20): Promise<ActivityPage> {
    const result = await api.get<unknown>(`/activities/users/${userId}`, { params: { page, size } });
    return assertActivityPage(result);
  }

  async getSiteFeed(siteId: string, page = 0, size = 20): Promise<ActivityPage> {
    const result = await api.get<unknown>(`/activities/sites/${siteId}`, { params: { page, size } });
    return assertActivityPage(result);
  }

  async getFollowingFeed(page = 0, size = 20): Promise<ActivityPage> {
    const result = await api.get<unknown>('/activities/following', { params: { page, size } });
    return assertActivityPage(result);
  }

  async getNodeFeed(nodeId: string, page = 0, size = 20): Promise<ActivityPage> {
    const result = await api.get<unknown>(`/activities/nodes/${nodeId}`, { params: { page, size } });
    return assertActivityPage(result);
  }
}

const activityService = new ActivityService();
export default activityService;
