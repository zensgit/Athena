import { APIRequestContext, Page, expect, test } from '@playwright/test';

const baseApiUrl = process.env.ECM_API_URL || 'http://localhost:7700';
const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';

async function fetchAccessToken(request: APIRequestContext, username: string, password: string) {
  const deadline = Date.now() + 60_000;
  let lastError: string | undefined;

  while (Date.now() < deadline) {
    try {
      const tokenRes = await request.post('http://localhost:8180/realms/ecm/protocol/openid-connect/token', {
        form: {
          grant_type: 'password',
          client_id: 'unified-portal',
          username,
          password,
        },
      });
      if (!tokenRes.ok()) {
        lastError = `token status=${tokenRes.status()}`;
      } else {
        const tokenJson = (await tokenRes.json()) as { access_token?: string };
        if (tokenJson.access_token) {
          return tokenJson.access_token;
        }
        lastError = 'access_token missing';
      }
    } catch (error) {
      lastError = error instanceof Error ? error.message : String(error);
    }

    await new Promise((resolve) => setTimeout(resolve, 2000));
  }

  throw new Error(`Failed to obtain access token for API calls: ${lastError ?? 'unknown error'}`);
}

async function waitForApiReady(request: APIRequestContext) {
  const deadline = Date.now() + 60_000;
  let lastError: string | undefined;

  while (Date.now() < deadline) {
    try {
      const res = await request.get(`${baseApiUrl}/actuator/health`);
      if (res.ok()) {
        const payload = (await res.json()) as { status?: string };
        if (!payload?.status || payload.status.toUpperCase() !== 'DOWN') {
          return;
        }
        lastError = `health status=${payload.status}`;
      } else {
        lastError = `health status code=${res.status()}`;
      }
    } catch (error) {
      lastError = error instanceof Error ? error.message : String(error);
    }

    await new Promise((resolve) => setTimeout(resolve, 2000));
  }

  throw new Error(`API did not become ready: ${lastError ?? 'unknown error'}`);
}

async function getUploadsFolderId(request: APIRequestContext, token: string) {
  const rootsRes = await request.get(`${baseApiUrl}/api/v1/folders/roots`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(rootsRes.ok()).toBeTruthy();
  const roots = (await rootsRes.json()) as { id: string; name: string }[];
  const uploads = roots.find((root) => root.name === 'uploads') ?? roots[0];
  if (!uploads?.id) {
    throw new Error('Failed to resolve uploads folder');
  }
  return uploads.id;
}

async function createFolder(
  request: APIRequestContext,
  parentId: string,
  name: string,
  token: string,
) {
  const res = await request.post(`${baseApiUrl}/api/v1/folders`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { name, parentId },
  });
  expect(res.ok()).toBeTruthy();
  const payload = (await res.json()) as { id?: string };
  if (!payload.id) {
    throw new Error('Folder creation did not return id');
  }
  return payload.id;
}

async function uploadTextFile(
  request: APIRequestContext,
  folderId: string,
  filename: string,
  content: string,
  token: string,
) {
  const deadline = Date.now() + 90_000;
  let lastError: string | undefined;

  while (Date.now() < deadline) {
    try {
      const uploadRes = await request.post(`${baseApiUrl}/api/v1/documents/upload?folderId=${folderId}`, {
        headers: { Authorization: `Bearer ${token}` },
        multipart: {
          file: {
            name: filename,
            mimeType: 'text/plain',
            buffer: Buffer.from(content),
          },
        },
        timeout: 60_000,
      });
      if (!uploadRes.ok()) {
        lastError = `upload status=${uploadRes.status()}`;
      } else {
        const uploadJson = (await uploadRes.json()) as { documentId?: string; id?: string };
        const docId = uploadJson.documentId ?? uploadJson.id;
        if (docId) {
          return docId;
        }
        lastError = 'upload missing document id';
      }
    } catch (error) {
      lastError = error instanceof Error ? error.message : String(error);
    }

    await new Promise((resolve) => setTimeout(resolve, 2000));
  }

  throw new Error(`Upload did not return document id: ${lastError ?? 'unknown error'}`);
}

