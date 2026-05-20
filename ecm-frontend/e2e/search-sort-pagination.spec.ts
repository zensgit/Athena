import { APIRequestContext, expect, test } from '@playwright/test';
import {
  fetchAccessToken,
  findChildFolderId,
  getRootFolderId,
  resolveApiUrl,
  waitForApiReady,
  waitForSearchIndex,
} from './helpers/api';
import { gotoWithAuthE2E } from './helpers/login';

const baseApiUrl = resolveApiUrl();
const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';

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

async function resultNamesWithPrefix(page: Page, prefix: string, count: number) {
  return (await page.locator('.MuiCard-root h6').allTextContents())
    .map((name) => name.trim())
    .filter((name) => name.startsWith(prefix))
    .slice(0, count);
}

async function expectResultNamesWithPrefix(
  page: Page,
  prefix: string,
  expected: string[],
) {
  await expect.poll(
    () => resultNamesWithPrefix(page, prefix, expected.length),
    { timeout: 60_000 }
  ).toEqual(expected);
}

function isMainSearchResponseUrl(url: string, query: string) {
  try {
    const parsed = new URL(url);
    return parsed.pathname.endsWith('/api/v1/search') && parsed.searchParams.get('q') === query;
  } catch {
    return false;
  }
}

async function submitSearch(page: Page, query: string) {
  const searchInput = page.locator('input[placeholder="Quick search by name..."]');
  await searchInput.fill(query);
  await Promise.all([
    page.waitForResponse((res) => {
      return isMainSearchResponseUrl(res.url(), query) && res.ok();
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
    const expectedSortParams: Record<string, { sortBy: string; sortDirection: string }> = {
      Name: { sortBy: 'name', sortDirection: 'asc' },
      'Modified Date': { sortBy: 'modified', sortDirection: 'desc' },
      Size: { sortBy: 'size', sortDirection: 'desc' },
    };
    const expectedSort = expectedSortParams[optionLabel];
    await Promise.all([
      page.waitForResponse((res) => {
        try {
          const url = new URL(res.url());
          return isMainSearchResponseUrl(res.url(), query)
            && (!expectedSort || (
              url.searchParams.get('sortBy') === expectedSort.sortBy
              && url.searchParams.get('sortDirection') === expectedSort.sortDirection
            ))
            && res.ok();
        } catch {
          return false;
        }
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

async function resultMetadataWithPrefix(page: Page, prefix: string, count: number) {
  return (await page.locator('.MuiCard-root').evaluateAll((cards, prefixValue) => {
    return cards
      .map((card) => {
        const name = card.querySelector('h6')?.textContent?.trim() || '';
        const lines = (card as HTMLElement).innerText.split('\n').map((line) => line.trim()).filter(Boolean);
        const modified = (lines.find((line) => line.startsWith('Modified:')) || '').replace('Modified:', '').trim();
        return { name, modified };
      })
      .filter((item) => item.name.startsWith(prefixValue as string));
  }, prefix)).slice(0, count);
}

async function expectModifiedDescending(page: Page, prefix: string, count: number) {
  await expect.poll(async () => {
    const metadata = await resultMetadataWithPrefix(page, prefix, count);
    if (metadata.length < count) {
      return false;
    }
    const times = metadata.map((item) => Date.parse(item.modified));
    if (times.some((time) => !Number.isFinite(time))) {
      return false;
    }
    return times.every((time, index) => index === 0 || times[index - 1] >= time);
  }, { timeout: 60_000 }).toBe(true);
}

test('Search sorting and pagination are consistent', async ({ page, request }) => {
  test.setTimeout(360_000);

  await waitForApiReady(request, { apiUrl: baseApiUrl });
  const apiToken = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootId = await getRootFolderId(request, apiToken, { apiUrl: baseApiUrl });
  const documentsId = await findChildFolderId(request, rootId, 'Documents', apiToken, { apiUrl: baseApiUrl });

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
  await waitForSearchIndex(request, sortPrefix, apiToken, {
    apiUrl: baseApiUrl,
    minResults: sortDocs.length,
    maxAttempts: 90,
    delayMs: 2000,
  });

  const pagePrefix = `e2epage${Date.now()}`;
  const pageCount = 25;
  for (let i = 1; i <= pageCount; i += 1) {
    const filename = `${pagePrefix}-${String(i).padStart(3, '0')}.txt`;
    await uploadDocument(request, folderId, filename, 20, apiToken);
    await page.waitForTimeout(200);
  }
  await waitForSearchIndex(request, pagePrefix, apiToken, {
    apiUrl: baseApiUrl,
    minResults: pageCount,
    maxAttempts: 90,
    delayMs: 2000,
  });

  await gotoWithAuthE2E(page, '/search-results', defaultUsername, defaultPassword, { token: apiToken });

  // Sorting checks (Name, Modified, Size)
  await page.waitForURL(/\/search-results/, { timeout: 60_000 });
  await submitSearch(page, sortPrefix);
  await waitForResults(page);
  await page.getByText(`${sortPrefix}-A.txt`).first().waitFor({ timeout: 60_000 });

  await selectSort(page, 'Name', sortPrefix);
  await waitForResults(page);

  const nameSorted = sortDocs.map((doc) => doc.name).sort((left, right) => left.localeCompare(right));
  await expectResultNamesWithPrefix(page, sortPrefix, nameSorted);

  await selectSort(page, 'Modified Date', sortPrefix);
  await waitForResults(page);

  await expectModifiedDescending(page, sortPrefix, 3);

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

  // Pagination check (Name sort ascending). Re-enter the page to isolate this
  // check from the sorting section's pending debounce/effect-driven searches.
  await gotoWithAuthE2E(page, '/search-results', defaultUsername, defaultPassword, { token: apiToken });
  await page.waitForURL(/\/search-results/, { timeout: 60_000 });
  await selectSort(page, 'Name');

  await submitSearch(page, pagePrefix);
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

  const apiPage0Names = apiPage0Json.content.map((item) => item.name).slice(0, 5);
  await expectResultNamesWithPrefix(page, pagePrefix, apiPage0Names);

  if (apiPage0Json.totalElements > 20) {
    const pagination = page.locator('nav[aria-label*="pagination"]').first();
    if (await pagination.count()) {
      await pagination.scrollIntoViewIfNeeded();
      const page2Button = pagination.getByRole('button', { name: '2' }).first();
      await page2Button.click();
    } else {
      await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
      await page.getByRole('button', { name: '2' }).first().click();
    }
    await waitForResults(page);
    await expect.poll(async () => {
      const text = await page.locator('.MuiCard-root h6').first().textContent();
      return text?.trim() ?? '';
    }, { timeout: 60_000 }).toContain(`${pagePrefix}-021.txt`);

    const apiPage1Names = apiPage1Json.content.map((item) => item.name).slice(0, 5);
    expect(apiPage1Names).toHaveLength(Math.min(5, Math.max(0, pageCount - 20)));
    await expectResultNamesWithPrefix(page, pagePrefix, apiPage1Names);
  }

  // Cleanup folder (best-effort)
  await request.delete(`${baseApiUrl}/api/v1/nodes/${folderId}`, {
    headers: { Authorization: `Bearer ${apiToken}` },
  }).catch(() => null);
});
