import React from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import MailAutomationPage from './MailAutomationPage';
import mailAutomationService, {
  EmailTestSmtpResponse,
  TEST_SMTP_UNEXPECTED_RESPONSE_MESSAGE,
} from 'services/mailAutomationService';

// react-toastify in CI mode renders a portal that interferes with timers; mock it.
jest.mock('react-toastify', () => ({
  toast: {
    error: jest.fn(),
    success: jest.fn(),
    info: jest.fn(),
    warn: jest.fn(),
  },
  ToastContainer: () => null,
}));

const mockNavigate = jest.fn();
jest.mock('react-router-dom', () => {
  const actual = jest.requireActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
    useLocation: () => ({ pathname: '/admin/mail', search: '', hash: '', state: null, key: 'test' }),
  };
});

jest.mock('services/mailAutomationService', () => ({
  __esModule: true,
  default: {
    listAccounts: jest.fn(),
    listRules: jest.fn(),
    getFetchSummary: jest.fn(),
    getProcessedRetention: jest.fn(),
    getDiagnostics: jest.fn(),
    getReport: jest.fn(),
    getReportSchedule: jest.fn(),
    getRuntimeMetrics: jest.fn(),
    listProviderPresets: jest.fn(),
    testSmtp: jest.fn(),
  },
  // Re-export the shape-guard message constant so tests can pin it.
  TEST_SMTP_UNEXPECTED_RESPONSE_MESSAGE:
    'Test SMTP endpoint returned an unexpected response. Mocked CI gate may not cover it; runtime configuration may be missing.',
}));

jest.mock('services/tagService', () => ({
  __esModule: true,
  default: {
    getAllTags: jest.fn().mockResolvedValue([]),
  },
}));

jest.mock('services/nodeService', () => ({
  __esModule: true,
  default: {
    getNodeByPath: jest.fn(),
    getNode: jest.fn(),
  },
}));

const mockedMailService = mailAutomationService as jest.Mocked<typeof mailAutomationService>;

const setupBaselineMocks = () => {
  mockedMailService.listAccounts.mockResolvedValue([]);
  mockedMailService.listRules.mockResolvedValue([]);
  mockedMailService.getFetchSummary.mockResolvedValue({ summary: null, fetchedAt: null });
  mockedMailService.getProcessedRetention.mockResolvedValue({ retentionDays: 0, enabled: false, expiredCount: 0 });
  mockedMailService.getDiagnostics.mockResolvedValue({ limit: 25, recentProcessed: [], recentDocuments: [] });
  mockedMailService.getReport.mockResolvedValue({
    accountId: null,
    ruleId: null,
    startDate: '2026-01-01',
    endDate: '2026-01-31',
    days: 30,
    totals: { processed: 0, errors: 0, total: 0 },
    accounts: [],
    rules: [],
    trend: [],
  });
  mockedMailService.getReportSchedule.mockResolvedValue({
    enabled: false,
    cron: null,
    folderId: null,
    days: 30,
    accountId: null,
    ruleId: null,
    lastExport: null,
  });
  mockedMailService.getRuntimeMetrics.mockResolvedValue({
    windowMinutes: 60,
    attempts: 0,
    successes: 0,
    errors: 0,
    errorRate: 0,
    status: 'UNKNOWN',
  });
  mockedMailService.listProviderPresets.mockResolvedValue([]);
};

const openTestSmtpDialog = async () => {
  const button = await screen.findByRole('button', { name: 'Test SMTP' });
  fireEvent.click(button);
  return screen.findByRole('dialog');
};

beforeEach(() => {
  jest.clearAllMocks();
  setupBaselineMocks();
});

