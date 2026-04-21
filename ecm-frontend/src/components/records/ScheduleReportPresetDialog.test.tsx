import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import ScheduleReportPresetDialog from './ScheduleReportPresetDialog';
import recordsManagementService from 'services/recordsManagementService';
import { RmReportPreset } from 'types';

jest.mock('services/recordsManagementService', () => ({
  __esModule: true,
  default: {
    getReportPresetSchedule: jest.fn(),
    updateReportPresetSchedule: jest.fn(),
    deliverReportPresetNow: jest.fn(),
    listReportPresetExecutions: jest.fn(),
  },
  supportsReportPresetCsvDelivery: jest.requireActual(
    '../../services/recordsManagementService'
  ).supportsReportPresetCsvDelivery,
}));

jest.mock('../browser/FolderTree', () => ({
  __esModule: true,
  default: ({ onNodeSelect, selectedNodeId }: any) => (
    <div data-testid="folder-tree-mock">
      <span data-testid="folder-tree-selected">{selectedNodeId || ''}</span>
      <button
        type="button"
        onClick={() =>
          onNodeSelect?.({ id: 'folder-42', name: 'Compliance Reports', nodeType: 'FOLDER' })
        }
      >
        Pick folder-42
      </button>
      <button
        type="button"
        onClick={() => onNodeSelect?.({ id: 'doc-9', name: 'not-a-folder', nodeType: 'DOCUMENT' })}
      >
        Pick non-folder
      </button>
    </div>
  ),
}));

const mockedService = recordsManagementService as jest.Mocked<typeof recordsManagementService>;

const makePreset = (overrides: Partial<RmReportPreset> = {}): RmReportPreset => ({
  id: 'preset-1',
  owner: 'alice',
  name: 'Weekly Family Report',
  kind: 'ACTIVITY_FAMILY_REPORT',
  params: { from: '2026-04-01T00:00:00', to: '2026-04-08T00:00:00' },
  ...overrides,
});

