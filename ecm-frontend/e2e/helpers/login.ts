import { expect, Page } from '@playwright/test';
import { fetchAccessToken } from './api';

type LoginHelperOptions = {
  token?: string;
};

const resolveRolesForUser = (username: string): string[] => {
  const adminUsername = process.env.ECM_E2E_USERNAME || 'admin';
  const editorUsername = process.env.ECM_E2E_EDITOR_USERNAME || 'editor';
  const viewerUsername = process.env.ECM_E2E_VIEWER_USERNAME || 'viewer';

  if (username === adminUsername) {
    return ['ROLE_ADMIN'];
  }
  if (username === editorUsername) {
    return ['ROLE_EDITOR'];
  }
  if (username === viewerUsername) {
    return ['ROLE_VIEWER'];
  }
  return ['ROLE_ADMIN'];
};

const seedBypassSession = async (page: Page, username: string, token: string) => {
  const roles = resolveRolesForUser(username);
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
        roles,
      },
    }
  );
};

export async function loginWithCredentialsE2E(
  page: Page,
  username: string,
  password: string,
  options: LoginHelperOptions = {}
) {
  const forceUiLogin = process.env.ECM_E2E_FORCE_UI_LOGIN === '1';
  if (!forceUiLogin) {
    const resolvedToken = options.token
      ?? await fetchAccessToken(page.request, username, password).catch(() => undefined);
    if (resolvedToken) {
      await seedBypassSession(page, username, resolvedToken);
      await page.goto('/browse/root', { waitUntil: 'domcontentloaded' });
      await page.waitForURL(/\/browse\//, { timeout: 60_000 });
      await expect(page.getByText('Athena ECM')).toBeVisible({ timeout: 60_000 });
      return;
    }
  }

  if (process.env.ECM_E2E_SKIP_LOGIN === '1') {
    const resolvedToken = options.token
      ?? await fetchAccessToken(page.request, username, password).catch(() => undefined);
    if (resolvedToken) {
      await seedBypassSession(page, username, resolvedToken);
      await page.goto('/browse/root', { waitUntil: 'domcontentloaded' });
      await page.waitForURL(/\/browse\//, { timeout: 60_000 });
      await expect(page.getByText('Athena ECM')).toBeVisible({ timeout: 60_000 });
      return;
    }
  }

  const authPattern = /\/protocol\/openid-connect\/auth/;
  const browsePattern = /\/browse\//;

  await page.goto('/login', { waitUntil: 'domcontentloaded' });

  for (let attempt = 0; attempt < 4; attempt += 1) {
    await page.waitForURL(/(\/login$|\/browse\/|\/protocol\/openid-connect\/auth|login_required)/, {
      timeout: 60_000,
    });

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
