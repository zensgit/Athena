import {
  applyRenditionMutationToDefinitions,
  formatRenditionMutationSummary,
  formatRenditionDefinitionLine,
  formatRenditionGenerationMode,
  getRenditionDefinitionDisplayState,
  getRenditionDefinitionLines,
  summarizeRenditionDefinitions,
} from './renditionDefinitionUtils';

import type { NodeRenditionDefinitionStatus, NodeRenditionMutationResponse } from 'services/nodeService';

const buildDefinition = (
  overrides: Partial<NodeRenditionDefinitionStatus>
): NodeRenditionDefinitionStatus => ({
  nodeId: 'node-1',
  renditionKey: 'preview',
  label: 'Preview',
  targetMimeType: 'application/json',
  generationMode: 'PREVIEW_PIPELINE',
  downloadable: false,
  sortOrder: 0,
  dependencyRenditionKey: null,
  registered: true,
  applicable: true,
  applicabilityReason: null,
  currentState: 'READY',
  available: true,
  contentUrl: '/api/v1/documents/node-1/preview',
  ...overrides,
});

const buildMutation = (
  overrides: Partial<NodeRenditionMutationResponse>
): NodeRenditionMutationResponse => ({
  renditionKey: 'preview',
  action: 'REQUEUE',
  invalidated: false,
  previewLinked: true,
  message: 'Queued preview-linked rendition pipeline',
  queueStatus: null,
  previewSummary: {
    nodeId: 'node-1',
    document: true,
    previewStatus: 'PROCESSING',
    renditionAvailable: false,
    previewFailureReason: null,
    previewFailureCategory: null,
    previewLastUpdated: '2026-03-28T18:12:00',
    currentVersionLabel: '1.4',
  },
  resource: {
    id: 'resource-1',
    documentId: 'node-1',
    renditionKey: 'preview',
    label: 'Preview',
    mimeType: 'application/json',
    state: 'PROCESSING',
    available: false,
    downloadable: false,
    applicable: true,
    applicabilityReason: null,
    generationMode: 'PREVIEW_PIPELINE',
    dependencyRenditionKey: null,
    contentUrl: '/api/v1/documents/node-1/preview',
    errorReason: null,
    errorCategory: null,
    sourceStatus: 'PROCESSING',
    versionLabel: '1.4',
    sourceUpdatedAt: '2026-03-28T18:12:00',
    lastSyncedAt: '2026-03-28T18:12:00',
    sortOrder: 0,
  },
  ...overrides,
});

describe('renditionDefinitionUtils', () => {
  it('formats generation modes for operator surfaces', () => {
    expect(formatRenditionGenerationMode('PREVIEW_PIPELINE')).toBe('preview pipeline');
    expect(formatRenditionGenerationMode('PREVIEW_DERIVED')).toBe('preview-derived');
    expect(formatRenditionGenerationMode('CUSTOM_MODE')).toBe('CUSTOM_MODE');
  });

  it('maps registered state to pending when applicable but not yet materialized', () => {
    expect(getRenditionDefinitionDisplayState(buildDefinition({
      currentState: 'REGISTERED',
      applicable: true,
    }))).toBe('pending');
  });

  it('prefers not-applicable over raw state labels', () => {
    expect(getRenditionDefinitionDisplayState(buildDefinition({
      applicable: false,
      currentState: 'REGISTERED',
    }))).toBe('not applicable');
  });

  it('formats definition lines with mode, dependency and applicability reason', () => {
    expect(formatRenditionDefinitionLine(buildDefinition({
      label: 'Thumbnail',
      renditionKey: 'thumbnail',
      generationMode: 'PREVIEW_DERIVED',
      dependencyRenditionKey: 'preview',
      applicable: false,
      applicabilityReason: 'Thumbnail definition depends on a preview-eligible source rendition',
      currentState: 'REGISTERED',
      sortOrder: 1,
    }))).toBe(
      'Thumbnail not applicable • via preview-derived • depends on preview • Thumbnail definition depends on a preview-eligible source rendition'
    );
  });

  it('summarizes sorted definition lines and truncates overflow', () => {
    const lines = getRenditionDefinitionLines([
      buildDefinition({
        label: 'Thumbnail',
        renditionKey: 'thumbnail',
        generationMode: 'PREVIEW_DERIVED',
        dependencyRenditionKey: 'preview',
        currentState: 'REGISTERED',
        available: false,
        sortOrder: 1,
      }),
      buildDefinition({ sortOrder: 0 }),
      buildDefinition({
        label: 'Poster',
        renditionKey: 'poster',
        generationMode: 'CUSTOM_MODE',
        currentState: 'FAILED',
        sortOrder: 2,
      }),
    ]);

    expect(lines[0]).toBe('Preview ready • via preview pipeline');
    expect(lines[1]).toBe('Thumbnail pending • via preview-derived • depends on preview');
    expect(summarizeRenditionDefinitions([
      buildDefinition({ sortOrder: 0 }),
      buildDefinition({
        label: 'Thumbnail',
        renditionKey: 'thumbnail',
        generationMode: 'PREVIEW_DERIVED',
        dependencyRenditionKey: 'preview',
        currentState: 'REGISTERED',
        sortOrder: 1,
      }),
      buildDefinition({
        label: 'Poster',
        renditionKey: 'poster',
        generationMode: 'CUSTOM_MODE',
        currentState: 'FAILED',
        sortOrder: 2,
      }),
    ], 2)).toBe(
      'Preview ready • via preview pipeline • Thumbnail pending • via preview-derived • depends on preview • +1 more'
    );
  });

  it('applies rendition mutation state back into the matching definition', () => {
    const updated = applyRenditionMutationToDefinitions([
      buildDefinition({ currentState: 'FAILED', available: false, contentUrl: null }),
      buildDefinition({ renditionKey: 'thumbnail', label: 'Thumbnail', sortOrder: 1 }),
    ], buildMutation({
      resource: {
        ...buildMutation({}).resource,
        state: 'PROCESSING',
        available: true,
        contentUrl: '/api/v1/documents/node-1/preview?fresh=true',
      },
    }));

    expect(updated[0].currentState).toBe('PROCESSING');
    expect(updated[0].available).toBe(true);
    expect(updated[0].contentUrl).toBe('/api/v1/documents/node-1/preview?fresh=true');
    expect(updated[1].renditionKey).toBe('thumbnail');
  });

  it('formats mutation summary with effective preview outcome', () => {
    expect(formatRenditionMutationSummary(buildMutation({
      previewSummary: {
        ...buildMutation({}).previewSummary!,
        previewStatus: 'UNSUPPORTED',
        previewFailureReason: 'Preview definition is not registered for generic binary sources',
      },
    }))).toBe(
      'Queued preview-linked rendition pipeline • Effective preview unsupported: Preview definition is not registered for generic binary sources'
    );
  });
});
