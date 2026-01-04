import { APIRequestContext, expect, test } from '@playwright/test';

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

async function getVersions(request: APIRequestContext, documentId: string, token: string) {
  const res = await request.get(`${baseApiUrl}/api/v1/documents/${documentId}/versions`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(res.ok()).toBeTruthy();
  return (await res.json()) as Array<{
    id: string;
    documentId: string;
    versionLabel: string;
    comment?: string;
    createdDate: string;
    creator: string;
    size: number;
    major: boolean;
    mimeType?: string;
    contentHash?: string;
    contentId?: string;
    status?: string;
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

test('Version details: checkin metadata matches expectations', async ({ request }) => {
  await waitForApiReady(request);
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const uploadsId = await getUploadsFolderId(request, token);

  const baseName = `e2e-version-details-${Date.now()}`;
  const initialContent = `version-details-initial-${Date.now()}\n`;
  const initialFilename = `${baseName}.txt`;
  const documentId = await uploadTextFile(request, uploadsId, initialFilename, initialContent, token);

  const initialVersions = await waitForVersionCount(request, documentId, token, 1);
  const initial = initialVersions[0];
  expect(initial.versionLabel).toBeTruthy();
  expect(initial.createdDate).toBeTruthy();
  expect(initial.creator).toBeTruthy();
  expect(initial.size).toBeGreaterThan(0);

  const updatedContent = `version-details-updated-${Date.now()}\n`;
  const comment = `E2E version checkin ${Date.now()}`;
  const checkinRes = await request.post(`${baseApiUrl}/api/v1/documents/${documentId}/checkin`, {
    headers: { Authorization: `Bearer ${token}` },
    multipart: {
      file: {
        name: initialFilename,
        mimeType: 'text/plain',
        buffer: Buffer.from(updatedContent),
      },
      comment,
      majorVersion: 'false',
    },
  });
  expect(checkinRes.ok()).toBeTruthy();

  const versions = await waitForVersionCount(request, documentId, token, initialVersions.length + 1);
  const latest = versions[0];

  expect(latest.comment).toBe(comment);
  expect(latest.creator).toBe(initial.creator);
  expect(latest.versionLabel).not.toBe(initial.versionLabel);
  expect(latest.size).toBe(Buffer.byteLength(updatedContent));
  expect(latest.mimeType).toBeTruthy();
  expect(latest.status).toBeTruthy();
  expect(latest.contentId).toBeTruthy();

  if (initial.contentId) {
    expect(latest.contentId).not.toBe(initial.contentId);
  }
  if (initial.contentHash && latest.contentHash) {
    expect(latest.contentHash).not.toBe(initial.contentHash);
  }

  await request.delete(`${baseApiUrl}/api/v1/nodes/${documentId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
});
