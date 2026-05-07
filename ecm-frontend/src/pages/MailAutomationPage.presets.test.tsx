import React from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import MailAutomationPage from './MailAutomationPage';
import mailAutomationService, { MailProviderPreset } from 'services/mailAutomationService';

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

// MailAutomationPage uses useLocation/useNavigate. The default export of
// react-router-dom is preserved so component imports continue to resolve.
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
  },
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

// Single source of truth for preset values inside the test. The real values
// live in the backend; this fixture mirrors that shape so the page wiring is
// exercised against realistic data without duplicating the canonical values.
const PRESET_FIXTURE: MailProviderPreset[] = [
  { id: 'ALIYUN_QIYE', label: '阿里云企业邮箱', imapHost: 'imap.qiye.aliyun.com', imapPort: 993, imapSecurity: 'SSL' },
  { id: 'TENCENT_EXMAIL', label: '腾讯企业邮箱', imapHost: 'imap.exmail.qq.com', imapPort: 993, imapSecurity: 'SSL' },
  { id: 'TENCENT_EXMAIL_OVERSEAS', label: '腾讯企业邮箱（海外）', imapHost: 'hwimap.exmail.qq.com', imapPort: 993, imapSecurity: 'SSL' },
  { id: 'MAIL_263', label: '263 企业邮箱', imapHost: 'imap.263.net', imapPort: 993, imapSecurity: 'SSL' },
  { id: 'MAIL_263_OVERSEAS', label: '263 企业邮箱（海外）', imapHost: 'imap.263xmail.com', imapPort: 993, imapSecurity: 'SSL' },
];

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
};

const openAccountDialog = async () => {
  const newAccountButton = await screen.findByRole('button', { name: 'New Account' });
  fireEvent.click(newAccountButton);
  return screen.findByRole('dialog');
};

const pickPreset = async (dialog: HTMLElement, label: string) => {
  const presetSelect = within(dialog).getByRole('combobox', { name: 'Provider preset' });
  fireEvent.mouseDown(presetSelect);
  const listbox = await screen.findByRole('listbox');
  fireEvent.click(within(listbox).getByText(label));
  // After selection, MUI's listbox unmounts; wait for it to clear.
  await waitFor(() => {
    expect(screen.queryByRole('listbox')).toBeNull();
  });
};

beforeEach(() => {
  jest.clearAllMocks();
  setupBaselineMocks();
  mockedMailService.listProviderPresets.mockResolvedValue(PRESET_FIXTURE);
});

describe('MailAutomationPage provider preset dropdown', () => {
  test('fetches provider presets on mount', async () => {
    render(<MailAutomationPage />);

    await waitFor(() => {
      expect(mockedMailService.listProviderPresets).toHaveBeenCalledTimes(1);
    });
  });

  test('selecting Aliyun fills IMAP host/port/security from the preset fixture', async () => {
    const aliyun = PRESET_FIXTURE.find((entry) => entry.id === 'ALIYUN_QIYE')!;

    render(<MailAutomationPage />);

    const dialog = await openAccountDialog();
    // Wait for the preset to appear in the dropdown after the mount fetch resolves.
    await within(dialog).findByRole('combobox', { name: 'Provider preset' });

    await pickPreset(dialog, aliyun.label);

    const hostInput = within(dialog).getByLabelText('Host') as HTMLInputElement;
    const portInput = within(dialog).getByLabelText('Port') as HTMLInputElement;
    const securitySelect = within(dialog).getByRole('combobox', { name: 'Security' });

    await waitFor(() => {
      expect(hostInput.value).toBe(aliyun.imapHost);
    });
    expect(portInput.value).toBe(String(aliyun.imapPort));
    expect(securitySelect.textContent).toBe(aliyun.imapSecurity);
  });

  test('selecting Tencent fills IMAP host/port/security from the preset fixture', async () => {
    const tencent = PRESET_FIXTURE.find((entry) => entry.id === 'TENCENT_EXMAIL')!;

    render(<MailAutomationPage />);

    const dialog = await openAccountDialog();
    await within(dialog).findByRole('combobox', { name: 'Provider preset' });

    await pickPreset(dialog, tencent.label);

    const hostInput = within(dialog).getByLabelText('Host') as HTMLInputElement;
    const portInput = within(dialog).getByLabelText('Port') as HTMLInputElement;
    const securitySelect = within(dialog).getByRole('combobox', { name: 'Security' });

    await waitFor(() => {
      expect(hostInput.value).toBe(tencent.imapHost);
    });
    expect(portInput.value).toBe(String(tencent.imapPort));
    expect(securitySelect.textContent).toBe(tencent.imapSecurity);
  });

  test('selecting 263 fills IMAP host/port/security from the preset fixture', async () => {
    const mail263 = PRESET_FIXTURE.find((entry) => entry.id === 'MAIL_263')!;

    render(<MailAutomationPage />);

    const dialog = await openAccountDialog();
    await within(dialog).findByRole('combobox', { name: 'Provider preset' });

    await pickPreset(dialog, mail263.label);

    const hostInput = within(dialog).getByLabelText('Host') as HTMLInputElement;
    const portInput = within(dialog).getByLabelText('Port') as HTMLInputElement;
    const securitySelect = within(dialog).getByRole('combobox', { name: 'Security' });

    await waitFor(() => {
      expect(hostInput.value).toBe(mail263.imapHost);
    });
    expect(portInput.value).toBe(String(mail263.imapPort));
    expect(securitySelect.textContent).toBe(mail263.imapSecurity);
  });

  test('manual edits to host after picking a preset persist (no auto-revert)', async () => {
    const aliyun = PRESET_FIXTURE.find((entry) => entry.id === 'ALIYUN_QIYE')!;

    render(<MailAutomationPage />);

    const dialog = await openAccountDialog();
    await within(dialog).findByRole('combobox', { name: 'Provider preset' });

    await pickPreset(dialog, aliyun.label);

    const hostInput = within(dialog).getByLabelText('Host') as HTMLInputElement;
    await waitFor(() => {
      expect(hostInput.value).toBe(aliyun.imapHost);
    });

    fireEvent.change(hostInput, { target: { value: 'imap.custom.example.com' } });

    expect(hostInput.value).toBe('imap.custom.example.com');
  });

  test('preset endpoint failure does not crash the page; dropdown still renders Custom', async () => {
    mockedMailService.listProviderPresets.mockRejectedValueOnce(new Error('boom'));

    render(<MailAutomationPage />);

    await waitFor(() => {
      expect(mockedMailService.listProviderPresets).toHaveBeenCalledTimes(1);
    });

    const dialog = await openAccountDialog();
    const presetSelect = within(dialog).getByRole('combobox', { name: 'Provider preset' });
    fireEvent.mouseDown(presetSelect);

    const listbox = await screen.findByRole('listbox');
    expect(within(listbox).getByText('Custom')).toBeTruthy();
    // None of the fetched presets are present.
    expect(within(listbox).queryByText('阿里云企业邮箱')).toBeNull();
  });
});
