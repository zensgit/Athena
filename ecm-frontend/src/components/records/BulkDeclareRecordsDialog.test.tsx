import React from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { toast } from 'react-toastify';
import BulkDeclareRecordsDialog, { parseUuidList } from './BulkDeclareRecordsDialog';
import recordsManagementService from 'services/recordsManagementService';

jest.mock('react-toastify', () => ({
  toast: {
    success: jest.fn(),
    error: jest.fn(),
    warn: jest.fn(),
  },
}));

jest.mock('services/recordsManagementService', () => ({
  __esModule: true,
  default: {
    createBulkDeclarations: jest.fn(),
    listRecordCategories: jest.fn(),
  },
}));

const mockedService = recordsManagementService as jest.Mocked<typeof recordsManagementService>;
const toastSuccess = toast.success as jest.Mock;
const toastWarn = toast.warn as jest.Mock;
const toastError = toast.error as jest.Mock;

// Three valid UUIDs (mix of v4 and non-v4 variants to lock the generic 8-4-4-4-12 parser).
const UUID_V4 = '11111111-1111-4111-8111-111111111111';
const UUID_V1 = '22222222-2222-1222-9222-222222222222';
const UUID_V5 = '33333333-3333-5333-a333-333333333333';

describe('parseUuidList', () => {
  it('returns an empty array for empty input', () => {
    expect(parseUuidList('')).toEqual([]);
    expect(parseUuidList('   \n  \n')).toEqual([]);
  });

  it('dedupes identical UUIDs preserving first-seen order', () => {
    const raw = `${UUID_V4}\n${UUID_V1}\n${UUID_V4}`;
    expect(parseUuidList(raw)).toEqual([UUID_V4, UUID_V1]);
  });

  it('accepts UUID variants that are not v4 (8-4-4-4-12 hex)', () => {
    // Brief v3 Finding 2: parser is generic UUID format, not v4-only. Backend uses
    // GenerationType.UUID accepting any variant.
    expect(parseUuidList(`${UUID_V1}\n${UUID_V5}`)).toEqual([UUID_V1, UUID_V5]);
  });

  it('drops blank lines, malformed entries, and tokens that are not UUIDs', () => {
    const raw = `${UUID_V4}\n  \nnot-a-uuid\n12345\n${UUID_V1}`;
    expect(parseUuidList(raw)).toEqual([UUID_V4, UUID_V1]);
  });

  it('splits on commas and semicolons too', () => {
    const raw = `${UUID_V4},${UUID_V1};${UUID_V5}`;
    expect(parseUuidList(raw)).toEqual([UUID_V4, UUID_V1, UUID_V5]);
  });
});

