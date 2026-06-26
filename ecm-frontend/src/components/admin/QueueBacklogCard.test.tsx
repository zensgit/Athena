import React from 'react';
import { render, screen } from '@testing-library/react';
import QueueBacklogCard, { QueueBacklogSummary } from './QueueBacklogCard';
import apiService from '../../services/api';

jest.mock('../../services/api', () => ({
  __esModule: true,
  default: { get: jest.fn() },
}));
jest.mock('react-toastify', () => ({ toast: { warn: jest.fn() } }));

const mockedGet = apiService.get as jest.Mock;

const summary: QueueBacklogSummary = {
  ocr: { available: true, pendingDepth: 7, oldestPendingAgeSeconds: 120 },
  mail: { available: true, lastSuccessAt: '2026-06-26T09:00:00', errorRate: 0.2, errors: 2, status: 'DEGRADED' },
  transfer: {
    available: true,
    pendingCount: 3,
    runningCount: 1,
    failedCount: 2,
    oldestPendingAgeSeconds: 1800,
    stuckRunningCount: 1,
    stuckThresholdMinutes: 60,
  },
};

describe('QueueBacklogCard', () => {
  beforeEach(() => jest.clearAllMocks());

  it('renders the three backlog panels with data', async () => {
    mockedGet.mockResolvedValueOnce(summary);

    render(<QueueBacklogCard />);

    expect(await screen.findByText('OCR')).toBeTruthy();
    expect(screen.getByText('Mail fetch')).toBeTruthy();
    expect(screen.getByText('Transfer replication')).toBeTruthy();
    // mail status chip
    expect(screen.getByText('DEGRADED')).toBeTruthy();
  });

  it('renders a per-subsystem "unavailable" note without breaking the other panels', async () => {
    mockedGet.mockResolvedValueOnce({
      ...summary,
      ocr: { available: false, pendingDepth: 0, oldestPendingAgeSeconds: null },
    });

    render(<QueueBacklogCard />);

    expect(await screen.findByText('OCR')).toBeTruthy();
    expect(screen.getByText('unavailable')).toBeTruthy();
    // the other panels still render
    expect(screen.getByText('Transfer replication')).toBeTruthy();
  });

  it('isolates a fetch failure: shows a warning and does not render the panels or crash', async () => {
    mockedGet.mockRejectedValueOnce(new Error('boom'));

    render(<QueueBacklogCard />);

    expect(await screen.findByText(/unavailable right now/i)).toBeTruthy();
    expect(screen.queryByText('Transfer replication')).toBeNull();
  });
});
