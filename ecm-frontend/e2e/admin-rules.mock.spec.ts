import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';
import { mockKeycloakUnreachable } from './helpers/keycloakMock';

const RULE_EVENT_TRIGGERED: any = {
  id: 'rule-001',
  name: 'Auto-tag on Document Created',
  description: 'Tags every new document as "new-upload"',
  triggerType: 'DOCUMENT_CREATED',
  priority: 100,
  enabled: true,
  stopOnMatch: false,
  scopeMimeTypes: '',
  scopeFolderId: null,
  condition: { type: 'ALWAYS_TRUE' },
  actions: [{ type: 'ADD_TAG', params: { tagName: 'new-upload' }, continueOnError: true, order: 0 }],
  executionCount: 42,
  failureCount: 1,
  owner: 'admin',
  createdDate: '2026-04-01T00:00:00',
};

const RULE_SCHEDULED: any = {
  id: 'rule-002',
  name: 'Nightly Archive Sweep',
  description: 'Runs every night at midnight to archive old documents',
  triggerType: 'SCHEDULED',
  cronExpression: '0 0 0 * * *',
  timezone: 'UTC',
  maxItemsPerRun: 200,
  manualBackfillMinutes: 30,
  priority: 200,
  enabled: true,
  stopOnMatch: false,
  scopeMimeTypes: '',
  scopeFolderId: null,
  condition: { type: 'ALWAYS_TRUE' },
  actions: [{ type: 'MOVE_TO_ARCHIVE', params: {}, continueOnError: false, order: 0 }],
  executionCount: 10,
  failureCount: 0,
  owner: 'admin',
};

const RULES_PAGE_RESPONSE = {
  content: [RULE_EVENT_TRIGGERED, RULE_SCHEDULED],
  totalElements: 2,
  totalPages: 1,
  number: 0,
  size: 100,
};

const STATS_RESPONSE = {
  totalRules: 2,
  enabledRules: 2,
  disabledRules: 0,
  totalExecutions: 52,
  totalFailures: 1,
  successRate: 98.1,
};

// Register all mock routes that fire on RulesPage mount.
// Playwright matches in reverse registration order (last registered = first matched),
// so specific paths are registered last (highest priority).
const registerRuleRoutes = async (page: any) => {
  // Catch-all for audit timeline
  await page.route('**/api/v1/rules/executions/audit**', async (route: any) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) });
  });
  // Catch-all for execution timeline
  await page.route('**/api/v1/rules/executions/timeline**', async (route: any) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) });
  });
  // Action definitions (wrapper shape: { actions: [] })
  await page.route('**/api/v1/rules/actions/definitions', async (route: any) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ actions: [] }),
    });
  });
  // Stats
  await page.route('**/api/v1/rules/stats', async (route: any) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(STATS_RESPONSE),
    });
  });
  // Templates
  await page.route('**/api/v1/rules/templates', async (route: any) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) });
  });
  // Validate cron (pre-register so any dialog action doesn't hit real backend)
  await page.route('**/api/v1/rules/validate-cron', async (route: any) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ valid: true, nextExecutions: [] }),
    });
  });
  // Validate condition
  await page.route('**/api/v1/rules/validate', async (route: any) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ valid: true, message: 'Condition valid' }),
    });
  });
  // Paginated rule list — registered last (highest priority in LIFO).
  // Uses a broad glob to match query params (?page=0&size=100&sort=...) and a URL check
  // to call route.fallback() for sub-path requests so the specific handlers above handle those.
  await page.route('**/api/v1/rules**', async (route: any) => {
    const url = route.request().url();
    if (/\/api\/v1\/rules\//.test(url)) {
      await route.fallback();
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(RULES_PAGE_RESPONSE),
    });
  });
};

test('shows automation rules list', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');
  await registerRuleRoutes(page);

  await page.goto('/rules');

  // Page heading
  await expect(page.getByRole('heading', { name: 'Automation Rules' })).toBeVisible();

  // Both rule names from mock data must appear in the table
  await expect(page.getByText('Auto-tag on Document Created')).toBeVisible();
  await expect(page.getByText('Nightly Archive Sweep')).toBeVisible();

  // Stats chips rendered from STATS_RESPONSE
  await expect(page.getByText('Total: 2')).toBeVisible();
  await expect(page.getByText('Enabled: 2')).toBeVisible();
});

test('shows scheduled vs manual rule distinction', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');
  await registerRuleRoutes(page);

  await page.goto('/rules');

  // The SCHEDULED rule shows its trigger type in the table
  await expect(page.getByText('SCHEDULED')).toBeVisible();

  // The SCHEDULED row shows a "Backfill: 30m" caption (manualBackfillMinutes: 30)
  await expect(page.getByText('Backfill: 30m')).toBeVisible();

  // The event-triggered rule shows its trigger type in the table cell
  await expect(page.getByRole('cell', { name: 'DOCUMENT_CREATED' })).toBeVisible();
});

test('create rule dialog opens', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');
  await registerRuleRoutes(page);

  await page.goto('/rules');

  // Wait for the page to render before clicking
  await expect(page.getByRole('heading', { name: 'Automation Rules' })).toBeVisible();

  await page.getByRole('button', { name: 'New Rule' }).click();

  // Dialog title
  await expect(page.getByRole('dialog').getByText('New Rule')).toBeVisible();

  // Key form fields inside the dialog
  await expect(page.getByLabel('Name')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Save' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Cancel' })).toBeVisible();
});

test('toggle rule enabled / disabled', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');
  await registerRuleRoutes(page);

  // Mock the PATCH disable endpoint for rule-001 (enabled: true → disable)
  let disableCalled = false;
  await page.route('**/api/v1/rules/rule-001/disable', async (route: any) => {
    disableCalled = true;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ ...RULE_EVENT_TRIGGERED, enabled: false }),
    });
  });

  await page.goto('/rules');
  await expect(page.getByText('Auto-tag on Document Created')).toBeVisible();

  // The switch for rule-001 (Auto-tag on Document Created) is in its own row.
  // Locate the row then its checkbox/switch.
  const ruleRow = page.getByRole('row').filter({ hasText: 'Auto-tag on Document Created' });
  const toggleSwitch = ruleRow.getByRole('checkbox');

  // Rule starts enabled, so switch is checked
  await expect(toggleSwitch).toBeChecked();

  // Click to disable
  await toggleSwitch.click();

  // The optimistic state update unchecks the switch and the API was called
  await expect(toggleSwitch).not.toBeChecked();
  expect(disableCalled).toBe(true);
});
