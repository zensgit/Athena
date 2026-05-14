import api from './api';
import notificationService, {
  NotificationDto,
  NotificationPage,
  NOTIFICATION_UNEXPECTED_RESPONSE_MESSAGE,
} from './notificationService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    patch: jest.fn(),
    delete: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const notification: NotificationDto = {
  id: 'notification-1',
  activityType: 'site.member.role_changed',
  actorUserId: 'alice',
  siteId: 'engineering',
  nodeId: 'node-1',
  nodeName: 'Design Brief',
  summary: { role: 'COLLABORATOR' },
  read: false,
  readAt: null,
  createdAt: '2026-05-14T00:00:00',
};

const notificationWithMissingActivity: NotificationDto = {
  ...notification,
  activityType: null,
  actorUserId: null,
  siteId: null,
  nodeId: null,
  nodeName: null,
  summary: {},
};

const page: NotificationPage = {
  content: [notification],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 20,
};

describe('notificationService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns guarded inbox pages', async () => {
    mockedApi.get.mockResolvedValueOnce(page);

    await expect(notificationService.getInbox(1, 10)).resolves.toEqual(page);

    expect(mockedApi.get).toHaveBeenCalledWith('/notifications', { params: { page: 1, size: 10 } });
  });

  it('returns guarded unread pages', async () => {
    mockedApi.get.mockResolvedValueOnce(page);

    await expect(notificationService.getUnread(2, 5)).resolves.toEqual(page);

    expect(mockedApi.get).toHaveBeenCalledWith('/notifications/unread', { params: { page: 2, size: 5 } });
  });

  it('accepts backend notifications without attached activity fields', async () => {
    const nullablePage = {
      ...page,
      content: [notificationWithMissingActivity],
    };
    mockedApi.get.mockResolvedValueOnce(nullablePage);

    await expect(notificationService.getInbox()).resolves.toEqual(nullablePage);
  });

  it('rejects HTML fallback for notification pages', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(notificationService.getInbox()).rejects.toThrow(
      NOTIFICATION_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('rejects malformed notification page items', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...page,
      content: [{ ...notification, summary: 'not-json' }],
    });

    await expect(notificationService.getInbox()).rejects.toThrow(
      NOTIFICATION_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('returns guarded unread counts', async () => {
    mockedApi.get.mockResolvedValueOnce({ count: 4 });

    await expect(notificationService.getUnreadCount()).resolves.toBe(4);

    expect(mockedApi.get).toHaveBeenCalledWith('/notifications/unread-count');
  });

  it('rejects malformed unread count responses', async () => {
    mockedApi.get.mockResolvedValueOnce({ count: '4' });

    await expect(notificationService.getUnreadCount()).rejects.toThrow(
      NOTIFICATION_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('returns guarded mark-read readbacks', async () => {
    mockedApi.patch.mockResolvedValueOnce({ ...notification, read: true });

    await expect(notificationService.markRead('notification-1')).resolves.toEqual({
      ...notification,
      read: true,
    });

    expect(mockedApi.patch).toHaveBeenCalledWith('/notifications/notification-1/read');
  });

  it('rejects malformed mark-read readbacks', async () => {
    mockedApi.patch.mockResolvedValueOnce({ ...notification, read: 'true' });

    await expect(notificationService.markRead('notification-1')).rejects.toThrow(
      NOTIFICATION_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('returns guarded mark-all-read counts', async () => {
    mockedApi.post.mockResolvedValueOnce({ marked: 3 });

    await expect(notificationService.markAllRead()).resolves.toBe(3);

    expect(mockedApi.post).toHaveBeenCalledWith('/notifications/mark-all-read');
  });

  it('rejects malformed mark-all-read responses', async () => {
    mockedApi.post.mockResolvedValueOnce({ marked: '3' });

    await expect(notificationService.markAllRead()).rejects.toThrow(
      NOTIFICATION_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('keeps delete endpoint wiring unchanged', async () => {
    mockedApi.delete.mockResolvedValueOnce(undefined);

    await notificationService.deleteNotification('notification-1');

    expect(mockedApi.delete).toHaveBeenCalledWith('/notifications/notification-1');
  });
});
