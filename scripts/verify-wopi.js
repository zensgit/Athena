#!/usr/bin/env node
const fs = require('fs');
const path = require('path');

const FRONTEND_DIR = process.env.FRONTEND_DIR || path.resolve(__dirname, '..', 'ecm-frontend');
let playwright;
try {
  playwright = require(path.join(FRONTEND_DIR, 'node_modules', 'playwright'));
} catch (error) {
  console.error('[verify] Playwright not found. Run npm ci in ecm-frontend.');
  process.exit(1);
}

const { chromium } = playwright;

const BASE_URL = process.env.ECM_FRONTEND_URL || 'http://localhost:5500';
const USER = process.env.ECM_VERIFY_USER || 'admin';
const PASS = process.env.ECM_VERIFY_PASS || 'admin';
const KEYCLOAK_URL = process.env.KEYCLOAK_URL || 'http://localhost:8180';
const KEYCLOAK_REALM = process.env.KEYCLOAK_REALM || 'ecm';
const API_BASE_OVERRIDE = process.env.ECM_API_URL || '';
const FILE_NAME_OVERRIDE = process.env.ECM_VERIFY_FILE || '';
const FILE_QUERY = process.env.ECM_VERIFY_QUERY || 'xlsx';
const SAMPLE_FILE_NAME = 'verify-wopi-sample.xlsx';
const SAMPLE_MIME_TYPE = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

const log = (message) => {
  process.stdout.write(`[verify] ${message}\n`);
};

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const loadXlsxSampleBuffer = () => {
  const samplePath = path.join(FRONTEND_DIR, 'e2e', 'fixtures', 'xlsxSample.ts');
  const sampleSource = fs.readFileSync(samplePath, 'utf8');
  const matches = [...sampleSource.matchAll(/'([^']+)'/g)];
  if (!matches.length) {
    throw new Error('Unable to load XLSX sample fixture');
  }
  const base64 = matches.map((match) => match[1]).join('');
  return Buffer.from(base64, 'base64');
};

const fetchAccessToken = async (request, username, password) => {
  const tokenRes = await request.post(`${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token`, {
    form: {
      grant_type: 'password',
      client_id: 'unified-portal',
      username,
      password,
    },
  });
  if (!tokenRes.ok()) {
    throw new Error(`Failed to obtain access token: ${tokenRes.status()}`);
  }
  const tokenJson = await tokenRes.json();
  if (!tokenJson.access_token) {
    throw new Error('Access token missing from Keycloak response');
  }
  return tokenJson.access_token;
};

