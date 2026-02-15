import { expect, test } from '@playwright/test';
import { fetchAccessToken, resolveApiUrl, waitForApiReady } from './helpers/api';
import { gotoWithAuthE2E } from './helpers/login';

const baseApiUrl = resolveApiUrl();
const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';

type IntegrationMailAccount = {
  id: string;
  name: string;
};

type IntegrationMailRule = {
  id: string;
  name: string;
  accountId?: string | null;
};

test('Mail automation Phase 6 integration: account health and preview dialog controls', async ({ page, request }) => {
  test.setTimeout(240_000);
  await waitForApiReady(request, { apiUrl: baseApiUrl });

  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  let createdAccountId: string | null = null;

  try {
    const accountsRes = await request.get(`${baseApiUrl}/api/v1/integration/mail/accounts`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(accountsRes.ok()).toBeTruthy();
    let accounts = (await accountsRes.json()) as IntegrationMailAccount[];

    if (accounts.length === 0) {
      const seedName = `e2e-phase6-account-${Date.now()}`;
      const createAccountRes = await request.post(`${baseApiUrl}/api/v1/integration/mail/accounts`, {
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        data: {
          name: seedName,
          host: 'imap.example.com',
          port: 993,
          username: 'e2e-phase6@example.com',
          password: 'dummy-password',
          security: 'SSL',
          enabled: false,
          pollIntervalMinutes: 10,
        },
      });
      expect(createAccountRes.ok()).toBeTruthy();
      const created = (await createAccountRes.json()) as { id?: string };
      expect(created.id).toBeTruthy();
      createdAccountId = created.id || null;

      const seededAccountsRes = await request.get(`${baseApiUrl}/api/v1/integration/mail/accounts`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      expect(seededAccountsRes.ok()).toBeTruthy();
      accounts = (await seededAccountsRes.json()) as IntegrationMailAccount[];
      expect(accounts.length).toBeGreaterThan(0);
    }

    const rulesRes = await request.get(`${baseApiUrl}/api/v1/integration/mail/rules`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(rulesRes.ok()).toBeTruthy();
    const rules = (await rulesRes.json()) as IntegrationMailRule[];

    let previewCandidate: { ruleId: string; ruleName: string } | null = null;
    for (const rule of rules) {
      const accountId = rule.accountId || accounts[0]?.id;
      if (!accountId) {
        continue;
      }
      const previewProbeRes = await request.post(`${baseApiUrl}/api/v1/integration/mail/rules/${rule.id}/preview`, {
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        data: { accountId, maxMessagesPerFolder: 5 },
      });
      if (previewProbeRes.ok()) {
        previewCandidate = { ruleId: rule.id, ruleName: rule.name };
        break;
      }
    }

    await gotoWithAuthE2E(page, '/admin/mail', defaultUsername, defaultPassword, { token });
    await expect(page.getByRole('heading', { name: 'Mail Automation' })).toBeVisible({ timeout: 60_000 });

    await expect(page.getByText('Account health')).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(`Total ${accounts.length}`)).toBeVisible({ timeout: 30_000 });

    const diagnosticsSection = page.locator('#diagnostics');
    await expect(diagnosticsSection.getByText('Last fetch summary')).toBeVisible({ timeout: 30_000 });
    await expect(diagnosticsSection.getByRole('button', { name: 'Refresh status' })).toBeVisible({ timeout: 30_000 });

    await expect(page.getByRole('button', { name: 'All', exact: true }).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByRole('button', { name: 'Processed', exact: true }).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByRole('button', { name: 'Error', exact: true }).first()).toBeVisible({ timeout: 30_000 });

    if (!previewCandidate) {
      test.info().annotations.push({
        type: 'note',
        description: 'No preview-capable rule found; skipped preview dialog assertions.',
      });
      return;
    }

    const previewButton = page.getByLabel(`Preview rule ${previewCandidate.ruleName}`).first();
    await expect(previewButton).toBeVisible({ timeout: 30_000 });
    await previewButton.click();

    const dialog = page.getByRole('dialog', { name: /preview mail rule/i });
    await expect(dialog).toBeVisible({ timeout: 30_000 });

    await dialog.getByRole('button', { name: 'Run Preview' }).click();
    await expect(dialog.getByText('Summary')).toBeVisible({ timeout: 60_000 });
    await expect(dialog.getByLabel('Processable')).toBeVisible({ timeout: 30_000 });
    await expect(dialog.getByRole('button', { name: 'Copy JSON' })).toBeVisible({ timeout: 30_000 });

    await dialog.getByLabel('Processable').click();
    await page.getByRole('option', { name: 'Processable only' }).click();
    await expect(dialog).toBeVisible({ timeout: 30_000 });
  } finally {
    if (createdAccountId) {
      await request.delete(`${baseApiUrl}/api/v1/integration/mail/accounts/${createdAccountId}`, {
        headers: { Authorization: `Bearer ${token}` },
      }).catch(() => null);
    }
  }
});
