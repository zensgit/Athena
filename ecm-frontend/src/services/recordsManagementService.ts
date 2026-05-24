import api from './api';
import {
  BulkDeclareErrorCategory,
  BulkDeclareRequest,
  BulkDeclareResponse,
  BulkDeclareResult,
  BulkDeclareStatus,
  CreateFilePlanRequest,
  CreateRecordCategoryRequest,
  RecordsActivityBreakdown,
  RecordsActivityContributorFamilyHighlights,
  RecordsActivityContributorFamilyTrend,
  RecordsActivityContributorEventTypeHighlights,
  RecordsActivityContributorEventTypeTrend,
  RecordsActivityContributors,
  RecordsActivityEventTypes,
  RecordsActivityFamilies,
  RecordsActivityFamilyHighlights,
  RecordsActivityHighlights,
  DeclareRecordRequest,
  FilePlan,
  MoveFilePlanRequest,
  MoveRecordCategoryRequest,
  RecordsActivityTimeline,
  RecordsOperationsTelemetry,
  PageResponse,
  RecordAuditEntry,
  RecordCategory,
  RecordDeclaration,
  RenameFilePlanRequest,
  RenameRecordCategoryRequest,
  RmReportPreset,
  RmReportPresetExecution,
  RmReportPresetExecutionStatus,
  RmReportPresetExecutionTrigger,
  RmReportPresetKind,
  RmReportPresetScheduleStatus,
  RmScheduledDeliveryTelemetry,
  RecordsSummary,
  UpdateFilePlanRequest,
  UpdateRecordCategoryRequest,
  UndeclareRecordRequest,
} from 'types';

export type RmDeliverableReportPresetKind = RmReportPresetKind;

export const RECORDS_MANAGEMENT_UNEXPECTED_RESPONSE_MESSAGE =
  'Records management endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

// Dedicated sentinel for the bulk-declare endpoint. Phase 5 Mocked HTML-fallback drift on
// a brand-new route returns SPA index.html with HTTP 200; a separate sentinel makes the
// failure surface for the new endpoint independently grep-able from existing RM routes.
// See feedback_phase5_mocked_html_fallback.
export const RECORDS_MANAGEMENT_BULK_DECLARE_UNEXPECTED_RESPONSE_MESSAGE =
  'Bulk record declaration endpoint returned an unexpected response. Mocked CI gate may not cover /api/v1/nodes/bulk-declare; verify the backend wrapper shape (bulkDeclareResults.rows).';

const RM_REPORT_PRESET_KINDS: RmReportPresetKind[] = [
  'ACTIVITY_FAMILY_REPORT',
  'ACTIVITY_FAMILY_HIGHLIGHTS',
  'ACTIVITY_FAMILY_MIX',
  'ACTIVITY_EVENT_TYPE_REPORT',
  'ACTIVITY_CONTRIBUTOR_REPORT',
  'ACTIVITY_CONTRIBUTOR_FAMILY_REPORT',
  'ACTIVITY_CONTRIBUTOR_EVENT_TYPE_REPORT',
];

const DELIVERABLE_REPORT_PRESET_KINDS = new Set<RmReportPresetKind>(RM_REPORT_PRESET_KINDS);
const RM_REPORT_PRESET_EXECUTION_TRIGGERS: RmReportPresetExecutionTrigger[] = ['MANUAL', 'SCHEDULED'];
const RM_REPORT_PRESET_EXECUTION_STATUSES: RmReportPresetExecutionStatus[] = ['SUCCESS', 'FAILED'];

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isFiniteNumber = (value: unknown): value is number => (
  typeof value === 'number' && Number.isFinite(value)
);

const isStringOrNullish = (value: unknown): value is string | null | undefined => (
  value === null || value === undefined || typeof value === 'string'
);

const isBooleanOrNullish = (value: unknown): value is boolean | null | undefined => (
  value === null || value === undefined || typeof value === 'boolean'
);

const isRmReportPresetKind = (value: unknown): value is RmReportPresetKind => (
  typeof value === 'string' && (RM_REPORT_PRESET_KINDS as string[]).includes(value)
);

