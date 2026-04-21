/* eslint-disable testing-library/no-node-access, testing-library/no-wait-for-multiple-assertions */
import React from 'react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { toast } from 'react-toastify';
import RecordsManagementPage from './RecordsManagementPage';
import recordsManagementService from 'services/recordsManagementService';

jest.mock('react-toastify', () => ({
  toast: {
    error: jest.fn(),
    success: jest.fn(),
  },
}));

jest.mock('components/browser/FolderTree', () => ({
  __esModule: true,
  default: ({ onNodeSelect, selectedNodeId }: any) => (
    <div data-testid="folder-tree-mock">
      <span data-testid="folder-tree-selected">{selectedNodeId || ''}</span>
      <button
        type="button"
        onClick={() =>
          onNodeSelect?.({ id: 'folder-1', name: 'Compliance Reports', nodeType: 'FOLDER' })
        }
      >
        Pick folder-1
      </button>
    </div>
  ),
}));

jest.mock('services/recordsManagementService', () => ({
  __esModule: true,
  supportsReportPresetCsvDelivery: jest.requireActual(
    '../services/recordsManagementService'
  ).supportsReportPresetCsvDelivery,
  default: {
    getSummary: jest.fn(),
    getOperationsTelemetry: jest.fn(),
    getActivityBreakdown: jest.fn(),
    listReportPresets: jest.fn(),
    createReportPreset: jest.fn(),
    updateReportPreset: jest.fn(),
    deleteReportPreset: jest.fn(),
    getReportPresetSchedule: jest.fn(),
    updateReportPresetSchedule: jest.fn(),
    deliverReportPresetNow: jest.fn(),
    listReportPresetExecutions: jest.fn(),
    listReportPresetExecutionLedger: jest.fn(),
    exportReportPresetExecutionLedgerCsv: jest.fn(),
    getActivityContributorEventTypeHighlights: jest.fn(),
    getActivityContributorFamilyHighlights: jest.fn(),
    exportActivityFamilyReportCsv: jest.fn(),
    exportActivityEventTypeReportCsv: jest.fn(),
    exportActivityContributorFamilyReportCsv: jest.fn(),
    exportActivityContributorEventTypeReportCsv: jest.fn(),
    getActivityContributorFamilyTrend: jest.fn(),
    getActivityContributors: jest.fn(),
    exportActivityContributorReportCsv: jest.fn(),
    getActivityContributorEventTypeTrend: jest.fn(),
    getActivityEventTypes: jest.fn(),
    getActivityFamilies: jest.fn(),
    getActivityFamilyHighlights: jest.fn(),
    getActivityHighlights: jest.fn(),
    getActivityTimeline: jest.fn(),
    listRecords: jest.fn(),
    listFilePlans: jest.fn(),
    listRecordCategories: jest.fn(),
    listAudit: jest.fn(),
    createFilePlan: jest.fn(),
    updateFilePlan: jest.fn(),
    renameFilePlan: jest.fn(),
    moveFilePlan: jest.fn(),
    deleteFilePlan: jest.fn(),
    createRecordCategory: jest.fn(),
    updateRecordCategory: jest.fn(),
    renameRecordCategory: jest.fn(),
    moveRecordCategory: jest.fn(),
    deleteRecordCategory: jest.fn(),
    assignRecordCategory: jest.fn(),
    undeclareRecord: jest.fn(),
  },
}));

const mockedRecordsManagementService = recordsManagementService as jest.Mocked<typeof recordsManagementService>;
const toastSuccessMock = toast.success as jest.Mock;

jest.setTimeout(45000);

const renderPage = () => {
  const store = configureStore({
    reducer: {
      auth: () => ({
        user: {
          roles: ['ROLE_ADMIN'],
        },
      }),
    },
  });

  return render(
    <Provider store={store}>
      <RecordsManagementPage />
    </Provider>
  );
};

