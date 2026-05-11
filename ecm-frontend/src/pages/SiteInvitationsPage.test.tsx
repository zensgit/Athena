import React from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { toast } from 'react-toastify';
import SiteInvitationsPage from './SiteInvitationsPage';
import siteInvitationService, {
  SITE_INVITATION_RESEND_UNEXPECTED_RESPONSE_MESSAGE,
  SiteInvitationDto,
} from 'services/siteInvitationService';

// react-toastify renders a portal that interferes with default jsdom timers; mock it.
jest.mock('react-toastify', () => ({
  toast: {
    error: jest.fn(),
    success: jest.fn(),
    info: jest.fn(),
    warn: jest.fn(),
  },
  ToastContainer: () => null,
}));

// Mock the service. Re-export the synthetic shape-guard message constant
// because jest.mock replaces the module wholesale; without re-exporting the
// constant the named import would be `undefined` and the assertion would
// silently degrade. Mirrors the approach in MailAutomationPage.testSmtp.test.tsx.
jest.mock('services/siteInvitationService', () => ({
  __esModule: true,
  default: {
    listInvitations: jest.fn(),
    createInvitation: jest.fn(),
    cancelInvitation: jest.fn(),
    acceptInvitation: jest.fn(),
    rejectInvitation: jest.fn(),
    resendInvitation: jest.fn(),
  },
  SITE_INVITATION_RESEND_UNEXPECTED_RESPONSE_MESSAGE:
    'Site invitation resend endpoint returned an unexpected response. Mocked CI gate may not cover it; runtime configuration may be missing.',
}));

const mockedService = siteInvitationService as jest.Mocked<typeof siteInvitationService>;

const SITE_ID = 'site-1';

const baseInvitation: SiteInvitationDto = {
  id: 'inv-base',
  siteId: SITE_ID,
  siteTitle: 'Acme Workspace',
  inviteeEmail: 'base@example.com',
  inviteeUsername: null,
  invitedRole: 'CONSUMER',
  status: 'PENDING',
  message: null,
  invitedBy: 'admin@example.com',
  expiresAt: '2026-06-01T12:00:00Z',
  acceptedAt: null,
  createdDate: '2026-05-01T12:00:00Z',
  lastSendAttemptAt: null,
  lastSendStatus: null,
  lastSendError: null,
  sendAttemptCount: 0,
  lastSentAt: null,
};

const sentInvitation: SiteInvitationDto = {
  ...baseInvitation,
  id: 'inv-sent',
  inviteeEmail: 'sent@example.com',
  lastSendAttemptAt: '2026-05-05T09:30:00Z',
  lastSendStatus: 'SENT',
  lastSendError: null,
  sendAttemptCount: 1,
  lastSentAt: '2026-05-05T09:30:00Z',
};

const failedInvitation: SiteInvitationDto = {
  ...baseInvitation,
  id: 'inv-failed',
  inviteeEmail: 'failed@example.com',
  lastSendAttemptAt: '2026-05-06T08:00:00Z',
  lastSendStatus: 'FAILED',
  lastSendError: 'SMTP relay rejected: 550 mailbox unavailable',
  sendAttemptCount: 2,
  lastSentAt: null,
};

const neverSentInvitation: SiteInvitationDto = {
  ...baseInvitation,
  id: 'inv-never',
  inviteeEmail: 'never@example.com',
  lastSendAttemptAt: null,
  lastSendStatus: null,
  lastSendError: null,
  sendAttemptCount: 0,
  lastSentAt: null,
};

const acceptedInvitation: SiteInvitationDto = {
  ...baseInvitation,
  id: 'inv-accepted',
  inviteeEmail: 'accepted@example.com',
  status: 'ACCEPTED',
  acceptedAt: '2026-05-04T10:00:00Z',
};

const acceptedFailedInvitation: SiteInvitationDto = {
  ...failedInvitation,
  id: 'inv-accepted-failed',
  inviteeEmail: 'accepted-failed@example.com',
  status: 'ACCEPTED',
  acceptedAt: '2026-05-04T10:00:00Z',
};

const cancelledInvitation: SiteInvitationDto = {
  ...baseInvitation,
  id: 'inv-cancelled',
  inviteeEmail: 'cancelled@example.com',
  status: 'CANCELLED',
};