const isRmReportPresetExecutionTrigger = (value: unknown): value is RmReportPresetExecutionTrigger => (
  typeof value === 'string' && (RM_REPORT_PRESET_EXECUTION_TRIGGERS as string[]).includes(value)
);

const isRmReportPresetExecutionStatus = (value: unknown): value is RmReportPresetExecutionStatus => (
  typeof value === 'string' && (RM_REPORT_PRESET_EXECUTION_STATUSES as string[]).includes(value)
);

const assertUnexpectedResponse = (): never => {
  throw new Error(RECORDS_MANAGEMENT_UNEXPECTED_RESPONSE_MESSAGE);
};

const assertObjectResponse = <T>(value: unknown): T => (
  isObject(value) ? value as T : assertUnexpectedResponse()
);

const assertObjectArrayResponse = <T>(value: unknown): T[] => {
  if (!Array.isArray(value) || !value.every(isObject)) {
    return assertUnexpectedResponse();
  }
  return value as T[];
};

const assertPageResponse = <T>(value: unknown): PageResponse<T> => {
  if (
    !isObject(value)
    || !Array.isArray(value.content)
    || !value.content.every(isObject)
    || !isFiniteNumber(value.totalElements)
    || !isFiniteNumber(value.totalPages)
    || !isFiniteNumber(value.number)
    || !isFiniteNumber(value.size)
  ) {
    return assertUnexpectedResponse();
  }
  return value as unknown as PageResponse<T>;
};

// ---------------------------------------------------------------------------
// Bulk record declaration shape guards
// ---------------------------------------------------------------------------

const BULK_DECLARE_STATUSES: BulkDeclareStatus[] = ['DECLARED', 'SKIPPED_ALREADY_DECLARED', 'FAILED'];
const BULK_DECLARE_ERROR_CATEGORIES: BulkDeclareErrorCategory[] = [
  'NODE_NOT_FOUND',
  'NODE_NOT_VISIBLE',
  'INTERNAL_ERROR',
];

const isBulkDeclareStatus = (value: unknown): value is BulkDeclareStatus => (
  typeof value === 'string' && (BULK_DECLARE_STATUSES as string[]).includes(value)
);

const isBulkDeclareErrorCategory = (value: unknown): value is BulkDeclareErrorCategory => (
  typeof value === 'string' && (BULK_DECLARE_ERROR_CATEGORIES as string[]).includes(value)
);

const isNullish = (value: unknown): boolean => value === null || value === undefined;

const assertBulkDeclareUnexpectedResponse = (): never => {
  throw new Error(RECORDS_MANAGEMENT_BULK_DECLARE_UNEXPECTED_RESPONSE_MESSAGE);
};

const isRecordDeclarationShape = (value: unknown): boolean => (
  isObject(value)
  && typeof value.nodeId === 'string'
  && typeof value.name === 'string'
  && typeof value.path === 'string'
);

// Per-row shape guard. Enforces the §3 / Finding 3 row invariants — DECLARED and
// SKIPPED_ALREADY_DECLARED both carry a non-null declaration and a null errorCategory;
// FAILED carries a null declaration and a known errorCategory + non-empty errorMessage.
// Tolerates both wire shapes for null fields (explicit JSON null vs omitted), so a
// backend that serializes with WRITE_NULL or NON_NULL does not over-trigger the sentinel.
// See feedback_guard_predicates_real_backend_shape_drift.
const isBulkDeclareResult = (value: unknown): value is BulkDeclareResult => {
  if (!isObject(value)) {
    return false;
  }
  if (typeof value.nodeId !== 'string') {
    return false;
  }
  if (!isBulkDeclareStatus(value.status)) {
    return false;
  }
  if (value.status === 'DECLARED' || value.status === 'SKIPPED_ALREADY_DECLARED') {
    return (
      isRecordDeclarationShape(value.declaration)
      && isNullish(value.errorCategory)
      && isNullish(value.errorMessage)
    );
  }
  // status === 'FAILED'
  return (
    isNullish(value.declaration)
    && isBulkDeclareErrorCategory(value.errorCategory)
    && typeof value.errorMessage === 'string'
    && value.errorMessage.length > 0
  );
};

