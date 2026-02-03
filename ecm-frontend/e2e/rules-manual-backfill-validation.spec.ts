import { expect, Page, test } from '@playwright/test';
import { fetchAccessToken, waitForApiReady } from './helpers/api';

const baseApiUrl = process.env.ECM_API_URL || 'http://localhost:7700';
const baseUiUrl = process.env.ECM_UI_URL || 'http://localhost:5500';
const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';

async function loginWithCredentials(page: Page, username: string, password: string, token?: string) {
  if (process.env.ECM_E2E_SKIP_LOGIN === '1') {
    const resolvedToken = token ?? await fetchAccessToken(page.request, username, password);
    await page.addInitScript(
      ({ authToken, authUser }) => {
        window.localStorage.setItem('token', authToken);
        window.localStorage.setItem('ecm_e2e_bypass', '1');
        window.localStorage.setItem('user', JSON.stringify(authUser));
      },
      {
        authToken: resolvedToken,
        authUser: {
          id: `e2e-${username}`,
          username,
          email: `${username}@example.com`,
          roles: ['ROLE_ADMIN'],
        },
      }
    );
    return;
  }
  const authPattern = /\/protocol\/openid-connect\/auth/;
  const browsePattern = /\/browse\//;

  await page.goto(`${baseUiUrl}/login`, { waitUntil: 'domcontentloaded' });

  for (let attempt = 0; attempt < 3; attempt += 1) {
    await page.waitForURL(/(\/login$|\/browse\/|\/protocol\/openid-connect\/auth|login_required)/, {
      timeout: 60_000,
    });

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
      await page.goto(`${baseUiUrl}/login`, { waitUntil: 'domcontentloaded' });
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
    await page.goto(`${baseUiUrl}/browse/root`, { waitUntil: 'domcontentloaded' });
  }
  await page.waitForURL(browsePattern, { timeout: 60_000 });
}

test.beforeEach(async ({ request }) => {
  await waitForApiReady(request, { apiUrl: baseApiUrl });
});

test('rules: manual backfill blocks out-of-range values before POST', async ({ page, request }) => {
  test.setTimeout(180_000);

  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  await loginWithCredentials(page, defaultUsername, defaultPassword, token);
  await page.goto(`${baseUiUrl}/rules`, { waitUntil: 'domcontentloaded' });

  await page.getByRole('button', { name: 'New Rule' }).click();
  const dialog = page.getByRole('dialog').filter({ hasText: 'New Rule' });
  await expect(dialog).toBeVisible({ timeout: 60_000 });

  await dialog.getByLabel('Name').fill(`e2e-invalid-backfill-${Date.now()}`);

  await dialog.getByLabel('Trigger').click();
  await page.getByRole('option', { name: 'SCHEDULED' }).click();
  await expect(dialog.getByText('Schedule Configuration')).toBeVisible({ timeout: 60_000 });

  await dialog.getByLabel('Cron Preset').click();
  await page.getByRole('option', { name: 'Every hour' }).click();
  await expect(dialog.getByLabel('Cron Expression')).toHaveValue('0 0 * * * *', {
    timeout: 60_000,
  });

  await dialog.getByLabel('Manual Trigger Backfill (minutes)').fill('2000');

  const createRuleRequest = page
    .waitForRequest(
      (req) => req.url().includes('/api/v1/rules') && req.method() === 'POST',
      { timeout: 5_000 },
    )
    .then(() => true)
    .catch(() => false);

  await dialog.getByRole('button', { name: 'Save', exact: true }).click();

  await expect(page.getByText('Manual backfill minutes must be 1440 or less')).toBeVisible({
    timeout: 10_000,
  });
  await expect(dialog).toBeVisible();

  const requestSent = await createRuleRequest;
  expect(requestSent).toBeFalsy();
});
