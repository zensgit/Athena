import { expect, Page, test } from '@playwright/test';
import { fetchAccessToken, waitForApiReady } from './helpers/api';
import { loginWithCredentialsE2E } from './helpers/login';
import crypto from 'crypto';

const baseApiUrl = process.env.ECM_API_URL || 'http://localhost:7700';
const baseUiUrl = process.env.ECM_UI_URL || 'http://localhost:5500';
const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';

async function loginWithCredentials(page: Page, username: string, password: string, token?: string) {
  await loginWithCredentialsE2E(page, username, password, { token });
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
