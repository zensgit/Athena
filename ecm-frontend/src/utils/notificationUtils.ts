import { ActivityDto } from 'services/activityService';
import { NotificationDto } from 'services/notificationService';
import {
  ActivityLinkTarget,
  formatActivityLabel,
  formatActivitySummary,
  getActivityLinkTargets,
} from 'utils/siteActivityUtils';

export const toActivityFromNotification = (notification: NotificationDto): ActivityDto => ({
  id: notification.id,
  activityType: notification.activityType,
  userId: notification.actorUserId,
  siteId: notification.siteId ?? null,
  nodeId: notification.nodeId ?? null,
  nodeName: notification.nodeName ?? null,
  summary: notification.summary ?? {},
  postedAt: notification.createdAt,
});

export const formatNotificationLabel = (notification: NotificationDto): string =>
  formatActivityLabel(notification.activityType);

export const formatNotificationSummary = (notification: NotificationDto): string =>
  formatActivitySummary(toActivityFromNotification(notification));

export const getNotificationLinkTargets = (notification: NotificationDto): ActivityLinkTarget[] => {
  const activity = toActivityFromNotification(notification);
  const targets = [...getActivityLinkTargets(activity)];
  const params = new URLSearchParams();

  if (notification.siteId) {
    params.set('siteId', notification.siteId);
  } else {
    params.set('scope', 'global');
  }

  if (notification.activityType) {
    params.set('type', notification.activityType);
  }

  targets.push({
    href: `/activities?${params.toString()}`,
    label: 'Open Activity',
  });

  return targets;
};