describe('ScheduleReportPresetDialog', () => {
  const openSpy = jest.spyOn(window, 'open').mockImplementation(() => null);

  beforeEach(() => {
    jest.clearAllMocks();
  });

  afterAll(() => {
    openSpy.mockRestore();
  });

  it('loads and displays existing schedule status when opened', async () => {
    mockedService.getReportPresetSchedule.mockResolvedValueOnce({
      presetId: 'preset-1',
      enabled: true,
      cronExpression: '0 9 * * MON-FRI',
      timezone: 'America/New_York',
      deliveryFolderId: 'folder-1',
      nextRunAt: '2026-04-22T13:00:00',
      lastRunAt: null,
      lastExecution: null,
    });
    mockedService.listReportPresetExecutions.mockResolvedValueOnce([]);

    render(
      <ScheduleReportPresetDialog open preset={makePreset()} onClose={jest.fn()} />
    );

    await waitFor(() => {
      expect(mockedService.getReportPresetSchedule).toHaveBeenCalledWith('preset-1');
    });
    expect(mockedService.listReportPresetExecutions).toHaveBeenCalledWith('preset-1', 5);
    expect(await screen.findByDisplayValue('0 9 * * MON-FRI')).not.toBeNull();
    expect(screen.getByTestId('folder-tree-selected').textContent).toBe('folder-1');
  });

  it('selects a folder via the folder tree and ignores non-folder nodes', async () => {
    mockedService.getReportPresetSchedule.mockResolvedValueOnce({
      presetId: 'preset-1',
      enabled: false,
      cronExpression: null,
      timezone: 'UTC',
      deliveryFolderId: null,
      nextRunAt: null,
      lastRunAt: null,
      lastExecution: null,
    });
    mockedService.listReportPresetExecutions.mockResolvedValueOnce([]);

    render(
      <ScheduleReportPresetDialog open preset={makePreset()} onClose={jest.fn()} />
    );

    await waitFor(() =>
      expect(
        (screen.getByRole('checkbox', {
          name: /enable scheduled delivery/i,
        }) as HTMLInputElement).disabled
      ).toBe(false)
    );

    fireEvent.click(screen.getByRole('button', { name: /pick non-folder/i }));
    expect(screen.getByTestId('folder-tree-selected').textContent).toBe('');

    fireEvent.click(screen.getByRole('button', { name: /pick folder-42/i }));
    expect(screen.getByTestId('folder-tree-selected').textContent).toBe('folder-42');
    expect(screen.getByText('Selected: Compliance Reports')).not.toBeNull();
  });

  it('rejects saving enabled schedule without cron expression', async () => {
    mockedService.getReportPresetSchedule.mockResolvedValueOnce({
      presetId: 'preset-1',
      enabled: false,
      cronExpression: null,
      timezone: 'UTC',
      deliveryFolderId: null,
      nextRunAt: null,
      lastRunAt: null,
      lastExecution: null,
    });
    mockedService.listReportPresetExecutions.mockResolvedValueOnce([]);

    render(
      <ScheduleReportPresetDialog open preset={makePreset()} onClose={jest.fn()} />
    );

    await waitFor(() =>
      expect(
        (screen.getByRole('checkbox', {
          name: /enable scheduled delivery/i,
        }) as HTMLInputElement).disabled
      ).toBe(false)
    );

    fireEvent.click(screen.getByRole('checkbox', { name: /enable scheduled delivery/i }));
    fireEvent.click(screen.getByRole('button', { name: /save schedule/i }));

    await waitFor(() => {
      expect(
        screen.getByText('Cron expression is required when schedule is enabled')
      ).not.toBeNull();
    });
    expect(mockedService.updateReportPresetSchedule).not.toHaveBeenCalled();
  });

  it('disables the schedule with a minimal request body', async () => {
    mockedService.getReportPresetSchedule
      .mockResolvedValueOnce({
        presetId: 'preset-1',
        enabled: true,
        cronExpression: '0 9 * * MON-FRI',
        timezone: 'UTC',
        deliveryFolderId: 'folder-1',
        nextRunAt: null,
        lastRunAt: null,
        lastExecution: null,
      })
      .mockResolvedValueOnce({
        presetId: 'preset-1',
        enabled: false,
        cronExpression: null,
        timezone: 'UTC',
        deliveryFolderId: 'folder-1',
        nextRunAt: null,
        lastRunAt: null,
        lastExecution: null,
      });
    mockedService.listReportPresetExecutions.mockResolvedValue([]);
    mockedService.updateReportPresetSchedule.mockResolvedValueOnce({
      presetId: 'preset-1',
      enabled: false,
      cronExpression: null,
      timezone: 'UTC',
      deliveryFolderId: 'folder-1',
      nextRunAt: null,
      lastRunAt: null,
      lastExecution: null,
    });

    const onSaved = jest.fn();
    render(
      <ScheduleReportPresetDialog open preset={makePreset()} onClose={jest.fn()} onSaved={onSaved} />
    );

    // Wait until the loaded schedule is reflected in the cron field before
    // interacting — the initial render has loading=true which disables inputs.
    await screen.findByDisplayValue('0 9 * * MON-FRI');

    fireEvent.click(screen.getByRole('checkbox', { name: /enable scheduled delivery/i }));
    fireEvent.click(screen.getByRole('button', { name: /save schedule/i }));

    await waitFor(() => {
      expect(mockedService.updateReportPresetSchedule).toHaveBeenCalledWith('preset-1', {
        enabled: false,
        timezone: 'UTC',
        deliveryFolderId: 'folder-1',
      });
    });
    expect(mockedService.getReportPresetSchedule).toHaveBeenCalledTimes(2);
    expect(onSaved).toHaveBeenCalled();
  });

  it('shows a disabled/non-CSV warning for summary-only kinds', async () => {
    render(
      <ScheduleReportPresetDialog
        open
        preset={makePreset({ kind: 'ACTIVITY_FAMILY_HIGHLIGHTS' })}
        onClose={jest.fn()}
      />
    );

    expect(await screen.findByText(/summary-only/i)).not.toBeNull();
    expect(mockedService.getReportPresetSchedule).not.toHaveBeenCalled();
    expect(screen.queryByRole('button', { name: /save schedule/i })).toBeNull();
  });

  it('delivers now and prepends the new execution to the list', async () => {
    mockedService.getReportPresetSchedule
      .mockResolvedValueOnce({
        presetId: 'preset-1',
        enabled: false,
        cronExpression: null,
        timezone: 'UTC',
        deliveryFolderId: 'folder-1',
        nextRunAt: null,
        lastRunAt: null,
        lastExecution: null,
      })
      .mockResolvedValueOnce({
        presetId: 'preset-1',
        enabled: false,
        cronExpression: null,
        timezone: 'UTC',
        deliveryFolderId: 'folder-1',
        nextRunAt: null,
        lastRunAt: '2026-04-21T01:00:01',
        lastExecution: {
          id: 'exec-1',
          presetId: 'preset-1',
          triggerType: 'MANUAL',
          status: 'SUCCESS',
          filename: 'Weekly-Family-Report-20260421.csv',
          targetFolderId: 'folder-1',
          documentId: 'doc-1',
          message: 'Delivered successfully',
          startedAt: '2026-04-21T01:00:00',
          finishedAt: '2026-04-21T01:00:01',
          durationMs: 1000,
        },
      });
    mockedService.listReportPresetExecutions
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([
        {
          id: 'exec-1',
          presetId: 'preset-1',
          triggerType: 'MANUAL',
          status: 'SUCCESS',
          filename: 'Weekly-Family-Report-20260421.csv',
          targetFolderId: 'folder-1',
          documentId: 'doc-1',
          message: 'Delivered successfully',
          startedAt: '2026-04-21T01:00:00',
          finishedAt: '2026-04-21T01:00:01',
          durationMs: 1000,
        },
      ]);
    mockedService.deliverReportPresetNow.mockResolvedValueOnce({
      id: 'exec-1',
      presetId: 'preset-1',
      triggerType: 'MANUAL',
      status: 'SUCCESS',
      filename: 'Weekly-Family-Report-20260421.csv',
      targetFolderId: 'folder-1',
      documentId: 'doc-1',
      message: 'Delivered successfully',
      startedAt: '2026-04-21T01:00:00',
      finishedAt: '2026-04-21T01:00:01',
      durationMs: 1000,
    });

    render(
      <ScheduleReportPresetDialog open preset={makePreset()} onClose={jest.fn()} />
    );

    // Wait for load to finish so the "Deliver now" button is not disabled.
    await waitFor(() =>
      expect(
        (screen.getByRole('button', { name: /deliver now/i }) as HTMLButtonElement).disabled
      ).toBe(false)
    );

    fireEvent.click(screen.getByRole('button', { name: /deliver now/i }));

    await waitFor(() => {
      expect(mockedService.deliverReportPresetNow).toHaveBeenCalledWith('preset-1');
    });
    expect(mockedService.getReportPresetSchedule).toHaveBeenCalledTimes(2);
    expect(mockedService.listReportPresetExecutions).toHaveBeenCalledTimes(2);
    expect(
      await screen.findByText('Weekly-Family-Report-20260421.csv')
    ).not.toBeNull();
    expect(await screen.findByText(/Last:/)).not.toBeNull();
  });

  it('filters execution history and opens delivered browse targets', async () => {
    mockedService.getReportPresetSchedule.mockResolvedValueOnce({
      presetId: 'preset-1',
      enabled: true,
      cronExpression: '0 9 * * MON-FRI',
      timezone: 'UTC',
      deliveryFolderId: 'folder-1',
      nextRunAt: '2026-04-22T09:00:00',
      lastRunAt: '2026-04-21T09:00:01',
      lastExecution: {
        id: 'exec-2',
        presetId: 'preset-1',
        triggerType: 'SCHEDULED',
        status: 'FAILED',
        filename: null,
        targetFolderId: 'folder-1',
        documentId: null,
        message: 'Folder permission denied',
        startedAt: '2026-04-21T09:00:00',
        finishedAt: '2026-04-21T09:00:01',
        durationMs: 1000,
      },
    });
    mockedService.listReportPresetExecutions.mockResolvedValueOnce([
      {
        id: 'exec-1',
        presetId: 'preset-1',
        triggerType: 'MANUAL',
        status: 'SUCCESS',
        filename: 'weekly-family-report.csv',
        targetFolderId: 'folder-1',
        documentId: 'doc-1',
        message: 'Delivered successfully',
        startedAt: '2026-04-21T08:00:00',
        finishedAt: '2026-04-21T08:00:01',
        durationMs: 1000,
      },
      {
        id: 'exec-2',
        presetId: 'preset-1',
        triggerType: 'SCHEDULED',
        status: 'FAILED',
        filename: null,
        targetFolderId: 'folder-1',
        documentId: null,
        message: 'Folder permission denied',
        startedAt: '2026-04-21T09:00:00',
        finishedAt: '2026-04-21T09:00:01',
        durationMs: 1000,
      },
    ]);

    render(
      <ScheduleReportPresetDialog open preset={makePreset()} onClose={jest.fn()} />
    );

    expect(await screen.findByText('Showing 2 of 2 deliveries.')).not.toBeNull();
    expect(screen.getByText('weekly-family-report.csv')).not.toBeNull();
    expect(screen.getByText('Folder permission denied')).not.toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'Failed' }));

    expect(await screen.findByText('Showing 1 of 2 deliveries.')).not.toBeNull();
    expect(screen.queryByText('weekly-family-report.csv')).toBeNull();
    expect(screen.getByText('Folder permission denied')).not.toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'Scheduled' }));
    expect(await screen.findByText('Showing 1 of 2 deliveries.')).not.toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'All results' }));
    fireEvent.click(screen.getByRole('button', { name: 'All triggers' }));

    const openFileButton = await screen.findByRole('button', { name: 'Open delivered file' });
    fireEvent.click(openFileButton);
    expect(openSpy).toHaveBeenCalledWith('/browse/doc-1', '_blank', 'noopener,noreferrer');

    fireEvent.click(screen.getAllByRole('button', { name: 'Open target folder' })[0]);
    expect(openSpy).toHaveBeenCalledWith('/browse/folder-1', '_blank', 'noopener,noreferrer');
  });
});
