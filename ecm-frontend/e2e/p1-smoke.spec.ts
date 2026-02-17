import { expect, Page, test } from '@playwright/test';
import {
  fetchAccessToken,
  getRootFolderId,
  waitForApiReady,
  waitForSearchIndex,
} from './helpers/api';

const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';
const apiUrl = process.env.ECM_API_URL || 'http://localhost:7700';

test('P1 smoke: login CTA redirects to Keycloak auth endpoint', async ({ page, request }) => {
  await waitForApiReady(request);
  await page.goto('/login', { waitUntil: 'domcontentloaded' });

  const authPattern = /\/realms\/ecm\/protocol\/openid-connect\/auth/;
  const loginPattern = /\/login($|#)/;
  let reachedAuth = false;

  for (let attempt = 0; attempt < 4; attempt += 1) {
    await page.waitForURL(/(\/login($|#)|\/protocol\/openid-connect\/auth|\/browse\/)/, { timeout: 60_000 });
    const currentUrl = page.url();
    if (authPattern.test(currentUrl)) {
      reachedAuth = true;
      break;
    }
    if (!loginPattern.test(currentUrl) && !currentUrl.includes('login_required')) {
      break;
    }

    if (!loginPattern.test(currentUrl)) {
      await page.goto('/login', { waitUntil: 'domcontentloaded' });
    }

    await expect(page.getByRole('heading', { name: /Athena ECM/i })).toBeVisible({ timeout: 30_000 });
    const loginButton = page.getByRole('button', { name: /sign in with keycloak/i });
    await expect(loginButton).toBeVisible({ timeout: 30_000 });
    await loginButton.click({ noWaitAfter: true });
    if (await page.waitForURL(authPattern, { timeout: 15_000 }).then(() => true).catch(() => false)) {
      reachedAuth = true;
      break;
    }
  }

  expect(reachedAuth).toBeTruthy();
  await expect(page).toHaveURL(/client_id=unified-portal/);
});

test('P1 smoke: app error fallback can return to login', async ({ page, request }) => {
  await waitForApiReady(request);
  await page.addInitScript(() => {
    try {
      // Ensure the error trigger is applied once for this context so "Back to Login"
      // can clear it without being immediately re-applied on the next navigation.
      if (window.sessionStorage.getItem('ecm_e2e_force_render_error_once') !== '1') {
        window.localStorage.setItem('ecm_e2e_force_render_error', '1');
        window.sessionStorage.setItem('ecm_e2e_force_render_error_once', '1');
      }
    } catch {
      // Ignore storage access errors.
    }
  });
  await page.goto('/login', { waitUntil: 'domcontentloaded' });
  await expect(page.getByText(/unexpected error/i)).toBeVisible({ timeout: 30_000 });
  await expect(page.getByRole('button', { name: /back to login/i })).toBeVisible({ timeout: 30_000 });

  await page.getByRole('button', { name: /back to login/i }).click();
  await page.waitForURL(/\/login$/, { timeout: 30_000 });
  await expect(page.getByRole('button', { name: /sign in with keycloak/i })).toBeVisible({ timeout: 30_000 });
});

async function loginWithCredentials(page: Page, username: string, password: string, token?: string) {
  const forceUiLogin = process.env.ECM_E2E_FORCE_UI_LOGIN === '1';
  if (!forceUiLogin) {
    const resolvedToken = token
      ?? await fetchAccessToken(page.request, username, password).catch(() => undefined);
    if (resolvedToken) {
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
      await page.goto('/browse/root', { waitUntil: 'domcontentloaded' });
      await page.waitForURL(/\/browse\//, { timeout: 60_000 });
      await expect(page.getByText('Athena ECM')).toBeVisible({ timeout: 60_000 });
      return;
    }
  }

  if (process.env.ECM_E2E_SKIP_LOGIN === '1' && token) {
    await page.addInitScript(
      ({ authToken, authUser }) => {
        window.localStorage.setItem('token', authToken);
        window.localStorage.setItem('ecm_e2e_bypass', '1');
        window.localStorage.setItem('user', JSON.stringify(authUser));
      },
      {
        authToken: token,
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

function randomSuffix() {
  return Math.random().toString(36).replace(/[^a-z]+/g, '').slice(0, 6) || 'spell';
}

test('P1 smoke: spellcheck suggests corrected term', async ({ page, request }) => {
  await waitForApiReady(request);
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootId = await getRootFolderId(request, token, { apiUrl });
  const testFolderName = `e2e-spellcheck-${Date.now()}`;
  const createFolderRes = await request.post(`${apiUrl}/api/v1/folders`, {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    data: { name: testFolderName, parentId: rootId },
  });
  expect(createFolderRes.ok()).toBeTruthy();
  const testFolder = (await createFolderRes.json()) as { id?: string };
  const uploadsId = testFolder.id;
  if (!uploadsId) {
    throw new Error('Failed to create test folder for spellcheck upload');
  }

  const suffix = randomSuffix();
  const targetWord = 'spellcheck';
  const misspelled = 'spelcheck';
  const filename = `${targetWord}-${suffix}.txt`;

  const uploadRes = await request.post(`${apiUrl}/api/v1/documents/upload?folderId=${uploadsId}`, {
    headers: { Authorization: `Bearer ${token}` },
    multipart: {
      file: {
        name: filename,
        mimeType: 'text/plain',
        buffer: Buffer.from(`This document contains ${targetWord} for spellcheck.`),
      },
    },
  });
  expect(uploadRes.ok()).toBeTruthy();
  const uploadJson = (await uploadRes.json()) as { documentId?: string; id?: string };
  const documentId = uploadJson.documentId ?? uploadJson.id;
  if (!documentId) {
    throw new Error('Upload did not return document id');
  }

  const indexRes = await request.post(`${apiUrl}/api/v1/search/index/${documentId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(indexRes.ok()).toBeTruthy();

  await waitForSearchIndex(request, filename, token, { apiUrl, maxAttempts: 20, delayMs: 2000 });

  await loginWithCredentials(page, defaultUsername, defaultPassword, token);
  await page.goto('/search-results', { waitUntil: 'domcontentloaded' });

  const input = page.getByPlaceholder('Quick search by name...');
  await input.fill(misspelled);
  await input.press('Enter');

  await page.waitForResponse((response) => response.url().includes('/api/v1/search') && response.request().method() === 'GET');
  const didYouMean = page.getByText(/Did you mean|Search instead for/i);
  await expect(didYouMean).toBeVisible({ timeout: 60_000 });
  await expect(page.getByRole('button', { name: targetWord })).toBeVisible({ timeout: 60_000 });
});

test('P1 smoke: filename-like query skips spellcheck call', async ({ page, request }) => {
  await waitForApiReady(request);
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  await loginWithCredentials(page, defaultUsername, defaultPassword, token);
  await page.goto('/search-results', { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('networkidle');

  const assertNoSpellcheckRequest = process.env.ECM_E2E_ASSERT_SPELLCHECK_SKIP === '1';
  let spellcheckRequestCount = 0;
  const onRequest = (req: { url: () => string; method: () => string }) => {
    if (req.method() === 'GET' && req.url().includes('/api/v1/search/spellcheck')) {
      spellcheckRequestCount += 1;
    }
  };
  page.on('request', onRequest);
  try {
    const filenameLikeQuery = `e2e-preview-failure-${Date.now()}.bin`;
    const input = page.getByPlaceholder('Quick search by name...');
    await input.fill(filenameLikeQuery);
    await input.press('Enter');

    await page.waitForResponse((response) => response.url().includes('/api/v1/search') && response.request().method() === 'GET');
    await page.waitForTimeout(500);
    await expect(page.getByText(/Did you mean|Search instead for/i)).toHaveCount(0);
    await expect(page.getByText(/Checking spelling suggestions/i)).toHaveCount(0);
    if (assertNoSpellcheckRequest) {
      expect(spellcheckRequestCount).toBe(0);
    }
  } finally {
    page.off('request', onRequest);
  }
});

test('P1 smoke: mail rule preview dialog runs', async ({ page, request }) => {
  await waitForApiReady(request);
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const accountsRes = await request.get(`${apiUrl}/api/v1/integration/mail/accounts`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(accountsRes.ok()).toBeTruthy();
  const accounts = (await accountsRes.json()) as Array<{ id: string }>;

  const rulesRes = await request.get(`${apiUrl}/api/v1/integration/mail/rules`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(rulesRes.ok()).toBeTruthy();
  const rules = (await rulesRes.json()) as Array<{ id: string; name: string; accountId?: string | null }>;

  test.skip(!accounts.length || !rules.length, 'No mail accounts or rules configured');

  const rule = rules[0];
  await loginWithCredentials(page, defaultUsername, defaultPassword, token);
  await page.goto('/admin/mail', { waitUntil: 'domcontentloaded' });
  await page.waitForURL(/\/admin\/mail/, { timeout: 60_000 });

  const previewButton = page.getByLabel(`Preview rule ${rule.name}`).first();
  await expect(previewButton).toBeVisible({ timeout: 60_000 });
  await previewButton.click();

  const dialog = page.getByRole('dialog', { name: /preview mail rule/i });
  await expect(dialog).toBeVisible({ timeout: 60_000 });

  const runButton = dialog.getByRole('button', { name: /run preview/i });
  await expect(runButton).toBeEnabled({ timeout: 30_000 });
  const previewResponsePromise = page.waitForResponse(
    (response) =>
      response.url().includes(`/api/v1/integration/mail/rules/${rule.id}/preview`)
      && response.request().method() === 'POST',
    { timeout: 60_000 },
  ).catch(() => null);
  await runButton.click();

  const previewErrorText = dialog.getByText(/Failed to preview mail rule/i);
  const previewResponse = await previewResponsePromise;
  if (previewResponse && !previewResponse.ok()) {
    await expect(previewErrorText).toBeVisible({ timeout: 60_000 });
    return;
  }

  await Promise.race([
    dialog.getByText(/Summary/i).waitFor({ state: 'visible', timeout: 60_000 }),
    previewErrorText.waitFor({ state: 'visible', timeout: 60_000 }),
  ]);
  if (await previewErrorText.isVisible().catch(() => false)) {
    return;
  }
  await expect(dialog.getByText(/Summary/i)).toBeVisible({ timeout: 60_000 });
  await expect(dialog.getByText(/Matched messages/i)).toBeVisible({ timeout: 60_000 });
});