const assertBulkDeclareResponse = (value: unknown): BulkDeclareResponse => {
  if (!isObject(value) || !isObject(value.bulkDeclareResults)) {
    return assertBulkDeclareUnexpectedResponse();
  }
  const results = value.bulkDeclareResults as Record<string, unknown>;
  if (!Array.isArray(results.rows) || !results.rows.every(isBulkDeclareResult)) {
    return assertBulkDeclareUnexpectedResponse();
  }
  return value as unknown as BulkDeclareResponse;
};

const assertReportPreset = (value: unknown): RmReportPreset => {
  if (
    !isObject(value)
    || typeof value.id !== 'string'
    || typeof value.owner !== 'string'
    || typeof value.name !== 'string'
    || !isStringOrNullish(value.description)
    || !isRmReportPresetKind(value.kind)
    || !isObject(value.params)
    || !isBooleanOrNullish(value.scheduleEnabled)
    || !isStringOrNullish(value.deliveryFolderId)
    || !isStringOrNullish(value.nextRunAt)
    || !isStringOrNullish(value.lastRunAt)
    || !isStringOrNullish(value.createdDate)
    || !isStringOrNullish(value.lastModifiedDate)
  ) {
    return assertUnexpectedResponse();
  }
  return value as unknown as RmReportPreset;
};

const assertReportPresetArray = (value: unknown): RmReportPreset[] => {
  if (!Array.isArray(value)) {
    return assertUnexpectedResponse();
  }
  return value.map(assertReportPreset);
};

const assertReportPresetExecution = (value: unknown): RmReportPresetExecution => {
  if (
    !isObject(value)
    || typeof value.id !== 'string'
    || typeof value.presetId !== 'string'
    || !isStringOrNullish(value.presetName)
    || (value.presetKind !== undefined && value.presetKind !== null && !isRmReportPresetKind(value.presetKind))
    || !isRmReportPresetExecutionTrigger(value.triggerType)
    || !isRmReportPresetExecutionStatus(value.status)
    || !isStringOrNullish(value.filename)
    || !isStringOrNullish(value.targetFolderId)
    || !isStringOrNullish(value.documentId)
    || !isStringOrNullish(value.message)
    || typeof value.startedAt !== 'string'
    || typeof value.finishedAt !== 'string'
    || !isFiniteNumber(value.durationMs)
  ) {
    return assertUnexpectedResponse();
  }
  return value as unknown as RmReportPresetExecution;
};

const assertReportPresetExecutionArray = (value: unknown): RmReportPresetExecution[] => {
  if (!Array.isArray(value)) {
    return assertUnexpectedResponse();
  }
  return value.map(assertReportPresetExecution);
};

const assertReportPresetScheduleStatus = (value: unknown): RmReportPresetScheduleStatus => {
  if (
    !isObject(value)
    || typeof value.presetId !== 'string'
    || typeof value.enabled !== 'boolean'
    || !isStringOrNullish(value.cronExpression)
    || !isStringOrNullish(value.timezone)
    || !isStringOrNullish(value.deliveryFolderId)
    || !isStringOrNullish(value.nextRunAt)
    || !isStringOrNullish(value.lastRunAt)
    || (value.lastExecution !== undefined && value.lastExecution !== null && !isObject(value.lastExecution))
  ) {
    return assertUnexpectedResponse();
  }
  return {
    ...value,
    lastExecution: value.lastExecution ? assertReportPresetExecution(value.lastExecution) : value.lastExecution,
  } as unknown as RmReportPresetScheduleStatus;
};

const assertScheduledDeliveryTelemetry = (value: unknown): RmScheduledDeliveryTelemetry => {
  if (
    !isObject(value)
    || !isFiniteNumber(value.scheduleEnabledCount)
    || !isFiniteNumber(value.duePresetCount)
    || !isFiniteNumber(value.last24hSuccessCount)
    || !isFiniteNumber(value.last24hFailedCount)
    || !isStringOrNullish(value.lastExecutionAt)
    || typeof value.generatedAt !== 'string'
  ) {
    return assertUnexpectedResponse();
  }
  return value as unknown as RmScheduledDeliveryTelemetry;
};

