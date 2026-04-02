import { ActivityDto } from 'services/activityService';

export interface ActivityLinkTarget {
  href: string;
  label: string;
}

const ACTIVITY_LABELS: Record<string, string> = {
  'comment.added': 'Comment Added',
  'node.created': 'Node Created',
  'node.deleted': 'Node Deleted',
  'node.locked': 'Node Locked',
  'node.moved': 'Node Moved',
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
    case 'version.created':
      return versionLabel ? `Created version ${versionLabel}.` : 'Created a new version.';
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

export const getActivityLinkTargets = (activity: ActivityDto): ActivityLinkTarget[] => {
  const targets: ActivityLinkTarget[] = [];

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
