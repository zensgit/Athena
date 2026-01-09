import { APIRequestContext, expect } from '@playwright/test';

const DEFAULT_API_URL = process.env.ECM_API_URL || 'http://localhost:7700';
const DEFAULT_KEYCLOAK_URL = process.env.KEYCLOAK_URL || 'http://localhost:8180';
const DEFAULT_KEYCLOAK_REALM = process.env.KEYCLOAK_REALM || 'ecm';
const DEFAULT_KEYCLOAK_CLIENT_ID = process.env.KEYCLOAK_CLIENT_ID || 'unified-portal';

type AccessTokenOptions = {
  keycloakUrl?: string;
  realm?: string;
  clientId?: string;
  timeoutMs?: number;
  delayMs?: number;
};

export async function fetchAccessToken(
  request: APIRequestContext,
  username: string,
  password: string,
  options: AccessTokenOptions = {},
) {
  const timeoutMs = options.timeoutMs ?? 60_000;
  const delayMs = options.delayMs ?? 2000;
  const keycloakUrl = options.keycloakUrl ?? DEFAULT_KEYCLOAK_URL;
  const realm = options.realm ?? DEFAULT_KEYCLOAK_REALM;
  const clientId = options.clientId ?? DEFAULT_KEYCLOAK_CLIENT_ID;
  const deadline = Date.now() + timeoutMs;
  let lastError: string | undefined;

  while (Date.now() < deadline) {
    try {
      const tokenRes = await request.post(`${keycloakUrl}/realms/${realm}/protocol/openid-connect/token`, {
        form: {
          grant_type: 'password',
          client_id: clientId,
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

    await new Promise((resolve) => setTimeout(resolve, delayMs));
  }

  throw new Error(`Failed to obtain access token for API calls: ${lastError ?? 'unknown error'}`);
}

type ApiReadyOptions = {
  apiUrl?: string;
  timeoutMs?: number;
  delayMs?: number;
};

export async function waitForApiReady(
  request: APIRequestContext,
  options: ApiReadyOptions = {},
) {
  const apiUrl = options.apiUrl ?? DEFAULT_API_URL;
  const timeoutMs = options.timeoutMs ?? 60_000;
  const delayMs = options.delayMs ?? 2000;
  const deadline = Date.now() + timeoutMs;
  let lastError: string | undefined;

  while (Date.now() < deadline) {
    try {
      const res = await request.get(`${apiUrl}/actuator/health`);
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

    await new Promise((resolve) => setTimeout(resolve, delayMs));
  }

  throw new Error(`API did not become ready: ${lastError ?? 'unknown error'}`);
}

type SearchIndexOptions = {
  apiUrl?: string;
  maxAttempts?: number;
  delayMs?: number;
  minResults?: number;
  pageSize?: number;
};

export async function waitForSearchIndex(
  request: APIRequestContext,
  query: string,
  token: string,
  options: SearchIndexOptions = {},
) {
  const apiUrl = options.apiUrl ?? DEFAULT_API_URL;
  const maxAttempts = options.maxAttempts ?? 30;
  const delayMs = options.delayMs ?? 2000;
  const minResults = options.minResults;
  const pageSize = options.pageSize ?? (minResults ? Math.max(minResults, 100) : 10);
  let lastError = 'unknown error';

  for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
    try {
      const res = await request.get(`${apiUrl}/api/v1/search`, {
        params: { q: query, page: 0, size: pageSize },
        headers: { Authorization: `Bearer ${token}` },
      });
      if (res.ok()) {
        const payload = (await res.json()) as { content?: Array<{ name: string }> };
        const content = payload.content ?? [];
        if (minResults !== undefined) {
          if (content.length >= minResults) {
            return;
          }
          lastError = `status=${res.status()} count=${content.length}`;
        } else if (content.some((item) => item.name === query)) {
          return;
        } else {
          lastError = `status=${res.status()}`;
        }
      } else {
        lastError = `status=${res.status()}`;
      }
    } catch (error) {
      lastError = error instanceof Error ? error.message : String(error);
    }
    await new Promise((resolve) => setTimeout(resolve, delayMs));
  }

  if (minResults !== undefined) {
    throw new Error(`Search index did not return ${minResults} results for ${query} (${lastError})`);
  }
  throw new Error(`Search index did not include '${query}' (${lastError})`);
}

type ListResponse<T> = {
  content?: T[];
};

type WaitForListOptions<T> = {
  url: string;
  token: string;
  params?: Record<string, string | number>;
  predicate: (item: T) => boolean;
  maxAttempts?: number;
  delayMs?: number;
  description?: string;
};

export async function waitForListItem<T>(
  request: APIRequestContext,
  options: WaitForListOptions<T>,
) {
  const {
    url,
    token,
    params,
    predicate,
    maxAttempts = 30,
    delayMs = 1000,
    description,
  } = options;

  for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
    const response = await request.get(url, {
      params,
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(response.ok()).toBeTruthy();
    const payload = (await response.json()) as ListResponse<T>;
    const match = payload.content?.find(predicate);
    if (match) {
      return match;
    }
    await new Promise((resolve) => setTimeout(resolve, delayMs));
  }

  throw new Error(`${description ?? 'Item'} not found after create`);
}

type ListLookupOptions = {
  apiUrl?: string;
  maxAttempts?: number;
  delayMs?: number;
  pageSize?: number;
};

export async function findChildFolderId(
  request: APIRequestContext,
  parentId: string,
  folderName: string,
  token: string,
  options: ListLookupOptions = {},
) {
  const apiUrl = options.apiUrl ?? DEFAULT_API_URL;
  const match = await waitForListItem<{ id: string; name: string; nodeType: string }>(request, {
    url: `${apiUrl}/api/v1/folders/${parentId}/contents`,
    token,
    params: {
      page: 0,
      size: options.pageSize ?? 200,
      sort: 'name,asc',
    },
    predicate: (node) => node.name === folderName && node.nodeType === 'FOLDER',
    maxAttempts: options.maxAttempts,
    delayMs: options.delayMs,
    description: `Folder '${folderName}'`,
  });

  return match.id;
}

export async function findDocumentId(
  request: APIRequestContext,
  folderId: string,
  filename: string,
  token: string,
  options: ListLookupOptions = {},
) {
  const apiUrl = options.apiUrl ?? DEFAULT_API_URL;
  const match = await waitForListItem<{ id: string; name: string; nodeType: string }>(request, {
    url: `${apiUrl}/api/v1/folders/${folderId}/contents`,
    token,
    params: {
      page: 0,
      size: options.pageSize ?? 1000,
      sort: 'name,asc',
    },
    predicate: (node) => node.name === filename && node.nodeType === 'DOCUMENT',
    maxAttempts: options.maxAttempts,
    delayMs: options.delayMs,
    description: `Document '${filename}'`,
  });

  return match.id;
}

type RootFolder = { id: string; name: string; path?: string; folderType?: string };

export async function getRootFolderId(
  request: APIRequestContext,
  token: string,
  options: { apiUrl?: string } = {},
) {
  const apiUrl = options.apiUrl ?? DEFAULT_API_URL;
  const res = await request.get(`${apiUrl}/api/v1/folders/roots`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(res.ok()).toBeTruthy();
  const roots = (await res.json()) as RootFolder[];
  if (!roots.length) {
    throw new Error('No root folders returned');
  }
  const preferred = roots.find((root) => {
    const isSystem = root.folderType?.toUpperCase() === 'SYSTEM';
    return isSystem || root.name === 'Root' || root.path === '/Root';
  });
  return (preferred ?? roots[0]).id;
}
