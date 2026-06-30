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
  ocr: { available: true, failedCount: 6, runningCount: 1 },
  mailProcessed: { available: true, errorCount: 9 },
};

const renderCard = () =>
  render(
    <MemoryRouter>
      <FailureInventoryCard />
    </MemoryRouter>,
  );

describe('FailureInventoryCard', () => {
  beforeEach(() => jest.clearAllMocks());

  it('renders the four failure panels with data + a category tally chip', async () => {
    mockedGet.mockResolvedValueOnce(summary);

    renderCard();

    expect(await screen.findByText('Preview dead-letters')).toBeTruthy();
    expect(screen.getByText('Transfer replication')).toBeTruthy();
    expect(screen.getByText('Mail')).toBeTruthy();
    expect(screen.getByText('OCR processing')).toBeTruthy();
    // both mail axes render: account-level (fetch) + per-message (processing)
    expect(screen.getByText(/Accounts in error/)).toBeTruthy();
    expect(screen.getByText(/Messages failed/)).toBeTruthy();
    // category tally chip (non-PII)
    expect(screen.getByText('TIMEOUT: 3')).toBeTruthy();
    // links out to the deep surfaces (OCR has none — it shows a caption instead)
    expect(screen.getByText('Open preview diagnostics')).toBeTruthy();
    expect(screen.getByText('Open transfer jobs')).toBeTruthy();
    expect(screen.getByText('Open mail diagnostics')).toBeTruthy();
    expect(screen.getByText(/Per-document OCR state/i)).toBeTruthy();
  });

  it('renders OCR FAILED + PROCESSING counts (no fake deep-surface link)', async () => {
    mockedGet.mockResolvedValueOnce(summary);

    renderCard();

    expect(await screen.findByText('OCR processing')).toBeTruthy();
    // count-only, index-first failed + processing values are surfaced
    expect(screen.getByText(/Failed:/)).toBeTruthy();
    expect(screen.getByText('6')).toBeTruthy();
    // OCR intentionally has no "Open ..." deep-surface link
    expect(screen.queryByText(/Open OCR/i)).toBeNull();
  });

  it('shows OCR unavailable in isolation without breaking peers', async () => {
    mockedGet.mockResolvedValueOnce({
      ...summary,
      ocr: { available: false, failedCount: 0, runningCount: 0 },
    });

    renderCard();

    expect(await screen.findByText('OCR processing')).toBeTruthy();
    expect(screen.getByText('unavailable')).toBeTruthy();
    // peers still render
    expect(screen.getByText('Mail')).toBeTruthy();
  });

  it('renders the per-message mail ERROR count, distinct from the account-level fetch count', async () => {
    mockedGet.mockResolvedValueOnce(summary);

    renderCard();

    expect(await screen.findByText(/Messages failed/)).toBeTruthy();
    expect(screen.getByText('9')).toBeTruthy(); // per-message errorCount
    expect(screen.getByText(/Accounts in error/)).toBeTruthy(); // account-level still shown
  });

  it('isolates a per-message mail failure without hiding the account-level line', async () => {
    mockedGet.mockResolvedValueOnce({
      ...summary,
      mailProcessed: { available: false, errorCount: 0 },
    });

    renderCard();

    expect(await screen.findByText(/Accounts in error/)).toBeTruthy();
    expect(screen.getByText('unavailable')).toBeTruthy();
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
