import { APIRequestContext, expect, test } from '@playwright/test';
import { fetchAccessToken, resolveApiUrl, waitForApiReady } from './helpers/api';
import { gotoWithAuthE2E } from './helpers/login';

const baseApiUrl = resolveApiUrl();
const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';

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
  const deadline = Date.now() + 90_000;
  let lastError: string | undefined;

  while (Date.now() < deadline) {
    try {
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
        timeout: 60_000,
      });

      if (res.ok()) {
        return;
      }

      const body = await res.text().catch(() => '');
      lastError = `checkin status=${res.status()} ${body}`;
    } catch (error) {
      lastError = error instanceof Error ? error.message : String(error);
    }

    await new Promise((resolve) => setTimeout(resolve, 2000));
  }

  throw new Error(`Checkin did not succeed: ${lastError ?? 'unknown error'}`);
}

async function getVersions(request: APIRequestContext, documentId: string, token: string) {
  const res = await request.get(`${baseApiUrl}/api/v1/documents/${documentId}/versions`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(res.ok()).toBeTruthy();
  return (await res.json()) as Array<{
    id: string;
    versionLabel: string;
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

test('Version compare: can select any two versions', async ({ page, request }) => {
  test.setTimeout(240_000);

  await waitForApiReady(request, { apiUrl: baseApiUrl });
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const uploadsId = await getUploadsFolderId(request, token);

  const folderName = `e2e-version-compare-any-${Date.now()}`;
  const folderId = await createFolder(request, uploadsId, folderName, token);

  const filename = `e2e-version-compare-any-${Date.now()}.txt`;
  const v1Marker = `version-compare-any-v1-${Date.now()}`;
  const documentId = await uploadTextFile(request, folderId, filename, `${v1Marker}\n`, token);

  const v2Marker = `version-compare-any-v2-${Date.now()}`;
  await checkinDocument(request, documentId, filename, `${v2Marker}\n`, token, `E2E checkin v2 ${Date.now()}`);

  const v3Marker = `version-compare-any-v3-${Date.now()}`;
  await checkinDocument(request, documentId, filename, `${v3Marker}\n`, token, `E2E checkin v3 ${Date.now()}`);

  const versions = await waitForVersionCount(request, documentId, token, 3);
  const v3 = versions[0];
  const v1 = versions[2];
  if (!v3 || !v1) {
    throw new Error('Expected at least three versions for arbitrary compare');
  }

  await gotoWithAuthE2E(page, `/browse/${folderId}`, defaultUsername, defaultPassword, { token });

  const row = page.getByRole('row', { name: new RegExp(filename) });
  await expect(row).toBeVisible({ timeout: 60_000 });
  await row.getByRole('button', { name: `Actions for ${filename}` }).click();
  await page.getByRole('menuitem', { name: 'Version History' }).click();

  const versionsDialog = page.getByRole('dialog').filter({ hasText: 'Version History' });
  await expect(versionsDialog).toBeVisible({ timeout: 60_000 });

  const latestRow = versionsDialog.getByRole('row').filter({ hasText: v3.versionLabel }).first();
  await expect(latestRow).toBeVisible({ timeout: 60_000 });
  await latestRow.getByRole('button').click();
  await page.getByRole('menuitem', { name: 'Compare versions' }).click();

  const compareDialog = page.getByRole('dialog').filter({ hasText: 'Compare Versions' });
  await expect(compareDialog).toBeVisible({ timeout: 30_000 });

  const fromSelect = compareDialog.getByRole('combobox', { name: 'From version' });
  await fromSelect.click();
  await page.getByRole('option', { name: v1.versionLabel }).first().click();

  const toSelect = compareDialog.getByRole('combobox', { name: 'To version' });
  await toSelect.click();
  await page.getByRole('option', { name: v3.versionLabel }).first().click();

  await compareDialog.getByRole('button', { name: /Load text diff/i }).click();

  const diffPre = compareDialog.locator('pre');
  await expect(diffPre).toContainText('--- from', { timeout: 30_000 });
  await expect(diffPre).toContainText(`- ${v1Marker}`, { timeout: 30_000 });
  await expect(diffPre).toContainText(`+ ${v3Marker}`, { timeout: 30_000 });

  await compareDialog.getByRole('button', { name: 'Close' }).click();
  await expect(compareDialog).toBeHidden({ timeout: 60_000 });

  await request.delete(`${baseApiUrl}/api/v1/nodes/${folderId}`, {
    headers: { Authorization: `Bearer ${token}` },
  }).catch(() => null);
});

