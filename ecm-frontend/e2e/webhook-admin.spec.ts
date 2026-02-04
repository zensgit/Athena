import { expect, Page, test } from '@playwright/test';
import { fetchAccessToken, waitForApiReady } from './helpers/api';
import crypto from 'crypto';
import http from 'http';

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

test.beforeEach(async ({ request }) => {
  await waitForApiReady(request, { apiUrl });
});

test('Webhook subscriptions can be created, tested, and deleted', async ({ page, request }) => {
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  await loginWithCredentials(page, defaultUsername, defaultPassword, token);

  const received: Array<{ headers: http.IncomingHttpHeaders; body: string }> = [];
  const server = http.createServer((req, res) => {
    let body = '';
    req.on('data', (chunk) => {
      body += chunk.toString();
    });
    req.on('end', () => {
      received.push({ headers: req.headers, body });
      res.writeHead(200, { 'Content-Type': 'text/plain' });
      res.end('ok');
    });
  });

  await new Promise<void>((resolve) => {
    server.listen(0, '0.0.0.0', () => resolve());
  });

  const address = server.address();
  if (!address || typeof address === 'string') {
    server.close();
    throw new Error('Failed to bind webhook test server');
  }

  const port = address.port;
  const name = `e2e-webhook-${Date.now()}`;
  const secret = `e2e-secret-${Date.now()}`;
  const endpointUrl = `http://host.docker.internal:${port}/webhook`;

  try {
    await page.goto('/admin/webhooks', { waitUntil: 'domcontentloaded' });
    await page.waitForURL(/\/admin\/webhooks/, { timeout: 60_000 });
    await expect(page.getByRole('heading', { name: /webhook subscriptions/i })).toBeVisible({ timeout: 60_000 });

    await page.getByLabel('Name').fill(name);
    await page.getByLabel('Endpoint URL').fill(endpointUrl);
    await page.getByLabel('Signing Secret (optional)').fill(secret);

    const createButton = page.getByRole('button', { name: /^create$/i });
    await expect(createButton).toBeEnabled({ timeout: 30_000 });
    await createButton.click();
    await expect(page.locator('.Toastify__toast').last()).toContainText(/Webhook subscription created/i, {
      timeout: 60_000,
    });

    const row = page.getByRole('row', { name: new RegExp(name, 'i') });
    await expect(row).toBeVisible({ timeout: 60_000 });

    await row.getByRole('button', { name: /send test/i }).click();
    await expect(page.locator('.Toastify__toast').last()).toContainText(/Test event dispatched|Failed to send test event/i, {
      timeout: 60_000,
    });

    await expect.poll(() => received.length, { timeout: 60_000 }).toBeGreaterThan(0);

    const event = received[0];
    const signatureHeader = event.headers['x-ecm-signature'];
    const eventHeader = event.headers['x-ecm-event'];
    const signature = Array.isArray(signatureHeader) ? signatureHeader[0] : signatureHeader;
    const eventType = Array.isArray(eventHeader) ? eventHeader[0] : eventHeader;

    expect(eventType).toBe('TEST');
    expect(signature).toBeTruthy();

    const expectedSignature = crypto.createHmac('sha256', secret).update(event.body).digest('base64');
    expect(signature).toBe(expectedSignature);

    page.once('dialog', (dialog) => dialog.accept());
    await row.getByRole('button', { name: /delete/i }).click();
    await expect(page.locator('.Toastify__toast').last()).toContainText(/Webhook deleted|Failed to delete webhook/i, {
      timeout: 60_000,
    });
    await expect(row).toHaveCount(0, { timeout: 60_000 });
  } finally {
    server.close();
  }
});
