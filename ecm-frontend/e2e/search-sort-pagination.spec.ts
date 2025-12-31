import { APIRequestContext, expect, Page, test } from '@playwright/test';

const baseApiUrl = process.env.ECM_API_URL || 'http://localhost:7700';
const baseUiUrl = process.env.ECM_UI_URL || 'http://localhost:5500';
const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';

async function fetchAccessToken(request: APIRequestContext, username: string, password: string) {
  const tokenRes = await request.post('http://localhost:8180/realms/ecm/protocol/openid-connect/token', {
    form: {
      grant_type: 'password',
      client_id: 'unified-portal',
      username,
      password,
    },
  });
  expect(tokenRes.ok()).toBeTruthy();
  const tokenJson = (await tokenRes.json()) as { access_token?: string };
  if (!tokenJson.access_token) {
    throw new Error('Failed to obtain access token for API calls');
  }
  return tokenJson.access_token;
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

async function loginWithCredentials(page: Page, username: string, password: string) {
  await page.goto(`${baseUiUrl}/`, { waitUntil: 'domcontentloaded' });

  if (page.url().endsWith('/login')) {
    await page.getByRole('button', { name: /sign in with keycloak/i }).click();
  }

  await page.waitForURL(/(\/browse\/|\/protocol\/openid-connect\/auth)/, { timeout: 60_000 });
  if (page.url().includes('/protocol/openid-connect/auth')) {
    await page.locator('#username').fill(username);
    await page.locator('#password').fill(password);
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'domcontentloaded' }),
      page.locator('#kc-login').click(),
    ]);
  }

  if (!/\/browse\//.test(page.url())) {
    await page.goto(`${baseUiUrl}/browse/root`, { waitUntil: 'domcontentloaded' });
  }
  await page.waitForURL(/\/browse\//, { timeout: 60_000 });
}

