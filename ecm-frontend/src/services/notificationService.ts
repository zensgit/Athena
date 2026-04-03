import api from './api';

export interface NotificationDto {
  id: string;
  activityType: string;
  actorUserId: string;
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

class NotificationService {
  async getInbox(page = 0, size = 20): Promise<NotificationPage> {
    return api.get<NotificationPage>('/notifications', { params: { page, size } });
  }

  async getUnread(page = 0, size = 20): Promise<NotificationPage> {
    return api.get<NotificationPage>('/notifications/unread', { params: { page, size } });
  }

  async getUnreadCount(): Promise<number> {
    const res = await api.get<{ count: number }>('/notifications/unread-count');
    return res.count;
  }

  async markRead(id: string): Promise<NotificationDto> {
    return api.patch<NotificationDto>(`/notifications/${id}/read`);
  }

  async markAllRead(): Promise<number> {
    const res = await api.post<{ marked: number }>('/notifications/mark-all-read');
    return res.marked;
  }

  async deleteNotification(id: string): Promise<void> {
    return api.delete(`/notifications/${id}`);
  }
}

const notificationService = new NotificationService();
export default notificationService;
