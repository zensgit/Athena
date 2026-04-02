import {
  formatActivityLabel,
  formatActivitySummary,
  getActivityLinkTargets,
  matchesActivityFilter,
} from './siteActivityUtils';
import { ActivityDto } from 'services/activityService';

const buildActivity = (overrides: Partial<ActivityDto> = {}): ActivityDto => ({
  id: 'activity-1',
  activityType: 'site.member.role_changed',
  userId: 'admin',
  siteId: 'engineering',
  nodeId: 'node-123',
  nodeName: 'spec.docx',
  summary: { memberUsername: 'alice', role: 'MANAGER' },
  postedAt: '2026-04-02T08:00:00Z',
  ...overrides,
});

describe('siteActivityUtils', () => {
  it('formats known activity labels', () => {
    expect(formatActivityLabel('site.member.role_changed')).toBe('Member Role Changed');
  });

  it('formats membership summaries with role and username', () => {
    expect(formatActivitySummary(buildActivity())).toBe('Changed alice to manager.');
  });

  it('formats version activity summary from version label', () => {
    expect(
      formatActivitySummary(
        buildActivity({
          activityType: 'version.created',
          summary: { versionLabel: '2.0' },
          nodeId: null,
          nodeName: null,
        })
      )
    ).toBe('Created version 2.0.');
  });

  it('matches activity filters against labels and summaries', () => {
    expect(matchesActivityFilter(buildActivity(), 'manager')).toBe(true);
    expect(matchesActivityFilter(buildActivity(), 'role changed')).toBe(true);
    expect(matchesActivityFilter(buildActivity(), 'finance')).toBe(false);
  });

  it('builds site and node drill-down links', () => {
    expect(getActivityLinkTargets(buildActivity())).toEqual([
      { href: '/sites?siteId=engineering', label: 'Open Site' },
      { href: '/browse/node-123', label: 'Open Node' },
    ]);
  });
});
