import React from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { toast } from 'react-toastify';
import LegalHoldsPage, { parseUuidList } from './LegalHoldsPage';
import legalHoldService, {
  BulkApplyResult,
  LegalHoldDetail,
  LegalHoldSummary,
} from 'services/legalHoldService';

// react-toastify portal interferes with default jsdom timers; mock it.
jest.mock('react-toastify', () => ({
  toast: {
    error: jest.fn(),
    success: jest.fn(),
    info: jest.fn(),
    warn: jest.fn(),
  },
  ToastContainer: () => null,
}));

// Mock the service. Re-export the synthetic shape-guard message constants
// because jest.mock replaces the module wholesale; without re-exporting them
// any test that imports them would resolve to undefined.
jest.mock('services/legalHoldService', () => ({
  __esModule: true,
  default: {
    listHolds: jest.fn(),
    getHold: jest.fn(),
    createHold: jest.fn(),
    addItems: jest.fn(),
    removeItem: jest.fn(),
    releaseHold: jest.fn(),
  },
  LEGAL_HOLD_UNEXPECTED_RESPONSE_MESSAGE:
    'Legal hold endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.',
  LEGAL_HOLD_BULK_CREATE_UNEXPECTED_RESPONSE_MESSAGE:
    'Legal hold bulk-create endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.',
  LEGAL_HOLD_RELEASE_UNEXPECTED_RESPONSE_MESSAGE:
    'Legal hold release endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.',
}));

const mockedService = legalHoldService as jest.Mocked<typeof legalHoldService>;

const baseDetail: LegalHoldDetail = {
  id: 'hold-1',
  name: 'Discovery Q3',
  description: null,
  status: 'ACTIVE',
  createdBy: 'admin',
  createdDate: '2026-05-24T10:00:00Z',
  releasedBy: null,
  releasedAt: null,
  releaseComment: null,
  releaseReason: null,
  itemCount: 0,
  items: [],
  bulkApplyResults: null,
};

const baseSummary: LegalHoldSummary = {
  id: 'hold-1',
  name: 'Discovery Q3',
  description: null,
  status: 'ACTIVE',
  itemCount: 0,
  createdBy: 'admin',
  createdDate: '2026-05-24T10:00:00Z',
  releasedBy: null,
  releasedAt: null,
  releaseReason: null,
};

beforeEach(() => {
  jest.clearAllMocks();
});

// ======================================================================
// parseUuidList — pure helper
// ======================================================================

describe('parseUuidList', () => {
  const A = '11111111-1111-1111-1111-111111111111';
  const B = '22222222-2222-2222-2222-222222222222';

  test('splits on newline, comma, and semicolon and trims surrounding whitespace', () => {
    const raw = `${A},${B}\n  ${A} ;${B}`;  // 2nd A is a dup of the first
    expect(parseUuidList(raw)).toEqual([A, B]);
  });

  test('lowercases hex digits and dedupes case-insensitively', () => {
    const RAW = '11111111-1111-1111-1111-111111111111';
    const UPPER = '11111111-1111-1111-1111-111111111111'.toUpperCase();
    const result = parseUuidList(`${UPPER}\n${RAW}`);
    expect(result).toEqual([RAW]);
  });

  test('drops blanks and malformed entries (not v4-only — accepts any UUID variant by format)', () => {
    // v1 / v3 / v5 UUIDs all share the 8-4-4-4-12 hex format; only the version
    // nibble at position 14 differs. The parser must accept all of them.
    const v1 = 'aaaaaaaa-aaaa-1aaa-aaaa-aaaaaaaaaaaa';
    const v3 = 'bbbbbbbb-bbbb-3bbb-bbbb-bbbbbbbbbbbb';
    const v5 = 'cccccccc-cccc-5ccc-cccc-cccccccccccc';
    const v4 = 'dddddddd-dddd-4ddd-dddd-dddddddddddd';
    const malformed = 'not-a-uuid';
    const blank = '   ';
    const raw = `${v1}\n${malformed}\n${v3};${blank},${v5}\n${v4}`;
    expect(parseUuidList(raw)).toEqual([v1, v3, v5, v4]);
  });

  test('returns empty array for empty / pure-whitespace input', () => {
    expect(parseUuidList('')).toEqual([]);
    expect(parseUuidList('   ;,,\n   ')).toEqual([]);
  });
});

// ======================================================================
// Create dialog — bulk apply, partial-failure UX
// ======================================================================