async function checkinDocument(
  request: APIRequestContext,
  documentId: string,
  filename: string,
  content: string,
  token: string,
  comment: string,
) {
  const res = await request.post(`${baseApiUrl}/api/v1/documents/${documentId}/checkin`, {
    headers: { Authorization: `Bearer ${token}` },
    multipart: {
      file: {
        name: filename,
        mimeType: 'text/plain',
        buffer: Buffer.from(content),
      },
      comment,
      majorVersion: 'false',
    },
  });
  expect(res.ok()).toBeTruthy();
}

async function getVersions(request: APIRequestContext, documentId: string, token: string) {
  const res = await request.get(`${baseApiUrl}/api/v1/documents/${documentId}/versions`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(res.ok()).toBeTruthy();
  return (await res.json()) as Array<{
    id: string;
    versionLabel: string;
    comment?: string;
  }>;
}

async function waitForVersionCount(
  request: APIRequestContext,
  documentId: string,
  token: string,
  minCount: number,
  maxAttempts = 12,
) {
  for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
    const versions = await getVersions(request, documentId, token);
    if (versions.length >= minCount) {
      return versions;
    }
    await new Promise((resolve) => setTimeout(resolve, 2000));
  }
  throw new Error(`Version count did not reach ${minCount} for document ${documentId}`);
}

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

test('Version history actions: download + restore', async ({ page, request }) => {
  page.on('dialog', (dialog) => dialog.accept());
  test.setTimeout(240_000);

  await waitForApiReady(request);
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const uploadsId = await getUploadsFolderId(request, token);
  const folderName = `e2e-version-actions-${Date.now()}`;
  const folderId = await createFolder(request, uploadsId, folderName, token);

  const filename = `e2e-version-actions-${Date.now()}.txt`;
  const documentId = await uploadTextFile(
    request,
    folderId,
    filename,
    `version-actions-initial-${Date.now()}\n`,
    token,
  );
  await waitForVersionCount(request, documentId, token, 1);

  const checkinComment = `E2E version action ${Date.now()}`;
  await checkinDocument(
    request,
    documentId,
    filename,
    `version-actions-updated-${Date.now()}\n`,
    token,
    checkinComment,
  );

  const versions = await waitForVersionCount(request, documentId, token, 2);
  const latest = versions[0];
  const previous = versions[1];
  if (!latest || !previous) {
    throw new Error('Expected at least two versions for version history actions');
  }

  await loginWithCredentials(page, defaultUsername, defaultPassword);
  await page.goto(`/browse/${folderId}`, { waitUntil: 'domcontentloaded' });

  const row = page.getByRole('row', { name: new RegExp(filename) });
  let rowVisible = false;
  for (let attempt = 0; attempt < 10; attempt += 1) {
    try {
      await expect(row).toBeVisible({ timeout: 4_000 });
      rowVisible = true;
      break;
    } catch {
      await page.waitForTimeout(2_000);
      await page.reload({ waitUntil: 'domcontentloaded' });
    }
  }
  expect(rowVisible).toBeTruthy();

  await row.getByRole('button', { name: `Actions for ${filename}` }).click();
  await page.getByRole('menuitem', { name: 'Version History' }).click();

  const versionsDialog = page.getByRole('dialog').filter({ hasText: 'Version History' });
  await expect(versionsDialog).toBeVisible({ timeout: 60_000 });

  const latestRow = versionsDialog.getByRole('row').filter({ hasText: latest.versionLabel }).first();
  await expect(latestRow).toBeVisible({ timeout: 60_000 });

  const downloadResponse = page.waitForResponse((response) =>
    response.url().includes(`/api/v1/documents/${documentId}/versions/${latest.id}/download`)
      && response.status() === 200,
  );
  await latestRow.getByRole('button').click();
  await page.getByRole('menuitem', { name: 'Download this version' }).click();
  await downloadResponse;
  await expect(page.getByText('Version downloaded successfully')).toBeVisible({ timeout: 60_000 });

  const previousRow = versionsDialog.getByRole('row').filter({ hasText: previous.versionLabel }).first();
  await expect(previousRow).toBeVisible({ timeout: 60_000 });
  await previousRow.getByRole('button').click();
  await page.getByRole('menuitem', { name: 'Restore to this version' }).click();

  await expect(page.getByText('Document restored successfully')).toBeVisible({ timeout: 60_000 });
  await expect(versionsDialog).toBeHidden({ timeout: 60_000 });

  await request.delete(`${baseApiUrl}/api/v1/nodes/${documentId}`, {
    headers: { Authorization: `Bearer ${token}` },
  }).catch(() => null);
});

