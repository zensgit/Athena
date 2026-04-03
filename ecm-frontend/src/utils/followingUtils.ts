import { FollowSubscriptionDto, FollowTargetType } from 'services/followingService';
import { ActivityTargetKind } from './siteActivityUtils';

export interface FollowTargetLink {
  href: string;
  label: string;
}

export interface FollowSubscriptionGroup {
  targetType: FollowTargetType;
  label: string;
  subscriptions: FollowSubscriptionDto[];
}

const FOLLOW_GROUP_LABELS: Record<FollowTargetType, string> = {
  USER: 'Users',
  SITE: 'Sites',
  NODE: 'Nodes',
};

export const getFollowTargetLink = (subscription: FollowSubscriptionDto): FollowTargetLink => {
  switch (subscription.targetType) {
    case 'USER':
      return {
        href: `/people-directory?username=${encodeURIComponent(subscription.targetId)}`,
        label: 'Open User',
      };
    case 'SITE':
      return {
        href: `/sites?siteId=${encodeURIComponent(subscription.targetId)}`,
        label: 'Open Site',
      };
    case 'NODE':
      return {
        href: `/browse/${subscription.targetId}`,
        label: 'Open Node',
      };
  }
};

export const matchesFollowSubscriptionTargetKind = (
  subscription: FollowSubscriptionDto,
  targetKind: ActivityTargetKind | 'ALL'
): boolean => targetKind === 'ALL' || subscription.targetType === targetKind;

export const groupFollowSubscriptions = (
  subscriptions: FollowSubscriptionDto[],
  targetKind: ActivityTargetKind | 'ALL'
): FollowSubscriptionGroup[] => {
  const orderedTargetTypes: FollowTargetType[] = ['SITE', 'USER', 'NODE'];

  return orderedTargetTypes
    .map((targetType) => {
      const filtered = subscriptions.filter(
        (subscription) =>
          subscription.targetType === targetType
          && matchesFollowSubscriptionTargetKind(subscription, targetKind)
      );
      return {
        targetType,
        label: FOLLOW_GROUP_LABELS[targetType],
        subscriptions: filtered,
      };
    })
    .filter((group) => group.subscriptions.length > 0);
};