const fetchSearchResults = async (request, apiBase, token, query) => {
  const searchRes = await request.get(`${apiBase}/api/v1/search`, {
    params: { q: query, size: 20 },
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!searchRes.ok()) {
    throw new Error(`Search API failed: ${searchRes.status()}`);
  }
  const searchJson = await searchRes.json();
  return searchJson.content || [];
};

const waitForSearchIndex = async (request, apiBase, token, query) => {
  for (let attempt = 0; attempt < 30; attempt += 1) {
    const results = await fetchSearchResults(request, apiBase, token, query);
    if (results.some((item) => item.name === query && item.nodeType === 'DOCUMENT')) {
      return true;
    }
    await sleep(2000);
  }
  return false;
};

const normalizeApiBase = (value) => {
  if (!value) {
    return '';
  }
  return value.replace(/\/api\/v1\/?$/, '').replace(/\/+$/, '');
};

const probeHealth = async (request, baseUrl) => {
  try {
    const res = await request.get(`${baseUrl}/actuator/health`);
    return res.ok();
  } catch {
    return false;
  }
};

const resolveApiBase = async (request) => {
  const normalizedOverride = normalizeApiBase(API_BASE_OVERRIDE);
  if (normalizedOverride) {
    return normalizedOverride;
  }
  const candidates = ['http://localhost:7700', 'http://localhost:8080'];
  for (const base of candidates) {
    if (await probeHealth(request, base)) {
      return base;
    }
  }
  return candidates[0];
};

const resolveDocumentsFolderId = async (request, apiBase, token) => {
  const rootsRes = await request.get(`${apiBase}/api/v1/folders/roots`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!rootsRes.ok()) {
    throw new Error(`Root folders request failed: ${rootsRes.status()}`);
  }
  const roots = await rootsRes.json();
  const rootFolder = roots.find((item) => item.name === 'Root') || roots[0];
  if (!rootFolder?.id) {
    throw new Error('Root folder not found');
  }

  const contentsRes = await request.get(`${apiBase}/api/v1/folders/${rootFolder.id}/contents`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!contentsRes.ok()) {
    throw new Error(`Root folder contents request failed: ${contentsRes.status()}`);
  }
  const contentsJson = await contentsRes.json();
  const documentsFolder = (contentsJson.content || []).find(
    (item) => item.nodeType === 'FOLDER' && item.name === 'Documents'
  );
  if (!documentsFolder?.id) {
    throw new Error('Documents folder not found');
  }
  return documentsFolder.id;
};

const uploadSampleDocument = async (request, apiBase, token) => {
  const folderId = await resolveDocumentsFolderId(request, apiBase, token);
  const buffer = loadXlsxSampleBuffer();
  const uploadRes = await request.post(`${apiBase}/api/v1/documents/upload?folderId=${folderId}`, {
    headers: { Authorization: `Bearer ${token}` },
    multipart: {
      file: {
        name: SAMPLE_FILE_NAME,
        mimeType: SAMPLE_MIME_TYPE,
        buffer,
      },
    },
  });
  if (!uploadRes.ok()) {
    throw new Error(`Sample upload failed: ${uploadRes.status()}`);
  }
  const uploadJson = await uploadRes.json();
  const documentId = uploadJson.documentId || uploadJson.id;
  if (!documentId) {
    throw new Error('Upload did not return document id');
  }

  const indexRes = await request.post(`${apiBase}/api/v1/search/index/${documentId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!indexRes.ok()) {
    throw new Error(`Search index request failed: ${indexRes.status()}`);
  }

  return { id: documentId, name: SAMPLE_FILE_NAME };
};

const findDocument = (results, preferredName) => {
  if (!Array.isArray(results)) {
    return null;
  }
  if (preferredName) {
    return results.find((item) => item.name === preferredName && item.nodeType === 'DOCUMENT') || null;
  }
  return results.find((item) => item.nodeType === 'DOCUMENT' && /\.xlsx$/i.test(item.name || '')) || null;
};

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();
  page.setDefaultTimeout(30000);

  log(`Opening ${BASE_URL}`);
  await page.goto(BASE_URL, { waitUntil: 'domcontentloaded' });

  const loginSelector = 'input[name="username"], input#username';
  try {
    await page.waitForSelector(loginSelector, { timeout: 15000 });
    log('Login form detected, signing in...');
    await page.fill(loginSelector, USER);
    await page.fill('input[name="password"], input#password', PASS);
    await page.click('input[type="submit"], button[type="submit"]');
  } catch {
    log('Login form not detected, assuming already authenticated');
  }

  await page.waitForURL(/localhost:5500/, { timeout: 60000 });
  log(`Current URL: ${page.url()}`);

  const apiBase = await resolveApiBase(page.request);
  log(`API base resolved to ${apiBase}`);
  const apiToken = await fetchAccessToken(page.request, USER, PASS);

  log('Fetching document via search API');
  let searchResults = await fetchSearchResults(page.request, apiBase, apiToken, FILE_QUERY);
  let documentMatch = findDocument(searchResults, FILE_NAME_OVERRIDE);

  if (!documentMatch && !FILE_NAME_OVERRIDE) {
    log(`No matching .xlsx document found, uploading ${SAMPLE_FILE_NAME} for verification...`);
    const sampleDocument = await uploadSampleDocument(page.request, apiBase, apiToken);
    await waitForSearchIndex(page.request, apiBase, apiToken, sampleDocument.name);
    searchResults = await fetchSearchResults(page.request, apiBase, apiToken, sampleDocument.name);
    documentMatch = findDocument(searchResults, sampleDocument.name);
  }

  if (!documentMatch?.id || !documentMatch?.name) {
    throw new Error('No matching .xlsx document found for verification');
  }
  const documentId = documentMatch.id;
  const documentName = documentMatch.name;

  log(`Using document: ${documentName} (${documentId})`);

  log('Opening advanced search');
  await page.getByRole('button', { name: 'Search' }).click();

  const dialog = page.getByRole('dialog');
  const searchRoot = (await dialog.count()) ? dialog : page;

  await searchRoot.getByRole('textbox', { name: 'Name contains' }).fill(documentName);
  await searchRoot.getByRole('button', { name: 'Search', exact: true }).click();

  await page.waitForURL(/\/search-results/, { timeout: 60000 });
  log('Search results loaded');

  const heading = page.getByRole('heading', { name: documentName }).first();
  const card = heading.locator('xpath=ancestor::div[contains(@class, "MuiCard-root")]').first();
  await card.getByRole('button', { name: 'View' }).click();

  log('Waiting for preview to load');
  await page.getByText(documentName).first().waitFor({ timeout: 60000 });
  await page.waitForSelector('iframe', { timeout: 60000 });
  const previewIframeSrc = await page.$eval('iframe', (el) => el.getAttribute('src'));
  log(`Preview iframe src: ${previewIframeSrc ? 'present' : 'missing'}`);

  log('Checking preview menu');
  await page.locator('button svg[data-testid="MoreVertIcon"]').first().click();
  const onlineMenuItem = page.getByRole('menuitem', { name: /^(View|Edit) Online$/ });
  await onlineMenuItem.first().waitFor({ timeout: 30000 });
  const menuLabel = (await onlineMenuItem.first().innerText())?.trim() || 'Online';
  const isEditOnline = menuLabel.toLowerCase().includes('edit');
  log(`Menu contains ${menuLabel}`);

  log(`Opening ${menuLabel}`);
  await onlineMenuItem.first().click();
  await page.waitForURL(
    new RegExp(`/editor/.+permission=${isEditOnline ? 'write' : 'read'}`),
    { timeout: 60000 }
  );
  await page.waitForSelector('iframe', { timeout: 60000 });
  const editorIframeSrc = await page.$eval('iframe', (el) => el.getAttribute('src'));
  log(`Editor iframe src: ${editorIframeSrc ? 'present' : 'missing'}`);

  log('Requesting WOPI write URL');
  const wopiRes = await page.request.get(`${apiBase}/api/v1/integration/wopi/url/${documentId}?permission=write`, {
    headers: { Authorization: `Bearer ${apiToken}` },
  });
  if (!wopiRes.ok()) {
    throw new Error(`WOPI URL request failed: ${wopiRes.status()}`);
  }
  const wopiJson = await wopiRes.json();
  const wopiUrl = wopiJson.wopiUrl;
  if (!wopiUrl) {
    throw new Error('WOPI URL missing from response');
  }

  const wopiParams = new URL(wopiUrl).searchParams;
  const wopiToken = wopiParams.get('access_token');
  if (!wopiToken) {
    throw new Error('WOPI access token missing');
  }

  const apiOrigin = new URL(apiBase).origin;
  const wopiGetUrl = `${apiOrigin}/wopi/files/${documentId}/contents?access_token=${encodeURIComponent(wopiToken)}`;
  const wopiPutUrl = `${apiOrigin}/wopi/files/${documentId}/contents?access_token=${encodeURIComponent(wopiToken)}`;

  log('Fetching WOPI content');
  const contentRes = await page.request.get(wopiGetUrl);
  if (!contentRes.ok()) {
    throw new Error(`WOPI GetFile failed: ${contentRes.status()}`);
  }
  const contentBuffer = await contentRes.body();

  log('Posting WOPI update (PutFile)');
  const putRes = await page.request.post(wopiPutUrl, {
    headers: { 'Content-Type': 'application/octet-stream' },
    data: contentBuffer,
  });
  if (!putRes.ok()) {
    throw new Error(`WOPI PutFile failed: ${putRes.status()}`);
  }

  log('Checking audit logs for WOPI_UPDATED');
  let auditMatch = null;
  for (let attempt = 0; attempt < 10; attempt += 1) {
    const auditRes = await page.request.get(`${apiBase}/api/v1/analytics/audit/recent?limit=50`, {
      headers: { Authorization: `Bearer ${apiToken}` },
    });
    if (!auditRes.ok()) {
      throw new Error(`Audit log request failed: ${auditRes.status()}`);
    }
    const auditJson = await auditRes.json();
    auditMatch = (auditJson || []).find(
      (item) => item.eventType === 'WOPI_UPDATED' && item.nodeId === documentId
    );
    if (auditMatch) {
      break;
    }
    await sleep(2000);
  }

  if (!auditMatch) {
    throw new Error('WOPI_UPDATED audit entry not found');
  }

  log(`Audit log entry found: ${auditMatch.eventType} at ${auditMatch.eventTime}`);

  await browser.close();
  log('Verification complete');
})().catch((err) => {
  console.error('[verify] Failed:', err);
  process.exitCode = 1;
});
