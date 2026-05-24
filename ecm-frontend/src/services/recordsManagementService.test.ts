import recordsManagementService, {
  RECORDS_MANAGEMENT_BULK_DECLARE_UNEXPECTED_RESPONSE_MESSAGE,
  RECORDS_MANAGEMENT_UNEXPECTED_RESPONSE_MESSAGE,
  supportsReportPresetCsvDelivery,
} from './recordsManagementService';
import api from './api';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    put: jest.fn(),
    delete: jest.fn(),
    downloadFile: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const expectUnexpectedResponse = async (promise: Promise<unknown>) => {
  await expect(promise).rejects.toThrow(RECORDS_MANAGEMENT_UNEXPECTED_RESPONSE_MESSAGE);
};

describe('recordsManagementService', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('lists declared records', async () => {
    mockedApi.get.mockResolvedValueOnce([{ nodeId: 'node-1', name: 'Record 1', path: '/Records/1' }] as any);

    const result = await recordsManagementService.listRecords();

    expect(mockedApi.get).toHaveBeenCalledWith('/records');
    expect(result).toHaveLength(1);
  });

  it('loads record declaration for a node', async () => {
    mockedApi.get.mockResolvedValueOnce({ nodeId: 'node-1', name: 'Record 1', path: '/Records/1' } as any);

    await recordsManagementService.getRecord('node-1');

    expect(mockedApi.get).toHaveBeenCalledWith('/nodes/node-1/record');
  });

  it('declares a node as a record with a trimmed comment', async () => {
    mockedApi.put.mockResolvedValueOnce({ nodeId: 'node-1', name: 'Record 1', path: '/Records/1' } as any);

    await recordsManagementService.declareRecord('node-1', { comment: '  legal hold  ' });

    expect(mockedApi.put).toHaveBeenCalledWith('/nodes/node-1/record', { comment: 'legal hold' });
  });

  it('undeclares a node as a record with a trimmed reason', async () => {
    mockedApi.post.mockResolvedValueOnce(undefined as any);

    await recordsManagementService.undeclareRecord('node-1', { reason: '  governance update  ' });

    expect(mockedApi.post).toHaveBeenCalledWith('/nodes/node-1/record/undeclare', {
      reason: 'governance update',
    });
  });

  it('loads records summary', async () => {
    mockedApi.get.mockResolvedValueOnce({
      declaredRecordCount: 1,
      filePlanCount: 2,
      recordCategoryCount: 3,
      uncategorizedRecordCount: 0,
      outsideFilePlanRecordCount: 1,
      categoryBreakdown: [],
      filePlanBreakdown: [],
    } as any);

    await recordsManagementService.getSummary();

    expect(mockedApi.get).toHaveBeenCalledWith('/records/summary');
  });

  it('lists RM report presets', async () => {
    mockedApi.get.mockResolvedValueOnce([
      {
        id: 'preset-1',
        owner: 'admin',
        name: 'Family current',
        kind: 'ACTIVITY_FAMILY_REPORT',
        params: {
          from: '2026-04-09T00:00:00',
          to: '2026-04-15T23:59:59',
        },
      },
    ] as any);

    await recordsManagementService.listReportPresets();

    expect(mockedApi.get).toHaveBeenCalledWith('/records/report-presets');
  });

  it('rejects malformed RM report preset lists', async () => {
    mockedApi.get.mockResolvedValueOnce('<html>mocked fallback</html>' as any);

    await expectUnexpectedResponse(recordsManagementService.listReportPresets());
  });

  it('creates an RM report preset with trimmed name and description', async () => {
    mockedApi.post.mockResolvedValueOnce({
      id: 'preset-1',
      owner: 'admin',
      name: 'Family current',
      kind: 'ACTIVITY_FAMILY_REPORT',
      params: {
        from: '2026-04-09T00:00:00',
        to: '2026-04-15T23:59:59',
      },
    } as any);

    await recordsManagementService.createReportPreset({
      name: '  Family current  ',
      description: '  Saved from RM highlights  ',
      kind: 'ACTIVITY_FAMILY_REPORT',
      params: {
        from: '2026-04-09T00:00:00',
        to: '2026-04-15T23:59:59',
      },
    });

    expect(mockedApi.post).toHaveBeenCalledWith('/records/report-presets', {
      name: 'Family current',
      description: 'Saved from RM highlights',
      kind: 'ACTIVITY_FAMILY_REPORT',
      params: {
        from: '2026-04-09T00:00:00',
        to: '2026-04-15T23:59:59',
      },
    });
  });

  it('updates an RM report preset with trimmed editable fields', async () => {
    mockedApi.put.mockResolvedValueOnce({
      id: 'preset-1',
      owner: 'admin',
      name: 'Family current updated',
      kind: 'ACTIVITY_FAMILY_REPORT',
      params: {
        from: '2026-04-09T00:00:00',
        to: '2026-04-15T23:59:59',
      },
    } as any);

    await recordsManagementService.updateReportPreset('preset-1', {
      name: '  Family current updated  ',
      description: '  Updated from RM page  ',
      params: {
        from: '2026-04-09T00:00:00',
        to: '2026-04-15T23:59:59',
      },
    });

    expect(mockedApi.put).toHaveBeenCalledWith('/records/report-presets/preset-1', {
      name: 'Family current updated',
      description: 'Updated from RM page',
      params: {
        from: '2026-04-09T00:00:00',
        to: '2026-04-15T23:59:59',
      },
    });
  });

  it('deletes an RM report preset', async () => {
    mockedApi.delete.mockResolvedValueOnce(undefined as any);

    await recordsManagementService.deleteReportPreset('preset-1');

    expect(mockedApi.delete).toHaveBeenCalledWith('/records/report-presets/preset-1');
  });

  it('fetches the scheduled delivery health telemetry', async () => {
    mockedApi.get.mockResolvedValueOnce({
      scheduleEnabledCount: 5,
      duePresetCount: 2,
      last24hSuccessCount: 7,
      last24hFailedCount: 1,
      lastExecutionAt: '2026-04-21T09:00:00',
      generatedAt: '2026-04-21T16:00:00',
    } as any);

    await recordsManagementService.getScheduledDeliveryTelemetry();

    expect(mockedApi.get).toHaveBeenCalledWith('/records/report-presets/telemetry');
  });

  it('fetches an RM report preset schedule status', async () => {
    mockedApi.get.mockResolvedValueOnce({
      presetId: 'preset-1',
      enabled: false,
      cronExpression: null,
      timezone: 'UTC',
      deliveryFolderId: null,
      nextRunAt: null,
      lastRunAt: null,
      lastExecution: null,
    } as any);

    await recordsManagementService.getReportPresetSchedule('preset-1');

    expect(mockedApi.get).toHaveBeenCalledWith('/records/report-presets/preset-1/schedule');
  });

  it('rejects malformed RM report preset schedule status', async () => {
    mockedApi.get.mockResolvedValueOnce({ presetId: 'preset-1', enabled: 'yes' } as any);

    await expectUnexpectedResponse(recordsManagementService.getReportPresetSchedule('preset-1'));
  });

  it('updates an RM report preset schedule with normalized payload', async () => {
    mockedApi.put.mockResolvedValueOnce({
      presetId: 'preset-1',
      enabled: true,
      cronExpression: '0 9 * * MON-FRI',
      timezone: 'America/New_York',
      deliveryFolderId: 'folder-1',
      nextRunAt: '2026-04-22T13:00:00',
      lastRunAt: null,
      lastExecution: null,
    } as any);

    await recordsManagementService.updateReportPresetSchedule('preset-1', {
      enabled: true,
      cronExpression: '0 9 * * MON-FRI',
      timezone: 'America/New_York',
      deliveryFolderId: 'folder-1',
    });

    expect(mockedApi.put).toHaveBeenCalledWith('/records/report-presets/preset-1/schedule', {
      enabled: true,
      cronExpression: '0 9 * * MON-FRI',
      timezone: 'America/New_York',
      deliveryFolderId: 'folder-1',
    });
  });

  it('disables an RM report preset schedule without requiring cron/folder', async () => {
    mockedApi.put.mockResolvedValueOnce({ presetId: 'preset-1', enabled: false } as any);

    await recordsManagementService.updateReportPresetSchedule('preset-1', { enabled: false });

    expect(mockedApi.put).toHaveBeenCalledWith('/records/report-presets/preset-1/schedule', {
      enabled: false,
      cronExpression: null,
      timezone: null,
      deliveryFolderId: null,
    });
  });

  it('delivers an RM report preset immediately', async () => {
    mockedApi.post.mockResolvedValueOnce({
      id: 'exec-1',
      presetId: 'preset-1',
      triggerType: 'MANUAL',
      status: 'SUCCESS',
      startedAt: '2026-04-21T01:00:00',
      finishedAt: '2026-04-21T01:00:01',
      durationMs: 1000,
    } as any);

    await recordsManagementService.deliverReportPresetNow('preset-1');

    expect(mockedApi.post).toHaveBeenCalledWith('/records/report-presets/preset-1/deliver', {});
  });

  it('rejects malformed RM report preset execution readbacks', async () => {
    mockedApi.post.mockResolvedValueOnce({ id: 'exec-1', status: 'SUCCESS' } as any);

    await expectUnexpectedResponse(recordsManagementService.deliverReportPresetNow('preset-1'));
  });

  it('lists RM report preset executions with an optional limit', async () => {
    mockedApi.get.mockResolvedValueOnce([] as any);

    await recordsManagementService.listReportPresetExecutions('preset-1', 25);

    expect(mockedApi.get).toHaveBeenCalledWith('/records/report-presets/preset-1/executions', {
      params: { limit: 25 },
    });
  });

  it('omits the limit param when not specified', async () => {
    mockedApi.get.mockResolvedValueOnce([] as any);

    await recordsManagementService.listReportPresetExecutions('preset-1');

    expect(mockedApi.get).toHaveBeenCalledWith('/records/report-presets/preset-1/executions', {
      params: undefined,
    });
  });

  it('lists preset delivery ledger rows with optional filters', async () => {
    mockedApi.get.mockResolvedValueOnce({
      content: [
        {
          id: 'exec-1',
          presetId: 'preset-1',
          presetName: 'Weekly Family Report',
          presetKind: 'ACTIVITY_FAMILY_REPORT',
          triggerType: 'MANUAL',
          status: 'SUCCESS',
          startedAt: '2026-04-21T10:00:00',
          finishedAt: '2026-04-21T10:00:01',
          durationMs: 1000,
        },
      ],
      page: 1,
      size: 25,
      totalElements: 30,
      totalPages: 2,
      first: false,
      last: false,
    } as any);

    const result = await recordsManagementService.listReportPresetExecutionLedger({
      presetId: ' preset-1 ',
      status: 'FAILED',
      triggerType: 'SCHEDULED',
      from: ' 2026-04-01T00:00:00 ',
      to: ' 2026-04-21T23:59:59 ',
      page: 1,
      size: 25,
    });

    expect(mockedApi.get).toHaveBeenCalledWith('/records/report-presets/executions', {
      params: {
        presetId: 'preset-1',
        status: 'FAILED',
        triggerType: 'SCHEDULED',
        from: '2026-04-01T00:00:00',
        to: '2026-04-21T23:59:59',
        page: 1,
        size: 25,
      },
    });
    expect(result.number).toBe(1);
    expect(result.size).toBe(25);
    expect(result.totalElements).toBe(30);
    expect(result.content[0].presetName).toBe('Weekly Family Report');
  });

  it('rejects malformed preset delivery ledger pages', async () => {
    mockedApi.get.mockResolvedValueOnce({
      content: 'not an array',
      page: 0,
      size: 10,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
    } as any);

    await expectUnexpectedResponse(recordsManagementService.listReportPresetExecutionLedger());
  });

  it('exports preset delivery ledger CSV with current filters', async () => {
    mockedApi.downloadFile.mockResolvedValueOnce(undefined as any);

    await recordsManagementService.exportReportPresetExecutionLedgerCsv({
      presetId: ' preset-1 ',
      status: 'SUCCESS',
      triggerType: 'MANUAL',
      from: ' 2026-04-09T00:00:00 ',
      to: ' 2026-04-15T23:59:59 ',
      limit: 75,
    });

    expect(mockedApi.downloadFile).toHaveBeenCalledWith(
      '/records/report-presets/executions/export',
      'rm-report-preset-executions-2026-04-09-to-2026-04-15.csv',
      {
        params: {
          presetId: 'preset-1',
          status: 'SUCCESS',
          triggerType: 'MANUAL',
          from: '2026-04-09T00:00:00',
          to: '2026-04-15T23:59:59',
          limit: 75,
        },
      }
    );
  });

  it('identifies which preset kinds support CSV delivery', () => {
    expect(supportsReportPresetCsvDelivery('ACTIVITY_FAMILY_REPORT')).toBe(true);
    expect(supportsReportPresetCsvDelivery('ACTIVITY_CONTRIBUTOR_EVENT_TYPE_REPORT')).toBe(true);
    expect(supportsReportPresetCsvDelivery('ACTIVITY_FAMILY_HIGHLIGHTS')).toBe(true);
    expect(supportsReportPresetCsvDelivery('ACTIVITY_FAMILY_MIX')).toBe(true);
  });

  it('loads operations telemetry with a limit', async () => {
    mockedApi.get.mockResolvedValueOnce({
      governedImportJobCount: 3,
      activeGovernedImportJobCount: 1,
      governedTransferJobCount: 2,
      activeGovernedTransferJobCount: 1,
      importStatusBreakdown: [],
      transferStatusBreakdown: [],
      recentImportJobs: [],
      recentTransferJobs: [],
    } as any);

    await recordsManagementService.getOperationsTelemetry(12);

    expect(mockedApi.get).toHaveBeenCalledWith('/records/operations', {
      params: { limit: 12 },
    });
  });

  it('loads activity timeline with a day range', async () => {
    mockedApi.get.mockResolvedValueOnce({
      days: 14,
      points: [],
    } as any);

    await recordsManagementService.getActivityTimeline(14);

    expect(mockedApi.get).toHaveBeenCalledWith('/records/activity-timeline', {
      params: { days: 14 },
    });
  });

  it('loads activity highlights with a comparison window', async () => {
    mockedApi.get.mockResolvedValueOnce({
      windowDays: 7,
      currentWindow: {
        fromDay: '2026-04-08',
        toDay: '2026-04-14',
        activeDayCount: 5,
        declaredCount: 3,
        undeclaredCount: 1,
        categoryAssignedCount: 4,
        governanceChangeCount: 2,
        totalCount: 10,
      },
      previousWindow: {
        fromDay: '2026-04-01',
        toDay: '2026-04-07',
        activeDayCount: 4,
        declaredCount: 1,
        undeclaredCount: 0,
        categoryAssignedCount: 2,
        governanceChangeCount: 1,
        totalCount: 4,
      },
      busiestDay: {
        day: '2026-04-14',
        totalCount: 4,
      },
    } as any);

    await recordsManagementService.getActivityHighlights(7);

    expect(mockedApi.get).toHaveBeenCalledWith('/records/activity-highlights', {
      params: { windowDays: 7 },
    });
  });

  it('loads activity breakdown with day and bucket ranges', async () => {
    mockedApi.get.mockResolvedValueOnce({
      days: 28,
      bucketDays: 7,
      buckets: [],
    } as any);

    await recordsManagementService.getActivityBreakdown(28, 7);

    expect(mockedApi.get).toHaveBeenCalledWith('/records/activity-breakdown', {
      params: { days: 28, bucketDays: 7 },
    });
  });

  it('loads activity contributors with day and limit ranges', async () => {
    mockedApi.get.mockResolvedValueOnce({
      days: 28,
      limit: 5,
      contributors: [],
    } as any);

    await recordsManagementService.getActivityContributors(28, 5);

    expect(mockedApi.get).toHaveBeenCalledWith('/records/activity-contributors', {
      params: { days: 28, limit: 5 },
    });
  });

  it('exports activity contributor report CSV with trimmed filename range and csv params', async () => {
    mockedApi.downloadFile.mockResolvedValueOnce(undefined as any);

    await recordsManagementService.exportActivityContributorReportCsv({
      from: '2026-04-09T00:00:00',
      to: '2026-04-15T23:59:59',
      limit: 5,
    });

    expect(mockedApi.downloadFile).toHaveBeenCalledWith(
      '/records/activity-contributor-report',
      'rm-activity-contributor-report-2026-04-09-to-2026-04-15.csv',
      {
        params: {
          from: '2026-04-09T00:00:00',
          to: '2026-04-15T23:59:59',
          limit: 5,
          format: 'csv',
        },
      }
    );
  });

  it('loads contributor event-type trend with day, bucket, limit, and event-type ranges', async () => {
    mockedApi.get.mockResolvedValueOnce({
      days: 28,
      bucketDays: 7,
      limit: 5,
      eventTypeLimit: 3,
      trackedContributors: [],
      buckets: [],
    } as any);

    await recordsManagementService.getActivityContributorEventTypeTrend(28, 7, 5, 3);

    expect(mockedApi.get).toHaveBeenCalledWith('/records/activity-contributor-event-type-trend', {
      params: { days: 28, bucketDays: 7, limit: 5, eventTypeLimit: 3 },
    });
  });

  it('loads contributor family trend with day, bucket, and limit ranges', async () => {
    mockedApi.get.mockResolvedValueOnce({
      days: 28,
      bucketDays: 7,
      limit: 5,
      trackedContributors: [],
      buckets: [],
    } as any);

    await recordsManagementService.getActivityContributorFamilyTrend(28, 7, 5);

    expect(mockedApi.get).toHaveBeenCalledWith('/records/activity-contributor-family-trend', {
      params: { days: 28, bucketDays: 7, limit: 5 },
    });
  });

  it('loads contributor family highlights with window and limit ranges', async () => {
    mockedApi.get.mockResolvedValueOnce({
      windowDays: 7,
      limit: 5,
      currentWindow: { fromDay: '2026-04-09', toDay: '2026-04-15' },
      previousWindow: { fromDay: '2026-04-02', toDay: '2026-04-08' },
      contributors: [],
    } as any);

    await recordsManagementService.getActivityContributorFamilyHighlights(7, 5);

    expect(mockedApi.get).toHaveBeenCalledWith('/records/activity-contributor-family-highlights', {
      params: { windowDays: 7, limit: 5 },
    });
  });

  it('exports contributor family report CSV with trimmed filename range and csv params', async () => {
    mockedApi.downloadFile.mockResolvedValueOnce(undefined as any);

    await recordsManagementService.exportActivityContributorFamilyReportCsv({
      from: '2026-04-09T00:00:00',
      to: '2026-04-15T23:59:59',
      limit: 5,
    });

    expect(mockedApi.downloadFile).toHaveBeenCalledWith(
      '/records/activity-contributor-family-report',
      'rm-activity-contributor-family-report-2026-04-09-to-2026-04-15.csv',
      {
        params: {
          from: '2026-04-09T00:00:00',
          to: '2026-04-15T23:59:59',
          limit: 5,
          format: 'csv',
        },
      }
    );
  });

  it('exports activity family report CSV with trimmed filename range and csv params', async () => {
    mockedApi.downloadFile.mockResolvedValueOnce(undefined as any);

    await recordsManagementService.exportActivityFamilyReportCsv({
      from: '2026-04-09T00:00:00',
      to: '2026-04-15T23:59:59',
    });

    expect(mockedApi.downloadFile).toHaveBeenCalledWith(
      '/records/activity-family-report',
      'rm-activity-family-report-2026-04-09-to-2026-04-15.csv',
      {
        params: {
          from: '2026-04-09T00:00:00',
          to: '2026-04-15T23:59:59',
          format: 'csv',
        },
      }
    );
  });

  it('loads contributor event-type highlights with window, limit, and event-type ranges', async () => {
    mockedApi.get.mockResolvedValueOnce({
      windowDays: 7,
      limit: 5,
      eventTypeLimit: 3,
      currentWindow: { fromDay: '2026-04-09', toDay: '2026-04-15' },
      previousWindow: { fromDay: '2026-04-02', toDay: '2026-04-08' },
      contributors: [],
    } as any);

    await recordsManagementService.getActivityContributorEventTypeHighlights(7, 5, 3);

    expect(mockedApi.get).toHaveBeenCalledWith('/records/activity-contributor-event-type-highlights', {
      params: { windowDays: 7, limit: 5, eventTypeLimit: 3 },
    });
  });

  it('exports contributor event-type report CSV with trimmed filename range and csv params', async () => {
    mockedApi.downloadFile.mockResolvedValueOnce(undefined as any);

    await recordsManagementService.exportActivityContributorEventTypeReportCsv({
      from: '2026-04-09T00:00:00',
      to: '2026-04-15T23:59:59',
      limit: 5,
      eventTypeLimit: 3,
    });

    expect(mockedApi.downloadFile).toHaveBeenCalledWith(
      '/records/activity-contributor-event-type-report',
      'rm-activity-contributor-event-type-report-2026-04-09-to-2026-04-15.csv',
      {
        params: {
          from: '2026-04-09T00:00:00',
          to: '2026-04-15T23:59:59',
          limit: 5,
          eventTypeLimit: 3,
          format: 'csv',
        },
      }
    );
  });

  it('loads activity event hotspots with day and limit ranges', async () => {
    mockedApi.get.mockResolvedValueOnce({
      days: 28,
      limit: 8,
      eventTypes: [],
    } as any);

    await recordsManagementService.getActivityEventTypes(28, 8);

    expect(mockedApi.get).toHaveBeenCalledWith('/records/activity-event-types', {
      params: { days: 28, limit: 8 },
    });
  });

  it('exports activity event-type report CSV with trimmed filename range and csv params', async () => {
    mockedApi.downloadFile.mockResolvedValueOnce(undefined as any);

    await recordsManagementService.exportActivityEventTypeReportCsv({
      from: '2026-04-09T00:00:00',
      to: '2026-04-15T23:59:59',
      limit: 8,
    });

    expect(mockedApi.downloadFile).toHaveBeenCalledWith(
      '/records/activity-event-type-report',
      'rm-activity-event-type-report-2026-04-09-to-2026-04-15.csv',
      {
        params: {
          from: '2026-04-09T00:00:00',
          to: '2026-04-15T23:59:59',
          limit: 8,
          format: 'csv',
        },
      }
    );
  });

  it('loads activity family mix with a day range', async () => {
    mockedApi.get.mockResolvedValueOnce({
      days: 28,
      totalCount: 0,
      families: [],
    } as any);

    await recordsManagementService.getActivityFamilies(28);

    expect(mockedApi.get).toHaveBeenCalledWith('/records/activity-families', {
      params: { days: 28 },
    });
  });

  it('loads activity family highlights with a comparison window', async () => {
    mockedApi.get.mockResolvedValueOnce({
      windowDays: 7,
      currentWindow: { fromDay: '2026-04-09', toDay: '2026-04-15' },
      previousWindow: { fromDay: '2026-04-02', toDay: '2026-04-08' },
      families: [],
    } as any);

    await recordsManagementService.getActivityFamilyHighlights(7);

    expect(mockedApi.get).toHaveBeenCalledWith('/records/activity-family-highlights', {
      params: { windowDays: 7 },
    });
  });

  it('lists audit with trimmed filters and pagination', async () => {
    mockedApi.get.mockResolvedValueOnce({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 25 } as any);

    await recordsManagementService.listAudit({
      family: '  DECLARED ',
      eventType: '  rm_record_declared ',
      username: '  admin ',
      from: '2026-04-14T10:00',
      to: '2026-04-14T23:59:59',
      page: 2,
      size: 25,
    });

    expect(mockedApi.get).toHaveBeenCalledWith('/records/audit', {
      params: {
        family: 'DECLARED',
        eventType: 'rm_record_declared',
        username: 'admin',
        from: '2026-04-14T10:00',
        to: '2026-04-14T23:59:59',
        page: 2,
        size: 25,
      },
    });
  });

  it('creates a file plan with trimmed fields', async () => {
    mockedApi.post.mockResolvedValueOnce({ folderId: 'plan-1', name: 'File Plan', path: '/File Plan' } as any);

    await recordsManagementService.createFilePlan({
      name: '  File Plan ',
      description: '  RM workspace ',
      parentId: 'parent-1',
    });

    expect(mockedApi.post).toHaveBeenCalledWith('/records/file-plans', {
      name: 'File Plan',
      description: 'RM workspace',
      parentId: 'parent-1',
    });
  });

  it('creates a record category with trimmed fields', async () => {
    mockedApi.post.mockResolvedValueOnce({ categoryId: 'cat-1', name: 'HR', path: '/Records Management/HR' } as any);

    await recordsManagementService.createRecordCategory({
      name: '  HR ',
      description: '  Employee records ',
      parentId: 'root-1',
    });

    expect(mockedApi.post).toHaveBeenCalledWith('/records/categories', {
      name: 'HR',
      description: 'Employee records',
      parentId: 'root-1',
    });
  });

  it('updates a file plan description', async () => {
    mockedApi.put.mockResolvedValueOnce({ folderId: 'plan-1', description: 'Updated' } as any);

    await recordsManagementService.updateFilePlan('plan-1', {
      description: '  Updated  ',
    });

    expect(mockedApi.put).toHaveBeenCalledWith('/records/file-plans/plan-1', {
      description: 'Updated',
    });
  });

  it('renames a file plan with a trimmed name', async () => {
    mockedApi.put.mockResolvedValueOnce({ folderId: 'plan-1', name: 'People File Plan' } as any);

    await recordsManagementService.renameFilePlan('plan-1', {
      name: '  People File Plan  ',
    });

    expect(mockedApi.put).toHaveBeenCalledWith('/records/file-plans/plan-1/rename', {
      name: 'People File Plan',
    });
  });

  it('moves a file plan', async () => {
    mockedApi.put.mockResolvedValueOnce({ folderId: 'plan-3', parentId: 'plan-2' } as any);

    await recordsManagementService.moveFilePlan('plan-3', {
      targetParentId: 'plan-2',
    });

    expect(mockedApi.put).toHaveBeenCalledWith('/records/file-plans/plan-3/move', {
      targetParentId: 'plan-2',
    });
  });

  it('deletes a file plan', async () => {
    mockedApi.delete.mockResolvedValueOnce(undefined as any);

    await recordsManagementService.deleteFilePlan('plan-1');

    expect(mockedApi.delete).toHaveBeenCalledWith('/records/file-plans/plan-1');
  });

  it('updates a record category description', async () => {
    mockedApi.put.mockResolvedValueOnce({ categoryId: 'cat-1', description: 'Updated' } as any);

    await recordsManagementService.updateRecordCategory('cat-1', {
      description: '  Updated  ',
    });

    expect(mockedApi.put).toHaveBeenCalledWith('/records/categories/cat-1', {
      description: 'Updated',
    });
  });

  it('renames a record category with a trimmed name', async () => {
    mockedApi.put.mockResolvedValueOnce({ categoryId: 'cat-1', name: 'Agreements' } as any);

    await recordsManagementService.renameRecordCategory('cat-1', {
      name: '  Agreements  ',
    });

    expect(mockedApi.put).toHaveBeenCalledWith('/records/categories/cat-1/rename', {
      name: 'Agreements',
    });
  });

  it('moves a record category', async () => {
    mockedApi.put.mockResolvedValueOnce({ categoryId: 'cat-1', parentId: 'cat-2' } as any);

    await recordsManagementService.moveRecordCategory('cat-1', {
      targetParentId: 'cat-2',
    });

    expect(mockedApi.put).toHaveBeenCalledWith('/records/categories/cat-1/move', {
      targetParentId: 'cat-2',
    });
  });

  it('deletes a record category', async () => {
    mockedApi.delete.mockResolvedValueOnce(undefined as any);

    await recordsManagementService.deleteRecordCategory('cat-1');

    expect(mockedApi.delete).toHaveBeenCalledWith('/records/categories/cat-1');
  });

  it('assigns a record category', async () => {
    mockedApi.put.mockResolvedValueOnce({ nodeId: 'node-1', recordCategoryId: 'cat-1' } as any);

    await recordsManagementService.assignRecordCategory('node-1', 'cat-1');

    expect(mockedApi.put).toHaveBeenCalledWith('/nodes/node-1/record/category', { categoryId: 'cat-1' });
  });

  describe('createBulkDeclarations', () => {
    const declaredRow = (nodeId: string) => ({
      nodeId,
      status: 'DECLARED' as const,
      declaration: {
        nodeId,
        name: `${nodeId}.pdf`,
        path: `/Sites/Finance/${nodeId}.pdf`,
      },
      errorCategory: null,
      errorMessage: null,
    });
    const skippedRow = (nodeId: string) => ({
      nodeId,
      status: 'SKIPPED_ALREADY_DECLARED' as const,
      declaration: {
        nodeId,
        name: `${nodeId}.pdf`,
        path: `/Sites/Finance/${nodeId}.pdf`,
      },
      errorCategory: null,
      errorMessage: null,
    });
    const failedRow = (nodeId: string, category: string) => ({
      nodeId,
      status: 'FAILED' as const,
      declaration: null,
      errorCategory: category,
      errorMessage: 'The target node was not found.',
    });

    it('sends nodeIds + optional categoryId + trimmed comment and parses a mixed-row response', async () => {
      mockedApi.post.mockResolvedValueOnce({
        bulkDeclareResults: {
          rows: [declaredRow('node-1'), skippedRow('node-2'), failedRow('node-3', 'NODE_NOT_FOUND')],
        },
      } as any);

      const response = await recordsManagementService.createBulkDeclarations({
        nodeIds: ['node-1', 'node-2', 'node-3'],
        categoryId: 'cat-1',
        comment: '  batch declaration  ',
      });

      expect(mockedApi.post).toHaveBeenCalledWith('/nodes/bulk-declare', {
        nodeIds: ['node-1', 'node-2', 'node-3'],
        categoryId: 'cat-1',
        comment: 'batch declaration',
      });
      expect(response.bulkDeclareResults.rows).toHaveLength(3);
      expect(response.bulkDeclareResults.rows[0].status).toBe('DECLARED');
      expect(response.bulkDeclareResults.rows[1].status).toBe('SKIPPED_ALREADY_DECLARED');
      expect(response.bulkDeclareResults.rows[2].status).toBe('FAILED');
    });

    it('omits empty categoryId and blank comment from the payload', async () => {
      mockedApi.post.mockResolvedValueOnce({
        bulkDeclareResults: { rows: [declaredRow('node-1')] },
      } as any);

      await recordsManagementService.createBulkDeclarations({
        nodeIds: ['node-1'],
        categoryId: null,
        comment: '   ',
      });

      const payload = mockedApi.post.mock.calls[0][1];
      expect(payload).toEqual({ nodeIds: ['node-1'] });
      expect(payload).not.toHaveProperty('categoryId');
      expect(payload).not.toHaveProperty('comment');
    });

    it('throws the dedicated sentinel when the response body is the SPA HTML fallback (Phase 5 Mocked drift on the new route)', async () => {
      mockedApi.post.mockResolvedValueOnce('<!doctype html><html>mocked SPA fallback</html>' as any);

      await expect(
        recordsManagementService.createBulkDeclarations({ nodeIds: ['node-1'] })
      ).rejects.toThrow(RECORDS_MANAGEMENT_BULK_DECLARE_UNEXPECTED_RESPONSE_MESSAGE);
      // Sentinel must be distinct from the generic RM sentinel so Phase 5 Mocked drift on
      // the new route is independently traceable.
      expect(RECORDS_MANAGEMENT_BULK_DECLARE_UNEXPECTED_RESPONSE_MESSAGE)
        .not.toBe(RECORDS_MANAGEMENT_UNEXPECTED_RESPONSE_MESSAGE);
    });

    it('rejects rows where status is DECLARED but errorMessage is populated', async () => {
      mockedApi.post.mockResolvedValueOnce({
        bulkDeclareResults: {
          rows: [{
            ...declaredRow('node-1'),
            errorMessage: 'unexpected error message on DECLARED row',
          }],
        },
      } as any);

      await expect(
        recordsManagementService.createBulkDeclarations({ nodeIds: ['node-1'] })
      ).rejects.toThrow(RECORDS_MANAGEMENT_BULK_DECLARE_UNEXPECTED_RESPONSE_MESSAGE);
    });

    it('rejects rows where status is SKIPPED_ALREADY_DECLARED but errorCategory is populated', async () => {
      mockedApi.post.mockResolvedValueOnce({
        bulkDeclareResults: {
          rows: [{
            ...skippedRow('node-1'),
            errorCategory: 'NODE_NOT_FOUND',
          }],
        },
      } as any);

      await expect(
        recordsManagementService.createBulkDeclarations({ nodeIds: ['node-1'] })
      ).rejects.toThrow(RECORDS_MANAGEMENT_BULK_DECLARE_UNEXPECTED_RESPONSE_MESSAGE);
    });

    it("rejects rows where status is FAILED but errorCategory is 'ALREADY_DECLARED' (Finding 3 — not a valid error category)", async () => {
      mockedApi.post.mockResolvedValueOnce({
        bulkDeclareResults: {
          rows: [{
            nodeId: 'node-1',
            status: 'FAILED',
            declaration: null,
            errorCategory: 'ALREADY_DECLARED',
            errorMessage: 'already a record',
          }],
        },
      } as any);

      await expect(
        recordsManagementService.createBulkDeclarations({ nodeIds: ['node-1'] })
      ).rejects.toThrow(RECORDS_MANAGEMENT_BULK_DECLARE_UNEXPECTED_RESPONSE_MESSAGE);
    });

    it('rejects rows with an unknown errorCategory variant', async () => {
      mockedApi.post.mockResolvedValueOnce({
        bulkDeclareResults: {
          rows: [{
            nodeId: 'node-1',
            status: 'FAILED',
            declaration: null,
            errorCategory: 'PERMISSION_DENIED',
            errorMessage: 'some message',
          }],
        },
      } as any);

      await expect(
        recordsManagementService.createBulkDeclarations({ nodeIds: ['node-1'] })
      ).rejects.toThrow(RECORDS_MANAGEMENT_BULK_DECLARE_UNEXPECTED_RESPONSE_MESSAGE);
    });

    it('rejects rows with a FAILED status and empty errorMessage', async () => {
      mockedApi.post.mockResolvedValueOnce({
        bulkDeclareResults: {
          rows: [{
            nodeId: 'node-1',
            status: 'FAILED',
            declaration: null,
            errorCategory: 'INTERNAL_ERROR',
            errorMessage: '',
          }],
        },
      } as any);

      await expect(
        recordsManagementService.createBulkDeclarations({ nodeIds: ['node-1'] })
      ).rejects.toThrow(RECORDS_MANAGEMENT_BULK_DECLARE_UNEXPECTED_RESPONSE_MESSAGE);
    });

    it('rejects responses where the top-level bulkDeclareResults wrapper is missing', async () => {
      mockedApi.post.mockResolvedValueOnce({ rows: [declaredRow('node-1')] } as any);

      await expect(
        recordsManagementService.createBulkDeclarations({ nodeIds: ['node-1'] })
      ).rejects.toThrow(RECORDS_MANAGEMENT_BULK_DECLARE_UNEXPECTED_RESPONSE_MESSAGE);
    });

    it('accepts rows where DECLARED carries explicit null errorCategory and null errorMessage', async () => {
      mockedApi.post.mockResolvedValueOnce({
        bulkDeclareResults: { rows: [declaredRow('node-1')] },
      } as any);

      const response = await recordsManagementService.createBulkDeclarations({
        nodeIds: ['node-1'],
      });

      expect(response.bulkDeclareResults.rows[0].errorCategory).toBeNull();
      expect(response.bulkDeclareResults.rows[0].errorMessage).toBeNull();
    });
  });
});
