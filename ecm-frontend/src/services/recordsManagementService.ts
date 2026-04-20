import api from './api';
import {
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
  RmReportPresetKind,
  RecordsSummary,
  UpdateFilePlanRequest,
  UpdateRecordCategoryRequest,
  UndeclareRecordRequest,
} from 'types';

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

class RecordsManagementService {
  async listRecords(): Promise<RecordDeclaration[]> {
    return api.get<RecordDeclaration[]>('/records');
  }

  async getSummary(): Promise<RecordsSummary> {
    return api.get<RecordsSummary>('/records/summary');
  }

  async listReportPresets(): Promise<RmReportPreset[]> {
    return api.get<RmReportPreset[]>('/records/report-presets');
  }

  async createReportPreset(request: CreateReportPresetRequest): Promise<RmReportPreset> {
    const trimmedName = request.name.trim();
    const trimmedDescription = request.description?.trim();
    return api.post<RmReportPreset>('/records/report-presets', {
      name: trimmedName,
      ...(trimmedDescription ? { description: trimmedDescription } : {}),
      kind: request.kind,
      params: request.params,
    });
  }

  async updateReportPreset(id: string, request: UpdateReportPresetRequest): Promise<RmReportPreset> {
    const trimmedName = request.name?.trim();
    const trimmedDescription = request.description?.trim();
    return api.put<RmReportPreset>(`/records/report-presets/${id}`, {
      ...(trimmedName !== undefined ? { name: trimmedName } : {}),
      ...(trimmedDescription !== undefined ? { description: trimmedDescription } : {}),
      ...(request.params !== undefined ? { params: request.params } : {}),
    });
  }

  async deleteReportPreset(id: string): Promise<void> {
    return api.delete<void>(`/records/report-presets/${id}`);
  }

  async getOperationsTelemetry(limit = 20): Promise<RecordsOperationsTelemetry> {
    return api.get<RecordsOperationsTelemetry>('/records/operations', {
      params: { limit },
    });
  }

  async getActivityTimeline(days = 14): Promise<RecordsActivityTimeline> {
    return api.get<RecordsActivityTimeline>('/records/activity-timeline', {
      params: { days },
    });
  }

  async getActivityHighlights(windowDays = 7): Promise<RecordsActivityHighlights> {
    return api.get<RecordsActivityHighlights>('/records/activity-highlights', {
      params: { windowDays },
    });
  }

  async getActivityBreakdown(days = 28, bucketDays = 7): Promise<RecordsActivityBreakdown> {
    return api.get<RecordsActivityBreakdown>('/records/activity-breakdown', {
      params: { days, bucketDays },
    });
  }

  async getActivityContributors(days = 28, limit = 5): Promise<RecordsActivityContributors> {
    return api.get<RecordsActivityContributors>('/records/activity-contributors', {
      params: { days, limit },
    });
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
    return api.get<RecordsActivityContributorEventTypeTrend>('/records/activity-contributor-event-type-trend', {
      params: { days, bucketDays, limit, eventTypeLimit },
    });
  }

  async getActivityContributorFamilyTrend(
    days = 28,
    bucketDays = 7,
    limit = 5
  ): Promise<RecordsActivityContributorFamilyTrend> {
    return api.get<RecordsActivityContributorFamilyTrend>('/records/activity-contributor-family-trend', {
      params: { days, bucketDays, limit },
    });
  }

  async getActivityContributorFamilyHighlights(
    windowDays = 7,
    limit = 5
  ): Promise<RecordsActivityContributorFamilyHighlights> {
    return api.get<RecordsActivityContributorFamilyHighlights>('/records/activity-contributor-family-highlights', {
      params: { windowDays, limit },
    });
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
    return api.get<RecordsActivityContributorEventTypeHighlights>('/records/activity-contributor-event-type-highlights', {
      params: { windowDays, limit, eventTypeLimit },
    });
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
    return api.get<RecordsActivityEventTypes>('/records/activity-event-types', {
      params: { days, limit },
    });
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
    return api.get<RecordsActivityFamilies>('/records/activity-families', {
      params: { days },
    });
  }