describe('MailAutomationPage Test SMTP dialog', () => {
  test('opens dialog with Send test disabled until a recipient containing @ is entered', async () => {
    render(<MailAutomationPage />);

    const dialog = await openTestSmtpDialog();
    const sendButton = within(dialog).getByRole('button', { name: 'Send test' }) as HTMLButtonElement;
    expect(sendButton.disabled).toBe(true);

    const recipient = within(dialog).getByLabelText('Recipient email') as HTMLInputElement;

    fireEvent.change(recipient, { target: { value: 'not-an-email' } });
    expect((within(dialog).getByRole('button', { name: 'Send test' }) as HTMLButtonElement).disabled).toBe(true);

    fireEvent.change(recipient, { target: { value: 'operator@example.com' } });
    expect((within(dialog).getByRole('button', { name: 'Send test' }) as HTMLButtonElement).disabled).toBe(false);
  });

  test('renders a success Alert with smtpHost/smtpPort/fromAddress when ok=true', async () => {
    const successResponse: EmailTestSmtpResponse = {
      ok: true,
      message: 'Sent successfully.',
      smtpHost: 'smtp.example.com',
      smtpPort: 465,
      fromAddress: 'noreply@example.com',
      diagnostic: null,
    };
    mockedMailService.testSmtp.mockResolvedValueOnce(successResponse);

    render(<MailAutomationPage />);
    const dialog = await openTestSmtpDialog();

    fireEvent.change(within(dialog).getByLabelText('Recipient email'), {
      target: { value: 'ops@example.com' },
    });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Send test' }));

    await waitFor(() => {
      expect(mockedMailService.testSmtp).toHaveBeenCalledWith({ to: 'ops@example.com' });
    });

    const successAlert = await within(dialog).findByTestId('test-smtp-success');
    expect(successAlert.textContent).toContain('Sent!');
    expect(successAlert.textContent).toContain('smtp.example.com');
    expect(successAlert.textContent).toContain('465');
    expect(successAlert.textContent).toContain('noreply@example.com');

    // Close button replaces Cancel after a successful send.
    expect(within(dialog).getByRole('button', { name: 'Close' })).toBeTruthy();
  });

  test('renders an error Alert with message and diagnostic block when ok=false', async () => {
    const failureResponse: EmailTestSmtpResponse = {
      ok: false,
      message: 'SMTP authentication rejected by upstream.',
      smtpHost: 'smtp.example.com',
      smtpPort: 587,
      fromAddress: 'noreply@example.com',
      diagnostic: 'AuthenticationFailedException: 535 5.7.8 Bad credentials',
    };
    mockedMailService.testSmtp.mockResolvedValueOnce(failureResponse);

    render(<MailAutomationPage />);
    const dialog = await openTestSmtpDialog();

    fireEvent.change(within(dialog).getByLabelText('Recipient email'), {
      target: { value: 'ops@example.com' },
    });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Send test' }));

    const errorAlert = await within(dialog).findByTestId('test-smtp-error');
    expect(errorAlert.textContent).toContain('SMTP authentication rejected by upstream.');

    const diagnostic = within(dialog).getByTestId('test-smtp-diagnostic');
    expect(diagnostic.textContent).toContain('AuthenticationFailedException');
    expect(diagnostic.textContent).toContain('535 5.7.8 Bad credentials');
  });

  test('renders backend message when the service rejects', async () => {
    const error = Object.assign(new Error('axios rejected'), {
      response: { data: { message: 'You do not have permission to call test-smtp.' } },
    });
    mockedMailService.testSmtp.mockRejectedValueOnce(error);

    render(<MailAutomationPage />);
    const dialog = await openTestSmtpDialog();

    fireEvent.change(within(dialog).getByLabelText('Recipient email'), {
      target: { value: 'ops@example.com' },
    });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Send test' }));

    const errorAlert = await within(dialog).findByTestId('test-smtp-error');
    expect(errorAlert.textContent).toContain('You do not have permission to call test-smtp.');
    // No diagnostic block in the rejection path.
    expect(within(dialog).queryByTestId('test-smtp-diagnostic')).toBeNull();
  });

  test('does not crash and surfaces the synthetic shape-guard message when the service resolves null (HTML fallback)', async () => {
    // Simulates the Phase 5 Mocked harness serving SPA index.html with HTTP
    // 200 for /admin/email/test-smtp. The service-level shape guard is
    // bypassed here because the test mocks the service directly; the page
    // handler must still defend itself.
    mockedMailService.testSmtp.mockResolvedValueOnce(null as unknown as EmailTestSmtpResponse);

    render(<MailAutomationPage />);
    const dialog = await openTestSmtpDialog();

    fireEvent.change(within(dialog).getByLabelText('Recipient email'), {
      target: { value: 'ops@example.com' },
    });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Send test' }));

    const errorAlert = await within(dialog).findByTestId('test-smtp-error');
    expect(errorAlert.textContent).toContain(TEST_SMTP_UNEXPECTED_RESPONSE_MESSAGE);
    // The page is still mounted — the page title text remains in the DOM.
    expect(screen.getAllByText('Mail Automation').length).toBeGreaterThan(0);
  });
});