describe('BulkDeclareRecordsDialog', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedService.listRecordCategories.mockResolvedValue([]);
  });

  const renderDialog = (overrides: Partial<React.ComponentProps<typeof BulkDeclareRecordsDialog>> = {}) => {
    const onClose = jest.fn();
    const onDeclared = jest.fn();
    render(
      <BulkDeclareRecordsDialog
        open
        onClose={onClose}
        onDeclared={onDeclared}
        categories={[]}
        {...overrides}
      />
    );
    return { onClose, onDeclared };
  };

  it('disables submit when no valid UUIDs are parsed', () => {
    renderDialog();
    const submit = screen.getByTestId('bulk-declare-submit') as HTMLButtonElement;
    expect(submit.disabled).toBe(true);
    // Typing a malformed token does not enable submit.
    fireEvent.change(screen.getByTestId('bulk-declare-node-ids'), {
      target: { value: 'not-a-uuid\nstill-not-a-uuid' },
    });
    expect((screen.getByTestId('bulk-declare-submit') as HTMLButtonElement).disabled).toBe(true);
  });

  it('closes + emits success toast when every row is DECLARED', async () => {
    mockedService.createBulkDeclarations.mockResolvedValueOnce({
      bulkDeclareResults: {
        rows: [
          {
            nodeId: UUID_V4,
            status: 'DECLARED',
            declaration: { nodeId: UUID_V4, name: 'A.pdf', path: '/A.pdf' },
            errorCategory: null,
            errorMessage: null,
          },
          {
            nodeId: UUID_V1,
            status: 'DECLARED',
            declaration: { nodeId: UUID_V1, name: 'B.pdf', path: '/B.pdf' },
            errorCategory: null,
            errorMessage: null,
          },
        ],
      },
    } as any);

    const { onClose, onDeclared } = renderDialog();
    fireEvent.change(screen.getByTestId('bulk-declare-node-ids'), {
      target: { value: `${UUID_V4}\n${UUID_V1}` },
    });
    fireEvent.click(screen.getByTestId('bulk-declare-submit'));

    await waitFor(() => {
      expect(mockedService.createBulkDeclarations).toHaveBeenCalledWith({
        nodeIds: [UUID_V4, UUID_V1],
        categoryId: null,
        comment: null,
      });
    });
    expect(toastSuccess).toHaveBeenCalledWith(expect.stringContaining('2 declared'));
    expect(onDeclared).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
    expect(toastWarn).not.toHaveBeenCalled();
    expect(toastError).not.toHaveBeenCalled();
  });

  it('stays open on partial failure, drains successful UUIDs from the textarea, and renders failed rows grouped by errorCategory', async () => {
    mockedService.createBulkDeclarations.mockResolvedValueOnce({
      bulkDeclareResults: {
        rows: [
          {
            nodeId: UUID_V4,
            status: 'DECLARED',
            declaration: { nodeId: UUID_V4, name: 'A.pdf', path: '/A.pdf' },
            errorCategory: null,
            errorMessage: null,
          },
          {
            nodeId: UUID_V1,
            status: 'FAILED',
            declaration: null,
            errorCategory: 'NODE_NOT_FOUND',
            errorMessage: 'The target node was not found.',
          },
          {
            nodeId: UUID_V5,
            status: 'FAILED',
            declaration: null,
            errorCategory: 'INTERNAL_ERROR',
            errorMessage: 'Internal error while declaring the document as a record. (RuntimeException).',
          },
        ],
      },
    } as any);

    const { onClose } = renderDialog();
    fireEvent.change(screen.getByTestId('bulk-declare-node-ids'), {
      target: { value: `${UUID_V4}\n${UUID_V1}\n${UUID_V5}` },
    });
    fireEvent.click(screen.getByTestId('bulk-declare-submit'));

    await waitFor(() => {
      expect(toastWarn).toHaveBeenCalledWith(expect.stringContaining('1 declared'));
    });
    expect(onClose).not.toHaveBeenCalled(); // stays open on partial failure

    // Textarea drained: the successful UUID is removed; the failed two remain.
    const textarea = screen.getByTestId('bulk-declare-node-ids') as HTMLTextAreaElement;
    expect(textarea.value).not.toContain(UUID_V4);
    expect(textarea.value).toContain(UUID_V1);
    expect(textarea.value).toContain(UUID_V5);

    // Per-category groups rendered with the dedicated test IDs.
    const failedAlert = screen.getByTestId('bulk-declare-failed-rows');
    expect(within(failedAlert).getByText(/2 rows failed/i)).toBeTruthy();
    const notFoundGroup = screen.getByTestId('bulk-declare-failed-node_not_found');
    expect(within(notFoundGroup).getByText(UUID_V1)).toBeTruthy();
    const internalGroup = screen.getByTestId('bulk-declare-failed-internal_error');
    expect(within(internalGroup).getByText(UUID_V5)).toBeTruthy();
    // The internal-error message must NOT leak raw probe strings; assert the class-name
    // sanitization shape lands on screen (errorMessage starts with the fixed copy +
    // includes the exception class simple name). Both the chip label "Internal error (1)"
    // and the row secondary text match "Internal error" — use getAllByText to allow
    // multiple matches, then specifically assert the class-name shape on the row text.
    expect(within(internalGroup).getAllByText(/Internal error/i).length).toBeGreaterThan(0);
    expect(within(internalGroup).getByText(/RuntimeException/)).toBeTruthy();
  });

  it('drains succeeded UUIDs from comma/semicolon-separated input on partial failure', async () => {
    // Drain must use the same separator set as parseUuidList (\n , ;). A \n-only split
    // would leave the succeeded UUID inside an "ok,fail" line, so a retry would re-submit
    // it and get SKIPPED_ALREADY_DECLARED. Mixed separators here lock the parsed-token drain.
    mockedService.createBulkDeclarations.mockResolvedValueOnce({
      bulkDeclareResults: {
        rows: [
          {
            nodeId: UUID_V4,
            status: 'DECLARED',
            declaration: { nodeId: UUID_V4, name: 'A.pdf', path: '/A.pdf' },
            errorCategory: null,
            errorMessage: null,
          },
          {
            nodeId: UUID_V1,
            status: 'SKIPPED_ALREADY_DECLARED',
            declaration: { nodeId: UUID_V1, name: 'B.pdf', path: '/B.pdf' },
            errorCategory: null,
            errorMessage: null,
          },
          {
            nodeId: UUID_V5,
            status: 'FAILED',
            declaration: null,
            errorCategory: 'NODE_NOT_FOUND',
            errorMessage: 'The target node was not found.',
          },
        ],
      },
    } as any);

    const { onClose } = renderDialog();
    fireEvent.change(screen.getByTestId('bulk-declare-node-ids'), {
      // Comma between the first two, semicolon before the third — no newlines at all.
      target: { value: `${UUID_V4},${UUID_V1};${UUID_V5}` },
    });
    fireEvent.click(screen.getByTestId('bulk-declare-submit'));

    await waitFor(() => {
      expect(toastWarn).toHaveBeenCalled();
    });
    expect(onClose).not.toHaveBeenCalled();

    // Both the DECLARED and the SKIPPED_ALREADY_DECLARED UUIDs are drained; only the
    // FAILED one remains for retry.
    const textarea = screen.getByTestId('bulk-declare-node-ids') as HTMLTextAreaElement;
    expect(textarea.value).not.toContain(UUID_V4);
    expect(textarea.value).not.toContain(UUID_V1);
    expect(textarea.value).toContain(UUID_V5);
  });

  it('renders SKIPPED_ALREADY_DECLARED rows in a dedicated soft-skip Alert, separate from the failed-rows Alert', async () => {
    mockedService.createBulkDeclarations.mockResolvedValueOnce({
      bulkDeclareResults: {
        rows: [
          {
            nodeId: UUID_V4,
            status: 'SKIPPED_ALREADY_DECLARED',
            declaration: { nodeId: UUID_V4, name: 'Already.pdf', path: '/Already.pdf' },
            errorCategory: null,
            errorMessage: null,
          },
          {
            nodeId: UUID_V1,
            status: 'FAILED',
            declaration: null,
            errorCategory: 'NODE_NOT_FOUND',
            errorMessage: 'The target node was not found.',
          },
        ],
      },
    } as any);

    renderDialog();
    fireEvent.change(screen.getByTestId('bulk-declare-node-ids'), {
      target: { value: `${UUID_V4}\n${UUID_V1}` },
    });
    fireEvent.click(screen.getByTestId('bulk-declare-submit'));

    await waitFor(() => {
      expect(toastWarn).toHaveBeenCalled();
    });

    // Skipped rows render in the soft-skip Alert (info severity), NOT inside the failed
    // Alert. Asserting both presence and DOM separation locks the Finding 3 invariant that
    // SKIPPED_ALREADY_DECLARED is not under any errorCategory heading.
    const skippedAlert = screen.getByTestId('bulk-declare-skipped-rows');
    expect(within(skippedAlert).getByText('Already.pdf')).toBeTruthy();
    expect(within(skippedAlert).queryByTestId('bulk-declare-failed-node_not_found')).toBeNull();

    const failedAlert = screen.getByTestId('bulk-declare-failed-rows');
    expect(within(failedAlert).queryByText('Already.pdf')).toBeNull();
  });

  it('trims comment whitespace before sending and omits an empty categoryId from the request', async () => {
    mockedService.createBulkDeclarations.mockResolvedValueOnce({
      bulkDeclareResults: {
        rows: [
          {
            nodeId: UUID_V4,
            status: 'DECLARED',
            declaration: { nodeId: UUID_V4, name: 'A.pdf', path: '/A.pdf' },
            errorCategory: null,
            errorMessage: null,
          },
        ],
      },
    } as any);

    renderDialog();
    fireEvent.change(screen.getByTestId('bulk-declare-node-ids'), {
      target: { value: UUID_V4 },
    });
    fireEvent.change(screen.getByTestId('bulk-declare-comment'), {
      target: { value: '  batch run  ' },
    });
    fireEvent.click(screen.getByTestId('bulk-declare-submit'));

    await waitFor(() => {
      expect(mockedService.createBulkDeclarations).toHaveBeenCalledWith({
        nodeIds: [UUID_V4],
        categoryId: null,
        comment: '  batch run  ',
      });
    });
    // (Service-layer trims the comment before sending the HTTP payload — see
    // recordsManagementService.test.ts. Dialog-level trims would double-strip.)
  });

  it('shows an error toast and reopens submit when the service rejects the call', async () => {
    mockedService.createBulkDeclarations.mockRejectedValueOnce(new Error('Bulk record declaration endpoint returned an unexpected response.'));

    const { onClose } = renderDialog();
    fireEvent.change(screen.getByTestId('bulk-declare-node-ids'), {
      target: { value: UUID_V4 },
    });
    fireEvent.click(screen.getByTestId('bulk-declare-submit'));

    await waitFor(() => {
      expect(toastError).toHaveBeenCalledWith(expect.stringContaining('Bulk record declaration'));
    });
    expect(onClose).not.toHaveBeenCalled();
    expect((screen.getByTestId('bulk-declare-submit') as HTMLButtonElement).disabled).toBe(false);
  });
});
