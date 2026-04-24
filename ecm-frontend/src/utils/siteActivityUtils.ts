import { ActivityDto } from 'services/activityService';

export interface ActivityLinkTarget {
  href: string;
  label: string;
}

export type ActivityTargetKind = 'USER' | 'SITE' | 'NODE';

const ACTIVITY_LABELS: Record<string, string> = {
  'comment.added': 'Comment Added',
  'calendar.created': 'Calendar Event Created',
  'calendar.deleted': 'Calendar Event Deleted',
  'calendar.updated': 'Calendar Event Updated',
  'discussion.reply.created': 'Discussion Reply Added',
  'discussion.reply.deleted': 'Discussion Reply Deleted',
  'discussion.reply.updated': 'Discussion Reply Updated',
  'discussion.topic.created': 'Discussion Topic Created',
  'discussion.topic.deleted': 'Discussion Topic Deleted',
  'discussion.topic.updated': 'Discussion Topic Updated',
  'node.archived': 'Node Archived',
  'node.created': 'Node Created',
  'node.deleted': 'Node Deleted',
  'node.locked': 'Node Locked',
  'node.moved': 'Node Moved',
  'node.restored': 'Node Restored',
  'node.unlocked': 'Node Unlocked',
  'node.updated': 'Node Updated',
  'site.archived': 'Site Archived',
  'site.created': 'Site Created',
  'site.member.added': 'Member Added',
  'site.member.removed': 'Member Removed',
  'site.member.role_changed': 'Member Role Changed',
  'site.membership.approved': 'Membership Approved',
  'site.membership.rejected': 'Membership Rejected',
  'site.membership.requested': 'Membership Requested',
  'site.membership.withdrawn': 'Membership Withdrawn',
  'site.updated': 'Site Updated',
  'version.created': 'Version Created',
  'rm.report_preset.delivery.succeeded': 'Scheduled Delivery Succeeded',
  'rm.report_preset.delivery.failed': 'Scheduled Delivery Failed',
};

const formatFallbackLabel = (activityType: string): string =>
  activityType
    .split('.')
    .map((segment) => segment.charAt(0).toUpperCase() + segment.slice(1))
    .join(' ');

const getSummaryString = (summary: Record<string, unknown>, key: string): string | null => {
  const value = summary[key];
  return typeof value === 'string' && value.trim() ? value : null;
};

export const formatActivityLabel = (activityType: string): string =>
  ACTIVITY_LABELS[activityType] ?? formatFallbackLabel(activityType);

