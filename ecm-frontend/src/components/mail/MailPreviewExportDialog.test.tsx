import React from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { toast } from 'react-toastify';
import MailPreviewExportDialog from './MailPreviewExportDialog';
import mailAutomationService from 'services/mailAutomationService';

jest.mock('react-toastify', () => ({
  toast: {
    success: jest.fn(),
    error: jest.fn(),
    warn: jest.fn(),
  },
}));

jest.mock('services/mailAutomationService', () => ({
  __esModule: true,
  default: {
    exportPreviewMatches: jest.fn(),
  },
}));

const mockedService = mailAutomationService as jest.Mocked<typeof mailAutomationService>;
const toastSuccess = toast.success as jest.Mock;
const toastWarn = toast.warn as jest.Mock;
const toastError = toast.error as jest.Mock;

const FOLDER_ID = '11111111-1111-4111-8111-111111111111';
const ACCOUNT_ID = '22222222-2222-4222-8222-222222222222';
const RULE_ID = '33333333-3333-4333-8333-333333333333';

const MATCHES = [
  { folder: 'INBOX', uid: '1', subject: 'A', from: null, recipients: null, receivedAt: null, attachmentCount: 0, processable: true },
  { folder: 'INBOX', uid: '2', subject: 'B', from: null, recipients: null, receivedAt: null, attachmentCount: 1, processable: true },
  { folder: 'INBOX', uid: '3', subject: 'C', from: null, recipients: null, receivedAt: null, attachmentCount: 0, processable: false },
];

const renderDialog = (overrides: Partial<React.ComponentProps<typeof MailPreviewExportDialog>> = {}) => {
  const onClose = jest.fn();
  const onExported = jest.fn();
  render(
    <MailPreviewExportDialog
      open
      onClose={onClose}
      onExported={onExported}
      accountId={ACCOUNT_ID}
      ruleId={RULE_ID}
      matches={MATCHES as any}
      {...overrides}
    />,
  );
  return { onClose, onExported };
};

const setFolder = () => {
  fireEvent.change(screen.getByTestId('mail-export-target-folder'), { target: { value: FOLDER_ID } });
};

// data-testid sits on the MUI Checkbox root; the toggleable element is the inner input.
const rowInput = (uid: string): HTMLInputElement =>
  within(screen.getByTestId(`mail-export-row-${uid}`)).getByRole('checkbox') as HTMLInputElement;
const clickRow = (uid: string) => fireEvent.click(rowInput(uid));

describe('MailPreviewExportDialog', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('disables submit until a valid folder UUID and at least one selection exist', () => {
    renderDialog();
    const submit = screen.getByTestId('mail-export-submit') as HTMLButtonElement;
    expect(submit.disabled).toBe(true);

    // Selecting a row but no folder keeps it disabled.
    clickRow('1');
    expect((screen.getByTestId('mail-export-submit') as HTMLButtonElement).disabled).toBe(true);

    // A malformed folder id keeps it disabled.
    fireEvent.change(screen.getByTestId('mail-export-target-folder'), { target: { value: 'not-a-uuid' } });
    expect((screen.getByTestId('mail-export-submit') as HTMLButtonElement).disabled).toBe(true);

    // Valid folder + selection enables it.
    setFolder();
    expect((screen.getByTestId('mail-export-submit') as HTMLButtonElement).disabled).toBe(false);
  });

  it('select-all toggles every row and sends all selections', async () => {
    mockedService.exportPreviewMatches.mockResolvedValueOnce({
      accountId: ACCOUNT_ID, ruleId: RULE_ID, targetFolderId: FOLDER_ID,
      exported: 3, skipped: 0, failed: 0,
      rows: MATCHES.map((m) => ({ folder: m.folder, uid: m.uid, status: 'EXPORTED', errorCategory: null, errorMessage: null })),
    } as any);

    const { onClose, onExported } = renderDialog();
    setFolder();
    fireEvent.click(screen.getByTestId('mail-export-select-all'));
    fireEvent.click(screen.getByTestId('mail-export-submit'));

    await waitFor(() => {
      expect(mockedService.exportPreviewMatches).toHaveBeenCalledWith(RULE_ID, {
        accountId: ACCOUNT_ID,
        targetFolderId: FOLDER_ID,
        selections: [
          { folder: 'INBOX', uid: '1' },
          { folder: 'INBOX', uid: '2' },
          { folder: 'INBOX', uid: '3' },
        ],
      });
    });
    expect(toastSuccess).toHaveBeenCalledWith(expect.stringContaining('3 exported'));
    expect(onExported).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it('stays open on partial failure, drains handled selections, keeps failed selected, lists failures', async () => {
    mockedService.exportPreviewMatches.mockResolvedValueOnce({
      accountId: ACCOUNT_ID, ruleId: RULE_ID, targetFolderId: FOLDER_ID,
      exported: 1, skipped: 1, failed: 1,
      rows: [
        { folder: 'INBOX', uid: '1', status: 'EXPORTED', errorCategory: null, errorMessage: null },
        { folder: 'INBOX', uid: '2', status: 'SKIPPED_ALREADY_PROCESSED', errorCategory: null, errorMessage: null },
        { folder: 'INBOX', uid: '3', status: 'FAILED', errorCategory: 'INTERNAL_ERROR', errorMessage: 'Failed to export the mail message into the target folder. (MessagingException).' },
      ],
    } as any);

    const { onClose, onExported } = renderDialog();
    setFolder();
    fireEvent.click(screen.getByTestId('mail-export-select-all'));
    fireEvent.click(screen.getByTestId('mail-export-submit'));

    await waitFor(() => {
      expect(toastWarn).toHaveBeenCalledWith(expect.stringContaining('1 failed'));
    });
    expect(onClose).not.toHaveBeenCalled();
    expect(onExported).toHaveBeenCalled(); // exported > 0

    // Drain: EXPORTED (uid 1) and SKIPPED (uid 2) are unchecked; FAILED (uid 3) stays checked.
    // The testid sits on the MUI Checkbox root; read the inner input for checked state.
    expect(rowInput('1').checked).toBe(false);
    expect(rowInput('2').checked).toBe(false);
    expect(rowInput('3').checked).toBe(true);

    // Failure surfaced with the sanitized message (class name, never raw detail).
    const failed = screen.getByTestId('mail-export-failed-rows');
    expect(within(failed).getByText(/uid 3/)).toBeTruthy();
    expect(within(failed).getByText(/MessagingException/)).toBeTruthy();
    expect(screen.getByTestId('mail-export-skipped-rows')).toBeTruthy();
  });

  it('shows an error toast and stays open when the service rejects', async () => {
    mockedService.exportPreviewMatches.mockRejectedValueOnce(
      new Error('Mail automation endpoint returned an unexpected response.'),
    );

    const { onClose } = renderDialog();
    setFolder();
    clickRow('1');
    fireEvent.click(screen.getByTestId('mail-export-submit'));

    await waitFor(() => {
      expect(toastError).toHaveBeenCalledWith(expect.stringContaining('unexpected response'));
    });
    expect(onClose).not.toHaveBeenCalled();
  });
});