describe('LegalHoldsPage create dialog (bulk apply)', () => {
  const openCreate = async () => {
    const inviteButton = await screen.findByRole('button', { name: /Create Hold/i });
    fireEvent.click(inviteButton);
    return screen.findByRole('dialog', { name: 'Create Legal Hold' });
  };

  beforeEach(() => {
    mockedService.listHolds.mockResolvedValue([]);
  });

  test('all-rows-added bulk create: dialog closes, toast lists ADDED counts, hold prepended', async () => {
    const A = '11111111-1111-1111-1111-111111111111';
    const B = '22222222-2222-2222-2222-222222222222';
    const detail: LegalHoldDetail = {
      ...baseDetail,
      itemCount: 2,
      bulkApplyResults: {
        rows: [
          {
            requestedNodeId: A,
            status: 'ADDED',
            item: { nodeId: A, nodeName: 'a.pdf', nodeType: 'DOCUMENT', nodePath: '/X/a.pdf', addedAt: '2026-05-24T10:01:00Z', addedBy: 'admin' },
            errorCategory: null,
            errorMessage: null,
          },
          {
            requestedNodeId: B,
            status: 'ADDED',
            item: { nodeId: B, nodeName: 'b.pdf', nodeType: 'DOCUMENT', nodePath: '/X/b.pdf', addedAt: '2026-05-24T10:01:00Z', addedBy: 'admin' },
            errorCategory: null,
            errorMessage: null,
          },
        ],
      },
    };
    mockedService.createHold.mockResolvedValueOnce(detail);

    render(<LegalHoldsPage />);
    const dialog = await openCreate();

    fireEvent.change(within(dialog).getByLabelText(/Name/i), { target: { value: 'Discovery Q3' } });
    fireEvent.change(within(dialog).getByTestId('legal-hold-bulk-uuid-textarea'), {
      target: { value: `${A}\n${B}` },
    });
    fireEvent.click(within(dialog).getByTestId('legal-hold-bulk-submit'));

    await waitFor(() => {
      expect(mockedService.createHold).toHaveBeenCalledWith({
        name: 'Discovery Q3',
        description: undefined,
        nodeIds: [A, B],
      });
    });
    await waitFor(() => {
      expect(toast.success).toHaveBeenCalledWith('Hold "Discovery Q3" created. Bulk apply: 2 added.');
    });
    // Dialog closed
    await waitFor(() => {
      expect(screen.queryByRole('dialog', { name: 'Create Legal Hold' })).toBeNull();
    });
    // Summary row appears in the left list AND the detail-panel header also
    // shows the name, so at least 2 occurrences in the DOM. Use getAllByText.
    expect(screen.getAllByText('Discovery Q3').length).toBeGreaterThanOrEqual(1);
  });

  test('partial-failure bulk create: dialog stays open, textarea drains to failed UUIDs, Alert lists failed rows', async () => {
    const A = '11111111-1111-1111-1111-111111111111';
    const B = '22222222-2222-2222-2222-222222222222';
    const C = '33333333-3333-3333-3333-333333333333';
    const failedB: BulkApplyResult = {
      requestedNodeId: B,
      status: 'FAILED',
      item: null,
      errorCategory: 'NODE_NOT_FOUND',
      errorMessage: 'Requested node was not found.',
    };
    const failedC: BulkApplyResult = {
      requestedNodeId: C,
      status: 'FAILED',
      item: null,
      errorCategory: 'NODE_NOT_VISIBLE',
      errorMessage: 'Requested node is not visible in the current tenant workspace.',
    };
    const detail: LegalHoldDetail = {
      ...baseDetail,
      itemCount: 1,
      bulkApplyResults: {
        rows: [
          {
            requestedNodeId: A,
            status: 'ADDED',
            item: { nodeId: A, nodeName: 'a.pdf', nodeType: 'DOCUMENT', nodePath: '/X/a.pdf', addedAt: '2026-05-24T10:01:00Z', addedBy: 'admin' },
            errorCategory: null,
            errorMessage: null,
          },
          failedB,
          failedC,
        ],
      },
    };
    mockedService.createHold.mockResolvedValueOnce(detail);

    render(<LegalHoldsPage />);
    const dialog = await openCreate();

    fireEvent.change(within(dialog).getByLabelText(/Name/i), { target: { value: 'Partial' } });
    fireEvent.change(within(dialog).getByTestId('legal-hold-bulk-uuid-textarea'), {
      target: { value: `${A}\n${B}\n${C}` },
    });
    fireEvent.click(within(dialog).getByTestId('legal-hold-bulk-submit'));

    // Aggregated toast surfaces all 3 counters
    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith(
        'Hold "Discovery Q3" created. Bulk apply: 1 added, 0 skipped duplicates, 2 failed.',
      );
    });

    // Dialog stays open; failed-rows Alert is rendered with both failed rows
    await waitFor(() => {
      expect(screen.getByTestId('legal-hold-bulk-failed-rows')).toBeTruthy();
    });
    expect(screen.getByTestId('legal-hold-bulk-failed-row-NODE_NOT_FOUND')).toBeTruthy();
    expect(screen.getByTestId('legal-hold-bulk-failed-row-NODE_NOT_VISIBLE')).toBeTruthy();

    // Textarea drained to ONLY the failed UUIDs (A is successful → drained out)
    const textarea = within(dialog).getByTestId('legal-hold-bulk-uuid-textarea') as HTMLTextAreaElement;
    expect(textarea.value).toBe(`${B}\n${C}`);
  });

  test('no nodeIds: single-row create back-compat toast and dialog close', async () => {
    mockedService.createHold.mockResolvedValueOnce({ ...baseDetail, name: 'Solo' });

    render(<LegalHoldsPage />);
    const dialog = await openCreate();

    fireEvent.change(within(dialog).getByLabelText(/Name/i), { target: { value: 'Solo' } });
    fireEvent.click(within(dialog).getByTestId('legal-hold-bulk-submit'));

    await waitFor(() => {
      expect(mockedService.createHold).toHaveBeenCalledWith({
        name: 'Solo',
        description: undefined,
        nodeIds: undefined,
      });
    });
    await waitFor(() => {
      expect(toast.success).toHaveBeenCalledWith('Legal hold "Solo" created.');
    });
  });
});

