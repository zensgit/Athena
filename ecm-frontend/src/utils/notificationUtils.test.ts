import {
  formatNotificationLabel,
  formatNotificationSummary,
  getNotificationLinkTargets,
  toActivityFromNotification,
} from 'utils/notificationUtils';
import { NotificationDto } from 'services/notificationService';

const buildNotification = (overrides: Partial<NotificationDto> = {}): NotificationDto => ({
  id: 'notif-1',
  activityType: 'site.member.role_changed',
  actorUserId: 'alice',
  siteId: 'engineering',
  nodeId: 'node-123',
  nodeName: 'Design Brief',
  summary: {
    role: 'COLLABORATOR',
    memberUsername: 'bob',
  },
  read: false,
  readAt: null,
  createdAt: '2026-04-03T12:00:00Z',
  ...overrides,
});

describe('notificationUtils', () => {
  it('adapts notifications to activity shape', () => {
    const notification = buildNotification();

    expect(toActivityFromNotification(notification)).toEqual({
      id: 'notif-1',
      activityType: 'site.member.role_changed',
      userId: 'alice',
      siteId: 'engineering',
      nodeId: 'node-123',
      nodeName: 'Design Brief',
      summary: {
        role: 'COLLABORATOR',
        memberUsername: 'bob',
      },
      postedAt: '2026-04-03T12:00:00Z',
    });
  });

  it('reuses shared activity formatting for labels and summaries', () => {
    const notification = buildNotification();

    expect(formatNotificationLabel(notification)).toBe('Member Role Changed');
    expect(formatNotificationSummary(notification)).toBe('Changed bob to collaborator.');
  });

  it('builds site, node, and activity drill-down targets', () => {
    const notification = buildNotification();

    expect(getNotificationLinkTargets(notification)).toEqual([
      { href: '/sites?siteId=engineering', label: 'Open Site' },
      { href: '/browse/node-123', label: 'Open Node' },
      { href: '/activities?siteId=engineering&type=site.member.role_changed', label: 'Open Activity' },
    ]);
  });

  it('falls back to global activity drill-down when no site is present', () => {
    const notification = buildNotification({ siteId: null, nodeId: null, nodeName: null });

    expect(getNotificationLinkTargets(notification)).toEqual([
      { href: '/activities?scope=global&type=site.member.role_changed', label: 'Open Activity' },
    ]);
  });
});