export const formatActivitySummary = (activity: ActivityDto): string => {
  const summary = activity.summary ?? {};
  const role = getSummaryString(summary, 'role');
  const memberUsername = getSummaryString(summary, 'memberUsername');
  const title = getSummaryString(summary, 'title');
  const visibility = getSummaryString(summary, 'visibility');
  const status = getSummaryString(summary, 'status');
  const action = getSummaryString(summary, 'action');
  const versionLabel = getSummaryString(summary, 'versionLabel');
  const presetName = getSummaryString(summary, 'presetName');
  const triggerType = getSummaryString(summary, 'triggerType');
  const message = getSummaryString(summary, 'message');
  const filename = getSummaryString(summary, 'filename');

  switch (activity.activityType) {
    case 'site.created':
      return `Created ${title ?? activity.siteId ?? 'site'}${visibility ? ` with ${visibility.toLowerCase()} visibility` : ''}.`;
    case 'site.updated':
      return `Updated ${title ?? activity.siteId ?? 'site'}${status ? ` (${status.toLowerCase()})` : ''}.`;
    case 'site.archived':
      return `Archived ${title ?? activity.siteId ?? 'site'}.`;
    case 'site.member.added':
      return `${memberUsername ?? 'Member'} joined${role ? ` as ${role.toLowerCase()}` : ''}.`;
    case 'site.member.role_changed':
      return `Changed ${memberUsername ?? 'member'} to ${role?.toLowerCase() ?? 'a new role'}.`;
    case 'site.member.removed':
      return `Removed ${memberUsername ?? 'member'} from the site.`;
    case 'site.membership.requested':
      return `Requested ${role?.toLowerCase() ?? 'member'} access.`;
    case 'site.membership.approved':
      return `Approved ${role?.toLowerCase() ?? 'membership'} request.`;
    case 'site.membership.rejected':
      return `Rejected ${role?.toLowerCase() ?? 'membership'} request.`;
    case 'site.membership.withdrawn':
      return 'Withdrawn membership request.';
    case 'discussion.topic.created':
      return `Created discussion topic ${title ?? 'topic'}.`;
    case 'discussion.topic.updated':
      return `Updated discussion topic ${title ?? 'topic'}${status ? ` (${status.toLowerCase()})` : ''}.`;
    case 'discussion.topic.deleted':
      return `Deleted discussion topic ${title ?? 'topic'}.`;
    case 'discussion.reply.created':
      return `Added a reply to ${title ?? 'discussion topic'}.`;
    case 'discussion.reply.updated':
      return `Updated a reply in ${title ?? 'discussion topic'}.`;
    case 'discussion.reply.deleted':
      return `Deleted a reply from ${title ?? 'discussion topic'}.`;
    case 'calendar.created':
      return `Created calendar event ${title ?? 'event'}.`;
    case 'calendar.updated':
      return `Updated calendar event ${title ?? 'event'}.`;
    case 'calendar.deleted':
      return `Deleted calendar event ${title ?? 'event'}.`;
    case 'node.archived':
      return `Archived ${activity.nodeName ?? 'node'}${getSummaryString(summary, 'archiveStoreTier') ? ` to ${String(getSummaryString(summary, 'archiveStoreTier')).toLowerCase()} storage` : ''}.`;
    case 'node.restored':
      return `Restored ${activity.nodeName ?? 'node'} to hot storage.`;
    case 'version.created':
      return versionLabel ? `Created version ${versionLabel}.` : 'Created a new version.';
    case 'rm.report_preset.delivery.succeeded': {
      const triggerLabel = triggerType ? triggerType.toLowerCase() : 'scheduled';
      return `Delivered ${presetName ?? 'report preset'} (${triggerLabel})${filename ? ` as ${filename}` : ''}.`;
    }
    case 'rm.report_preset.delivery.failed': {
      const triggerLabel = triggerType ? triggerType.toLowerCase() : 'scheduled';
      return `Delivery failed for ${presetName ?? 'report preset'} (${triggerLabel})${message ? `: ${message}.` : '.'}`;
    }
    default:
      if (action) {
        return `${action.charAt(0).toUpperCase() + action.slice(1)}${activity.nodeName ? ` ${activity.nodeName}` : ''}.`;
      }
      return activity.nodeName
        ? `Updated ${activity.nodeName}.`
        : 'No additional details available.';
  }
};

export const matchesActivityFilter = (activity: ActivityDto, filter: string): boolean => {
  const normalizedFilter = filter.trim().toLowerCase();
  if (!normalizedFilter) {
    return true;
  }

  const haystack = [
    activity.activityType,
    formatActivityLabel(activity.activityType),
    formatActivitySummary(activity),
    activity.siteId,
    activity.nodeName,
  ]
    .filter(Boolean)
    .join(' ')
    .toLowerCase();

  return haystack.includes(normalizedFilter);
};

export const getActivityTargetKind = (activity: ActivityDto): ActivityTargetKind => {
  if (activity.nodeId) {
    return 'NODE';
  }
  if (activity.siteId) {
    return 'SITE';
  }
  return 'USER';
};

export const matchesActivityTargetKind = (
  activity: ActivityDto,
  targetKind: ActivityTargetKind | 'ALL'
): boolean => targetKind === 'ALL' || getActivityTargetKind(activity) === targetKind;

export const getActivityLinkTargets = (activity: ActivityDto): ActivityLinkTarget[] => {
  const targets: ActivityLinkTarget[] = [];

  if (
    activity.activityType === 'rm.report_preset.delivery.failed'
    || activity.activityType === 'rm.report_preset.delivery.succeeded'
  ) {
    targets.push({
      href: '/admin/records-management',
      label: 'Open Records Management',
    });
  }

  if (activity.siteId) {
    targets.push({
      href: `/sites?siteId=${encodeURIComponent(activity.siteId)}`,
      label: 'Open Site',
    });
  }

  if (activity.nodeId) {
    targets.push({
      href: `/browse/${activity.nodeId}`,
      label: 'Open Node',
    });
  }

  return targets;
};
