import {
  formatActivityLabel,
  formatActivitySummary,
  getActivityTargetKind,
  getActivityLinkTargets,
  matchesActivityFilter,
  matchesActivityTargetKind,
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

  it('formats discussion and calendar summaries', () => {
    expect(
      formatActivitySummary(
        buildActivity({
          activityType: 'discussion.topic.created',
          summary: { title: 'Q1 Planning' },
          nodeId: null,
          nodeName: null,
        })
      )
    ).toBe('Created discussion topic Q1 Planning.');

    expect(
      formatActivitySummary(
        buildActivity({
          activityType: 'calendar.deleted',
          summary: { title: 'Board Meeting' },
          nodeId: null,
          nodeName: null,
        })
      )
    ).toBe('Deleted calendar event Board Meeting.');

    expect(
      formatActivitySummary(
        buildActivity({
          activityType: 'node.archived',
          summary: { action: 'archived', archiveStoreTier: 'GLACIER' },
          nodeName: 'spec.docx',
        })
      )
    ).toBe('Archived spec.docx to glacier storage.');
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

  it('formats scheduled delivery failure activity summary', () => {
    expect(
      formatActivitySummary(
        buildActivity({
          activityType: 'rm.report_preset.delivery.failed',
          nodeId: 'folder-123',
          nodeName: null,
          siteId: null,
          summary: {
            presetName: 'Daily RM Family Report',
            triggerType: 'SCHEDULED',
            message: 'Folder not found',
          },
        })
      )
    ).toBe('Delivery failed for Daily RM Family Report (scheduled): Folder not found.');
  });

  it('formats scheduled delivery success activity summary', () => {
    expect(
      formatActivitySummary(
        buildActivity({
          activityType: 'rm.report_preset.delivery.succeeded',
          nodeId: 'document-123',
          nodeName: 'daily-rm-family-report-20260423.csv',
          siteId: null,
          summary: {
            presetName: 'Daily RM Family Report',
            triggerType: 'SCHEDULED',
            filename: 'daily-rm-family-report-20260423.csv',
          },
        })
      )
    ).toBe('Delivered Daily RM Family Report (scheduled) as daily-rm-family-report-20260423.csv.');
  });

  it('matches activity filters against labels and summaries', () => {
    expect(matchesActivityFilter(buildActivity(), 'manager')).toBe(true);
    expect(matchesActivityFilter(buildActivity(), 'role changed')).toBe(true);
    expect(matchesActivityFilter(buildActivity(), 'finance')).toBe(false);
  });

  it('classifies node, site, and user activity targets', () => {
    expect(getActivityTargetKind(buildActivity())).toBe('NODE');
    expect(getActivityTargetKind(buildActivity({ nodeId: null, nodeName: null }))).toBe('SITE');
    expect(getActivityTargetKind(buildActivity({ nodeId: null, nodeName: null, siteId: null }))).toBe('USER');
  });

  it('matches activity target kind filters', () => {
    expect(matchesActivityTargetKind(buildActivity(), 'NODE')).toBe(true);
    expect(matchesActivityTargetKind(buildActivity({ nodeId: null, nodeName: null }), 'SITE')).toBe(true);
    expect(matchesActivityTargetKind(buildActivity({ nodeId: null, nodeName: null, siteId: null }), 'USER')).toBe(true);
    expect(matchesActivityTargetKind(buildActivity(), 'SITE')).toBe(false);
  });

  it('builds site and node drill-down links', () => {
    expect(getActivityLinkTargets(buildActivity())).toEqual([
      { href: '/sites?siteId=engineering', label: 'Open Site' },
      { href: '/browse/node-123', label: 'Open Node' },
    ]);
  });

  it('builds records-management and node drill-down links for preset delivery failures', () => {
    expect(
      getActivityLinkTargets(
        buildActivity({
          activityType: 'rm.report_preset.delivery.failed',
          siteId: null,
          nodeId: 'folder-123',
          nodeName: null,
          summary: { presetName: 'Daily RM Family Report' },
        })
      )
    ).toEqual([
      { href: '/admin/records-management', label: 'Open Records Management' },
      { href: '/browse/folder-123', label: 'Open Node' },
    ]);
  });

  it('builds records-management and node drill-down links for preset delivery successes', () => {
    expect(
      getActivityLinkTargets(
        buildActivity({
          activityType: 'rm.report_preset.delivery.succeeded',
          siteId: null,
          nodeId: 'document-123',
          nodeName: 'daily-rm-family-report-20260423.csv',
          summary: { presetName: 'Daily RM Family Report' },
        })
      )
    ).toEqual([
      { href: '/admin/records-management', label: 'Open Records Management' },
      { href: '/browse/document-123', label: 'Open Node' },
    ]);
  });
});