const expiredInvitation: SiteInvitationDto = {
  ...baseInvitation,
  id: 'inv-expired',
  inviteeEmail: 'expired@example.com',
  status: 'EXPIRED',
};

const rejectedInvitation: SiteInvitationDto = {
  ...baseInvitation,
  id: 'inv-rejected',
  inviteeEmail: 'rejected@example.com',
  status: 'REJECTED',
};

const LocationProbe: React.FC = () => {
  const location = useLocation();
  return <span data-testid="location-search">{location.search}</span>;
};

const renderPage = (initialEntry = `/admin/sites/${SITE_ID}/invitations`) => {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <LocationProbe />
      <Routes>
        <Route path="/admin/sites/:siteId/invitations" element={<SiteInvitationsPage />} />
      </Routes>
    </MemoryRouter>,
  );
};

beforeEach(() => {
  jest.clearAllMocks();
});

describe('SiteInvitationsPage send-status display', () => {
  beforeEach(() => {
    mockedService.listInvitations.mockResolvedValue([
      sentInvitation,
      failedInvitation,
      neverSentInvitation,
    ]);
  });

  // Locate a data row by the unique email it contains. Using `findAllByRole('row')`
  // and filtering by the row's text content avoids `.closest()` (testing-library
  // discourages node-walking) and avoids index-based fragility.
  const findRow = async (email: string): Promise<HTMLElement> => {
    const rows = await screen.findAllByRole('row');
    const match = rows.find((row) => row.textContent?.includes(email));
    if (!match) {
      throw new Error(`Row containing ${email} not found`);
    }
    return match;
  };

  test('renders SENT chip and last-sent caption for the SENT row', async () => {
    renderPage();

    const sentRow = await findRow('sent@example.com');
    const cells = within(sentRow);
    expect(cells.getByText('SENT')).toBeTruthy();
    expect(cells.getByText(/last sent:/)).toBeTruthy();
    expect(cells.getByText(/attempts: 1/)).toBeTruthy();
  });

  test('renders FAILED chip and the error caption for the FAILED row', async () => {
    renderPage();

    const failedRow = await findRow('failed@example.com');
    const errorCaption = screen.getByTestId(`invitation-send-error-${failedInvitation.id}`);
    expect(errorCaption.textContent).toContain('SMTP relay rejected');
    expect(within(failedRow).getByText('FAILED')).toBeTruthy();
    expect(within(failedRow).getByText(/attempts: 2/)).toBeTruthy();
  });

  test('renders the Not yet sent chip for the never-attempted row', async () => {
    renderPage();

    const neverRow = await findRow('never@example.com');
    expect(within(neverRow).getByText('Not yet sent')).toBeTruthy();
    expect(within(neverRow).getByText(/attempts: 0/)).toBeTruthy();
  });
});

describe('SiteInvitationsPage send-status filter chips', () => {
  test('filters the table to failed sends and can return to all invitations', async () => {
    mockedService.listInvitations.mockResolvedValue([
      sentInvitation,
      failedInvitation,
      neverSentInvitation,
    ]);

    renderPage();

    expect(await screen.findByText('sent@example.com')).toBeTruthy();
    expect(screen.getByText('failed@example.com')).toBeTruthy();
    expect(screen.getByText('never@example.com')).toBeTruthy();
    expect(screen.getByTestId('location-search').textContent).toBe('');

    fireEvent.click(screen.getByLabelText('Show failed-send invitations (1)'));

    expect(screen.getByText('failed@example.com')).toBeTruthy();
    expect(screen.queryByText('sent@example.com')).toBeNull();
    expect(screen.queryByText('never@example.com')).toBeNull();
    expect(screen.getByTestId('location-search').textContent).toBe('?sendStatus=failed');

    fireEvent.click(screen.getByLabelText('Show all invitations (3)'));

    expect(screen.getByText('sent@example.com')).toBeTruthy();
    expect(screen.getByText('failed@example.com')).toBeTruthy();
    expect(screen.getByText('never@example.com')).toBeTruthy();
    expect(screen.getByTestId('location-search').textContent).toBe('');
  });

  test('shows a filter-specific empty state when no rows have failed sends', async () => {
    mockedService.listInvitations.mockResolvedValue([
      sentInvitation,
      neverSentInvitation,
    ]);

    renderPage();
    expect(await screen.findByText('sent@example.com')).toBeTruthy();

    fireEvent.click(screen.getByLabelText('Show failed-send invitations (0)'));

    expect(screen.getByText('No failed-send invitations found.')).toBeTruthy();
    expect(screen.queryByText('sent@example.com')).toBeNull();
    expect(screen.queryByText('never@example.com')).toBeNull();
  });

  test('hydrates the failed-send filter from the URL query parameter', async () => {
    mockedService.listInvitations.mockResolvedValue([
      sentInvitation,
      failedInvitation,
      neverSentInvitation,
    ]);

    renderPage(`/admin/sites/${SITE_ID}/invitations?sendStatus=failed`);

    expect(await screen.findByText('failed@example.com')).toBeTruthy();
    expect(screen.queryByText('sent@example.com')).toBeNull();
    expect(screen.queryByText('never@example.com')).toBeNull();
    expect(screen.getByTestId('location-search').textContent).toBe('?sendStatus=failed');
  });
});

