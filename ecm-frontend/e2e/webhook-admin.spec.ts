import { APIRequestContext, expect, Page, test } from '@playwright/test';
import { fetchAccessToken, getRootFolderId, waitForApiReady } from './helpers/api';
import { loginWithCredentialsE2E } from './helpers/login';
import crypto from 'crypto';
import http from 'http';

const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';
const apiUrl = process.env.ECM_API_URL || 'http://localhost:7700';

async function loginWithCredentials(page: Page, username: string, password: string, token?: string) {
  await loginWithCredentialsE2E(page, username, password, { token });
}

async function createFolder(
  request: APIRequestContext,
  parentId: string,
  name: string,
  token: string,
) {
  const res = await request.post(`${apiUrl}/api/v1/folders`, {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    data: { name, parentId, folderType: 'GENERAL', inheritPermissions: true },
  });
  expect(res.ok()).toBeTruthy();
  const payload = (await res.json()) as { id?: string };
  if (!payload.id) {
    throw new Error('Failed to create folder');
  }
  return payload.id;
}

async function deleteNode(request: APIRequestContext, nodeId: string, token: string) {
  await request.delete(`${apiUrl}/api/v1/nodes/${nodeId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
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

test('Webhook subscriptions honor event type filters', async ({ page, request }) => {
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const typesRes = await request.get(`${apiUrl}/api/v1/webhooks/event-types`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(typesRes.ok()).toBeTruthy();
  const eventTypes = (await typesRes.json()) as string[];
  test.skip(
    !eventTypes.includes('NODE_CREATED') || !eventTypes.includes('VERSION_CREATED'),
    'Required webhook event types missing',
  );

  await loginWithCredentials(page, defaultUsername, defaultPassword, token);

  const receivedNode: Array<{ eventType?: string; signature?: string; body: string }> = [];
  const receivedOther: Array<{ eventType?: string; signature?: string; body: string }> = [];

  const createServer = (bucket: typeof receivedNode) => http.createServer((req, res) => {
    let body = '';
    req.on('data', (chunk) => {
      body += chunk.toString();
    });
    req.on('end', () => {
      const eventHeader = req.headers['x-ecm-event'];
      const signatureHeader = req.headers['x-ecm-signature'];
      const eventType = Array.isArray(eventHeader) ? eventHeader[0] : eventHeader;
      const signature = Array.isArray(signatureHeader) ? signatureHeader[0] : signatureHeader;
      bucket.push({ eventType, signature, body });
      res.writeHead(200, { 'Content-Type': 'text/plain' });
      res.end('ok');
    });
  });

  const nodeServer = createServer(receivedNode);
  const otherServer = createServer(receivedOther);

  await new Promise<void>((resolve) => nodeServer.listen(0, '0.0.0.0', resolve));
  await new Promise<void>((resolve) => otherServer.listen(0, '0.0.0.0', resolve));

  const nodeAddress = nodeServer.address();
  const otherAddress = otherServer.address();
  if (!nodeAddress || typeof nodeAddress === 'string' || !otherAddress || typeof otherAddress === 'string') {
    nodeServer.close();
    otherServer.close();
    throw new Error('Failed to bind webhook test server');
  }

  const nodePort = nodeAddress.port;
  const otherPort = otherAddress.port;
  const nodeSecret = `e2e-node-secret-${Date.now()}`;
  const otherSecret = `e2e-other-secret-${Date.now()}`;
  const nodeName = `e2e-node-${Date.now()}`;
  const otherName = `e2e-other-${Date.now()}`;
  const nodeUrl = `http://host.docker.internal:${nodePort}/webhook`;
  const otherUrl = `http://host.docker.internal:${otherPort}/webhook`;
  let folderId: string | null = null;

  try {
    await page.goto('/admin/webhooks', { waitUntil: 'domcontentloaded' });
    await page.waitForURL(/\/admin\/webhooks/, { timeout: 60_000 });
    await expect(page.getByRole('heading', { name: /webhook subscriptions/i })).toBeVisible({ timeout: 60_000 });

    const eventInput = page.getByLabel('Event Types (empty = all)');
    const createButton = page.getByRole('button', { name: /^create$/i });

    await page.getByLabel('Name').fill(nodeName);
    await page.getByLabel('Endpoint URL').fill(nodeUrl);
    await page.getByLabel('Signing Secret (optional)').fill(nodeSecret);
    await eventInput.click();
    await page.getByRole('option', { name: 'NODE_CREATED' }).click();
    await page.keyboard.press('Escape');
    await expect(createButton).toBeEnabled({ timeout: 30_000 });
    await createButton.click();
    await expect(page.locator('.Toastify__toast').last()).toContainText(/Webhook subscription created/i, {
      timeout: 60_000,
    });

    await page.getByLabel('Name').fill(otherName);
    await page.getByLabel('Endpoint URL').fill(otherUrl);
    await page.getByLabel('Signing Secret (optional)').fill(otherSecret);
    await eventInput.click();
    await page.getByRole('option', { name: 'VERSION_CREATED' }).click();
    await page.keyboard.press('Escape');
    await expect(createButton).toBeEnabled({ timeout: 30_000 });
    await createButton.click();
    await expect(page.locator('.Toastify__toast').last()).toContainText(/Webhook subscription created/i, {
      timeout: 60_000,
    });

    const nodeRow = page.getByRole('row', { name: new RegExp(nodeName, 'i') });
    const otherRow = page.getByRole('row', { name: new RegExp(otherName, 'i') });
    await expect(nodeRow.getByText('NODE_CREATED')).toBeVisible({ timeout: 30_000 });
    await expect(otherRow.getByText('VERSION_CREATED')).toBeVisible({ timeout: 30_000 });

    const rootId = await getRootFolderId(request, token, { apiUrl });
    folderId = await createFolder(request, rootId, `e2e-webhook-folder-${Date.now()}`, token);

    await expect.poll(
      () => receivedNode.filter((event) => event.eventType === 'NODE_CREATED').length,
      { timeout: 60_000 },
    ).toBeGreaterThan(0);

    const nodeEvent = receivedNode.find((event) => event.eventType === 'NODE_CREATED');
    expect(nodeEvent?.signature).toBeTruthy();
    const expectedSignature = crypto.createHmac('sha256', nodeSecret).update(nodeEvent?.body ?? '').digest('base64');
    expect(nodeEvent?.signature).toBe(expectedSignature);

    await new Promise((resolve) => setTimeout(resolve, 3000));
    const otherNodeEvents = receivedOther.filter((event) => event.eventType === 'NODE_CREATED').length;
    expect(otherNodeEvents).toBe(0);

    page.once('dialog', (dialog) => dialog.accept());
    await nodeRow.getByRole('button', { name: /delete/i }).click();
    await expect(page.locator('.Toastify__toast').last()).toContainText(/Webhook deleted|Failed to delete webhook/i, {
      timeout: 60_000,
    });

    page.once('dialog', (dialog) => dialog.accept());
    await otherRow.getByRole('button', { name: /delete/i }).click();
    await expect(page.locator('.Toastify__toast').last()).toContainText(/Webhook deleted|Failed to delete webhook/i, {
      timeout: 60_000,
    });
  } finally {
    if (folderId) {
      await deleteNode(request, folderId, token);
    }
    nodeServer.close();
    otherServer.close();
  }
});
