import { expect, test } from '@playwright/test';
import { fetchAccessToken, waitForApiReady } from './helpers/api';
import { gotoWithAuthE2E } from './helpers/login';

const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';
const apiUrl = process.env.ECM_API_URL || 'http://localhost:7700';

test('Mail automation test connection and fetch summary', async ({ page, request }) => {
  await waitForApiReady(request);
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const accountsRes = await request.get(`${apiUrl}/api/v1/integration/mail/accounts`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(accountsRes.ok()).toBeTruthy();
  const accounts = (await accountsRes.json()) as Array<{
    id: string;
    security?: string;
    oauthEnvConfigured?: boolean;
  }>;
  test.skip(!accounts.length, 'No mail accounts configured');
  const hasOauthMissing = accounts.some(
    (account) => account.security === 'OAUTH2' && account.oauthEnvConfigured === false,
  );

  await gotoWithAuthE2E(page, '/admin/mail', defaultUsername, defaultPassword, { token });
  await page.waitForURL(/\/admin\/mail/, { timeout: 60_000 });

  const heading = page.getByRole('heading', { name: /mail automation/i });
  await expect(heading).toBeVisible({ timeout: 60_000 });

  const summarySection = page
    .getByRole('heading', { name: /connection summary/i })
    .locator('xpath=ancestor::div[contains(@class,"MuiCardContent-root")]');
  await expect(summarySection).toBeVisible({ timeout: 60_000 });
  await expect(summarySection.getByRole('button', { name: /test connection/i })).toBeVisible({ timeout: 30_000 });

  const oauthMissingChip = page.getByText(/OAuth env missing/i);
  if (hasOauthMissing) {
    await expect(oauthMissingChip.first()).toBeVisible({ timeout: 30_000 });
  } else {
    await expect(oauthMissingChip).toHaveCount(0);
  }

  const testButton = page.getByRole('button', { name: /test connection/i }).first();
  await expect(testButton).toBeVisible({ timeout: 30_000 });
  if (hasOauthMissing) {
    await expect(testButton).toBeDisabled();
    await expect(page.getByText(/OAuth env missing — test connection disabled/i)).toBeVisible({ timeout: 30_000 });
  } else {
    await expect(testButton).toBeEnabled();
    await testButton.click();
    const connectionToast = page.locator('.Toastify__toast').last();
    await expect(connectionToast).toContainText(/Connection (OK|failed)|Failed to test connection/i, { timeout: 60_000 });
  }

  const triggerButton = page.getByRole('button', { name: /trigger fetch/i });
  await expect(triggerButton).toBeEnabled({ timeout: 30_000 });
  await triggerButton.click();

  const fetchToast = page.locator('.Toastify__toast').last();
  await expect(fetchToast).toContainText(/Processed|Failed to trigger mail fetch/i, { timeout: 60_000 });

  const diagnosticsButton = page.getByRole('button', { name: /run diagnostics/i });
  await expect(diagnosticsButton).toBeEnabled({ timeout: 30_000 });
  await diagnosticsButton.click();

  const diagnosticsToast = page.locator('.Toastify__toast').last();
  await expect(diagnosticsToast).toContainText(/Diagnostics complete|Failed to run mail diagnostics/i, {
    timeout: 60_000,
  });
});

test('Mail automation lists folders and shows folder helper text', async ({ page, request }) => {
  await waitForApiReady(request);
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const accountsRes = await request.get(`${apiUrl}/api/v1/integration/mail/accounts`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(accountsRes.ok()).toBeTruthy();
  const accounts = (await accountsRes.json()) as Array<{ id: string }>;
  test.skip(!accounts.length, 'No mail accounts configured');
  const rulesRes = await request.get(`${apiUrl}/api/v1/integration/mail/rules`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(rulesRes.ok()).toBeTruthy();
  const rules = (await rulesRes.json()) as Array<{ name: string }>;
  test.skip(!rules.length, 'No mail rules configured');
  const ruleName = rules[0].name;

  await gotoWithAuthE2E(page, '/admin/mail', defaultUsername, defaultPassword, { token });
  await page.waitForURL(/\/admin\/mail/, { timeout: 60_000 });

  const listFoldersButton = page.getByRole('button', { name: /list folders/i });
  await expect(listFoldersButton).toBeEnabled({ timeout: 30_000 });
  await listFoldersButton.click();

  const availableFoldersText = page.getByText(/Available folders/i);
  const listFoldersFailedToast = page.locator('.Toastify__toast').filter({ hasText: /Failed to list folders/i }).last();
  await Promise.race([
    availableFoldersText.waitFor({ state: 'visible', timeout: 60_000 }),
    listFoldersFailedToast.waitFor({ state: 'visible', timeout: 60_000 }),
  ]);

  const rulesSection = page
    .getByRole('heading', { name: /mail rules/i })
    .locator('xpath=ancestor::div[contains(@class,"MuiCardContent-root")]');
  const ruleRow = rulesSection.getByRole('row', { name: new RegExp(ruleName, 'i') }).first();
  await expect(ruleRow).toBeVisible({ timeout: 30_000 });
  await ruleRow.getByRole('button', { name: /edit/i }).click();

  await expect(page.getByRole('dialog')).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText(/IMAP folder name\(s\), comma-separated, default INBOX/i)).toBeVisible({
    timeout: 30_000,
  });

  await page.getByRole('button', { name: /cancel/i }).click();
});

test('Mail automation diagnostics filters can be cleared', async ({ page, request }) => {
  await waitForApiReady(request);
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const accountsRes = await request.get(`${apiUrl}/api/v1/integration/mail/accounts`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(accountsRes.ok()).toBeTruthy();
  const accounts = (await accountsRes.json()) as Array<{ id: string; name: string }>;
  test.skip(!accounts.length, 'No mail accounts configured');

  await gotoWithAuthE2E(page, '/admin/mail#diagnostics', defaultUsername, defaultPassword, { token });
  await page.waitForURL(/\/admin\/mail/, { timeout: 60_000 });

  const statusSelect = page.getByRole('combobox', { name: 'Status' });
  await statusSelect.click();
  const errorOption = page.getByRole('option', { name: 'Error' });
  await errorOption.click();
  await statusSelect.click();
  await expect(errorOption).toHaveAttribute('aria-selected', 'true');
  await page.keyboard.press('Escape');

  const subjectInput = page.getByLabel('Subject contains');
  await subjectInput.fill('e2e');
  await expect(subjectInput).toHaveValue('e2e');

  const clearButton = page.getByTestId('diagnostics-clear-filters');
  await expect(clearButton).toBeEnabled({ timeout: 30_000 });
  await clearButton.click();

  await expect(subjectInput).toHaveValue('');
  await statusSelect.click();
  const allOption = page.getByRole('option', { name: 'All Statuses' });
  await expect(allOption).toHaveAttribute('aria-selected', 'true');
  await page.keyboard.press('Escape');
});

test('Mail automation processed item can show ingested documents dialog', async ({ page, request }) => {
  await waitForApiReady(request);
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);

  // Avoid flaky UI-based skipping by checking server state first.
  const diagnosticsRes = await request.get(`${apiUrl}/api/v1/integration/mail/diagnostics?limit=1`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(diagnosticsRes.ok()).toBeTruthy();
  const diagnostics = (await diagnosticsRes.json()) as { recentProcessed?: unknown[] };
  test.skip(!diagnostics.recentProcessed?.length, 'No processed messages available to view ingested documents');

  await gotoWithAuthE2E(page, '/admin/mail#diagnostics', defaultUsername, defaultPassword, { token });
  await page.waitForURL(/\/admin\/mail/, { timeout: 60_000 });

  const viewDocsButtons = page.getByRole('button', { name: /view ingested documents/i });
  await expect(viewDocsButtons.first()).toBeVisible({ timeout: 30_000 });

  await viewDocsButtons.first().click();

  const dialog = page.getByRole('dialog');
  await expect(dialog).toContainText(/Ingested Documents/i, { timeout: 30_000 });

  const noDocs = dialog.getByText(/No mail documents found for this message/i);
  const docsTable = dialog.locator('table');
  const loadError = dialog.getByText(/Failed to load ingested mail documents/i);
  await Promise.race([
    noDocs.waitFor({ state: 'visible', timeout: 60_000 }),
    docsTable.waitFor({ state: 'visible', timeout: 60_000 }),
    loadError.waitFor({ state: 'visible', timeout: 60_000 }),
  ]);

  await dialog.getByRole('button', { name: /^close$/i }).click();
  await expect(dialog).toHaveCount(0);
});

test('Mail automation rule diagnostics drawer opens from leaderboard', async ({ page, request }) => {
  await waitForApiReady(request);
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  await gotoWithAuthE2E(page, '/admin/mail#diagnostics', defaultUsername, defaultPassword, { token });
  await page.waitForURL(/\/admin\/mail/, { timeout: 60_000 });

  const openDrawerButtons = page.locator('button:visible', { hasText: /open drawer/i });
  const buttonCount = await openDrawerButtons.count();
  test.skip(buttonCount === 0, 'No rule errors available to open diagnostics drawer');

  await openDrawerButtons.first().click();
  const applyToMainFiltersButton = page.getByRole('button', { name: /apply to main filters/i });
  await expect(applyToMainFiltersButton).toBeVisible({ timeout: 30_000 });

  await page.getByRole('button', { name: /^close$/i }).first().click();
  await expect(applyToMainFiltersButton).toHaveCount(0);
});

test('Mail automation can replay failed processed item', async ({ page, request }) => {
  await waitForApiReady(request);
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  await gotoWithAuthE2E(page, '/admin/mail#diagnostics', defaultUsername, defaultPassword, { token });
  await page.waitForURL(/\/admin\/mail/, { timeout: 60_000 });

  const replayButtons = page.getByRole('button', { name: /^replay$/i });
  const count = await replayButtons.count();
  test.skip(count === 0, 'No failed processed items to replay');

  await replayButtons.first().click();
  const toastMessage = page.locator('.Toastify__toast').last();
  await expect(toastMessage).toContainText(
    /Replay processed successfully|Replay finished|Replay skipped|Failed to replay processed mail/i,
    { timeout: 60_000 }
  );
});

test('Mail automation runtime health panel renders', async ({ page, request }) => {
  await waitForApiReady(request);
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  await gotoWithAuthE2E(page, '/admin/mail', defaultUsername, defaultPassword, { token });
  await page.waitForURL(/\/admin\/mail/, { timeout: 60_000 });

  const heading = page.getByRole('heading', { name: /runtime health/i });
  await expect(heading).toBeVisible({ timeout: 30_000 });
  const card = heading.locator('xpath=ancestor::div[contains(@class,"MuiCardContent-root")]');
  await expect(card).toContainText(/Status/i, { timeout: 30_000 });
  await card.getByRole('button', { name: /refresh/i }).first().click();

  const runtimeTopErrors = page.locator('[data-testid^="runtime-top-error-"]');
  const topErrorCount = await runtimeTopErrors.count();
  test.skip(topErrorCount === 0, 'No runtime top-error items available to verify diagnostics linkage');

  await runtimeTopErrors.first().click();
  await expect(page).toHaveURL(/#diagnostics/, { timeout: 30_000 });

  const statusSelect = page.getByRole('combobox', { name: 'Status' });
  await expect(statusSelect).toContainText(/Error/i, { timeout: 30_000 });
  const errorFilterInput = page.getByLabel('Error contains');
  await expect(errorFilterInput).not.toHaveValue('');
});

test('Mail automation diagnostics export scope snapshot renders', async ({ page, request }) => {
  await waitForApiReady(request);
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  await gotoWithAuthE2E(page, '/admin/mail#diagnostics', defaultUsername, defaultPassword, { token });
  await page.waitForURL(/\/admin\/mail/, { timeout: 60_000 });

  await expect(page.getByText(/Export scope snapshot:/i)).toBeVisible({ timeout: 30_000 });
});

test('Mail automation mail-documents similar action navigates to search', async ({ page, request }) => {
  await waitForApiReady(request);
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  await gotoWithAuthE2E(page, '/admin/mail#diagnostics', defaultUsername, defaultPassword, { token });
  await page.waitForURL(/\/admin\/mail/, { timeout: 60_000 });

  const similarButtons = page.getByRole('button', { name: /find similar documents/i });
  const count = await similarButtons.count();
  test.skip(count === 0, 'No mail documents available for similar-action navigation');

  await similarButtons.first().click();
  await page.waitForURL(/\/search/, { timeout: 30_000 });
});

test('Mail automation reporting panel renders', async ({ page, request }) => {
  await waitForApiReady(request);
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);

  await gotoWithAuthE2E(page, '/admin/mail', defaultUsername, defaultPassword, { token });
  await page.waitForURL(/\/admin\/mail/, { timeout: 60_000 });

  const reportHeading = page.getByRole('heading', { name: /mail reporting/i });
  await expect(reportHeading).toBeVisible({ timeout: 60_000 });

  const reportCard = reportHeading.locator('xpath=ancestor::div[contains(@class,\"MuiCardContent-root\")]');
  await expect(reportCard).toContainText(/No report data available yet|Processed/i, { timeout: 60_000 });

  const refreshButton = reportCard.getByRole('button', { name: /refresh/i }).first();
  await expect(refreshButton).toBeEnabled({ timeout: 30_000 });
  await refreshButton.click();

  await expect(reportCard).toContainText(/No report data available yet|Daily trend/i, { timeout: 60_000 });
});