describe('SiteInvitationsPage Resend button gating', () => {
  test('Resend button is enabled for every PENDING row', async () => {
    mockedService.listInvitations.mockResolvedValue([
      sentInvitation,
      failedInvitation,
      neverSentInvitation,
    ]);

    renderPage();

    await screen.findByText('sent@example.com');

    const sentBtn = screen.getByRole('button', { name: 'Resend invitation to sent@example.com' }) as HTMLButtonElement;
    const failedBtn = screen.getByRole('button', { name: 'Resend invitation to failed@example.com' }) as HTMLButtonElement;
    const neverBtn = screen.getByRole('button', { name: 'Resend invitation to never@example.com' }) as HTMLButtonElement;

    expect(sentBtn.disabled).toBe(false);
    expect(failedBtn.disabled).toBe(false);
    expect(neverBtn.disabled).toBe(false);
  });

  test('Resend button is disabled for ACCEPTED, CANCELLED, EXPIRED, and REJECTED rows', async () => {
    mockedService.listInvitations.mockResolvedValue([
      acceptedInvitation,
      cancelledInvitation,
      expiredInvitation,
      rejectedInvitation,
    ]);

    renderPage();

    await screen.findByText('accepted@example.com');

    const acceptedBtn = screen.getByRole('button', { name: 'Resend invitation to accepted@example.com' }) as HTMLButtonElement;
    const cancelledBtn = screen.getByRole('button', { name: 'Resend invitation to cancelled@example.com' }) as HTMLButtonElement;
    const expiredBtn = screen.getByRole('button', { name: 'Resend invitation to expired@example.com' }) as HTMLButtonElement;
    const rejectedBtn = screen.getByRole('button', { name: 'Resend invitation to rejected@example.com' }) as HTMLButtonElement;

    expect(acceptedBtn.disabled).toBe(true);
    expect(cancelledBtn.disabled).toBe(true);
    expect(expiredBtn.disabled).toBe(true);
    expect(rejectedBtn.disabled).toBe(true);
  });
});

