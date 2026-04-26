// NOTE: NotificationsPage (/notifications) is an inbox page, not an email-preferences
// page. The original spec requested email preference toggles, but notificationService
// has no preference endpoints — only inbox CRUD. These tests cover what the page
// actually renders: inbox list, mark-all-read, and the Unread/All mode toggle.
import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';
import { mockKeycloakUnreachable } from './helpers/keycloakMock';

const UNREAD_PAGE = {
  content: [
    {
      id: 'n-1',
      activityType: 'document_shared',
      actorUserId: 'alice',
      siteId: null,
      nodeId: 'node-abc',
      nodeName: 'Contract.pdf',
      summary: {},
      read: false,
      readAt: null,
      createdAt: '2026-04-26T08:00:00Z',
    },
    {
      id: 'n-2',
      activityType: 'comment_added',
      actorUserId: 'bob',
      siteId: null,
      nodeId: 'node-def',
      nodeName: 'Report.docx',
      summary: {},
      read: false,
      readAt: null,
      createdAt: '2026-04-26T07:00:00Z',
    },
  ],
  totalElements: 2,
  totalPages: 1,
  number: 0,
  size: 20,
};

const EMPTY_PAGE = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size: 20,
};

const ALL_PAGE = {
  content: [
    {
      id: 'n-3',
      activityType: 'document_created',
      actorUserId: 'carol',
      siteId: null,
      nodeId: 'node-ghi',
      nodeName: 'Memo.txt',
      summary: {},
      read: true,
      readAt: '2026-04-26T06:30:00Z',
      createdAt: '2026-04-26T06:00:00Z',
    },
  ],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 20,
};

function registerCommonMocks(page: import('@playwright/test').Page, opts: {
  unreadPage?: object;
  allPage?: object;
  unreadCount?: number;
} = {}) {
  const {
    unreadPage = UNREAD_PAGE,
    allPage = ALL_PAGE,
    unreadCount = 2,
  } = opts;

  // Catch-all for all-mode inbox — registered FIRST (lowest priority in LIFO).
  // Uses route.fallback() to pass /unread and /mark-all-read to the specific handlers below.
  page.route('**/api/v1/notifications**', async (route) => {
    const url = route.request().url();
    if (url.includes('/unread') || url.includes('/mark-all-read')) {
      await route.fallback();
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(allPage),
    });
  });

  // Unread list — registered second (medium priority in LIFO).
  // **/unread** also matches /unread-count, so unread-count must be registered LAST
  // (highest priority) to win over this handler.
  page.route('**/api/v1/notifications/unread**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(unreadPage),
    });
  });

  // Unread-count — registered LAST (highest priority in LIFO) so it beats /unread**
  page.route('**/api/v1/notifications/unread-count**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ count: unreadCount }),
    });
  });
}

test('shows notification inbox list with activity type chips', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  registerCommonMocks(page);

  await page.goto('/notifications');

  // Page heading
  await expect(page.getByRole('heading', { name: /notifications/i })).toBeVisible();

  // Activity-type chips from the two unread items
  await expect(page.getByText('document_shared', { exact: true })).toBeVisible();
  await expect(page.getByText('comment_added', { exact: true })).toBeVisible();

  // Node names rendered in the card body
  await expect(page.getByText('Contract.pdf', { exact: true })).toBeVisible();
  await expect(page.getByText('Report.docx', { exact: true })).toBeVisible();
});

test('mark all read calls POST /notifications/mark-all-read', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  registerCommonMocks(page);

  let markAllReadCalled = false;
  await page.route('**/api/v1/notifications/mark-all-read', async (route) => {
    markAllReadCalled = true;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ marked: 2 }),
    });
  });

  await page.goto('/notifications');

  // "Mark All Read" button is visible because unreadCount > 0
  const markAllBtn = page.getByRole('button', { name: /Mark All Read/i });
  await expect(markAllBtn).toBeVisible();
  await markAllBtn.click();

  expect(markAllReadCalled).toBe(true);
});

test('switching to All mode re-fetches from GET /notifications', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  registerCommonMocks(page);

  await page.goto('/notifications');

  // Initially in Unread mode — unread notifications are visible
  await expect(page.getByText('document_shared', { exact: true })).toBeVisible();

  // Click the "All" toggle button
  const allBtn = page.getByRole('button', { name: /^All$/ });
  await expect(allBtn).toBeVisible();
  await allBtn.click();

  // After switching to All mode the page should show the item from ALL_PAGE
  await expect(page.getByText('document_created', { exact: true })).toBeVisible();
  await expect(page.getByText('Memo.txt', { exact: true })).toBeVisible();
});

test('shows empty state when there are no unread notifications', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  registerCommonMocks(page, { unreadPage: EMPTY_PAGE, unreadCount: 0 });

  await page.goto('/notifications');

  // The empty-state Paper shows "No unread notifications"
  await expect(page.getByText('No unread notifications')).toBeVisible();

  // When unreadCount is 0 the "Mark All Read" button must NOT appear
  await expect(page.getByRole('button', { name: /Mark All Read/i })).not.toBeVisible();
});
