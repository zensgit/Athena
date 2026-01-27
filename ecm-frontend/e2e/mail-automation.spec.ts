import { expect, Page, test } from '@playwright/test';
import { fetchAccessToken, waitForApiReady } from './helpers/api';

const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';
const apiUrl = process.env.ECM_API_URL || 'http://localhost:7700';

async function loginWithCredentials(page: Page, username: string, password: string) {
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

  await loginWithCredentials(page, defaultUsername, defaultPassword);
  await page.goto('/admin/mail', { waitUntil: 'domcontentloaded' });
  await page.waitForURL(/\/admin\/mail/, { timeout: 60_000 });

  const heading = page.getByRole('heading', { name: /mail automation/i });
  await expect(heading).toBeVisible({ timeout: 60_000 });

  const oauthMissingChip = page.getByText(/OAuth env missing/i);
  if (hasOauthMissing) {
    await expect(oauthMissingChip.first()).toBeVisible({ timeout: 30_000 });
  } else {
    await expect(oauthMissingChip).toHaveCount(0);
  }

  const linkIcon = page.locator('svg[data-testid="LinkIcon"]').first();
  await expect(linkIcon).toBeVisible({ timeout: 30_000 });
  await linkIcon.locator('xpath=ancestor::button[1]').click();

  const connectionToast = page.locator('.Toastify__toast').last();
  await expect(connectionToast).toContainText(/Connection (OK|failed)|Failed to test connection/i, { timeout: 60_000 });

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

  await loginWithCredentials(page, defaultUsername, defaultPassword);
  await page.goto('/admin/mail', { waitUntil: 'domcontentloaded' });
  await page.waitForURL(/\/admin\/mail/, { timeout: 60_000 });

  const listFoldersButton = page.getByRole('button', { name: /list folders/i });
  await expect(listFoldersButton).toBeEnabled({ timeout: 30_000 });
  await listFoldersButton.click();

  await expect(page.getByText(/Available folders \(/i)).toBeVisible({ timeout: 60_000 });

  const ruleRow = page.getByRole('row', { name: new RegExp(ruleName, 'i') }).first();
  await expect(ruleRow).toBeVisible({ timeout: 30_000 });
  await ruleRow.getByRole('button', { name: /edit/i }).click();

  await expect(page.getByRole('dialog')).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText(/IMAP folder name\(s\), comma-separated, default INBOX/i)).toBeVisible({
    timeout: 30_000,
  });

  await page.getByRole('button', { name: /cancel/i }).click();
});
