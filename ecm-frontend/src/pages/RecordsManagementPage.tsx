import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  FormControl,
  Grid,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import { Refresh } from '@mui/icons-material';
import { toast } from 'react-toastify';
import { useAppSelector } from 'store';
import recordsManagementService from 'services/recordsManagementService';
import {
  FilePlan,
  GovernedImportJob,
  GovernedTransferJob,
  PageResponse,
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
  RecordsActivityTimeline,
  RecordAuditEntry,
  RecordCategory,
  RecordDeclaration,
  RecordsOperationsTelemetry,
  RecordsSummary,
  RmReportPreset,
  RmReportPresetExecution,
  RmReportPresetExecutionStatus,
  RmReportPresetExecutionTrigger,
  RmReportPresetKind,
  RmScheduledDeliveryTelemetry,
} from 'types';
import MoveFilePlanDialog from 'components/records/MoveFilePlanDialog';
import MoveRecordCategoryDialog from 'components/records/MoveRecordCategoryDialog';
import RenameFilePlanDialog from 'components/records/RenameFilePlanDialog';
import RecordStatusChip from 'components/records/RecordStatusChip';
import RenameRecordCategoryDialog from 'components/records/RenameRecordCategoryDialog';
import SaveReportPresetDialog from 'components/records/SaveReportPresetDialog';
import ScheduleReportPresetDialog from 'components/records/ScheduleReportPresetDialog';
import { supportsReportPresetCsvDelivery } from 'services/recordsManagementService';
import UndeclareRecordDialog from 'components/records/UndeclareRecordDialog';

const AUDIT_DEFAULT_ROWS = 10;
const OPERATIONS_DEFAULT_LIMIT = 10;
const ACTIVITY_BREAKDOWN_DEFAULT_DAYS = 28;
const ACTIVITY_BREAKDOWN_DEFAULT_BUCKET_DAYS = 7;
const ACTIVITY_CONTRIBUTORS_DEFAULT_DAYS = 28;
const ACTIVITY_CONTRIBUTORS_DEFAULT_LIMIT = 5;
const ACTIVITY_CONTRIBUTOR_EVENT_TYPE_HIGHLIGHTS_DEFAULT_WINDOW_DAYS = 7;
const ACTIVITY_CONTRIBUTOR_EVENT_TYPE_HIGHLIGHTS_DEFAULT_EVENT_TYPE_LIMIT = 3;
const ACTIVITY_CONTRIBUTOR_EVENT_TYPE_TREND_DEFAULT_BUCKET_DAYS = 7;
const ACTIVITY_CONTRIBUTOR_EVENT_TYPE_TREND_DEFAULT_EVENT_TYPE_LIMIT = 3;
const ACTIVITY_FAMILIES_DEFAULT_DAYS = 28;
const ACTIVITY_FAMILY_HIGHLIGHTS_DEFAULT_WINDOW_DAYS = 7;
const ACTIVITY_EVENT_TYPES_DEFAULT_DAYS = 28;
const ACTIVITY_EVENT_TYPES_DEFAULT_LIMIT = 8;
const ACTIVITY_HIGHLIGHTS_DEFAULT_WINDOW_DAYS = 7;
const ACTIVITY_TIMELINE_DEFAULT_DAYS = 14;
const PRESET_EXECUTION_LEDGER_DEFAULT_ROWS = 10;

const emptyAuditPage = (size = AUDIT_DEFAULT_ROWS): PageResponse<RecordAuditEntry> => ({
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size,
});

const emptyPresetExecutionLedgerPage = (
  size = PRESET_EXECUTION_LEDGER_DEFAULT_ROWS
): PageResponse<RmReportPresetExecution> => ({
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size,
});

const formatDateTime = (value?: string | null) => {
  if (!value) {
    return '—';
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  return parsed.toLocaleString();
};

const formatRmAuditEventTypeLabel = (eventType: string) => eventType
  .replace(/^RM_/, '')
  .toLowerCase()
  .split('_')
  .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
  .join(' ');

const formatRmActivityFamilyLabel = (family?: string | null) => {
  switch (family) {
    case 'DECLARED':
      return 'Declared';
    case 'UNDECLARED':
      return 'Undeclared';
    case 'CATEGORY_ASSIGNED':
      return 'Category Assigned';
    case 'GOVERNANCE_CHANGE':
      return 'Governance Change';
    default:
      return 'Other';
  }
};

const formatShareOfTotal = (count: number, total: number) => {
  if (total <= 0) {
    return '0% of total';
  }
  const percentage = (count / total) * 100;
  const formatted = percentage >= 10 || Number.isInteger(percentage)
    ? percentage.toFixed(0)
    : percentage.toFixed(1);
  return `${formatted}% of total`;
};

const renderGovernanceReasonChips = (reasons: string[], selectedReason: string | null) => {
  if (!reasons.length) {
    return <Typography variant="body2" color="text.secondary">—</Typography>;
  }
  return (
    <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
      {reasons.map((reason) => (
        <Chip
          key={reason}
          size="small"
          label={reason}
          color={selectedReason === reason ? 'secondary' : 'default'}
          variant={selectedReason === reason ? 'filled' : 'outlined'}
        />
      ))}
    </Stack>
  );
};

const formatImportProgress = (job: GovernedImportJob) =>
  `${job.importedFiles}/${job.totalFiles} imported · ${job.skippedFiles} skipped · ${job.failedFiles} failed`;

const formatTransferStatus = (job: GovernedTransferJob) => {
  const workflow = job.status ?? 'UNKNOWN';
  const transport = job.transportStatus ?? 'UNKNOWN';
  return `${workflow} / ${transport}`;
};

const ACTIVE_OPERATION_STATES = new Set([
  'QUEUED',
  'PENDING',
  'RUNNING',
  'STARTED',
  'IN_PROGRESS',
  'CONNECTED',
]);

const FAILED_OPERATION_STATES = new Set([
  'FAILED',
  'FAILURE',
  'ERROR',
  'CANCELED',
  'CANCELLED',
  'ABORTED',
  'DISCONNECTED',
]);

const normalizePath = (value?: string | null) => {
  if (!value) {
    return null;
  }
  const trimmed = value.trim();
  return trimmed || null;
};

const normalizeOperationState = (value?: string | null) => {
  if (!value) {
    return null;
  }
  const trimmed = value.trim();
  return trimmed ? trimmed.toUpperCase() : null;
};

const normalizeOperationBucket = (value?: string | null) => normalizeOperationState(value);

const isPathInside = (path: string, containerPath: string) =>
  path === containerPath || path.startsWith(`${containerPath}/`);

const isActiveImportJob = (job: GovernedImportJob) => ACTIVE_OPERATION_STATES.has(normalizeOperationState(job.status) ?? '');

const isFailedImportJob = (job: GovernedImportJob) => FAILED_OPERATION_STATES.has(normalizeOperationState(job.status) ?? '');

const isActiveTransferJob = (job: GovernedTransferJob) =>
  [job.status, job.transportStatus].some((value) => ACTIVE_OPERATION_STATES.has(normalizeOperationState(value) ?? ''));

const isFailedTransferJob = (job: GovernedTransferJob) =>
  [job.status, job.transportStatus].some((value) => FAILED_OPERATION_STATES.has(normalizeOperationState(value) ?? ''));

const hasGovernanceReason = (reasons: string[], reason: string | null) => {
  if (!reason) {
    return true;
  }
  return reasons.includes(reason);
};

const matchesImportStatusBucket = (job: GovernedImportJob, bucket: string | null) => {
  if (!bucket) {
    return true;
  }
  return normalizeOperationBucket(job.status) === normalizeOperationBucket(bucket);
};

const matchesTransferStatusBucket = (job: GovernedTransferJob, bucket: string | null) => {
  if (!bucket) {
    return true;
  }
  return normalizeOperationBucket(formatTransferStatus(job)) === normalizeOperationBucket(bucket);
};

const formatFilterSummary = (statusFilter: 'all' | 'active' | 'failed', statusBucket: string | null, reason: string | null) => {
  const parts: string[] = [];
  if (statusFilter !== 'all') {
    parts.push(`${statusFilter} queue`);
  }
  if (statusBucket) {
    parts.push(statusBucket);
  }
  if (reason) {
    parts.push(reason);
  }
  return parts;
};

const formatScopedFilterSummaryLabel = (scope: 'Import' | 'Transfer', parts: string[]) => `${scope}: ${parts.join(' · ')}`;

const formatScopedFilterMatchLabel = (scope: 'Import' | 'Transfer', matched: number, total: number) =>
  `${scope} matches ${matched}/${total}`;

const formatSignedDelta = (current: number, previous: number) => {
  const delta = current - previous;
  if (delta === 0) {
    return 'No change vs previous window';
  }
  return `${delta > 0 ? '+' : ''}${delta} vs previous window`;
};

const formatWindowRangeLabel = (fromDay?: string | null, toDay?: string | null, activeDayCount?: number | null) => {
  if (!fromDay || !toDay) {
    return 'No window data available';
  }
  return `${fromDay} to ${toDay} | ${activeDayCount ?? 0} active day(s)`;
};

interface AuditFilterState {
  family: string;
  eventType: string;
  username: string;
  from: string;
  to: string;
}

interface AuditDrilldownState {
  label: string;
  from: string;
  to: string;
}

interface ReportPresetDraft {
  presetId?: string;
  title: string;
  helperText: string;
  name: string;
  description?: string;
  submitLabel?: string;
  kind: RmReportPresetKind;
  params: Record<string, unknown>;
}

type ReportPresetTableFilter = 'all' | 'scheduled' | 'dueNow';

interface PresetExecutionLedgerFilterState {
  presetId: string;
  status: '' | RmReportPresetExecutionStatus;
  triggerType: '' | RmReportPresetExecutionTrigger;
  from: string;
  to: string;
}

const emptyAuditFiltersState = (): AuditFilterState => ({
  family: '',
  eventType: '',
  username: '',
  from: '',
  to: '',
});

const emptyPresetExecutionLedgerFiltersState = (): PresetExecutionLedgerFilterState => ({
  presetId: '',
  status: '',
  triggerType: '',
  from: '',
  to: '',
});

const normalizePresetExecutionLedgerFilters = (filters: PresetExecutionLedgerFilterState) => ({
  ...(filters.presetId.trim() ? { presetId: filters.presetId.trim() } : {}),
  ...(filters.status ? { status: filters.status } : {}),
  ...(filters.triggerType ? { triggerType: filters.triggerType } : {}),
  ...(filters.from.trim() ? { from: filters.from.trim() } : {}),
  ...(filters.to.trim() ? { to: filters.to.trim() } : {}),
});

const AUDIT_FAMILY_OPTIONS = [
  { value: 'DECLARED', label: 'Declared' },
  { value: 'UNDECLARED', label: 'Undeclared' },
  { value: 'CATEGORY_ASSIGNED', label: 'Category Assigned' },
  { value: 'GOVERNANCE_CHANGE', label: 'Governance Change' },
  { value: 'OTHER', label: 'Other' },
] as const;

const formatLocalDateInputDay = (date: Date) => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

const buildAuditRangeBoundary = (day?: string | null, endOfDay = false) =>
  day ? `${day}T${endOfDay ? '23:59:59' : '00:00:00'}` : '';

const buildRollingWindowRange = (days: number, scope: 'current' | 'previous') => {
  const endDate = new Date();
  const currentStartDate = new Date(endDate);
  currentStartDate.setDate(currentStartDate.getDate() - (days - 1));

  const previousEndDate = new Date(currentStartDate);
  previousEndDate.setDate(previousEndDate.getDate() - 1);
  const previousStartDate = new Date(previousEndDate);
  previousStartDate.setDate(previousStartDate.getDate() - (days - 1));

  const fromDay = scope === 'current'
    ? formatLocalDateInputDay(currentStartDate)
    : formatLocalDateInputDay(previousStartDate);
  const toDay = scope === 'current'
    ? formatLocalDateInputDay(endDate)
    : formatLocalDateInputDay(previousEndDate);

  return {
    from: buildAuditRangeBoundary(fromDay, false),
    to: buildAuditRangeBoundary(toDay, true),
    fromDay,
    toDay,
  };
};

const buildNamedWindowRange = (window?: { fromDay?: string | null; toDay?: string | null } | null) => {
  if (!window?.fromDay || !window?.toDay) {
    return null;
  }
  return {
    from: buildAuditRangeBoundary(window.fromDay, false),
    to: buildAuditRangeBoundary(window.toDay, true),
    fromDay: window.fromDay,
    toDay: window.toDay,
  };
};

const buildPresetDisplayName = (reportLabel: string, scope: 'current' | 'previous') =>
  `${reportLabel} ${scope === 'current' ? 'Current Window' : 'Previous Window'}`;

const getPresetStringParam = (preset: RmReportPreset, key: string) => {
  const value = preset.params?.[key];
  return typeof value === 'string' ? value.trim() : '';
};

const getPresetNumberParam = (preset: RmReportPreset, key: string) => {
  const value = preset.params?.[key];
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : undefined;
  }
  return undefined;
};

const resolvePresetAuditRange = (preset: RmReportPreset) => {
  const from = getPresetStringParam(preset, 'from');
  const to = getPresetStringParam(preset, 'to');
  if (from && to) {
    return {
      from,
      to,
      fromDay: from.slice(0, 10),
      toDay: to.slice(0, 10),
    };
  }

  let rollingDays: number | undefined;
  switch (preset.kind) {
    case 'ACTIVITY_FAMILY_HIGHLIGHTS':
      rollingDays = getPresetNumberParam(preset, 'windowDays');
      break;
    case 'ACTIVITY_FAMILY_MIX':
      rollingDays = getPresetNumberParam(preset, 'days');
      break;
    default:
      rollingDays = undefined;
      break;
  }

  if (!rollingDays || rollingDays < 1) {
    return null;
  }

  return buildRollingWindowRange(rollingDays, 'current');
};

const formatPresetKindLabel = (kind: RmReportPresetKind) => {
  switch (kind) {
    case 'ACTIVITY_FAMILY_REPORT':
      return 'Activity Family Report';
    case 'ACTIVITY_FAMILY_HIGHLIGHTS':
      return 'Activity Family Highlights';
    case 'ACTIVITY_FAMILY_MIX':
      return 'Activity Family Mix';
    case 'ACTIVITY_EVENT_TYPE_REPORT':
      return 'Activity Event-Type Report';
    case 'ACTIVITY_CONTRIBUTOR_REPORT':
      return 'Activity Contributor Report';
    case 'ACTIVITY_CONTRIBUTOR_FAMILY_REPORT':
      return 'Contributor Family Report';
    case 'ACTIVITY_CONTRIBUTOR_EVENT_TYPE_REPORT':
      return 'Contributor Event-Type Report';
    default:
      return kind;
  }
};

const formatPresetRangeLabel = (preset: RmReportPreset) => {
  const range = resolvePresetAuditRange(preset);
  if (!range?.from || !range?.to) {
    return 'Window unavailable';
  }
  return `${range.from.slice(0, 10)} to ${range.to.slice(0, 10)}`;
};

const formatPresetExecutionTriggerLabel = (triggerType: RmReportPresetExecutionTrigger) =>
  triggerType === 'MANUAL' ? 'Manual' : 'Scheduled';

const formatPresetExecutionStatusLabel = (status: RmReportPresetExecutionStatus) =>
  status === 'SUCCESS' ? 'Successful' : 'Failed';

interface SnapshotSegment {
  label: string;
  value: number;
  color: string;
}

interface SnapshotDistributionCardProps {
  title: string;
  subtitle: string;
  totalLabel: string;
  segments: SnapshotSegment[];
  emptyLabel: string;
}

const SnapshotDistributionCard: React.FC<SnapshotDistributionCardProps> = ({
  title,
  subtitle,
  totalLabel,
  segments,
  emptyLabel,
}) => {
  const total = segments.reduce((sum, segment) => sum + segment.value, 0);

  return (
    <Stack spacing={1.5}>
      <Box>
        <Typography variant="subtitle2">{title}</Typography>
        <Typography variant="body2" color="text.secondary">
          {subtitle}
        </Typography>
      </Box>
      {total === 0 ? (
        <Typography variant="body2" color="text.secondary">
          {emptyLabel}
        </Typography>
      ) : (
        <>
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            {segments.map((segment) => (
              <Chip
                key={`${title}-${segment.label}`}
                size="small"
                label={`${segment.label} · ${segment.value}`}
                variant="outlined"
                sx={{
                  borderColor: segment.color,
                  color: segment.color,
                }}
              />
            ))}
          </Stack>
          <Box
            sx={{
              display: 'flex',
              width: '100%',
              height: 12,
              borderRadius: 999,
              overflow: 'hidden',
              backgroundColor: 'action.hover',
            }}
          >
            {segments.map((segment) => (
              <Box
                key={`${title}-${segment.label}-bar`}
                sx={{
                  width: `${(segment.value / total) * 100}%`,
                  backgroundColor: segment.color,
                  display: segment.value > 0 ? 'block' : 'none',
                }}
              />
            ))}
          </Box>
        </>
      )}
      <Typography variant="caption" color="text.secondary">
        {totalLabel}
      </Typography>
    </Stack>
  );
};

const ACTIVITY_TIMELINE_SERIES = [
  { key: 'declaredCount', label: 'Declared', color: '#2e7d32' },
  { key: 'undeclaredCount', label: 'Undeclared', color: '#ed6c02' },
  { key: 'categoryAssignedCount', label: 'Category Assigned', color: '#0288d1' },
  { key: 'governanceChangeCount', label: 'Governance Changes', color: '#7b1fa2' },
] as const;

const renderZeroMatchScopedAlert = (
  scopeLabel: 'import' | 'transfer',
  total: number,
  buttonLabel: string,
  onClick: () => void
) => (
  <Alert
    severity="warning"
    action={(
      <Button color="inherit" size="small" onClick={onClick}>
        {buttonLabel}
      </Button>
    )}
  >
    {`No recent governed ${scopeLabel} jobs match the current filter. Current ${scopeLabel} filters matched 0 of ${total} recent job(s).`}
  </Alert>
);