describe('SiteInvitationsPage bulk failed resend', () => {
  test('resends only failed PENDING invitations and replaces returned rows', async () => {
    const updated: SiteInvitationDto = {
      ...failedInvitation,
      lastSendAttemptAt: '2026-05-11T12:00:00Z',
      lastSendStatus: 'SENT',
      lastSendError: null,
      sendAttemptCount: 3,
      lastSentAt: '2026-05-11T12:00:00Z',
    };
    const confirmSpy = jest.spyOn(window, 'confirm').mockReturnValueOnce(true);
    mockedService.listInvitations.mockResolvedValue([
      failedInvitation,
      acceptedFailedInvitation,
      sentInvitation,
    ]);
    mockedService.resendInvitation.mockResolvedValueOnce(updated);

    renderPage();
    expect(await screen.findByText('failed@example.com')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Resend all failed pending invitations (1)' }));

    await waitFor(() => {
      expect(mockedService.resendInvitation).toHaveBeenCalledTimes(1);
    });
    expect(mockedService.resendInvitation).toHaveBeenCalledWith(SITE_ID, failedInvitation.id);
    expect(mockedService.resendInvitation).not.toHaveBeenCalledWith(SITE_ID, acceptedFailedInvitation.id);
    expect(toast.success).toHaveBeenCalledWith('Bulk resend finished: 1 sent.');
    expect(confirmSpy).toHaveBeenCalledWith('Resend 1 failed invitation email(s)?');

    const rows = await screen.findAllByRole('row');
    const updatedRow = rows.find((row) => row.textContent?.includes('failed@example.com'));
    expect(updatedRow).toBeTruthy();
    expect(within(updatedRow as HTMLElement).getByText('SENT')).toBeTruthy();
    expect(within(updatedRow as HTMLElement).getByText(/attempts: 3/)).toBeTruthy();
    expect(within(updatedRow as HTMLElement).queryByText('FAILED')).toBeNull();
    confirmSpy.mockRestore();
  });

  test('disables the bulk resend action when failed sends are not pending', async () => {
    mockedService.listInvitations.mockResolvedValue([
      acceptedFailedInvitation,
      sentInvitation,
    ]);

    renderPage();
    expect(await screen.findByText('accepted-failed@example.com')).toBeTruthy();

    const bulkButton = screen.getByRole('button', {
      name: 'Resend all failed pending invitations (0)',
    }) as HTMLButtonElement;
    expect(bulkButton.disabled).toBe(true);
  });
});

describe('SiteInvitationsPage invite create flow', () => {
  beforeEach(() => {
    mockedService.listInvitations.mockResolvedValue([]);
  });

  test('does not report success when the create response records an email send failure', async () => {
    const created: SiteInvitationDto = {
      ...baseInvitation,
      id: 'inv-created-failed',
      inviteeEmail: 'created-failed@example.com',
      lastSendAttemptAt: '2026-05-08T12:00:00Z',
      lastSendStatus: 'FAILED',
      lastSendError: 'SMTP send failed: 535 bad credentials',
      sendAttemptCount: 1,
      lastSentAt: null,
    };
    mockedService.createInvitation.mockResolvedValueOnce(created);

    renderPage();
    await screen.findByText(/No invitations found/);

    fireEvent.click(screen.getByRole('button', { name: 'Invite' }));
    const dialog = await screen.findByRole('dialog', { name: 'Invite to Site' });
    fireEvent.change(within(dialog).getByLabelText(/Email address/), {
      target: { value: created.inviteeEmail },
    });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Send Invitation' }));

    await waitFor(() => {
      expect(mockedService.createInvitation).toHaveBeenCalledWith(SITE_ID, {
        inviteeEmail: created.inviteeEmail,
        invitedRole: 'CONSUMER',
        message: undefined,
      });
    });

    expect(toast.success).not.toHaveBeenCalledWith(expect.stringContaining(created.inviteeEmail));
    expect(toast.error).toHaveBeenCalledWith(
      expect.stringContaining('Invitation created for created-failed@example.com, but email send failed'),
    );
    expect(await screen.findByText(created.inviteeEmail)).toBeTruthy();
    expect(screen.getByTestId(`invitation-send-error-${created.id}`).textContent).toContain(
      'SMTP send failed',
    );
  });
});

describe('SiteInvitationsPage Resend confirmation flow', () => {
  beforeEach(() => {
    mockedService.listInvitations.mockResolvedValue([
      neverSentInvitation,
    ]);
  });

  test('confirms then calls service and replaces the row from the redacted response', async () => {
    const updated: SiteInvitationDto = {
      ...neverSentInvitation,
      lastSendAttemptAt: '2026-05-07T12:00:00Z',
      lastSendStatus: 'SENT',
      sendAttemptCount: 1,
      lastSentAt: '2026-05-07T12:00:00Z',
    };
    mockedService.resendInvitation.mockResolvedValueOnce(updated);

    renderPage();
    await screen.findByText('never@example.com');

    fireEvent.click(screen.getByRole('button', { name: 'Resend invitation to never@example.com' }));

    const dialog = await screen.findByRole('dialog', { name: 'Resend invitation' });
    expect(within(dialog).getByText(/never@example.com/)).toBeTruthy();

    fireEvent.click(within(dialog).getByRole('button', { name: 'Resend' }));

    await waitFor(() => {
      expect(mockedService.resendInvitation).toHaveBeenCalledWith(SITE_ID, neverSentInvitation.id);
    });

    // Dialog closes on success.
    await waitFor(() => {
      expect(screen.queryByRole('dialog', { name: 'Resend invitation' })).toBeNull();
    });

    // Row updated: attempt count flips from 0 to 1 and SENT chip appears.
    expect(await screen.findByText(/attempts: 1/)).toBeTruthy();
    expect(screen.getByText('SENT')).toBeTruthy();
  });

  test('keeps the dialog open and surfaces DTO failure when resend returns FAILED', async () => {
    const updated: SiteInvitationDto = {
      ...neverSentInvitation,
      lastSendAttemptAt: '2026-05-08T13:00:00Z',
      lastSendStatus: 'FAILED',
      lastSendError: 'SMTP send failed: 535 bad credentials',
      sendAttemptCount: 1,
      lastSentAt: null,
    };
    mockedService.resendInvitation.mockResolvedValueOnce(updated);

    renderPage();
    await screen.findByText('never@example.com');

    fireEvent.click(screen.getByRole('button', { name: 'Resend invitation to never@example.com' }));
    const dialog = await screen.findByRole('dialog', { name: 'Resend invitation' });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Resend' }));

    const errorAlert = await within(dialog).findByTestId('resend-invitation-error');
    expect(errorAlert.textContent).toContain('SMTP send failed: 535 bad credentials');
    expect(toast.success).not.toHaveBeenCalledWith(expect.stringContaining('never@example.com'));
    expect(toast.error).toHaveBeenCalledWith(
      expect.stringContaining('Resend attempted for never@example.com, but email send failed'),
    );
    expect(screen.getByRole('dialog', { name: 'Resend invitation' })).toBeTruthy();
    expect(screen.getByTestId(`invitation-send-error-${updated.id}`).textContent).toContain(
      'SMTP send failed',
    );
    expect(screen.getByText('FAILED')).toBeTruthy();
  });

  test('cancelling the confirmation dialog does not call the service', async () => {
    renderPage();
    await screen.findByText('never@example.com');

    fireEvent.click(screen.getByRole('button', { name: 'Resend invitation to never@example.com' }));
    const dialog = await screen.findByRole('dialog', { name: 'Resend invitation' });

    fireEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }));

    await waitFor(() => {
      expect(screen.queryByRole('dialog', { name: 'Resend invitation' })).toBeNull();
    });
    expect(mockedService.resendInvitation).not.toHaveBeenCalled();
  });

  test('surfaces backend error message inside the dialog when the service rejects', async () => {
    mockedService.resendInvitation.mockRejectedValueOnce({
      response: { data: { message: 'INVITATION_RESEND_FAILED: SMTP timeout' } },
    });

    renderPage();
    await screen.findByText('never@example.com');

    fireEvent.click(screen.getByRole('button', { name: 'Resend invitation to never@example.com' }));
    const dialog = await screen.findByRole('dialog', { name: 'Resend invitation' });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Resend' }));

    const errorAlert = await within(dialog).findByTestId('resend-invitation-error');
    expect(errorAlert.textContent).toContain('INVITATION_RESEND_FAILED: SMTP timeout');
    // Page remains mounted.
    expect(screen.getByText('Site Invitations')).toBeTruthy();
  });

  test('surfaces the synthetic shape-guard message when the service rejects with the Phase 5 HTML-fallback error', async () => {
    // Simulates the service's Phase 5 Mocked HTML-fallback shape guard
    // throwing a synthetic Error: when api.post resolves with the SPA HTML
    // string, the service rejects with this exact message rather than
    // letting the page touch a non-DTO value. The page renders it via
    // resolveErrorMessage's Error.message branch.
    mockedService.resendInvitation.mockRejectedValueOnce(
      new Error(SITE_INVITATION_RESEND_UNEXPECTED_RESPONSE_MESSAGE),
    );

    renderPage();
    await screen.findByText('never@example.com');

    fireEvent.click(screen.getByRole('button', { name: 'Resend invitation to never@example.com' }));
    const dialog = await screen.findByRole('dialog', { name: 'Resend invitation' });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Resend' }));

    const errorAlert = await within(dialog).findByTestId('resend-invitation-error');
    expect(errorAlert.textContent).toContain(SITE_INVITATION_RESEND_UNEXPECTED_RESPONSE_MESSAGE);
    // Page is still mounted — title remains in the DOM.
    expect(screen.getByText('Site Invitations')).toBeTruthy();
  });
});