describe('RecordsManagementPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();

    mockedRecordsManagementService.getSummary.mockResolvedValue({
      declaredRecordCount: 2,
      filePlanCount: 3,
      recordCategoryCount: 2,
      uncategorizedRecordCount: 1,
      outsideFilePlanRecordCount: 1,
      categoryBreakdown: [{ key: '(Uncategorized)', count: 1 }],
      filePlanBreakdown: [{ key: '(Outside File Plan)', count: 1 }],
    } as any);
    mockedRecordsManagementService.getOperationsTelemetry.mockResolvedValue({
      governedImportJobCount: 3,
      activeGovernedImportJobCount: 1,
      failedGovernedImportJobCount: 1,
      governedTransferJobCount: 2,
      activeGovernedTransferJobCount: 1,
      failedGovernedTransferJobCount: 1,
      importStatusBreakdown: [
        { key: 'RUNNING', count: 1 },
        { key: 'FAILED', count: 1 },
      ],
      transferStatusBreakdown: [
        { key: 'RUNNING / CONNECTED', count: 1 },
        { key: 'FAILED / DISCONNECTED', count: 1 },
      ],
      importGovernanceReasonBreakdown: [
        { key: 'TARGET_FILE_PLAN', count: 1 },
        { key: 'TARGET_OUTSIDE_FILE_PLAN', count: 1 },
      ],
      transferGovernanceReasonBreakdown: [
        { key: 'SOURCE_INSIDE_FILE_PLAN', count: 1 },
        { key: 'TARGET_OUTSIDE_FILE_PLAN', count: 1 },
      ],
      recentImportJobs: [
        {
          jobId: 'import-1',
          targetFolderPath: '/Company Home/HR File Plan',
          status: 'RUNNING',
          conflictPolicy: 'SKIP',
          totalFiles: 12,
          importedFiles: 8,
          skippedFiles: 2,
          failedFiles: 1,
          governanceReasons: ['TARGET_FILE_PLAN'],
          createdAt: '2026-04-14T09:30:00',
        },
        {
          jobId: 'import-2',
          targetFolderPath: '/Sites/hr',
          status: 'FAILED',
          conflictPolicy: 'OVERWRITE',
          totalFiles: 3,
          importedFiles: 1,
          skippedFiles: 0,
          failedFiles: 2,
          governanceReasons: ['TARGET_OUTSIDE_FILE_PLAN'],
          createdAt: '2026-04-14T10:30:00',
        },
      ],
      recentTransferJobs: [
        {
          jobId: 'transfer-1',
          sourceNodePath: '/Company Home/HR File Plan/Contracts',
          targetFolderPath: '/Archive/HR',
          status: 'RUNNING',
          transportStatus: 'CONNECTED',
          governanceReasons: ['SOURCE_INSIDE_FILE_PLAN'],
          createdAt: '2026-04-14T09:45:00',
        },
        {
          jobId: 'transfer-2',
          sourceNodePath: '/Sites/hr/Transfer Candidate',
          targetFolderPath: '/Archive/Shared',
          status: 'FAILED',
          transportStatus: 'DISCONNECTED',
          governanceReasons: ['TARGET_OUTSIDE_FILE_PLAN'],
          createdAt: '2026-04-14T10:45:00',
        },
      ],
    } as any);
    mockedRecordsManagementService.getActivityBreakdown.mockResolvedValue({
      days: 28,
      bucketDays: 7,
      buckets: [
        {
          label: '2026-03-18 to 2026-03-24',
          fromDay: '2026-03-18',
          toDay: '2026-03-24',
          activeDayCount: 3,
          declaredCount: 2,
          undeclaredCount: 0,
          categoryAssignedCount: 1,
          governanceChangeCount: 1,
          totalCount: 4,
        },
        {
          label: '2026-04-08 to 2026-04-14',
          fromDay: '2026-04-08',
          toDay: '2026-04-14',
          activeDayCount: 5,
          declaredCount: 3,
          undeclaredCount: 1,
          categoryAssignedCount: 4,
          governanceChangeCount: 2,
          totalCount: 10,
        },
      ],
    } as any);
    mockedRecordsManagementService.getActivityContributorEventTypeHighlights.mockResolvedValue({
      windowDays: 7,
      limit: 5,
      eventTypeLimit: 3,
      currentWindow: {
        fromDay: '2026-04-09',
        toDay: '2026-04-15',
      },
      previousWindow: {
        fromDay: '2026-04-02',
        toDay: '2026-04-08',
      },
      contributors: [
        {
          username: 'admin',
          label: 'admin',
          currentCount: 5,
          previousCount: 2,
          delta: 3,
          lastEventTime: '2026-04-15T10:30:00',
          eventTypes: [
            {
              eventType: 'RM_RECORD_DECLARED',
              family: 'DECLARED',
              currentCount: 3,
              previousCount: 1,
              delta: 2,
              lastEventTime: '2026-04-15T10:30:00',
            },
            {
              eventType: 'RM_RECORD_UNDECLARED',
              family: 'UNDECLARED',
              currentCount: 2,
              previousCount: 1,
              delta: 1,
              lastEventTime: '2026-04-14T10:30:00',
            },
          ],
        },
      ],
    } as any);
    mockedRecordsManagementService.getActivityContributorFamilyHighlights.mockResolvedValue({
      windowDays: 7,
      limit: 5,
      currentWindow: {
        fromDay: '2026-04-09',
        toDay: '2026-04-15',
      },
      previousWindow: {
        fromDay: '2026-04-02',
        toDay: '2026-04-08',
      },
      contributors: [
        {
          username: 'admin',
          label: 'admin',
          currentCount: 5,
          previousCount: 2,
          delta: 3,
          lastEventTime: '2026-04-15T10:30:00',
          families: [
            {
              family: 'DECLARED',
              currentCount: 3,
              previousCount: 1,
              delta: 2,
              lastEventTime: '2026-04-15T10:30:00',
            },
            {
              family: 'UNDECLARED',
              currentCount: 2,
              previousCount: 1,
              delta: 1,
              lastEventTime: '2026-04-14T10:30:00',
            },
          ],
        },
      ],
    } as any);
    mockedRecordsManagementService.exportActivityFamilyReportCsv.mockResolvedValue(undefined as any);
    mockedRecordsManagementService.exportActivityEventTypeReportCsv.mockResolvedValue(undefined as any);
    mockedRecordsManagementService.exportActivityContributorFamilyReportCsv.mockResolvedValue(undefined as any);
    mockedRecordsManagementService.exportActivityContributorEventTypeReportCsv.mockResolvedValue(undefined as any);
    mockedRecordsManagementService.getActivityContributors.mockResolvedValue({
      days: 28,
      limit: 5,
      contributors: [
        {
          username: 'admin',
          label: 'admin',
          declaredCount: 3,
          undeclaredCount: 1,
          categoryAssignedCount: 2,
          governanceChangeCount: 1,
          totalCount: 7,
          lastEventTime: '2026-04-14T10:30:00',
        },
        {
          username: null,
          label: '(System)',
          declaredCount: 0,
          undeclaredCount: 0,
          categoryAssignedCount: 0,
          governanceChangeCount: 2,
          totalCount: 2,
          lastEventTime: '2026-04-14T11:00:00',
        },
      ],
    } as any);
    mockedRecordsManagementService.exportActivityContributorReportCsv.mockResolvedValue(undefined as any);
    mockedRecordsManagementService.getActivityContributorFamilyTrend.mockResolvedValue({
      days: 28,
      bucketDays: 7,
      limit: 5,
      trackedContributors: [
        {
          username: 'admin',
          label: 'admin',
          count: 7,
          lastEventTime: '2026-04-14T10:30:00',
        },
      ],
      buckets: [
        {
          label: '2026-04-08 to 2026-04-14',
          fromDay: '2026-04-08',
          toDay: '2026-04-14',
          activeDayCount: 5,
          totalCount: 10,
          otherCount: 2,
          contributorCounts: [
            {
              username: 'admin',
              label: 'admin',
              count: 5,
              families: [
                { family: 'DECLARED', count: 3 },
                { family: 'UNDECLARED', count: 2 },
              ],
            },
          ],
        },
      ],
    } as any);
    mockedRecordsManagementService.getActivityContributorEventTypeTrend.mockResolvedValue({
      days: 28,
      bucketDays: 7,
      limit: 5,
      eventTypeLimit: 3,
      trackedContributors: [
        {
          username: 'admin',
          label: 'admin',
          count: 7,
          lastEventTime: '2026-04-14T10:30:00',
        },
      ],
      buckets: [
        {
          label: '2026-04-08 to 2026-04-14',
          fromDay: '2026-04-08',
          toDay: '2026-04-14',
          activeDayCount: 5,
          totalCount: 10,
          otherCount: 2,
          contributorCounts: [
            {
              username: 'admin',
              label: 'admin',
              count: 5,
              eventTypes: [
                { eventType: 'RM_RECORD_DECLARED', family: 'DECLARED', count: 3 },
                { eventType: 'RM_RECORD_UNDECLARED', family: 'UNDECLARED', count: 2 },
              ],
            },
          ],
        },
      ],
    } as any);
    mockedRecordsManagementService.getActivityEventTypes.mockResolvedValue({
      days: 28,
      limit: 8,
      eventTypes: [
        {
          eventType: 'RM_RECORD_DECLARED',
          family: 'DECLARED',
          count: 5,
          lastEventTime: '2026-04-14T10:30:00',
        },
        {
          eventType: 'RM_RECORD_UNDECLARE_BLOCKED',
          family: 'OTHER',
          count: 2,
          lastEventTime: '2026-04-14T11:00:00',
        },
      ],
    } as any);
    mockedRecordsManagementService.getActivityFamilies.mockResolvedValue({
      days: 28,
      totalCount: 10,
      families: [
        {
          family: 'DECLARED',
          count: 5,
          lastEventTime: '2026-04-14T10:30:00',
        },
        {
          family: 'OTHER',
          count: 2,
          lastEventTime: '2026-04-14T11:00:00',
        },
      ],
    } as any);
    mockedRecordsManagementService.getActivityFamilyHighlights.mockResolvedValue({
      windowDays: 7,
      currentWindow: {
        fromDay: '2026-04-09',
        toDay: '2026-04-15',
      },
      previousWindow: {
        fromDay: '2026-04-02',
        toDay: '2026-04-08',
      },
      families: [
        {
          family: 'DECLARED',
          currentCount: 5,
          previousCount: 2,
          delta: 3,
          lastEventTime: '2026-04-15T10:30:00',
        },
        {
          family: 'OTHER',
          currentCount: 1,
          previousCount: 3,
          delta: -2,
          lastEventTime: '2026-04-14T11:00:00',
        },
      ],
    } as any);
    mockedRecordsManagementService.getActivityHighlights.mockResolvedValue({
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
    mockedRecordsManagementService.getActivityTimeline.mockResolvedValue({
      days: 14,
      points: [
        {
          day: '2026-04-12',
          declaredCount: 0,
          undeclaredCount: 0,
          categoryAssignedCount: 1,
          governanceChangeCount: 0,
          totalCount: 1,
        },
        {
          day: '2026-04-13',
          declaredCount: 1,
          undeclaredCount: 0,
          categoryAssignedCount: 1,
          governanceChangeCount: 1,
          totalCount: 3,
        },
        {
          day: '2026-04-14',
          declaredCount: 1,
          undeclaredCount: 1,
          categoryAssignedCount: 0,
          governanceChangeCount: 2,
          totalCount: 4,
        },
      ],
    } as any);
    mockedRecordsManagementService.listRecords.mockResolvedValue([
      {
        nodeId: 'record-1',
        name: 'Employee Contract',
        path: '/Sites/hr/Employee Contract.pdf',
        declaredAt: '2026-04-14T10:00:00',
      },
      {
        nodeId: 'record-2',
        name: 'Finance Policy',
        path: '/Company Home/Finance File Plan/Finance Policy.pdf',
        declaredAt: '2026-04-14T11:00:00',
        recordCategoryId: 'cat-2',
        recordCategoryName: 'Finance',
        recordCategoryPath: '/Records Management/Finance',
      },
    ] as any);
    mockedRecordsManagementService.listFilePlans.mockResolvedValue([
      {
        folderId: 'plan-1',
        name: 'HR File Plan',
        path: '/Company Home/HR File Plan',
        description: 'HR governance',
      },
      {
        folderId: 'plan-2',
        name: 'Finance File Plan',
        path: '/Company Home/Finance File Plan',
        description: 'Finance governance',
      },
      {
        folderId: 'plan-3',
        name: 'Contracts File Plan',
        path: '/Company Home/HR File Plan/Contracts File Plan',
        description: 'Contracts governance',
        parentId: 'plan-1',
      },
    ] as any);
    mockedRecordsManagementService.listRecordCategories.mockResolvedValue([
      {
        categoryId: 'root-1',
        name: 'Records Management',
        path: '/Records Management',
        level: 1,
      },
      {
        categoryId: 'cat-1',
        name: 'Contracts',
        path: '/Records Management/Contracts',
        level: 2,
        parentId: 'root-1',
      },
      {
        categoryId: 'cat-2',
        name: 'Finance',
        path: '/Records Management/Finance',
        level: 2,
        parentId: 'root-1',
      },
    ] as any);
    mockedRecordsManagementService.listAudit.mockResolvedValue({
      content: [
        {
          auditLogId: 'audit-1',
          eventType: 'RM_RECORD_DECLARED',
          nodeName: 'Employee Contract',
          username: 'admin',
          eventTime: '2026-04-14T10:00:00',
          details: 'Declared document as record',
        },
      ],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 10,
    } as any);
    mockedRecordsManagementService.createFilePlan.mockResolvedValue({
      folderId: 'plan-4',
      name: 'People File Plan',
      path: '/Company Home/People File Plan',
    } as any);
    mockedRecordsManagementService.createRecordCategory.mockResolvedValue({
      categoryId: 'cat-2',
      name: 'Finance',
      path: '/Records Management/Finance',
      level: 2,
    } as any);
    mockedRecordsManagementService.updateFilePlan.mockResolvedValue({
      folderId: 'plan-1',
      name: 'HR File Plan',
      path: '/Company Home/HR File Plan',
      description: 'Updated HR governance',
    } as any);
    mockedRecordsManagementService.renameFilePlan.mockResolvedValue({
      folderId: 'plan-1',
      name: 'People File Plan',
      path: '/Company Home/People File Plan',
      description: 'HR governance',
    } as any);
    mockedRecordsManagementService.moveFilePlan.mockResolvedValue({
      folderId: 'plan-3',
      name: 'Contracts File Plan',
      path: '/Company Home/Finance File Plan/Contracts File Plan',
      description: 'Contracts governance',
      parentId: 'plan-2',
    } as any);
    mockedRecordsManagementService.deleteFilePlan.mockResolvedValue(undefined as any);
    mockedRecordsManagementService.updateRecordCategory.mockResolvedValue({
      categoryId: 'cat-1',
      name: 'Contracts',
      path: '/Records Management/Contracts',
      description: 'Updated contracts',
      level: 2,
    } as any);
    mockedRecordsManagementService.renameRecordCategory.mockResolvedValue({
      categoryId: 'cat-1',
      name: 'Agreements',
      path: '/Records Management/Agreements',
      description: 'Updated contracts',
      level: 2,
      parentId: 'root-1',
    } as any);
    mockedRecordsManagementService.moveRecordCategory.mockResolvedValue({
      categoryId: 'cat-1',
      name: 'Contracts',
      path: '/Records Management/Finance/Contracts',
      description: 'Updated contracts',
      level: 3,
      parentId: 'cat-2',
    } as any);
    mockedRecordsManagementService.deleteRecordCategory.mockResolvedValue(undefined as any);
    mockedRecordsManagementService.assignRecordCategory.mockResolvedValue({
      nodeId: 'record-1',
      name: 'Employee Contract',
      path: '/Sites/hr/Employee Contract.pdf',
      declaredAt: '2026-04-14T10:00:00',
      recordCategoryId: 'cat-1',
      recordCategoryName: 'Contracts',
      recordCategoryPath: '/Records Management/Contracts',
    } as any);
    mockedRecordsManagementService.undeclareRecord.mockResolvedValue(undefined as any);
    mockedRecordsManagementService.listReportPresets.mockResolvedValue([
      {
        id: 'preset-family-current',
        owner: 'admin',
        name: 'HR family current',
        description: 'Saved family report',
        kind: 'ACTIVITY_FAMILY_REPORT',
        params: {
          from: '2026-04-09T00:00:00',
          to: '2026-04-15T23:59:59',
        },
        createdDate: '2026-04-15T12:00:00',
        lastModifiedDate: '2026-04-15T12:30:00',
      },
      {
        id: 'preset-family-highlights',
        owner: 'admin',
        name: 'HR family highlights',
        description: 'Summary-only window',
        kind: 'ACTIVITY_FAMILY_HIGHLIGHTS',
        params: {
          from: '2026-04-09T00:00:00',
          to: '2026-04-15T23:59:59',
        },
        createdDate: '2026-04-15T12:05:00',
        lastModifiedDate: '2026-04-15T12:35:00',
      },
    ] as any);
    mockedRecordsManagementService.createReportPreset.mockResolvedValue({
      id: 'preset-1',
      owner: 'admin',
      name: 'Preset',
      kind: 'ACTIVITY_FAMILY_REPORT',
      params: {},
    } as any);
    mockedRecordsManagementService.updateReportPreset.mockResolvedValue({
      id: 'preset-family-current',
      owner: 'admin',
      name: 'HR family current updated',
      kind: 'ACTIVITY_FAMILY_REPORT',
      params: {
        from: '2026-04-09T00:00:00',
        to: '2026-04-15T23:59:59',
      },
    } as any);
    mockedRecordsManagementService.deleteReportPreset.mockResolvedValue(undefined as any);
    mockedRecordsManagementService.getReportPresetSchedule.mockResolvedValue({
      presetId: 'preset-family-current',
      enabled: false,
      cronExpression: null,
      timezone: 'UTC',
      deliveryFolderId: null,
      nextRunAt: null,
      lastRunAt: null,
      lastExecution: null,
    } as any);
    mockedRecordsManagementService.updateReportPresetSchedule.mockResolvedValue({
      presetId: 'preset-family-current',
      enabled: true,
      cronExpression: '0 9 * * MON-FRI',
      timezone: 'UTC',
      deliveryFolderId: 'folder-1',
      nextRunAt: '2026-04-22T09:00:00',
      lastRunAt: null,
      lastExecution: null,
    } as any);
    mockedRecordsManagementService.deliverReportPresetNow.mockResolvedValue({
      id: 'exec-1',
      presetId: 'preset-family-current',
      triggerType: 'MANUAL',
      status: 'SUCCESS',
      filename: 'HR-family-current.csv',
      targetFolderId: 'folder-1',
      documentId: 'doc-1',
      message: 'Delivered successfully',
      startedAt: '2026-04-21T09:00:00',
      finishedAt: '2026-04-21T09:00:01',
      durationMs: 1000,
    } as any);
    mockedRecordsManagementService.listReportPresetExecutions.mockResolvedValue([] as any);
    mockedRecordsManagementService.listReportPresetExecutionLedger.mockResolvedValue({
      content: [
        {
          id: 'ledger-1',
          presetId: 'preset-family-current',
          presetName: 'HR family current',
          presetKind: 'ACTIVITY_FAMILY_REPORT',
          triggerType: 'MANUAL',
          status: 'SUCCESS',
          filename: 'hr-family-current-20260421.csv',
          targetFolderId: 'folder-1',
          documentId: 'doc-1',
          message: 'Delivered successfully',
          startedAt: '2026-04-21T09:00:00',
          finishedAt: '2026-04-21T09:00:01',
          durationMs: 1000,
        },
        {
          id: 'ledger-2',
          presetId: 'preset-family-current',
          presetName: 'HR family current',
          presetKind: 'ACTIVITY_FAMILY_REPORT',
          triggerType: 'SCHEDULED',
          status: 'FAILED',
          filename: 'hr-family-current-20260422.csv',
          targetFolderId: 'folder-1',
          documentId: null,
          message: 'Delivery failed',
          startedAt: '2026-04-22T09:00:00',
          finishedAt: '2026-04-22T09:00:03',
          durationMs: 3000,
        },
      ],
      totalElements: 2,
      totalPages: 1,
      number: 0,
      size: 10,
    } as any);
    mockedRecordsManagementService.exportReportPresetExecutionLedgerCsv.mockResolvedValue(undefined as any);
  });

  it('loads records management summary, operations telemetry, browse surfaces, and audit', async () => {
    renderPage();

    expect(await screen.findByRole('heading', { name: 'Records Management' })).toBeTruthy();
    expect(await screen.findByRole('heading', { name: 'Governance Health' })).toBeTruthy();
    expect(await screen.findByRole('heading', { name: 'Governed Operations' })).toBeTruthy();
    expect(screen.getByText('Failed Governed Imports')).toBeTruthy();
    expect(screen.getByText('Top Import Governance Reasons')).toBeTruthy();
    expect(screen.getByText('Top Transfer Governance Reasons')).toBeTruthy();
    expect(screen.getByText('Declared records that still need a record category.')).toBeTruthy();
    expect(screen.getByText('TARGET_FILE_PLAN')).toBeTruthy();
    expect(screen.getByText('/Company Home/HR File Plan/Contracts')).toBeTruthy();
    expect(screen.getByText('/Sites/hr/Transfer Candidate')).toBeTruthy();
    expect(await screen.findByText('HR File Plan')).toBeTruthy();
    expect(screen.getByText('/Records Management/Contracts')).toBeTruthy();
    expect(screen.getByText('/Sites/hr/Employee Contract.pdf')).toBeTruthy();
    expect(screen.getByText('/Company Home/Finance File Plan/Finance Policy.pdf')).toBeTruthy();
    expect(screen.getAllByText('RM_RECORD_DECLARED').length).toBeGreaterThan(0);
  });

  it('renders RM snapshot cards for coverage and governed-operations health', async () => {
    renderPage();

    expect(await screen.findByText('Categorized records · 1')).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Declared Record Coverage Snapshot' })).toBeTruthy();
    expect(screen.getByText('Category Coverage')).toBeTruthy();
    expect(screen.getByText('Uncategorized records · 1')).toBeTruthy();
    expect(screen.getByText('Inside file plan · 1')).toBeTruthy();
    expect(screen.getByText('Outside file plan · 1')).toBeTruthy();

    expect(screen.getByRole('heading', { name: 'Governed Operations Snapshot' })).toBeTruthy();
    expect(screen.getByText('Import Queue Health')).toBeTruthy();
    expect(screen.getByText('Active imports · 1')).toBeTruthy();
    expect(screen.getByText('Failed imports · 1')).toBeTruthy();
    expect(screen.getByText('Other imports · 1')).toBeTruthy();
    expect(screen.getByText('Transfer Queue Health')).toBeTruthy();
    expect(screen.getByText('Active transfers · 1')).toBeTruthy();
    expect(screen.getByText('Failed transfers · 1')).toBeTruthy();
    expect(screen.getByText('Other transfers · 0')).toBeTruthy();
  });

  it('renders RM activity highlights from audit-backed comparison data', async () => {
    renderPage();

    expect(await screen.findByRole('heading', { name: 'RM Activity Highlights' })).toBeTruthy();
    expect(await screen.findByText('Current window: 2026-04-08 to 2026-04-14 | 5 active day(s)')).toBeTruthy();
    expect(await screen.findByText('Previous window: 2026-04-01 to 2026-04-07 | 4 active day(s)')).toBeTruthy();
    expect(screen.getByText('Total RM Events')).toBeTruthy();
    expect(await screen.findByText('+6 vs previous window | 5 active day(s)')).toBeTruthy();
    expect(screen.getByText('Busiest Day')).toBeTruthy();
    expect(screen.getByText('2026-04-14 | 4 event(s)')).toBeTruthy();
  });

  it('renders RM activity breakdown from bucketed trend data', async () => {
    renderPage();

    const breakdownSection = (await screen.findByRole('heading', { name: 'RM Activity Breakdown' })).closest('.MuiCard-root');
    expect(breakdownSection).toBeTruthy();
    expect(await within(breakdownSection as HTMLElement).findByText('2026-04-08 to 2026-04-14')).toBeTruthy();
    expect(within(breakdownSection as HTMLElement).getByText('10 event(s)')).toBeTruthy();
    expect(within(breakdownSection as HTMLElement).getByText('Declared 3 · Undeclared 1 · Category Assigned 4 · Governance Changes 2 · Active Days 5')).toBeTruthy();
  });

  it('renders RM activity contributors from audit-backed contributor data', async () => {
    renderPage();

    const contributorsSection = (await screen.findByRole('heading', { name: 'RM Activity Contributors' })).closest('.MuiCard-root');
    expect(contributorsSection).toBeTruthy();
    expect(await within(contributorsSection as HTMLElement).findByText('admin')).toBeTruthy();
    expect(within(contributorsSection as HTMLElement).getByText('Declared 3 · Undeclared 1 · Category Assigned 2 · Governance Changes 1')).toBeTruthy();
    expect(within(contributorsSection as HTMLElement).getByText(/Last event .*7 total event\(s\)/)).toBeTruthy();
    expect(within(contributorsSection as HTMLElement).getByText('(System)')).toBeTruthy();
  });

  it('exports activity contributor report CSVs for current and previous windows', async () => {
    renderPage();

    const contributorsSection = (await screen.findByRole('heading', { name: 'RM Activity Contributors' })).closest('.MuiCard-root');
    expect(contributorsSection).toBeTruthy();

    const effectiveDays = 28;
    const endDate = new Date();
    const currentStartDate = new Date(endDate);
    currentStartDate.setDate(currentStartDate.getDate() - (effectiveDays - 1));
    const previousEndDate = new Date(currentStartDate);
    previousEndDate.setDate(previousEndDate.getDate() - 1);
    const previousStartDate = new Date(previousEndDate);
    previousStartDate.setDate(previousStartDate.getDate() - (effectiveDays - 1));
    const formatLocalDay = (date: Date) => {
      const year = date.getFullYear();
      const month = `${date.getMonth() + 1}`.padStart(2, '0');
      const day = `${date.getDate()}`.padStart(2, '0');
      return `${year}-${month}-${day}`;
    };
    const formatRangeBoundary = (date: Date, endOfDay = false) =>
      `${formatLocalDay(date)}T${endOfDay ? '23:59:59' : '00:00:00'}`;

    fireEvent.click(await within(contributorsSection as HTMLElement).findByRole('button', { name: 'Export current CSV' }));

    await waitFor(() => expect(mockedRecordsManagementService.exportActivityContributorReportCsv).toHaveBeenCalledWith({
      from: formatRangeBoundary(currentStartDate),
      to: formatRangeBoundary(endDate, true),
      limit: 5,
    }));
    expect(toastSuccessMock).toHaveBeenCalledWith('Activity contributor current window CSV exported');

    fireEvent.click(await within(contributorsSection as HTMLElement).findByRole('button', { name: 'Export previous CSV' }));

    await waitFor(() => expect(mockedRecordsManagementService.exportActivityContributorReportCsv).toHaveBeenLastCalledWith({
      from: formatRangeBoundary(previousStartDate),
      to: formatRangeBoundary(previousEndDate, true),
      limit: 5,
    }));
    expect(toastSuccessMock).toHaveBeenCalledWith('Activity contributor previous window CSV exported');
  });

  it('applies a saved RM report preset to the existing audit surface', async () => {
    renderPage();

    const presetSection = (await screen.findByRole('heading', { name: 'Saved RM Report Presets' })).closest('.MuiCard-root');
    expect(presetSection).toBeTruthy();
    const presetRow = (await within(presetSection as HTMLElement).findByText('HR family current')).closest('tr');
    expect(presetRow).toBeTruthy();

    fireEvent.click(within(presetRow as HTMLElement).getByRole('button', { name: 'Apply to audit' }));

    expect(await screen.findByText(/Reviewing audit evidence for Preset HR family current/)).toBeTruthy();
  });

  it('exports a saved RM report preset via the existing CSV route', async () => {
    renderPage();

    const presetSection = (await screen.findByRole('heading', { name: 'Saved RM Report Presets' })).closest('.MuiCard-root');
    expect(presetSection).toBeTruthy();
    const presetRow = (await within(presetSection as HTMLElement).findByText('HR family current')).closest('tr');
    expect(presetRow).toBeTruthy();

    fireEvent.click(within(presetRow as HTMLElement).getByRole('button', { name: 'Export CSV' }));

    await waitFor(() => expect(mockedRecordsManagementService.exportActivityFamilyReportCsv).toHaveBeenCalledWith({
      from: '2026-04-09T00:00:00',
      to: '2026-04-15T23:59:59',
    }));
    expect(toastSuccessMock).toHaveBeenCalledWith('RM report preset CSV exported');
  });

  it('opens the schedule dialog from a CSV-capable preset row and saves schedule config', async () => {
    renderPage();

    const presetSection = (await screen.findByRole('heading', { name: 'Saved RM Report Presets' })).closest('.MuiCard-root');
    expect(presetSection).toBeTruthy();
    const presetRow = (await within(presetSection as HTMLElement).findByText('HR family current')).closest('tr');
    expect(presetRow).toBeTruthy();

    fireEvent.click(within(presetRow as HTMLElement).getByRole('button', { name: 'Schedule' }));

    expect(await screen.findByRole('dialog', { name: /Schedule Delivery/i })).toBeTruthy();
    await waitFor(() =>
      expect(mockedRecordsManagementService.getReportPresetSchedule).toHaveBeenCalledWith('preset-family-current')
    );
    expect(mockedRecordsManagementService.listReportPresetExecutions).toHaveBeenCalledWith('preset-family-current', 5);

    fireEvent.click(screen.getByRole('checkbox', { name: /Enable scheduled delivery/i }));
    fireEvent.change(screen.getByRole('textbox', { name: 'Cron expression' }), {
      target: { value: ' 0 9 * * MON-FRI ' },
    });
    fireEvent.click(screen.getByRole('button', { name: /pick folder-1/i }));
    fireEvent.click(screen.getByRole('button', { name: 'Save schedule' }));

    await waitFor(() => expect(mockedRecordsManagementService.updateReportPresetSchedule).toHaveBeenCalledWith(
      'preset-family-current',
      {
        enabled: true,
        cronExpression: '0 9 * * MON-FRI',
        timezone: 'UTC',
        deliveryFolderId: 'folder-1',
      }
    ));
  });

  it('keeps summary-only presets audit-only in the preset table', async () => {
    renderPage();

    const presetRow = (await screen.findByText('HR family highlights')).closest('tr');
    expect(presetRow).toBeTruthy();

    expect(within(presetRow as HTMLElement).queryByRole('button', { name: 'Export CSV' })).toBeNull();
    expect(within(presetRow as HTMLElement).queryByRole('button', { name: 'Schedule' })).toBeNull();
    expect(within(presetRow as HTMLElement).getByRole('button', { name: 'Apply to audit' })).toBeTruthy();
  });

  it('edits a saved RM report preset from the preset table', async () => {
    renderPage();

    const presetSection = (await screen.findByRole('heading', { name: 'Saved RM Report Presets' })).closest('.MuiCard-root');
    expect(presetSection).toBeTruthy();
    const presetRow = (await within(presetSection as HTMLElement).findByText('HR family current')).closest('tr');
    expect(presetRow).toBeTruthy();

    fireEvent.click(within(presetRow as HTMLElement).getByRole('button', { name: 'Edit' }));

    expect(await screen.findByRole('dialog', { name: 'Edit RM Report Preset' })).toBeTruthy();
    fireEvent.change(screen.getByRole('textbox', { name: 'Preset Name' }), {
      target: { value: '  HR family current updated  ' },
    });
    fireEvent.change(screen.getByRole('textbox', { name: 'Description (optional)' }), {
      target: { value: '  Updated from preset table  ' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Update Preset' }));

    await waitFor(() => expect(mockedRecordsManagementService.updateReportPreset).toHaveBeenCalledWith(
      'preset-family-current',
      {
        name: 'HR family current updated',
        description: 'Updated from preset table',
        params: {
          from: '2026-04-09T00:00:00',
          to: '2026-04-15T23:59:59',
        },
      }
    ));
    expect(toastSuccessMock).toHaveBeenCalledWith('RM report preset updated');
  });

  it('deletes a saved RM report preset from the preset table', async () => {
    const confirmSpy = jest.spyOn(window, 'confirm').mockReturnValue(true);
    renderPage();

    const presetSection = (await screen.findByRole('heading', { name: 'Saved RM Report Presets' })).closest('.MuiCard-root');
    expect(presetSection).toBeTruthy();
    const presetRow = (await within(presetSection as HTMLElement).findByText('HR family current')).closest('tr');
    expect(presetRow).toBeTruthy();

    fireEvent.click(within(presetRow as HTMLElement).getByRole('button', { name: 'Delete' }));

    await waitFor(() => expect(mockedRecordsManagementService.deleteReportPreset).toHaveBeenCalledWith('preset-family-current'));
    expect(toastSuccessMock).toHaveBeenCalledWith('RM report preset deleted');
    confirmSpy.mockRestore();
  });

  it('renders the preset delivery ledger and opens delivered browse targets', async () => {
    const openSpy = jest.spyOn(window, 'open').mockImplementation(() => null);
    renderPage();

    const ledgerSection = (await screen.findByRole('heading', { name: 'Preset Delivery Ledger' })).closest('.MuiCard-root');
    expect(ledgerSection).toBeTruthy();
    expect(await within(ledgerSection as HTMLElement).findAllByText('HR family current')).toHaveLength(2);
    expect(within(ledgerSection as HTMLElement).getByText('Successful')).toBeTruthy();
    expect(within(ledgerSection as HTMLElement).getByText('Failed')).toBeTruthy();
    expect(within(ledgerSection as HTMLElement).getByText('Showing 2 of 2 deliveries')).toBeTruthy();

    fireEvent.click(within(ledgerSection as HTMLElement).getByRole('button', { name: 'Open delivered file' }));
    expect(openSpy).toHaveBeenCalledWith('/browse/doc-1', '_blank', 'noopener,noreferrer');

    fireEvent.click(within(ledgerSection as HTMLElement).getAllByRole('button', { name: 'Open target folder' })[0]);
    expect(openSpy).toHaveBeenLastCalledWith('/browse/folder-1', '_blank', 'noopener,noreferrer');

    openSpy.mockRestore();
  });

  it('filters and exports the preset delivery ledger', async () => {
    renderPage();

    const ledgerSection = (await screen.findByRole('heading', { name: 'Preset Delivery Ledger' })).closest('.MuiCard-root');
    expect(ledgerSection).toBeTruthy();

    const resultSelect = within(ledgerSection as HTMLElement).getByRole('combobox', { name: 'Result' });
    fireEvent.mouseDown(resultSelect);
    fireEvent.click(await screen.findByRole('option', { name: 'Failed' }));

    const triggerSelect = within(ledgerSection as HTMLElement).getByRole('combobox', { name: 'Trigger' });
    fireEvent.mouseDown(triggerSelect);
    fireEvent.click(await screen.findByRole('option', { name: 'Scheduled' }));

    fireEvent.change(within(ledgerSection as HTMLElement).getByLabelText('From'), {
      target: { value: '2026-04-21T00:00:00' },
    });
    fireEvent.change(within(ledgerSection as HTMLElement).getByLabelText('To'), {
      target: { value: '2026-04-22T23:59:59' },
    });

    fireEvent.click(within(ledgerSection as HTMLElement).getByRole('button', { name: 'Apply' }));

    await waitFor(() => expect(mockedRecordsManagementService.listReportPresetExecutionLedger).toHaveBeenLastCalledWith({
      status: 'FAILED',
      triggerType: 'SCHEDULED',
      from: '2026-04-21T00:00',
      to: '2026-04-22T23:59:59.000',
      page: 0,
      size: 10,
    }));
    expect(within(ledgerSection as HTMLElement).getByText('Active ledger filters')).toBeTruthy();
    expect(within(ledgerSection as HTMLElement).getByText('Result: Failed')).toBeTruthy();
    expect(within(ledgerSection as HTMLElement).getByText('Trigger: Scheduled')).toBeTruthy();

    fireEvent.click(within(ledgerSection as HTMLElement).getByRole('button', { name: 'Export ledger CSV' }));

    await waitFor(() => expect(mockedRecordsManagementService.exportReportPresetExecutionLedgerCsv).toHaveBeenCalledWith({
      status: 'FAILED',
      triggerType: 'SCHEDULED',
      from: '2026-04-21T00:00',
      to: '2026-04-22T23:59:59.000',
      limit: 10,
    }));
    expect(toastSuccessMock).toHaveBeenCalledWith('RM preset delivery ledger CSV exported');
  });

  it('shows zero-match preset delivery ledger state and recovers by clearing filters', async () => {
    mockedRecordsManagementService.listReportPresetExecutionLedger
      .mockReset()
      .mockResolvedValueOnce({
        content: [
          {
            id: 'ledger-1',
            presetId: 'preset-family-current',
            presetName: 'HR family current',
            presetKind: 'ACTIVITY_FAMILY_REPORT',
            triggerType: 'MANUAL',
            status: 'SUCCESS',
            filename: 'hr-family-current-20260421.csv',
            targetFolderId: 'folder-1',
            documentId: 'doc-1',
            message: 'Delivered successfully',
            startedAt: '2026-04-21T09:00:00',
            finishedAt: '2026-04-21T09:00:01',
            durationMs: 1000,
          },
          {
            id: 'ledger-2',
            presetId: 'preset-family-current',
            presetName: 'HR family current',
            presetKind: 'ACTIVITY_FAMILY_REPORT',
            triggerType: 'SCHEDULED',
            status: 'FAILED',
            filename: 'hr-family-current-20260422.csv',
            targetFolderId: 'folder-1',
            documentId: null,
            message: 'Delivery failed',
            startedAt: '2026-04-22T09:00:00',
            finishedAt: '2026-04-22T09:00:03',
            durationMs: 3000,
          },
        ],
        totalElements: 2,
        totalPages: 1,
        number: 0,
        size: 10,
      } as any)
      .mockResolvedValueOnce({
        content: [],
        totalElements: 0,
        totalPages: 0,
        number: 0,
        size: 10,
      } as any)
      .mockResolvedValueOnce({
        content: [
          {
            id: 'ledger-1',
            presetId: 'preset-family-current',
            presetName: 'HR family current',
            presetKind: 'ACTIVITY_FAMILY_REPORT',
            triggerType: 'MANUAL',
            status: 'SUCCESS',
            filename: 'hr-family-current-20260421.csv',
            targetFolderId: 'folder-1',
            documentId: 'doc-1',
            message: 'Delivered successfully',
            startedAt: '2026-04-21T09:00:00',
            finishedAt: '2026-04-21T09:00:01',
            durationMs: 1000,
          },
          {
            id: 'ledger-2',
            presetId: 'preset-family-current',
            presetName: 'HR family current',
            presetKind: 'ACTIVITY_FAMILY_REPORT',
            triggerType: 'SCHEDULED',
            status: 'FAILED',
            filename: 'hr-family-current-20260422.csv',
            targetFolderId: 'folder-1',
            documentId: null,
            message: 'Delivery failed',
            startedAt: '2026-04-22T09:00:00',
            finishedAt: '2026-04-22T09:00:03',
            durationMs: 3000,
          },
        ],
        totalElements: 2,
        totalPages: 1,
        number: 0,
        size: 10,
      } as any);

    renderPage();

    const ledgerSection = (await screen.findByRole('heading', { name: 'Preset Delivery Ledger' })).closest('.MuiCard-root');
    expect(ledgerSection).toBeTruthy();

    const resultSelect = within(ledgerSection as HTMLElement).getByRole('combobox', { name: 'Result' });
    fireEvent.mouseDown(resultSelect);
    fireEvent.click(await screen.findByRole('option', { name: 'Failed' }));
    fireEvent.click(within(ledgerSection as HTMLElement).getByRole('button', { name: 'Apply' }));

    expect(await within(ledgerSection as HTMLElement).findByText('No deliveries match the current filters.')).toBeTruthy();
    expect(within(ledgerSection as HTMLElement).getByRole('button', { name: 'Show all deliveries' })).toBeTruthy();

    fireEvent.click(within(ledgerSection as HTMLElement).getByRole('button', { name: 'Show all deliveries' }));

    await waitFor(() => expect(mockedRecordsManagementService.listReportPresetExecutionLedger).toHaveBeenLastCalledWith({
      page: 0,
      size: 10,
    }));
    await waitFor(() => expect(within(ledgerSection as HTMLElement).queryByText('Active ledger filters')).toBeNull());
    await waitFor(() => expect(within(ledgerSection as HTMLElement).getByText('Showing 2 of 2 deliveries')).toBeTruthy());
  });

  it('saves an activity contributor report preset for the previous rolling window', async () => {
    renderPage();

    const contributorsSection = (await screen.findByRole('heading', { name: 'RM Activity Contributors' })).closest('.MuiCard-root');
    expect(contributorsSection).toBeTruthy();

    const effectiveDays = 28;
    const endDate = new Date();
    const currentStartDate = new Date(endDate);
    currentStartDate.setDate(currentStartDate.getDate() - (effectiveDays - 1));
    const previousEndDate = new Date(currentStartDate);
    previousEndDate.setDate(previousEndDate.getDate() - 1);
    const previousStartDate = new Date(previousEndDate);
    previousStartDate.setDate(previousStartDate.getDate() - (effectiveDays - 1));
    const formatLocalDay = (date: Date) => {
      const year = date.getFullYear();
      const month = `${date.getMonth() + 1}`.padStart(2, '0');
      const day = `${date.getDate()}`.padStart(2, '0');
      return `${year}-${month}-${day}`;
    };
    const formatRangeBoundary = (date: Date, endOfDay = false) =>
      `${formatLocalDay(date)}T${endOfDay ? '23:59:59' : '00:00:00'}`;

    fireEvent.click(await within(contributorsSection as HTMLElement).findByRole('button', { name: 'Save previous preset' }));
    fireEvent.change(await screen.findByRole('textbox', { name: 'Preset Name' }), {
      target: { value: '  Contributor previous window  ' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save Preset' }));

    await waitFor(() => expect(mockedRecordsManagementService.createReportPreset).toHaveBeenCalledWith({
      name: 'Contributor previous window',
      description: undefined,
      kind: 'ACTIVITY_CONTRIBUTOR_REPORT',
      params: {
        from: formatRangeBoundary(previousStartDate),
        to: formatRangeBoundary(previousEndDate, true),
        limit: 5,
      },
    }));
    expect(toastSuccessMock).toHaveBeenCalledWith('RM report preset saved');
  });

  it('renders RM contributor event-type highlights from audit-backed comparison data', async () => {
    renderPage();

    const highlightsSection = (await screen.findByRole('heading', { name: 'RM Contributor Event-Type Highlights' })).closest('.MuiCard-root');
    expect(highlightsSection).toBeTruthy();
    expect(within(highlightsSection as HTMLElement).getByText('Current 5')).toBeTruthy();
    expect(within(highlightsSection as HTMLElement).getByText('Previous 2')).toBeTruthy();
    expect(within(highlightsSection as HTMLElement).getByText(/\+3 vs previous window/)).toBeTruthy();
    expect(within(highlightsSection as HTMLElement).getByText('Record Declared')).toBeTruthy();
    expect(within(highlightsSection as HTMLElement).getAllByRole('button', { name: 'Review current audit' }).length).toBeGreaterThan(0);
  });

  it('renders RM contributor family highlights from audit-backed comparison data', async () => {
    renderPage();

    const highlightsSection = (await screen.findByRole('heading', { name: 'RM Contributor Family Highlights' })).closest('.MuiCard-root');
    expect(highlightsSection).toBeTruthy();
    expect(await within(highlightsSection as HTMLElement).findByText('Current 5')).toBeTruthy();
    expect(within(highlightsSection as HTMLElement).getByText('Previous 2')).toBeTruthy();
    expect(within(highlightsSection as HTMLElement).getByText(/\+3 vs previous window/)).toBeTruthy();
    expect(within(highlightsSection as HTMLElement).getByText('Declared')).toBeTruthy();
    expect(within(highlightsSection as HTMLElement).getAllByRole('button', { name: 'Review current audit' }).length).toBeGreaterThan(0);
  });

  it('exports activity family report CSVs for current and previous windows', async () => {
    renderPage();

    const familyHighlightsSection = (await screen.findByRole('heading', { name: 'RM Activity Family Highlights' })).closest('.MuiCard-root');
    expect(familyHighlightsSection).toBeTruthy();

    fireEvent.click(await within(familyHighlightsSection as HTMLElement).findByRole('button', { name: 'Export current CSV' }));

    await waitFor(() => expect(mockedRecordsManagementService.exportActivityFamilyReportCsv).toHaveBeenCalledWith({
      from: '2026-04-09T00:00:00',
      to: '2026-04-15T23:59:59',
    }));
    expect(toastSuccessMock).toHaveBeenCalledWith('Activity family current window CSV exported');

    fireEvent.click(await within(familyHighlightsSection as HTMLElement).findByRole('button', { name: 'Export previous CSV' }));

    await waitFor(() => expect(mockedRecordsManagementService.exportActivityFamilyReportCsv).toHaveBeenLastCalledWith({
      from: '2026-04-02T00:00:00',
      to: '2026-04-08T23:59:59',
    }));
    expect(toastSuccessMock).toHaveBeenCalledWith('Activity family previous window CSV exported');
  });

  it('saves an activity family report preset for the current window', async () => {
    renderPage();

    const familyHighlightsSection = (await screen.findByRole('heading', { name: 'RM Activity Family Highlights' })).closest('.MuiCard-root');
    expect(familyHighlightsSection).toBeTruthy();

    fireEvent.click(await within(familyHighlightsSection as HTMLElement).findByRole('button', { name: 'Save current preset' }));

    expect(await screen.findByRole('dialog', { name: 'Save RM Report Preset' })).toBeTruthy();
    const nameInput = screen.getByRole('textbox', { name: 'Preset Name' }) as HTMLInputElement;
    expect(nameInput.value).toBe('RM Activity Family Report Current Window');

    fireEvent.change(nameInput, {
      target: { value: '  HR family current window  ' },
    });
    fireEvent.change(screen.getByRole('textbox', { name: 'Description (optional)' }), {
      target: { value: '  Saved from highlights card  ' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save Preset' }));

    await waitFor(() => expect(mockedRecordsManagementService.createReportPreset).toHaveBeenCalledWith({
      name: 'HR family current window',
      description: 'Saved from highlights card',
      kind: 'ACTIVITY_FAMILY_REPORT',
      params: {
        from: '2026-04-09T00:00:00',
        to: '2026-04-15T23:59:59',
      },
    }));
    expect(toastSuccessMock).toHaveBeenCalledWith('RM report preset saved');
  });

  it('exports activity event-type report CSVs for current and previous windows', async () => {
    renderPage();

    const hotspotsSection = (await screen.findByRole('heading', { name: 'RM Activity Event Hotspots' })).closest('.MuiCard-root');
    expect(hotspotsSection).toBeTruthy();

    const effectiveDays = 28;
    const endDate = new Date();
    const currentStartDate = new Date(endDate);
    currentStartDate.setDate(currentStartDate.getDate() - (effectiveDays - 1));
    const previousEndDate = new Date(currentStartDate);
    previousEndDate.setDate(previousEndDate.getDate() - 1);
    const previousStartDate = new Date(previousEndDate);
    previousStartDate.setDate(previousStartDate.getDate() - (effectiveDays - 1));
    const formatLocalDay = (date: Date) => {
      const year = date.getFullYear();
      const month = `${date.getMonth() + 1}`.padStart(2, '0');
      const day = `${date.getDate()}`.padStart(2, '0');
      return `${year}-${month}-${day}`;
    };
    const formatRangeBoundary = (date: Date, endOfDay = false) =>
      `${formatLocalDay(date)}T${endOfDay ? '23:59:59' : '00:00:00'}`;

    fireEvent.click(await within(hotspotsSection as HTMLElement).findByRole('button', { name: 'Export current CSV' }));

    await waitFor(() => expect(mockedRecordsManagementService.exportActivityEventTypeReportCsv).toHaveBeenCalledWith({
      from: formatRangeBoundary(currentStartDate),
      to: formatRangeBoundary(endDate, true),
      limit: 8,
    }));
    expect(toastSuccessMock).toHaveBeenCalledWith('Activity event-type current window CSV exported');

    fireEvent.click(await within(hotspotsSection as HTMLElement).findByRole('button', { name: 'Export previous CSV' }));

    await waitFor(() => expect(mockedRecordsManagementService.exportActivityEventTypeReportCsv).toHaveBeenLastCalledWith({
      from: formatRangeBoundary(previousStartDate),
      to: formatRangeBoundary(previousEndDate, true),
      limit: 8,
    }));
    expect(toastSuccessMock).toHaveBeenCalledWith('Activity event-type previous window CSV exported');
  });

  it('exports contributor family report CSVs for current and previous windows', async () => {
    renderPage();

    const highlightsSection = (await screen.findByRole('heading', { name: 'RM Contributor Family Highlights' })).closest('.MuiCard-root');
    expect(highlightsSection).toBeTruthy();

    fireEvent.click(await within(highlightsSection as HTMLElement).findByRole('button', { name: 'Export current CSV' }));

    await waitFor(() => expect(mockedRecordsManagementService.exportActivityContributorFamilyReportCsv).toHaveBeenCalledWith({
      from: '2026-04-09T00:00:00',
      to: '2026-04-15T23:59:59',
      limit: 5,
    }));
    expect(toastSuccessMock).toHaveBeenCalledWith('Contributor family current window CSV exported');

    fireEvent.click(await within(highlightsSection as HTMLElement).findByRole('button', { name: 'Export previous CSV' }));

    await waitFor(() => expect(mockedRecordsManagementService.exportActivityContributorFamilyReportCsv).toHaveBeenLastCalledWith({
      from: '2026-04-02T00:00:00',
      to: '2026-04-08T23:59:59',
      limit: 5,
    }));
    expect(toastSuccessMock).toHaveBeenCalledWith('Contributor family previous window CSV exported');
  });

  it('exports contributor event-type report CSVs for current and previous windows', async () => {
    renderPage();

    const highlightsSection = (await screen.findByRole('heading', { name: 'RM Contributor Event-Type Highlights' })).closest('.MuiCard-root');
    expect(highlightsSection).toBeTruthy();

    fireEvent.click(await within(highlightsSection as HTMLElement).findByRole('button', { name: 'Export current CSV' }));

    await waitFor(() => expect(mockedRecordsManagementService.exportActivityContributorEventTypeReportCsv).toHaveBeenCalledWith({
      from: '2026-04-09T00:00:00',
      to: '2026-04-15T23:59:59',
      limit: 5,
      eventTypeLimit: 3,
    }));
    expect(toastSuccessMock).toHaveBeenCalledWith('Contributor event-type current window CSV exported');

    fireEvent.click(await within(highlightsSection as HTMLElement).findByRole('button', { name: 'Export previous CSV' }));

    await waitFor(() => expect(mockedRecordsManagementService.exportActivityContributorEventTypeReportCsv).toHaveBeenLastCalledWith({
      from: '2026-04-02T00:00:00',
      to: '2026-04-08T23:59:59',
      limit: 5,
      eventTypeLimit: 3,
    }));
    expect(toastSuccessMock).toHaveBeenCalledWith('Contributor event-type previous window CSV exported');
  });

  it('renders RM contributor event-type trend from bucketed contributor event-type data', async () => {
    renderPage();

    const trendSection = (await screen.findByRole('heading', { name: 'RM Contributor Event-Type Trend' })).closest('.MuiCard-root');
    expect(trendSection).toBeTruthy();
    expect(within(trendSection as HTMLElement).getByText('2026-04-08 to 2026-04-14')).toBeTruthy();
    expect(within(trendSection as HTMLElement).getByText('10 event(s) · 5 active day(s) · 2 outside tracked contributors')).toBeTruthy();
    expect(within(trendSection as HTMLElement).getByText('admin')).toBeTruthy();
    expect(within(trendSection as HTMLElement).getByRole('button', { name: 'Record Declared 3' })).toBeTruthy();
    expect(within(trendSection as HTMLElement).getByRole('button', { name: 'Record Undeclared 2' })).toBeTruthy();
  });

  it('renders RM contributor family trend from bucketed contributor family data', async () => {
    renderPage();

    const trendSection = (await screen.findByRole('heading', { name: 'RM Contributor Family Trend' })).closest('.MuiCard-root');
    expect(trendSection).toBeTruthy();
    expect(await within(trendSection as HTMLElement).findByText('2026-04-08 to 2026-04-14')).toBeTruthy();
    expect(within(trendSection as HTMLElement).getByText('10 event(s) · 5 active day(s) · 2 outside tracked contributors')).toBeTruthy();
    expect(within(trendSection as HTMLElement).getByText('admin')).toBeTruthy();
    expect(within(trendSection as HTMLElement).getByRole('button', { name: 'Declared 3' })).toBeTruthy();
    expect(within(trendSection as HTMLElement).getByRole('button', { name: 'Undeclared 2' })).toBeTruthy();
  });

  it('renders RM activity timeline from audit-backed trend data', async () => {
    renderPage();

    expect(await screen.findByRole('heading', { name: 'RM Activity Timeline' })).toBeTruthy();
    expect(await screen.findByText('Declared 1 · Undeclared 1 · Category Assigned 0 · Governance Changes 2')).toBeTruthy();
    expect(screen.getByText('2026-04-14')).toBeTruthy();
    expect(screen.getAllByText('4 event(s)').length).toBeGreaterThan(0);
    expect(screen.getByText('2026-04-13')).toBeTruthy();
    expect(screen.getByText('3 event(s)')).toBeTruthy();
  });

  it('drills from the full timeline shortcut into the existing audit table', async () => {
    renderPage();

    const timelineSection = (await screen.findByRole('heading', { name: 'RM Activity Timeline' })).closest('.MuiCard-root');
    expect(timelineSection).toBeTruthy();

    fireEvent.click(await within(timelineSection as HTMLElement).findByRole('button', { name: 'Review full timeline audit' }));

    await waitFor(() => expect(mockedRecordsManagementService.listAudit).toHaveBeenLastCalledWith({
      family: '',
      eventType: '',
      username: '',
      from: '2026-04-12T00:00:00',
      to: '2026-04-14T23:59:59',
      page: 0,
      size: 10,
    }));

    expect(screen.getByText(/Reviewing audit evidence for Activity timeline window/)).toBeTruthy();
  });

  it('drills from the full breakdown shortcut into the existing audit table', async () => {
    renderPage();

    const breakdownSection = (await screen.findByRole('heading', { name: 'RM Activity Breakdown' })).closest('.MuiCard-root');
    expect(breakdownSection).toBeTruthy();

    fireEvent.click(await within(breakdownSection as HTMLElement).findByRole('button', { name: 'Review full breakdown audit' }));

    await waitFor(() => expect(mockedRecordsManagementService.listAudit).toHaveBeenLastCalledWith({
      family: '',
      eventType: '',
      username: '',
      from: '2026-03-18T00:00:00',
      to: '2026-04-14T23:59:59',
      page: 0,
      size: 10,
    }));

    expect(screen.getByText(/Reviewing audit evidence for Activity breakdown window/)).toBeTruthy();
  });

  it('drills from contributors into the existing audit table with username and range', async () => {
    renderPage();

    const contributorsSection = (await screen.findByRole('heading', { name: 'RM Activity Contributors' })).closest('.MuiCard-root');
    expect(contributorsSection).toBeTruthy();

    fireEvent.click(within(contributorsSection as HTMLElement).getAllByRole('button', { name: 'Review contributor audit' })[0]);

    await waitFor(() => {
      const lastCall = mockedRecordsManagementService.listAudit.mock.calls.at(-1)?.[0];
      expect(lastCall.family).toBe('');
      expect(lastCall.username).toBe('admin');
      expect(lastCall.from).toBeTruthy();
      expect(lastCall.to).toBeTruthy();
      expect(lastCall.page).toBe(0);
      expect(lastCall.size).toBe(10);
    });

    expect((screen.getByLabelText('Username') as HTMLInputElement).value).toBe('admin');
    expect(screen.getByText(/Reviewing audit evidence for Contributor admin/)).toBeTruthy();
  });

  it('prefills audit range from the current activity window drilldown', async () => {
    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: 'Review current-window audit' }));

    await waitFor(() => expect(mockedRecordsManagementService.listAudit).toHaveBeenLastCalledWith({
      family: '',
      eventType: '',
      username: '',
      from: '2026-04-08T00:00:00',
      to: '2026-04-14T23:59:59',
      page: 0,
      size: 10,
    }));

    const auditSection = (screen.getByRole('heading', { name: 'Records Audit' })).closest('.MuiCard-root') as HTMLElement;
    expect((within(auditSection).getByLabelText('From') as HTMLInputElement).value.startsWith('2026-04-08T00:00')).toBe(true);
    expect((within(auditSection).getByLabelText('To') as HTMLInputElement).value.startsWith('2026-04-14T23:59:59')).toBe(true);
    expect(screen.getByText(/Reviewing audit evidence for Current activity window/)).toBeTruthy();
  });

  it('drills from breakdown and timeline cards into the existing audit table', async () => {
    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: 'Review audit for 2026-04-08 to 2026-04-14' }));

    await waitFor(() => expect(mockedRecordsManagementService.listAudit).toHaveBeenLastCalledWith({
      family: '',
      eventType: '',
      username: '',
      from: '2026-04-08T00:00:00',
      to: '2026-04-14T23:59:59',
      page: 0,
      size: 10,
    }));

    fireEvent.click(screen.getByRole('button', { name: 'Review audit for 2026-04-14' }));

    await waitFor(() => expect(mockedRecordsManagementService.listAudit).toHaveBeenLastCalledWith({
      family: '',
      eventType: '',
      username: '',
      from: '2026-04-14T00:00:00',
      to: '2026-04-14T23:59:59',
      page: 0,
      size: 10,
    }));

    expect(screen.getByText(/Reviewing audit evidence for Activity on 2026-04-14/)).toBeTruthy();
  });

  it('clears the active audit drilldown range', async () => {
    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: 'Review current-window audit' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Clear audit drilldown' }));

    await waitFor(() => expect(mockedRecordsManagementService.listAudit).toHaveBeenLastCalledWith({
      family: '',
      eventType: '',
      username: '',
      from: '',
      to: '',
      page: 0,
      size: 10,
    }));

    const auditSection = (screen.getByRole('heading', { name: 'Records Audit' })).closest('.MuiCard-root') as HTMLElement;
    expect((within(auditSection).getByLabelText('From') as HTMLInputElement).value).toBe('');
    expect((within(auditSection).getByLabelText('To') as HTMLInputElement).value).toBe('');
    expect(screen.queryByText(/Reviewing audit evidence for/)).toBeNull();
  });

  it('drills from contributor family counts into the existing audit table', async () => {
    renderPage();

    const contributorsSection = (await screen.findByRole('heading', { name: 'RM Activity Contributors' })).closest('.MuiCard-root');
    expect(contributorsSection).toBeTruthy();

    fireEvent.click(within(contributorsSection as HTMLElement).getByRole('button', { name: 'Declared 3' }));

    await waitFor(() => {
      const lastCall = mockedRecordsManagementService.listAudit.mock.calls.at(-1)?.[0];
      expect(lastCall.family).toBe('DECLARED');
      expect(lastCall.username).toBe('admin');
      expect(lastCall.from).toBeTruthy();
      expect(lastCall.to).toBeTruthy();
      expect(lastCall.page).toBe(0);
      expect(lastCall.size).toBe(10);
    });

    expect(screen.getByRole('combobox', { name: 'Family' }).textContent).toContain('Declared');
    expect((screen.getByLabelText('Username') as HTMLInputElement).value).toBe('admin');
    expect(screen.getByText(/Reviewing audit evidence for Contributor admin · Declared/)).toBeTruthy();
  });

  it('drills from contributor event-type trend into the existing audit table', async () => {
    renderPage();

    const trendSection = (await screen.findByRole('heading', { name: 'RM Contributor Event-Type Trend' })).closest('.MuiCard-root');
    expect(trendSection).toBeTruthy();

    fireEvent.click(within(trendSection as HTMLElement).getByRole('button', { name: 'Record Declared 3' }));

    await waitFor(() => {
      const lastCall = mockedRecordsManagementService.listAudit.mock.calls.at(-1)?.[0];
      expect(lastCall.eventType).toBe('RM_RECORD_DECLARED');
      expect(lastCall.username).toBe('admin');
      expect(lastCall.from).toBe('2026-04-08T00:00:00');
      expect(lastCall.to).toBe('2026-04-14T23:59:59');
      expect(lastCall.page).toBe(0);
      expect(lastCall.size).toBe(10);
    });

    expect((screen.getByLabelText('Event Type') as HTMLInputElement).value).toBe('RM_RECORD_DECLARED');
    expect((screen.getByLabelText('Username') as HTMLInputElement).value).toBe('admin');
    expect(screen.getByText(/Reviewing audit evidence for Contributor admin · Record Declared/)).toBeTruthy();
  });

  it('drills from contributor family trend into the existing audit table', async () => {
    renderPage();

    const trendSection = (await screen.findByRole('heading', { name: 'RM Contributor Family Trend' })).closest('.MuiCard-root');
    expect(trendSection).toBeTruthy();

    fireEvent.click(within(trendSection as HTMLElement).getByRole('button', { name: 'Declared 3' }));

    await waitFor(() => {
      const lastCall = mockedRecordsManagementService.listAudit.mock.calls.at(-1)?.[0];
      expect(lastCall.family).toBe('DECLARED');
      expect(lastCall.username).toBe('admin');
      expect(lastCall.from).toBe('2026-04-08T00:00:00');
      expect(lastCall.to).toBe('2026-04-14T23:59:59');
      expect(lastCall.page).toBe(0);
      expect(lastCall.size).toBe(10);
    });

    expect(screen.getByRole('combobox', { name: 'Family' }).textContent).toContain('Declared');
    expect((screen.getByLabelText('Username') as HTMLInputElement).value).toBe('admin');
    expect(screen.getByText(/Reviewing audit evidence for Contributor admin · Declared/)).toBeTruthy();
  });

  it('drills from contributor event-type highlights into the existing audit table', async () => {
    renderPage();

    const highlightsSection = (await screen.findByRole('heading', { name: 'RM Contributor Event-Type Highlights' })).closest('.MuiCard-root');
    expect(highlightsSection).toBeTruthy();

    fireEvent.click(within(highlightsSection as HTMLElement).getAllByRole('button', { name: 'Review current audit' })[0]);

    await waitFor(() => {
      const lastCall = mockedRecordsManagementService.listAudit.mock.calls.at(-1)?.[0];
      expect(lastCall.eventType).toBe('RM_RECORD_DECLARED');
      expect(lastCall.username).toBe('admin');
      expect(lastCall.from).toBe('2026-04-09T00:00:00');
      expect(lastCall.to).toBe('2026-04-15T23:59:59');
      expect(lastCall.page).toBe(0);
      expect(lastCall.size).toBe(10);
    });

    expect((screen.getByLabelText('Event Type') as HTMLInputElement).value).toBe('RM_RECORD_DECLARED');
    expect((screen.getByLabelText('Username') as HTMLInputElement).value).toBe('admin');
    expect(screen.getByText(/Reviewing audit evidence for Contributor admin · Record Declared · Current window/)).toBeTruthy();
  });

  it('drills from contributor family highlights into the existing audit table', async () => {
    renderPage();

    const highlightsSection = (await screen.findByRole('heading', { name: 'RM Contributor Family Highlights' })).closest('.MuiCard-root');
    expect(highlightsSection).toBeTruthy();

    fireEvent.click(within(highlightsSection as HTMLElement).getAllByRole('button', { name: 'Review current audit' })[0]);

    await waitFor(() => {
      const lastCall = mockedRecordsManagementService.listAudit.mock.calls.at(-1)?.[0];
      expect(lastCall.family).toBe('DECLARED');
      expect(lastCall.username).toBe('admin');
      expect(lastCall.from).toBe('2026-04-09T00:00:00');
      expect(lastCall.to).toBe('2026-04-15T23:59:59');
      expect(lastCall.page).toBe(0);
      expect(lastCall.size).toBe(10);
    });

    expect(screen.getByRole('combobox', { name: 'Family' }).textContent).toContain('Declared');
    expect((screen.getByLabelText('Username') as HTMLInputElement).value).toBe('admin');
    expect(screen.getByText(/Reviewing audit evidence for Contributor admin · Declared · Current window/)).toBeTruthy();
  });

  it('renders RM activity event hotspots from audit-backed event-type data', async () => {
    renderPage();

    const hotspotsSection = (await screen.findByRole('heading', { name: 'RM Activity Event Hotspots' })).closest('.MuiCard-root');
    expect(hotspotsSection).toBeTruthy();
    expect(within(hotspotsSection as HTMLElement).getByText('Record Declared')).toBeTruthy();
    expect(within(hotspotsSection as HTMLElement).getByText('RM_RECORD_DECLARED')).toBeTruthy();
    expect(within(hotspotsSection as HTMLElement).getByText(/5 event\(s\)/)).toBeTruthy();
  });

  it('renders RM activity family mix from audit-backed family data', async () => {
    renderPage();

    const familyMixSection = (await screen.findByRole('heading', { name: 'RM Activity Family Mix' })).closest('.MuiCard-root');
    expect(familyMixSection).toBeTruthy();
    expect(within(familyMixSection as HTMLElement).getAllByText('Declared').length).toBeGreaterThan(0);
    expect(within(familyMixSection as HTMLElement).getByText('5 event(s)')).toBeTruthy();
    expect(within(familyMixSection as HTMLElement).getByText(/50% of total/)).toBeTruthy();
  });

  it('exports activity family mix report CSVs for current and previous windows', async () => {
    renderPage();

    const familyMixSection = (await screen.findByRole('heading', { name: 'RM Activity Family Mix' })).closest('.MuiCard-root');
    expect(familyMixSection).toBeTruthy();

    const effectiveDays = 28;
    const endDate = new Date();
    const currentStartDate = new Date(endDate);
    currentStartDate.setDate(currentStartDate.getDate() - (effectiveDays - 1));
    const previousEndDate = new Date(currentStartDate);
    previousEndDate.setDate(previousEndDate.getDate() - 1);
    const previousStartDate = new Date(previousEndDate);
    previousStartDate.setDate(previousStartDate.getDate() - (effectiveDays - 1));
    const formatLocalDay = (date: Date) => {
      const year = date.getFullYear();
      const month = `${date.getMonth() + 1}`.padStart(2, '0');
      const day = `${date.getDate()}`.padStart(2, '0');
      return `${year}-${month}-${day}`;
    };
    const formatRangeBoundary = (date: Date, endOfDay = false) =>
      `${formatLocalDay(date)}T${endOfDay ? '23:59:59' : '00:00:00'}`;

    fireEvent.click(await within(familyMixSection as HTMLElement).findByRole('button', { name: 'Export current CSV' }));

    await waitFor(() => expect(mockedRecordsManagementService.exportActivityFamilyReportCsv).toHaveBeenCalledWith({
      from: formatRangeBoundary(currentStartDate),
      to: formatRangeBoundary(endDate, true),
    }));
    expect(toastSuccessMock).toHaveBeenCalledWith('Activity family current window CSV exported');

    fireEvent.click(await within(familyMixSection as HTMLElement).findByRole('button', { name: 'Export previous CSV' }));

    await waitFor(() => expect(mockedRecordsManagementService.exportActivityFamilyReportCsv).toHaveBeenLastCalledWith({
      from: formatRangeBoundary(previousStartDate),
      to: formatRangeBoundary(previousEndDate, true),
    }));
    expect(toastSuccessMock).toHaveBeenCalledWith('Activity family previous window CSV exported');
  });

  it('drills from family mix into the existing audit table with family and range', async () => {
    renderPage();

    const familyMixSection = (await screen.findByRole('heading', { name: 'RM Activity Family Mix' })).closest('.MuiCard-root');
    expect(familyMixSection).toBeTruthy();

    fireEvent.click(within(familyMixSection as HTMLElement).getAllByRole('button', { name: 'Review family audit' })[1]);

    await waitFor(() => {
      const lastCall = mockedRecordsManagementService.listAudit.mock.calls.at(-1)?.[0];
      expect(lastCall.family).toBe('OTHER');
      expect(lastCall.username).toBe('');
      expect(lastCall.eventType).toBe('');
      expect(lastCall.from).toBeTruthy();
      expect(lastCall.to).toBeTruthy();
      expect(lastCall.page).toBe(0);
      expect(lastCall.size).toBe(10);
    });

    expect(screen.getByRole('combobox', { name: 'Family' }).textContent).toContain('Other');
    expect(screen.getByText(/Reviewing audit evidence for Activity family Other/)).toBeTruthy();
  });

  it('renders RM activity family highlights from audit-backed comparison data', async () => {
    renderPage();

    const familyHighlightsSection = (await screen.findByRole('heading', { name: 'RM Activity Family Highlights' })).closest('.MuiCard-root');
    expect(familyHighlightsSection).toBeTruthy();
    expect(within(familyHighlightsSection as HTMLElement).getByText('Current 5')).toBeTruthy();
    expect(within(familyHighlightsSection as HTMLElement).getByText('Previous 2')).toBeTruthy();
    expect(within(familyHighlightsSection as HTMLElement).getByText(/\+3 vs previous window/)).toBeTruthy();
  });

  it('drills from family highlights into the existing audit table with family and current window range', async () => {
    renderPage();

    const familyHighlightsSection = (await screen.findByRole('heading', { name: 'RM Activity Family Highlights' })).closest('.MuiCard-root');
    expect(familyHighlightsSection).toBeTruthy();

    fireEvent.click(within(familyHighlightsSection as HTMLElement).getAllByRole('button', { name: 'Review current audit' })[1]);

    await waitFor(() => {
      const lastCall = mockedRecordsManagementService.listAudit.mock.calls.at(-1)?.[0];
      expect(lastCall.family).toBe('OTHER');
      expect(lastCall.username).toBe('');
      expect(lastCall.eventType).toBe('');
      expect(lastCall.from).toBe('2026-04-09T00:00:00');
      expect(lastCall.to).toBe('2026-04-15T23:59:59');
      expect(lastCall.page).toBe(0);
      expect(lastCall.size).toBe(10);
    });

    expect(screen.getByText(/Reviewing audit evidence for Family Other · Current window/)).toBeTruthy();
  });

  it('drills from event hotspots into the existing audit table with eventType and range', async () => {
    renderPage();

    const hotspotsSection = (await screen.findByRole('heading', { name: 'RM Activity Event Hotspots' })).closest('.MuiCard-root');
    expect(hotspotsSection).toBeTruthy();

    fireEvent.click(within(hotspotsSection as HTMLElement).getAllByRole('button', { name: 'Review event audit' })[0]);

    await waitFor(() => {
      const lastCall = mockedRecordsManagementService.listAudit.mock.calls.at(-1)?.[0];
      expect(lastCall.eventType).toBe('RM_RECORD_DECLARED');
      expect(lastCall.username).toBe('');
      expect(lastCall.from).toBeTruthy();
      expect(lastCall.to).toBeTruthy();
      expect(lastCall.page).toBe(0);
      expect(lastCall.size).toBe(10);
    });

    expect((screen.getByLabelText('Event Type') as HTMLInputElement).value).toBe('RM_RECORD_DECLARED');
    expect(screen.getByText(/Reviewing audit evidence for Event Record Declared/)).toBeTruthy();
  });

  it('keeps core RM surfaces available when activity family mix fails', async () => {
    mockedRecordsManagementService.getActivityFamilies.mockRejectedValueOnce(new Error('family mix down'));

    renderPage();

    expect(await screen.findByRole('heading', { name: 'Records Management' })).toBeTruthy();
    expect(await screen.findByText('Failed to load RM activity family mix')).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Governance Health' })).toBeTruthy();
    expect(screen.getByText('HR File Plan')).toBeTruthy();
    expect(screen.getByText('/Sites/hr/Employee Contract.pdf')).toBeTruthy();
  });

  it('keeps core RM surfaces available when activity family highlights fail', async () => {
    mockedRecordsManagementService.getActivityFamilyHighlights.mockRejectedValueOnce(new Error('family highlights down'));

    renderPage();

    expect(await screen.findByRole('heading', { name: 'Records Management' })).toBeTruthy();
    expect(await screen.findByText('Failed to load RM activity family highlights')).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Governance Health' })).toBeTruthy();
    expect(screen.getByText('HR File Plan')).toBeTruthy();
    expect(screen.getByText('/Sites/hr/Employee Contract.pdf')).toBeTruthy();
  });

  it('keeps core RM surfaces available when activity event hotspots fail', async () => {
    mockedRecordsManagementService.getActivityEventTypes.mockRejectedValueOnce(new Error('event hotspots down'));

    renderPage();

    expect(await screen.findByRole('heading', { name: 'Records Management' })).toBeTruthy();
    expect(await screen.findByText('Failed to load RM activity event hotspots')).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Governance Health' })).toBeTruthy();
    expect(screen.getByText('HR File Plan')).toBeTruthy();
    expect(screen.getByText('/Sites/hr/Employee Contract.pdf')).toBeTruthy();
  });

  it('keeps core RM surfaces available when activity highlights fail', async () => {
    mockedRecordsManagementService.getActivityHighlights.mockRejectedValueOnce(new Error('highlights down'));

    renderPage();

    expect(await screen.findByRole('heading', { name: 'Records Management' })).toBeTruthy();
    expect(await screen.findByText('Failed to load RM activity highlights')).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Governance Health' })).toBeTruthy();
    expect(screen.getByText('HR File Plan')).toBeTruthy();
    expect(screen.getByText('/Sites/hr/Employee Contract.pdf')).toBeTruthy();
  });

  it('keeps core RM surfaces available when activity breakdown fails', async () => {
    mockedRecordsManagementService.getActivityBreakdown.mockRejectedValueOnce(new Error('breakdown down'));

    renderPage();

    expect(await screen.findByRole('heading', { name: 'Records Management' })).toBeTruthy();
    expect(await screen.findByText('Failed to load RM activity breakdown')).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Governance Health' })).toBeTruthy();
    expect(screen.getByText('HR File Plan')).toBeTruthy();
    expect(screen.getByText('/Sites/hr/Employee Contract.pdf')).toBeTruthy();
  });

  it('keeps core RM surfaces available when contributor event-type highlights fail', async () => {
    mockedRecordsManagementService.getActivityContributorEventTypeHighlights.mockRejectedValueOnce(new Error('highlights down'));

    renderPage();

    expect(await screen.findByRole('heading', { name: 'Records Management' })).toBeTruthy();
    expect(await screen.findByText('Failed to load RM contributor event-type highlights')).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'RM Activity Contributors' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Governance Health' })).toBeTruthy();
    expect(screen.getByText('HR File Plan')).toBeTruthy();
  });

  it('keeps core RM surfaces available when activity contributors fail', async () => {
    mockedRecordsManagementService.getActivityContributors.mockRejectedValueOnce(new Error('contributors down'));

    renderPage();

    expect(await screen.findByRole('heading', { name: 'Records Management' })).toBeTruthy();
    expect(await screen.findByText('Failed to load RM activity contributors')).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Governance Health' })).toBeTruthy();
    expect(screen.getByText('HR File Plan')).toBeTruthy();
    expect(screen.getByText('/Sites/hr/Employee Contract.pdf')).toBeTruthy();
  });

  it('keeps core RM surfaces available when contributor event-type trend fails', async () => {
    mockedRecordsManagementService.getActivityContributorEventTypeTrend.mockRejectedValueOnce(new Error('trend down'));

    renderPage();

    expect(await screen.findByRole('heading', { name: 'Records Management' })).toBeTruthy();
    expect(await screen.findByText('Failed to load RM contributor event-type trend')).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'RM Activity Contributors' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Governance Health' })).toBeTruthy();
    expect(screen.getByText('HR File Plan')).toBeTruthy();
  });

  it('keeps core RM surfaces available when activity timeline fails', async () => {
    mockedRecordsManagementService.getActivityTimeline.mockRejectedValueOnce(new Error('timeline down'));

    renderPage();

    expect(await screen.findByRole('heading', { name: 'Records Management' })).toBeTruthy();
    expect(await screen.findByText('Failed to load RM activity timeline')).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Governance Health' })).toBeTruthy();
    expect(screen.getByText('HR File Plan')).toBeTruthy();
    expect(screen.getByText('/Sites/hr/Employee Contract.pdf')).toBeTruthy();
  });

  it('filters declared records into uncategorized and categorized queues', async () => {
    renderPage();

    expect(await screen.findByText('/Sites/hr/Employee Contract.pdf')).toBeTruthy();
    expect(screen.getByText('/Company Home/Finance File Plan/Finance Policy.pdf')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Categorized · 1' }));

    expect(await screen.findByText('Showing 1 of 2 declared record(s).')).toBeTruthy();
    expect(screen.queryByText('/Sites/hr/Employee Contract.pdf')).toBeNull();
    expect(screen.getByText('/Company Home/Finance File Plan/Finance Policy.pdf')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Uncategorized · 1' }));

    expect(await screen.findByText('/Sites/hr/Employee Contract.pdf')).toBeTruthy();
    expect(screen.queryByText('/Company Home/Finance File Plan/Finance Policy.pdf')).toBeNull();
  });

  it('opens the uncategorized review queue from governance health', async () => {
    renderPage();

    expect(await screen.findByText('/Sites/hr/Employee Contract.pdf')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Review queue' }));

    expect(await screen.findByText('Showing 1 of 2 declared record(s).')).toBeTruthy();
    expect(screen.getByText('/Sites/hr/Employee Contract.pdf')).toBeTruthy();
    expect(screen.queryByText('/Company Home/Finance File Plan/Finance Policy.pdf')).toBeNull();
  });

  it('filters declared records outside file plan coverage and opens that queue from governance health', async () => {
    renderPage();

    expect(await screen.findByText('/Sites/hr/Employee Contract.pdf')).toBeTruthy();
    expect(screen.getByText('/Company Home/Finance File Plan/Finance Policy.pdf')).toBeTruthy();

    expect(screen.getAllByText('Outside File Plan').length).toBeGreaterThan(0);
    fireEvent.click(screen.getByRole('button', { name: 'Outside File Plan · 1' }));

    expect(await screen.findByText('Showing 1 of 2 declared record(s).')).toBeTruthy();
    expect(screen.getByText('/Sites/hr/Employee Contract.pdf')).toBeTruthy();
    expect(screen.queryByText('/Company Home/Finance File Plan/Finance Policy.pdf')).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'Review coverage' }));

    expect(await screen.findByText('Showing 1 of 2 declared record(s).')).toBeTruthy();
    expect(screen.getByText('/Sites/hr/Employee Contract.pdf')).toBeTruthy();
    expect(screen.queryByText('/Company Home/Finance File Plan/Finance Policy.pdf')).toBeNull();
  });

  it('filters recent governed imports into failed queue from governance health', async () => {
    renderPage();

    expect(await screen.findByText('Showing all 2 recent governed import job(s).')).toBeTruthy();
    expect(screen.getByText('/Sites/hr')).toBeTruthy();

    const reviewButtons = await screen.findAllByRole('button', { name: 'Review recent failures' });
    fireEvent.click(reviewButtons[0]);

    expect(await screen.findByText('Showing 1 of 2 recent governed import job(s).')).toBeTruthy();
    expect(screen.getByText('/Sites/hr')).toBeTruthy();
    expect(screen.getByText('1/3 imported · 0 skipped · 2 failed')).toBeTruthy();
    expect(screen.queryByText('8/12 imported · 2 skipped · 1 failed')).toBeNull();
  });

  it('filters recent governed imports by exact status breakdown and clears that status', async () => {
    renderPage();

    expect(await screen.findByText('Showing all 2 recent governed import job(s).')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'FAILED · 1' }));

    expect(await screen.findByText('Selected status FAILED. Matched 1 of 2 recent governed import job(s).')).toBeTruthy();
    expect(screen.getByText('1/3 imported · 0 skipped · 2 failed')).toBeTruthy();
    expect(screen.queryByText('8/12 imported · 2 skipped · 1 failed')).toBeNull();
    expect(screen.getByText('Status: FAILED')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Clear import status' }));

    expect(await screen.findByText('Showing all 2 recent governed import job(s).')).toBeTruthy();
    expect(screen.queryByText('Selected status FAILED. Matched 1 of 2 recent governed import job(s).')).toBeNull();
    expect(screen.getByText('8/12 imported · 2 skipped · 1 failed')).toBeTruthy();
  });

  it('filters recent governed transfers into failed queue from governance health', async () => {
    renderPage();

    expect(await screen.findByText('/Archive/HR')).toBeTruthy();
    expect(screen.getByText('/Archive/Shared')).toBeTruthy();

    const reviewButtons = await screen.findAllByRole('button', { name: 'Review recent failures' });
    fireEvent.click(reviewButtons[1]);

    expect(await screen.findByText('Showing 1 of 2 recent governed transfer job(s).')).toBeTruthy();
    expect(screen.getByText('/Archive/Shared')).toBeTruthy();
    expect(screen.queryByText('/Archive/HR')).toBeNull();
  });

  it('filters recent governed transfers by exact status breakdown', async () => {
    renderPage();

    expect(await screen.findByText('Showing all 2 recent governed transfer job(s).')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'FAILED / DISCONNECTED · 1' }));

    expect(await screen.findByText('Selected status FAILED / DISCONNECTED. Matched 1 of 2 recent governed transfer job(s).')).toBeTruthy();
    expect(screen.getByText('/Archive/Shared')).toBeTruthy();
    expect(screen.queryByText('/Archive/HR')).toBeNull();
    expect(screen.getByText('Status: FAILED / DISCONNECTED')).toBeTruthy();
  });

  it('filters recent governed imports by governance reason', async () => {
    renderPage();

    expect(await screen.findByText('Showing all 2 recent governed import job(s).')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'TARGET_FILE_PLAN · 1' }));

    expect(await screen.findByText('Selected reason TARGET_FILE_PLAN. Matched 1 of 2 recent governed import job(s).')).toBeTruthy();
    expect(screen.getByText('8/12 imported · 2 skipped · 1 failed')).toBeTruthy();
    expect(screen.queryByText('/Sites/hr')).toBeNull();
    expect(screen.getByText('Reason: TARGET_FILE_PLAN')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Clear import reason' }));

    expect(await screen.findByText('Showing all 2 recent governed import job(s).')).toBeTruthy();
    expect(screen.queryByText('Selected reason TARGET_FILE_PLAN. Matched 1 of 2 recent governed import job(s).')).toBeNull();
    expect(screen.getByText('/Sites/hr')).toBeTruthy();
  });

  it('filters recent governed transfers by governance reason', async () => {
    renderPage();

    expect(await screen.findByText('Showing all 2 recent governed transfer job(s).')).toBeTruthy();
    fireEvent.click(screen.getAllByRole('button', { name: 'TARGET_OUTSIDE_FILE_PLAN · 1' })[1]);

    expect(await screen.findByText('Selected reason TARGET_OUTSIDE_FILE_PLAN. Matched 1 of 2 recent governed transfer job(s).')).toBeTruthy();
    expect(screen.getByText('/Archive/Shared')).toBeTruthy();
    expect(screen.queryByText('/Archive/HR')).toBeNull();
    expect(screen.getByText('Reason: TARGET_OUTSIDE_FILE_PLAN')).toBeTruthy();
  });

  it('summarizes selected operations filters and clears them by scope', async () => {
    renderPage();

    expect(await screen.findByText('Showing all 2 recent governed import job(s).')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'FAILED · 1' }));
    fireEvent.click(screen.getAllByRole('button', { name: 'TARGET_OUTSIDE_FILE_PLAN · 1' })[1]);

    expect(await screen.findByText('Selected operations filters')).toBeTruthy();
    expect(screen.getByText('Import: FAILED')).toBeTruthy();
    expect(screen.getByText('Import matches 1/2')).toBeTruthy();
    expect(screen.getByText('Transfer: TARGET_OUTSIDE_FILE_PLAN')).toBeTruthy();
    expect(screen.getByText('Transfer matches 1/2')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Clear transfer filters' }));

    expect(await screen.findByText('Import: FAILED')).toBeTruthy();
    expect(screen.getByText('Import matches 1/2')).toBeTruthy();
    expect(screen.queryByText('Transfer: TARGET_OUTSIDE_FILE_PLAN')).toBeNull();
    expect(screen.queryByText('Transfer matches 1/2')).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'Clear all filters' }));

    expect(screen.queryByText('Selected operations filters')).toBeNull();
    expect(await screen.findByText('Showing all 2 recent governed import job(s).')).toBeTruthy();
    expect(screen.getByText('Showing all 2 recent governed transfer job(s).')).toBeTruthy();
  });

  it('shows zero-match summary for scoped import filters with no matching jobs', async () => {
    renderPage();

    expect(await screen.findByText('Showing all 2 recent governed import job(s).')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'FAILED · 1' }));
    fireEvent.click(screen.getByRole('button', { name: 'TARGET_FILE_PLAN · 1' }));

    expect(await screen.findByText('Import: FAILED · TARGET_FILE_PLAN')).toBeTruthy();
    expect(screen.getByText('Import matches 0/2')).toBeTruthy();
    expect(screen.getByText('No recent governed import jobs match the current filter. Current import filters matched 0 of 2 recent job(s).')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Show all imports' }));

    expect(await screen.findByText('Showing all 2 recent governed import job(s).')).toBeTruthy();
    expect(screen.getByText('8/12 imported · 2 skipped · 1 failed')).toBeTruthy();
    expect(screen.getByText('1/3 imported · 0 skipped · 2 failed')).toBeTruthy();
    expect(screen.queryByText('Import: FAILED · TARGET_FILE_PLAN')).toBeNull();
    expect(screen.queryByText('Import matches 0/2')).toBeNull();
  });

  it('shows zero-match summary for scoped transfer filters with recover CTA', async () => {
    renderPage();

    expect(await screen.findByText('Showing all 2 recent governed transfer job(s).')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'RUNNING / CONNECTED · 1' }));
    fireEvent.click(screen.getAllByRole('button', { name: 'TARGET_OUTSIDE_FILE_PLAN · 1' })[1]);

    expect(await screen.findByText('Transfer: RUNNING / CONNECTED · TARGET_OUTSIDE_FILE_PLAN')).toBeTruthy();
    expect(screen.getByText('Transfer matches 0/2')).toBeTruthy();
    expect(screen.getByText('No recent governed transfer jobs match the current filter. Current transfer filters matched 0 of 2 recent job(s).')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Show all transfers' }));

    expect(await screen.findByText('Showing all 2 recent governed transfer job(s).')).toBeTruthy();
    expect(screen.getByText('/Archive/HR')).toBeTruthy();
    expect(screen.getByText('/Archive/Shared')).toBeTruthy();
    expect(screen.queryByText('Transfer: RUNNING / CONNECTED · TARGET_OUTSIDE_FILE_PLAN')).toBeNull();
    expect(screen.queryByText('Transfer matches 0/2')).toBeNull();
  });

  it('creates a file plan and assigns a record category', async () => {
    renderPage();

    await screen.findByText('HR File Plan');

    fireEvent.change(screen.getByRole('textbox', { name: 'File Plan Name' }), {
      target: { value: '  Finance File Plan  ' },
    });
    fireEvent.change(screen.getByRole('textbox', { name: 'File Plan Description' }), {
      target: { value: '  Finance governance  ' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Create File Plan' }));

    await waitFor(() => {
      expect(mockedRecordsManagementService.createFilePlan).toHaveBeenCalledWith({
        name: '  Finance File Plan  ',
        description: '  Finance governance  ',
        parentId: undefined,
      });
    });

    const recordRow = screen.getByText('/Sites/hr/Employee Contract.pdf').closest('tr');
    expect(recordRow).toBeTruthy();
    const categorySelect = within(recordRow as HTMLTableRowElement).getByRole('combobox', { name: 'Record Category' });
    fireEvent.mouseDown(categorySelect);
    fireEvent.click(await screen.findByRole('option', { name: '/Records Management/Contracts' }));
    fireEvent.click(within(recordRow as HTMLTableRowElement).getByRole('button', { name: 'Assign' }));

    await waitFor(() => {
      expect(mockedRecordsManagementService.assignRecordCategory).toHaveBeenCalledWith('record-1', 'cat-1');
    });
    expect(toastSuccessMock).toHaveBeenCalledWith('File plan created');
    expect(toastSuccessMock).toHaveBeenCalledWith('Record category assigned');
  });

  it('edits and deletes file plans from the management table', async () => {
    const confirmSpy = jest.spyOn(window, 'confirm').mockReturnValue(true);
    renderPage();

    await screen.findByText('HR File Plan');

    const filePlanRow = screen.getByText('HR File Plan').closest('tr');
    expect(filePlanRow).toBeTruthy();

    fireEvent.click(within(filePlanRow as HTMLTableRowElement).getByRole('button', { name: 'Edit' }));
    expect((screen.getByRole('textbox', { name: 'File Plan Name' }) as HTMLInputElement).disabled).toBe(true);
    expect(screen.getByRole('combobox', { name: 'Parent File Plan' }).getAttribute('aria-disabled')).toBe('true');
    const descriptionInput = screen.getByRole('textbox', { name: 'File Plan Description' });
    fireEvent.change(descriptionInput, {
      target: { value: '  Updated HR governance  ' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save File Plan' }));

    await waitFor(() => {
      expect(mockedRecordsManagementService.updateFilePlan).toHaveBeenCalledWith('plan-1', {
        description: '  Updated HR governance  ',
      });
    });

    fireEvent.click(within(filePlanRow as HTMLTableRowElement).getByRole('button', { name: 'Delete' }));

    await waitFor(() => {
      expect(mockedRecordsManagementService.deleteFilePlan).toHaveBeenCalledWith('plan-1');
    });

    expect(confirmSpy).toHaveBeenCalled();
    expect(toastSuccessMock).toHaveBeenCalledWith('File plan updated');
    expect(toastSuccessMock).toHaveBeenCalledWith('File plan deleted');
    confirmSpy.mockRestore();
  });

  it('renames a file plan from the management table', async () => {
    renderPage();

    await screen.findByText('HR File Plan');

    const filePlanRow = screen.getByText('HR File Plan').closest('tr');
    expect(filePlanRow).toBeTruthy();

    fireEvent.click(within(filePlanRow as HTMLTableRowElement).getByRole('button', { name: 'Rename' }));

    expect(await screen.findByRole('dialog', { name: 'Rename File Plan' })).toBeTruthy();
    fireEvent.change(screen.getByRole('textbox', { name: 'File Plan Name' }), {
      target: { value: '  People File Plan  ' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Rename File Plan' }));

    await waitFor(() => {
      expect(mockedRecordsManagementService.renameFilePlan).toHaveBeenCalledWith('plan-1', {
        name: 'People File Plan',
      });
    });

    expect(toastSuccessMock).toHaveBeenCalledWith('File plan renamed');
  });

  it('moves a file plan from the management table using alternate file plan targets only', async () => {
    renderPage();

    await screen.findByText('Contracts File Plan');

    const filePlanRow = screen.getByText('Contracts File Plan').closest('tr');
    expect(filePlanRow).toBeTruthy();

    fireEvent.click(within(filePlanRow as HTMLTableRowElement).getByRole('button', { name: 'Move' }));

    expect(await screen.findByRole('dialog', { name: 'Move File Plan' })).toBeTruthy();
    expect((screen.getByRole('button', { name: 'Move File Plan' }) as HTMLButtonElement).disabled).toBe(true);
    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'New Parent File Plan' }));
    expect(screen.queryByRole('option', { name: '/Company Home/HR File Plan' })).toBeNull();
    expect(screen.queryByRole('option', { name: '/Company Home/HR File Plan/Contracts File Plan' })).toBeNull();
    fireEvent.click(await screen.findByRole('option', { name: '/Company Home/Finance File Plan' }));
    fireEvent.click(screen.getByRole('button', { name: 'Move File Plan' }));

    await waitFor(() => {
      expect(mockedRecordsManagementService.moveFilePlan).toHaveBeenCalledWith('plan-3', {
        targetParentId: 'plan-2',
      });
    });

    expect(toastSuccessMock).toHaveBeenCalledWith('File plan moved');
  });

  it('edits and deletes non-root record categories while protecting the root', async () => {
    const confirmSpy = jest.spyOn(window, 'confirm').mockReturnValue(true);
    renderPage();

    await screen.findByText('/Records Management/Contracts');
    expect(screen.getByText('Protected')).toBeTruthy();

    const categoryRow = screen.getByText('/Records Management/Contracts').closest('tr');
    expect(categoryRow).toBeTruthy();

    fireEvent.click(within(categoryRow as HTMLTableRowElement).getByRole('button', { name: 'Edit' }));
    const descriptionInput = screen.getByRole('textbox', { name: 'Category Description' });
    fireEvent.change(descriptionInput, {
      target: { value: '  Updated contracts  ' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save Category' }));

    await waitFor(() => {
      expect(mockedRecordsManagementService.updateRecordCategory).toHaveBeenCalledWith('cat-1', {
        description: '  Updated contracts  ',
      });
    });

    fireEvent.click(within(categoryRow as HTMLTableRowElement).getByRole('button', { name: 'Delete' }));

    await waitFor(() => {
      expect(mockedRecordsManagementService.deleteRecordCategory).toHaveBeenCalledWith('cat-1');
    });

    expect(confirmSpy).toHaveBeenCalled();
    expect(toastSuccessMock).toHaveBeenCalledWith('Record category updated');
    expect(toastSuccessMock).toHaveBeenCalledWith('Record category deleted');
    confirmSpy.mockRestore();
  });

  it('renames a record category from the management table', async () => {
    renderPage();

    await screen.findByText('/Records Management/Contracts');

    const categoryRow = screen.getByText('/Records Management/Contracts').closest('tr');
    expect(categoryRow).toBeTruthy();

    fireEvent.click(within(categoryRow as HTMLTableRowElement).getByRole('button', { name: 'Rename' }));

    expect(await screen.findByRole('dialog', { name: 'Rename Record Category' })).toBeTruthy();
    fireEvent.change(screen.getByRole('textbox', { name: 'Category Name' }), {
      target: { value: '  Agreements  ' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Rename Category' }));

    await waitFor(() => {
      expect(mockedRecordsManagementService.renameRecordCategory).toHaveBeenCalledWith('cat-1', {
        name: 'Agreements',
      });
    });

    expect(toastSuccessMock).toHaveBeenCalledWith('Record category renamed');
  });

  it('moves a record category from the management table', async () => {
    renderPage();

    await screen.findByText('/Records Management/Contracts');

    const categoryRow = screen.getByText('/Records Management/Contracts').closest('tr');
    expect(categoryRow).toBeTruthy();

    fireEvent.click(within(categoryRow as HTMLTableRowElement).getByRole('button', { name: 'Move' }));

    expect(await screen.findByRole('dialog', { name: 'Move Record Category' })).toBeTruthy();
    expect((screen.getByRole('button', { name: 'Move Category' }) as HTMLButtonElement).disabled).toBe(true);
    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'New Parent Category' }));
    expect(screen.queryByRole('option', { name: '/Records Management' })).toBeNull();
    fireEvent.click(await screen.findByRole('option', { name: '/Records Management/Finance' }));
    fireEvent.click(screen.getByRole('button', { name: 'Move Category' }));

    await waitFor(() => {
      expect(mockedRecordsManagementService.moveRecordCategory).toHaveBeenCalledWith('cat-1', {
        targetParentId: 'cat-2',
      });
    });

    expect(toastSuccessMock).toHaveBeenCalledWith('Record category moved');
  });

  it('undeclares a record from the declared records table', async () => {
    mockedRecordsManagementService.listRecords
      .mockResolvedValueOnce([
        {
          nodeId: 'record-1',
          name: 'Employee Contract',
          path: '/Sites/hr/Employee Contract.pdf',
          declaredAt: '2026-04-14T10:00:00',
        },
      ] as any)
      .mockResolvedValueOnce([] as any);

    renderPage();

    const recordRow = await screen.findByText('/Sites/hr/Employee Contract.pdf');
    const row = recordRow.closest('tr');
    expect(row).toBeTruthy();

    fireEvent.click(within(row as HTMLTableRowElement).getByRole('button', { name: 'Undeclare Record...' }));
    fireEvent.change(await screen.findByRole('textbox', { name: /Reason/i }), {
      target: { value: '  governance update  ' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Undeclare Record' }));

    await waitFor(() => {
      expect(mockedRecordsManagementService.undeclareRecord).toHaveBeenCalledWith('record-1', {
        reason: 'governance update',
      });
    });

    await waitFor(() => {
      expect(screen.queryByText('/Sites/hr/Employee Contract.pdf')).toBeNull();
    });
    expect(toastSuccessMock).toHaveBeenCalledWith('Document undeclared as record');
  });

  it('keeps core RM surfaces available when operations telemetry fails', async () => {
    mockedRecordsManagementService.getOperationsTelemetry.mockRejectedValueOnce(new Error('telemetry failed'));

    renderPage();

    expect(await screen.findByRole('heading', { name: 'Records Management' })).toBeTruthy();
    expect(await screen.findByRole('heading', { name: 'Governance Health' })).toBeTruthy();
    expect(await screen.findByText('Failed to load governed operations telemetry')).toBeTruthy();
    expect(await screen.findByText('HR File Plan')).toBeTruthy();
    expect(screen.getByText('/Sites/hr/Employee Contract.pdf')).toBeTruthy();
    expect(screen.getByText('/Company Home/Finance File Plan/Finance Policy.pdf')).toBeTruthy();
    expect(screen.getAllByText('RM_RECORD_DECLARED').length).toBeGreaterThan(0);
  });
});