  async getActivityFamilyHighlights(windowDays = 7): Promise<RecordsActivityFamilyHighlights> {
    return api.get<RecordsActivityFamilyHighlights>('/records/activity-family-highlights', {
      params: { windowDays },
    });
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
    return api.get<PageResponse<RecordAuditEntry>>('/records/audit', { params });
  }

  async listFilePlans(): Promise<FilePlan[]> {
    return api.get<FilePlan[]>('/records/file-plans');
  }

  async createFilePlan(request: CreateFilePlanRequest): Promise<FilePlan> {
    return api.post<FilePlan>('/records/file-plans', {
      name: request.name.trim(),
      ...(request.description?.trim() ? { description: request.description.trim() } : {}),
      ...(request.parentId ? { parentId: request.parentId } : {}),
    });
  }

  async updateFilePlan(folderId: string, request: UpdateFilePlanRequest): Promise<FilePlan> {
    const description = request.description?.trim();
    return api.put<FilePlan>(`/records/file-plans/${folderId}`, {
      description: description || '',
    });
  }

  async renameFilePlan(folderId: string, request: RenameFilePlanRequest): Promise<FilePlan> {
    return api.put<FilePlan>(`/records/file-plans/${folderId}/rename`, {
      name: request.name.trim(),
    });
  }

  async moveFilePlan(folderId: string, request: MoveFilePlanRequest): Promise<FilePlan> {
    return api.put<FilePlan>(`/records/file-plans/${folderId}/move`, {
      targetParentId: request.targetParentId,
    });
  }

  async deleteFilePlan(folderId: string): Promise<void> {
    return api.delete<void>(`/records/file-plans/${folderId}`);
  }

  async listRecordCategories(): Promise<RecordCategory[]> {
    return api.get<RecordCategory[]>('/records/categories');
  }

  async createRecordCategory(request: CreateRecordCategoryRequest): Promise<RecordCategory> {
    return api.post<RecordCategory>('/records/categories', {
      name: request.name.trim(),
      ...(request.description?.trim() ? { description: request.description.trim() } : {}),
      ...(request.parentId ? { parentId: request.parentId } : {}),
    });
  }

  async updateRecordCategory(categoryId: string, request: UpdateRecordCategoryRequest): Promise<RecordCategory> {
    const description = request.description?.trim();
    return api.put<RecordCategory>(`/records/categories/${categoryId}`, {
      description: description || '',
    });
  }

  async renameRecordCategory(categoryId: string, request: RenameRecordCategoryRequest): Promise<RecordCategory> {
    return api.put<RecordCategory>(`/records/categories/${categoryId}/rename`, {
      name: request.name.trim(),
    });
  }

  async moveRecordCategory(categoryId: string, request: MoveRecordCategoryRequest): Promise<RecordCategory> {
    return api.put<RecordCategory>(`/records/categories/${categoryId}/move`, {
      targetParentId: request.targetParentId,
    });
  }

  async deleteRecordCategory(categoryId: string): Promise<void> {
    return api.delete<void>(`/records/categories/${categoryId}`);
  }

  async getRecord(nodeId: string): Promise<RecordDeclaration> {
    return api.get<RecordDeclaration>(`/nodes/${nodeId}/record`);
  }

  async declareRecord(nodeId: string, request?: DeclareRecordRequest): Promise<RecordDeclaration> {
    const comment = request?.comment?.trim();
    return api.put<RecordDeclaration>(`/nodes/${nodeId}/record`, {
      ...(comment ? { comment } : {}),
      ...(request?.categoryId ? { categoryId: request.categoryId } : {}),
    });
  }

  async undeclareRecord(nodeId: string, request: UndeclareRecordRequest): Promise<void> {
    return api.post<void>(`/nodes/${nodeId}/record/undeclare`, {
      reason: request.reason.trim(),
    });
  }

  async assignRecordCategory(nodeId: string, categoryId: string): Promise<RecordDeclaration> {
    return api.put<RecordDeclaration>(`/nodes/${nodeId}/record/category`, { categoryId });
  }
}

const recordsManagementService = new RecordsManagementService();

export default recordsManagementService;
