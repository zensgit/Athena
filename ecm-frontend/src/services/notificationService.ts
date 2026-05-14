import api from './api';

export const NOTIFICATION_UNEXPECTED_RESPONSE_MESSAGE =
  'Notification endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isNumber = (value: unknown): value is number => (
  typeof value === 'number' && Number.isFinite(value)
);

const isStringOrNullish = (value: unknown): value is string | null | undefined => (
  value === null || value === undefined || typeof value === 'string'
);

export interface NotificationDto {
  id: string;
  activityType: string | null;
  actorUserId: string | null;
  siteId?: string | null;
  nodeId?: string | null;
  nodeName?: string | null;
  summary: Record<string, unknown>;
  read: boolean;
  readAt?: string | null;
  createdAt: string;
}

export interface NotificationPage {
  content: NotificationDto[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

const assertUnexpectedResponse = (): never => {
  throw new Error(NOTIFICATION_UNEXPECTED_RESPONSE_MESSAGE);
};

const isNotificationDto = (value: unknown): value is NotificationDto => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && isStringOrNullish(value.activityType)
    && isStringOrNullish(value.actorUserId)
    && isStringOrNullish(value.siteId)
    && isStringOrNullish(value.nodeId)
    && isStringOrNullish(value.nodeName)
    && isObject(value.summary)
    && typeof value.read === 'boolean'
    && isStringOrNullish(value.readAt)
    && typeof value.createdAt === 'string';
};

const assertNotificationDto = (value: unknown): NotificationDto => (
  isNotificationDto(value) ? value : assertUnexpectedResponse()
);

const isNotificationPage = (value: unknown): value is NotificationPage => {
  if (!isObject(value) || !Array.isArray(value.content)) {
    return false;
  }
  return value.content.every(isNotificationDto)
    && isNumber(value.totalElements)
    && isNumber(value.totalPages)
    && isNumber(value.number)
    && isNumber(value.size);
};

const assertNotificationPage = (value: unknown): NotificationPage => (
  isNotificationPage(value) ? value : assertUnexpectedResponse()
);

const assertNumberField = (value: unknown, fieldName: string): number => {
  if (!isObject(value)) {
    throw new Error(NOTIFICATION_UNEXPECTED_RESPONSE_MESSAGE);
  }
  const fieldValue = value[fieldName];
  if (!isNumber(fieldValue)) {
    throw new Error(NOTIFICATION_UNEXPECTED_RESPONSE_MESSAGE);
  }
  return fieldValue;
};

class NotificationService {
  async getInbox(page = 0, size = 20): Promise<NotificationPage> {
    const result = await api.get<unknown>('/notifications', { params: { page, size } });
    return assertNotificationPage(result);
  }

  async getUnread(page = 0, size = 20): Promise<NotificationPage> {
    const result = await api.get<unknown>('/notifications/unread', { params: { page, size } });
    return assertNotificationPage(result);
  }

  async getUnreadCount(): Promise<number> {
    const result = await api.get<unknown>('/notifications/unread-count');
    return assertNumberField(result, 'count');
  }

  async markRead(id: string): Promise<NotificationDto> {
    const result = await api.patch<unknown>(`/notifications/${id}/read`);
    return assertNotificationDto(result);
  }

  async markAllRead(): Promise<number> {
    const result = await api.post<unknown>('/notifications/mark-all-read');
    return assertNumberField(result, 'marked');
  }

  async deleteNotification(id: string): Promise<void> {
    return api.delete(`/notifications/${id}`);
  }
}

const notificationService = new NotificationService();
export default notificationService;
