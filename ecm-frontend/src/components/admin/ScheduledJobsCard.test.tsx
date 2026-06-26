import React from 'react';
import { render, screen } from '@testing-library/react';
import ScheduledJobsCard, { SchedulerJobSnapshot } from './ScheduledJobsCard';
import apiService from '../../services/api';

jest.mock('../../services/api', () => ({
  __esModule: true,
  default: { get: jest.fn() },
}));
jest.mock('react-toastify', () => ({ toast: { warn: jest.fn() } }));

const mockedGet = apiService.get as jest.Mock;

describe('ScheduledJobsCard', () => {
  beforeEach(() => jest.clearAllMocks());

  it('renders one row per scheduled job with status and schedule', async () => {
    const rows: SchedulerJobSnapshot[] = [
      {
        jobId: 'com.ecm.core.service.TrashService#purgeOldTrashItems',
        lastRunAt: '2026-06-25T02:00:00',
        lastStatus: 'SUCCESS',
        lastDurationMs: 1200,
        lastErrorType: null,
        runCount: 5,
        failCount: 0,
        nextRunAt: '2026-06-26T02:00:00',
        scheduleDescription: 'cron: 0 0 2 * * *',
      },
      {
        jobId: 'com.ecm.core.integration.mail.service.MailFetcherService#fetchAllAccounts',
        lastRunAt: '2026-06-25T10:00:00',
        lastStatus: 'FAILED',
        lastDurationMs: 300,
        lastErrorType: 'MailConnectException',
        runCount: 40,
        failCount: 3,
        nextRunAt: null,
        scheduleDescription: 'fixedDelay=60000ms',
      },
    ];
    mockedGet.mockResolvedValueOnce(rows);

    render(<ScheduledJobsCard />);

    expect(await screen.findByText(/TrashService#purgeOldTrashItems/)).toBeTruthy();
    expect(screen.getByText('SUCCESS')).toBeTruthy();
    expect(screen.getByText('FAILED')).toBeTruthy();
    expect(screen.getByText('MailConnectException')).toBeTruthy();
    // a fixedDelay job (nextRunAt == null) shows the scheduleDescription in the Next-run column
    expect(screen.getByText('fixedDelay=60000ms')).toBeTruthy();
  });

  it('isolates a fetch failure: shows a warning and does not render the table or crash', async () => {
    mockedGet.mockRejectedValueOnce(new Error('boom'));

    render(<ScheduledJobsCard />);

    expect(await screen.findByText(/unavailable right now/i)).toBeTruthy();
    expect(screen.queryByRole('table')).toBeNull();
  });
});