test('Share links enforce password, deactivation, and access limits', async ({ request }) => {
  test.setTimeout(180_000);

  await waitForApiReady(request);
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const uploadsId = await getUploadsFolderId(request, token);

  const filename = `e2e-share-flow-${Date.now()}.txt`;
  const documentId = await uploadTextFile(
    request,
    uploadsId,
    filename,
    `share-flow-${Date.now()}\n`,
    token,
  );

  const passwordLinkRes = await request.post(`${baseApiUrl}/api/v1/share/nodes/${documentId}`, {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      name: `e2e-share-password-${Date.now()}`,
      permissionLevel: 'VIEW',
      password: 'secret',
    },
  });
  expect(passwordLinkRes.status()).toBe(201);
  const passwordLink = (await passwordLinkRes.json()) as { token: string; nodeId: string };

  const accessWithoutPassword = await request.get(`${baseApiUrl}/api/v1/share/access/${passwordLink.token}`);
  expect(accessWithoutPassword.status()).toBe(401);
  const accessWithoutPayload = (await accessWithoutPassword.json()) as { passwordRequired?: boolean };
  expect(accessWithoutPayload.passwordRequired).toBeTruthy();

  const accessWithPassword = await request.get(`${baseApiUrl}/api/v1/share/access/${passwordLink.token}`, {
    params: { password: 'secret' },
  });
  expect(accessWithPassword.ok()).toBeTruthy();
  const accessWithPayload = (await accessWithPassword.json()) as { nodeId?: string };
  expect(accessWithPayload.nodeId).toBe(documentId);

  const deactivateRes = await request.post(`${baseApiUrl}/api/v1/share/${passwordLink.token}/deactivate`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(deactivateRes.ok()).toBeTruthy();

  const accessAfterDeactivate = await request.get(`${baseApiUrl}/api/v1/share/access/${passwordLink.token}`, {
    params: { password: 'secret' },
  });
  expect(accessAfterDeactivate.status()).toBe(403);
  const accessAfterPayload = (await accessAfterDeactivate.json()) as { error?: string };
  expect(accessAfterPayload.error).toContain('deactivated');

  const limitedLinkRes = await request.post(`${baseApiUrl}/api/v1/share/nodes/${documentId}`, {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      name: `e2e-share-limit-${Date.now()}`,
      permissionLevel: 'VIEW',
      maxAccessCount: 1,
    },
  });
  expect(limitedLinkRes.status()).toBe(201);
  const limitedLink = (await limitedLinkRes.json()) as { token: string; nodeId: string };

  const limitedAccess = await request.get(`${baseApiUrl}/api/v1/share/access/${limitedLink.token}`);
  expect(limitedAccess.ok()).toBeTruthy();
  const limitedPayload = (await limitedAccess.json()) as { nodeId?: string };
  expect(limitedPayload.nodeId).toBe(documentId);

  const limitedAccessAgain = await request.get(`${baseApiUrl}/api/v1/share/access/${limitedLink.token}`);
  expect(limitedAccessAgain.status()).toBe(403);
  const limitedAgainPayload = (await limitedAccessAgain.json()) as { error?: string };
  expect(limitedAgainPayload.error).toContain('Access limit');

  await request.delete(`${baseApiUrl}/api/v1/nodes/${documentId}`, {
    headers: { Authorization: `Bearer ${token}` },
  }).catch(() => null);
});
