import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import FailureInventoryCard, { FailureInventorySummary } from './FailureInventoryCard';
import apiService from '../../services/api';

jest.mock('../../services/api', () => ({
  __esModule: true,
  default: { get: jest.fn() },
}));
jest.mock('react-toastify', () => ({ toast: { warn: jest.fn() } }));

const mockedGet = apiService.get as jest.Mock;

const summary: FailureInventorySummary = {
  preview: {
    available: true,
    deadLetterCount: 5,
    categoryTally: { TIMEOUT: 3, UNKNOWN: 2 },
    latestFailedAt: '2026-06-29T12:00:00Z',
  },
  transfer: { available: true, failedCount: 4 },
  mail: { available: true, errorAccountCount: 2 },
};

const renderCard = () =>
  render(
    <MemoryRouter>
      <FailureInventoryCard />
    </MemoryRouter>,
  );

describe('FailureInventoryCard', () => {
  beforeEach(() => jest.clearAllMocks());

  it('renders the three failure panels with data + a category tally chip', async () => {
    mockedGet.mockResolvedValueOnce(summary);

    renderCard();

    expect(await screen.findByText('Preview dead-letters')).toBeTruthy();
    expect(screen.getByText('Transfer replication')).toBeTruthy();
    expect(screen.getByText('Mail fetch')).toBeTruthy();
    // category tally chip (non-PII)
    expect(screen.getByText('TIMEOUT: 3')).toBeTruthy();
    // links out to the deep surfaces
    expect(screen.getByText('Open preview diagnostics')).toBeTruthy();
    expect(screen.getByText('Open transfer jobs')).toBeTruthy();
    expect(screen.getByText('Open mail diagnostics')).toBeTruthy();
  });

  it('renders a per-subsystem "unavailable" note without breaking the other panels', async () => {
    mockedGet.mockResolvedValueOnce({
      ...summary,
      preview: { available: false, deadLetterCount: 0, categoryTally: {}, latestFailedAt: null },
    });

    renderCard();

    expect(await screen.findByText('Preview dead-letters')).toBeTruthy();
    expect(screen.getByText('unavailable')).toBeTruthy();
    // the other panels still render, and the deep-surface link is still offered
    expect(screen.getByText('Transfer replication')).toBeTruthy();
    expect(screen.getByText('Open preview diagnostics')).toBeTruthy();
  });

  it('isolates a fetch failure: shows a warning and does not render the panels or crash', async () => {
    mockedGet.mockRejectedValueOnce(new Error('boom'));

    renderCard();

    expect(await screen.findByText(/unavailable right now/i)).toBeTruthy();
    expect(screen.queryByText('Transfer replication')).toBeNull();
  });
});
