import { expect, Page, test } from '@playwright/test';
import { fetchAccessToken, waitForApiReady } from './helpers/api';

const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';
const apiUrl = process.env.ECM_API_URL || 'http://localhost:7700';

async function loginWithCredentials(page: Page, username: string, password: string, token?: string) {
  if (process.env.ECM_E2E_SKIP_LOGIN === '1' && token) {
    await page.addInitScript(
      ({ authToken, authUser }) => {
        window.localStorage.setItem('token', authToken);
        window.localStorage.setItem('user', JSON.stringify(authUser));
        window.localStorage.setItem('ecm_e2e_bypass', '1');
      },
      {
        authToken: token,
        authUser: {
          id: `e2e-${username}`,
          username,
          email: `${username}@example.com`,
          roles: username === 'admin' ? ['ROLE_ADMIN'] : ['ROLE_VIEWER'],
        },
      }
    );
    return;
  }
  const authPattern = /\/protocol\/openid-connect\/auth/;
  const browsePattern = /\/browse\//;

  await page.goto('/login', { waitUntil: 'domcontentloaded' });

  for (let attempt = 0; attempt < 3; attempt += 1) {
    await page.waitForURL(/(\/login$|\/browse\/|\/protocol\/openid-connect\/auth|login_required)/, { timeout: 60_000 });

    if (page.url().endsWith('/login')) {
      const keycloakButton = page.getByRole('button', { name: /sign in with keycloak/i });
      try {
        await keycloakButton.waitFor({ state: 'visible', timeout: 30_000 });
        await keycloakButton.click();
      } catch {
        // Retry loop if login screen is not ready yet.
      }
      continue;
    }

    if (page.url().includes('login_required')) {
      await page.goto('/login', { waitUntil: 'domcontentloaded' });
      continue;
    }

    if (authPattern.test(page.url())) {
      await page.locator('#username').fill(username);
      await page.locator('#password').fill(password);
      await Promise.all([
        page.waitForNavigation({ waitUntil: 'domcontentloaded' }),
        page.locator('#kc-login').click(),
      ]);
    }

    if (browsePattern.test(page.url())) {
      break;
    }
  }

  if (!browsePattern.test(page.url())) {
    await page.goto('/browse/root', { waitUntil: 'domcontentloaded' });
  }

  await page.waitForURL(browsePattern, { timeout: 60_000 });
  await expect(page.getByText('Athena ECM')).toBeVisible({ timeout: 60_000 });
}

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

  await loginWithCredentials(page, defaultUsername, defaultPassword, token);
  await page.goto('/admin/mail', { waitUntil: 'domcontentloaded' });
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

  await loginWithCredentials(page, defaultUsername, defaultPassword, token);
  await page.goto('/admin/mail', { waitUntil: 'domcontentloaded' });
  await page.waitForURL(/\/admin\/mail/, { timeout: 60_000 });

  const listFoldersButton = page.getByRole('button', { name: /list folders/i });
  await expect(listFoldersButton).toBeEnabled({ timeout: 30_000 });
  await listFoldersButton.click();

  await expect(page.getByText(/Available folders \(/i)).toBeVisible({ timeout: 60_000 });

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

  await loginWithCredentials(page, defaultUsername, defaultPassword, token);
  await page.goto('/admin/mail#diagnostics', { waitUntil: 'domcontentloaded' });
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

test('Mail automation reporting panel renders', async ({ page, request }) => {
  await waitForApiReady(request);
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);

  await loginWithCredentials(page, defaultUsername, defaultPassword, token);
  await page.goto('/admin/mail', { waitUntil: 'domcontentloaded' });
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
