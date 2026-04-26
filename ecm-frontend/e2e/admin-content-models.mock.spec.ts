import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';
import { mockKeycloakUnreachable } from './helpers/keycloakMock';

// ---------------------------------------------------------------------------
// Mock data
// ---------------------------------------------------------------------------

const PROPERTY_TITLE: any = {
  id: 'prop-001',
  name: 'title',
  title: 'Document Title',
  dataType: 'TEXT',
  mandatory: true,
  multiValued: false,
  indexed: true,
  protectedField: false,
  encrypted: false,
  qualifiedName: 'acme:title',
  constraints: [],
};

const PROPERTY_DESCRIPTION: any = {
  id: 'prop-002',
  name: 'description',
  title: 'Description',
  dataType: 'MLTEXT',
  mandatory: false,
  multiValued: false,
  indexed: true,
  protectedField: false,
  encrypted: false,
  qualifiedName: 'acme:description',
  constraints: [],
};

const TYPE_INVOICE: any = {
  id: 'type-001',
  name: 'invoice',
  title: 'Invoice',
  description: 'Invoice document type',
  parentName: 'cm:content',
  qualifiedName: 'acme:invoice',
  mandatoryAspects: [],
  properties: [PROPERTY_TITLE, PROPERTY_DESCRIPTION],
};

const ASPECT_AUDITABLE: any = {
  id: 'aspect-001',
  name: 'auditable',
  title: 'Auditable',
  description: 'Tracks audit metadata',
  parentName: null,
  qualifiedName: 'acme:auditable',
  properties: [
    {
      id: 'prop-003',
      name: 'createdBy',
      title: 'Created By',
      dataType: 'TEXT',
      mandatory: false,
      multiValued: false,
      indexed: true,
      protectedField: false,
      encrypted: false,
      qualifiedName: 'acme:createdBy',
      constraints: [],
    },
  ],
};

const MODEL_ACME: any = {
  id: 'model-001',
  namespaceUri: 'https://www.acme.com/model/1.0',
  prefix: 'acme',
  name: 'contentModel',
  description: 'ACME custom content model',
  author: 'admin',
  status: 'ACTIVE',
  versionLabel: '1.0',
  types: [TYPE_INVOICE],
  aspects: [ASPECT_AUDITABLE],
};

// ---------------------------------------------------------------------------
// Route helpers
// ---------------------------------------------------------------------------

// Register all routes that ContentModelsPage fires on mount.
// Playwright matches in REVERSE registration order (last registered = highest priority).
// Catch-alls are registered FIRST (lowest priority); specific sub-paths are registered
// LAST so they win over the generic catch-alls.
const registerContentModelRoutes = async (page: any) => {
  // Content models list — registered first (lowest priority)
  await page.route('**/api/v1/content-models', async (route: any) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([MODEL_ACME]),
    });
  });

  // List all dictionary types (bare /dictionary/types)
  await page.route('**/api/v1/dictionary/types', async (route: any) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([TYPE_INVOICE]),
    });
  });

  // List all dictionary aspects (bare /dictionary/aspects)
  await page.route('**/api/v1/dictionary/aspects', async (route: any) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([ASPECT_AUDITABLE]),
    });
  });

  // Dictionary type detail catch-all — /dictionary/types/{qualifiedName}
  await page.route('**/api/v1/dictionary/types/**', async (route: any) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(TYPE_INVOICE),
    });
  });

  // Dictionary aspect detail catch-all — /dictionary/aspects/{qualifiedName}
  await page.route('**/api/v1/dictionary/aspects/**', async (route: any) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(ASPECT_AUDITABLE),
    });
  });

  // Dictionary type mandatory-aspects sub-path — registered after catch-alls (higher priority)
  await page.route('**/api/v1/dictionary/types/**/mandatory-aspects', async (route: any) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([]),
    });
  });

  // Dictionary type hierarchy sub-path — registered last (highest priority)
  await page.route('**/api/v1/dictionary/types/**/hierarchy', async (route: any) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(['cm:base', 'cm:content', 'acme:invoice']),
    });
  });
};

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

test('shows content models list', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');
  await registerContentModelRoutes(page);

  await page.goto('/admin/content-models');

  // Page heading
  await expect(page.getByRole('heading', { name: 'Content Models' })).toBeVisible();

  // Model Registry card heading
  await expect(page.getByText('Model Registry')).toBeVisible();

  // The model row shows prefix:name — "acme:contentModel" (exact to avoid matching alert messages)
  await expect(page.getByText('acme:contentModel', { exact: true })).toBeVisible();

  // Status chip
  await expect(page.getByText('ACTIVE', { exact: true }).first()).toBeVisible();
});

test('shows type detail and properties panel', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');
  await registerContentModelRoutes(page);

  await page.goto('/admin/content-models');

  // Wait for the model to appear
  await expect(page.getByText('acme:contentModel', { exact: true })).toBeVisible();

  // The selected model card shows the type chip; auto-select fires on mount
  // because selectedTypeName is initialised to the first type's qualifiedName.
  // Dictionary Type Explorer card must be visible
  await expect(page.getByText('Dictionary Type Explorer')).toBeVisible();

  // The type is auto-selected on mount (first in the list), so its qualified name
  // appears in the select element and the properties table renders
  await expect(page.getByText('acme:invoice', { exact: true }).first()).toBeVisible();

  // Properties from TYPE_INVOICE — the property table shows title or name
  await expect(page.getByText('Document Title')).toBeVisible();

  // Hierarchy section is rendered from the mock array
  await expect(page.getByText('Hierarchy')).toBeVisible();
});

test('create model dialog opens', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');
  await registerContentModelRoutes(page);

  await page.goto('/admin/content-models');
  await expect(page.getByText('Model Registry')).toBeVisible();

  await page.getByRole('button', { name: 'New Model' }).click();

  // Dialog title
  await expect(page.getByRole('dialog').getByText('Create Content Model')).toBeVisible();

  // Required fields present in dialog — MUI renders required labels as "Field *"
  await expect(page.getByLabel('Prefix *', { exact: true })).toBeVisible();
  await expect(page.getByLabel('Name *', { exact: true })).toBeVisible();
  await expect(page.getByLabel('Namespace URI *', { exact: true })).toBeVisible();

  // Action buttons
  await expect(page.getByRole('button', { name: 'Create' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Cancel' })).toBeVisible();
});

test('shows Dictionary Aspect Explorer section with aspect name', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');
  await registerContentModelRoutes(page);

  await page.goto('/admin/content-models');

  // Aspect Explorer card is always rendered
  await expect(page.getByText('Dictionary Aspect Explorer')).toBeVisible();

  // The aspect is auto-selected on mount (first in listAspects response)
  // so its qualified name appears in the select and the card shows its details
  await expect(page.getByText('acme:auditable', { exact: true }).first()).toBeVisible();

  // The selected model card also shows the aspect chip
  await expect(page.getByText('Aspects', { exact: true }).first()).toBeVisible();
});