const RecordsManagementPage: React.FC = () => {
  const [summary, setSummary] = useState<RecordsSummary | null>(null);
  const [operations, setOperations] = useState<RecordsOperationsTelemetry | null>(null);
  const [activityBreakdown, setActivityBreakdown] = useState<RecordsActivityBreakdown | null>(null);
  const [activityContributorEventTypeHighlights, setActivityContributorEventTypeHighlights] = useState<RecordsActivityContributorEventTypeHighlights | null>(null);
  const [activityContributorFamilyHighlights, setActivityContributorFamilyHighlights] = useState<RecordsActivityContributorFamilyHighlights | null>(null);
  const [activityContributorFamilyTrend, setActivityContributorFamilyTrend] = useState<RecordsActivityContributorFamilyTrend | null>(null);
  const [activityContributors, setActivityContributors] = useState<RecordsActivityContributors | null>(null);
  const [activityContributorEventTypeTrend, setActivityContributorEventTypeTrend] = useState<RecordsActivityContributorEventTypeTrend | null>(null);
  const [activityEventTypes, setActivityEventTypes] = useState<RecordsActivityEventTypes | null>(null);
  const [activityFamilies, setActivityFamilies] = useState<RecordsActivityFamilies | null>(null);
  const [activityFamilyHighlights, setActivityFamilyHighlights] = useState<RecordsActivityFamilyHighlights | null>(null);
  const [activityHighlights, setActivityHighlights] = useState<RecordsActivityHighlights | null>(null);
  const [activityTimeline, setActivityTimeline] = useState<RecordsActivityTimeline | null>(null);
  const [records, setRecords] = useState<RecordDeclaration[]>([]);
  const [filePlans, setFilePlans] = useState<FilePlan[]>([]);
  const [categories, setCategories] = useState<RecordCategory[]>([]);
  const [auditPage, setAuditPage] = useState<PageResponse<RecordAuditEntry>>(emptyAuditPage());
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [operationsLoading, setOperationsLoading] = useState(false);
  const [breakdownLoading, setBreakdownLoading] = useState(false);
  const [contributorEventTypeHighlightsLoading, setContributorEventTypeHighlightsLoading] = useState(false);
  const [contributorFamilyHighlightsLoading, setContributorFamilyHighlightsLoading] = useState(false);
  const [contributorFamilyReportExportingScope, setContributorFamilyReportExportingScope] = useState<'current' | 'previous' | null>(null);
  const [activityFamilyReportExportingScope, setActivityFamilyReportExportingScope] = useState<'current' | 'previous' | null>(null);
  const [activityFamilyMixReportExportingScope, setActivityFamilyMixReportExportingScope] = useState<'current' | 'previous' | null>(null);
  const [activityEventTypeReportExportingScope, setActivityEventTypeReportExportingScope] = useState<'current' | 'previous' | null>(null);
  const [activityContributorReportExportingScope, setActivityContributorReportExportingScope] = useState<'current' | 'previous' | null>(null);
  const [contributorEventTypeReportExportingScope, setContributorEventTypeReportExportingScope] = useState<'current' | 'previous' | null>(null);
  const [contributorFamilyTrendLoading, setContributorFamilyTrendLoading] = useState(false);
  const [contributorsLoading, setContributorsLoading] = useState(false);
  const [contributorEventTypeTrendLoading, setContributorEventTypeTrendLoading] = useState(false);
  const [eventTypesLoading, setEventTypesLoading] = useState(false);
  const [familiesLoading, setFamiliesLoading] = useState(false);
  const [familyHighlightsLoading, setFamilyHighlightsLoading] = useState(false);
  const [highlightsLoading, setHighlightsLoading] = useState(false);
  const [timelineLoading, setTimelineLoading] = useState(false);
  const [auditLoading, setAuditLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [operationsError, setOperationsError] = useState<string | null>(null);
  const [breakdownError, setBreakdownError] = useState<string | null>(null);
  const [contributorEventTypeHighlightsError, setContributorEventTypeHighlightsError] = useState<string | null>(null);
  const [contributorFamilyHighlightsError, setContributorFamilyHighlightsError] = useState<string | null>(null);
  const [contributorFamilyTrendError, setContributorFamilyTrendError] = useState<string | null>(null);
  const [contributorsError, setContributorsError] = useState<string | null>(null);
  const [contributorEventTypeTrendError, setContributorEventTypeTrendError] = useState<string | null>(null);
  const [eventTypesError, setEventTypesError] = useState<string | null>(null);
  const [familiesError, setFamiliesError] = useState<string | null>(null);
  const [familyHighlightsError, setFamilyHighlightsError] = useState<string | null>(null);
  const [highlightsError, setHighlightsError] = useState<string | null>(null);
  const [timelineError, setTimelineError] = useState<string | null>(null);
  const [filePlanSubmitting, setFilePlanSubmitting] = useState(false);
  const [categorySubmitting, setCategorySubmitting] = useState(false);
  const [deletingFilePlanId, setDeletingFilePlanId] = useState<string | null>(null);
  const [deletingCategoryId, setDeletingCategoryId] = useState<string | null>(null);
  const [assigningRecordId, setAssigningRecordId] = useState<string | null>(null);
  const [undeclareDialogOpen, setUndeclareDialogOpen] = useState(false);
  const [undeclareTarget, setUndeclareTarget] = useState<RecordDeclaration | null>(null);
  const [renameFilePlanDialogOpen, setRenameFilePlanDialogOpen] = useState(false);
  const [renameFilePlanTarget, setRenameFilePlanTarget] = useState<FilePlan | null>(null);
  const [moveFilePlanDialogOpen, setMoveFilePlanDialogOpen] = useState(false);
  const [moveFilePlanTarget, setMoveFilePlanTarget] = useState<FilePlan | null>(null);
  const [renameCategoryDialogOpen, setRenameCategoryDialogOpen] = useState(false);
  const [renameCategoryTarget, setRenameCategoryTarget] = useState<RecordCategory | null>(null);
  const [moveCategoryDialogOpen, setMoveCategoryDialogOpen] = useState(false);
  const [moveCategoryTarget, setMoveCategoryTarget] = useState<RecordCategory | null>(null);
  const [reportPresetDraft, setReportPresetDraft] = useState<ReportPresetDraft | null>(null);
  const [reportPresetSubmitting, setReportPresetSubmitting] = useState(false);
  const [reportPresets, setReportPresets] = useState<RmReportPreset[]>([]);
  const [reportPresetsLoading, setReportPresetsLoading] = useState(false);
  const [reportPresetsError, setReportPresetsError] = useState<string | null>(null);
  const [reportPresetTableFilter, setReportPresetTableFilter] = useState<ReportPresetTableFilter>('all');
  const [presetExportingId, setPresetExportingId] = useState<string | null>(null);
  const [presetDeletingId, setPresetDeletingId] = useState<string | null>(null);
  const [schedulePresetTarget, setSchedulePresetTarget] = useState<RmReportPreset | null>(null);
  const [scheduledDeliveryTelemetry, setScheduledDeliveryTelemetry] =
    useState<RmScheduledDeliveryTelemetry | null>(null);
  const [scheduledDeliveryTelemetryLoading, setScheduledDeliveryTelemetryLoading] = useState(false);
  const [scheduledDeliveryTelemetryError, setScheduledDeliveryTelemetryError] = useState<string | null>(null);
  const [presetExecutionLedgerPage, setPresetExecutionLedgerPage] = useState<PageResponse<RmReportPresetExecution>>(
    emptyPresetExecutionLedgerPage()
  );
  const [presetExecutionLedgerLoading, setPresetExecutionLedgerLoading] = useState(false);
  const [presetExecutionLedgerError, setPresetExecutionLedgerError] = useState<string | null>(null);
  const [presetExecutionLedgerFilters, setPresetExecutionLedgerFilters] = useState<PresetExecutionLedgerFilterState>(
    emptyPresetExecutionLedgerFiltersState
  );
  const [appliedPresetExecutionLedgerFilters, setAppliedPresetExecutionLedgerFilters] =
    useState<PresetExecutionLedgerFilterState>(emptyPresetExecutionLedgerFiltersState);
  const [presetExecutionLedgerPageIndex, setPresetExecutionLedgerPageIndex] = useState(0);
  const [presetExecutionLedgerRowsPerPage, setPresetExecutionLedgerRowsPerPage] =
    useState(PRESET_EXECUTION_LEDGER_DEFAULT_ROWS);
  const [presetExecutionLedgerExporting, setPresetExecutionLedgerExporting] = useState(false);
  const [editingFilePlanId, setEditingFilePlanId] = useState<string | null>(null);
  const [editingCategoryId, setEditingCategoryId] = useState<string | null>(null);
  const [filePlanForm, setFilePlanForm] = useState({
    name: '',
    description: '',
    parentId: '',
  });
  const [categoryForm, setCategoryForm] = useState({
    name: '',
    description: '',
    parentId: '',
  });
  const [auditFilters, setAuditFilters] = useState<AuditFilterState>(emptyAuditFiltersState);
  const [appliedAuditFilters, setAppliedAuditFilters] = useState<AuditFilterState>(emptyAuditFiltersState);
  const [auditDrilldown, setAuditDrilldown] = useState<AuditDrilldownState | null>(null);
  const [auditPageIndex, setAuditPageIndex] = useState(0);
  const [auditRowsPerPage, setAuditRowsPerPage] = useState(AUDIT_DEFAULT_ROWS);
  const [recordCategoryDrafts, setRecordCategoryDrafts] = useState<Record<string, string>>({});
  const [declaredRecordsFilter, setDeclaredRecordsFilter] = useState<'all' | 'uncategorized' | 'categorized' | 'outsideFilePlan'>('all');
  const [importJobsFilter, setImportJobsFilter] = useState<'all' | 'active' | 'failed'>('all');
  const [transferJobsFilter, setTransferJobsFilter] = useState<'all' | 'active' | 'failed'>('all');
  const [selectedImportStatusBucket, setSelectedImportStatusBucket] = useState<string | null>(null);
  const [selectedTransferStatusBucket, setSelectedTransferStatusBucket] = useState<string | null>(null);
  const [selectedImportReason, setSelectedImportReason] = useState<string | null>(null);
  const [selectedTransferReason, setSelectedTransferReason] = useState<string | null>(null);
  const isAdmin = useAppSelector((state) => Boolean(state.auth.user?.roles?.includes('ROLE_ADMIN')));
  const declaredRecordsRef = useRef<HTMLDivElement | null>(null);
  const operationsRef = useRef<HTMLDivElement | null>(null);
  const auditRef = useRef<HTMLDivElement | null>(null);
  const reportPresetsRef = useRef<HTMLDivElement | null>(null);

  const loadAdminData = useCallback(async (silent = false) => {
    if (silent) {
      setRefreshing(true);
    } else {
      setLoading(true);
      setError(null);
    }

    try {
      const [summaryResult, recordsResult, filePlansResult, categoriesResult] = await Promise.all([
        recordsManagementService.getSummary(),
        recordsManagementService.listRecords(),
        recordsManagementService.listFilePlans(),
        recordsManagementService.listRecordCategories(),
      ]);

      setSummary(summaryResult);
      setRecords(recordsResult);
      setFilePlans(filePlansResult);
      setCategories(categoriesResult);
      setRecordCategoryDrafts((current) => {
        const next: Record<string, string> = {};
        recordsResult.forEach((record) => {
          next[record.nodeId] = current[record.nodeId] ?? record.recordCategoryId ?? '';
        });
        return next;
      });
      setError(null);
    } catch {
      const message = 'Failed to load records management data';
      setError(message);
      toast.error(message);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  const loadOperations = useCallback(async (silent = false) => {
    if (!silent) {
      setOperationsLoading(true);
    }
    try {
      const result = await recordsManagementService.getOperationsTelemetry(OPERATIONS_DEFAULT_LIMIT);
      setOperations(result);
      setOperationsError(null);
    } catch {
      setOperations(null);
      setOperationsError('Failed to load governed operations telemetry');
      if (!silent) {
        toast.error('Failed to load governed operations telemetry');
      }
    } finally {
      if (!silent) {
        setOperationsLoading(false);
      }
    }
  }, []);

  const loadScheduledDeliveryTelemetry = useCallback(async () => {
    setScheduledDeliveryTelemetryLoading(true);
    try {
      const result = await recordsManagementService.getScheduledDeliveryTelemetry();
      setScheduledDeliveryTelemetry(result);
      setScheduledDeliveryTelemetryError(null);
    } catch {
      setScheduledDeliveryTelemetry(null);
      setScheduledDeliveryTelemetryError('Failed to load scheduled delivery health');
    } finally {
      setScheduledDeliveryTelemetryLoading(false);
    }
  }, []);

  const loadReportPresets = useCallback(async (silent = false) => {
    if (!silent) {
      setReportPresetsLoading(true);
    }
    try {
      const result = await recordsManagementService.listReportPresets();
      setReportPresets(result);
      setReportPresetsError(null);
    } catch {
      setReportPresets([]);
      setReportPresetsError('Failed to load RM report presets');
      if (!silent) {
        toast.error('Failed to load RM report presets');
      }
    } finally {
      if (!silent) {
        setReportPresetsLoading(false);
      }
    }
  }, []);

  const loadPresetExecutionLedger = useCallback(async (
    filters = appliedPresetExecutionLedgerFilters,
    page = presetExecutionLedgerPageIndex,
    size = presetExecutionLedgerRowsPerPage,
    silent = false
  ) => {
    if (!silent) {
      setPresetExecutionLedgerLoading(true);
    }
    try {
      const result = await recordsManagementService.listReportPresetExecutionLedger({
        ...normalizePresetExecutionLedgerFilters(filters),
        page,
        size,
      });
      setPresetExecutionLedgerPage(result);
      setPresetExecutionLedgerError(null);
    } catch {
      setPresetExecutionLedgerError('Failed to load preset delivery ledger');
      if (!silent) {
        toast.error('Failed to load preset delivery ledger');
        setPresetExecutionLedgerPage(emptyPresetExecutionLedgerPage(size));
      }
    } finally {
      if (!silent) {
        setPresetExecutionLedgerLoading(false);
      }
    }
  }, [appliedPresetExecutionLedgerFilters, presetExecutionLedgerPageIndex, presetExecutionLedgerRowsPerPage]);

  const refreshPresetDeliverySurfaces = useCallback(async () => {
    await Promise.all([
      loadReportPresets(true),
      loadScheduledDeliveryTelemetry(),
      loadPresetExecutionLedger(undefined, undefined, undefined, true),
    ]);
  }, [loadPresetExecutionLedger, loadReportPresets, loadScheduledDeliveryTelemetry]);

  const loadBreakdown = useCallback(async (silent = false) => {
    if (!silent) {
      setBreakdownLoading(true);
    }
    try {
      const result = await recordsManagementService.getActivityBreakdown(
        ACTIVITY_BREAKDOWN_DEFAULT_DAYS,
        ACTIVITY_BREAKDOWN_DEFAULT_BUCKET_DAYS
      );
      setActivityBreakdown(result);
      setBreakdownError(null);
    } catch {
      setActivityBreakdown(null);
      setBreakdownError('Failed to load RM activity breakdown');
      if (!silent) {
        toast.error('Failed to load RM activity breakdown');
      }
    } finally {
      if (!silent) {
        setBreakdownLoading(false);
      }
    }
  }, []);

  const loadContributorEventTypeHighlights = useCallback(async (silent = false) => {
    if (!silent) {
      setContributorEventTypeHighlightsLoading(true);
    }
    try {
      const result = await recordsManagementService.getActivityContributorEventTypeHighlights(
        ACTIVITY_CONTRIBUTOR_EVENT_TYPE_HIGHLIGHTS_DEFAULT_WINDOW_DAYS,
        ACTIVITY_CONTRIBUTORS_DEFAULT_LIMIT,
        ACTIVITY_CONTRIBUTOR_EVENT_TYPE_HIGHLIGHTS_DEFAULT_EVENT_TYPE_LIMIT
      );
      setActivityContributorEventTypeHighlights(result);
      setContributorEventTypeHighlightsError(null);
    } catch {
      setActivityContributorEventTypeHighlights(null);
      setContributorEventTypeHighlightsError('Failed to load RM contributor event-type highlights');
      if (!silent) {
        toast.error('Failed to load RM contributor event-type highlights');
      }
    } finally {
      if (!silent) {
        setContributorEventTypeHighlightsLoading(false);
      }
    }
  }, []);

  const loadContributors = useCallback(async (silent = false) => {
    if (!silent) {
      setContributorsLoading(true);
    }
    try {
      const result = await recordsManagementService.getActivityContributors(
        ACTIVITY_CONTRIBUTORS_DEFAULT_DAYS,
        ACTIVITY_CONTRIBUTORS_DEFAULT_LIMIT
      );
      setActivityContributors(result);
      setContributorsError(null);
    } catch {
      setActivityContributors(null);
      setContributorsError('Failed to load RM activity contributors');
      if (!silent) {
        toast.error('Failed to load RM activity contributors');
      }
    } finally {
      if (!silent) {
        setContributorsLoading(false);
      }
    }
  }, []);

  const loadContributorFamilyTrend = useCallback(async (silent = false) => {
    if (!silent) {
      setContributorFamilyTrendLoading(true);
    }
    try {
      const result = await recordsManagementService.getActivityContributorFamilyTrend(
        ACTIVITY_CONTRIBUTORS_DEFAULT_DAYS,
        ACTIVITY_CONTRIBUTOR_EVENT_TYPE_TREND_DEFAULT_BUCKET_DAYS,
        ACTIVITY_CONTRIBUTORS_DEFAULT_LIMIT
      );
      setActivityContributorFamilyTrend(result);
      setContributorFamilyTrendError(null);
    } catch {
      setActivityContributorFamilyTrend(null);
      setContributorFamilyTrendError('Failed to load RM contributor family trend');
      if (!silent) {
        toast.error('Failed to load RM contributor family trend');
      }
    } finally {
      if (!silent) {
        setContributorFamilyTrendLoading(false);
      }
    }
  }, []);

  const loadContributorFamilyHighlights = useCallback(async (silent = false) => {
    if (!silent) {
      setContributorFamilyHighlightsLoading(true);
    }
    try {
      const result = await recordsManagementService.getActivityContributorFamilyHighlights(
        ACTIVITY_CONTRIBUTOR_EVENT_TYPE_HIGHLIGHTS_DEFAULT_WINDOW_DAYS,
        ACTIVITY_CONTRIBUTORS_DEFAULT_LIMIT
      );
      setActivityContributorFamilyHighlights(result);
      setContributorFamilyHighlightsError(null);
    } catch {
      setActivityContributorFamilyHighlights(null);
      setContributorFamilyHighlightsError('Failed to load RM contributor family highlights');
      if (!silent) {
        toast.error('Failed to load RM contributor family highlights');
      }
    } finally {
      if (!silent) {
        setContributorFamilyHighlightsLoading(false);
      }
    }
  }, []);

  const loadContributorEventTypeTrend = useCallback(async (silent = false) => {
    if (!silent) {
      setContributorEventTypeTrendLoading(true);
    }
    try {
      const result = await recordsManagementService.getActivityContributorEventTypeTrend(
        ACTIVITY_CONTRIBUTORS_DEFAULT_DAYS,
        ACTIVITY_CONTRIBUTOR_EVENT_TYPE_TREND_DEFAULT_BUCKET_DAYS,
        ACTIVITY_CONTRIBUTORS_DEFAULT_LIMIT,
        ACTIVITY_CONTRIBUTOR_EVENT_TYPE_TREND_DEFAULT_EVENT_TYPE_LIMIT
      );
      setActivityContributorEventTypeTrend(result);
      setContributorEventTypeTrendError(null);
    } catch {
      setActivityContributorEventTypeTrend(null);
      setContributorEventTypeTrendError('Failed to load RM contributor event-type trend');
      if (!silent) {
        toast.error('Failed to load RM contributor event-type trend');
      }
    } finally {
      if (!silent) {
        setContributorEventTypeTrendLoading(false);
      }
    }
  }, []);

  const loadFamilies = useCallback(async (silent = false) => {
    if (!silent) {
      setFamiliesLoading(true);
    }
    try {
      const result = await recordsManagementService.getActivityFamilies(ACTIVITY_FAMILIES_DEFAULT_DAYS);
      setActivityFamilies(result);
      setFamiliesError(null);
    } catch {
      setActivityFamilies(null);
      setFamiliesError('Failed to load RM activity family mix');
      if (!silent) {
        toast.error('Failed to load RM activity family mix');
      }
    } finally {
      if (!silent) {
        setFamiliesLoading(false);
      }
    }
  }, []);

  const loadFamilyHighlights = useCallback(async (silent = false) => {
    if (!silent) {
      setFamilyHighlightsLoading(true);
    }
    try {
      const result = await recordsManagementService.getActivityFamilyHighlights(ACTIVITY_FAMILY_HIGHLIGHTS_DEFAULT_WINDOW_DAYS);
      setActivityFamilyHighlights(result);
      setFamilyHighlightsError(null);
    } catch {
      setActivityFamilyHighlights(null);
      setFamilyHighlightsError('Failed to load RM activity family highlights');
      if (!silent) {
        toast.error('Failed to load RM activity family highlights');
      }
    } finally {
      if (!silent) {
        setFamilyHighlightsLoading(false);
      }
    }
  }, []);

  const loadEventTypes = useCallback(async (silent = false) => {
    if (!silent) {
      setEventTypesLoading(true);
    }
    try {
      const result = await recordsManagementService.getActivityEventTypes(
        ACTIVITY_EVENT_TYPES_DEFAULT_DAYS,
        ACTIVITY_EVENT_TYPES_DEFAULT_LIMIT
      );
      setActivityEventTypes(result);
      setEventTypesError(null);
    } catch {
      setActivityEventTypes(null);
      setEventTypesError('Failed to load RM activity event hotspots');
      if (!silent) {
        toast.error('Failed to load RM activity event hotspots');
      }
    } finally {
      if (!silent) {
        setEventTypesLoading(false);
      }
    }
  }, []);

  const loadHighlights = useCallback(async (silent = false) => {
    if (!silent) {
      setHighlightsLoading(true);
    }
    try {
      const result = await recordsManagementService.getActivityHighlights(ACTIVITY_HIGHLIGHTS_DEFAULT_WINDOW_DAYS);
      setActivityHighlights(result);
      setHighlightsError(null);
    } catch {
      setActivityHighlights(null);
      setHighlightsError('Failed to load RM activity highlights');
      if (!silent) {
        toast.error('Failed to load RM activity highlights');
      }
    } finally {
      if (!silent) {
        setHighlightsLoading(false);
      }
    }
  }, []);

  const loadTimeline = useCallback(async (silent = false) => {
    if (!silent) {
      setTimelineLoading(true);
    }
    try {
      const result = await recordsManagementService.getActivityTimeline(ACTIVITY_TIMELINE_DEFAULT_DAYS);
      setActivityTimeline(result);
      setTimelineError(null);
    } catch {
      setActivityTimeline(null);
      setTimelineError('Failed to load RM activity timeline');
      if (!silent) {
        toast.error('Failed to load RM activity timeline');
      }
    } finally {
      if (!silent) {
        setTimelineLoading(false);
      }
    }
  }, []);

  const loadAudit = useCallback(async (
    filters = appliedAuditFilters,
    page = auditPageIndex,
    size = auditRowsPerPage,
    silent = false
  ) => {
    if (!silent) {
      setAuditLoading(true);
    }
    try {
      const result = await recordsManagementService.listAudit({
        ...filters,
        page,
        size,
      });
      setAuditPage(result);
    } catch {
      toast.error('Failed to load records management audit');
      if (!silent) {
        setAuditPage(emptyAuditPage(size));
      }
    } finally {
      if (!silent) {
        setAuditLoading(false);
      }
    }
  }, [appliedAuditFilters, auditPageIndex, auditRowsPerPage]);

  useEffect(() => {
    void loadAdminData();
  }, [loadAdminData]);

  useEffect(() => {
    void loadOperations();
  }, [loadOperations]);

  useEffect(() => {
    void loadReportPresets();
  }, [loadReportPresets]);

  useEffect(() => {
    void loadScheduledDeliveryTelemetry();
  }, [loadScheduledDeliveryTelemetry]);

  useEffect(() => {
    void loadPresetExecutionLedger(
      appliedPresetExecutionLedgerFilters,
      presetExecutionLedgerPageIndex,
      presetExecutionLedgerRowsPerPage
    );
  }, [
    appliedPresetExecutionLedgerFilters,
    loadPresetExecutionLedger,
    presetExecutionLedgerPageIndex,
    presetExecutionLedgerRowsPerPage,
  ]);

  useEffect(() => {
    void loadBreakdown();
  }, [loadBreakdown]);

  useEffect(() => {
    void loadContributorEventTypeHighlights();
  }, [loadContributorEventTypeHighlights]);

  useEffect(() => {
    void loadContributors();
  }, [loadContributors]);

  useEffect(() => {
    void loadContributorFamilyTrend();
  }, [loadContributorFamilyTrend]);

  useEffect(() => {
    void loadContributorFamilyHighlights();
  }, [loadContributorFamilyHighlights]);

  useEffect(() => {
    void loadContributorEventTypeTrend();
  }, [loadContributorEventTypeTrend]);

  useEffect(() => {
    void loadFamilies();
  }, [loadFamilies]);

  useEffect(() => {
    void loadFamilyHighlights();
  }, [loadFamilyHighlights]);

  useEffect(() => {
    void loadEventTypes();
  }, [loadEventTypes]);

  useEffect(() => {
    void loadHighlights();
  }, [loadHighlights]);

  useEffect(() => {
    void loadTimeline();
  }, [loadTimeline]);

  useEffect(() => {
    void loadAudit(appliedAuditFilters, auditPageIndex, auditRowsPerPage);
  }, [appliedAuditFilters, auditPageIndex, auditRowsPerPage, loadAudit]);

  const openReportPresetDraft = useCallback((draft: ReportPresetDraft) => {
    setReportPresetDraft(draft);
  }, []);

  const closeReportPresetDialog = useCallback(() => {
    if (reportPresetSubmitting) {
      return;
    }
    setReportPresetDraft(null);
  }, [reportPresetSubmitting]);

  const saveReportPreset = useCallback(async ({ name, description }: { name: string; description?: string }) => {
    if (!reportPresetDraft) {
      return;
    }
    setReportPresetSubmitting(true);
    try {
      if (reportPresetDraft.presetId) {
        await recordsManagementService.updateReportPreset(reportPresetDraft.presetId, {
          name,
          description,
          params: reportPresetDraft.params,
        });
      } else {
        await recordsManagementService.createReportPreset({
          name,
          description,
          kind: reportPresetDraft.kind,
          params: reportPresetDraft.params,
        });
      }
      await loadReportPresets(true);
      toast.success(reportPresetDraft.presetId ? 'RM report preset updated' : 'RM report preset saved');
      setReportPresetDraft(null);
    } catch {
      toast.error(reportPresetDraft.presetId ? 'Failed to update RM report preset' : 'Failed to save RM report preset');
    } finally {
      setReportPresetSubmitting(false);
    }
  }, [loadReportPresets, reportPresetDraft]);

  const openEditReportPresetDraft = useCallback((preset: RmReportPreset) => {
    setReportPresetDraft({
      presetId: preset.id,
      title: 'Edit RM Report Preset',
      helperText: `Update the saved preset for ${formatPresetKindLabel(preset.kind)} without changing its report kind.`,
      name: preset.name,
      description: preset.description ?? undefined,
      submitLabel: 'Update Preset',
      kind: preset.kind,
      params: preset.params ?? {},
    });
  }, []);

  const deleteReportPreset = useCallback(async (preset: RmReportPreset) => {
    if (!window.confirm(`Delete RM report preset "${preset.name}"?`)) {
      return;
    }
    setPresetDeletingId(preset.id);
    try {
      await recordsManagementService.deleteReportPreset(preset.id);
      await loadReportPresets(true);
      toast.success('RM report preset deleted');
    } catch {
      toast.error('Failed to delete RM report preset');
    } finally {
      setPresetDeletingId((current) => (current === preset.id ? null : current));
    }
  }, [loadReportPresets]);

  const visibleCategoryOptions = useMemo(
    () => categories.filter((category) => category.path !== '/Records Management'),
    [categories]
  );

  const reportPresetById = useMemo(
    () => new Map(reportPresets.map((preset) => [preset.id, preset])),
    [reportPresets]
  );

  const isPresetDueNow = useCallback((preset: RmReportPreset) => {
    if (!preset.scheduleEnabled || !preset.nextRunAt) {
      return false;
    }
    const nextRun = new Date(preset.nextRunAt);
    if (Number.isNaN(nextRun.getTime())) {
      return false;
    }
    return nextRun.getTime() <= Date.now();
  }, []);

  const reportPresetFilterCounts = useMemo(() => ({
    all: reportPresets.length,
    scheduled: reportPresets.filter((preset) => preset.scheduleEnabled).length,
    dueNow: reportPresets.filter((preset) => isPresetDueNow(preset)).length,
  }), [isPresetDueNow, reportPresets]);

  const filteredReportPresets = useMemo(() => {
    switch (reportPresetTableFilter) {
      case 'scheduled':
        return reportPresets.filter((preset) => preset.scheduleEnabled);
      case 'dueNow':
        return reportPresets.filter((preset) => isPresetDueNow(preset));
      case 'all':
      default:
        return reportPresets;
    }
  }, [isPresetDueNow, reportPresetTableFilter, reportPresets]);

  const applyReportPresetTableFilter = useCallback((filter: ReportPresetTableFilter) => {
    setReportPresetTableFilter(filter);
    reportPresetsRef.current?.scrollIntoView?.({ behavior: 'smooth', block: 'start' });
  }, []);

  const openBrowseTarget = useCallback((nodeId?: string | null) => {
    if (!nodeId) {
      return;
    }
    window.open(`/browse/${nodeId}`, '_blank', 'noopener,noreferrer');
  }, []);

  const handleApplyPresetExecutionLedgerFilters = useCallback(() => {
    setAppliedPresetExecutionLedgerFilters({ ...presetExecutionLedgerFilters });
    setPresetExecutionLedgerPageIndex(0);
  }, [presetExecutionLedgerFilters]);

  const handleClearPresetExecutionLedgerFilters = useCallback(() => {
    const cleared = emptyPresetExecutionLedgerFiltersState();
    setPresetExecutionLedgerFilters(cleared);
    setAppliedPresetExecutionLedgerFilters(cleared);
    setPresetExecutionLedgerPageIndex(0);
  }, []);

  const exportPresetExecutionLedgerCsv = useCallback(async () => {
    setPresetExecutionLedgerExporting(true);
    try {
      await recordsManagementService.exportReportPresetExecutionLedgerCsv({
        ...normalizePresetExecutionLedgerFilters(appliedPresetExecutionLedgerFilters),
        limit: Math.max(
          presetExecutionLedgerPage.totalElements,
          presetExecutionLedgerPage.size || PRESET_EXECUTION_LEDGER_DEFAULT_ROWS
        ),
      });
      toast.success('RM preset delivery ledger CSV exported');
    } catch {
      toast.error('Failed to export preset delivery ledger');
    } finally {
      setPresetExecutionLedgerExporting(false);
    }
  }, [appliedPresetExecutionLedgerFilters, presetExecutionLedgerPage]);

  const hasAppliedPresetExecutionLedgerFilters = useMemo(
    () => Boolean(
      appliedPresetExecutionLedgerFilters.presetId
      || appliedPresetExecutionLedgerFilters.status
      || appliedPresetExecutionLedgerFilters.triggerType
      || appliedPresetExecutionLedgerFilters.from
      || appliedPresetExecutionLedgerFilters.to
    ),
    [appliedPresetExecutionLedgerFilters]
  );

  const presetExecutionLedgerFilterChips = useMemo(() => {
    const chips: string[] = [];
    if (appliedPresetExecutionLedgerFilters.presetId) {
      const preset = reportPresetById.get(appliedPresetExecutionLedgerFilters.presetId);
      chips.push(`Preset: ${preset?.name || appliedPresetExecutionLedgerFilters.presetId}`);
    }
    if (appliedPresetExecutionLedgerFilters.status) {
      chips.push(`Result: ${formatPresetExecutionStatusLabel(appliedPresetExecutionLedgerFilters.status)}`);
    }
    if (appliedPresetExecutionLedgerFilters.triggerType) {
      chips.push(`Trigger: ${formatPresetExecutionTriggerLabel(appliedPresetExecutionLedgerFilters.triggerType)}`);
    }
    if (appliedPresetExecutionLedgerFilters.from) {
      chips.push(`From: ${appliedPresetExecutionLedgerFilters.from}`);
    }
    if (appliedPresetExecutionLedgerFilters.to) {
      chips.push(`To: ${appliedPresetExecutionLedgerFilters.to}`);
    }
    return chips;
  }, [appliedPresetExecutionLedgerFilters, reportPresetById]);

  const isRootRecordCategory = useCallback(
    (category: RecordCategory) => category.path === '/Records Management',
    []
  );

  const governanceAlerts = useMemo(() => ([
    {
      label: 'Uncategorized Records',
      value: summary?.uncategorizedRecordCount ?? 0,
      description: 'Declared records that still need a record category.',
    },
    {
      label: 'Outside File Plan',
      value: summary?.outsideFilePlanRecordCount ?? 0,
      description: 'Declared records that are not governed by any file plan.',
    },
    {
      label: 'Failed Governed Imports',
      value: operations?.failedGovernedImportJobCount ?? 0,
      description: 'Governed import jobs that ended in FAILED or CANCELED state.',
    },
    {
      label: 'Failed Governed Transfers',
      value: operations?.failedGovernedTransferJobCount ?? 0,
      description: 'Governed transfer jobs with failed workflow or transport state.',
    },
  ]), [operations, summary]);

  const activeGovernanceAlerts = useMemo(
    () => governanceAlerts.filter((item) => item.value > 0),
    [governanceAlerts]
  );

  const filePlanCoverage = useMemo(() => {
    const normalizedFilePlans = [...filePlans]
      .map((filePlan) => ({
        ...filePlan,
        normalizedPath: normalizePath(filePlan.path),
      }))
      .filter((filePlan): filePlan is FilePlan & { normalizedPath: string } => Boolean(filePlan.normalizedPath))
      .sort((left, right) => right.normalizedPath.length - left.normalizedPath.length);

    const coverage = new Map<string, FilePlan | null>();
    records.forEach((record) => {
      const normalizedRecordPath = normalizePath(record.path);
      if (!normalizedRecordPath) {
        coverage.set(record.nodeId, null);
        return;
      }
      const matched = normalizedFilePlans.find((filePlan) => isPathInside(normalizedRecordPath, filePlan.normalizedPath)) ?? null;
      coverage.set(record.nodeId, matched);
    });
    return coverage;
  }, [filePlans, records]);

  const uncategorizedRecordCount = useMemo(
    () => records.filter((record) => !record.recordCategoryId).length,
    [records]
  );

  const categorizedRecordCount = useMemo(
    () => records.filter((record) => Boolean(record.recordCategoryId)).length,
    [records]
  );

  const outsideFilePlanRecordCount = useMemo(
    () => records.filter((record) => !filePlanCoverage.get(record.nodeId)).length,
    [filePlanCoverage, records]
  );

  const filteredRecords = useMemo(() => {
    if (declaredRecordsFilter === 'uncategorized') {
      return records.filter((record) => !record.recordCategoryId);
    }
    if (declaredRecordsFilter === 'categorized') {
      return records.filter((record) => Boolean(record.recordCategoryId));
    }
    if (declaredRecordsFilter === 'outsideFilePlan') {
      return records.filter((record) => !filePlanCoverage.get(record.nodeId));
    }
    return records;
  }, [declaredRecordsFilter, filePlanCoverage, records]);

  const reviewUncategorizedRecords = () => {
    setDeclaredRecordsFilter('uncategorized');
    if (typeof declaredRecordsRef.current?.scrollIntoView === 'function') {
      declaredRecordsRef.current.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  };

  const reviewOutsideFilePlanRecords = () => {
    setDeclaredRecordsFilter('outsideFilePlan');
    if (typeof declaredRecordsRef.current?.scrollIntoView === 'function') {
      declaredRecordsRef.current.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  };

  const scrollAuditIntoView = useCallback(() => {
    if (typeof auditRef.current?.scrollIntoView === 'function') {
      auditRef.current.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }, []);

  const applyAuditDrilldown = useCallback((label: string, from: string, to: string, overrides: Partial<AuditFilterState> = {}) => {
    const nextFilters: AuditFilterState = {
      family: overrides.family?.trim() ?? '',
      eventType: overrides.eventType?.trim() ?? '',
      username: overrides.username?.trim() ?? '',
      from,
      to,
    };
    setAuditDrilldown({ label, from, to });
    setAuditFilters(nextFilters);
    setAuditPageIndex(0);
    setAppliedAuditFilters(nextFilters);
    scrollAuditIntoView();
  }, [scrollAuditIntoView]);

  const reviewAuditRange = useCallback((label: string, fromDay?: string | null, toDay?: string | null) => {
    const from = buildAuditRangeBoundary(fromDay, false);
    const to = buildAuditRangeBoundary(toDay, true);
    if (!from || !to) {
      return;
    }
    applyAuditDrilldown(label, from, to);
  }, [applyAuditDrilldown]);

  const reviewAuditDay = useCallback((day?: string | null) => {
    if (!day) {
      return;
    }
    reviewAuditRange(`Activity on ${day}`, day, day);
  }, [reviewAuditRange]);

  const reviewTimelineWindowAudit = useCallback(() => {
    if (!activityTimeline?.points?.length) {
      return;
    }
    const firstDay = activityTimeline.points[0]?.day;
    const lastDay = activityTimeline.points[activityTimeline.points.length - 1]?.day;
    if (!firstDay || !lastDay) {
      return;
    }
    reviewAuditRange('Activity timeline window', firstDay, lastDay);
  }, [activityTimeline, reviewAuditRange]);

  const reviewBreakdownWindowAudit = useCallback(() => {
    if (!activityBreakdown?.buckets?.length) {
      return;
    }
    const firstBucket = activityBreakdown.buckets[0];
    const lastBucket = activityBreakdown.buckets[activityBreakdown.buckets.length - 1];
    if (!firstBucket?.fromDay || !lastBucket?.toDay) {
      return;
    }
    reviewAuditRange('Activity breakdown window', firstBucket.fromDay, lastBucket.toDay);
  }, [activityBreakdown, reviewAuditRange]);

  const reviewContributorAudit = useCallback((username: string | null | undefined, label: string) => {
    const effectiveDays = activityContributors?.days ?? ACTIVITY_CONTRIBUTORS_DEFAULT_DAYS;
    const endDate = new Date();
    const startDate = new Date(endDate);
    startDate.setDate(startDate.getDate() - (effectiveDays - 1));
    const from = buildAuditRangeBoundary(formatLocalDateInputDay(startDate), false);
    const to = buildAuditRangeBoundary(formatLocalDateInputDay(endDate), true);
    if (!from || !to) {
      return;
    }
    applyAuditDrilldown(`Contributor ${label}`, from, to, {
      username: username ?? '',
    });
  }, [activityContributors, applyAuditDrilldown]);

  const reviewContributorFamilyAudit = useCallback((
    username: string | null | undefined,
    label: string,
    family: (typeof AUDIT_FAMILY_OPTIONS)[number]['value']
  ) => {
    const effectiveDays = activityContributors?.days ?? ACTIVITY_CONTRIBUTORS_DEFAULT_DAYS;
    const endDate = new Date();
    const startDate = new Date(endDate);
    startDate.setDate(startDate.getDate() - (effectiveDays - 1));
    const from = buildAuditRangeBoundary(formatLocalDateInputDay(startDate), false);
    const to = buildAuditRangeBoundary(formatLocalDateInputDay(endDate), true);
    if (!from || !to) {
      return;
    }
    const familyLabel = AUDIT_FAMILY_OPTIONS.find((option) => option.value === family)?.label ?? family;
    applyAuditDrilldown(`Contributor ${label} · ${familyLabel}`, from, to, {
      username: username ?? '',
      family,
    });
  }, [activityContributors, applyAuditDrilldown]);

  const reviewContributorEventTypeTrendAudit = useCallback((
    username: string | null | undefined,
    label: string,
    eventType: string,
    fromDay?: string | null,
    toDay?: string | null
  ) => {
    const from = buildAuditRangeBoundary(fromDay, false);
    const to = buildAuditRangeBoundary(toDay, true);
    if (!from || !to) {
      return;
    }
    applyAuditDrilldown(`Contributor ${label} · ${formatRmAuditEventTypeLabel(eventType)}`, from, to, {
      username: username ?? '',
      eventType,
    });
  }, [applyAuditDrilldown]);

  const reviewContributorFamilyTrendAudit = useCallback((
    username: string | null | undefined,
    label: string,
    family: string,
    fromDay?: string | null,
    toDay?: string | null
  ) => {
    const from = buildAuditRangeBoundary(fromDay, false);
    const to = buildAuditRangeBoundary(toDay, true);
    if (!from || !to) {
      return;
    }
    applyAuditDrilldown(`Contributor ${label} · ${formatRmActivityFamilyLabel(family)}`, from, to, {
      username: username ?? '',
      family,
    });
  }, [applyAuditDrilldown]);

  const openWindowReportPreset = useCallback((
    kind: RmReportPresetKind,
    reportLabel: string,
    window: { fromDay?: string | null; toDay?: string | null } | null | undefined,
    scope: 'current' | 'previous',
    extraParams: Record<string, unknown> = {}
  ) => {
    const range = buildNamedWindowRange(window);
    if (!range?.from || !range?.to) {
      toast.error('Failed to prepare RM report preset');
      return;
    }
    openReportPresetDraft({
      title: 'Save RM Report Preset',
      helperText: `Save ${reportLabel} for the ${scope} window (${range.fromDay} to ${range.toDay}) as a reusable RM report preset.`,
      name: buildPresetDisplayName(reportLabel, scope),
      kind,
      params: {
        from: range.from,
        to: range.to,
        ...extraParams,
      },
    });
  }, [openReportPresetDraft]);

  const openRollingReportPreset = useCallback((
    kind: RmReportPresetKind,
    reportLabel: string,
    days: number,
    scope: 'current' | 'previous',
    extraParams: Record<string, unknown> = {}
  ) => {
    const range = buildRollingWindowRange(days, scope);
    if (!range.from || !range.to) {
      toast.error('Failed to prepare RM report preset');
      return;
    }
    openReportPresetDraft({
      title: 'Save RM Report Preset',
      helperText: `Save ${reportLabel} for the ${scope} window (${range.fromDay} to ${range.toDay}) as a reusable RM report preset.`,
      name: buildPresetDisplayName(reportLabel, scope),
      kind,
      params: {
        from: range.from,
        to: range.to,
        ...extraParams,
      },
    });
  }, [openReportPresetDraft]);

  const openContributorFamilyReportPreset = useCallback((scope: 'current' | 'previous') => {
    openWindowReportPreset(
      'ACTIVITY_CONTRIBUTOR_FAMILY_REPORT',
      'RM Contributor Family Report',
      scope === 'current'
        ? activityContributorFamilyHighlights?.currentWindow
        : activityContributorFamilyHighlights?.previousWindow,
      scope,
      {
        limit: activityContributorFamilyHighlights?.limit,
      }
    );
  }, [activityContributorFamilyHighlights, openWindowReportPreset]);

  const openContributorEventTypeReportPreset = useCallback((scope: 'current' | 'previous') => {
    openWindowReportPreset(
      'ACTIVITY_CONTRIBUTOR_EVENT_TYPE_REPORT',
      'RM Contributor Event-Type Report',
      scope === 'current'
        ? activityContributorEventTypeHighlights?.currentWindow
        : activityContributorEventTypeHighlights?.previousWindow,
      scope,
      {
        limit: activityContributorEventTypeHighlights?.limit,
        eventTypeLimit: activityContributorEventTypeHighlights?.eventTypeLimit,
      }
    );
  }, [activityContributorEventTypeHighlights, openWindowReportPreset]);

  const openActivityFamilyReportPreset = useCallback((scope: 'current' | 'previous') => {
    openWindowReportPreset(
      'ACTIVITY_FAMILY_REPORT',
      'RM Activity Family Report',
      scope === 'current'
        ? activityFamilyHighlights?.currentWindow
        : activityFamilyHighlights?.previousWindow,
      scope
    );
  }, [activityFamilyHighlights, openWindowReportPreset]);

  const openActivityFamilyMixReportPreset = useCallback((scope: 'current' | 'previous') => {
    openRollingReportPreset(
      'ACTIVITY_FAMILY_REPORT',
      'RM Activity Family Report',
      activityFamilies?.days ?? ACTIVITY_FAMILIES_DEFAULT_DAYS,
      scope
    );
  }, [activityFamilies, openRollingReportPreset]);

  const openActivityEventTypeReportPreset = useCallback((scope: 'current' | 'previous') => {
    openRollingReportPreset(
      'ACTIVITY_EVENT_TYPE_REPORT',
      'RM Activity Event-Type Report',
      activityEventTypes?.days ?? ACTIVITY_EVENT_TYPES_DEFAULT_DAYS,
      scope,
      {
        limit: activityEventTypes?.limit,
      }
    );
  }, [activityEventTypes, openRollingReportPreset]);

  const openActivityContributorReportPreset = useCallback((scope: 'current' | 'previous') => {
    openRollingReportPreset(
      'ACTIVITY_CONTRIBUTOR_REPORT',
      'RM Activity Contributor Report',
      activityContributors?.days ?? ACTIVITY_CONTRIBUTORS_DEFAULT_DAYS,
      scope,
      {
        limit: activityContributors?.limit,
      }
    );
  }, [activityContributors, openRollingReportPreset]);

  const applyReportPresetToAudit = useCallback((preset: RmReportPreset) => {
    const range = resolvePresetAuditRange(preset);
    if (!range?.from || !range?.to) {
      toast.error('Failed to apply RM report preset');
      return;
    }
    applyAuditDrilldown(`Preset ${preset.name}`, range.from, range.to, {
      family: getPresetStringParam(preset, 'family'),
      eventType: getPresetStringParam(preset, 'eventType'),
      username: getPresetStringParam(preset, 'username'),
    });
  }, [applyAuditDrilldown]);

  const exportReportPresetCsv = useCallback(async (preset: RmReportPreset) => {
    const range = resolvePresetAuditRange(preset);
    if (!range?.from || !range?.to) {
      toast.error('Failed to export RM report preset');
      return;
    }
    const { from, to } = range;

    setPresetExportingId(preset.id);
    try {
      switch (preset.kind) {
        case 'ACTIVITY_FAMILY_REPORT':
        case 'ACTIVITY_FAMILY_HIGHLIGHTS':
        case 'ACTIVITY_FAMILY_MIX':
          await recordsManagementService.exportActivityFamilyReportCsv({ from, to });
          break;
        case 'ACTIVITY_EVENT_TYPE_REPORT':
          await recordsManagementService.exportActivityEventTypeReportCsv({
            from,
            to,
            limit: getPresetNumberParam(preset, 'limit'),
          });
          break;
        case 'ACTIVITY_CONTRIBUTOR_REPORT':
          await recordsManagementService.exportActivityContributorReportCsv({
            from,
            to,
            limit: getPresetNumberParam(preset, 'limit'),
          });
          break;
        case 'ACTIVITY_CONTRIBUTOR_FAMILY_REPORT':
          await recordsManagementService.exportActivityContributorFamilyReportCsv({
            from,
            to,
            limit: getPresetNumberParam(preset, 'limit'),
          });
          break;
        case 'ACTIVITY_CONTRIBUTOR_EVENT_TYPE_REPORT':
          await recordsManagementService.exportActivityContributorEventTypeReportCsv({
            from,
            to,
            limit: getPresetNumberParam(preset, 'limit'),
            eventTypeLimit: getPresetNumberParam(preset, 'eventTypeLimit'),
          });
          break;
        default:
          throw new Error('Unsupported preset kind');
      }
      toast.success('RM report preset CSV exported');
    } catch {
      toast.error('Failed to export RM report preset');
    } finally {
      setPresetExportingId((current) => (current === preset.id ? null : current));
    }
  }, []);

  const reviewContributorFamilyHighlightAudit = useCallback((
    username: string | null | undefined,
    label: string,
    family: string,
    scope: 'current' | 'previous'
  ) => {
    const window = scope === 'current'
      ? activityContributorFamilyHighlights?.currentWindow
      : activityContributorFamilyHighlights?.previousWindow;
    if (!window?.fromDay || !window?.toDay) {
      return;
    }
    applyAuditDrilldown(
      `Contributor ${label} · ${formatRmActivityFamilyLabel(family)} · ${scope === 'current' ? 'Current window' : 'Previous window'}`,
      buildAuditRangeBoundary(window.fromDay, false),
      buildAuditRangeBoundary(window.toDay, true),
      {
        username: username ?? '',
        family,
      }
    );
  }, [activityContributorFamilyHighlights, applyAuditDrilldown]);

  const exportContributorFamilyReportCsv = useCallback(async (scope: 'current' | 'previous') => {
    const window = scope === 'current'
      ? activityContributorFamilyHighlights?.currentWindow
      : activityContributorFamilyHighlights?.previousWindow;
    if (!window?.fromDay || !window?.toDay) {
      toast.error('Failed to export contributor family report');
      return;
    }

    const from = buildAuditRangeBoundary(window.fromDay, false);
    const to = buildAuditRangeBoundary(window.toDay, true);
    if (!from || !to) {
      toast.error('Failed to export contributor family report');
      return;
    }

    setContributorFamilyReportExportingScope(scope);
    try {
      await recordsManagementService.exportActivityContributorFamilyReportCsv({
        from,
        to,
        limit: activityContributorFamilyHighlights?.limit,
      });
      toast.success(`Contributor family ${scope} window CSV exported`);
    } catch {
      toast.error('Failed to export contributor family report');
    } finally {
      setContributorFamilyReportExportingScope((current) => (current === scope ? null : current));
    }
  }, [activityContributorFamilyHighlights]);

  const reviewContributorEventTypeHighlightAudit = useCallback((
    username: string | null | undefined,
    label: string,
    eventType: string,
    scope: 'current' | 'previous'
  ) => {
    const window = scope === 'current'
      ? activityContributorEventTypeHighlights?.currentWindow
      : activityContributorEventTypeHighlights?.previousWindow;
    if (!window?.fromDay || !window?.toDay) {
      return;
    }
    applyAuditDrilldown(
      `Contributor ${label} · ${formatRmAuditEventTypeLabel(eventType)} · ${scope === 'current' ? 'Current window' : 'Previous window'}`,
      buildAuditRangeBoundary(window.fromDay, false),
      buildAuditRangeBoundary(window.toDay, true),
      {
        username: username ?? '',
        eventType,
      }
    );
  }, [activityContributorEventTypeHighlights, applyAuditDrilldown]);

  const exportContributorEventTypeReportCsv = useCallback(async (scope: 'current' | 'previous') => {
    const window = scope === 'current'
      ? activityContributorEventTypeHighlights?.currentWindow
      : activityContributorEventTypeHighlights?.previousWindow;
    if (!window?.fromDay || !window?.toDay) {
      toast.error('Failed to export contributor event-type report');
      return;
    }

    const from = buildAuditRangeBoundary(window.fromDay, false);
    const to = buildAuditRangeBoundary(window.toDay, true);
    if (!from || !to) {
      toast.error('Failed to export contributor event-type report');
      return;
    }

    setContributorEventTypeReportExportingScope(scope);
    try {
      await recordsManagementService.exportActivityContributorEventTypeReportCsv({
        from,
        to,
        limit: activityContributorEventTypeHighlights?.limit,
        eventTypeLimit: activityContributorEventTypeHighlights?.eventTypeLimit,
      });
      toast.success(`Contributor event-type ${scope} window CSV exported`);
    } catch {
      toast.error('Failed to export contributor event-type report');
    } finally {
      setContributorEventTypeReportExportingScope((current) => (current === scope ? null : current));
    }
  }, [activityContributorEventTypeHighlights]);

  const reviewFamilyAudit = useCallback((family: (typeof AUDIT_FAMILY_OPTIONS)[number]['value']) => {
    const effectiveDays = activityFamilies?.days ?? ACTIVITY_FAMILIES_DEFAULT_DAYS;
    const endDate = new Date();
    const startDate = new Date(endDate);
    startDate.setDate(startDate.getDate() - (effectiveDays - 1));
    const from = buildAuditRangeBoundary(formatLocalDateInputDay(startDate), false);
    const to = buildAuditRangeBoundary(formatLocalDateInputDay(endDate), true);
    if (!from || !to) {
      return;
    }
    const familyLabel = AUDIT_FAMILY_OPTIONS.find((option) => option.value === family)?.label ?? family;
    applyAuditDrilldown(`Activity family ${familyLabel}`, from, to, {
      family,
    });
  }, [activityFamilies, applyAuditDrilldown]);

  const reviewFamilyHighlightAudit = useCallback((family: (typeof AUDIT_FAMILY_OPTIONS)[number]['value'], scope: 'current' | 'previous') => {
    const window = scope === 'current'
      ? activityFamilyHighlights?.currentWindow
      : activityFamilyHighlights?.previousWindow;
    if (!window?.fromDay || !window?.toDay) {
      return;
    }
    const familyLabel = AUDIT_FAMILY_OPTIONS.find((option) => option.value === family)?.label ?? family;
    applyAuditDrilldown(
      `Family ${familyLabel} · ${scope === 'current' ? 'Current window' : 'Previous window'}`,
      buildAuditRangeBoundary(window.fromDay, false),
      buildAuditRangeBoundary(window.toDay, true),
      { family }
    );
  }, [activityFamilyHighlights, applyAuditDrilldown]);

  const exportActivityFamilyReportCsv = useCallback(async (scope: 'current' | 'previous') => {
    const window = scope === 'current'
      ? activityFamilyHighlights?.currentWindow
      : activityFamilyHighlights?.previousWindow;
    if (!window?.fromDay || !window?.toDay) {
      toast.error('Failed to export activity family report');
      return;
    }

    const from = buildAuditRangeBoundary(window.fromDay, false);
    const to = buildAuditRangeBoundary(window.toDay, true);
    if (!from || !to) {
      toast.error('Failed to export activity family report');
      return;
    }

    setActivityFamilyReportExportingScope(scope);
    try {
      await recordsManagementService.exportActivityFamilyReportCsv({
        from,
        to,
      });
      toast.success(`Activity family ${scope} window CSV exported`);
    } catch {
      toast.error('Failed to export activity family report');
    } finally {
      setActivityFamilyReportExportingScope((current) => (current === scope ? null : current));
    }
  }, [activityFamilyHighlights]);

  const exportActivityFamilyMixReportCsv = useCallback(async (scope: 'current' | 'previous') => {
    const effectiveDays = activityFamilies?.days ?? ACTIVITY_FAMILIES_DEFAULT_DAYS;
    const endDate = new Date();
    const currentStartDate = new Date(endDate);
    currentStartDate.setDate(currentStartDate.getDate() - (effectiveDays - 1));

    const previousEndDate = new Date(currentStartDate);
    previousEndDate.setDate(previousEndDate.getDate() - 1);
    const previousStartDate = new Date(previousEndDate);
    previousStartDate.setDate(previousStartDate.getDate() - (effectiveDays - 1));

    const fromDay = scope === 'current'
      ? formatLocalDateInputDay(currentStartDate)
      : formatLocalDateInputDay(previousStartDate);
    const toDay = scope === 'current'
      ? formatLocalDateInputDay(endDate)
      : formatLocalDateInputDay(previousEndDate);

    const from = buildAuditRangeBoundary(fromDay, false);
    const to = buildAuditRangeBoundary(toDay, true);
    if (!from || !to) {
      toast.error('Failed to export activity family report');
      return;
    }

    setActivityFamilyMixReportExportingScope(scope);
    try {
      await recordsManagementService.exportActivityFamilyReportCsv({
        from,
        to,
      });
      toast.success(`Activity family ${scope} window CSV exported`);
    } catch {
      toast.error('Failed to export activity family report');
    } finally {
      setActivityFamilyMixReportExportingScope((current) => (current === scope ? null : current));
    }
  }, [activityFamilies]);

  const reviewEventTypeAudit = useCallback((eventType: string) => {
    const effectiveDays = activityEventTypes?.days ?? ACTIVITY_EVENT_TYPES_DEFAULT_DAYS;
    const endDate = new Date();
    const startDate = new Date(endDate);
    startDate.setDate(startDate.getDate() - (effectiveDays - 1));
    const from = buildAuditRangeBoundary(formatLocalDateInputDay(startDate), false);
    const to = buildAuditRangeBoundary(formatLocalDateInputDay(endDate), true);
    if (!from || !to) {
      return;
    }
    applyAuditDrilldown(`Event ${formatRmAuditEventTypeLabel(eventType)}`, from, to, {
      eventType,
    });
  }, [activityEventTypes, applyAuditDrilldown]);

  const exportActivityEventTypeReportCsv = useCallback(async (scope: 'current' | 'previous') => {
    const effectiveDays = activityEventTypes?.days ?? ACTIVITY_EVENT_TYPES_DEFAULT_DAYS;
    const endDate = new Date();
    const currentStartDate = new Date(endDate);
    currentStartDate.setDate(currentStartDate.getDate() - (effectiveDays - 1));

    const previousEndDate = new Date(currentStartDate);
    previousEndDate.setDate(previousEndDate.getDate() - 1);
    const previousStartDate = new Date(previousEndDate);
    previousStartDate.setDate(previousStartDate.getDate() - (effectiveDays - 1));

    const fromDay = scope === 'current'
      ? formatLocalDateInputDay(currentStartDate)
      : formatLocalDateInputDay(previousStartDate);
    const toDay = scope === 'current'
      ? formatLocalDateInputDay(endDate)
      : formatLocalDateInputDay(previousEndDate);

    const from = buildAuditRangeBoundary(fromDay, false);
    const to = buildAuditRangeBoundary(toDay, true);
    if (!from || !to) {
      toast.error('Failed to export activity event-type report');
      return;
    }

    setActivityEventTypeReportExportingScope(scope);
    try {
      await recordsManagementService.exportActivityEventTypeReportCsv({
        from,
        to,
        limit: activityEventTypes?.limit,
      });
      toast.success(`Activity event-type ${scope} window CSV exported`);
    } catch {
      toast.error('Failed to export activity event-type report');
    } finally {
      setActivityEventTypeReportExportingScope((current) => (current === scope ? null : current));
    }
  }, [activityEventTypes]);

  const exportActivityContributorReportCsv = useCallback(async (scope: 'current' | 'previous') => {
    const effectiveDays = activityContributors?.days ?? ACTIVITY_CONTRIBUTORS_DEFAULT_DAYS;
    const endDate = new Date();
    const currentStartDate = new Date(endDate);
    currentStartDate.setDate(currentStartDate.getDate() - (effectiveDays - 1));

    const previousEndDate = new Date(currentStartDate);
    previousEndDate.setDate(previousEndDate.getDate() - 1);
    const previousStartDate = new Date(previousEndDate);
    previousStartDate.setDate(previousStartDate.getDate() - (effectiveDays - 1));

    const fromDay = scope === 'current'
      ? formatLocalDateInputDay(currentStartDate)
      : formatLocalDateInputDay(previousStartDate);
    const toDay = scope === 'current'
      ? formatLocalDateInputDay(endDate)
      : formatLocalDateInputDay(previousEndDate);

    const from = buildAuditRangeBoundary(fromDay, false);
    const to = buildAuditRangeBoundary(toDay, true);
    if (!from || !to) {
      toast.error('Failed to export activity contributor report');
      return;
    }

    setActivityContributorReportExportingScope(scope);
    try {
      await recordsManagementService.exportActivityContributorReportCsv({
        from,
        to,
        limit: activityContributors?.limit,
      });
      toast.success(`Activity contributor ${scope} window CSV exported`);
    } catch {
      toast.error('Failed to export activity contributor report');
    } finally {
      setActivityContributorReportExportingScope((current) => (current === scope ? null : current));
    }
  }, [activityContributors]);

  const clearAuditDrilldown = useCallback(() => {
    setAuditDrilldown(null);
    setAuditFilters((current) => ({ ...current, family: '', eventType: '', username: '', from: '', to: '' }));
    setAuditPageIndex(0);
    setAppliedAuditFilters((current) => ({ ...current, family: '', eventType: '', username: '', from: '', to: '' }));
    scrollAuditIntoView();
  }, [scrollAuditIntoView]);

  const filteredImportJobs = useMemo(() => {
    const jobs = operations?.recentImportJobs ?? [];
    return jobs.filter((job) => {
      const matchesStatus = importJobsFilter === 'active'
        ? isActiveImportJob(job)
        : importJobsFilter === 'failed'
          ? isFailedImportJob(job)
          : true;
      return matchesStatus
        && matchesImportStatusBucket(job, selectedImportStatusBucket)
        && hasGovernanceReason(job.governanceReasons, selectedImportReason);
    });
  }, [importJobsFilter, operations, selectedImportReason, selectedImportStatusBucket]);

  const filteredTransferJobs = useMemo(() => {
    const jobs = operations?.recentTransferJobs ?? [];
    return jobs.filter((job) => {
      const matchesStatus = transferJobsFilter === 'active'
        ? isActiveTransferJob(job)
        : transferJobsFilter === 'failed'
          ? isFailedTransferJob(job)
          : true;
      return matchesStatus
        && matchesTransferStatusBucket(job, selectedTransferStatusBucket)
        && hasGovernanceReason(job.governanceReasons, selectedTransferReason);
    });
  }, [operations, selectedTransferReason, selectedTransferStatusBucket, transferJobsFilter]);

  const recentActiveImportJobCount = useMemo(
    () => (operations?.recentImportJobs ?? []).filter(isActiveImportJob).length,
    [operations]
  );

  const recentFailedImportJobCount = useMemo(
    () => (operations?.recentImportJobs ?? []).filter(isFailedImportJob).length,
    [operations]
  );

  const recentActiveTransferJobCount = useMemo(
    () => (operations?.recentTransferJobs ?? []).filter(isActiveTransferJob).length,
    [operations]
  );

  const recentFailedTransferJobCount = useMemo(
    () => (operations?.recentTransferJobs ?? []).filter(isFailedTransferJob).length,
    [operations]
  );

  const reviewFailedImportJobs = () => {
    setSelectedImportStatusBucket(null);
    setImportJobsFilter('failed');
    if (typeof operationsRef.current?.scrollIntoView === 'function') {
      operationsRef.current.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  };

  const reviewFailedTransferJobs = () => {
    setSelectedTransferStatusBucket(null);
    setTransferJobsFilter('failed');
    if (typeof operationsRef.current?.scrollIntoView === 'function') {
      operationsRef.current.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  };

  const reviewImportStatusBucket = (bucket: string) => {
    setImportJobsFilter('all');
    setSelectedImportStatusBucket(bucket);
    if (typeof operationsRef.current?.scrollIntoView === 'function') {
      operationsRef.current.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  };

  const reviewTransferStatusBucket = (bucket: string) => {
    setTransferJobsFilter('all');
    setSelectedTransferStatusBucket(bucket);
    if (typeof operationsRef.current?.scrollIntoView === 'function') {
      operationsRef.current.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  };

  const reviewImportGovernanceReason = (reason: string) => {
    setSelectedImportReason(reason);
    if (typeof operationsRef.current?.scrollIntoView === 'function') {
      operationsRef.current.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  };

  const reviewTransferGovernanceReason = (reason: string) => {
    setSelectedTransferReason(reason);
    if (typeof operationsRef.current?.scrollIntoView === 'function') {
      operationsRef.current.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  };

  const clearImportOperationFilters = () => {
    setImportJobsFilter('all');
    setSelectedImportStatusBucket(null);
    setSelectedImportReason(null);
  };

  const clearTransferOperationFilters = () => {
    setTransferJobsFilter('all');
    setSelectedTransferStatusBucket(null);
    setSelectedTransferReason(null);
  };

  const importFilterSummary = useMemo(
    () => formatFilterSummary(importJobsFilter, selectedImportStatusBucket, selectedImportReason),
    [importJobsFilter, selectedImportReason, selectedImportStatusBucket]
  );

  const transferFilterSummary = useMemo(
    () => formatFilterSummary(transferJobsFilter, selectedTransferStatusBucket, selectedTransferReason),
    [selectedTransferReason, selectedTransferStatusBucket, transferJobsFilter]
  );

  const hasOperationFilterSummary = importFilterSummary.length > 0 || transferFilterSummary.length > 0;
  const importTotalJobs = operations?.recentImportJobs?.length ?? 0;
  const transferTotalJobs = operations?.recentTransferJobs?.length ?? 0;
  const importQueueOtherCount = Math.max(
    0,
    (operations?.governedImportJobCount ?? 0)
      - (operations?.activeGovernedImportJobCount ?? 0)
      - (operations?.failedGovernedImportJobCount ?? 0)
  );
  const transferQueueOtherCount = Math.max(
    0,
    (operations?.governedTransferJobCount ?? 0)
      - (operations?.activeGovernedTransferJobCount ?? 0)
      - (operations?.failedGovernedTransferJobCount ?? 0)
  );

  const declaredRecordSnapshot = useMemo(() => ([
    {
      title: 'Category Coverage',
      subtitle: 'Declared records with and without a record category.',
      totalLabel: `${records.length} declared record(s) in current RM view`,
      emptyLabel: 'No declared records are available for coverage analysis yet.',
      segments: [
        { label: 'Categorized records', value: categorizedRecordCount, color: '#2e7d32' },
        { label: 'Uncategorized records', value: uncategorizedRecordCount, color: '#ed6c02' },
      ],
    },
    {
      title: 'File Plan Coverage',
      subtitle: 'Declared records inside versus outside visible file-plan scope.',
      totalLabel: `${records.length} declared record(s) checked for file-plan coverage`,
      emptyLabel: 'No declared records are available for file-plan coverage analysis yet.',
      segments: [
        { label: 'Inside file plan', value: Math.max(0, records.length - outsideFilePlanRecordCount), color: '#0288d1' },
        { label: 'Outside file plan', value: outsideFilePlanRecordCount, color: '#d32f2f' },
      ],
    },
  ]), [categorizedRecordCount, outsideFilePlanRecordCount, records.length, uncategorizedRecordCount]);

  const governedOperationsSnapshot = useMemo(() => ([
    {
      title: 'Import Queue Health',
      subtitle: 'Governed import workload by current queue outcome.',
      totalLabel: `${operations?.governedImportJobCount ?? 0} governed import job(s) in telemetry`,
      emptyLabel: 'No governed import jobs are available for queue-health analysis yet.',
      segments: [
        { label: 'Active imports', value: operations?.activeGovernedImportJobCount ?? 0, color: '#0288d1' },
        { label: 'Failed imports', value: operations?.failedGovernedImportJobCount ?? 0, color: '#d32f2f' },
        { label: 'Other imports', value: importQueueOtherCount, color: '#9e9e9e' },
      ],
    },
    {
      title: 'Transfer Queue Health',
      subtitle: 'Governed transfer workload by current queue outcome.',
      totalLabel: `${operations?.governedTransferJobCount ?? 0} governed transfer job(s) in telemetry`,
      emptyLabel: 'No governed transfer jobs are available for queue-health analysis yet.',
      segments: [
        { label: 'Active transfers', value: operations?.activeGovernedTransferJobCount ?? 0, color: '#7b1fa2' },
        { label: 'Failed transfers', value: operations?.failedGovernedTransferJobCount ?? 0, color: '#d32f2f' },
        { label: 'Other transfers', value: transferQueueOtherCount, color: '#9e9e9e' },
      ],
    },
  ]), [
    importQueueOtherCount,
    operations?.activeGovernedImportJobCount,
    operations?.activeGovernedTransferJobCount,
    operations?.failedGovernedImportJobCount,
    operations?.failedGovernedTransferJobCount,
    operations?.governedImportJobCount,
    operations?.governedTransferJobCount,
    transferQueueOtherCount,
  ]);

  const timelineMaxTotal = useMemo(
    () => Math.max(1, ...(activityTimeline?.points ?? []).map((point) => point.totalCount)),
    [activityTimeline]
  );

  const breakdownMaxTotal = useMemo(
    () => Math.max(1, ...(activityBreakdown?.buckets ?? []).map((bucket) => bucket.totalCount)),
    [activityBreakdown]
  );

  const activityHighlightMetrics = useMemo(() => {
    if (!activityHighlights) {
      return [];
    }
    const currentWindow = activityHighlights.currentWindow;
    const previousWindow = activityHighlights.previousWindow;
    return [
      {
        label: 'Total RM Events',
        value: currentWindow.totalCount,
        caption: `${formatSignedDelta(currentWindow.totalCount, previousWindow.totalCount)} | ${currentWindow.activeDayCount} active day(s)`,
      },
      {
        label: 'Declarations',
        value: currentWindow.declaredCount,
        caption: formatSignedDelta(currentWindow.declaredCount, previousWindow.declaredCount),
      },
      {
        label: 'Undeclarations',
        value: currentWindow.undeclaredCount,
        caption: formatSignedDelta(currentWindow.undeclaredCount, previousWindow.undeclaredCount),
      },
      {
        label: 'Category Assignments',
        value: currentWindow.categoryAssignedCount,
        caption: formatSignedDelta(currentWindow.categoryAssignedCount, previousWindow.categoryAssignedCount),
      },
      {
        label: 'Governance Changes',
        value: currentWindow.governanceChangeCount,
        caption: formatSignedDelta(currentWindow.governanceChangeCount, previousWindow.governanceChangeCount),
      },
      {
        label: 'Busiest Day',
        value: activityHighlights.busiestDay?.totalCount ?? 0,
        caption: activityHighlights.busiestDay
          ? `${activityHighlights.busiestDay.day} | ${activityHighlights.busiestDay.totalCount} event(s)`
          : 'No RM activity recorded yet',
      },
    ];
  }, [activityHighlights]);

  const handleRefresh = async () => {
    await Promise.all([
      loadAdminData(true),
      loadOperations(true),
      loadBreakdown(true),
      loadContributors(true),
      loadFamilies(true),
      loadFamilyHighlights(true),
      loadEventTypes(true),
      loadHighlights(true),
      loadTimeline(true),
      loadAudit(appliedAuditFilters, auditPageIndex, auditRowsPerPage, true),
    ]);
    toast.success('Records management data refreshed');
  };

  const resetFilePlanForm = () => {
    setEditingFilePlanId(null);
    setFilePlanForm({ name: '', description: '', parentId: '' });
  };

  const resetCategoryForm = () => {
    setEditingCategoryId(null);
    setCategoryForm({ name: '', description: '', parentId: '' });
  };

  const handleSubmitFilePlan = async () => {
    if (!editingFilePlanId && !filePlanForm.name.trim()) {
      toast.error('File plan name is required');
      return;
    }
    setFilePlanSubmitting(true);
    try {
      if (editingFilePlanId) {
        await recordsManagementService.updateFilePlan(editingFilePlanId, {
          description: filePlanForm.description,
        });
        toast.success('File plan updated');
      } else {
        await recordsManagementService.createFilePlan({
          name: filePlanForm.name,
          description: filePlanForm.description,
          parentId: filePlanForm.parentId || undefined,
        });
        toast.success('File plan created');
      }
      resetFilePlanForm();
      await loadAdminData(true);
    } catch {
      toast.error(editingFilePlanId ? 'Failed to update file plan' : 'Failed to create file plan');
    } finally {
      setFilePlanSubmitting(false);
    }
  };

  const handleSubmitCategory = async () => {
    if (!editingCategoryId && !categoryForm.name.trim()) {
      toast.error('Record category name is required');
      return;
    }
    setCategorySubmitting(true);
    try {
      if (editingCategoryId) {
        await recordsManagementService.updateRecordCategory(editingCategoryId, {
          description: categoryForm.description,
        });
        toast.success('Record category updated');
      } else {
        await recordsManagementService.createRecordCategory({
          name: categoryForm.name,
          description: categoryForm.description,
          parentId: categoryForm.parentId || undefined,
        });
        toast.success('Record category created');
      }
      resetCategoryForm();
      await loadAdminData(true);
    } catch {
      toast.error(editingCategoryId ? 'Failed to update record category' : 'Failed to create record category');
    } finally {
      setCategorySubmitting(false);
    }
  };

  const handleEditFilePlan = (filePlan: FilePlan) => {
    setEditingFilePlanId(filePlan.folderId);
    setFilePlanForm({
      name: filePlan.name,
      description: filePlan.description ?? '',
      parentId: filePlan.parentId ?? '',
    });
  };

  const handleDeleteFilePlan = async (filePlan: FilePlan) => {
    if (!window.confirm(`Delete file plan "${filePlan.name}"? It must be empty and have no active retention attachments.`)) {
      return;
    }
    setDeletingFilePlanId(filePlan.folderId);
    try {
      await recordsManagementService.deleteFilePlan(filePlan.folderId);
      if (editingFilePlanId === filePlan.folderId) {
        resetFilePlanForm();
      }
      await loadAdminData(true);
      toast.success('File plan deleted');
    } catch {
      toast.error('Failed to delete file plan');
    } finally {
      setDeletingFilePlanId(null);
    }
  };

  const handleOpenRenameFilePlan = (filePlan: FilePlan) => {
    setRenameFilePlanTarget(filePlan);
    setRenameFilePlanDialogOpen(true);
  };

  const handleOpenMoveFilePlan = (filePlan: FilePlan) => {
    setMoveFilePlanTarget(filePlan);
    setMoveFilePlanDialogOpen(true);
  };

  const handleFilePlanRenamed = async (updated: FilePlan) => {
    if (editingFilePlanId === updated.folderId) {
      resetFilePlanForm();
    }
    await loadAdminData(true);
  };

  const handleFilePlanMoved = async (updated: FilePlan) => {
    if (editingFilePlanId === updated.folderId) {
      resetFilePlanForm();
    }
    await loadAdminData(true);
  };

  const handleEditCategory = (category: RecordCategory) => {
    setEditingCategoryId(category.categoryId);
    setCategoryForm({
      name: category.name,
      description: category.description ?? '',
      parentId: category.parentId ?? '',
    });
  };

  const handleDeleteCategory = async (category: RecordCategory) => {
    if (!window.confirm(`Delete record category "${category.name}"? It must be a leaf and unused.`)) {
      return;
    }
    setDeletingCategoryId(category.categoryId);
    try {
      await recordsManagementService.deleteRecordCategory(category.categoryId);
      if (editingCategoryId === category.categoryId) {
        resetCategoryForm();
      }
      await loadAdminData(true);
      toast.success('Record category deleted');
    } catch {
      toast.error('Failed to delete record category');
    } finally {
      setDeletingCategoryId(null);
    }
  };

  const handleOpenRenameCategory = (category: RecordCategory) => {
    setRenameCategoryTarget(category);
    setRenameCategoryDialogOpen(true);
  };

  const handleOpenMoveCategory = (category: RecordCategory) => {
    setMoveCategoryTarget(category);
    setMoveCategoryDialogOpen(true);
  };

  const handleCategoryRenamed = async (updated: RecordCategory) => {
    if (editingCategoryId === updated.categoryId) {
      resetCategoryForm();
    }
    await loadAdminData(true);
  };

  const handleCategoryMoved = async (updated: RecordCategory) => {
    if (editingCategoryId === updated.categoryId) {
      resetCategoryForm();
    }
    await loadAdminData(true);
  };

  const handleAssignCategory = async (record: RecordDeclaration) => {
    const categoryId = recordCategoryDrafts[record.nodeId];
    if (!categoryId) {
      toast.error('Choose a record category first');
      return;
    }
    setAssigningRecordId(record.nodeId);
    try {
      const updated = await recordsManagementService.assignRecordCategory(record.nodeId, categoryId);
      setRecords((current) => current.map((item) => (item.nodeId === updated.nodeId ? updated : item)));
      setRecordCategoryDrafts((current) => ({ ...current, [record.nodeId]: updated.recordCategoryId ?? categoryId }));
      const nextSummary = await recordsManagementService.getSummary();
      setSummary(nextSummary);
      toast.success('Record category assigned');
    } catch {
      toast.error('Failed to assign record category');
    } finally {
      setAssigningRecordId(null);
    }
  };

  const handleOpenUndeclare = (record: RecordDeclaration) => {
    setUndeclareTarget(record);
    setUndeclareDialogOpen(true);
  };

  const handleApplyAuditFilters = () => {
    setAuditPageIndex(0);
    setAuditDrilldown(null);
    setAppliedAuditFilters({
      family: auditFilters.family.trim(),
      eventType: auditFilters.eventType.trim(),
      username: auditFilters.username.trim(),
      from: auditFilters.from.trim(),
      to: auditFilters.to.trim(),
    });
  };

  const handleClearAuditFilters = () => {
    setAuditDrilldown(null);
    setAuditFilters(emptyAuditFiltersState());
    setAuditPageIndex(0);
    setAppliedAuditFilters(emptyAuditFiltersState());
  };

  return (
    <Box sx={{ p: 3 }}>
      <Stack
        direction={{ xs: 'column', md: 'row' }}
        spacing={2}
        alignItems={{ xs: 'flex-start', md: 'center' }}
        justifyContent="space-between"
        sx={{ mb: 3 }}
      >
        <Box>
          <Typography variant="h4" gutterBottom>
            Records Management
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Manage file plans, record categories, declared records, and governance audit from one admin surface.
          </Typography>
        </Box>
        <Button
          variant="outlined"
          startIcon={<Refresh />}
          onClick={() => void handleRefresh()}
          disabled={loading || refreshing}
        >
          {refreshing ? 'Refreshing...' : 'Refresh'}
        </Button>
      </Stack>

      {error && (
        <Alert severity="error" sx={{ mb: 3 }}>
          {error}
        </Alert>
      )}

      <Grid container spacing={3}>
        <Grid item xs={12}>
          <Grid container spacing={2}>
            {[
              { label: 'Declared Records', value: summary?.declaredRecordCount ?? 0 },
              { label: 'File Plans', value: summary?.filePlanCount ?? 0 },
              { label: 'Record Categories', value: summary?.recordCategoryCount ?? 0 },
              { label: 'Uncategorized Records', value: summary?.uncategorizedRecordCount ?? 0 },
              { label: 'Outside File Plan', value: summary?.outsideFilePlanRecordCount ?? 0 },
            ].map((metric) => (
              <Grid item xs={12} sm={6} md={metric.label === 'Outside File Plan' ? 12 : 3} key={metric.label}>
                <Card variant="outlined">
                  <CardContent>
                    <Typography variant="body2" color="text.secondary">
                      {metric.label}
                    </Typography>
                    <Typography variant="h4" sx={{ mt: 1 }}>
                      {metric.value}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
            ))}
          </Grid>
        </Grid>

        <Grid item xs={12} md={6}>
          <Card variant="outlined" sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Declared Record Coverage Snapshot
              </Typography>
              <Stack spacing={2}>
                {declaredRecordSnapshot.map((distribution) => (
                  <SnapshotDistributionCard
                    key={distribution.title}
                    title={distribution.title}
                    subtitle={distribution.subtitle}
                    totalLabel={distribution.totalLabel}
                    emptyLabel={distribution.emptyLabel}
                    segments={distribution.segments}
                  />
                ))}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={6}>
          <Card variant="outlined" sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Governed Operations Snapshot
              </Typography>
              <Stack spacing={2}>
                {governedOperationsSnapshot.map((distribution) => (
                  <SnapshotDistributionCard
                    key={distribution.title}
                    title={distribution.title}
                    subtitle={distribution.subtitle}
                    totalLabel={distribution.totalLabel}
                    emptyLabel={distribution.emptyLabel}
                    segments={distribution.segments}
                  />
                ))}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} ref={reportPresetsRef}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={1.5}>
                <Box>
                  <Typography variant="h6" gutterBottom>
                    RM Activity Highlights
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Last {activityHighlights?.windowDays ?? ACTIVITY_HIGHLIGHTS_DEFAULT_WINDOW_DAYS} day(s) compared with the previous window, based on existing RM audit activity only.
                  </Typography>
                </Box>

                {highlightsLoading && (
                  <Typography variant="body2" color="text.secondary">
                    Loading RM activity highlights...
                  </Typography>
                )}

                {highlightsError && (
                  <Alert severity="warning">
                    {highlightsError}
                  </Alert>
                )}

                {!highlightsLoading && !highlightsError && activityHighlights && (
                  <>
                    <Stack spacing={0.5}>
                      <Typography variant="body2" color="text.secondary">
                        Current window: {formatWindowRangeLabel(
                          activityHighlights.currentWindow.fromDay,
                          activityHighlights.currentWindow.toDay,
                          activityHighlights.currentWindow.activeDayCount
                        )}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Previous window: {formatWindowRangeLabel(
                          activityHighlights.previousWindow.fromDay,
                          activityHighlights.previousWindow.toDay,
                          activityHighlights.previousWindow.activeDayCount
                        )}
                      </Typography>
                    </Stack>

                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      <Button
                        size="small"
                        variant="outlined"
                        onClick={() => reviewAuditRange(
                          'Current activity window',
                          activityHighlights.currentWindow.fromDay,
                          activityHighlights.currentWindow.toDay
                        )}
                      >
                        Review current-window audit
                      </Button>
                      <Button
                        size="small"
                        variant="outlined"
                        onClick={() => reviewAuditRange(
                          'Previous activity window',
                          activityHighlights.previousWindow.fromDay,
                          activityHighlights.previousWindow.toDay
                        )}
                      >
                        Review previous-window audit
                      </Button>
                    </Stack>

                    <Grid container spacing={2}>
                      {activityHighlightMetrics.map((metric) => (
                        <Grid item xs={12} sm={6} md={4} key={metric.label}>
                          <Card variant="outlined">
                            <CardContent>
                              <Typography variant="body2" color="text.secondary">
                                {metric.label}
                              </Typography>
                              <Typography variant="h5" sx={{ mt: 1 }}>
                                {metric.value}
                              </Typography>
                              <Typography variant="caption" color="text.secondary">
                                {metric.caption}
                              </Typography>
                            </CardContent>
                          </Card>
                        </Grid>
                      ))}
                    </Grid>
                  </>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={1.5}>
                <Box>
                  <Typography variant="h6" gutterBottom>
                    RM Contributor Family Trend
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Last {activityContributorFamilyTrend?.days ?? ACTIVITY_CONTRIBUTORS_DEFAULT_DAYS} day(s), grouped into {activityContributorFamilyTrend?.bucketDays ?? ACTIVITY_CONTRIBUTOR_EVENT_TYPE_TREND_DEFAULT_BUCKET_DAYS} day bucket(s), with nested RM family breakdown per tracked contributor.
                  </Typography>
                </Box>

                {contributorFamilyTrendLoading && (
                  <Typography variant="body2" color="text.secondary">
                    Loading RM contributor family trend...
                  </Typography>
                )}

                {contributorFamilyTrendError && (
                  <Alert severity="warning">
                    {contributorFamilyTrendError}
                  </Alert>
                )}

                {!contributorFamilyTrendLoading && !contributorFamilyTrendError && (
                  <>
                    {(activityContributorFamilyTrend?.buckets?.length ?? 0) === 0 ? (
                      <Typography variant="body2" color="text.secondary">
                        No RM contributor family trend is available yet.
                      </Typography>
                    ) : (
                      <Stack spacing={1.5}>
                        {activityContributorFamilyTrend?.buckets.map((bucket) => (
                          <Card key={bucket.label} variant="outlined">
                            <CardContent>
                              <Stack spacing={1.5}>
                                <Box>
                                  <Typography variant="subtitle1">
                                    {bucket.label}
                                  </Typography>
                                  <Typography variant="body2" color="text.secondary">
                                    {`${bucket.totalCount} event(s) · ${bucket.activeDayCount} active day(s) · ${bucket.otherCount} outside tracked contributors`}
                                  </Typography>
                                </Box>

                                {bucket.contributorCounts.length === 0 ? (
                                  <Typography variant="body2" color="text.secondary">
                                    No tracked contributors matched this bucket.
                                  </Typography>
                                ) : (
                                  <Stack spacing={1.25}>
                                    {bucket.contributorCounts.map((contributor) => (
                                      <Card
                                        key={`${bucket.label}-${contributor.label}-${contributor.username ?? 'system'}-family`}
                                        variant="outlined"
                                      >
                                        <CardContent>
                                          <Stack spacing={1}>
                                            <Box>
                                              <Typography variant="subtitle2">
                                                {contributor.label}
                                              </Typography>
                                              <Typography variant="body2" color="text.secondary">
                                                {`${contributor.count} event(s) in this bucket`}
                                              </Typography>
                                            </Box>
                                            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                              {contributor.families.map((family) => (
                                                <Button
                                                  key={`${bucket.label}-${contributor.label}-${family.family}`}
                                                  size="small"
                                                  variant="text"
                                                  onClick={() => reviewContributorFamilyTrendAudit(
                                                    contributor.username,
                                                    contributor.label,
                                                    family.family,
                                                    bucket.fromDay,
                                                    bucket.toDay
                                                  )}
                                                >
                                                  {`${formatRmActivityFamilyLabel(family.family)} ${family.count}`}
                                                </Button>
                                              ))}
                                            </Stack>
                                          </Stack>
                                        </CardContent>
                                      </Card>
                                    ))}
                                  </Stack>
                                )}
                              </Stack>
                            </CardContent>
                          </Card>
                        ))}
                      </Stack>
                    )}
                  </>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={1.5}>
                <Box>
                  <Typography variant="h6" gutterBottom>
                    RM Contributor Family Highlights
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Last {activityContributorFamilyHighlights?.windowDays ?? ACTIVITY_CONTRIBUTOR_EVENT_TYPE_HIGHLIGHTS_DEFAULT_WINDOW_DAYS} day(s) compared with the previous window, broken down by RM family for each top contributor.
                  </Typography>
                </Box>

                {contributorFamilyHighlightsLoading && (
                  <Typography variant="body2" color="text.secondary">
                    Loading RM contributor family highlights...
                  </Typography>
                )}

                {contributorFamilyHighlightsError && (
                  <Alert severity="warning">
                    {contributorFamilyHighlightsError}
                  </Alert>
                )}

                {!contributorFamilyHighlightsLoading && !contributorFamilyHighlightsError && (
                  <>
                    {activityContributorFamilyHighlights ? (
                      <>
                        <Stack spacing={0.5}>
                          <Typography variant="body2" color="text.secondary">
                            Current window: {formatWindowRangeLabel(
                              activityContributorFamilyHighlights.currentWindow.fromDay,
                              activityContributorFamilyHighlights.currentWindow.toDay
                            )}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Previous window: {formatWindowRangeLabel(
                              activityContributorFamilyHighlights.previousWindow.fromDay,
                              activityContributorFamilyHighlights.previousWindow.toDay
                            )}
                          </Typography>
                        </Stack>

                        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                          <Button
                            size="small"
                            variant="outlined"
                            disabled={contributorFamilyReportExportingScope === 'current'}
                            onClick={() => exportContributorFamilyReportCsv('current')}
                          >
                            Export current CSV
                          </Button>
                          <Button
                            size="small"
                            variant="outlined"
                            disabled={contributorFamilyReportExportingScope === 'previous'}
                            onClick={() => exportContributorFamilyReportCsv('previous')}
                          >
                            Export previous CSV
                          </Button>
                          <Button
                            size="small"
                            variant="outlined"
                            onClick={() => openContributorFamilyReportPreset('current')}
                          >
                            Save current preset
                          </Button>
                          <Button
                            size="small"
                            variant="outlined"
                            onClick={() => openContributorFamilyReportPreset('previous')}
                          >
                            Save previous preset
                          </Button>
                        </Stack>

                        {activityContributorFamilyHighlights.contributors.length === 0 ? (
                          <Typography variant="body2" color="text.secondary">
                            No RM contributor family highlights are available yet.
                          </Typography>
                        ) : (
                          <Stack spacing={1.5}>
                            {activityContributorFamilyHighlights.contributors.map((contributor) => (
                              <Card key={`${contributor.label}-${contributor.username ?? 'system'}-family-highlights`} variant="outlined">
                                <CardContent>
                                  <Stack spacing={1.25}>
                                    <Box>
                                      <Typography variant="subtitle1">
                                        {contributor.label}
                                      </Typography>
                                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mt: 0.5 }}>
                                        <Chip size="small" label={`Current ${contributor.currentCount}`} variant="outlined" />
                                        <Chip size="small" label={`Previous ${contributor.previousCount}`} variant="outlined" />
                                      </Stack>
                                      <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
                                        {`${formatSignedDelta(contributor.currentCount, contributor.previousCount)} · Last event ${formatDateTime(contributor.lastEventTime)}`}
                                      </Typography>
                                    </Box>

                                    <Stack spacing={1}>
                                      {contributor.families.map((family) => (
                                        <Card
                                          key={`${contributor.label}-${family.family}-highlight`}
                                          variant="outlined"
                                        >
                                          <CardContent>
                                            <Stack
                                              direction={{ xs: 'column', md: 'row' }}
                                              spacing={2}
                                              justifyContent="space-between"
                                              alignItems={{ xs: 'flex-start', md: 'center' }}
                                            >
                                              <Box>
                                                <Typography variant="subtitle2">
                                                  {formatRmActivityFamilyLabel(family.family)}
                                                </Typography>
                                                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mt: 0.5 }}>
                                                  <Chip size="small" label={`Current ${family.currentCount}`} variant="outlined" />
                                                  <Chip size="small" label={`Previous ${family.previousCount}`} variant="outlined" />
                                                </Stack>
                                                <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
                                                  {`${formatSignedDelta(family.currentCount, family.previousCount)} · Last event ${formatDateTime(family.lastEventTime)}`}
                                                </Typography>
                                              </Box>
                                              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                                <Button
                                                  size="small"
                                                  variant="outlined"
                                                  onClick={() => reviewContributorFamilyHighlightAudit(
                                                    contributor.username,
                                                    contributor.label,
                                                    family.family,
                                                    'current'
                                                  )}
                                                >
                                                  Review current audit
                                                </Button>
                                                <Button
                                                  size="small"
                                                  variant="outlined"
                                                  onClick={() => reviewContributorFamilyHighlightAudit(
                                                    contributor.username,
                                                    contributor.label,
                                                    family.family,
                                                    'previous'
                                                  )}
                                                >
                                                  Review previous audit
                                                </Button>
                                              </Stack>
                                            </Stack>
                                          </CardContent>
                                        </Card>
                                      ))}
                                    </Stack>
                                  </Stack>
                                </CardContent>
                              </Card>
                            ))}
                          </Stack>
                        )}
                      </>
                    ) : null}
                  </>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={1.5}>
                <Box>
                  <Typography variant="h6" gutterBottom>
                    RM Contributor Event-Type Highlights
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Last {activityContributorEventTypeHighlights?.windowDays ?? ACTIVITY_CONTRIBUTOR_EVENT_TYPE_HIGHLIGHTS_DEFAULT_WINDOW_DAYS} day(s) compared with the previous window, broken down by exact event type for each top contributor.
                  </Typography>
                </Box>

                {contributorEventTypeHighlightsLoading && (
                  <Typography variant="body2" color="text.secondary">
                    Loading RM contributor event-type highlights...
                  </Typography>
                )}

                {contributorEventTypeHighlightsError && (
                  <Alert severity="warning">
                    {contributorEventTypeHighlightsError}
                  </Alert>
                )}

                {!contributorEventTypeHighlightsLoading && !contributorEventTypeHighlightsError && (
                  <>
                    {activityContributorEventTypeHighlights ? (
                      <>
                        <Stack spacing={0.5}>
                          <Typography variant="body2" color="text.secondary">
                            Current window: {formatWindowRangeLabel(
                              activityContributorEventTypeHighlights.currentWindow.fromDay,
                              activityContributorEventTypeHighlights.currentWindow.toDay
                            )}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Previous window: {formatWindowRangeLabel(
                              activityContributorEventTypeHighlights.previousWindow.fromDay,
                              activityContributorEventTypeHighlights.previousWindow.toDay
                            )}
                          </Typography>
                        </Stack>

                        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                          <Button
                            size="small"
                            variant="outlined"
                            disabled={contributorEventTypeReportExportingScope === 'current'}
                            onClick={() => exportContributorEventTypeReportCsv('current')}
                          >
                            Export current CSV
                          </Button>
                          <Button
                            size="small"
                            variant="outlined"
                            disabled={contributorEventTypeReportExportingScope === 'previous'}
                            onClick={() => exportContributorEventTypeReportCsv('previous')}
                          >
                            Export previous CSV
                          </Button>
                          <Button
                            size="small"
                            variant="outlined"
                            onClick={() => openContributorEventTypeReportPreset('current')}
                          >
                            Save current preset
                          </Button>
                          <Button
                            size="small"
                            variant="outlined"
                            onClick={() => openContributorEventTypeReportPreset('previous')}
                          >
                            Save previous preset
                          </Button>
                        </Stack>

                        {activityContributorEventTypeHighlights.contributors.length === 0 ? (
                          <Typography variant="body2" color="text.secondary">
                            No RM contributor event-type highlights are available yet.
                          </Typography>
                        ) : (
                          <Stack spacing={1.5}>
                            {activityContributorEventTypeHighlights.contributors.map((contributor) => (
                              <Card key={`${contributor.label}-${contributor.username ?? 'system'}-highlights`} variant="outlined">
                                <CardContent>
                                  <Stack spacing={1.25}>
                                    <Box>
                                      <Typography variant="subtitle1">
                                        {contributor.label}
                                      </Typography>
                                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mt: 0.5 }}>
                                        <Chip size="small" label={`Current ${contributor.currentCount}`} variant="outlined" />
                                        <Chip size="small" label={`Previous ${contributor.previousCount}`} variant="outlined" />
                                      </Stack>
                                      <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
                                        {`${formatSignedDelta(contributor.currentCount, contributor.previousCount)} · Last event ${formatDateTime(contributor.lastEventTime)}`}
                                      </Typography>
                                    </Box>

                                    <Stack spacing={1}>
                                      {contributor.eventTypes.map((eventType) => (
                                        <Card
                                          key={`${contributor.label}-${eventType.eventType}-highlight`}
                                          variant="outlined"
                                        >
                                          <CardContent>
                                            <Stack
                                              direction={{ xs: 'column', md: 'row' }}
                                              spacing={2}
                                              justifyContent="space-between"
                                              alignItems={{ xs: 'flex-start', md: 'center' }}
                                            >
                                              <Box>
                                                <Typography variant="subtitle2">
                                                  {formatRmAuditEventTypeLabel(eventType.eventType)}
                                                </Typography>
                                                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mt: 0.5 }}>
                                                  <Chip size="small" label={`Current ${eventType.currentCount}`} variant="outlined" />
                                                  <Chip size="small" label={`Previous ${eventType.previousCount}`} variant="outlined" />
                                                  <Chip size="small" label={formatRmActivityFamilyLabel(eventType.family)} variant="outlined" />
                                                </Stack>
                                                <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
                                                  {`${formatSignedDelta(eventType.currentCount, eventType.previousCount)} · Last event ${formatDateTime(eventType.lastEventTime)}`}
                                                </Typography>
                                              </Box>
                                              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                                <Button
                                                  size="small"
                                                  variant="outlined"
                                                  onClick={() => reviewContributorEventTypeHighlightAudit(
                                                    contributor.username,
                                                    contributor.label,
                                                    eventType.eventType,
                                                    'current'
                                                  )}
                                                >
                                                  Review current audit
                                                </Button>
                                                <Button
                                                  size="small"
                                                  variant="outlined"
                                                  onClick={() => reviewContributorEventTypeHighlightAudit(
                                                    contributor.username,
                                                    contributor.label,
                                                    eventType.eventType,
                                                    'previous'
                                                  )}
                                                >
                                                  Review previous audit
                                                </Button>
                                              </Stack>
                                            </Stack>
                                          </CardContent>
                                        </Card>
                                      ))}
                                    </Stack>
                                  </Stack>
                                </CardContent>
                              </Card>
                            ))}
                          </Stack>
                        )}
                      </>
                    ) : null}
                  </>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={1.5}>
                <Box>
                  <Typography variant="h6" gutterBottom>
                    RM Contributor Event-Type Trend
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Last {activityContributorEventTypeTrend?.days ?? ACTIVITY_CONTRIBUTORS_DEFAULT_DAYS} day(s), grouped into {activityContributorEventTypeTrend?.bucketDays ?? ACTIVITY_CONTRIBUTOR_EVENT_TYPE_TREND_DEFAULT_BUCKET_DAYS} day bucket(s), with nested exact event-type breakdown per tracked contributor.
                  </Typography>
                </Box>

                {contributorEventTypeTrendLoading && (
                  <Typography variant="body2" color="text.secondary">
                    Loading RM contributor event-type trend...
                  </Typography>
                )}

                {contributorEventTypeTrendError && (
                  <Alert severity="warning">
                    {contributorEventTypeTrendError}
                  </Alert>
                )}

                {!contributorEventTypeTrendLoading && !contributorEventTypeTrendError && (
                  <>
                    {(activityContributorEventTypeTrend?.buckets?.length ?? 0) === 0 ? (
                      <Typography variant="body2" color="text.secondary">
                        No RM contributor event-type trend is available yet.
                      </Typography>
                    ) : (
                      <Stack spacing={1.5}>
                        {activityContributorEventTypeTrend?.buckets.map((bucket) => (
                          <Card key={bucket.label} variant="outlined">
                            <CardContent>
                              <Stack spacing={1.5}>
                                <Box>
                                  <Typography variant="subtitle1">
                                    {bucket.label}
                                  </Typography>
                                  <Typography variant="body2" color="text.secondary">
                                    {`${bucket.totalCount} event(s) · ${bucket.activeDayCount} active day(s) · ${bucket.otherCount} outside tracked contributors`}
                                  </Typography>
                                </Box>

                                {bucket.contributorCounts.length === 0 ? (
                                  <Typography variant="body2" color="text.secondary">
                                    No tracked contributors matched this bucket.
                                  </Typography>
                                ) : (
                                  <Stack spacing={1.25}>
                                    {bucket.contributorCounts.map((contributor) => (
                                      <Card
                                        key={`${bucket.label}-${contributor.label}-${contributor.username ?? 'system'}`}
                                        variant="outlined"
                                      >
                                        <CardContent>
                                          <Stack spacing={1}>
                                            <Box>
                                              <Typography variant="subtitle2">
                                                {contributor.label}
                                              </Typography>
                                              <Typography variant="body2" color="text.secondary">
                                                {`${contributor.count} event(s) in this bucket`}
                                              </Typography>
                                            </Box>
                                            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                              {contributor.eventTypes.map((eventType) => (
                                                <Button
                                                  key={`${bucket.label}-${contributor.label}-${eventType.eventType}`}
                                                  size="small"
                                                  variant="text"
                                                  onClick={() => reviewContributorEventTypeTrendAudit(
                                                    contributor.username,
                                                    contributor.label,
                                                    eventType.eventType,
                                                    bucket.fromDay,
                                                    bucket.toDay
                                                  )}
                                                >
                                                  {`${formatRmAuditEventTypeLabel(eventType.eventType)} ${eventType.count}`}
                                                </Button>
                                              ))}
                                            </Stack>
                                          </Stack>
                                        </CardContent>
                                      </Card>
                                    ))}
                                  </Stack>
                                )}
                              </Stack>
                            </CardContent>
                          </Card>
                        ))}
                      </Stack>
                    )}
                  </>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={1.5}>
                <Box>
                  <Typography variant="h6" gutterBottom>
                    RM Activity Family Highlights
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Last {activityFamilyHighlights?.windowDays ?? ACTIVITY_FAMILY_HIGHLIGHTS_DEFAULT_WINDOW_DAYS} day(s) compared with the previous window, broken down by RM activity family.
                  </Typography>
                </Box>

                {familyHighlightsLoading && (
                  <Typography variant="body2" color="text.secondary">
                    Loading RM activity family highlights...
                  </Typography>
                )}

                {familyHighlightsError && (
                  <Alert severity="warning">
                    {familyHighlightsError}
                  </Alert>
                )}

                {!familyHighlightsLoading && !familyHighlightsError && (
                  <>
                    {activityFamilyHighlights ? (
                      <>
                        <Stack spacing={0.5}>
                          <Typography variant="body2" color="text.secondary">
                            Current window: {formatWindowRangeLabel(
                              activityFamilyHighlights.currentWindow.fromDay,
                              activityFamilyHighlights.currentWindow.toDay
                            )}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Previous window: {formatWindowRangeLabel(
                              activityFamilyHighlights.previousWindow.fromDay,
                              activityFamilyHighlights.previousWindow.toDay
                            )}
                          </Typography>
                        </Stack>

                        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                          <Button
                            size="small"
                            variant="outlined"
                            disabled={activityFamilyReportExportingScope === 'current'}
                            onClick={() => exportActivityFamilyReportCsv('current')}
                          >
                            Export current CSV
                          </Button>
                          <Button
                            size="small"
                            variant="outlined"
                            disabled={activityFamilyReportExportingScope === 'previous'}
                            onClick={() => exportActivityFamilyReportCsv('previous')}
                          >
                            Export previous CSV
                          </Button>
                          <Button
                            size="small"
                            variant="outlined"
                            onClick={() => openActivityFamilyReportPreset('current')}
                          >
                            Save current preset
                          </Button>
                          <Button
                            size="small"
                            variant="outlined"
                            onClick={() => openActivityFamilyReportPreset('previous')}
                          >
                            Save previous preset
                          </Button>
                        </Stack>

                        {activityFamilyHighlights.families.length === 0 ? (
                          <Typography variant="body2" color="text.secondary">
                            No RM activity family highlights are available yet.
                          </Typography>
                        ) : (
                          <Stack spacing={1.5}>
                            {activityFamilyHighlights.families.map((entry) => (
                              <Card key={entry.family} variant="outlined">
                                <CardContent>
                                  <Stack
                                    direction={{ xs: 'column', md: 'row' }}
                                    spacing={2}
                                    justifyContent="space-between"
                                    alignItems={{ xs: 'flex-start', md: 'center' }}
                                  >
                                    <Box>
                                      <Typography variant="subtitle1">
                                        {formatRmActivityFamilyLabel(entry.family)}
                                      </Typography>
                                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mt: 0.5 }}>
                                        <Chip size="small" label={`Current ${entry.currentCount}`} variant="outlined" />
                                        <Chip size="small" label={`Previous ${entry.previousCount}`} variant="outlined" />
                                      </Stack>
                                      <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
                                        {`${formatSignedDelta(entry.currentCount, entry.previousCount)} · Last event ${formatDateTime(entry.lastEventTime)}`}
                                      </Typography>
                                    </Box>
                                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                      <Button
                                        size="small"
                                        variant="outlined"
                                        onClick={() => reviewFamilyHighlightAudit(entry.family as (typeof AUDIT_FAMILY_OPTIONS)[number]['value'], 'current')}
                                      >
                                        Review current audit
                                      </Button>
                                      <Button
                                        size="small"
                                        variant="outlined"
                                        onClick={() => reviewFamilyHighlightAudit(entry.family as (typeof AUDIT_FAMILY_OPTIONS)[number]['value'], 'previous')}
                                      >
                                        Review previous audit
                                      </Button>
                                    </Stack>
                                  </Stack>
                                </CardContent>
                              </Card>
                            ))}
                          </Stack>
                        )}
                      </>
                    ) : null}
                  </>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={1.5}>
                <Box>
                  <Typography variant="h6" gutterBottom>
                    RM Activity Family Mix
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Family-level mix over the last {activityFamilies?.days ?? ACTIVITY_FAMILIES_DEFAULT_DAYS} day(s), based on existing RM audit activity only.
                  </Typography>
                </Box>

                {familiesLoading && (
                  <Typography variant="body2" color="text.secondary">
                    Loading RM activity family mix...
                  </Typography>
                )}

                {familiesError && (
                  <Alert severity="warning">
                    {familiesError}
                  </Alert>
                )}

                {!familiesLoading && !familiesError && (
                  <>
                    {(activityFamilies?.families?.length ?? 0) === 0 ? (
                      <Typography variant="body2" color="text.secondary">
                        No RM activity family mix is available yet.
                      </Typography>
                    ) : (
                      <Stack spacing={1.5}>
                        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                          <Button
                            size="small"
                            variant="outlined"
                            disabled={activityFamilyMixReportExportingScope === 'current'}
                            onClick={() => exportActivityFamilyMixReportCsv('current')}
                          >
                            Export current CSV
                          </Button>
                          <Button
                            size="small"
                            variant="outlined"
                            disabled={activityFamilyMixReportExportingScope === 'previous'}
                            onClick={() => exportActivityFamilyMixReportCsv('previous')}
                          >
                            Export previous CSV
                          </Button>
                          <Button
                            size="small"
                            variant="outlined"
                            onClick={() => openActivityFamilyMixReportPreset('current')}
                          >
                            Save current preset
                          </Button>
                          <Button
                            size="small"
                            variant="outlined"
                            onClick={() => openActivityFamilyMixReportPreset('previous')}
                          >
                            Save previous preset
                          </Button>
                        </Stack>
                        {activityFamilies?.families.map((entry) => (
                          <Card key={entry.family} variant="outlined">
                            <CardContent>
                              <Stack
                                direction={{ xs: 'column', md: 'row' }}
                                spacing={2}
                                justifyContent="space-between"
                                alignItems={{ xs: 'flex-start', md: 'center' }}
                              >
                                <Box>
                                  <Typography variant="subtitle1">
                                    {formatRmActivityFamilyLabel(entry.family)}
                                  </Typography>
                                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mt: 0.5 }}>
                                    <Chip size="small" label={formatRmActivityFamilyLabel(entry.family)} variant="outlined" />
                                    <Chip size="small" label={`${entry.count} event(s)`} variant="outlined" />
                                  </Stack>
                                  <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
                                    {`${formatShareOfTotal(entry.count, activityFamilies?.totalCount ?? 0)} · Last event ${formatDateTime(entry.lastEventTime)}`}
                                  </Typography>
                                </Box>
                                <Button
                                  size="small"
                                  variant="outlined"
                                  onClick={() => reviewFamilyAudit(entry.family as (typeof AUDIT_FAMILY_OPTIONS)[number]['value'])}
                                >
                                  Review family audit
                                </Button>
                              </Stack>
                            </CardContent>
                          </Card>
                        ))}
                      </Stack>
                    )}
                  </>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={1.5}>
                <Box>
                  <Typography variant="h6" gutterBottom>
                    RM Activity Event Hotspots
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Top {activityEventTypes?.limit ?? ACTIVITY_EVENT_TYPES_DEFAULT_LIMIT} RM event type(s) over the last {activityEventTypes?.days ?? ACTIVITY_EVENT_TYPES_DEFAULT_DAYS} day(s), based on existing RM audit activity only.
                  </Typography>
                </Box>

                {eventTypesLoading && (
                  <Typography variant="body2" color="text.secondary">
                    Loading RM activity event hotspots...
                  </Typography>
                )}

                {eventTypesError && (
                  <Alert severity="warning">
                    {eventTypesError}
                  </Alert>
                )}

                {!eventTypesLoading && !eventTypesError && (
                  <>
                    {(activityEventTypes?.eventTypes?.length ?? 0) === 0 ? (
                      <Typography variant="body2" color="text.secondary">
                        No RM activity event hotspots are available yet.
                      </Typography>
                    ) : (
                      <Stack spacing={1.5}>
                        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                          <Button
                            size="small"
                            variant="outlined"
                            disabled={activityEventTypeReportExportingScope === 'current'}
                            onClick={() => exportActivityEventTypeReportCsv('current')}
                          >
                            Export current CSV
                          </Button>
                          <Button
                            size="small"
                            variant="outlined"
                            disabled={activityEventTypeReportExportingScope === 'previous'}
                            onClick={() => exportActivityEventTypeReportCsv('previous')}
                          >
                            Export previous CSV
                          </Button>
                          <Button
                            size="small"
                            variant="outlined"
                            onClick={() => openActivityEventTypeReportPreset('current')}
                          >
                            Save current preset
                          </Button>
                          <Button
                            size="small"
                            variant="outlined"
                            onClick={() => openActivityEventTypeReportPreset('previous')}
                          >
                            Save previous preset
                          </Button>
                        </Stack>
                        {activityEventTypes?.eventTypes.map((entry) => (
                          <Card key={entry.eventType} variant="outlined">
                            <CardContent>
                              <Stack
                                direction={{ xs: 'column', md: 'row' }}
                                spacing={2}
                                justifyContent="space-between"
                                alignItems={{ xs: 'flex-start', md: 'center' }}
                              >
                                <Box>
                                  <Typography variant="subtitle1">
                                    {formatRmAuditEventTypeLabel(entry.eventType)}
                                  </Typography>
                                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mt: 0.5 }}>
                                    <Chip size="small" label={formatRmActivityFamilyLabel(entry.family)} variant="outlined" />
                                    <Chip size="small" label={entry.eventType} variant="outlined" />
                                  </Stack>
                                  <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
                                    {`Last event ${formatDateTime(entry.lastEventTime)} · ${entry.count} event(s)`}
                                  </Typography>
                                </Box>
                                <Button
                                  size="small"
                                  variant="outlined"
                                  onClick={() => reviewEventTypeAudit(entry.eventType)}
                                >
                                  Review event audit
                                </Button>
                              </Stack>
                            </CardContent>
                          </Card>
                        ))}
                      </Stack>
                    )}
                  </>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={1.5}>
                <Box>
                  <Typography variant="h6" gutterBottom>
                    RM Activity Contributors
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Top {activityContributors?.limit ?? ACTIVITY_CONTRIBUTORS_DEFAULT_LIMIT} contributor(s) over the last {activityContributors?.days ?? ACTIVITY_CONTRIBUTORS_DEFAULT_DAYS} day(s), based on existing RM audit activity only.
                  </Typography>
                </Box>

                {contributorsLoading && (
                  <Typography variant="body2" color="text.secondary">
                    Loading RM activity contributors...
                  </Typography>
                )}

                {contributorsError && (
                  <Alert severity="warning">
                    {contributorsError}
                  </Alert>
                )}

                {!contributorsLoading && !contributorsError && (
                  <>
                    {(activityContributors?.contributors?.length ?? 0) === 0 ? (
                      <Typography variant="body2" color="text.secondary">
                        No RM activity contributors available yet.
                      </Typography>
                    ) : (
                      <Stack spacing={1.5}>
                        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                          <Button
                            size="small"
                            variant="outlined"
                            disabled={activityContributorReportExportingScope === 'current'}
                            onClick={() => exportActivityContributorReportCsv('current')}
                          >
                            Export current CSV
                          </Button>
                          <Button
                            size="small"
                            variant="outlined"
                            disabled={activityContributorReportExportingScope === 'previous'}
                            onClick={() => exportActivityContributorReportCsv('previous')}
                          >
                            Export previous CSV
                          </Button>
                          <Button
                            size="small"
                            variant="outlined"
                            onClick={() => openActivityContributorReportPreset('current')}
                          >
                            Save current preset
                          </Button>
                          <Button
                            size="small"
                            variant="outlined"
                            onClick={() => openActivityContributorReportPreset('previous')}
                          >
                            Save previous preset
                          </Button>
                        </Stack>
                        {activityContributors?.contributors.map((contributor) => (
                          <Card key={`${contributor.label}-${contributor.username ?? 'system'}`} variant="outlined">
                            <CardContent>
                              <Stack
                                direction={{ xs: 'column', md: 'row' }}
                                spacing={2}
                                justifyContent="space-between"
                                alignItems={{ xs: 'flex-start', md: 'center' }}
                              >
                                <Box>
                                  <Typography variant="subtitle1">
                                    {contributor.label}
                                  </Typography>
                                  <Typography variant="body2" color="text.secondary">
                                    {`Declared ${contributor.declaredCount} · Undeclared ${contributor.undeclaredCount} · Category Assigned ${contributor.categoryAssignedCount} · Governance Changes ${contributor.governanceChangeCount}`}
                                  </Typography>
                                  <Typography variant="caption" color="text.secondary">
                                    {`Last event ${formatDateTime(contributor.lastEventTime)} · ${contributor.totalCount} total event(s)`}
                                  </Typography>
                                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mt: 1 }}>
                                    <Button
                                      size="small"
                                      variant="text"
                                      onClick={() => reviewContributorFamilyAudit(contributor.username, contributor.label, 'DECLARED')}
                                    >
                                      {`Declared ${contributor.declaredCount}`}
                                    </Button>
                                    <Button
                                      size="small"
                                      variant="text"
                                      onClick={() => reviewContributorFamilyAudit(contributor.username, contributor.label, 'UNDECLARED')}
                                    >
                                      {`Undeclared ${contributor.undeclaredCount}`}
                                    </Button>
                                    <Button
                                      size="small"
                                      variant="text"
                                      onClick={() => reviewContributorFamilyAudit(contributor.username, contributor.label, 'CATEGORY_ASSIGNED')}
                                    >
                                      {`Category Assigned ${contributor.categoryAssignedCount}`}
                                    </Button>
                                    <Button
                                      size="small"
                                      variant="text"
                                      onClick={() => reviewContributorFamilyAudit(contributor.username, contributor.label, 'GOVERNANCE_CHANGE')}
                                    >
                                      {`Governance Changes ${contributor.governanceChangeCount}`}
                                    </Button>
                                  </Stack>
                                </Box>
                                <Button
                                  size="small"
                                  variant="outlined"
                                  onClick={() => reviewContributorAudit(contributor.username, contributor.label)}
                                >
                                  Review contributor audit
                                </Button>
                              </Stack>
                            </CardContent>
                          </Card>
                        ))}
                      </Stack>
                    )}
                  </>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={1.5}>
                <Box>
                  <Typography variant="h6" gutterBottom>
                    RM Activity Breakdown
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Last {activityBreakdown?.days ?? ACTIVITY_BREAKDOWN_DEFAULT_DAYS} day(s), grouped into {activityBreakdown?.bucketDays ?? ACTIVITY_BREAKDOWN_DEFAULT_BUCKET_DAYS} day bucket(s), using existing RM audit activity only.
                  </Typography>
                </Box>

                {breakdownLoading && (
                  <Typography variant="body2" color="text.secondary">
                    Loading RM activity breakdown...
                  </Typography>
                )}

                {breakdownError && (
                  <Alert severity="warning">
                    {breakdownError}
                  </Alert>
                )}

                {!breakdownLoading && !breakdownError && (
                  <>
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      {ACTIVITY_TIMELINE_SERIES.map((series) => (
                        <Chip
                          key={`breakdown-${series.key}`}
                          size="small"
                          label={series.label}
                          variant="outlined"
                          sx={{
                            borderColor: series.color,
                            color: series.color,
                          }}
                        />
                      ))}
                    </Stack>

                    {(activityBreakdown?.buckets?.length ?? 0) > 0 && (
                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        <Button
                          size="small"
                          variant="outlined"
                          onClick={reviewBreakdownWindowAudit}
                        >
                          Review full breakdown audit
                        </Button>
                      </Stack>
                    )}

                    {(activityBreakdown?.buckets?.length ?? 0) === 0 ? (
                      <Typography variant="body2" color="text.secondary">
                        No RM activity breakdown data available yet.
                      </Typography>
                    ) : (
                      <Stack spacing={1.5}>
                        {activityBreakdown?.buckets.map((bucket) => (
                          <Box key={bucket.label}>
                            <Stack
                              direction={{ xs: 'column', md: 'row' }}
                              spacing={1.5}
                              alignItems={{ xs: 'flex-start', md: 'center' }}
                            >
                              <Typography
                                variant="caption"
                                color="text.secondary"
                                sx={{ minWidth: { md: 168 }, fontFamily: 'monospace' }}
                              >
                                {bucket.label}
                              </Typography>
                              <Box
                                sx={{
                                  flex: 1,
                                  width: '100%',
                                  backgroundColor: 'action.hover',
                                  borderRadius: 999,
                                  overflow: 'hidden',
                                  minHeight: 12,
                                }}
                              >
                                {bucket.totalCount > 0 ? (
                                  <Box
                                    sx={{
                                      display: 'flex',
                                      width: `${Math.max((bucket.totalCount / breakdownMaxTotal) * 100, 8)}%`,
                                      minWidth: 8,
                                    }}
                                  >
                                    {ACTIVITY_TIMELINE_SERIES.map((series) => {
                                      const value = bucket[series.key];
                                      if (value <= 0) {
                                        return null;
                                      }
                                      return (
                                        <Box
                                          key={`${bucket.label}-${series.key}`}
                                          sx={{
                                            width: `${(value / bucket.totalCount) * 100}%`,
                                            backgroundColor: series.color,
                                            minHeight: 12,
                                          }}
                                        />
                                      );
                                    })}
                                  </Box>
                                ) : (
                                  <Box sx={{ width: '6%', minWidth: 6, minHeight: 12 }} />
                                )}
                              </Box>
                              <Typography variant="caption" color="text.secondary" sx={{ minWidth: { md: 96 } }}>
                                {bucket.totalCount} event(s)
                              </Typography>
                            </Stack>
                            <Typography variant="caption" color="text.secondary">
                              Declared {bucket.declaredCount} · Undeclared {bucket.undeclaredCount} · Category Assigned {bucket.categoryAssignedCount} · Governance Changes {bucket.governanceChangeCount} · Active Days {bucket.activeDayCount}
                            </Typography>
                            <Box sx={{ mt: 0.5 }}>
                              <Button
                                size="small"
                                variant="text"
                                onClick={() => reviewAuditRange(
                                  `Activity breakdown bucket ${bucket.label}`,
                                  bucket.fromDay,
                                  bucket.toDay
                                )}
                              >
                                {`Review audit for ${bucket.label}`}
                              </Button>
                            </Box>
                          </Box>
                        ))}
                      </Stack>
                    )}
                  </>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={1.5}>
                <Box>
                  <Typography variant="h6" gutterBottom>
                    RM Activity Timeline
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Last {activityTimeline?.days ?? ACTIVITY_TIMELINE_DEFAULT_DAYS} day(s) of RM audit activity across declarations, category assignments, and governance changes.
                  </Typography>
                </Box>

                {timelineLoading && (
                  <Typography variant="body2" color="text.secondary">
                    Loading RM activity timeline...
                  </Typography>
                )}

                {timelineError && (
                  <Alert severity="warning">
                    {timelineError}
                  </Alert>
                )}

                {!timelineLoading && !timelineError && (
                  <>
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      {ACTIVITY_TIMELINE_SERIES.map((series) => (
                        <Chip
                          key={series.key}
                          size="small"
                          label={series.label}
                          variant="outlined"
                          sx={{
                            borderColor: series.color,
                            color: series.color,
                          }}
                        />
                      ))}
                    </Stack>

                    {(activityTimeline?.points?.length ?? 0) > 0 && (
                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        <Button
                          size="small"
                          variant="outlined"
                          onClick={reviewTimelineWindowAudit}
                        >
                          Review full timeline audit
                        </Button>
                      </Stack>
                    )}

                    {(activityTimeline?.points?.length ?? 0) === 0 ? (
                      <Typography variant="body2" color="text.secondary">
                        No RM activity timeline data available yet.
                      </Typography>
                    ) : (
                      <Stack spacing={1.5}>
                        {activityTimeline?.points.map((point) => (
                          <Box key={point.day}>
                            <Stack
                              direction={{ xs: 'column', md: 'row' }}
                              spacing={1.5}
                              alignItems={{ xs: 'flex-start', md: 'center' }}
                            >
                              <Typography
                                variant="caption"
                                color="text.secondary"
                                sx={{ minWidth: { md: 96 }, fontFamily: 'monospace' }}
                              >
                                {point.day}
                              </Typography>
                              <Box
                                sx={{
                                  flex: 1,
                                  width: '100%',
                                  backgroundColor: 'action.hover',
                                  borderRadius: 999,
                                  overflow: 'hidden',
                                  minHeight: 12,
                                }}
                              >
                                {point.totalCount > 0 ? (
                                  <Box
                                    sx={{
                                      display: 'flex',
                                      width: `${Math.max((point.totalCount / timelineMaxTotal) * 100, 6)}%`,
                                      minWidth: 6,
                                    }}
                                  >
                                    {ACTIVITY_TIMELINE_SERIES.map((series) => {
                                      const value = point[series.key];
                                      if (!value) {
                                        return null;
                                      }
                                      return (
                                        <Box
                                          key={`${point.day}-${series.key}`}
                                          sx={{
                                            width: `${(value / point.totalCount) * 100}%`,
                                            backgroundColor: series.color,
                                            minHeight: 12,
                                          }}
                                        />
                                      );
                                    })}
                                  </Box>
                                ) : (
                                  <Box sx={{ width: '100%', minHeight: 12, backgroundColor: 'action.disabledBackground' }} />
                                )}
                              </Box>
                              <Typography variant="caption" color="text.secondary" sx={{ minWidth: { md: 92 } }}>
                                {`${point.totalCount} event(s)`}
                              </Typography>
                            </Stack>
                            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5, ml: { md: 14 } }}>
                              {`Declared ${point.declaredCount} · Undeclared ${point.undeclaredCount} · Category Assigned ${point.categoryAssignedCount} · Governance Changes ${point.governanceChangeCount}`}
                            </Typography>
                            <Box sx={{ mt: 0.5, ml: { md: 14 } }}>
                              <Button
                                size="small"
                                variant="text"
                                onClick={() => reviewAuditDay(point.day)}
                              >
                                {`Review audit for ${point.day}`}
                              </Button>
                            </Box>
                          </Box>
                        ))}
                      </Stack>
                    )}
                  </>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12}>
          <Card variant="outlined">
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Governance Health
              </Typography>
              {activeGovernanceAlerts.length === 0 ? (
                <Alert severity="success" sx={{ mb: 2 }}>
                  No immediate records-management governance alerts.
                </Alert>
              ) : (
                <Alert severity="warning" sx={{ mb: 2 }}>
                  {`${activeGovernanceAlerts.length} records-management signal${activeGovernanceAlerts.length === 1 ? '' : 's'} need attention.`}
                </Alert>
              )}
              <Grid container spacing={2}>
                {governanceAlerts.map((signal) => (
                  <Grid item xs={12} sm={6} md={3} key={signal.label}>
                    <Card
                      variant="outlined"
                      sx={{
                        height: '100%',
                        borderColor: signal.value > 0 ? 'warning.main' : 'divider',
                      }}
                    >
                      <CardContent>
                        <Typography variant="body2" color="text.secondary">
                          {signal.label}
                        </Typography>
                        <Typography variant="h4" sx={{ mt: 1, mb: 1 }}>
                          {signal.value}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          {signal.description}
                        </Typography>
                        {signal.label === 'Uncategorized Records' && signal.value > 0 && (
                          <Button
                            size="small"
                            variant="text"
                            sx={{ mt: 1, px: 0 }}
                            onClick={reviewUncategorizedRecords}
                          >
                            Review queue
                          </Button>
                        )}
                        {signal.label === 'Outside File Plan' && signal.value > 0 && (
                          <Button
                            size="small"
                            variant="text"
                            sx={{ mt: 1, px: 0 }}
                            onClick={reviewOutsideFilePlanRecords}
                          >
                            Review coverage
                          </Button>
                        )}
                        {signal.label === 'Failed Governed Imports' && signal.value > 0 && (
                          <Button
                            size="small"
                            variant="text"
                            sx={{ mt: 1, px: 0 }}
                            onClick={reviewFailedImportJobs}
                          >
                            Review recent failures
                          </Button>
                        )}
                        {signal.label === 'Failed Governed Transfers' && signal.value > 0 && (
                          <Button
                            size="small"
                            variant="text"
                            sx={{ mt: 1, px: 0 }}
                            onClick={reviewFailedTransferJobs}
                          >
                            Review recent failures
                          </Button>
                        )}
                      </CardContent>
                    </Card>
                  </Grid>
                ))}
              </Grid>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={6}>
          <Card variant="outlined" sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Category Breakdown
              </Typography>
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                {(summary?.categoryBreakdown ?? []).slice(0, 8).map((bucket) => (
                  <Chip key={bucket.key} label={`${bucket.key} · ${bucket.count}`} variant="outlined" />
                ))}
                {summary && summary.categoryBreakdown.length === 0 && (
                  <Typography variant="body2" color="text.secondary">
                    No record categories assigned yet.
                  </Typography>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={6}>
          <Card variant="outlined" sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                File Plan Breakdown
              </Typography>
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                {(summary?.filePlanBreakdown ?? []).slice(0, 8).map((bucket) => (
                  <Chip key={bucket.key} label={`${bucket.key} · ${bucket.count}`} variant="outlined" />
                ))}
                {summary && summary.filePlanBreakdown.length === 0 && (
                  <Typography variant="body2" color="text.secondary">
                    No file plan coverage yet.
                  </Typography>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12}>
          <Card variant="outlined">
            <CardContent ref={operationsRef}>
              <Stack
                direction={{ xs: 'column', md: 'row' }}
                spacing={2}
                justifyContent="space-between"
                alignItems={{ xs: 'flex-start', md: 'center' }}
                sx={{ mb: 2 }}
              >
                <Box>
                  <Typography variant="h6" gutterBottom>
                    Governed Operations
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Track bulk import and transfer jobs that touch file plans, declared records, or RM-governed scopes.
                  </Typography>
                </Box>
                {operationsLoading && (
                  <Typography variant="body2" color="text.secondary">
                    Loading operations telemetry...
                  </Typography>
                )}
              </Stack>

              {operationsError && (
                <Alert severity="warning" sx={{ mb: 2 }}>
                  {operationsError}
                </Alert>
              )}

              {hasOperationFilterSummary && (
                <Alert
                  severity="info"
                  sx={{ mb: 2 }}
                  action={(
                    <Stack direction="row" spacing={1}>
                      {importFilterSummary.length > 0 && (
                        <Button color="inherit" size="small" onClick={clearImportOperationFilters}>
                          Clear import filters
                        </Button>
                      )}
                      {transferFilterSummary.length > 0 && (
                        <Button color="inherit" size="small" onClick={clearTransferOperationFilters}>
                          Clear transfer filters
                        </Button>
                      )}
                      <Button
                        color="inherit"
                        size="small"
                        onClick={() => {
                          clearImportOperationFilters();
                          clearTransferOperationFilters();
                        }}
                      >
                        Clear all filters
                      </Button>
                    </Stack>
                  )}
                >
                  <Stack spacing={1}>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      Selected operations filters
                    </Typography>
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      {importFilterSummary.length > 0 && (
                        <>
                          <Chip
                            size="small"
                            label={formatScopedFilterSummaryLabel('Import', importFilterSummary)}
                            color="info"
                            variant="outlined"
                          />
                          <Chip
                            size="small"
                            label={formatScopedFilterMatchLabel('Import', filteredImportJobs.length, importTotalJobs)}
                            color={filteredImportJobs.length === 0 ? 'warning' : 'info'}
                            variant={filteredImportJobs.length === 0 ? 'filled' : 'outlined'}
                          />
                        </>
                      )}
                      {transferFilterSummary.length > 0 && (
                        <>
                          <Chip
                            size="small"
                            label={formatScopedFilterSummaryLabel('Transfer', transferFilterSummary)}
                            color="secondary"
                            variant="outlined"
                          />
                          <Chip
                            size="small"
                            label={formatScopedFilterMatchLabel('Transfer', filteredTransferJobs.length, transferTotalJobs)}
                            color={filteredTransferJobs.length === 0 ? 'warning' : 'secondary'}
                            variant={filteredTransferJobs.length === 0 ? 'filled' : 'outlined'}
                          />
                        </>
                      )}
                    </Stack>
                  </Stack>
                </Alert>
              )}

              <Grid container spacing={2} sx={{ mb: 2 }}>
                {[
                  { label: 'Governed Import Jobs', value: operations?.governedImportJobCount ?? 0 },
                  { label: 'Active Import Jobs', value: operations?.activeGovernedImportJobCount ?? 0 },
                  { label: 'Failed Import Jobs', value: operations?.failedGovernedImportJobCount ?? 0 },
                  { label: 'Governed Transfer Jobs', value: operations?.governedTransferJobCount ?? 0 },
                  { label: 'Active Transfer Jobs', value: operations?.activeGovernedTransferJobCount ?? 0 },
                  { label: 'Failed Transfer Jobs', value: operations?.failedGovernedTransferJobCount ?? 0 },
                ].map((metric) => (
                  <Grid item xs={12} sm={6} md={2} key={metric.label}>
                    <Card variant="outlined">
                      <CardContent>
                        <Typography variant="body2" color="text.secondary">
                          {metric.label}
                        </Typography>
                        <Typography variant="h4" sx={{ mt: 1 }}>
                          {metric.value}
                        </Typography>
                      </CardContent>
                    </Card>
                  </Grid>
                ))}
              </Grid>

              <Grid container spacing={2} sx={{ mb: 2 }}>
                <Grid item xs={12} md={6}>
                    <Typography variant="subtitle2" gutterBottom>
                      Import Status Breakdown
                    </Typography>
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      {(operations?.importStatusBreakdown ?? []).map((bucket) => (
                      <Chip
                        key={`import-${bucket.key}`}
                        label={`${bucket.key} · ${bucket.count}`}
                        color={selectedImportStatusBucket === bucket.key ? 'primary' : 'default'}
                        variant={selectedImportStatusBucket === bucket.key ? 'filled' : 'outlined'}
                        onClick={() => reviewImportStatusBucket(bucket.key)}
                      />
                    ))}
                    {operations && operations.importStatusBreakdown.length === 0 && (
                      <Typography variant="body2" color="text.secondary">
                        No governed import jobs recorded yet.
                      </Typography>
                    )}
                  </Stack>
                </Grid>
                <Grid item xs={12} md={6}>
                    <Typography variant="subtitle2" gutterBottom>
                      Transfer Status Breakdown
                    </Typography>
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      {(operations?.transferStatusBreakdown ?? []).map((bucket) => (
                      <Chip
                        key={`transfer-${bucket.key}`}
                        label={`${bucket.key} · ${bucket.count}`}
                        color={selectedTransferStatusBucket === bucket.key ? 'primary' : 'default'}
                        variant={selectedTransferStatusBucket === bucket.key ? 'filled' : 'outlined'}
                        onClick={() => reviewTransferStatusBucket(bucket.key)}
                      />
                    ))}
                    {operations && operations.transferStatusBreakdown.length === 0 && (
                      <Typography variant="body2" color="text.secondary">
                        No governed transfer jobs recorded yet.
                      </Typography>
                    )}
                  </Stack>
                </Grid>
              </Grid>

              <Grid container spacing={2} sx={{ mb: 2 }}>
                <Grid item xs={12} md={6}>
                  <Typography variant="subtitle2" gutterBottom>
                    Top Import Governance Reasons
                  </Typography>
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    {(operations?.importGovernanceReasonBreakdown ?? []).slice(0, 8).map((bucket) => (
                      <Chip
                        key={`import-reason-${bucket.key}`}
                        label={`${bucket.key} · ${bucket.count}`}
                        color={selectedImportReason === bucket.key ? 'primary' : 'default'}
                        variant={selectedImportReason === bucket.key ? 'filled' : 'outlined'}
                        onClick={() => reviewImportGovernanceReason(bucket.key)}
                      />
                    ))}
                    {operations && operations.importGovernanceReasonBreakdown.length === 0 && (
                      <Typography variant="body2" color="text.secondary">
                        No governed import reasons recorded yet.
                      </Typography>
                    )}
                  </Stack>
                </Grid>
                <Grid item xs={12} md={6}>
                  <Typography variant="subtitle2" gutterBottom>
                    Top Transfer Governance Reasons
                  </Typography>
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    {(operations?.transferGovernanceReasonBreakdown ?? []).slice(0, 8).map((bucket) => (
                      <Chip
                        key={`transfer-reason-${bucket.key}`}
                        label={`${bucket.key} · ${bucket.count}`}
                        color={selectedTransferReason === bucket.key ? 'primary' : 'default'}
                        variant={selectedTransferReason === bucket.key ? 'filled' : 'outlined'}
                        onClick={() => reviewTransferGovernanceReason(bucket.key)}
                      />
                    ))}
                    {operations && operations.transferGovernanceReasonBreakdown.length === 0 && (
                      <Typography variant="body2" color="text.secondary">
                        No governed transfer reasons recorded yet.
                      </Typography>
                    )}
                  </Stack>
                </Grid>
              </Grid>

              <Grid container spacing={2}>
                <Grid item xs={12} md={6}>
                  <Stack spacing={1} sx={{ mb: 1 }}>
                    <Box>
                      <Typography variant="subtitle2" gutterBottom>
                        Recent Governed Imports
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {importJobsFilter === 'all'
                          ? `Showing all ${(operations?.recentImportJobs ?? []).length} recent governed import job(s).`
                          : `Showing ${filteredImportJobs.length} of ${(operations?.recentImportJobs ?? []).length} recent governed import job(s).`}
                      </Typography>
                    </Box>
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      <Chip
                        label={`All · ${(operations?.recentImportJobs ?? []).length}`}
                        color={importJobsFilter === 'all' ? 'primary' : 'default'}
                        variant={importJobsFilter === 'all' ? 'filled' : 'outlined'}
                        onClick={() => {
                          setImportJobsFilter('all');
                          setSelectedImportStatusBucket(null);
                        }}
                      />
                      <Chip
                        label={`Active · ${recentActiveImportJobCount}`}
                        color={importJobsFilter === 'active' ? 'info' : 'default'}
                        variant={importJobsFilter === 'active' ? 'filled' : 'outlined'}
                        onClick={() => {
                          setImportJobsFilter('active');
                          setSelectedImportStatusBucket(null);
                        }}
                      />
                      <Chip
                        label={`Failed · ${recentFailedImportJobCount}`}
                        color={importJobsFilter === 'failed' ? 'warning' : 'default'}
                        variant={importJobsFilter === 'failed' ? 'filled' : 'outlined'}
                        onClick={() => {
                          setImportJobsFilter('failed');
                          setSelectedImportStatusBucket(null);
                        }}
                      />
                      {selectedImportStatusBucket && (
                        <Chip
                          label={`Status: ${selectedImportStatusBucket}`}
                          color="info"
                          variant="filled"
                          onDelete={() => setSelectedImportStatusBucket(null)}
                        />
                      )}
                      {selectedImportReason && (
                        <Chip
                          label={`Reason: ${selectedImportReason}`}
                          color="secondary"
                          variant="filled"
                          onDelete={() => setSelectedImportReason(null)}
                        />
                      )}
                    </Stack>
                    {selectedImportStatusBucket && (
                      <Alert
                        severity="info"
                        action={(
                          <Button color="inherit" size="small" onClick={() => setSelectedImportStatusBucket(null)}>
                            Clear import status
                          </Button>
                        )}
                      >
                        {`Selected status ${selectedImportStatusBucket}. Matched ${filteredImportJobs.length} of ${(operations?.recentImportJobs ?? []).length} recent governed import job(s).`}
                      </Alert>
                    )}
                    {selectedImportReason && (
                      <Alert
                        severity="info"
                        action={(
                          <Button color="inherit" size="small" onClick={() => setSelectedImportReason(null)}>
                            Clear import reason
                          </Button>
                        )}
                      >
                        {`Selected reason ${selectedImportReason}. Matched ${filteredImportJobs.length} of ${(operations?.recentImportJobs ?? []).length} recent governed import job(s).`}
                      </Alert>
                    )}
                  </Stack>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Created</TableCell>
                        <TableCell>Target</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell>Progress</TableCell>
                        <TableCell>Reasons</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {filteredImportJobs.length === 0 ? (
                        <TableRow>
                          <TableCell colSpan={5}>
                            {(operations?.recentImportJobs?.length ?? 0) === 0
                              ? 'No governed import jobs found.'
                              : renderZeroMatchScopedAlert('import', importTotalJobs, 'Show all imports', clearImportOperationFilters)}
                          </TableCell>
                        </TableRow>
                      ) : (
                        filteredImportJobs.map((job) => (
                          <TableRow
                            key={job.jobId}
                            sx={
                              (selectedImportReason && hasGovernanceReason(job.governanceReasons, selectedImportReason))
                                || (selectedImportStatusBucket && matchesImportStatusBucket(job, selectedImportStatusBucket))
                                ? { backgroundColor: 'action.hover' }
                                : undefined
                            }
                          >
                            <TableCell>{formatDateTime(job.createdAt)}</TableCell>
                            <TableCell>{job.targetFolderPath || job.targetFolderName || '—'}</TableCell>
                            <TableCell>
                              <Chip
                                size="small"
                                label={job.status || '—'}
                                color={selectedImportStatusBucket && matchesImportStatusBucket(job, selectedImportStatusBucket) ? 'info' : 'default'}
                                variant={selectedImportStatusBucket && matchesImportStatusBucket(job, selectedImportStatusBucket) ? 'filled' : 'outlined'}
                              />
                            </TableCell>
                            <TableCell>{formatImportProgress(job)}</TableCell>
                            <TableCell>{renderGovernanceReasonChips(job.governanceReasons, selectedImportReason)}</TableCell>
                          </TableRow>
                        ))
                      )}
                    </TableBody>
                  </Table>
                </Grid>
                <Grid item xs={12} md={6}>
                  <Stack spacing={1} sx={{ mb: 1 }}>
                    <Box>
                      <Typography variant="subtitle2" gutterBottom>
                        Recent Governed Transfers
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {transferJobsFilter === 'all'
                          ? `Showing all ${(operations?.recentTransferJobs ?? []).length} recent governed transfer job(s).`
                          : `Showing ${filteredTransferJobs.length} of ${(operations?.recentTransferJobs ?? []).length} recent governed transfer job(s).`}
                      </Typography>
                    </Box>
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      <Chip
                        label={`All · ${(operations?.recentTransferJobs ?? []).length}`}
                        color={transferJobsFilter === 'all' ? 'primary' : 'default'}
                        variant={transferJobsFilter === 'all' ? 'filled' : 'outlined'}
                        onClick={() => {
                          setTransferJobsFilter('all');
                          setSelectedTransferStatusBucket(null);
                        }}
                      />
                      <Chip
                        label={`Active · ${recentActiveTransferJobCount}`}
                        color={transferJobsFilter === 'active' ? 'info' : 'default'}
                        variant={transferJobsFilter === 'active' ? 'filled' : 'outlined'}
                        onClick={() => {
                          setTransferJobsFilter('active');
                          setSelectedTransferStatusBucket(null);
                        }}
                      />
                      <Chip
                        label={`Failed · ${recentFailedTransferJobCount}`}
                        color={transferJobsFilter === 'failed' ? 'warning' : 'default'}
                        variant={transferJobsFilter === 'failed' ? 'filled' : 'outlined'}
                        onClick={() => {
                          setTransferJobsFilter('failed');
                          setSelectedTransferStatusBucket(null);
                        }}
                      />
                      {selectedTransferStatusBucket && (
                        <Chip
                          label={`Status: ${selectedTransferStatusBucket}`}
                          color="info"
                          variant="filled"
                          onDelete={() => setSelectedTransferStatusBucket(null)}
                        />
                      )}
                      {selectedTransferReason && (
                        <Chip
                          label={`Reason: ${selectedTransferReason}`}
                          color="secondary"
                          variant="filled"
                          onDelete={() => setSelectedTransferReason(null)}
                        />
                      )}
                    </Stack>
                    {selectedTransferStatusBucket && (
                      <Alert
                        severity="info"
                        action={(
                          <Button color="inherit" size="small" onClick={() => setSelectedTransferStatusBucket(null)}>
                            Clear transfer status
                          </Button>
                        )}
                      >
                        {`Selected status ${selectedTransferStatusBucket}. Matched ${filteredTransferJobs.length} of ${(operations?.recentTransferJobs ?? []).length} recent governed transfer job(s).`}
                      </Alert>
                    )}
                    {selectedTransferReason && (
                      <Alert
                        severity="info"
                        action={(
                          <Button color="inherit" size="small" onClick={() => setSelectedTransferReason(null)}>
                            Clear transfer reason
                          </Button>
                        )}
                      >
                        {`Selected reason ${selectedTransferReason}. Matched ${filteredTransferJobs.length} of ${(operations?.recentTransferJobs ?? []).length} recent governed transfer job(s).`}
                      </Alert>
                    )}
                  </Stack>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Created</TableCell>
                        <TableCell>Source</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell>Target</TableCell>
                        <TableCell>Reasons</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {filteredTransferJobs.length === 0 ? (
                        <TableRow>
                          <TableCell colSpan={5}>
                            {(operations?.recentTransferJobs?.length ?? 0) === 0
                              ? 'No governed transfer jobs found.'
                              : renderZeroMatchScopedAlert('transfer', transferTotalJobs, 'Show all transfers', clearTransferOperationFilters)}
                          </TableCell>
                        </TableRow>
                      ) : (
                        filteredTransferJobs.map((job) => (
                          <TableRow
                            key={job.jobId}
                            sx={
                              (selectedTransferReason && hasGovernanceReason(job.governanceReasons, selectedTransferReason))
                                || (selectedTransferStatusBucket && matchesTransferStatusBucket(job, selectedTransferStatusBucket))
                                ? { backgroundColor: 'action.hover' }
                                : undefined
                            }
                          >
                            <TableCell>{formatDateTime(job.createdAt)}</TableCell>
                            <TableCell>{job.sourceNodePath || job.sourceNodeName || '—'}</TableCell>
                            <TableCell>
                              <Chip
                                size="small"
                                label={formatTransferStatus(job)}
                                color={selectedTransferStatusBucket && matchesTransferStatusBucket(job, selectedTransferStatusBucket) ? 'info' : 'default'}
                                variant={selectedTransferStatusBucket && matchesTransferStatusBucket(job, selectedTransferStatusBucket) ? 'filled' : 'outlined'}
                              />
                            </TableCell>
                            <TableCell>{job.targetFolderPath || job.targetFolderName || '—'}</TableCell>
                            <TableCell>{renderGovernanceReasonChips(job.governanceReasons, selectedTransferReason)}</TableCell>
                          </TableRow>
                        ))
                      )}
                    </TableBody>
                  </Table>
                </Grid>
              </Grid>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={6}>
          <Card variant="outlined" sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                File Plans
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                {editingFilePlanId
                  ? 'Editing description only. Use the dedicated Rename and Move actions in the table for path-safe file plan changes.'
                  : 'Create new file plans here. Use the table actions to rename or re-parent existing file plans with RM-safe subtree repair.'}
              </Typography>
              <Grid container spacing={2} sx={{ mb: 2 }}>
                <Grid item xs={12}>
                  <TextField
                    fullWidth
                    label="File Plan Name"
                    value={filePlanForm.name}
                    disabled={Boolean(editingFilePlanId)}
                    onChange={(event) => setFilePlanForm((current) => ({ ...current, name: event.target.value }))}
                  />
                </Grid>
                <Grid item xs={12}>
                  <TextField
                    fullWidth
                    label="File Plan Description"
                    value={filePlanForm.description}
                    onChange={(event) => setFilePlanForm((current) => ({ ...current, description: event.target.value }))}
                  />
                </Grid>
                <Grid item xs={12}>
                  <FormControl fullWidth>
                    <InputLabel id="file-plan-parent-label">Parent File Plan</InputLabel>
                    <Select
                      labelId="file-plan-parent-label"
                      label="Parent File Plan"
                      value={filePlanForm.parentId}
                      disabled={Boolean(editingFilePlanId)}
                      onChange={(event) => setFilePlanForm((current) => ({ ...current, parentId: event.target.value }))}
                    >
                      <MenuItem value="">
                        <em>Root / Workspace</em>
                      </MenuItem>
                      {filePlans.map((filePlan) => (
                        <MenuItem key={filePlan.folderId} value={filePlan.folderId}>
                          {filePlan.path}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid item xs={12}>
                  <Stack direction="row" spacing={1}>
                    <Button
                      variant="contained"
                      onClick={() => void handleSubmitFilePlan()}
                      disabled={filePlanSubmitting}
                    >
                      {filePlanSubmitting
                        ? (editingFilePlanId ? 'Saving...' : 'Creating...')
                        : (editingFilePlanId ? 'Save File Plan' : 'Create File Plan')}
                    </Button>
                    {editingFilePlanId && (
                      <Button
                        variant="text"
                        onClick={resetFilePlanForm}
                        disabled={filePlanSubmitting}
                      >
                        Cancel
                      </Button>
                    )}
                  </Stack>
                </Grid>
              </Grid>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Name</TableCell>
                    <TableCell>Path</TableCell>
                    <TableCell>Description</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {filePlans.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={4}>No file plans yet.</TableCell>
                    </TableRow>
                  ) : (
                    filePlans.map((filePlan) => (
                      <TableRow key={filePlan.folderId}>
                        <TableCell>{filePlan.name}</TableCell>
                        <TableCell>{filePlan.path}</TableCell>
                        <TableCell>{filePlan.description || '—'}</TableCell>
                        <TableCell align="right">
                          <Stack direction="row" spacing={1} justifyContent="flex-end">
                            <Button size="small" variant="outlined" onClick={() => handleEditFilePlan(filePlan)}>
                              Edit
                            </Button>
                            <Button size="small" variant="outlined" onClick={() => handleOpenRenameFilePlan(filePlan)}>
                              Rename
                            </Button>
                            <Button size="small" variant="outlined" onClick={() => handleOpenMoveFilePlan(filePlan)}>
                              Move
                            </Button>
                            <Button
                              size="small"
                              variant="outlined"
                              color="error"
                              disabled={deletingFilePlanId === filePlan.folderId}
                              onClick={() => void handleDeleteFilePlan(filePlan)}
                            >
                              {deletingFilePlanId === filePlan.folderId ? 'Deleting...' : 'Delete'}
                            </Button>
                          </Stack>
                        </TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={6}>
          <Card variant="outlined" sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Record Categories
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                {editingCategoryId
                  ? 'Editing description only. Use the dedicated Rename and Move actions in the table for path-safe category changes.'
                  : 'Create new record categories here. Use the table actions to rename or re-parent existing categories with RM-safe path repair.'}
              </Typography>
              <Grid container spacing={2} sx={{ mb: 2 }}>
                <Grid item xs={12}>
                  <TextField
                    fullWidth
                    label="Category Name"
                    value={categoryForm.name}
                    disabled={Boolean(editingCategoryId)}
                    onChange={(event) => setCategoryForm((current) => ({ ...current, name: event.target.value }))}
                  />
                </Grid>
                <Grid item xs={12}>
                  <TextField
                    fullWidth
                    label="Category Description"
                    value={categoryForm.description}
                    onChange={(event) => setCategoryForm((current) => ({ ...current, description: event.target.value }))}
                  />
                </Grid>
                <Grid item xs={12}>
                  <FormControl fullWidth>
                    <InputLabel id="record-category-parent-label">Parent Category</InputLabel>
                    <Select
                      labelId="record-category-parent-label"
                      label="Parent Category"
                      value={categoryForm.parentId}
                      disabled={Boolean(editingCategoryId)}
                      onChange={(event) => setCategoryForm((current) => ({ ...current, parentId: event.target.value }))}
                    >
                      <MenuItem value="">
                        <em>Records Management root</em>
                      </MenuItem>
                      {categories.map((category) => (
                        <MenuItem key={category.categoryId} value={category.categoryId}>
                          {category.path}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid item xs={12}>
                  <Stack direction="row" spacing={1}>
                    <Button
                      variant="contained"
                      onClick={() => void handleSubmitCategory()}
                      disabled={categorySubmitting}
                    >
                      {categorySubmitting
                        ? (editingCategoryId ? 'Saving...' : 'Creating...')
                        : (editingCategoryId ? 'Save Category' : 'Create Category')}
                    </Button>
                    {editingCategoryId && (
                      <Button
                        variant="text"
                        onClick={resetCategoryForm}
                        disabled={categorySubmitting}
                      >
                        Cancel
                      </Button>
                    )}
                  </Stack>
                </Grid>
              </Grid>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Path</TableCell>
                    <TableCell>Description</TableCell>
                    <TableCell>Level</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {categories.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={4}>No record categories yet.</TableCell>
                    </TableRow>
                  ) : (
                    categories.map((category) => (
                      <TableRow key={category.categoryId}>
                        <TableCell>
                          <Box sx={{ pl: Math.max((category.level ?? 0) - 1, 0) * 2 }}>
                            {category.path}
                          </Box>
                        </TableCell>
                        <TableCell>{category.description || '—'}</TableCell>
                        <TableCell>{category.level ?? '—'}</TableCell>
                        <TableCell align="right">
                          {isRootRecordCategory(category) ? (
                            <Chip size="small" label="Protected" variant="outlined" />
                          ) : (
                            <Stack direction="row" spacing={1} justifyContent="flex-end">
                              <Button size="small" variant="outlined" onClick={() => handleEditCategory(category)}>
                                Edit
                              </Button>
                              <Button size="small" variant="outlined" onClick={() => handleOpenRenameCategory(category)}>
                                Rename
                              </Button>
                              <Button size="small" variant="outlined" onClick={() => handleOpenMoveCategory(category)}>
                                Move
                              </Button>
                              <Button
                                size="small"
                                variant="outlined"
                                color="error"
                                disabled={deletingCategoryId === category.categoryId}
                                onClick={() => void handleDeleteCategory(category)}
                              >
                                {deletingCategoryId === category.categoryId ? 'Deleting...' : 'Delete'}
                              </Button>
                            </Stack>
                          )}
                        </TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12}>
          <Card variant="outlined">
            <CardContent ref={declaredRecordsRef}>
              <Stack
                direction={{ xs: 'column', md: 'row' }}
                spacing={2}
                justifyContent="space-between"
                alignItems={{ xs: 'flex-start', md: 'center' }}
                sx={{ mb: 2 }}
              >
                <Box>
                  <Typography variant="h6" gutterBottom>
                    Declared Records
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {declaredRecordsFilter === 'all'
                      ? `Showing all ${records.length} declared record(s).`
                      : `Showing ${filteredRecords.length} of ${records.length} declared record(s).`}
                  </Typography>
                </Box>
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  <Chip
                    label={`All · ${records.length}`}
                    color={declaredRecordsFilter === 'all' ? 'primary' : 'default'}
                    variant={declaredRecordsFilter === 'all' ? 'filled' : 'outlined'}
                    onClick={() => setDeclaredRecordsFilter('all')}
                  />
                  <Chip
                    label={`Uncategorized · ${uncategorizedRecordCount}`}
                    color={declaredRecordsFilter === 'uncategorized' ? 'warning' : 'default'}
                    variant={declaredRecordsFilter === 'uncategorized' ? 'filled' : 'outlined'}
                    onClick={() => setDeclaredRecordsFilter('uncategorized')}
                  />
                  <Chip
                    label={`Categorized · ${categorizedRecordCount}`}
                    color={declaredRecordsFilter === 'categorized' ? 'success' : 'default'}
                    variant={declaredRecordsFilter === 'categorized' ? 'filled' : 'outlined'}
                    onClick={() => setDeclaredRecordsFilter('categorized')}
                  />
                  <Chip
                    label={`Outside File Plan · ${outsideFilePlanRecordCount}`}
                    color={declaredRecordsFilter === 'outsideFilePlan' ? 'warning' : 'default'}
                    variant={declaredRecordsFilter === 'outsideFilePlan' ? 'filled' : 'outlined'}
                    onClick={() => setDeclaredRecordsFilter('outsideFilePlan')}
                  />
                </Stack>
              </Stack>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Name</TableCell>
                    <TableCell>Path</TableCell>
                    <TableCell>File Plan Coverage</TableCell>
                    <TableCell>Declared</TableCell>
                    <TableCell>Category</TableCell>
                    <TableCell align="right">Action</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {filteredRecords.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={6}>
                        {records.length === 0
                          ? 'No declared records yet.'
                          : 'No declared records match the current filter.'}
                      </TableCell>
                    </TableRow>
                  ) : (
                    filteredRecords.map((record) => (
                      <TableRow key={record.nodeId}>
                        <TableCell>
                          <Stack direction="row" spacing={1} alignItems="center">
                            <Typography variant="body2">{record.name}</Typography>
                            <RecordStatusChip declaration={record} />
                          </Stack>
                        </TableCell>
                        <TableCell>{record.path}</TableCell>
                        <TableCell sx={{ minWidth: 220 }}>
                          {filePlanCoverage.get(record.nodeId) ? (
                            <Chip
                              size="small"
                              color="success"
                              variant="outlined"
                              label={filePlanCoverage.get(record.nodeId)?.name ?? 'Covered'}
                              title={filePlanCoverage.get(record.nodeId)?.path ?? undefined}
                            />
                          ) : (
                            <Chip size="small" color="warning" variant="outlined" label="Outside File Plan" />
                          )}
                        </TableCell>
                        <TableCell>{formatDateTime(record.declaredAt)}</TableCell>
                        <TableCell sx={{ minWidth: 260 }}>
                          <FormControl fullWidth size="small">
                            <InputLabel id={`record-category-${record.nodeId}`}>Record Category</InputLabel>
                            <Select
                              labelId={`record-category-${record.nodeId}`}
                              label="Record Category"
                              value={recordCategoryDrafts[record.nodeId] ?? ''}
                              onChange={(event) => setRecordCategoryDrafts((current) => ({
                                ...current,
                                [record.nodeId]: event.target.value,
                              }))}
                            >
                              <MenuItem value="">
                                <em>Unassigned</em>
                              </MenuItem>
                              {visibleCategoryOptions.map((category) => (
                                <MenuItem key={category.categoryId} value={category.categoryId}>
                                  {category.path}
                                </MenuItem>
                              ))}
                            </Select>
                          </FormControl>
                        </TableCell>
                        <TableCell align="right">
                          <Stack direction="row" spacing={1} justifyContent="flex-end">
                            <Button
                              size="small"
                              variant="outlined"
                              disabled={!recordCategoryDrafts[record.nodeId] || assigningRecordId === record.nodeId}
                              onClick={() => void handleAssignCategory(record)}
                            >
                              {assigningRecordId === record.nodeId ? 'Saving...' : 'Assign'}
                            </Button>
                            {isAdmin && (
                              <Button
                                size="small"
                                variant="outlined"
                                color="warning"
                                onClick={() => handleOpenUndeclare(record)}
                              >
                                Undeclare Record...
                              </Button>
                            )}
                          </Stack>
                        </TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={1.5}>
                <Box>
                  <Typography variant="h6" gutterBottom>
                    Saved RM Report Presets
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Reuse saved RM report windows through the existing audit and CSV export surfaces. Summary-only presets remain audit-only; CSV-capable presets expose export and scheduled delivery.
                  </Typography>
                </Box>

                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  <Chip
                    label={`All · ${reportPresetFilterCounts.all}`}
                    color={reportPresetTableFilter === 'all' ? 'primary' : 'default'}
                    variant={reportPresetTableFilter === 'all' ? 'filled' : 'outlined'}
                    onClick={() => setReportPresetTableFilter('all')}
                  />
                  <Chip
                    label={`Scheduled · ${reportPresetFilterCounts.scheduled}`}
                    color={reportPresetTableFilter === 'scheduled' ? 'primary' : 'default'}
                    variant={reportPresetTableFilter === 'scheduled' ? 'filled' : 'outlined'}
                    onClick={() => setReportPresetTableFilter('scheduled')}
                  />
                  <Chip
                    label={`Due now · ${reportPresetFilterCounts.dueNow}`}
                    color={reportPresetTableFilter === 'dueNow' ? 'warning' : 'default'}
                    variant={reportPresetTableFilter === 'dueNow' ? 'filled' : 'outlined'}
                    onClick={() => setReportPresetTableFilter('dueNow')}
                  />
                </Stack>

                {reportPresetsError && (
                  <Alert severity="warning">
                    {reportPresetsError}
                  </Alert>
                )}

                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Name</TableCell>
                      <TableCell>Kind</TableCell>
                      <TableCell>Delivery</TableCell>
                      <TableCell>Window</TableCell>
                      <TableCell>Description</TableCell>
                      <TableCell>Updated</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {filteredReportPresets.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={7}>
                          {reportPresetsLoading
                            ? 'Loading presets...'
                            : reportPresets.length === 0
                              ? 'No saved RM report presets.'
                              : 'No RM report presets match the current filter.'}
                        </TableCell>
                      </TableRow>
                    ) : (
                      filteredReportPresets.map((preset) => (
                        <TableRow key={preset.id}>
                          <TableCell>{preset.name}</TableCell>
                          <TableCell>{formatPresetKindLabel(preset.kind)}</TableCell>
                          <TableCell sx={{ minWidth: 180 }}>
                            <Stack spacing={0.5}>
                              <Chip
                                size="small"
                                color={
                                  preset.scheduleEnabled
                                    ? (isPresetDueNow(preset) ? 'warning' : 'success')
                                    : 'default'
                                }
                                variant="outlined"
                                label={
                                  preset.scheduleEnabled
                                    ? (isPresetDueNow(preset) ? 'Due now' : 'Scheduled')
                                    : 'Not scheduled'
                                }
                              />
                              {preset.scheduleEnabled && preset.nextRunAt && (
                                <Typography variant="caption" color="text.secondary">
                                  {`Next ${formatDateTime(preset.nextRunAt)}`}
                                </Typography>
                              )}
                              {preset.scheduleEnabled && preset.lastRunAt && (
                                <Typography variant="caption" color="text.secondary">
                                  {`Last ${formatDateTime(preset.lastRunAt)}`}
                                </Typography>
                              )}
                            </Stack>
                          </TableCell>
                          <TableCell>{formatPresetRangeLabel(preset)}</TableCell>
                          <TableCell>{preset.description || '—'}</TableCell>
                          <TableCell>{formatDateTime(preset.lastModifiedDate || preset.createdDate)}</TableCell>
                          <TableCell align="right">
                            <Stack direction="row" spacing={1} justifyContent="flex-end">
                              <Button
                                size="small"
                                variant="outlined"
                                onClick={() => openEditReportPresetDraft(preset)}
                              >
                                Edit
                              </Button>
                              <Button
                                size="small"
                                variant="outlined"
                                color="warning"
                                onClick={() => void deleteReportPreset(preset)}
                                disabled={presetDeletingId === preset.id}
                              >
                                {presetDeletingId === preset.id ? 'Deleting...' : 'Delete'}
                              </Button>
                              <Button
                                size="small"
                                variant="outlined"
                                onClick={() => applyReportPresetToAudit(preset)}
                              >
                                Apply to audit
                              </Button>
                              {supportsReportPresetCsvDelivery(preset.kind) && (
                                <>
                                  <Button
                                    size="small"
                                    variant="outlined"
                                    onClick={() => void exportReportPresetCsv(preset)}
                                    disabled={presetExportingId === preset.id}
                                  >
                                    {presetExportingId === preset.id ? 'Exporting...' : 'Export CSV'}
                                  </Button>
                                  <Button
                                    size="small"
                                    variant="outlined"
                                    onClick={() => setSchedulePresetTarget(preset)}
                                  >
                                    Schedule
                                  </Button>
                                </>
                              )}
                            </Stack>
                          </TableCell>
                        </TableRow>
                      ))
                    )}
                  </TableBody>
                </Table>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={1.5}>
                <Box>
                  <Typography variant="h6" gutterBottom>
                    Scheduled Delivery Health
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    At-a-glance view of how many RM report presets are on a schedule and how recent deliveries have performed.
                  </Typography>
                </Box>
                {scheduledDeliveryTelemetryError ? (
                  <Typography variant="body2" color="error">
                    {scheduledDeliveryTelemetryError}
                  </Typography>
                ) : scheduledDeliveryTelemetryLoading && !scheduledDeliveryTelemetry ? (
                  <Typography variant="body2" color="text.secondary">
                    Loading scheduled delivery health...
                  </Typography>
                ) : scheduledDeliveryTelemetry ? (
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    <Chip
                      size="small"
                      color="primary"
                      variant="outlined"
                      label={`Scheduled presets: ${scheduledDeliveryTelemetry.scheduleEnabledCount}`}
                      onClick={() => applyReportPresetTableFilter('scheduled')}
                    />
                    <Chip
                      size="small"
                      color={scheduledDeliveryTelemetry.duePresetCount > 0 ? 'warning' : 'default'}
                      variant="outlined"
                      label={`Due now: ${scheduledDeliveryTelemetry.duePresetCount}`}
                      onClick={() => applyReportPresetTableFilter('dueNow')}
                    />
                    <Chip
                      size="small"
                      color="success"
                      variant="outlined"
                      label={`Last 24h success: ${scheduledDeliveryTelemetry.last24hSuccessCount}`}
                    />
                    <Chip
                      size="small"
                      color={scheduledDeliveryTelemetry.last24hFailedCount > 0 ? 'error' : 'default'}
                      variant="outlined"
                      label={`Last 24h failed: ${scheduledDeliveryTelemetry.last24hFailedCount}`}
                    />
                    {scheduledDeliveryTelemetry.lastExecutionAt && (
                      <Chip
                        size="small"
                        variant="outlined"
                        label={`Last delivery: ${formatDateTime(scheduledDeliveryTelemetry.lastExecutionAt)}`}
                      />
                    )}
                  </Stack>
                ) : (
                  <Typography variant="body2" color="text.secondary">
                    No scheduled delivery activity yet.
                  </Typography>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={1.5}>
                <Box>
                  <Typography variant="h6" gutterBottom>
                    Preset Delivery Ledger
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Review delivery executions across all RM report presets, then export the filtered ledger or jump back to delivered evidence and the existing audit surface.
                  </Typography>
                </Box>

                <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} flexWrap="wrap" useFlexGap>
                  <TextField
                    select
                    size="small"
                    label="Preset"
                    value={presetExecutionLedgerFilters.presetId}
                    onChange={(event) => setPresetExecutionLedgerFilters((current) => ({
                      ...current,
                      presetId: event.target.value,
                    }))}
                    sx={{ minWidth: 220 }}
                  >
                    <MenuItem value="">
                      <em>All Presets</em>
                    </MenuItem>
                    {reportPresets.map((preset) => (
                      <MenuItem key={preset.id} value={preset.id}>
                        {preset.name}
                      </MenuItem>
                    ))}
                  </TextField>
                  <TextField
                    select
                    size="small"
                    label="Result"
                    value={presetExecutionLedgerFilters.status}
                    onChange={(event) => setPresetExecutionLedgerFilters((current) => ({
                      ...current,
                      status: event.target.value as PresetExecutionLedgerFilterState['status'],
                    }))}
                    sx={{ minWidth: 160 }}
                  >
                    <MenuItem value="">
                      <em>All Results</em>
                    </MenuItem>
                    <MenuItem value="SUCCESS">Successful</MenuItem>
                    <MenuItem value="FAILED">Failed</MenuItem>
                  </TextField>
                  <TextField
                    select
                    size="small"
                    label="Trigger"
                    value={presetExecutionLedgerFilters.triggerType}
                    onChange={(event) => setPresetExecutionLedgerFilters((current) => ({
                      ...current,
                      triggerType: event.target.value as PresetExecutionLedgerFilterState['triggerType'],
                    }))}
                    sx={{ minWidth: 160 }}
                  >
                    <MenuItem value="">
                      <em>All Triggers</em>
                    </MenuItem>
                    <MenuItem value="MANUAL">Manual</MenuItem>
                    <MenuItem value="SCHEDULED">Scheduled</MenuItem>
                  </TextField>
                  <TextField
                    size="small"
                    label="From"
                    type="datetime-local"
                    value={presetExecutionLedgerFilters.from}
                    onChange={(event) => setPresetExecutionLedgerFilters((current) => ({
                      ...current,
                      from: event.target.value,
                    }))}
                    InputLabelProps={{ shrink: true }}
                    inputProps={{ step: 1 }}
                  />
                  <TextField
                    size="small"
                    label="To"
                    type="datetime-local"
                    value={presetExecutionLedgerFilters.to}
                    onChange={(event) => setPresetExecutionLedgerFilters((current) => ({
                      ...current,
                      to: event.target.value,
                    }))}
                    InputLabelProps={{ shrink: true }}
                    inputProps={{ step: 1 }}
                  />
                  <Button
                    variant="contained"
                    onClick={handleApplyPresetExecutionLedgerFilters}
                    disabled={presetExecutionLedgerLoading}
                  >
                    Apply
                  </Button>
                  <Button
                    variant="text"
                    onClick={handleClearPresetExecutionLedgerFilters}
                    disabled={presetExecutionLedgerLoading}
                  >
                    Clear
                  </Button>
                  <Button
                    variant="outlined"
                    onClick={() => void exportPresetExecutionLedgerCsv()}
                    disabled={presetExecutionLedgerExporting}
                  >
                    {presetExecutionLedgerExporting ? 'Exporting...' : 'Export ledger CSV'}
                  </Button>
                </Stack>

                {presetExecutionLedgerError && (
                  <Alert severity="warning">
                    {presetExecutionLedgerError}
                  </Alert>
                )}

                {hasAppliedPresetExecutionLedgerFilters && (
                  <Stack
                    direction={{ xs: 'column', md: 'row' }}
                    spacing={1}
                    justifyContent="space-between"
                    alignItems={{ xs: 'flex-start', md: 'center' }}
                  >
                    <Stack spacing={1}>
                      <Typography variant="body2" color="text.secondary">
                        Active ledger filters
                      </Typography>
                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        {presetExecutionLedgerFilterChips.map((label) => (
                          <Chip key={label} size="small" variant="outlined" label={label} />
                        ))}
                      </Stack>
                    </Stack>
                    <Button
                      size="small"
                      variant="text"
                      onClick={handleClearPresetExecutionLedgerFilters}
                      disabled={presetExecutionLedgerLoading}
                    >
                      Clear applied filters
                    </Button>
                  </Stack>
                )}

                <Typography variant="body2" color="text.secondary">
                  {`Showing ${presetExecutionLedgerPage.content.length} of ${presetExecutionLedgerPage.totalElements} deliveries`}
                </Typography>

                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Started</TableCell>
                      <TableCell>Preset</TableCell>
                      <TableCell>Trigger</TableCell>
                      <TableCell>Result</TableCell>
                      <TableCell>File</TableCell>
                      <TableCell>Message</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {presetExecutionLedgerPage.content.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={7}>
                          {presetExecutionLedgerLoading ? (
                            'Loading preset delivery ledger...'
                          ) : hasAppliedPresetExecutionLedgerFilters ? (
                            <Stack
                              direction={{ xs: 'column', md: 'row' }}
                              spacing={1}
                              justifyContent="space-between"
                              alignItems={{ xs: 'flex-start', md: 'center' }}
                            >
                              <Typography variant="body2">
                                No deliveries match the current filters.
                              </Typography>
                              <Button
                                size="small"
                                variant="text"
                                onClick={handleClearPresetExecutionLedgerFilters}
                              >
                                Show all deliveries
                              </Button>
                            </Stack>
                          ) : 'No preset deliveries found.'}
                        </TableCell>
                      </TableRow>
                    ) : (
                      presetExecutionLedgerPage.content.map((execution) => {
                        const preset = reportPresetById.get(execution.presetId);
                        const presetKind = execution.presetKind ?? preset?.kind;
                        return (
                          <TableRow key={execution.id}>
                            <TableCell>{formatDateTime(execution.startedAt)}</TableCell>
                            <TableCell>
                              <Stack spacing={0.5}>
                                <Typography variant="body2">
                                  {execution.presetName || preset?.name || execution.presetId}
                                </Typography>
                                {presetKind && (
                                  <Typography variant="caption" color="text.secondary">
                                    {formatPresetKindLabel(presetKind)}
                                  </Typography>
                                )}
                              </Stack>
                            </TableCell>
                            <TableCell>{formatPresetExecutionTriggerLabel(execution.triggerType)}</TableCell>
                            <TableCell>
                              <Chip
                                size="small"
                                label={formatPresetExecutionStatusLabel(execution.status)}
                                color={execution.status === 'SUCCESS' ? 'success' : 'warning'}
                                variant="outlined"
                              />
                            </TableCell>
                            <TableCell>{execution.filename || '—'}</TableCell>
                            <TableCell>{execution.message || '—'}</TableCell>
                            <TableCell align="right">
                              <Stack direction="row" spacing={1} justifyContent="flex-end">
                                {preset && (
                                  <Button
                                    size="small"
                                    variant="outlined"
                                    onClick={() => applyReportPresetToAudit(preset)}
                                  >
                                    Apply preset
                                  </Button>
                                )}
                                {execution.documentId && (
                                  <Button
                                    size="small"
                                    variant="outlined"
                                    onClick={() => openBrowseTarget(execution.documentId)}
                                  >
                                    Open delivered file
                                  </Button>
                                )}
                                {execution.targetFolderId && (
                                  <Button
                                    size="small"
                                    variant="outlined"
                                    onClick={() => openBrowseTarget(execution.targetFolderId)}
                                  >
                                    Open target folder
                                  </Button>
                                )}
                              </Stack>
                            </TableCell>
                          </TableRow>
                        );
                      })
                    )}
                  </TableBody>
                </Table>
                <TablePagination
                  component="div"
                  count={presetExecutionLedgerPage.totalElements}
                  page={presetExecutionLedgerPage.number}
                  onPageChange={(_, nextPage) => setPresetExecutionLedgerPageIndex(nextPage)}
                  rowsPerPage={presetExecutionLedgerRowsPerPage}
                  onRowsPerPageChange={(event) => {
                    const nextRows = Number(event.target.value);
                    setPresetExecutionLedgerRowsPerPage(nextRows);
                    setPresetExecutionLedgerPageIndex(0);
                  }}
                  rowsPerPageOptions={[10, 25, 50]}
                />
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} ref={auditRef}>
          <Card variant="outlined">
            <CardContent>
              <Stack
                direction={{ xs: 'column', md: 'row' }}
                spacing={2}
                justifyContent="space-between"
                alignItems={{ xs: 'flex-start', md: 'center' }}
                sx={{ mb: 2 }}
              >
                <Typography variant="h6">Records Audit</Typography>
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={1}>
                  <TextField
                    select
                    size="small"
                    label="Family"
                    value={auditFilters.family}
                    onChange={(event) => setAuditFilters((current) => ({ ...current, family: event.target.value }))}
                    sx={{ minWidth: 180 }}
                  >
                    <MenuItem value="">
                      <em>All Families</em>
                    </MenuItem>
                    {AUDIT_FAMILY_OPTIONS.map((option) => (
                      <MenuItem key={option.value} value={option.value}>
                        {option.label}
                      </MenuItem>
                    ))}
                  </TextField>
                  <TextField
                    size="small"
                    label="Event Type"
                    value={auditFilters.eventType}
                    onChange={(event) => setAuditFilters((current) => ({ ...current, eventType: event.target.value }))}
                    placeholder="RM_RECORD_DECLARED"
                  />
                  <TextField
                    size="small"
                    label="Username"
                    value={auditFilters.username}
                    onChange={(event) => setAuditFilters((current) => ({ ...current, username: event.target.value }))}
                  />
                  <TextField
                    size="small"
                    label="From"
                    type="datetime-local"
                    value={auditFilters.from}
                    onChange={(event) => setAuditFilters((current) => ({ ...current, from: event.target.value }))}
                    InputLabelProps={{ shrink: true }}
                    inputProps={{ step: 1 }}
                  />
                  <TextField
                    size="small"
                    label="To"
                    type="datetime-local"
                    value={auditFilters.to}
                    onChange={(event) => setAuditFilters((current) => ({ ...current, to: event.target.value }))}
                    InputLabelProps={{ shrink: true }}
                    inputProps={{ step: 1 }}
                  />
                  <Button variant="contained" onClick={handleApplyAuditFilters} disabled={auditLoading}>
                    Apply
                  </Button>
                  <Button variant="text" onClick={handleClearAuditFilters} disabled={auditLoading}>
                    Clear
                  </Button>
                </Stack>
              </Stack>
              {auditDrilldown && (
                <Alert
                  severity="info"
                  sx={{ mb: 2 }}
                  action={(
                    <Button color="inherit" size="small" onClick={clearAuditDrilldown}>
                      Clear audit drilldown
                    </Button>
                  )}
                >
                  {`Reviewing audit evidence for ${auditDrilldown.label} from ${formatDateTime(auditDrilldown.from)} to ${formatDateTime(auditDrilldown.to)}.`}
                </Alert>
              )}
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>When</TableCell>
                    <TableCell>Event</TableCell>
                    <TableCell>Node</TableCell>
                    <TableCell>User</TableCell>
                    <TableCell>Details</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {auditPage.content.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={5}>{auditLoading ? 'Loading audit...' : 'No audit entries found.'}</TableCell>
                    </TableRow>
                  ) : (
                    auditPage.content.map((entry) => (
                      <TableRow key={entry.auditLogId}>
                        <TableCell>{formatDateTime(entry.eventTime)}</TableCell>
                        <TableCell>{entry.eventType}</TableCell>
                        <TableCell>{entry.nodeName || '—'}</TableCell>
                        <TableCell>{entry.username || '—'}</TableCell>
                        <TableCell>{entry.details || '—'}</TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
              <TablePagination
                component="div"
                count={auditPage.totalElements}
                page={auditPage.number}
                onPageChange={(_, nextPage) => setAuditPageIndex(nextPage)}
                rowsPerPage={auditRowsPerPage}
                onRowsPerPageChange={(event) => {
                  const nextRows = Number(event.target.value);
                  setAuditRowsPerPage(nextRows);
                  setAuditPageIndex(0);
                }}
                rowsPerPageOptions={[10, 25, 50]}
              />
            </CardContent>
          </Card>
      </Grid>
    </Grid>

      <SaveReportPresetDialog
        open={Boolean(reportPresetDraft)}
        title={reportPresetDraft?.title ?? 'Save RM Report Preset'}
        helperText={reportPresetDraft?.helperText ?? 'Save this report configuration as a reusable RM report preset.'}
        initialName={reportPresetDraft?.name ?? ''}
        initialDescription={reportPresetDraft?.description}
        submitLabel={reportPresetDraft?.submitLabel}
        submitting={reportPresetSubmitting}
        onClose={closeReportPresetDialog}
        onSave={saveReportPreset}
      />
      <ScheduleReportPresetDialog
        open={Boolean(schedulePresetTarget)}
        preset={schedulePresetTarget}
        onClose={() => setSchedulePresetTarget(null)}
        onChanged={refreshPresetDeliverySurfaces}
      />
      <RenameFilePlanDialog
        open={renameFilePlanDialogOpen}
        filePlan={renameFilePlanTarget}
        onClose={() => {
          setRenameFilePlanDialogOpen(false);
          setRenameFilePlanTarget(null);
        }}
        onRenamed={(updated) => handleFilePlanRenamed(updated)}
      />
      <MoveFilePlanDialog
        open={moveFilePlanDialogOpen}
        filePlan={moveFilePlanTarget}
        filePlans={filePlans}
        onClose={() => {
          setMoveFilePlanDialogOpen(false);
          setMoveFilePlanTarget(null);
        }}
        onMoved={(updated) => handleFilePlanMoved(updated)}
      />
      <UndeclareRecordDialog
        open={undeclareDialogOpen}
        nodeId={undeclareTarget?.nodeId}
        nodeName={undeclareTarget?.name ?? ''}
        onClose={() => {
          setUndeclareDialogOpen(false);
          setUndeclareTarget(null);
        }}
        onUndeclared={() => {
          void loadAdminData(true);
        }}
      />
      <RenameRecordCategoryDialog
        open={renameCategoryDialogOpen}
        category={renameCategoryTarget}
        onClose={() => {
          setRenameCategoryDialogOpen(false);
          setRenameCategoryTarget(null);
        }}
        onRenamed={(updated) => handleCategoryRenamed(updated)}
      />
      <MoveRecordCategoryDialog
        open={moveCategoryDialogOpen}
        category={moveCategoryTarget}
        categories={categories}
        onClose={() => {
          setMoveCategoryDialogOpen(false);
          setMoveCategoryTarget(null);
        }}
        onMoved={(updated) => handleCategoryMoved(updated)}
      />
    </Box>
  );
};

export default RecordsManagementPage;