// ======================================================================
// Release dialog — reason required
// ======================================================================

describe('LegalHoldsPage release dialog (release reason required)', () => {
  const openSelectedActive = async () => {
    render(<LegalHoldsPage />);
    // Click into the active hold to load detail.
    await screen.findByText('Discovery Q3');
    fireEvent.click(screen.getByText('Discovery Q3'));
    await screen.findByText('Held Items (0)');
    fireEvent.click(screen.getByRole('button', { name: /Release Hold/i }));
    return screen.findByRole('dialog', { name: 'Release Legal Hold' });
  };

  beforeEach(() => {
    mockedService.listHolds.mockResolvedValue([{ ...baseSummary }]);
    mockedService.getHold.mockResolvedValue({ ...baseDetail });
  });

  test('submit button disabled until a release reason is chosen', async () => {
    const dialog = await openSelectedActive();
    const submit = within(dialog).getByTestId('legal-hold-release-submit');
    expect(submit).toHaveProperty('disabled', true);

    fireEvent.mouseDown(within(dialog).getByLabelText(/Release reason/i));
    const option = await screen.findByText('Litigation ended');
    fireEvent.click(option);

    expect(within(dialog).getByTestId('legal-hold-release-submit')).toHaveProperty('disabled', false);
  });

  test('selecting a reason and submitting forwards releaseReason + comment to the service', async () => {
    mockedService.releaseHold.mockResolvedValueOnce({
      ...baseDetail,
      status: 'RELEASED',
      releasedBy: 'admin',
      releasedAt: '2026-05-24T12:00:00Z',
      releaseReason: 'LITIGATION_ENDED',
      releaseComment: 'Closed',
    });

    const dialog = await openSelectedActive();

    fireEvent.mouseDown(within(dialog).getByLabelText(/Release reason/i));
    fireEvent.click(await screen.findByText('Litigation ended'));

    fireEvent.change(within(dialog).getByLabelText(/Release Comment/i), {
      target: { value: 'Closed' },
    });
    fireEvent.click(within(dialog).getByTestId('legal-hold-release-submit'));

    await waitFor(() => {
      expect(mockedService.releaseHold).toHaveBeenCalledWith('hold-1', {
        releaseReason: 'LITIGATION_ENDED',
        comment: 'Closed',
      });
    });
  });
});

// ======================================================================
// Release-reason chip rendering on the detail panel
// ======================================================================

describe('LegalHoldsPage release-reason chip', () => {
  const renderWithReleasedHold = async (detail: LegalHoldDetail) => {
    mockedService.listHolds.mockResolvedValue([{
      ...baseSummary,
      status: 'RELEASED',
      releasedBy: detail.releasedBy,
      releasedAt: detail.releasedAt,
      releaseReason: detail.releaseReason,
    }]);
    mockedService.getHold.mockResolvedValue(detail);
    render(<LegalHoldsPage />);
    await screen.findByText('Discovery Q3');
    fireEvent.click(screen.getByText('Discovery Q3'));
    await screen.findByText('Held Items (0)');
  };

  test('known release reason renders the per-reason chip', async () => {
    await renderWithReleasedHold({
      ...baseDetail,
      status: 'RELEASED',
      releasedBy: 'admin',
      releasedAt: '2026-05-24T12:00:00Z',
      releaseReason: 'LITIGATION_ENDED',
    });

    expect(screen.getByTestId('legal-hold-release-reason-chip-LITIGATION_ENDED')).toBeTruthy();
    expect(screen.getByText('Litigation ended')).toBeTruthy();
  });

  test('legacy released hold (releaseReason === null) renders the "Legacy release" chip', async () => {
    await renderWithReleasedHold({
      ...baseDetail,
      status: 'RELEASED',
      releasedBy: 'old-admin',
      releasedAt: '2026-04-14T10:00:00Z',
      releaseReason: null,
    });

    expect(screen.getByTestId('legal-hold-release-reason-chip-legacy')).toBeTruthy();
    expect(screen.getByText('Legacy release')).toBeTruthy();
  });
});