async function getRootFolderId(request: APIRequestContext, token: string) {
  const res = await request.get(`${baseApiUrl}/api/v1/folders/roots`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(res.ok()).toBeTruthy();
  const roots = (await res.json()) as Array<{
    id: string;
    name: string;
    path?: string;
    folderType?: string;
  }>;
  if (!roots.length) {
    throw new Error('No root folders returned');
  }
  const preferred = roots.find((root) => {
    const isSystem = root.folderType?.toUpperCase() === 'SYSTEM';
    return isSystem || root.name === 'Root' || root.path === '/Root';
  });
  return (preferred ?? roots[0]).id;
}

async function findChildFolderId(
  request: APIRequestContext,
  parentId: string,
  folderName: string,
  token: string,
) {
  const response = await request.get(`${baseApiUrl}/api/v1/folders/${parentId}/contents`, {
    params: { page: 0, size: 200, sort: 'name,asc' },
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(response.ok()).toBeTruthy();
  const payload = (await response.json()) as { content?: Array<{ id: string; name: string; nodeType: string }> };
  const match = payload.content?.find((node) => node.name === folderName && node.nodeType === 'FOLDER');
  if (!match?.id) {
    throw new Error(`Folder not found: ${folderName}`);
  }
  return match.id;
}

async function createFolder(
  request: APIRequestContext,
  parentId: string,
  folderName: string,
  token: string,
) {
  const res = await request.post(`${baseApiUrl}/api/v1/folders`, {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    data: { name: folderName, parentId, folderType: 'GENERAL', inheritPermissions: true },
  });
  expect(res.ok()).toBeTruthy();
  const payload = (await res.json()) as { id: string };
  if (!payload.id) {
    throw new Error('Failed to create folder');
  }
  return payload.id;
}

async function waitForSearchIndex(
  request: APIRequestContext,
  query: string,
  minResults: number,
  token: string,
) {
  let lastError = 'unknown error';
  const pageSize = Math.max(minResults, 100);
  for (let attempt = 0; attempt < 45; attempt += 1) {
    try {
      const res = await request.get(`${baseApiUrl}/api/v1/search`, {
        params: { q: query, page: 0, size: pageSize },
        headers: { Authorization: `Bearer ${token}` },
      });
      if (res.ok()) {
        const payload = (await res.json()) as { content?: Array<{ name: string }> };
        const count = payload.content?.length ?? 0;
        if (count >= minResults) {
          return;
        }
        lastError = `status=${res.status()} count=${count}`;
      } else {
        lastError = `status=${res.status()}`;
      }
    } catch (error) {
      lastError = error instanceof Error ? error.message : String(error);
    }
    await new Promise((resolve) => setTimeout(resolve, 2000));
  }
  throw new Error(`Search index did not return ${minResults} results for ${query} (${lastError})`);
}

async function uploadDocument(
  request: APIRequestContext,
  folderId: string,
  filename: string,
  size: number,
  token: string,
) {
  let lastError = 'Upload failed';
  for (let attempt = 0; attempt < 3; attempt += 1) {
    const uploadRes = await request.post(
      `${baseApiUrl}/api/v1/documents/upload?folderId=${folderId}`,
      {
        headers: { Authorization: `Bearer ${token}` },
        multipart: {
          file: {
            name: filename,
            mimeType: 'text/plain',
            buffer: Buffer.alloc(size, 'a'),
          },
        },
      },
    );
    if (uploadRes.ok()) {
      const uploadJson = (await uploadRes.json()) as { documentId?: string; id?: string };
      const documentId = uploadJson.documentId ?? uploadJson.id;
      if (!documentId) {
        throw new Error('Upload did not return document id');
      }

      const indexRes = await request.post(`${baseApiUrl}/api/v1/search/index/${documentId}`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      expect(indexRes.ok()).toBeTruthy();
      return documentId;
    }

    const body = await uploadRes.text().catch(() => '');
    lastError = `Upload failed (attempt ${attempt + 1}/3): ${uploadRes.status()} ${body}`;
    await new Promise((resolve) => setTimeout(resolve, 1500 * (attempt + 1)));
  }
  throw new Error(lastError);
}

async function waitForResults(page: Page) {
  await page.waitForSelector('.MuiCircularProgress-root', { state: 'attached', timeout: 5_000 }).catch(() => null);
  await page.waitForSelector('.MuiCircularProgress-root', { state: 'detached', timeout: 60_000 }).catch(() => null);
  await page.waitForSelector('.MuiCard-root', { timeout: 60_000 });
}

async function submitSearch(page: Page, query: string) {
  const searchInput = page.locator('input[placeholder="Quick search by name..."]');
  await searchInput.fill(query);
  const encoded = encodeURIComponent(query);
  await Promise.all([
    page.waitForResponse((res) => {
      const url = res.url();
      return url.includes('/api/v1/search')
        && !url.includes('/facets')
        && url.includes(`q=${encoded}`);
    }),
    page.locator('form').first().evaluate((form) => form.requestSubmit()),
  ]);
}

async function selectSort(page: Page, optionLabel: string, query?: string) {
  const combo = page.locator(
    'div[role="combobox"]:has-text("Relevance"), div[role="combobox"]:has-text("Name"), div[role="combobox"]:has-text("Modified Date"), div[role="combobox"]:has-text("Size")',
  ).first();
  await combo.click();
  const listbox = page.getByRole('listbox');
  await listbox.waitFor({ timeout: 10_000 });
  const option = page.getByRole('option', { name: optionLabel }).first();
  if (query) {
    const encoded = encodeURIComponent(query);
    await Promise.all([
      page.waitForRequest((req) => {
        const url = req.url();
        return url.includes('/api/v1/search')
          && !url.includes('/facets')
          && url.includes(`q=${encoded}`)
          && url.includes('sortBy=');
      }),
      option.click(),
    ]);
  } else {
    await option.click();
  }
}

function parseSize(raw: string) {
  if (!raw || raw === '-') return null;
  const match = raw.match(/([0-9.]+)\s*(B|KB|MB|GB)/i);
  if (!match) return null;
  const value = parseFloat(match[1]);
  const unit = match[2].toUpperCase();
  const multiplier = unit === 'GB' ? 1024 ** 3 : unit === 'MB' ? 1024 ** 2 : unit === 'KB' ? 1024 : 1;
  return Math.round(value * multiplier);
}

test('Search sorting and pagination are consistent', async ({ page, request }) => {
  test.setTimeout(360_000);

  await waitForApiReady(request);
  const apiToken = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootId = await getRootFolderId(request, apiToken);
  const documentsId = await findChildFolderId(request, rootId, 'Documents', apiToken);

  const folderName = `e2e-search-sort-${Date.now()}`;
  const folderId = await createFolder(request, documentsId, folderName, apiToken);

  const sortPrefix = `e2esort${Date.now()}`;
  const sortDocs = [
    { name: `${sortPrefix}-A.txt`, size: 10 },
    { name: `${sortPrefix}-B.txt`, size: 100 },
    { name: `${sortPrefix}-C.txt`, size: 1000 },
  ];

  for (const doc of sortDocs) {
    await uploadDocument(request, folderId, doc.name, doc.size, apiToken);
    await page.waitForTimeout(500);
  }
  await waitForSearchIndex(request, sortPrefix, sortDocs.length, apiToken);

  const pagePrefix = `e2epage${Date.now()}`;
  const pageCount = 25;
  for (let i = 1; i <= pageCount; i += 1) {
    const filename = `${pagePrefix}-${String(i).padStart(3, '0')}.txt`;
    await uploadDocument(request, folderId, filename, 20, apiToken);
  }
  await waitForSearchIndex(request, pagePrefix, pageCount, apiToken);

  await loginWithCredentials(page, defaultUsername, defaultPassword);

  // Sorting checks (Name, Modified, Size)
  await page.goto(`${baseUiUrl}/search-results`, { waitUntil: 'domcontentloaded' });
  await submitSearch(page, sortPrefix);
  await waitForResults(page);
  await page.getByText(`${sortPrefix}-A.txt`).first().waitFor({ timeout: 60_000 });

  await selectSort(page, 'Name', sortPrefix);
  await waitForResults(page);

  const nameSorted = (await page.locator('.MuiCard-root h6').allTextContents())
    .map((name) => name.trim())
    .filter((name) => name.startsWith(sortPrefix))
    .slice(0, 3);
  expect(nameSorted).toHaveLength(3);
  expect(nameSorted).toEqual([
    `${sortPrefix}-A.txt`,
    `${sortPrefix}-B.txt`,
    `${sortPrefix}-C.txt`,
  ]);

  await selectSort(page, 'Modified Date', sortPrefix);
  await waitForResults(page);

  const modifiedNames = (await page.locator('.MuiCard-root h6').allTextContents())
    .map((name) => name.trim())
    .filter((name) => name.startsWith(sortPrefix))
    .slice(0, 3);
  expect(modifiedNames).toHaveLength(3);
  expect(modifiedNames[0]).toBe(`${sortPrefix}-C.txt`);
  expect(modifiedNames[1]).toBe(`${sortPrefix}-B.txt`);
  expect(modifiedNames[2]).toBe(`${sortPrefix}-A.txt`);

  await selectSort(page, 'Size', sortPrefix);
  await waitForResults(page);

  const sizeCards = await page.$$eval('.MuiCard-root', (cards) => {
    return cards.map((card) => {
      const name = card.querySelector('h6')?.textContent?.trim() || '';
      const lines = card.innerText.split('\n').map((line) => line.trim()).filter(Boolean);
      const sizeLine = lines.find((line) => line.startsWith('Size:')) || '';
      const size = sizeLine.replace('Size:', '').trim();
      return { name, size };
    });
  });
  const sizeSubset = sizeCards.filter((item) => item.name.startsWith(sortPrefix)).slice(0, 3);
  const sizes = sizeSubset.map((item) => parseSize(item.size)).filter((value) => value !== null) as number[];
  expect(sizes[0]).toBeGreaterThanOrEqual(sizes[1]);
  expect(sizes[1]).toBeGreaterThanOrEqual(sizes[2]);

  // Pagination check (Name sort ascending)
  await submitSearch(page, pagePrefix);
  await waitForResults(page);
  await page.getByText(`${pagePrefix}-001.txt`).first().waitFor({ timeout: 60_000 });

  await selectSort(page, 'Name', pagePrefix);
  await waitForResults(page);

  const apiPage0 = await request.get(`${baseApiUrl}/api/v1/search`, {
    params: { q: pagePrefix, page: 0, size: 20, sortBy: 'name', sortDirection: 'asc' },
    headers: { Authorization: `Bearer ${apiToken}` },
  });
  expect(apiPage0.ok()).toBeTruthy();
  const apiPage0Json = (await apiPage0.json()) as { content: Array<{ name: string }>; totalElements: number };

  const apiPage1 = await request.get(`${baseApiUrl}/api/v1/search`, {
    params: { q: pagePrefix, page: 1, size: 20, sortBy: 'name', sortDirection: 'asc' },
    headers: { Authorization: `Bearer ${apiToken}` },
  });
  expect(apiPage1.ok()).toBeTruthy();
  const apiPage1Json = (await apiPage1.json()) as { content: Array<{ name: string }>; totalElements: number };

  const uiPage0 = (await page.locator('.MuiCard-root h6').allTextContents())
    .map((name) => name.trim())
    .filter((name) => name.startsWith(pagePrefix))
    .slice(0, 5);
  expect(uiPage0).toHaveLength(5);
  const apiPage0Names = apiPage0Json.content.map((item) => item.name).slice(0, 5);
  expect(uiPage0).toEqual(apiPage0Names);

  if (apiPage0Json.totalElements > 20) {
    await page.getByRole('button', { name: '2' }).first().click();
    await waitForResults(page);

    const uiPage1 = (await page.locator('.MuiCard-root h6').allTextContents())
      .map((name) => name.trim())
      .filter((name) => name.startsWith(pagePrefix))
      .slice(0, 5);
    expect(uiPage1).toHaveLength(Math.min(5, Math.max(0, pageCount - 20)));
    const apiPage1Names = apiPage1Json.content.map((item) => item.name).slice(0, 5);
    expect(uiPage1).toEqual(apiPage1Names);
  }

  // Cleanup folder (best-effort)
  await request.delete(`${baseApiUrl}/api/v1/nodes/${folderId}`, {
    headers: { Authorization: `Bearer ${apiToken}` },
  }).catch(() => null);
});
