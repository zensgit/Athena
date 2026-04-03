import { FollowSubscriptionDto } from 'services/followingService';
import {
  getFollowTargetLink,
  groupFollowSubscriptions,
  matchesFollowSubscriptionTargetKind,
} from './followingUtils';

const subscription = (overrides: Partial<FollowSubscriptionDto> = {}): FollowSubscriptionDto => ({
  id: 'sub-1',
  userId: 'alice',
  targetType: 'SITE',
  targetId: 'engineering',
  createdAt: '2026-04-02T12:00:00Z',
  ...overrides,
});

describe('followingUtils', () => {
  it('builds user, site, and node links', () => {
    expect(getFollowTargetLink(subscription({ targetType: 'USER', targetId: 'bob' }))).toEqual({
      href: '/people-directory?username=bob',
      label: 'Open User',
    });
    expect(getFollowTargetLink(subscription({ targetType: 'SITE', targetId: 'engineering' }))).toEqual({
      href: '/sites?siteId=engineering',
      label: 'Open Site',
    });
    expect(getFollowTargetLink(subscription({ targetType: 'NODE', targetId: 'node-123' }))).toEqual({
      href: '/browse/node-123',
      label: 'Open Node',
    });
  });

  it('matches target-kind filters', () => {
    expect(matchesFollowSubscriptionTargetKind(subscription({ targetType: 'SITE' }), 'SITE')).toBe(true);
    expect(matchesFollowSubscriptionTargetKind(subscription({ targetType: 'USER' }), 'USER')).toBe(true);
    expect(matchesFollowSubscriptionTargetKind(subscription({ targetType: 'NODE' }), 'NODE')).toBe(true);
    expect(matchesFollowSubscriptionTargetKind(subscription({ targetType: 'NODE' }), 'SITE')).toBe(false);
  });

  it('groups subscriptions by target type and applies target filter', () => {
    const groups = groupFollowSubscriptions(
      [
        subscription({ id: '1', targetType: 'SITE', targetId: 'engineering' }),
        subscription({ id: '2', targetType: 'USER', targetId: 'bob' }),
        subscription({ id: '3', targetType: 'NODE', targetId: 'node-1' }),
      ],
      'ALL'
    );

    expect(groups.map((group) => group.targetType)).toEqual(['SITE', 'USER', 'NODE']);
    expect(groupFollowSubscriptions(groups.flatMap((group) => group.subscriptions), 'SITE')).toEqual([
      {
        targetType: 'SITE',
        label: 'Sites',
        subscriptions: [subscription({ id: '1', targetType: 'SITE', targetId: 'engineering' })],
      },
    ]);
  });
});
