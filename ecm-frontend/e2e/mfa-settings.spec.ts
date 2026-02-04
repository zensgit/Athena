import { expect, Page, test } from '@playwright/test';
import { fetchAccessToken, waitForApiReady } from './helpers/api';
import crypto from 'crypto';

const baseApiUrl = process.env.ECM_API_URL || 'http://localhost:7700';
const baseUiUrl = process.env.ECM_UI_URL || 'http://localhost:5500';
const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';

async function loginWithCredentials(page: Page, username: string, password: string, token?: string) {
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

  await page.goto(`${baseUiUrl}/login`, { waitUntil: 'domcontentloaded' });

  for (let attempt = 0; attempt < 3; attempt += 1) {
    await page.waitForURL(/(\/login$|\/browse\/|\/protocol\/openid-connect\/auth|login_required)/, { timeout: 60_000 });

    if (page.url().endsWith('/login')) {
      const keycloakButton = page.getByRole('button', { name: /sign in with keycloak/i });
      try {
        await keycloakButton.waitFor({ state: 'visible', timeout: 30_000 });
        await keycloakButton.click();
      } catch {
        // retry
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

function base32Decode(input: string) {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  const cleaned = input.replace(/=+$/, '').toUpperCase();
  let bits = 0;
  let value = 0;
  const output: number[] = [];
  for (const char of cleaned) {
    const idx = alphabet.indexOf(char);
    if (idx < 0) {
      continue;
    }
    value = (value << 5) | idx;
    bits += 5;
    if (bits >= 8) {
      output.push((value >>> (bits - 8)) & 0xff);
      bits -= 8;
    }
  }
  return Buffer.from(output);
}

function generateTotp(secret: string, timestampMs = Date.now()) {
  const counter = Math.floor(timestampMs / 1000 / 30);
  const buffer = Buffer.alloc(8);
  buffer.writeBigInt64BE(BigInt(counter));
  const key = base32Decode(secret);
  const hmac = crypto.createHmac('sha1', key).update(buffer).digest();
  const offset = hmac[hmac.length - 1] & 0x0f;
  const binary = ((hmac[offset] & 0x7f) << 24)
    | ((hmac[offset + 1] & 0xff) << 16)
    | ((hmac[offset + 2] & 0xff) << 8)
    | (hmac[offset + 3] & 0xff);
  const otp = binary % 1_000_000;
  return otp.toString().padStart(6, '0');
}

test.beforeEach(async ({ request }) => {
  await waitForApiReady(request, { apiUrl: baseApiUrl });
});

test('Settings reflects local MFA enable/disable', async ({ page, request }) => {
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);

  const enrollRes = await request.post(`${baseApiUrl}/api/v1/mfa/enroll`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(enrollRes.ok()).toBeTruthy();
  const enrollment = await enrollRes.json() as { secret: string };

  const verifyCode = generateTotp(enrollment.secret);
  const verifyRes = await request.post(`${baseApiUrl}/api/v1/mfa/verify`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { code: verifyCode },
  });
  expect(verifyRes.ok()).toBeTruthy();
  const verifyPayload = await verifyRes.json() as { verified: boolean };
  expect(verifyPayload.verified).toBeTruthy();

  await loginWithCredentials(page, defaultUsername, defaultPassword, token);
  await page.goto(`${baseUiUrl}/settings`, { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('button', { name: 'Disable Local MFA' })).toBeEnabled({ timeout: 60_000 });

  const disableCode = generateTotp(enrollment.secret);
  const disableRes = await request.post(`${baseApiUrl}/api/v1/mfa/disable`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { code: disableCode },
  });
  expect(disableRes.ok()).toBeTruthy();
  const disablePayload = await disableRes.json() as { disabled: boolean };
  expect(disablePayload.disabled).toBeTruthy();

  await page.reload({ waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('button', { name: 'Disable Local MFA' })).toBeDisabled({ timeout: 60_000 });
});
