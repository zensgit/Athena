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

  it('builds records-management and node links for preset delivery failure notifications', () => {
    const notification = buildNotification({
      activityType: 'rm.report_preset.delivery.failed',
      actorUserId: 'system',
      siteId: null,
      nodeId: 'folder-123',
      nodeName: null,
      summary: {
        presetName: 'Daily RM Family Report',
        triggerType: 'SCHEDULED',
        message: 'Folder not found',
      },
    });

    expect(formatNotificationLabel(notification)).toBe('Scheduled Delivery Failed');
    expect(formatNotificationSummary(notification)).toBe(
      'Delivery failed for Daily RM Family Report (scheduled): Folder not found.'
    );
    expect(getNotificationLinkTargets(notification)).toEqual([
      { href: '/admin/records-management', label: 'Open Records Management' },
      { href: '/browse/folder-123', label: 'Open Node' },
      { href: '/activities?scope=global&type=rm.report_preset.delivery.failed', label: 'Open Activity' },
    ]);
  });

  it('builds records-management and node links for preset delivery success notifications', () => {
    const notification = buildNotification({
      activityType: 'rm.report_preset.delivery.succeeded',
      actorUserId: 'system',
      siteId: null,
      nodeId: 'document-123',
      nodeName: 'daily-rm-family-report-20260423.csv',
      summary: {
        presetName: 'Daily RM Family Report',
        triggerType: 'SCHEDULED',
        filename: 'daily-rm-family-report-20260423.csv',
      },
    });

    expect(formatNotificationLabel(notification)).toBe('Scheduled Delivery Succeeded');
    expect(formatNotificationSummary(notification)).toBe(
      'Delivered Daily RM Family Report (scheduled) as daily-rm-family-report-20260423.csv.'
    );
    expect(getNotificationLinkTargets(notification)).toEqual([
      { href: '/admin/records-management', label: 'Open Records Management' },
      { href: '/browse/document-123', label: 'Open Node' },
      { href: '/activities?scope=global&type=rm.report_preset.delivery.succeeded', label: 'Open Activity' },
    ]);
  });
});