const assertReportPresetExecutionLedgerResponse = (
  value: unknown
): ReportPresetExecutionLedgerApiResponse => {
  if (
    !isObject(value)
    || !Array.isArray(value.content)
    || !isFiniteNumber(value.page)
    || !isFiniteNumber(value.size)
    || !isFiniteNumber(value.totalElements)
    || !isFiniteNumber(value.totalPages)
    || typeof value.first !== 'boolean'
    || typeof value.last !== 'boolean'
  ) {
    return assertUnexpectedResponse();
  }
  return {
    ...value,
    content: value.content.map(assertReportPresetExecution),
  } as unknown as ReportPresetExecutionLedgerApiResponse;
};

export const supportsReportPresetCsvDelivery = (
  kind: RmReportPresetKind
): kind is RmDeliverableReportPresetKind => DELIVERABLE_REPORT_PRESET_KINDS.has(kind);

export interface RecordAuditFilters {
  family?: string;
  eventType?: string;
  username?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export interface ActivityContributorEventTypeReportExportFilters {
  from: string;
  to: string;
  limit?: number;
  eventTypeLimit?: number;
}

export interface ActivityContributorFamilyReportExportFilters {
  from: string;
  to: string;
  limit?: number;
}

export interface ActivityFamilyReportExportFilters {
  from: string;
  to: string;
}

export interface ActivityEventTypeReportExportFilters {
  from: string;
  to: string;
  limit?: number;
}

export interface ActivityContributorReportExportFilters {
  from: string;
  to: string;
  limit?: number;
}

export interface CreateReportPresetRequest {
  name: string;
  description?: string;
  kind: RmReportPresetKind;
  params: Record<string, unknown>;
}

export interface UpdateReportPresetRequest {
  name?: string;
  description?: string;
  params?: Record<string, unknown>;
}

export type UpdateReportPresetScheduleRequest =
  | {
      enabled: false;
      cronExpression?: string | null;
      timezone?: string | null;
      deliveryFolderId?: string | null;
    }
  | {
      enabled: true;
      cronExpression: string;
      timezone?: string | null;
      deliveryFolderId: string;
    };

interface ReportPresetExecutionLedgerApiResponse {
  content: RmReportPresetExecution[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface ReportPresetExecutionLedgerFilters {
  presetId?: string;
  status?: RmReportPresetExecutionStatus;
  triggerType?: RmReportPresetExecutionTrigger;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export interface ReportPresetExecutionLedgerExportFilters {
  presetId?: string;
  status?: RmReportPresetExecutionStatus;
  triggerType?: RmReportPresetExecutionTrigger;
  from?: string;
  to?: string;
  limit?: number;
}

class RecordsManagementService {
  async listRecords(): Promise<RecordDeclaration[]> {
    const response = await api.get<unknown>('/records');
    return assertObjectArrayResponse<RecordDeclaration>(response);
  }

  async getSummary(): Promise<RecordsSummary> {
    const response = await api.get<unknown>('/records/summary');
    return assertObjectResponse<RecordsSummary>(response);
  }

  async listReportPresets(): Promise<RmReportPreset[]> {
    const response = await api.get<unknown>('/records/report-presets');
    return assertReportPresetArray(response);
  }

  async createReportPreset(request: CreateReportPresetRequest): Promise<RmReportPreset> {
    const trimmedName = request.name.trim();
    const trimmedDescription = request.description?.trim();
    const response = await api.post<unknown>('/records/report-presets', {
      name: trimmedName,
      ...(trimmedDescription ? { description: trimmedDescription } : {}),
      kind: request.kind,
      params: request.params,
    });
    return assertReportPreset(response);
  }

  async updateReportPreset(id: string, request: UpdateReportPresetRequest): Promise<RmReportPreset> {
    const trimmedName = request.name?.trim();
    const trimmedDescription = request.description?.trim();
    const response = await api.put<unknown>(`/records/report-presets/${id}`, {
      ...(trimmedName !== undefined ? { name: trimmedName } : {}),
      ...(trimmedDescription !== undefined ? { description: trimmedDescription } : {}),
      ...(request.params !== undefined ? { params: request.params } : {}),
    });
    return assertReportPreset(response);
  }

  async deleteReportPreset(id: string): Promise<void> {
    return api.delete<void>(`/records/report-presets/${id}`);
  }

  async getReportPresetSchedule(id: string): Promise<RmReportPresetScheduleStatus> {
    const response = await api.get<unknown>(`/records/report-presets/${id}/schedule`);
    return assertReportPresetScheduleStatus(response);
  }

  async updateReportPresetSchedule(
    id: string,
    request: UpdateReportPresetScheduleRequest
  ): Promise<RmReportPresetScheduleStatus> {
    const response = await api.put<unknown>(`/records/report-presets/${id}/schedule`, {
      enabled: request.enabled,
      cronExpression: request.cronExpression ?? null,
      timezone: request.timezone ?? null,
      deliveryFolderId: request.deliveryFolderId ?? null,
    });
    return assertReportPresetScheduleStatus(response);
  }

  async deliverReportPresetNow(id: string): Promise<RmReportPresetExecution> {
    const response = await api.post<unknown>(`/records/report-presets/${id}/deliver`, {});
    return assertReportPresetExecution(response);
  }

  async listReportPresetExecutions(id: string, limit?: number): Promise<RmReportPresetExecution[]> {
    const response = await api.get<unknown>(`/records/report-presets/${id}/executions`, {
      params: limit != null ? { limit } : undefined,
    });
    return assertReportPresetExecutionArray(response);
  }

  async listReportPresetExecutionLedger(
    filters: ReportPresetExecutionLedgerFilters = {}
  ): Promise<PageResponse<RmReportPresetExecution>> {
    const params = {
      ...(filters.presetId?.trim() ? { presetId: filters.presetId.trim() } : {}),
      ...(filters.status ? { status: filters.status } : {}),
      ...(filters.triggerType ? { triggerType: filters.triggerType } : {}),
      ...(filters.from?.trim() ? { from: filters.from.trim() } : {}),
      ...(filters.to?.trim() ? { to: filters.to.trim() } : {}),
      page: filters.page ?? 0,
      size: filters.size ?? 10,
    };
    const result = assertReportPresetExecutionLedgerResponse(await api.get<unknown>(
      '/records/report-presets/executions',
      { params }
    ));
    return {
      content: result.content,
      totalElements: result.totalElements,
      totalPages: result.totalPages,
      number: result.page,
      size: result.size,
    };
  }

  async exportReportPresetExecutionLedgerCsv(
    filters: ReportPresetExecutionLedgerExportFilters = {}
  ): Promise<void> {
    const normalizedFrom = filters.from?.trim();
    const normalizedTo = filters.to?.trim();
    const filename = normalizedFrom && normalizedTo
      ? `rm-report-preset-executions-${normalizedFrom.slice(0, 10)}-to-${normalizedTo.slice(0, 10)}.csv`
      : 'rm-report-preset-executions.csv';
    return api.downloadFile('/records/report-presets/executions/export', filename, {
      params: {
        ...(filters.presetId?.trim() ? { presetId: filters.presetId.trim() } : {}),
        ...(filters.status ? { status: filters.status } : {}),
        ...(filters.triggerType ? { triggerType: filters.triggerType } : {}),
        ...(normalizedFrom ? { from: normalizedFrom } : {}),
        ...(normalizedTo ? { to: normalizedTo } : {}),
        limit: filters.limit ?? undefined,
      },
    });
  }

  async getScheduledDeliveryTelemetry(): Promise<RmScheduledDeliveryTelemetry> {
    const response = await api.get<unknown>('/records/report-presets/telemetry');
    return assertScheduledDeliveryTelemetry(response);
  }

  async getOperationsTelemetry(limit = 20): Promise<RecordsOperationsTelemetry> {
    const response = await api.get<unknown>('/records/operations', {
      params: { limit },
    });
    return assertObjectResponse<RecordsOperationsTelemetry>(response);
  }

  async getActivityTimeline(days = 14): Promise<RecordsActivityTimeline> {
    const response = await api.get<unknown>('/records/activity-timeline', {
      params: { days },
    });
    return assertObjectResponse<RecordsActivityTimeline>(response);
  }

  async getActivityHighlights(windowDays = 7): Promise<RecordsActivityHighlights> {
    const response = await api.get<unknown>('/records/activity-highlights', {
      params: { windowDays },
    });
    return assertObjectResponse<RecordsActivityHighlights>(response);
  }

  async getActivityBreakdown(days = 28, bucketDays = 7): Promise<RecordsActivityBreakdown> {
    const response = await api.get<unknown>('/records/activity-breakdown', {
      params: { days, bucketDays },
    });
    return assertObjectResponse<RecordsActivityBreakdown>(response);
  }

  async getActivityContributors(days = 28, limit = 5): Promise<RecordsActivityContributors> {
    const response = await api.get<unknown>('/records/activity-contributors', {
      params: { days, limit },
    });
    return assertObjectResponse<RecordsActivityContributors>(response);
  }

  async exportActivityContributorReportCsv(filters: ActivityContributorReportExportFilters): Promise<void> {
    const filename = `rm-activity-contributor-report-${filters.from.slice(0, 10)}-to-${filters.to.slice(0, 10)}.csv`;
    return api.downloadFile('/records/activity-contributor-report', filename, {
      params: {
        from: filters.from,
        to: filters.to,
        limit: filters.limit ?? undefined,
        format: 'csv',
      },
    });
  }

  async getActivityContributorEventTypeTrend(
    days = 28,
    bucketDays = 7,
    limit = 5,
    eventTypeLimit = 3
  ): Promise<RecordsActivityContributorEventTypeTrend> {
    const response = await api.get<unknown>('/records/activity-contributor-event-type-trend', {
      params: { days, bucketDays, limit, eventTypeLimit },
    });
    return assertObjectResponse<RecordsActivityContributorEventTypeTrend>(response);
  }

  async getActivityContributorFamilyTrend(
    days = 28,
    bucketDays = 7,
    limit = 5
  ): Promise<RecordsActivityContributorFamilyTrend> {
    const response = await api.get<unknown>('/records/activity-contributor-family-trend', {
      params: { days, bucketDays, limit },
    });
    return assertObjectResponse<RecordsActivityContributorFamilyTrend>(response);
  }

  async getActivityContributorFamilyHighlights(
    windowDays = 7,
    limit = 5
  ): Promise<RecordsActivityContributorFamilyHighlights> {
    const response = await api.get<unknown>('/records/activity-contributor-family-highlights', {
      params: { windowDays, limit },
    });
    return assertObjectResponse<RecordsActivityContributorFamilyHighlights>(response);
  }

  async exportActivityContributorFamilyReportCsv(filters: ActivityContributorFamilyReportExportFilters): Promise<void> {
    const filename = `rm-activity-contributor-family-report-${filters.from.slice(0, 10)}-to-${filters.to.slice(0, 10)}.csv`;
    return api.downloadFile('/records/activity-contributor-family-report', filename, {
      params: {
        from: filters.from,
        to: filters.to,
        limit: filters.limit ?? undefined,
        format: 'csv',
      },
    });
  }

  async getActivityContributorEventTypeHighlights(
    windowDays = 7,
    limit = 5,
    eventTypeLimit = 3
  ): Promise<RecordsActivityContributorEventTypeHighlights> {
    const response = await api.get<unknown>('/records/activity-contributor-event-type-highlights', {
      params: { windowDays, limit, eventTypeLimit },
    });
    return assertObjectResponse<RecordsActivityContributorEventTypeHighlights>(response);
  }

  async exportActivityContributorEventTypeReportCsv(filters: ActivityContributorEventTypeReportExportFilters): Promise<void> {
    const filename = `rm-activity-contributor-event-type-report-${filters.from.slice(0, 10)}-to-${filters.to.slice(0, 10)}.csv`;
    return api.downloadFile('/records/activity-contributor-event-type-report', filename, {
      params: {
        from: filters.from,
        to: filters.to,
        limit: filters.limit ?? undefined,
        eventTypeLimit: filters.eventTypeLimit ?? undefined,
        format: 'csv',
      },
    });
  }

  async getActivityEventTypes(days = 28, limit = 8): Promise<RecordsActivityEventTypes> {
    const response = await api.get<unknown>('/records/activity-event-types', {
      params: { days, limit },
    });
    return assertObjectResponse<RecordsActivityEventTypes>(response);
  }

  async exportActivityEventTypeReportCsv(filters: ActivityEventTypeReportExportFilters): Promise<void> {
    const filename = `rm-activity-event-type-report-${filters.from.slice(0, 10)}-to-${filters.to.slice(0, 10)}.csv`;
    return api.downloadFile('/records/activity-event-type-report', filename, {
      params: {
        from: filters.from,
        to: filters.to,
        limit: filters.limit ?? undefined,
        format: 'csv',
      },
    });
  }

  async getActivityFamilies(days = 28): Promise<RecordsActivityFamilies> {
    const response = await api.get<unknown>('/records/activity-families', {
      params: { days },
    });
    return assertObjectResponse<RecordsActivityFamilies>(response);
  }

  async getActivityFamilyHighlights(windowDays = 7): Promise<RecordsActivityFamilyHighlights> {
    const response = await api.get<unknown>('/records/activity-family-highlights', {
      params: { windowDays },
    });
    return assertObjectResponse<RecordsActivityFamilyHighlights>(response);
  }

  async exportActivityFamilyReportCsv(filters: ActivityFamilyReportExportFilters): Promise<void> {
    const filename = `rm-activity-family-report-${filters.from.slice(0, 10)}-to-${filters.to.slice(0, 10)}.csv`;
    return api.downloadFile('/records/activity-family-report', filename, {
      params: {
        from: filters.from,
        to: filters.to,
        format: 'csv',
      },
    });
  }

  async listAudit(filters: RecordAuditFilters = {}): Promise<PageResponse<RecordAuditEntry>> {
    const params = {
      ...(filters.family?.trim() ? { family: filters.family.trim() } : {}),
      ...(filters.eventType?.trim() ? { eventType: filters.eventType.trim() } : {}),
      ...(filters.username?.trim() ? { username: filters.username.trim() } : {}),
      ...(filters.from?.trim() ? { from: filters.from.trim() } : {}),
      ...(filters.to?.trim() ? { to: filters.to.trim() } : {}),
      page: filters.page ?? 0,
      size: filters.size ?? 10,
    };
    const response = await api.get<unknown>('/records/audit', { params });
    return assertPageResponse<RecordAuditEntry>(response);
  }

  async listFilePlans(): Promise<FilePlan[]> {
    const response = await api.get<unknown>('/records/file-plans');
    return assertObjectArrayResponse<FilePlan>(response);
  }

  async createFilePlan(request: CreateFilePlanRequest): Promise<FilePlan> {
    const response = await api.post<unknown>('/records/file-plans', {
      name: request.name.trim(),
      ...(request.description?.trim() ? { description: request.description.trim() } : {}),
      ...(request.parentId ? { parentId: request.parentId } : {}),
    });
    return assertObjectResponse<FilePlan>(response);
  }

  async updateFilePlan(folderId: string, request: UpdateFilePlanRequest): Promise<FilePlan> {
    const description = request.description?.trim();
    const response = await api.put<unknown>(`/records/file-plans/${folderId}`, {
      description: description || '',
    });
    return assertObjectResponse<FilePlan>(response);
  }

  async renameFilePlan(folderId: string, request: RenameFilePlanRequest): Promise<FilePlan> {
    const response = await api.put<unknown>(`/records/file-plans/${folderId}/rename`, {
      name: request.name.trim(),
    });
    return assertObjectResponse<FilePlan>(response);
  }

  async moveFilePlan(folderId: string, request: MoveFilePlanRequest): Promise<FilePlan> {
    const response = await api.put<unknown>(`/records/file-plans/${folderId}/move`, {
      targetParentId: request.targetParentId,
    });
    return assertObjectResponse<FilePlan>(response);
  }

  async deleteFilePlan(folderId: string): Promise<void> {
    return api.delete<void>(`/records/file-plans/${folderId}`);
  }

  async listRecordCategories(): Promise<RecordCategory[]> {
    const response = await api.get<unknown>('/records/categories');
    return assertObjectArrayResponse<RecordCategory>(response);
  }

  async createRecordCategory(request: CreateRecordCategoryRequest): Promise<RecordCategory> {
    const response = await api.post<unknown>('/records/categories', {
      name: request.name.trim(),
      ...(request.description?.trim() ? { description: request.description.trim() } : {}),
      ...(request.parentId ? { parentId: request.parentId } : {}),
    });
    return assertObjectResponse<RecordCategory>(response);
  }

  async updateRecordCategory(categoryId: string, request: UpdateRecordCategoryRequest): Promise<RecordCategory> {
    const description = request.description?.trim();
    const response = await api.put<unknown>(`/records/categories/${categoryId}`, {
      description: description || '',
    });
    return assertObjectResponse<RecordCategory>(response);
  }

  async renameRecordCategory(categoryId: string, request: RenameRecordCategoryRequest): Promise<RecordCategory> {
    const response = await api.put<unknown>(`/records/categories/${categoryId}/rename`, {
      name: request.name.trim(),
    });
    return assertObjectResponse<RecordCategory>(response);
  }

  async moveRecordCategory(categoryId: string, request: MoveRecordCategoryRequest): Promise<RecordCategory> {
    const response = await api.put<unknown>(`/records/categories/${categoryId}/move`, {
      targetParentId: request.targetParentId,
    });
    return assertObjectResponse<RecordCategory>(response);
  }

  async deleteRecordCategory(categoryId: string): Promise<void> {
    return api.delete<void>(`/records/categories/${categoryId}`);
  }

  async getRecord(nodeId: string): Promise<RecordDeclaration> {
    const response = await api.get<unknown>(`/nodes/${nodeId}/record`);
    return assertObjectResponse<RecordDeclaration>(response);
  }

  async declareRecord(nodeId: string, request?: DeclareRecordRequest): Promise<RecordDeclaration> {
    const comment = request?.comment?.trim();
    const response = await api.put<unknown>(`/nodes/${nodeId}/record`, {
      ...(comment ? { comment } : {}),
      ...(request?.categoryId ? { categoryId: request.categoryId } : {}),
    });
    return assertObjectResponse<RecordDeclaration>(response);
  }

  // Bulk-declare a set of nodes. Each row returns DECLARED, SKIPPED_ALREADY_DECLARED, or
  // FAILED with one of {NODE_NOT_FOUND, NODE_NOT_VISIBLE, INTERNAL_ERROR}. Per-row partial
  // failure is HTTP 200 with the row's errorCategory populated (not a top-level error).
  // See docs/BULK_RECORD_DECLARATION_ADJUDICATION_AND_DESIGN_20260524.md.
  async createBulkDeclarations(request: BulkDeclareRequest): Promise<BulkDeclareResponse> {
    const trimmedComment = request.comment?.trim();
    const payload: Record<string, unknown> = {
      nodeIds: request.nodeIds,
    };
    if (request.categoryId) {
      payload.categoryId = request.categoryId;
    }
    if (trimmedComment) {
      payload.comment = trimmedComment;
    }
    const response = await api.post<unknown>('/nodes/bulk-declare', payload);
    return assertBulkDeclareResponse(response);
  }

  async undeclareRecord(nodeId: string, request: UndeclareRecordRequest): Promise<void> {
    return api.post<void>(`/nodes/${nodeId}/record/undeclare`, {
      reason: request.reason.trim(),
    });
  }

  async assignRecordCategory(nodeId: string, categoryId: string): Promise<RecordDeclaration> {
    const response = await api.put<unknown>(`/nodes/${nodeId}/record/category`, { categoryId });
    return assertObjectResponse<RecordDeclaration>(response);
  }
}

const recordsManagementService = new RecordsManagementService();

export default recordsManagementService;
