import React, { useState, useEffect, useCallback, useMemo } from 'react';
import {
  Alert,
  Autocomplete,
  Avatar,
  Box,
  Typography,
  Paper,
  Grid,
  List,
  ListItem,
  ListItemButton,
  ListItemText,
  Button,
  CircularProgress,
  Divider,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  IconButton,
  InputAdornment,
  MenuItem,
  TextField,
  Chip,
  Stack,
  ButtonBase,
} from '@mui/material';
import { ChatBubbleOutline, CheckCircle, Cancel, Clear, FolderOpen, Search, Visibility } from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';
import { Node, User } from 'types';
import workflowService, {
  Task,
  TaskInboxScope,
  WorkflowDefinitionDetail,
  WorkflowBusinessItem,
  WorkflowInvolvedActor,
  WorkflowProcessActivity,
  WorkflowProcessBrowserItem,
  WorkflowProcessDetail,
  WorkflowProcessListStatus,
  WorkflowProcessTask,
  WorkflowTaskCandidate,
  WorkflowDefinitionModel,
  WorkflowFormModelElement,
  WorkflowHistoricTaskItem,
  WorkflowHistoryItem,
  WorkflowSubmissionSummary,
  WorkflowVariableItem,
  WorkflowTaskDetail,
} from '../services/workflowService';
import nodeService from '../services/nodeService';
import peopleService from '../services/peopleService';
import authService from '../services/authService';
import { toast } from 'react-toastify';
import { format } from 'date-fns';

const DocumentPreview = React.lazy(() => import('components/preview/DocumentPreview'));
const PROCESS_BROWSER_PAGE_SIZE = 5;
type WorkflowHistoryQuickScope = 'ALL' | 'RUNNING' | 'ENDED' | 'APPROVED' | 'REJECTED' | 'COMMENTED' | 'REVIEWED';
type WorkflowVariableQuickScope = 'ALL' | 'STRING' | 'NUMBER' | 'BOOLEAN' | 'STRUCTURED' | 'OTHER';
type WorkflowActivityQuickScope = 'ALL' | 'RUNNING' | 'ENDED' | 'HUMAN' | 'SYSTEM';
type WorkflowTaskHistoryQuickScope = 'ALL' | 'ASSIGNED' | 'UNASSIGNED' | 'OWNED' | 'OUTCOME';

const formatTimestamp = (value?: string | null) => {
  if (!value) {
    return 'N/A';
  }
  try {
    return format(new Date(value), 'PPp');
  } catch {
    return value;
  }
};

const formatWorkflowValue = (value: unknown) => {
  if (value === null || value === undefined) {
    return 'N/A';
  }
  if (typeof value === 'string') {
    return value;
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value);
  }
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
};

const matchesWorkflowHistoryQuickScope = (
  item: WorkflowHistoryItem,
  scope: WorkflowHistoryQuickScope,
) => {
  if (scope === 'RUNNING') {
    return item.ended === false;
  }
  if (scope === 'ENDED') {
    return item.ended !== false;
  }
  if (scope === 'APPROVED') {
    return item.decision === 'APPROVED';
  }
  if (scope === 'REJECTED') {
    return item.decision === 'REJECTED';
  }
  if (scope === 'COMMENTED') {
    return Boolean(item.startComment || item.comment);
  }
  if (scope === 'REVIEWED') {
    return Boolean(item.reviewedBy || item.reviewedAt);
  }
  return true;
};

const getWorkflowVariableQuickScope = (
  type?: string | null,
): Exclude<WorkflowVariableQuickScope, 'ALL'> => {
  const normalizedType = type?.trim().toLowerCase() || '';
  if (/(string|char|text|uuid)/.test(normalizedType)) {
    return 'STRING';
  }
  if (/(int|long|double|float|number|decimal|bigdecimal|biginteger)/.test(normalizedType)) {
    return 'NUMBER';
  }
  if (/(bool)/.test(normalizedType)) {
    return 'BOOLEAN';
  }
  if (/(list|array|map|json|object|set)/.test(normalizedType)) {
    return 'STRUCTURED';
  }
  return 'OTHER';
};

const matchesWorkflowVariableQuickScope = (
  variable: WorkflowVariableItem,
  scope: WorkflowVariableQuickScope,
) => scope === 'ALL' || getWorkflowVariableQuickScope(variable.type) === scope;

const matchesWorkflowActivityQuickScope = (
  activity: WorkflowProcessActivity,
  scope: WorkflowActivityQuickScope,
): boolean => {
  if (scope === 'RUNNING') {
    return !activity.endTime;
  }
  if (scope === 'ENDED') {
    return Boolean(activity.endTime);
  }
  if (scope === 'HUMAN') {
    return Boolean(
      activity.taskId
      || activity.assignee
      || activity.activityType?.toLowerCase().includes('user')
    );
  }
  if (scope === 'SYSTEM') {
    return !matchesWorkflowActivityQuickScope(activity, 'HUMAN');
  }
  return true;
};

const matchesWorkflowTaskHistoryQuickScope = (
  item: WorkflowHistoricTaskItem,
  scope: WorkflowTaskHistoryQuickScope,
): boolean => {
  if (scope === 'ASSIGNED') {
    return Boolean(item.assignee);
  }
  if (scope === 'UNASSIGNED') {
    return !item.assignee;
  }
  if (scope === 'OWNED') {
    return Boolean(item.owner);
  }
  if (scope === 'OUTCOME') {
    return Boolean(item.deleteReason);
  }
  return true;
};

const formatPersonDisplay = (person: User): string => {
  const fullName = [person.firstName, person.lastName].filter(Boolean).join(' ').trim();
  if (fullName) {
    return `${person.username} (${fullName})`;
  }
  return person.username;
};

const TasksPage: React.FC = () => {
  const navigate = useNavigate();
  const currentUser = authService.getCurrentUser();
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(true);
  const [taskScope, setTaskScope] = useState<TaskInboxScope>('my');
  const [taskSearchInput, setTaskSearchInput] = useState('');
  const [taskSearchQuery, setTaskSearchQuery] = useState('');
  const [taskProcessIdInput, setTaskProcessIdInput] = useState('');
  const [taskProcessIdQuery, setTaskProcessIdQuery] = useState('');
  const [taskOwnerInput, setTaskOwnerInput] = useState('');
  const [taskOwnerQuery, setTaskOwnerQuery] = useState('');
  const [taskCandidateUserInput, setTaskCandidateUserInput] = useState('');
  const [taskCandidateUserQuery, setTaskCandidateUserQuery] = useState('');
  const [taskCandidateGroupInput, setTaskCandidateGroupInput] = useState('');
  const [taskCandidateGroupQuery, setTaskCandidateGroupQuery] = useState('');
  const [processBrowserStatus, setProcessBrowserStatus] = useState<WorkflowProcessListStatus>('ACTIVE');
  const [processBrowserSearchInput, setProcessBrowserSearchInput] = useState('');
  const [processBrowserSearchQuery, setProcessBrowserSearchQuery] = useState('');
  const [processBrowserStartedByInput, setProcessBrowserStartedByInput] = useState('');
  const [processBrowserStartedByQuery, setProcessBrowserStartedByQuery] = useState('');
  const [processBrowserDefinitionKeyInput, setProcessBrowserDefinitionKeyInput] = useState('');
  const [processBrowserDefinitionKeyQuery, setProcessBrowserDefinitionKeyQuery] = useState('');
  const [processBrowserPage, setProcessBrowserPage] = useState(0);
  const [processBrowserItems, setProcessBrowserItems] = useState<WorkflowProcessBrowserItem[]>([]);
  const [processBrowserTotal, setProcessBrowserTotal] = useState(0);
  const [processBrowserHasMore, setProcessBrowserHasMore] = useState(false);
  const [processBrowserLoading, setProcessBrowserLoading] = useState(false);
  const [selectedTask, setSelectedTask] = useState<Task | null>(null);
  const [taskDetail, setTaskDetail] = useState<WorkflowTaskDetail | null>(null);
  const [processDetail, setProcessDetail] = useState<WorkflowProcessDetail | null>(null);
  const [processTasks, setProcessTasks] = useState<WorkflowProcessTask[]>([]);
  const [processTaskHistory, setProcessTaskHistory] = useState<WorkflowHistoricTaskItem[]>([]);
  const [taskHistorySearchInput, setTaskHistorySearchInput] = useState('');
  const [taskHistorySearchQuery, setTaskHistorySearchQuery] = useState('');
  const [taskHistoryAssigneeInput, setTaskHistoryAssigneeInput] = useState('');
  const [taskHistoryAssigneeQuery, setTaskHistoryAssigneeQuery] = useState('');
  const [taskHistoryDefinitionInput, setTaskHistoryDefinitionInput] = useState('');
  const [taskHistoryDefinitionQuery, setTaskHistoryDefinitionQuery] = useState('');
  const [taskHistoryQuickScope, setTaskHistoryQuickScope] = useState<WorkflowTaskHistoryQuickScope>('ALL');
  const [activitySearchInput, setActivitySearchInput] = useState('');
  const [activitySearchQuery, setActivitySearchQuery] = useState('');
  const [activityAssigneeInput, setActivityAssigneeInput] = useState('');
  const [activityAssigneeQuery, setActivityAssigneeQuery] = useState('');
  const [activityTypeInput, setActivityTypeInput] = useState('');
  const [activityTypeQuery, setActivityTypeQuery] = useState('');
  const [workflowHistorySearchInput, setWorkflowHistorySearchInput] = useState('');
  const [workflowHistorySearchQuery, setWorkflowHistorySearchQuery] = useState('');
  const [processActivities, setProcessActivities] = useState<WorkflowProcessActivity[]>([]);
  const [activityQuickScope, setActivityQuickScope] = useState<WorkflowActivityQuickScope>('ALL');
  const [processInvolvedActors, setProcessInvolvedActors] = useState<WorkflowInvolvedActor[]>([]);
  const [processVariables, setProcessVariables] = useState<WorkflowVariableItem[]>([]);
  const [processVariableSearchInput, setProcessVariableSearchInput] = useState('');
  const [processVariableSearchQuery, setProcessVariableSearchQuery] = useState('');
  const [processItems, setProcessItems] = useState<WorkflowBusinessItem[]>([]);
  const [taskVariables, setTaskVariables] = useState<WorkflowVariableItem[]>([]);
  const [taskVariableSearchInput, setTaskVariableSearchInput] = useState('');
  const [taskVariableSearchQuery, setTaskVariableSearchQuery] = useState('');
  const [taskItems, setTaskItems] = useState<WorkflowBusinessItem[]>([]);
  const [taskCandidates, setTaskCandidates] = useState<WorkflowTaskCandidate[]>([]);
  const [taskInvolvedActors, setTaskInvolvedActors] = useState<WorkflowInvolvedActor[]>([]);
  const [taskFormModel, setTaskFormModel] = useState<WorkflowFormModelElement[]>([]);
  const [definitionDetail, setDefinitionDetail] = useState<WorkflowDefinitionDetail | null>(null);
  const [definitionModel, setDefinitionModel] = useState<WorkflowDefinitionModel | null>(null);
  const [definitionDiagramUrl, setDefinitionDiagramUrl] = useState<string | null>(null);
  const [definitionDiagramError, setDefinitionDiagramError] = useState<string | null>(null);
  const [workflowHistory, setWorkflowHistory] = useState<WorkflowHistoryItem[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailRefreshToken, setDetailRefreshToken] = useState(0);
  const [workflowHistoryQuickScope, setWorkflowHistoryQuickScope] = useState<WorkflowHistoryQuickScope>('ALL');
  const [processVariableQuickScope, setProcessVariableQuickScope] = useState<WorkflowVariableQuickScope>('ALL');
  const [taskVariableQuickScope, setTaskVariableQuickScope] = useState<WorkflowVariableQuickScope>('ALL');
  const [actionDialog, setActionDialog] = useState<
    'approve' | 'reject' | 'cancelProcess' | 'assignTask' | 'delegateTask' | 'resolveTask' | null
  >(null);
  const [comment, setComment] = useState('');
  const [assignmentUsername, setAssignmentUsername] = useState('');
  const [assignmentOptions, setAssignmentOptions] = useState<User[]>([]);
  const [assignmentOptionsLoading, setAssignmentOptionsLoading] = useState(false);
  const [showModelXml, setShowModelXml] = useState(false);
  const [previewNode, setPreviewNode] = useState<Node | null>(null);
  const [previewCommentsOpen, setPreviewCommentsOpen] = useState(false);
  const [previewCommentDraftText, setPreviewCommentDraftText] = useState<string | null>(null);
  const [previewLoadingItemId, setPreviewLoadingItemId] = useState<string | null>(null);
  const [taskLifecycleLoadingId, setTaskLifecycleLoadingId] = useState<string | null>(null);
  const [processCancelLoading, setProcessCancelLoading] = useState(false);
  const [taskAssignLoading, setTaskAssignLoading] = useState(false);
  const [processVariableEditorOpen, setProcessVariableEditorOpen] = useState(false);
  const [processVariableName, setProcessVariableName] = useState('');
  const [processVariableValue, setProcessVariableValue] = useState('');
  const [processVariableSaving, setProcessVariableSaving] = useState(false);
  const [processVariableDeletingName, setProcessVariableDeletingName] = useState<string | null>(null);
  const selectedTaskRef = React.useRef<Task | null>(null);
  const inboxRequestIdRef = React.useRef(0);
  const selectedTaskCompleted = selectedTask?.status === 'COMPLETED';
  const canEditProcessVariables = Boolean(
    processDetail
    && !processDetail.ended
    && (
      currentUser?.roles?.includes('ROLE_ADMIN')
      || (currentUser?.username && processDetail.startedBy && currentUser.username === processDetail.startedBy)
    )
  );

  const filteredProcessTaskHistory = useMemo(() => {
    const query = taskHistorySearchQuery.trim().toLowerCase();
    return processTaskHistory.filter((task) =>
      matchesWorkflowTaskHistoryQuickScope(task, taskHistoryQuickScope)
      && (
        !query
        || [task.name, task.assignee, task.owner, task.taskDefinitionKey, task.deleteReason]
          .filter(Boolean)
          .some((value) => String(value).toLowerCase().includes(query))
      )
    );
  }, [processTaskHistory, taskHistoryQuickScope, taskHistorySearchQuery]);

  const processTaskHistoryScopeCounts = useMemo(() => ({
    ALL: processTaskHistory.length,
    ASSIGNED: processTaskHistory.filter((task) => matchesWorkflowTaskHistoryQuickScope(task, 'ASSIGNED')).length,
    UNASSIGNED: processTaskHistory.filter((task) => matchesWorkflowTaskHistoryQuickScope(task, 'UNASSIGNED')).length,
    OWNED: processTaskHistory.filter((task) => matchesWorkflowTaskHistoryQuickScope(task, 'OWNED')).length,
    OUTCOME: processTaskHistory.filter((task) => matchesWorkflowTaskHistoryQuickScope(task, 'OUTCOME')).length,
  }), [processTaskHistory]);

  const filteredProcessActivities = useMemo(
    () => {
      const query = activitySearchQuery.trim().toLowerCase();
      const assignee = activityAssigneeQuery.trim().toLowerCase();
      const activityType = activityTypeQuery.trim().toLowerCase();
      return processActivities.filter((activity) =>
        matchesWorkflowActivityQuickScope(activity, activityQuickScope)
        && (
          !query
          || [
            activity.activityName,
            activity.activityId,
            activity.activityType,
            activity.executionId,
            activity.taskId,
            activity.assignee,
          ]
            .filter(Boolean)
            .some((value) => String(value).toLowerCase().includes(query))
        )
        && (
          !assignee
          || String(activity.assignee || '').toLowerCase() === assignee
        )
        && (
          !activityType
          || String(activity.activityType || '').toLowerCase() === activityType
        )
      );
    },
    [activityAssigneeQuery, activityQuickScope, activitySearchQuery, activityTypeQuery, processActivities],
  );

  const processActivityScopeCounts = useMemo(() => ({
    ALL: processActivities.length,
    RUNNING: processActivities.filter((activity) => matchesWorkflowActivityQuickScope(activity, 'RUNNING')).length,
    ENDED: processActivities.filter((activity) => matchesWorkflowActivityQuickScope(activity, 'ENDED')).length,
    HUMAN: processActivities.filter((activity) => matchesWorkflowActivityQuickScope(activity, 'HUMAN')).length,
    SYSTEM: processActivities.filter((activity) => matchesWorkflowActivityQuickScope(activity, 'SYSTEM')).length,
  }), [processActivities]);

  const activityAssigneeSuggestions = useMemo(() => {
    const suggestions = new Map<string, { value: string; count: number }>();
    processActivities.forEach((activity) => {
      const value = activity.assignee?.trim();
      if (!value) {
        return;
      }
      const key = value.toLowerCase();
      const existing = suggestions.get(key);
      if (existing) {
        existing.count += 1;
        return;
      }
      suggestions.set(key, { value, count: 1 });
    });
    return Array.from(suggestions.values()).sort((left, right) => left.value.localeCompare(right.value));
  }, [processActivities]);

  const activityTypeSuggestions = useMemo(() => {
    const suggestions = new Map<string, { value: string; count: number }>();
    processActivities.forEach((activity) => {
      const value = activity.activityType?.trim();
      if (!value) {
        return;
      }
      const key = value.toLowerCase();
      const existing = suggestions.get(key);
      if (existing) {
        existing.count += 1;
        return;
      }
      suggestions.set(key, { value, count: 1 });
    });
    return Array.from(suggestions.values()).sort((left, right) => left.value.localeCompare(right.value));
  }, [processActivities]);

  const filteredProcessVariables = useMemo(() => {
    const query = processVariableSearchQuery.trim().toLowerCase();
    return processVariables.filter((variable) =>
      matchesWorkflowVariableQuickScope(variable, processVariableQuickScope)
      && (
        !query
        || [variable.name, variable.type, variable.scope, formatWorkflowValue(variable.value)]
          .filter(Boolean)
          .some((value) => String(value).toLowerCase().includes(query))
      )
    );
  }, [processVariableQuickScope, processVariableSearchQuery, processVariables]);

  const filteredWorkflowHistory = useMemo(() => {
    const query = workflowHistorySearchQuery.trim().toLowerCase();
    return workflowHistory.filter((item) => {
      if (!matchesWorkflowHistoryQuickScope(item, workflowHistoryQuickScope)) {
        return false;
      }
      if (!query) {
        return true;
      }
      return (
      [
        item.id,
        item.businessKey,
        item.processDefinitionName,
        item.processDefinitionKey,
        item.startedBy,
        item.startComment,
        item.comment,
        item.reviewedBy,
        item.decision,
        item.decisionLabel,
      ]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(query))
      );
    });
  }, [workflowHistory, workflowHistoryQuickScope, workflowHistorySearchQuery]);

  const workflowHistoryScopeCounts = useMemo(() => ({
    ALL: workflowHistory.length,
    RUNNING: workflowHistory.filter((item) => matchesWorkflowHistoryQuickScope(item, 'RUNNING')).length,
    ENDED: workflowHistory.filter((item) => matchesWorkflowHistoryQuickScope(item, 'ENDED')).length,
    APPROVED: workflowHistory.filter((item) => matchesWorkflowHistoryQuickScope(item, 'APPROVED')).length,
    REJECTED: workflowHistory.filter((item) => matchesWorkflowHistoryQuickScope(item, 'REJECTED')).length,
    COMMENTED: workflowHistory.filter((item) => matchesWorkflowHistoryQuickScope(item, 'COMMENTED')).length,
    REVIEWED: workflowHistory.filter((item) => matchesWorkflowHistoryQuickScope(item, 'REVIEWED')).length,
  }), [workflowHistory]);

  const processVariableScopeCounts = useMemo(() => ({
    ALL: processVariables.length,
    STRING: processVariables.filter((variable) => matchesWorkflowVariableQuickScope(variable, 'STRING')).length,
    NUMBER: processVariables.filter((variable) => matchesWorkflowVariableQuickScope(variable, 'NUMBER')).length,
    BOOLEAN: processVariables.filter((variable) => matchesWorkflowVariableQuickScope(variable, 'BOOLEAN')).length,
    STRUCTURED: processVariables.filter((variable) => matchesWorkflowVariableQuickScope(variable, 'STRUCTURED')).length,
    OTHER: processVariables.filter((variable) => matchesWorkflowVariableQuickScope(variable, 'OTHER')).length,
  }), [processVariables]);

  const filteredTaskVariables = useMemo(() => {
    const query = taskVariableSearchQuery.trim().toLowerCase();
    return taskVariables.filter((variable) =>
      matchesWorkflowVariableQuickScope(variable, taskVariableQuickScope)
      && (
        !query
        || [variable.name, variable.type, variable.scope, formatWorkflowValue(variable.value)]
          .filter(Boolean)
          .some((value) => String(value).toLowerCase().includes(query))
      )
    );
  }, [taskVariableQuickScope, taskVariableSearchQuery, taskVariables]);

  const taskVariableScopeCounts = useMemo(() => ({
    ALL: taskVariables.length,
    STRING: taskVariables.filter((variable) => matchesWorkflowVariableQuickScope(variable, 'STRING')).length,
    NUMBER: taskVariables.filter((variable) => matchesWorkflowVariableQuickScope(variable, 'NUMBER')).length,
    BOOLEAN: taskVariables.filter((variable) => matchesWorkflowVariableQuickScope(variable, 'BOOLEAN')).length,
    STRUCTURED: taskVariables.filter((variable) => matchesWorkflowVariableQuickScope(variable, 'STRUCTURED')).length,
    OTHER: taskVariables.filter((variable) => matchesWorkflowVariableQuickScope(variable, 'OTHER')).length,
  }), [taskVariables]);

  const openPeopleDirectoryProfile = useCallback((username?: string | null) => {
    if (!username) {
      return;
    }
    navigate(`/people-directory?username=${encodeURIComponent(username)}`);
  }, [navigate]);

  const renderPersonValue = useCallback((username?: string | null, fallback = 'Unknown') => {
    if (!username) {
      return fallback;
    }
    return (
      <ButtonBase
        onClick={() => openPeopleDirectoryProfile(username)}
        sx={{
          color: 'primary.main',
          fontWeight: 600,
          borderRadius: 1,
          px: 0.25,
        }}
      >
        {username}
      </ButtonBase>
    );
  }, [openPeopleDirectoryProfile]);

  const renderPersonChip = useCallback((username?: string | null) => {
    if (!username) {
      return null;
    }
    return (
      <Chip
        key={`person-chip-${username}`}
        size="small"
        variant="outlined"
        label={`@${username}`}
        onClick={() => openPeopleDirectoryProfile(username)}
      />
    );
  }, [openPeopleDirectoryProfile]);

  const renderSubmissionSummary = useCallback((summary?: WorkflowSubmissionSummary | null) => {
    if (!summary) {
      return (
        <Typography color="textSecondary">
          Submission summary is unavailable.
        </Typography>
      );
    }

    return (
      <Stack spacing={1}>
        <Box display="flex" flexWrap="wrap" gap={0.75}>
          {summary.decisionLabel && (
            <Chip
              size="small"
              color={summary.decision === 'APPROVED' ? 'success' : summary.decision === 'REJECTED' ? 'error' : 'default'}
              label={summary.decisionLabel}
            />
          )}
          {summary.decision && !summary.decisionLabel && (
            <Chip size="small" variant="outlined" label={summary.decision} />
          )}
          {summary.approvers?.length > 0 && (
            <Chip size="small" variant="outlined" label={`Approvers ${summary.approvers.length}`} />
          )}
        </Box>

        {summary.approvers?.length > 0 && (
          <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
            {summary.approvers.map((approver) => renderPersonChip(approver))}
          </Stack>
        )}

        <Typography variant="body2">
          <strong>Submitted by:</strong> {renderPersonValue(summary.startFormSubmittedBy)}
        </Typography>
        <Typography variant="body2">
          <strong>Submitted at:</strong> {formatTimestamp(summary.startFormSubmittedAt)}
        </Typography>
        {summary.startComment && (
          <Typography variant="body2">
            <strong>Start note:</strong> {summary.startComment}
          </Typography>
        )}
        {summary.reviewedBy && (
          <Typography variant="body2">
            <strong>Reviewed by:</strong> {renderPersonValue(summary.reviewedBy)}{summary.reviewedAt ? ` · ${formatTimestamp(summary.reviewedAt)}` : ''}
          </Typography>
        )}
        {summary.comment && (
          <Typography variant="body2">
            <strong>Review note:</strong> {summary.comment}
          </Typography>
        )}
      </Stack>
    );
  }, [renderPersonChip, renderPersonValue]);

  useEffect(() => {
    return () => {
      if (definitionDiagramUrl) {
        window.URL.revokeObjectURL(definitionDiagramUrl);
      }
    };
  }, [definitionDiagramUrl]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setTaskSearchQuery(taskSearchInput.trim());
    }, 250);

    return () => {
      window.clearTimeout(timer);
    };
  }, [taskSearchInput]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setTaskProcessIdQuery(taskProcessIdInput.trim());
    }, 250);

    return () => {
      window.clearTimeout(timer);
    };
  }, [taskProcessIdInput]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setTaskOwnerQuery(taskOwnerInput.trim());
    }, 250);

    return () => {
      window.clearTimeout(timer);
    };
  }, [taskOwnerInput]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setTaskCandidateUserQuery(taskCandidateUserInput.trim());
    }, 250);

    return () => {
      window.clearTimeout(timer);
    };
  }, [taskCandidateUserInput]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setTaskCandidateGroupQuery(taskCandidateGroupInput.trim());
    }, 250);

    return () => {
      window.clearTimeout(timer);
    };
  }, [taskCandidateGroupInput]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setTaskHistorySearchQuery(taskHistorySearchInput.trim());
    }, 250);

    return () => {
      window.clearTimeout(timer);
    };
  }, [taskHistorySearchInput]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setTaskHistoryAssigneeQuery(taskHistoryAssigneeInput.trim());
    }, 250);

    return () => {
      window.clearTimeout(timer);
    };
  }, [taskHistoryAssigneeInput]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setTaskHistoryDefinitionQuery(taskHistoryDefinitionInput.trim());
    }, 250);

    return () => {
      window.clearTimeout(timer);
    };
  }, [taskHistoryDefinitionInput]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setActivitySearchQuery(activitySearchInput.trim());
    }, 250);

    return () => {
      window.clearTimeout(timer);
    };
  }, [activitySearchInput]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setActivityAssigneeQuery(activityAssigneeInput.trim());
    }, 250);

    return () => {
      window.clearTimeout(timer);
    };
  }, [activityAssigneeInput]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setActivityTypeQuery(activityTypeInput.trim());
    }, 250);

    return () => {
      window.clearTimeout(timer);
    };
  }, [activityTypeInput]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setProcessVariableSearchQuery(processVariableSearchInput.trim());
    }, 250);

    return () => {
      window.clearTimeout(timer);
    };
  }, [processVariableSearchInput]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setTaskVariableSearchQuery(taskVariableSearchInput.trim());
    }, 250);

    return () => {
      window.clearTimeout(timer);
    };
  }, [taskVariableSearchInput]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setWorkflowHistorySearchQuery(workflowHistorySearchInput.trim());
    }, 250);

    return () => {
      window.clearTimeout(timer);
    };
  }, [workflowHistorySearchInput]);

  useEffect(() => {
    if (!['claimable', 'unassigned', 'all'].includes(taskScope) && taskCandidateGroupInput) {
      setTaskCandidateGroupInput('');
      setTaskCandidateGroupQuery('');
    }
  }, [taskCandidateGroupInput, taskScope]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setProcessBrowserSearchQuery(processBrowserSearchInput.trim());
      setProcessBrowserPage(0);
    }, 250);

    return () => {
      window.clearTimeout(timer);
    };
  }, [processBrowserSearchInput]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setProcessBrowserStartedByQuery(processBrowserStartedByInput.trim());
      setProcessBrowserPage(0);
    }, 250);

    return () => {
      window.clearTimeout(timer);
    };
  }, [processBrowserStartedByInput]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setProcessBrowserDefinitionKeyQuery(processBrowserDefinitionKeyInput.trim());
      setProcessBrowserPage(0);
    }, 250);

    return () => {
      window.clearTimeout(timer);
    };
  }, [processBrowserDefinitionKeyInput]);

  useEffect(() => {
    selectedTaskRef.current = selectedTask;
  }, [detailRefreshToken, selectedTask]);

  const refreshTasks = useCallback(async (preserveSelection = false, preferredTaskId?: string | null) => {
    const requestId = ++inboxRequestIdRef.current;

    try {
      setLoading(true);
      const data = await workflowService.getTaskInbox({
        scope: taskScope,
        query: taskSearchQuery,
        processId: taskProcessIdQuery,
        owner: taskOwnerQuery,
        candidateUser: taskCandidateUserQuery,
        candidateGroup: taskCandidateGroupQuery,
      });

      if (requestId !== inboxRequestIdRef.current) {
        return;
      }

      setTasks(data);

      const preferredTask = preferredTaskId
        ? data.find((task) => task.id === preferredTaskId) || null
        : null;
      if (preferredTask) {
        setSelectedTask(preferredTask);
        return;
      }

      const currentSelectionId = selectedTaskRef.current?.id;
      const currentSelection = currentSelectionId
        ? data.find((task) => task.id === currentSelectionId) || null
        : null;

      if (preserveSelection && currentSelection) {
        setSelectedTask(currentSelection);
      } else if (data.length > 0) {
        setSelectedTask(data[0]);
      } else {
        setSelectedTask(null);
      }
    } catch {
      if (requestId === inboxRequestIdRef.current) {
        toast.error('Failed to load tasks');
      }
    } finally {
      if (requestId === inboxRequestIdRef.current) {
        setLoading(false);
      }
    }
  }, [taskCandidateGroupQuery, taskCandidateUserQuery, taskOwnerQuery, taskProcessIdQuery, taskScope, taskSearchQuery]);

  useEffect(() => {
    void refreshTasks();
  }, [refreshTasks]);

  useEffect(() => {
    let cancelled = false;
    setProcessBrowserLoading(true);

    workflowService.listProcesses(
      processBrowserStatus,
      undefined,
      processBrowserStartedByQuery || undefined,
      processBrowserDefinitionKeyQuery || undefined,
      processBrowserSearchQuery || undefined,
      processBrowserPage * PROCESS_BROWSER_PAGE_SIZE,
      PROCESS_BROWSER_PAGE_SIZE
    )
      .then((response) => {
        if (cancelled) {
          return;
        }
        setProcessBrowserItems(response.items || []);
        setProcessBrowserTotal(response.paging?.totalItems || 0);
        setProcessBrowserHasMore(Boolean(response.paging?.hasMoreItems));
      })
      .catch(() => {
        if (!cancelled) {
          setProcessBrowserItems([]);
          setProcessBrowserTotal(0);
          setProcessBrowserHasMore(false);
          toast.error('Failed to load process browser');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setProcessBrowserLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [
    processBrowserDefinitionKeyQuery,
    processBrowserPage,
    processBrowserSearchQuery,
    processBrowserStartedByQuery,
    processBrowserStatus,
  ]);

  useEffect(() => {
    if (actionDialog !== 'assignTask' && actionDialog !== 'delegateTask') {
      setAssignmentOptions([]);
      setAssignmentOptionsLoading(false);
      return undefined;
    }

    let cancelled = false;
    const timer = window.setTimeout(() => {
      setAssignmentOptionsLoading(true);
      peopleService.search(assignmentUsername.trim(), 0, 10)
        .then((response) => {
          if (!cancelled) {
            setAssignmentOptions(response.content || []);
          }
        })
        .catch(() => {
          if (!cancelled) {
            setAssignmentOptions([]);
          }
        })
        .finally(() => {
          if (!cancelled) {
            setAssignmentOptionsLoading(false);
          }
        });
    }, 200);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [actionDialog, assignmentUsername]);

  useEffect(() => {
    if (!selectedTask) {
      setTaskDetail(null);
      setProcessDetail(null);
      setProcessTasks([]);
      setProcessTaskHistory([]);
      setProcessActivities([]);
      setProcessInvolvedActors([]);
      setProcessVariables([]);
      setProcessItems([]);
      setTaskVariables([]);
      setTaskItems([]);
      setTaskCandidates([]);
      setTaskInvolvedActors([]);
      setTaskFormModel([]);
      setDefinitionDetail(null);
      setDefinitionModel(null);
      setDefinitionDiagramUrl(null);
      setDefinitionDiagramError(null);
      setWorkflowHistory([]);
      setShowModelXml(false);
      return;
    }

    let cancelled = false;
    const loadDetail = async () => {
      try {
        setDetailLoading(true);
        const isCompletedInboxItem = selectedTask.status === 'COMPLETED';

        if (isCompletedInboxItem) {
          setTaskDetail(null);
          setTaskVariables([]);
          setTaskItems([]);
          setTaskFormModel([]);

          if (!selectedTask.processInstanceId) {
            setProcessDetail(null);
            setProcessTasks([]);
            setProcessTaskHistory([]);
            setProcessActivities([]);
            setProcessInvolvedActors([]);
            setProcessVariables([]);
            setProcessItems([]);
            setDefinitionDetail(null);
            setDefinitionModel(null);
            setDefinitionDiagramUrl(null);
            setDefinitionDiagramError('Completed inbox item has no process instance reference.');
            setWorkflowHistory([]);
            return;
          }

          const processResult = await workflowService.getProcessDetail(selectedTask.processInstanceId);
          if (cancelled) return;
          setProcessDetail(processResult);

          const [
            processTasksResult,
            processTaskHistoryResult,
            processActivitiesResult,
            processInvolvedActorsResult,
            processVariablesResult,
            processItemsResult,
            definitionResult,
            modelResult,
            historyResult,
          ] = await Promise.allSettled([
            workflowService.getProcessTasks(processResult.id),
            workflowService.getProcessTaskHistory(processResult.id, {
              query: taskHistorySearchQuery || undefined,
              assignee: taskHistoryAssigneeQuery || undefined,
              taskDefinitionKey: taskHistoryDefinitionQuery || undefined,
            }),
            workflowService.getProcessActivities(processResult.id, {
              query: activitySearchQuery || undefined,
              assignee: activityAssigneeQuery || undefined,
              activityType: activityTypeQuery || undefined,
            }),
            workflowService.getProcessInvolvedActors(processResult.id),
            workflowService.getProcessVariables(processResult.id),
            workflowService.getProcessItems(processResult.id),
            workflowService.getDefinitionDetail(processResult.processDefinitionId),
            workflowService.getDefinitionModel(processResult.processDefinitionId),
            selectedTask.businessKey ? workflowService.getDocumentHistory(selectedTask.businessKey) : Promise.resolve([] as WorkflowHistoryItem[]),
          ]);

          if (cancelled) return;

          setProcessTasks(processTasksResult.status === 'fulfilled' ? processTasksResult.value || [] : []);
          setProcessTaskHistory(processTaskHistoryResult.status === 'fulfilled' ? processTaskHistoryResult.value || [] : []);
          setProcessActivities(processActivitiesResult.status === 'fulfilled' ? processActivitiesResult.value || [] : []);
          setProcessInvolvedActors(processInvolvedActorsResult.status === 'fulfilled' ? processInvolvedActorsResult.value || [] : []);
          setProcessVariables(processVariablesResult.status === 'fulfilled' ? processVariablesResult.value || [] : []);
          setProcessItems(processItemsResult.status === 'fulfilled' ? processItemsResult.value || [] : []);
          setTaskCandidates([]);
          setTaskInvolvedActors([]);
          setDefinitionDetail(definitionResult.status === 'fulfilled' ? definitionResult.value : null);
          setDefinitionModel(modelResult.status === 'fulfilled' ? modelResult.value : null);
          setWorkflowHistory(historyResult.status === 'fulfilled' ? historyResult.value || [] : []);

          if (definitionResult.status === 'fulfilled' && definitionResult.value.diagramAvailable) {
            try {
              const diagramBlob = await workflowService.getProcessDiagram(processResult.id)
                .catch(() => workflowService.getDefinitionDiagram(processResult.processDefinitionId));
              if (cancelled) return;
              const diagramUrl = window.URL.createObjectURL(diagramBlob);
              setDefinitionDiagramUrl((current) => {
                if (current) {
                  window.URL.revokeObjectURL(current);
                }
                return diagramUrl;
              });
              setDefinitionDiagramError(null);
            } catch {
              if (!cancelled) {
                setDefinitionDiagramUrl(null);
                setDefinitionDiagramError('Diagram resource is unavailable. BPMN XML remains available below.');
              }
            }
          } else {
            setDefinitionDiagramUrl(null);
            setDefinitionDiagramError(definitionResult.status === 'fulfilled'
              ? 'No diagram resource is deployed for this workflow definition.'
              : 'Definition metadata is unavailable, so no diagram can be loaded.');
          }
          return;
        }

        const detail = await workflowService.getTaskDetail(selectedTask.id);
        if (cancelled) return;
        setTaskDetail(detail);

        const [
          processResult,
          processTasksResult,
          processTaskHistoryResult,
          processActivitiesResult,
          processInvolvedActorsResult,
          processVariablesResult,
          processItemsResult,
          taskVariablesResult,
          taskItemsResult,
          taskCandidatesResult,
          taskInvolvedActorsResult,
          taskFormModelResult,
          definitionResult,
          modelResult,
          historyResult,
        ] = await Promise.allSettled([
          workflowService.getProcessDetail(detail.processInstanceId),
          workflowService.getProcessTasks(detail.processInstanceId),
          workflowService.getProcessTaskHistory(detail.processInstanceId, {
            query: taskHistorySearchQuery || undefined,
            assignee: taskHistoryAssigneeQuery || undefined,
            taskDefinitionKey: taskHistoryDefinitionQuery || undefined,
          }),
          workflowService.getProcessActivities(detail.processInstanceId, {
            query: activitySearchQuery || undefined,
            assignee: activityAssigneeQuery || undefined,
            activityType: activityTypeQuery || undefined,
          }),
          workflowService.getProcessInvolvedActors(detail.processInstanceId),
          workflowService.getProcessVariables(detail.processInstanceId),
          workflowService.getProcessItems(detail.processInstanceId),
          workflowService.getTaskVariables(detail.id),
          workflowService.getTaskItems(detail.id),
          workflowService.getTaskCandidates(detail.id),
          workflowService.getTaskInvolvedActors(detail.id),
          workflowService.getTaskFormModel(detail.id),
          workflowService.getDefinitionDetail(detail.processDefinitionId),
          workflowService.getDefinitionModel(detail.processDefinitionId),
          detail.businessKey ? workflowService.getDocumentHistory(detail.businessKey) : Promise.resolve([] as WorkflowHistoryItem[]),
        ]);

        if (cancelled) return;

        if (processResult.status === 'fulfilled') {
          setProcessDetail(processResult.value);
        } else {
          setProcessDetail(null);
        }

        if (processTasksResult.status === 'fulfilled') {
          setProcessTasks(processTasksResult.value || []);
        } else {
          setProcessTasks([]);
        }

        if (processTaskHistoryResult.status === 'fulfilled') {
          setProcessTaskHistory(processTaskHistoryResult.value || []);
        } else {
          setProcessTaskHistory([]);
        }

        if (processActivitiesResult.status === 'fulfilled') {
          setProcessActivities(processActivitiesResult.value || []);
        } else {
          setProcessActivities([]);
        }

        if (processInvolvedActorsResult.status === 'fulfilled') {
          setProcessInvolvedActors(processInvolvedActorsResult.value || []);
        } else {
          setProcessInvolvedActors([]);
        }

        if (processVariablesResult.status === 'fulfilled') {
          setProcessVariables(processVariablesResult.value || []);
        } else {
          setProcessVariables([]);
        }

        if (processItemsResult.status === 'fulfilled') {
          setProcessItems(processItemsResult.value || []);
        } else {
          setProcessItems([]);
        }

        if (taskVariablesResult.status === 'fulfilled') {
          setTaskVariables(taskVariablesResult.value || []);
        } else {
          setTaskVariables([]);
        }

        if (taskItemsResult.status === 'fulfilled') {
          setTaskItems(taskItemsResult.value || []);
        } else {
          setTaskItems([]);
        }

        if (taskCandidatesResult.status === 'fulfilled') {
          setTaskCandidates(taskCandidatesResult.value || []);
        } else {
          setTaskCandidates([]);
        }

        if (taskInvolvedActorsResult.status === 'fulfilled') {
          setTaskInvolvedActors(taskInvolvedActorsResult.value || []);
        } else {
          setTaskInvolvedActors([]);
        }

        if (taskFormModelResult.status === 'fulfilled') {
          setTaskFormModel(taskFormModelResult.value || []);
        } else {
          setTaskFormModel([]);
        }

        if (definitionResult.status === 'fulfilled') {
          setDefinitionDetail(definitionResult.value);
        } else {
          setDefinitionDetail(null);
        }

        if (modelResult.status === 'fulfilled') {
          setDefinitionModel(modelResult.value);
        } else {
          setDefinitionModel(null);
        }

        if (historyResult.status === 'fulfilled') {
          setWorkflowHistory(historyResult.value || []);
        } else {
          setWorkflowHistory([]);
        }

        if (definitionResult.status === 'fulfilled' && definitionResult.value.diagramAvailable) {
          try {
            const diagramBlob = await workflowService.getProcessDiagram(detail.processInstanceId)
              .catch(() => workflowService.getDefinitionDiagram(detail.processDefinitionId));
            if (cancelled) return;
            const diagramUrl = window.URL.createObjectURL(diagramBlob);
            setDefinitionDiagramUrl((current) => {
              if (current) {
                window.URL.revokeObjectURL(current);
              }
              return diagramUrl;
            });
            setDefinitionDiagramError(null);
          } catch {
            if (!cancelled) {
              setDefinitionDiagramUrl(null);
              setDefinitionDiagramError('Diagram resource is unavailable. BPMN XML remains available below.');
            }
          }
        } else {
          setDefinitionDiagramUrl(null);
          setDefinitionDiagramError(definitionResult.status === 'fulfilled'
            ? 'No diagram resource is deployed for this workflow definition.'
            : 'Definition metadata is unavailable, so no diagram can be loaded.');
        }
      } catch {
        if (!cancelled) {
          toast.error('Failed to load workflow details');
          setTaskDetail(null);
          setProcessDetail(null);
          setProcessTasks([]);
          setProcessTaskHistory([]);
          setProcessActivities([]);
          setProcessInvolvedActors([]);
          setProcessVariables([]);
          setProcessItems([]);
          setTaskVariables([]);
          setTaskItems([]);
          setTaskCandidates([]);
          setTaskInvolvedActors([]);
          setTaskFormModel([]);
          setDefinitionDetail(null);
          setDefinitionModel(null);
          setDefinitionDiagramUrl(null);
          setDefinitionDiagramError(null);
          setWorkflowHistory([]);
        }
      } finally {
        if (!cancelled) {
          setDetailLoading(false);
        }
      }
    };

    void loadDetail();

    return () => {
      cancelled = true;
    };
  }, [activityAssigneeQuery, activitySearchQuery, activityTypeQuery, detailRefreshToken, selectedTask, taskHistoryAssigneeQuery, taskHistoryDefinitionQuery, taskHistorySearchQuery]);

  const handleAction = async (approved: boolean) => {
    if (!selectedTask) return;

    try {
      await workflowService.transitionTask(selectedTask.id, {
        state: 'completed',
        values: {
          approved,
          comment,
        },
      });
      toast.success(`Task ${approved ? 'approved' : 'rejected'} successfully`);
      setActionDialog(null);
      setComment('');
      await refreshTasks(true);
    } catch (error) {
      toast.error('Failed to complete task');
    }
  };

  const handleClaimTask = async (taskId: string) => {
    try {
      setTaskLifecycleLoadingId(taskId);
      await workflowService.transitionTask(taskId, { state: 'claimed' });
      toast.success('Task claimed');
      await refreshTasks(false, taskId);
    } catch {
      toast.error('Failed to claim task');
    } finally {
      setTaskLifecycleLoadingId(null);
    }
  };

  const handleReleaseTask = async (taskId: string) => {
    try {
      setTaskLifecycleLoadingId(taskId);
      await workflowService.transitionTask(taskId, { state: 'unclaimed' });
      toast.success('Task released');
      await refreshTasks(true, taskId);
    } catch {
      toast.error('Failed to release task');
    } finally {
      setTaskLifecycleLoadingId(null);
    }
  };

  const handleFocusProcess = (processItem: WorkflowProcessBrowserItem) => {
    const matchedTask = tasks.find((task) => task.processInstanceId === processItem.id)
      || tasks.find((task) => processItem.businessKey && task.businessKey === processItem.businessKey);

    if (matchedTask) {
      setSelectedTask(matchedTask);
      return;
    }

    if (processItem.businessKey) {
      setTaskSearchInput(processItem.businessKey);
    }
    setTaskScope(processItem.ended ? 'completed' : 'all');
    toast.info('Applied process context to the task inbox to help locate related tasks.');
  };

  const handleCancelProcess = async () => {
    if (!processDetail || processDetail.ended) {
      return;
    }

    try {
      setProcessCancelLoading(true);
      await workflowService.cancelProcess(processDetail.id, comment.trim() || undefined);
      toast.success('Process cancelled');
      setActionDialog(null);
      setComment('');
      await refreshTasks(true, selectedTask?.id);
    } catch {
      toast.error('Failed to cancel process');
    } finally {
      setProcessCancelLoading(false);
    }
  };

  const handleAssignmentAction = async () => {
    if (!selectedTask) {
      return;
    }

    const nextAssignee = assignmentUsername.trim();
    if (!nextAssignee) {
      toast.error('Assignee is required');
      return;
    }

    try {
      setTaskAssignLoading(true);
      const isDelegate = actionDialog === 'delegateTask';
      await workflowService.transitionTask(selectedTask.id, {
        state: isDelegate ? 'delegated' : 'assigned',
        assignee: nextAssignee,
      });
      toast.success(`Task ${isDelegate ? 'delegated' : 'assigned'} to ${nextAssignee}`);
      setActionDialog(null);
      setAssignmentUsername('');
      await refreshTasks(false, selectedTask.id);
    } catch {
      toast.error(`Failed to ${actionDialog === 'delegateTask' ? 'delegate' : 'assign'} task`);
    } finally {
      setTaskAssignLoading(false);
    }
  };

  const handleResolveTask = async () => {
    if (!selectedTask) {
      return;
    }

    try {
      setTaskAssignLoading(true);
      await workflowService.transitionTask(selectedTask.id, {
        state: 'resolved',
        values: comment.trim() ? { comment: comment.trim() } : {},
      });
      toast.success('Delegated task resolved');
      setActionDialog(null);
      setComment('');
      await refreshTasks(false, selectedTask.id);
    } catch {
      toast.error('Failed to resolve delegated task');
    } finally {
      setTaskAssignLoading(false);
    }
  };

  const openProcessVariableEditor = (variable?: WorkflowVariableItem) => {
    setProcessVariableName(variable?.name || '');
    setProcessVariableValue(
      variable
        ? typeof variable.value === 'string'
          ? variable.value
          : JSON.stringify(variable.value, null, 2)
        : ''
    );
    setProcessVariableEditorOpen(true);
  };

  const closeProcessVariableEditor = () => {
    if (processVariableSaving) {
      return;
    }
    setProcessVariableEditorOpen(false);
    setProcessVariableName('');
    setProcessVariableValue('');
  };

  const handleSaveProcessVariable = async () => {
    if (!processDetail) {
      return;
    }
    const variableName = processVariableName.trim();
    if (!variableName) {
      toast.error('Variable name is required');
      return;
    }

    let parsedValue: any = processVariableValue;
    const normalizedValue = processVariableValue.trim();
    if (!normalizedValue) {
      parsedValue = '';
    } else {
      try {
        parsedValue = JSON.parse(normalizedValue);
      } catch {
        parsedValue = processVariableValue;
      }
    }

    try {
      setProcessVariableSaving(true);
      await workflowService.setProcessVariable(processDetail.id, variableName, parsedValue);
      toast.success(`Process variable ${variableName} saved`);
      setProcessVariableEditorOpen(false);
      setProcessVariableName('');
      setProcessVariableValue('');
      setDetailRefreshToken((current) => current + 1);
    } catch {
      toast.error('Failed to save process variable');
    } finally {
      setProcessVariableSaving(false);
    }
  };

  const handleDeleteProcessVariable = async (variableName: string) => {
    if (!processDetail) {
      return;
    }

    try {
      setProcessVariableDeletingName(variableName);
      await workflowService.deleteProcessVariable(processDetail.id, variableName);
      toast.success(`Process variable ${variableName} deleted`);
      setDetailRefreshToken((current) => current + 1);
    } catch {
      toast.error('Failed to delete process variable');
    } finally {
      setProcessVariableDeletingName((current) => (current === variableName ? null : current));
    }
  };

  const closeDocumentPreview = () => {
    setPreviewNode(null);
    setPreviewCommentsOpen(false);
    setPreviewCommentDraftText(null);
  };

  const openBusinessItem = async (
    item: WorkflowBusinessItem,
    options?: {
      discuss?: boolean;
    }
  ) => {
    const normalizedType = item.nodeType?.trim().toUpperCase();

    if (normalizedType === 'FOLDER') {
      navigate(`/browse/${item.id}`);
      return;
    }

    setPreviewLoadingItemId(item.id);
    try {
      const node = await nodeService.getNode(item.id);
      if (node.nodeType === 'FOLDER') {
        navigate(`/browse/${node.id}`);
        return;
      }

      setPreviewNode(node);
      setPreviewCommentsOpen(Boolean(options?.discuss));
      setPreviewCommentDraftText(null);
    } catch {
      toast.error('Failed to open workflow item');
    } finally {
      setPreviewLoadingItemId((current) => (current === item.id ? null : current));
    }
  };

  const renderBusinessItems = (title: string, items: WorkflowBusinessItem[]) => {
    return (
      <Box>
        <Box display="flex" alignItems="center" gap={1} mb={1}>
          <Typography variant="subtitle2">{title}</Typography>
          <Chip size="small" variant="outlined" label={`${items.length} item${items.length === 1 ? '' : 's'}`} />
        </Box>
        {items.length === 0 ? (
          <Typography variant="body2" color="textSecondary">
            No workflow business items are attached to this scope.
          </Typography>
        ) : (
          <Stack spacing={1}>
            {items.map((item) => {
              const normalizedType = item.nodeType?.trim().toUpperCase();
              const isFolder = normalizedType === 'FOLDER';
              const isDocument = normalizedType === 'DOCUMENT' || !isFolder;
              const isLoading = previewLoadingItemId === item.id;

              return (
                <Paper key={item.id} variant="outlined" sx={{ p: 1.25 }}>
                  <Stack spacing={1}>
                    <Box display="flex" alignItems="flex-start" justifyContent="space-between" gap={1}>
                      <Box minWidth={0}>
                        <Typography variant="body2" fontWeight={600} noWrap title={item.name}>
                          {item.name}
                        </Typography>
                        <Typography variant="caption" color="text.secondary" sx={{ wordBreak: 'break-word' }}>
                          {item.nodeType} · {item.path}
                        </Typography>
                        {item.businessKey && (
                          <Typography variant="caption" color="text.secondary" display="block">
                            Business key: {item.businessKey}
                          </Typography>
                        )}
                      </Box>
                      <Chip size="small" variant="outlined" label={item.source || 'workflow'} />
                    </Box>

                    <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                      {isFolder ? (
                        <Button
                          size="small"
                          variant="outlined"
                          startIcon={<FolderOpen />}
                          onClick={() => navigate(`/browse/${item.id}`)}
                        >
                          Open
                        </Button>
                      ) : (
                        <>
                          <Button
                            size="small"
                            variant="outlined"
                            startIcon={isLoading ? <CircularProgress size={14} /> : <Visibility />}
                            onClick={() => void openBusinessItem(item)}
                            disabled={isLoading}
                          >
                            Preview
                          </Button>
                          <Button
                            size="small"
                            variant="outlined"
                            startIcon={<ChatBubbleOutline />}
                            onClick={() => void openBusinessItem(item, { discuss: true })}
                            disabled={isLoading}
                          >
                            Discuss
                          </Button>
                        </>
                      )}

                      {!isDocument && !isFolder && (
                        <Button
                          size="small"
                          variant="text"
                          onClick={() => navigate(`/browse/${item.id}`)}
                        >
                          Open location
                        </Button>
                      )}
                    </Stack>
                  </Stack>
                </Paper>
              );
            })}
          </Stack>
        )}
      </Box>
    );
  };

  const renderTaskCandidates = () => {
    if (taskCandidates.length === 0) {
      return (
        <Typography variant="body2" color="textSecondary">
          No candidate users or groups are published for this task.
        </Typography>
      );
    }

    return (
      <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
        {taskCandidates.map((candidate, index) => {
          const userId = candidate.userId?.trim();
          const groupId = candidate.groupId?.trim();
          const label = userId ? `User ${userId}` : `Group ${groupId || 'unknown'}`;
          return (
            <Chip
              key={`${candidate.type || 'candidate'}-${userId || groupId || index}`}
              size="small"
              variant="outlined"
              label={label}
              onClick={userId ? () => openPeopleDirectoryProfile(userId) : undefined}
            />
          );
        })}
      </Stack>
    );
  };

  const renderInvolvedActors = (
    actors: WorkflowInvolvedActor[],
    emptyMessage: string
  ) => {
    if (actors.length === 0) {
      return (
        <Typography variant="body2" color="textSecondary">
          {emptyMessage}
        </Typography>
      );
    }

    return (
      <List dense disablePadding>
        {actors.map((actor, index) => {
          const label = actor.userId || actor.groupId || actor.displayName || 'Unknown';
          const isUser = Boolean(actor.userId);
          return (
            <React.Fragment key={`${actor.userId || actor.groupId || 'actor'}-${index}`}>
              {index > 0 && <Divider />}
              <ListItem disableGutters>
                <ListItemText
                  primary={isUser ? renderPersonValue(actor.userId, label) : actor.displayName || actor.groupId || 'Group'}
                  secondary={
                    <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap" sx={{ mt: 0.5 }}>
                      {(actor.roles || []).map((role) => (
                        <Chip
                          key={`${label}-${role}`}
                          size="small"
                          variant="outlined"
                          label={role}
                          color={role.toLowerCase().includes('candidate') ? 'default' : 'primary'}
                        />
                      ))}
                    </Stack>
                  }
                />
              </ListItem>
            </React.Fragment>
          );
        })}
      </List>
    );
  };

  const renderProcessActivities = () => {
    if (processActivities.length === 0) {
      return (
        <Typography color="textSecondary">
          No process activities are available for this workflow instance.
        </Typography>
      );
    }

    if (filteredProcessActivities.length === 0) {
      const hasActivityFilter =
        Boolean(activitySearchQuery || activityAssigneeQuery || activityTypeQuery)
        || activityQuickScope !== 'ALL';
      return (
        <Typography color="textSecondary">
          {hasActivityFilter
            ? 'No process activities match the current filters.'
            : 'No process activities match the current scope.'}
        </Typography>
      );
    }

    return (
      <List dense disablePadding>
        {filteredProcessActivities.map((activity, index) => (
          <React.Fragment key={activity.id}>
            {index > 0 && <Divider />}
            <ListItem disableGutters>
              <ListItemText
                primary={activity.activityName || activity.activityId || activity.activityType || activity.id}
                secondary={
                  <Stack spacing={0.5} sx={{ mt: 0.5 }}>
                    <Typography component="span" variant="caption" color="textSecondary">
                      Type: {activity.activityType || 'N/A'}
                      {activity.taskId ? ` · Task ${activity.taskId}` : ''}
                    </Typography>
                    <Typography component="span" variant="caption" color="textSecondary">
                      Start: {formatTimestamp(activity.startTime)}
                      {activity.endTime ? ` · End: ${formatTimestamp(activity.endTime)}` : ' · Running'}
                    </Typography>
                    {(activity.assignee || activity.durationInMillis !== undefined) && (
                      <Typography component="span" variant="caption" color="textSecondary">
                        {activity.assignee ? `Assignee: ${activity.assignee}` : 'Assignee: N/A'}
                        {activity.durationInMillis !== undefined ? ` · Duration ${activity.durationInMillis} ms` : ''}
                      </Typography>
                    )}
                  </Stack>
                }
              />
            </ListItem>
          </React.Fragment>
        ))}
      </List>
    );
  };

  const renderProcessTaskHistory = () => {
    if (filteredProcessTaskHistory.length === 0) {
      return (
        <Typography color="textSecondary">
          {(taskHistorySearchQuery || taskHistoryAssigneeQuery || taskHistoryDefinitionQuery || taskHistoryQuickScope !== 'ALL')
            ? 'No completed process tasks match the current filters.'
            : 'No completed process tasks are recorded for this workflow yet.'}
        </Typography>
      );
    }

    return (
      <List dense disablePadding>
        {filteredProcessTaskHistory.map((item, index) => (
          <React.Fragment key={item.id}>
            {index > 0 && <Divider />}
            <ListItem disableGutters>
              <ListItemText
                primary={item.name || item.taskDefinitionKey || item.id}
                secondary={
                  <Stack spacing={0.5} sx={{ mt: 0.5 }}>
                    <Typography component="span" variant="caption" color="textSecondary">
                      Assignee: {renderPersonValue(item.assignee, 'Unassigned')}
                      {item.owner ? ` · Owner: ${item.owner}` : ''}
                    </Typography>
                    <Typography component="span" variant="caption" color="textSecondary">
                      Start: {formatTimestamp(item.startTime)}
                      {item.endTime ? ` · End: ${formatTimestamp(item.endTime)}` : ''}
                    </Typography>
                    {(item.durationInMillis !== undefined || item.deleteReason) && (
                      <Typography component="span" variant="caption" color="textSecondary">
                        {item.durationInMillis !== undefined ? `Duration ${item.durationInMillis} ms` : 'Duration N/A'}
                        {item.deleteReason ? ` · ${item.deleteReason}` : ''}
                      </Typography>
                    )}
                  </Stack>
                }
              />
            </ListItem>
          </React.Fragment>
        ))}
      </List>
    );
  };

  const approvalField = taskFormModel.find((field) => field.name === 'approved');
  const reviewCommentField = taskFormModel.find((field) => field.name === 'comment');
  const canAssignSelectedTask = Boolean(taskDetail) && (
    taskDetail?.assignee === currentUser?.username
    || currentUser?.roles?.includes('ROLE_ADMIN')
  );
  const canDelegateSelectedTask = Boolean(taskDetail && !selectedTaskCompleted && taskDetail.assignee) && (
    taskDetail?.assignee === currentUser?.username
    || currentUser?.roles?.includes('ROLE_ADMIN')
  );
  const canResolveSelectedTask = Boolean(taskDetail && !selectedTaskCompleted)
    && taskDetail?.delegationState === 'PENDING'
    && (
      taskDetail?.assignee === currentUser?.username
      || currentUser?.roles?.includes('ROLE_ADMIN')
    );
  const supportsCandidateGroupFilter = ['claimable', 'unassigned', 'all'].includes(taskScope);
  const supportsCandidateUserFilter = ['claimable', 'unassigned', 'all'].includes(taskScope);
  const hasTaskFilters = taskScope !== 'my'
    || taskSearchQuery.length > 0
    || taskProcessIdQuery.length > 0
    || taskOwnerQuery.length > 0
    || taskCandidateUserQuery.length > 0
    || taskCandidateGroupQuery.length > 0;

  return (
    <Box p={3}>
      <Typography variant="h4" gutterBottom>
        Workflow Workbench
      </Typography>

      <Grid container spacing={2}>
        <Grid item xs={12} md={5}>
          <Paper sx={{ overflow: 'hidden' }}>
            <Box sx={{ p: 2, pb: 1.5 }}>
              <Box display="flex" alignItems="center" justifyContent="space-between" gap={1} mb={1.25}>
                <Box>
                  <Typography variant="h6">Task Inbox</Typography>
                  <Typography variant="caption" color="text.secondary">
                    Browse assigned work, claimable queue items, shared queue items, involved tasks, wider active inbox, or completed history.
                  </Typography>
                </Box>
                <Chip size="small" variant="outlined" label={`${tasks.length} task${tasks.length === 1 ? '' : 's'}`} />
              </Box>

              <Stack spacing={1.25}>
                <TextField
                  select
                  size="small"
                  label="Scope"
                  value={taskScope}
                  onChange={(event) => setTaskScope(event.target.value as TaskInboxScope)}
                  fullWidth
                >
                  <MenuItem value="my">My inbox</MenuItem>
                  <MenuItem value="claimable">Claimable</MenuItem>
                  <MenuItem value="unassigned">Unassigned</MenuItem>
                  <MenuItem value="involved">Involved</MenuItem>
                  <MenuItem value="all">All available</MenuItem>
                  <MenuItem value="completed">Completed</MenuItem>
                </TextField>

                <TextField
                  size="small"
                  label="Search tasks"
                  value={taskSearchInput}
                  onChange={(event) => setTaskSearchInput(event.target.value)}
                  placeholder="Name, assignee, starter, process, business key..."
                  fullWidth
                  InputProps={{
                    startAdornment: (
                      <InputAdornment position="start">
                        <Search fontSize="small" />
                      </InputAdornment>
                    ),
                    endAdornment: taskSearchInput ? (
                      <InputAdornment position="end">
                        <IconButton
                          size="small"
                          aria-label="Clear task search"
                          onClick={() => setTaskSearchInput('')}
                          edge="end"
                        >
                          <Clear fontSize="small" />
                        </IconButton>
                      </InputAdornment>
                    ) : undefined,
                  }}
                />

                <TextField
                  size="small"
                  label="Process ID"
                  value={taskProcessIdInput}
                  onChange={(event) => setTaskProcessIdInput(event.target.value)}
                  placeholder="Filter a specific process instance"
                  fullWidth
                />

                <TextField
                  size="small"
                  label="Owner"
                  value={taskOwnerInput}
                  onChange={(event) => setTaskOwnerInput(event.target.value)}
                  placeholder="Filter task owner"
                  fullWidth
                />

                <TextField
                  size="small"
                  label="Candidate user"
                  value={taskCandidateUserInput}
                  onChange={(event) => setTaskCandidateUserInput(event.target.value)}
                  placeholder={supportsCandidateUserFilter ? 'e.g. alice' : 'Available for claimable/shared/all active'}
                  fullWidth
                  disabled={!supportsCandidateUserFilter}
                  helperText={supportsCandidateUserFilter ? 'Only applies to active shared or claimable scopes.' : 'Switch scope to Claimable, Unassigned, or All available.'}
                />

                <TextField
                  size="small"
                  label="Candidate group"
                  value={taskCandidateGroupInput}
                  onChange={(event) => setTaskCandidateGroupInput(event.target.value)}
                  placeholder={supportsCandidateGroupFilter ? 'e.g. sales' : 'Available for claimable/shared/all active'}
                  fullWidth
                  disabled={!supportsCandidateGroupFilter}
                  helperText={supportsCandidateGroupFilter ? 'Only applies to active shared or claimable scopes.' : 'Switch scope to Claimable, Unassigned, or All available.'}
                />

                {hasTaskFilters && (
                  <Button
                    size="small"
                    variant="text"
                    onClick={() => {
                      setTaskScope('my');
                      setTaskSearchInput('');
                      setTaskProcessIdInput('');
                      setTaskOwnerInput('');
                      setTaskCandidateUserInput('');
                      setTaskCandidateGroupInput('');
                    }}
                    sx={{ alignSelf: 'flex-start', px: 0 }}
                  >
                    Reset filters
                  </Button>
                )}
              </Stack>
            </Box>

            <Divider />

            {tasks.length === 0 && !loading ? (
              <Box p={4} textAlign="center">
                <Typography color="textSecondary">
                  {hasTaskFilters ? 'No tasks match the current scope or search.' : 'No pending tasks'}
                </Typography>
              </Box>
            ) : loading && tasks.length === 0 ? (
              <Box p={4} textAlign="center">
                <CircularProgress size={24} />
                <Typography color="textSecondary" sx={{ mt: 1 }}>
                  Loading task inbox...
                </Typography>
              </Box>
            ) : (
              <List>
                {tasks.map((task, index) => {
                  const isSelected = selectedTask?.id === task.id;
                  const canRelease = Boolean(task.assignee) && task.assignee === currentUser?.username;
                  const canClaimFromInbox = Boolean(task.claimable);
                  const canResolveFromInbox = task.delegationState === 'PENDING' && task.assignee === currentUser?.username;
                  const isCompletedTask = task.status === 'COMPLETED';
                  const taskBusy = taskLifecycleLoadingId === task.id;
                  return (
                    <React.Fragment key={task.id}>
                      {index > 0 && <Divider />}
                      <ListItem disablePadding>
                        <ListItemButton selected={isSelected} onClick={() => setSelectedTask(task)}>
                          <ListItemText
                            primary={task.name}
                            secondary={
                              <>
                              <Typography component="span" variant="body2" color="textPrimary">
                                  {task.description || 'No description'}
                                </Typography>
                                <br />
                                <Typography component="span" variant="caption" color="textSecondary">
                                  Process: {task.processDefinitionName || task.processDefinitionKey || task.processDefinitionId || 'Workflow'}
                                </Typography>
                                <br />
                                <Typography component="span" variant="caption" color="textSecondary">
                                  Business key: {task.businessKey || 'N/A'}
                                </Typography>
                                <br />
                                <Typography component="span" variant="caption" color="textSecondary">
                                  Assignee: {renderPersonValue(task.assignee, 'Unassigned')}
                                </Typography>
                                <br />
                                <Typography component="span" variant="caption" color="textSecondary">
                                  Owner: {renderPersonValue(task.owner, 'N/A')}
                                  {task.delegationState ? ` · Delegation ${task.delegationState}` : ''}
                                </Typography>
                                <br />
                                <Typography component="span" variant="caption" color="textSecondary">
                                  Created: {format(new Date(task.createTime), 'PPp')}
                                </Typography>
                              </>
                            }
                          />
                          <Box display="flex" flexDirection="column" gap={1} alignItems="flex-end">
                            {task.status && (
                              <Chip
                                size="small"
                                color={isCompletedTask ? 'default' : task.claimable ? 'info' : 'success'}
                                variant={isCompletedTask ? 'filled' : 'outlined'}
                                label={task.status}
                              />
                            )}
                            {task.claimable && (
                              <Chip size="small" color="info" variant="outlined" label="Claimable for me" />
                            )}
                            {task.completedAt && (
                              <Typography variant="caption" color="text.secondary">
                                Completed {formatTimestamp(task.completedAt)}
                              </Typography>
                            )}
                            {canRelease && (
                              <Button
                                variant="text"
                                color="warning"
                                size="small"
                                disabled={taskBusy}
                                onClick={(event) => {
                                  event.stopPropagation();
                                  setSelectedTask(task);
                                  void handleReleaseTask(task.id);
                                }}
                              >
                                {taskBusy ? 'Releasing...' : 'Release'}
                              </Button>
                            )}
                            {isCompletedTask ? null : canClaimFromInbox ? (
                              <Button
                                variant="outlined"
                                color="primary"
                                size="small"
                                disabled={taskBusy}
                                onClick={(event) => {
                                  event.stopPropagation();
                                  setSelectedTask(task);
                                  void handleClaimTask(task.id);
                                }}
                              >
                                {taskBusy ? 'Claiming...' : 'Claim'}
                              </Button>
                            ) : canResolveFromInbox ? (
                              <Button
                                variant="contained"
                                color="secondary"
                                size="small"
                                disabled={taskBusy}
                                onClick={() => {
                                  setSelectedTask(task);
                                  setComment('');
                                  setActionDialog('resolveTask');
                                }}
                              >
                                Resolve
                              </Button>
                            ) : (
                              <>
                                <Button
                                  variant="contained"
                                  color="primary"
                                  size="small"
                                  startIcon={<CheckCircle />}
                                  onClick={() => {
                                    setSelectedTask(task);
                                    setActionDialog('approve');
                                  }}
                                  sx={{ mr: 1 }}
                                >
                                  Approve
                                </Button>
                                <Button
                                  variant="outlined"
                                  color="error"
                                  size="small"
                                  startIcon={<Cancel />}
                                  onClick={() => {
                                    setSelectedTask(task);
                                    setActionDialog('reject');
                                  }}
                                >
                                  Reject
                                </Button>
                              </>
                            )}
                          </Box>
                        </ListItemButton>
                      </ListItem>
                    </React.Fragment>
                  );
                })}
              </List>
            )}
          </Paper>
        </Grid>

        <Grid item xs={12} md={7}>
          <Stack spacing={2}>
            <Paper sx={{ p: 2 }}>
              <Box display="flex" justifyContent="space-between" alignItems="center" gap={1} mb={1.5}>
                <Box>
                  <Typography variant="h6">
                    Process Browser
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Browse active or completed processes with richer starter and definition filters, then push context back into the inbox.
                  </Typography>
                </Box>
                <Chip size="small" variant="outlined" label={`${processBrowserTotal} process${processBrowserTotal === 1 ? '' : 'es'}`} />
              </Box>
              <Stack spacing={1.25} sx={{ mb: 1.5 }}>
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.25}>
                  <TextField
                    select
                    size="small"
                    label="Status"
                    value={processBrowserStatus}
                    onChange={(event) => {
                      setProcessBrowserStatus(event.target.value as WorkflowProcessListStatus);
                      setProcessBrowserPage(0);
                    }}
                    sx={{ minWidth: 180 }}
                  >
                    <MenuItem value="ACTIVE">Active</MenuItem>
                    <MenuItem value="COMPLETED">Completed</MenuItem>
                    <MenuItem value="ALL">All</MenuItem>
                  </TextField>
                  <TextField
                    size="small"
                    label="Started by"
                    value={processBrowserStartedByInput}
                    onChange={(event) => setProcessBrowserStartedByInput(event.target.value)}
                    fullWidth
                  />
                </Stack>
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.25}>
                  <TextField
                    size="small"
                    label="Definition key"
                    value={processBrowserDefinitionKeyInput}
                    onChange={(event) => setProcessBrowserDefinitionKeyInput(event.target.value)}
                    fullWidth
                  />
                  <TextField
                    size="small"
                    label="Search"
                    value={processBrowserSearchInput}
                    onChange={(event) => setProcessBrowserSearchInput(event.target.value)}
                    placeholder="Definition, business key, starter..."
                    fullWidth
                    InputProps={{
                      startAdornment: (
                        <InputAdornment position="start">
                          <Search fontSize="small" />
                        </InputAdornment>
                      ),
                    }}
                  />
                </Stack>
              </Stack>
              {processBrowserLoading ? (
                <Typography color="textSecondary">Loading process browser...</Typography>
              ) : processBrowserItems.length === 0 ? (
                <Typography color="textSecondary">No processes match the current browser filters.</Typography>
              ) : (
                <List dense disablePadding>
                  {processBrowserItems.map((item, index) => (
                    <React.Fragment key={item.id}>
                      {index > 0 && <Divider />}
                      <ListItem disableGutters>
                        <ListItemText
                          primary={item.processDefinitionName || item.processDefinitionKey || item.id}
                          secondary={
                            <Stack spacing={0.5} sx={{ mt: 0.5 }}>
                            <Typography component="span" variant="caption" color="textSecondary">
                              Business key: {item.businessKey || 'N/A'}
                            </Typography>
                            <Typography component="span" variant="caption" color="textSecondary">
                              Definition: {item.processDefinitionKey || 'N/A'}
                            </Typography>
                            <Typography component="span" variant="caption" color="textSecondary">
                              Started by: {item.startedBy || 'Unknown'} · {formatTimestamp(item.startTime)}
                            </Typography>
                              <Typography component="span" variant="caption" color="textSecondary">
                                Ended: {item.endTime ? formatTimestamp(item.endTime) : 'Running'}
                              </Typography>
                            </Stack>
                          }
                        />
                        <Stack spacing={0.75} alignItems="flex-end">
                          <Box display="flex" gap={0.75} flexWrap="wrap" justifyContent="flex-end">
                            <Chip size="small" color={item.ended ? 'default' : 'success'} label={item.ended ? 'Completed' : 'Active'} />
                            {selectedTask?.processInstanceId === item.id && (
                              <Chip size="small" color="primary" label="Focused" />
                            )}
                          </Box>
                          <Button size="small" variant="outlined" onClick={() => handleFocusProcess(item)}>
                            Focus in inbox
                          </Button>
                        </Stack>
                      </ListItem>
                    </React.Fragment>
                  ))}
                </List>
              )}
              <Box display="flex" justifyContent="space-between" alignItems="center" mt={1.5}>
                <Typography variant="caption" color="text.secondary">
                  Page {processBrowserPage + 1} · Showing {processBrowserItems.length} of {processBrowserTotal}
                </Typography>
                <Stack direction="row" spacing={1}>
                  <Button
                    size="small"
                    variant="outlined"
                    disabled={processBrowserPage === 0}
                    onClick={() => setProcessBrowserPage((page) => Math.max(0, page - 1))}
                  >
                    Prev
                  </Button>
                  <Button
                    size="small"
                    variant="outlined"
                    disabled={!processBrowserHasMore}
                    onClick={() => setProcessBrowserPage((page) => page + 1)}
                  >
                    Next
                  </Button>
                </Stack>
              </Box>
            </Paper>

            <Paper sx={{ p: 2 }}>
              <Box display="flex" justifyContent="space-between" alignItems="center" gap={1} mb={1.5}>
                <Typography variant="h6">
                  Process Summary
                </Typography>
                {processDetail && !processDetail.ended && (
                  <Button
                    size="small"
                    variant="outlined"
                    color="warning"
                    disabled={processCancelLoading}
                    onClick={() => {
                      setComment('');
                      setActionDialog('cancelProcess');
                    }}
                  >
                    Cancel Process
                  </Button>
                )}
              </Box>
              {detailLoading ? (
                <Typography color="textSecondary">Loading process summary...</Typography>
              ) : processDetail ? (
                <Stack spacing={1}>
                  <Box display="flex" flexWrap="wrap" gap={1}>
                    <Chip size="small" label={processDetail.ended ? 'Ended' : 'Running'} color={processDetail.ended ? 'default' : 'success'} />
                    <Chip size="small" variant="outlined" label={processDetail.suspended ? 'Suspended' : 'Not suspended'} />
                    {processDetail.processDefinitionVersion !== undefined && (
                      <Chip size="small" variant="outlined" label={`v${processDetail.processDefinitionVersion}`} />
                    )}
                  </Box>
                  <Typography variant="body2">
                    <strong>Definition:</strong> {processDetail.processDefinitionName || processDetail.processDefinitionKey || processDetail.processDefinitionId}
                  </Typography>
                  <Typography variant="body2">
                    <strong>Started by:</strong> {renderPersonValue(processDetail.startedBy)}
                  </Typography>
                  <Typography variant="body2">
                    <strong>Start time:</strong> {processDetail.startTime ? format(new Date(processDetail.startTime), 'PPp') : 'N/A'}
                  </Typography>
                  <Typography variant="body2">
                    <strong>End time:</strong> {processDetail.endTime ? format(new Date(processDetail.endTime), 'PPp') : 'Running'}
                  </Typography>
                  <Typography variant="body2">
                    <strong>Business Key:</strong> {processDetail.businessKey || 'N/A'}
                  </Typography>
                  <Box>
                    <Typography variant="subtitle2" gutterBottom>
                      Submission Summary
                    </Typography>
                    {renderSubmissionSummary(processDetail.submissionSummary)}
                  </Box>
                  <Box>
                    <Typography variant="subtitle2" gutterBottom>
                      Involved People
                    </Typography>
                    {renderInvolvedActors(
                      processInvolvedActors,
                      'No involved people or groups are available for this process.'
                    )}
                  </Box>
                  {(processVariables.length > 0 || canEditProcessVariables) && (
                    <Box>
                      <Box display="flex" justifyContent="space-between" alignItems="center" gap={1} mb={1}>
                        <Typography variant="subtitle2">
                          Process Variables
                        </Typography>
                        {canEditProcessVariables && (
                          <Button size="small" variant="outlined" onClick={() => openProcessVariableEditor()}>
                            Set variable
                          </Button>
                        )}
                      </Box>
                      {processVariables.length > 0 && (
                        <>
                          <TextField
                            size="small"
                            label="Filter variables"
                            value={processVariableSearchInput}
                            onChange={(event) => setProcessVariableSearchInput(event.target.value)}
                            placeholder="Name, type, scope, value..."
                            sx={{ mb: 1.25, minWidth: { xs: '100%', sm: 280 } }}
                          />
                          <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap" sx={{ mb: 1.25 }}>
                            {([
                              ['ALL', 'All'],
                              ['STRING', 'String'],
                              ['NUMBER', 'Number'],
                              ['BOOLEAN', 'Boolean'],
                              ['STRUCTURED', 'Structured'],
                              ['OTHER', 'Other'],
                            ] as const).map(([scope, label]) => (
                              <Chip
                                key={`process-variable-${scope}`}
                                size="small"
                                variant={processVariableQuickScope === scope ? 'filled' : 'outlined'}
                                color={processVariableQuickScope === scope ? 'primary' : 'default'}
                                label={`${label} ${processVariableScopeCounts[scope]}`}
                                onClick={() => setProcessVariableQuickScope(scope)}
                              />
                            ))}
                          </Stack>
                        </>
                      )}
                      {processVariables.length > 0 ? (
                        filteredProcessVariables.length > 0 ? (
                          <Stack spacing={1}>
                            {filteredProcessVariables.map((variable) => (
                              <Paper key={variable.name} variant="outlined" sx={{ p: 1 }}>
                                <Box display="flex" justifyContent="space-between" alignItems="flex-start" gap={1}>
                                  <Box minWidth={0}>
                                    <Typography variant="body2" fontWeight={600}>
                                      {variable.name}
                                    </Typography>
                                    <Typography variant="caption" color="textSecondary">
                                      {variable.type} · {variable.scope}
                                    </Typography>
                                    <Typography variant="body2" sx={{ wordBreak: 'break-word' }}>
                                      {formatWorkflowValue(variable.value)}
                                    </Typography>
                                  </Box>
                                  {canEditProcessVariables && (
                                    <Stack direction="row" spacing={0.75}>
                                      <Button size="small" variant="text" onClick={() => openProcessVariableEditor(variable)}>
                                        Edit
                                      </Button>
                                      <Button
                                        size="small"
                                        color="error"
                                        disabled={processVariableDeletingName === variable.name}
                                        onClick={() => void handleDeleteProcessVariable(variable.name)}
                                      >
                                        {processVariableDeletingName === variable.name ? 'Deleting...' : 'Delete'}
                                      </Button>
                                    </Stack>
                                  )}
                                </Box>
                              </Paper>
                            ))}
                          </Stack>
                        ) : (
                          <Typography color="textSecondary">No process variables match the current filter.</Typography>
                        )
                      ) : (
                        <Typography color="textSecondary">No process variables are available.</Typography>
                      )}
                    </Box>
                  )}
                  {renderBusinessItems('Process Business Items', processItems)}
                </Stack>
              ) : (
                <Typography color="textSecondary">Process metadata is unavailable.</Typography>
              )}
            </Paper>

            <Paper sx={{ p: 2 }}>
              <Box display="flex" justifyContent="space-between" alignItems="center" gap={1} mb={1.5}>
                <Typography variant="h6">
                  Task Detail
                </Typography>
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  {canResolveSelectedTask && (
                    <Button
                      size="small"
                      variant="contained"
                      color="secondary"
                      onClick={() => {
                        setComment('');
                        setActionDialog('resolveTask');
                      }}
                    >
                      Resolve Task
                    </Button>
                  )}
                  {canDelegateSelectedTask && (
                    <Button
                      size="small"
                      variant="outlined"
                      onClick={() => {
                        setAssignmentUsername('');
                        setActionDialog('delegateTask');
                      }}
                    >
                      Delegate Task
                    </Button>
                  )}
                  {canAssignSelectedTask && (
                    <Button
                      size="small"
                      variant="outlined"
                      onClick={() => {
                        setAssignmentUsername(taskDetail?.assignee || '');
                        setActionDialog('assignTask');
                      }}
                    >
                      Assign Task
                    </Button>
                  )}
                </Stack>
              </Box>
              {detailLoading ? (
                <Typography color="textSecondary">Loading workflow detail...</Typography>
              ) : taskDetail ? (
                <Stack spacing={1}>
                  <Typography variant="body2">
                    <strong>Task:</strong> {taskDetail.name}
                  </Typography>
                  <Typography variant="body2">
                    <strong>Assignee:</strong> {renderPersonValue(taskDetail.assignee, 'Unassigned')}
                  </Typography>
                  <Typography variant="body2">
                    <strong>Owner:</strong> {renderPersonValue(taskDetail.owner, 'N/A')}
                  </Typography>
                  <Typography variant="body2">
                    <strong>Delegation:</strong> {taskDetail.delegationState || 'N/A'}
                  </Typography>
                  <Typography variant="body2">
                    <strong>Process:</strong> {taskDetail.processDefinitionName || taskDetail.processDefinitionKey || taskDetail.processDefinitionId}
                  </Typography>
                  <Typography variant="body2">
                    <strong>Instance:</strong> {taskDetail.processInstanceId}
                  </Typography>
                  <Typography variant="body2">
                    <strong>Business Key:</strong> {taskDetail.businessKey || 'N/A'}
                  </Typography>
                  <Box>
                    <Typography variant="subtitle2" gutterBottom>
                      Submission Summary
                    </Typography>
                    {renderSubmissionSummary(taskDetail.submissionSummary)}
                  </Box>
                  <Box>
                    <Typography variant="subtitle2" gutterBottom>
                      Involved People
                    </Typography>
                    {renderInvolvedActors(
                      taskInvolvedActors,
                      'No involved people or groups are available for this task.'
                    )}
                  </Box>
                  <Typography variant="body2">
                    <strong>Created:</strong> {format(new Date(taskDetail.createTime), 'PPp')}
                  </Typography>
                  {taskVariables.length > 0 && (
                    <Box>
                      <Typography variant="subtitle2" gutterBottom>
                        Task Variables
                      </Typography>
                      <TextField
                        size="small"
                        label="Filter task variables"
                        value={taskVariableSearchInput}
                        onChange={(event) => setTaskVariableSearchInput(event.target.value)}
                        placeholder="Name, type, scope, value..."
                        sx={{ mb: 1.25, minWidth: { xs: '100%', sm: 280 } }}
                      />
                      <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap" sx={{ mb: 1.25 }}>
                        {([
                          ['ALL', 'All'],
                          ['STRING', 'String'],
                          ['NUMBER', 'Number'],
                          ['BOOLEAN', 'Boolean'],
                          ['STRUCTURED', 'Structured'],
                          ['OTHER', 'Other'],
                        ] as const).map(([scope, label]) => (
                          <Chip
                            key={`task-variable-${scope}`}
                            size="small"
                            variant={taskVariableQuickScope === scope ? 'filled' : 'outlined'}
                            color={taskVariableQuickScope === scope ? 'primary' : 'default'}
                            label={`${label} ${taskVariableScopeCounts[scope]}`}
                            onClick={() => setTaskVariableQuickScope(scope)}
                          />
                        ))}
                      </Stack>
                      {filteredTaskVariables.length > 0 ? (
                        <Box display="flex" flexWrap="wrap" gap={0.75}>
                          {filteredTaskVariables.map((variable) => (
                            <Chip
                              key={variable.name}
                              size="small"
                              variant="outlined"
                              label={`${variable.name}: ${formatWorkflowValue(variable.value)}`}
                            />
                          ))}
                        </Box>
                      ) : (
                        <Typography color="textSecondary">No task variables match the current filter.</Typography>
                      )}
                    </Box>
                  )}
                  <Box>
                    <Typography variant="subtitle2" gutterBottom>
                      Task Candidates
                    </Typography>
                    {renderTaskCandidates()}
                  </Box>
                  {renderBusinessItems('Task Business Items', taskItems)}
                </Stack>
              ) : selectedTaskCompleted && selectedTask ? (
                <Stack spacing={1}>
                  <Typography variant="body2">
                    <strong>Task:</strong> {selectedTask.name}
                  </Typography>
                  <Typography variant="body2">
                    <strong>Status:</strong> {selectedTask.status || 'COMPLETED'}
                  </Typography>
                  <Typography variant="body2">
                    <strong>Assignee:</strong> {renderPersonValue(selectedTask.assignee, 'Unassigned')}
                  </Typography>
                  <Typography variant="body2">
                    <strong>Process:</strong> {selectedTask.processDefinitionName || selectedTask.processDefinitionKey || selectedTask.processDefinitionId || 'Workflow'}
                  </Typography>
                  <Typography variant="body2">
                    <strong>Instance:</strong> {selectedTask.processInstanceId || 'N/A'}
                  </Typography>
                  <Typography variant="body2">
                    <strong>Business Key:</strong> {selectedTask.businessKey || 'N/A'}
                  </Typography>
                  <Typography variant="body2">
                    <strong>Created:</strong> {formatTimestamp(selectedTask.createTime)}
                  </Typography>
                  <Typography variant="body2">
                    <strong>Completed:</strong> {formatTimestamp(selectedTask.completedAt)}
                  </Typography>
                  <Box>
                    <Typography variant="subtitle2" gutterBottom>
                      Involved People
                    </Typography>
                    {renderInvolvedActors(
                      processInvolvedActors,
                      'No involved people or groups are available for this completed workflow.'
                    )}
                  </Box>
                  <Alert severity="info">
                    Completed inbox items are shown in read-only mode. Use the process summary and workflow history panels for deeper traceability.
                  </Alert>
                </Stack>
              ) : (
                <Typography color="textSecondary">Select a task to inspect workflow details.</Typography>
              )}
            </Paper>

            <Paper sx={{ p: 2 }}>
              <Box display="flex" justifyContent="space-between" alignItems="center" gap={1} mb={1.5} flexWrap="wrap">
                <Typography variant="h6">Process Activity Timeline</Typography>
                <Chip size="small" variant="outlined" label={`${filteredProcessActivities.length}/${processActivities.length || 0}`} />
              </Box>
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1.5 }}>
                Server activity filters and local scopes are combined. Counts reflect visible activities.
              </Typography>
                {processActivities.length > 0 && (
                  <>
                    <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap" sx={{ mb: 1.5 }}>
                      {([
                        ['ALL', 'All'],
                        ['RUNNING', 'Running'],
                        ['ENDED', 'Ended'],
                        ['HUMAN', 'Human'],
                        ['SYSTEM', 'System'],
                      ] as const).map(([scope, label]) => (
                        <Chip
                          key={`process-activity-${scope}`}
                          size="small"
                          variant={activityQuickScope === scope ? 'filled' : 'outlined'}
                          color={activityQuickScope === scope ? 'primary' : 'default'}
                          label={`${label} ${processActivityScopeCounts[scope]}`}
                          onClick={() => setActivityQuickScope(scope)}
                        />
                      ))}
                    </Stack>
                  <Stack
                      direction={{ xs: 'column', sm: 'row' }}
                      spacing={1}
                      useFlexGap
                      flexWrap="wrap"
                      sx={{ mb: 1.5 }}
                    >
                      <TextField
                        size="small"
                        label="Filter activities"
                        value={activitySearchInput}
                        onChange={(event) => setActivitySearchInput(event.target.value)}
                        placeholder="Name, type, assignee, task..."
                        sx={{ flex: 1, minWidth: { xs: '100%', sm: 220 } }}
                      />
                      <TextField
                        size="small"
                        label="Assignee"
                        value={activityAssigneeInput}
                        onChange={(event) => setActivityAssigneeInput(event.target.value)}
                        placeholder="Exact assignee"
                        sx={{ flex: 1, minWidth: { xs: '100%', sm: 180 } }}
                      />
                      <TextField
                        size="small"
                        label="Activity type"
                        value={activityTypeInput}
                        onChange={(event) => setActivityTypeInput(event.target.value)}
                        placeholder="Exact activity type"
                        sx={{ flex: 1, minWidth: { xs: '100%', sm: 180 } }}
                      />
                    </Stack>
                    {(activityAssigneeSuggestions.length > 0 || activityTypeSuggestions.length > 0) && (
                      <Stack spacing={1} sx={{ mb: 1.5 }}>
                        {activityAssigneeSuggestions.length > 0 && (
                          <Box>
                            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.75 }}>
                              Assignee suggestions from loaded activities
                            </Typography>
                            <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                              {activityAssigneeSuggestions.map((suggestion) => {
                                const isSelected = activityAssigneeQuery.trim().toLowerCase() === suggestion.value.toLowerCase();
                                return (
                                  <Chip
                                    key={`activity-assignee-${suggestion.value}`}
                                    size="small"
                                    variant={isSelected ? 'filled' : 'outlined'}
                                    color={isSelected ? 'primary' : 'default'}
                                    label={`${suggestion.value} (${suggestion.count})`}
                                    onClick={() => {
                                      setActivityAssigneeInput(suggestion.value);
                                      setActivityAssigneeQuery(suggestion.value);
                                    }}
                                  />
                                );
                              })}
                            </Stack>
                          </Box>
                        )}
                        {activityTypeSuggestions.length > 0 && (
                          <Box>
                            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.75 }}>
                              Activity type suggestions from loaded activities
                            </Typography>
                            <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                              {activityTypeSuggestions.map((suggestion) => {
                                const isSelected = activityTypeQuery.trim().toLowerCase() === suggestion.value.toLowerCase();
                                return (
                                  <Chip
                                    key={`activity-type-${suggestion.value}`}
                                    size="small"
                                    variant={isSelected ? 'filled' : 'outlined'}
                                    color={isSelected ? 'primary' : 'default'}
                                    label={`${suggestion.value} (${suggestion.count})`}
                                    onClick={() => {
                                      setActivityTypeInput(suggestion.value);
                                      setActivityTypeQuery(suggestion.value);
                                    }}
                                  />
                                );
                              })}
                            </Stack>
                          </Box>
                        )}
                      </Stack>
                    )}
                  </>
                )}
              {detailLoading ? (
                <Typography color="textSecondary">Loading process activities...</Typography>
              ) : (
                renderProcessActivities()
              )}
            </Paper>

            <Paper sx={{ p: 2 }}>
              <Typography variant="h6" gutterBottom>
                Current Process Tasks
              </Typography>
              {detailLoading ? (
                <Typography color="textSecondary">Loading process tasks...</Typography>
              ) : processTasks.length === 0 ? (
                <Typography color="textSecondary">No active tasks are attached to this process.</Typography>
              ) : (
                <List dense disablePadding>
                  {processTasks.map((item, index) => (
                    <React.Fragment key={item.id}>
                      {index > 0 && <Divider />}
                      <ListItem disableGutters>
                        <ListItemText
                          primary={item.name}
                          secondary={
                            <>
                              <Typography component="span" variant="caption" color="textSecondary">
                                Assignee: {renderPersonValue(item.assignee, 'Unassigned')}
                                {item.owner ? ` · Owner: ${item.owner}` : ''}
                                {item.delegationState ? ` · Delegation: ${item.delegationState}` : ''}
                              </Typography>
                              <br />
                              <Typography component="span" variant="caption" color="textSecondary">
                                Definition key: {item.taskDefinitionKey || 'N/A'}
                              </Typography>
                            </>
                          }
                        />
                        <Box display="flex" alignItems="center" gap={1}>
                          {selectedTask?.id === item.id && <Chip size="small" color="primary" label="Selected" />}
                          {!item.assignee && (
                            <Button
                              size="small"
                              variant="outlined"
                              disabled={taskLifecycleLoadingId === item.id}
                              onClick={() => void handleClaimTask(item.id)}
                            >
                              {taskLifecycleLoadingId === item.id ? 'Claiming...' : 'Claim'}
                            </Button>
                          )}
                        </Box>
                      </ListItem>
                    </React.Fragment>
                  ))}
                </List>
              )}
            </Paper>

            <Paper sx={{ p: 2 }}>
              <Box display="flex" justifyContent="space-between" alignItems="center" gap={1} mb={1.5} flexWrap="wrap">
                <Typography variant="h6">Process Task History</Typography>
                <Chip size="small" variant="outlined" label={`${filteredProcessTaskHistory.length}/${processTaskHistory.length || 0}`} />
              </Box>
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1.5 }}>
                Server filters and local scopes are combined. Counts reflect visible historic tasks.
              </Typography>
              <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} sx={{ mb: 1.5 }}>
                <TextField
                  size="small"
                  label="Filter history"
                  value={taskHistorySearchInput}
                  onChange={(event) => setTaskHistorySearchInput(event.target.value)}
                  placeholder="Task, assignee, outcome..."
                  sx={{ minWidth: { xs: '100%', md: 240 } }}
                />
                <TextField
                  size="small"
                  label="Assignee"
                  value={taskHistoryAssigneeInput}
                  onChange={(event) => setTaskHistoryAssigneeInput(event.target.value)}
                  placeholder="alice"
                  sx={{ minWidth: { xs: '100%', md: 180 } }}
                />
                <TextField
                  size="small"
                  label="Definition key"
                  value={taskHistoryDefinitionInput}
                  onChange={(event) => setTaskHistoryDefinitionInput(event.target.value)}
                  placeholder="approvalTask"
                  sx={{ minWidth: { xs: '100%', md: 220 } }}
                />
              </Stack>
              {processTaskHistory.length > 0 && (
                <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap" sx={{ mb: 1.5 }}>
                  {([
                    ['ALL', 'All'],
                    ['ASSIGNED', 'Assigned'],
                    ['UNASSIGNED', 'Unassigned'],
                    ['OWNED', 'Owned'],
                    ['OUTCOME', 'Outcome'],
                  ] as const).map(([scope, label]) => (
                    <Chip
                      key={`process-task-history-${scope}`}
                      size="small"
                      variant={taskHistoryQuickScope === scope ? 'filled' : 'outlined'}
                      color={taskHistoryQuickScope === scope ? 'primary' : 'default'}
                      label={`${label} ${processTaskHistoryScopeCounts[scope]}`}
                      onClick={() => setTaskHistoryQuickScope(scope)}
                    />
                  ))}
                </Stack>
              )}
              {detailLoading ? (
                <Typography color="textSecondary">Loading process task history...</Typography>
              ) : (
                renderProcessTaskHistory()
              )}
            </Paper>

            <Paper sx={{ p: 2 }}>
              <Typography variant="h6" gutterBottom>
                Task Form Model
              </Typography>
              {detailLoading ? (
                <Typography color="textSecondary">Loading task form model...</Typography>
              ) : selectedTaskCompleted ? (
                <Typography color="textSecondary">Completed inbox items do not expose an active task form model.</Typography>
              ) : taskFormModel.length === 0 ? (
                <Typography color="textSecondary">No explicit task form fields were published for this workflow task.</Typography>
              ) : (
                <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                  {taskFormModel.map((field) => (
                    <Chip
                      key={field.id}
                      size="small"
                      variant="outlined"
                      label={`${field.title}${field.required ? ' *' : ''} · ${field.type}`}
                    />
                  ))}
                </Stack>
              )}
            </Paper>

            <Paper sx={{ p: 2 }}>
              <Typography variant="h6" gutterBottom>
                Workflow Definition
              </Typography>
              {definitionDetail ? (
                <Stack spacing={1}>
                  <Box display="flex" flexWrap="wrap" gap={1}>
                    <Chip size="small" label={`v${definitionDetail.version}`} />
                    <Chip size="small" label={definitionDetail.suspended ? 'Suspended' : 'Active'} color={definitionDetail.suspended ? 'warning' : 'success'} />
                    <Chip size="small" variant="outlined" label={`Diagram ${definitionDetail.diagramAvailable ? 'Yes' : 'No'}`} />
                    <Chip size="small" variant="outlined" label={`BPMN XML ${definitionDetail.bpmnXmlAvailable ? 'Yes' : 'No'}`} />
                  </Box>
                  <Typography variant="body2">
                    <strong>Name:</strong> {definitionDetail.name}
                  </Typography>
                  <Typography variant="body2">
                    <strong>Key:</strong> {definitionDetail.key}
                  </Typography>
                  <Typography variant="body2">
                    <strong>Deployment:</strong> {definitionDetail.deploymentId}
                  </Typography>
                  <Typography variant="body2">
                    <strong>Resources:</strong> {definitionDetail.resourceNames.join(', ')}
                  </Typography>
                  {definitionDiagramUrl ? (
                    <Box>
                      <Typography variant="subtitle2" gutterBottom>
                        Process Diagram Preview
                      </Typography>
                      <Box
                        component="img"
                        src={definitionDiagramUrl}
                        alt={`${definitionDetail.name} diagram`}
                        sx={{
                          width: '100%',
                          maxHeight: 320,
                          objectFit: 'contain',
                          border: '1px solid',
                          borderColor: 'divider',
                          borderRadius: 1,
                          bgcolor: 'grey.50',
                        }}
                      />
                    </Box>
                  ) : (
                    <Alert severity={definitionDetail.diagramAvailable ? 'warning' : 'info'}>
                      {definitionDiagramError || 'Diagram preview is unavailable.'}
                    </Alert>
                  )}
                  <Button size="small" onClick={() => setShowModelXml((value) => !value)}>
                    {showModelXml ? 'Hide BPMN XML' : 'Show BPMN XML'}
                  </Button>
                  {showModelXml && definitionModel?.xml && (
                    <Paper variant="outlined" sx={{ p: 1.5, maxHeight: 240, overflow: 'auto', bgcolor: 'grey.50' }}>
                      <Typography component="pre" variant="caption" sx={{ whiteSpace: 'pre-wrap', m: 0 }}>
                        {definitionModel.xml}
                      </Typography>
                    </Paper>
                  )}
                </Stack>
              ) : (
                <Typography color="textSecondary">Definition metadata is unavailable.</Typography>
              )}
            </Paper>

            <Paper sx={{ p: 2 }}>
              <Box display="flex" justifyContent="space-between" alignItems="center" gap={1} mb={1.5} flexWrap="wrap">
                <Typography variant="h6">Workflow History</Typography>
                <Chip size="small" variant="outlined" label={`${filteredWorkflowHistory.length}/${workflowHistory.length || 0}`} />
                <TextField
                  size="small"
                  label="Filter workflow history"
                  value={workflowHistorySearchInput}
                  onChange={(event) => setWorkflowHistorySearchInput(event.target.value)}
                  placeholder="Definition, starter, decision..."
                  helperText="Filters are matched locally against workflow history fields."
                  sx={{ minWidth: { xs: '100%', sm: 280 } }}
                />
              </Box>
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1.5 }}>
                Quick scopes and local filters are combined. Counts reflect visible workflow history entries.
              </Typography>
              {workflowHistory.length > 0 && (
                <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap" sx={{ mb: 1.5 }}>
                  {([
                    ['ALL', 'All'],
                    ['RUNNING', 'Running'],
                    ['ENDED', 'Ended'],
                    ['APPROVED', 'Approved'],
                    ['REJECTED', 'Rejected'],
                    ['COMMENTED', 'Commented'],
                    ['REVIEWED', 'Reviewed'],
                  ] as const).map(([scope, label]) => (
                    <Chip
                      key={scope}
                      size="small"
                      variant={workflowHistoryQuickScope === scope ? 'filled' : 'outlined'}
                      color={workflowHistoryQuickScope === scope ? 'primary' : 'default'}
                      label={`${label} ${workflowHistoryScopeCounts[scope]}`}
                      onClick={() => setWorkflowHistoryQuickScope(scope)}
                    />
                  ))}
                </Stack>
              )}
              {workflowHistory.length === 0 ? (
                <Typography color="textSecondary">
                  {taskDetail?.businessKey ? 'No completed workflow history found.' : 'Select a task with a business key to see history.'}
                </Typography>
              ) : filteredWorkflowHistory.length === 0 ? (
                <Typography color="textSecondary">
                  No workflow history entries match the current filter.
                </Typography>
              ) : (
                <List dense disablePadding>
                  {filteredWorkflowHistory.map((item, index) => (
                    <React.Fragment key={item.id}>
                      {index > 0 && <Divider />}
                      <ListItem disableGutters>
                      <ListItemText
                          primary={item.processDefinitionName || item.processDefinitionKey || item.id}
                          secondary={
                            <Stack spacing={0.5} sx={{ mt: 0.5 }}>
                              <Typography component="span" variant="caption" color="textSecondary">
                                Workflow ID: {item.id}
                              </Typography>
                              <Typography component="span" variant="caption" color="textSecondary">
                                Start: {formatTimestamp(item.startTime)}
                              </Typography>
                              <Typography component="span" variant="caption" color="textSecondary">
                                End: {item.endTime ? formatTimestamp(item.endTime) : 'Running'}
                              </Typography>
                              <Typography component="span" variant="caption" color="textSecondary">
                                Started by: {renderPersonValue(item.startedBy)}
                              </Typography>
                              {item.reviewedBy && (
                                <Typography component="span" variant="caption" color="textSecondary">
                                  Reviewed by: {renderPersonValue(item.reviewedBy)}{item.reviewedAt ? ` · ${formatTimestamp(item.reviewedAt)}` : ''}
                                </Typography>
                              )}
                              {item.startComment && (
                                <Typography component="span" variant="caption" color="textSecondary">
                                  Start note: {item.startComment}
                                </Typography>
                              )}
                              {item.comment && (
                                <Typography component="span" variant="caption" color="textSecondary">
                                  Review note: {item.comment}
                                </Typography>
                              )}
                            </Stack>
                          }
                        />
                        <Stack alignItems="flex-end" spacing={0.75}>
                          <Box display="flex" gap={0.75} flexWrap="wrap" justifyContent="flex-end">
                            <Chip
                              size="small"
                              color={item.ended === false ? 'warning' : 'default'}
                              label={item.ended === false ? 'Running' : 'Ended'}
                            />
                            {item.decisionLabel && (
                              <Chip
                                size="small"
                                color={item.decision === 'APPROVED' ? 'success' : item.decision === 'REJECTED' ? 'error' : 'default'}
                                label={item.decisionLabel}
                              />
                            )}
                          </Box>
                          {item.approvers && item.approvers.length > 0 && (
                            <Box display="flex" gap={0.5} flexWrap="wrap" justifyContent="flex-end">
                              {item.approvers.map((approver) => (
                                <React.Fragment key={`${item.id}-${approver}`}>
                                  {renderPersonChip(approver)}
                                </React.Fragment>
                              ))}
                            </Box>
                          )}
                        </Stack>
                      </ListItem>
                    </React.Fragment>
                  ))}
                </List>
              )}
            </Paper>
          </Stack>
        </Grid>
      </Grid>

      {/* Action Dialog */}
      <Dialog
        open={!!actionDialog}
        onClose={() => {
          setActionDialog(null);
          setAssignmentUsername('');
        }}
      >
          <DialogTitle>
          {actionDialog === 'approve'
            ? 'Approve Document'
            : actionDialog === 'reject'
              ? 'Reject Document'
              : actionDialog === 'delegateTask'
                ? 'Delegate Task'
                : actionDialog === 'resolveTask'
                  ? 'Resolve Delegated Task'
              : actionDialog === 'assignTask'
                ? 'Assign Task'
                : 'Cancel Process'}
        </DialogTitle>
        <DialogContent>
          <DialogContentText>
            {actionDialog === 'cancelProcess'
              ? 'Are you sure you want to cancel this process? This will stop the workflow for the selected document.'
              : actionDialog === 'delegateTask'
                ? 'Forward the selected task to another user while retaining owner traceability.'
              : actionDialog === 'resolveTask'
                ? 'Resolve the delegated task and return it to the owner. You can add an optional resolution note below.'
              : actionDialog === 'assignTask'
                ? 'Transfer the selected task to another user account.'
                : `Are you sure you want to ${actionDialog} this document?`
            }
            {actionDialog === 'cancelProcess'
              ? ' You can provide an optional reason below.'
              : actionDialog === 'delegateTask'
                ? ' Search an existing username below.'
              : actionDialog === 'resolveTask'
                ? ''
              : actionDialog === 'assignTask'
                ? ' Enter an existing username below.'
                : approvalField?.placeholder
                ? ` ${approvalField.placeholder}.`
                : ' You can add an optional comment below.'
            }
          </DialogContentText>
          {actionDialog !== 'cancelProcess' && actionDialog !== 'assignTask' && actionDialog !== 'delegateTask' && actionDialog !== 'resolveTask' && taskFormModel.length > 0 && (
            <Box display="flex" gap={1} flexWrap="wrap" sx={{ mt: 1 }}>
              {taskFormModel.map((field) => (
                <Chip
                  key={`dialog-field-${field.id}`}
                  size="small"
                  variant="outlined"
                  label={`${field.title}${field.required ? ' *' : ''}`}
                />
              ))}
            </Box>
          )}
          {actionDialog === 'assignTask' || actionDialog === 'delegateTask' ? (
            <Stack spacing={1.5} sx={{ mt: 1 }}>
              <Autocomplete
                freeSolo
                autoHighlight
                options={assignmentOptions}
                loading={assignmentOptionsLoading}
                value={assignmentOptions.find((option) => option.username === assignmentUsername) || null}
                inputValue={assignmentUsername}
                getOptionLabel={(option) => typeof option === 'string' ? option : formatPersonDisplay(option)}
                isOptionEqualToValue={(option, value) => option.username === value.username}
                onInputChange={(_, value) => setAssignmentUsername(value)}
                onChange={(_, value) => {
                  if (typeof value === 'string') {
                    setAssignmentUsername(value);
                    return;
                  }
                  setAssignmentUsername(value?.username || '');
                }}
                renderOption={(props, option) => (
                  <Box component="li" {...props}>
                    <Box display="flex" alignItems="center" gap={1}>
                      <Avatar sx={{ width: 28, height: 28 }}>
                        {option.username.charAt(0).toUpperCase()}
                      </Avatar>
                      <Box>
                        <Typography variant="body2">{option.username}</Typography>
                        <Typography variant="caption" color="text.secondary">
                          {[option.firstName, option.lastName].filter(Boolean).join(' ').trim() || option.email}
                        </Typography>
                      </Box>
                    </Box>
                  </Box>
                )}
                renderInput={(params) => (
                  <TextField
                    {...params}
                    autoFocus
                    margin="dense"
                    label={actionDialog === 'delegateTask' ? 'Delegate username' : 'Assignee username'}
                    placeholder="Search People Directory"
                    helperText="Search an existing user or type a username directly."
                  />
                )}
              />
              {assignmentUsername && (
                <Box display="flex" alignItems="center" gap={1} flexWrap="wrap">
                  <Typography variant="caption" color="text.secondary">
                    Target assignee:
                  </Typography>
                  {renderPersonChip(assignmentUsername)}
                </Box>
              )}
            </Stack>
          ) : (
            <TextField
              autoFocus
              margin="dense"
              label={actionDialog === 'cancelProcess' ? 'Reason' : actionDialog === 'resolveTask' ? 'Resolution note' : reviewCommentField?.title || 'Comment'}
              fullWidth
              multiline
              rows={3}
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              placeholder={actionDialog === 'cancelProcess'
                ? 'Optional cancellation reason'
                : actionDialog === 'resolveTask'
                  ? 'Optional note for the task owner'
                : reviewCommentField?.placeholder || 'Add optional review notes'}
            />
          )}
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => {
              setActionDialog(null);
              setAssignmentUsername('');
            }}
          >
            Cancel
          </Button>
          {actionDialog === 'cancelProcess' ? (
            <Button
              onClick={() => void handleCancelProcess()}
              color="warning"
              variant="contained"
              disabled={processCancelLoading}
            >
              {processCancelLoading ? 'Cancelling...' : 'Confirm Cancel'}
            </Button>
          ) : actionDialog === 'assignTask' || actionDialog === 'delegateTask' ? (
            <Button
              onClick={() => void handleAssignmentAction()}
              variant="contained"
              disabled={taskAssignLoading}
            >
              {taskAssignLoading ? (actionDialog === 'delegateTask' ? 'Delegating...' : 'Assigning...') : (actionDialog === 'delegateTask' ? 'Delegate' : 'Assign')}
            </Button>
          ) : actionDialog === 'resolveTask' ? (
            <Button
              onClick={() => void handleResolveTask()}
              variant="contained"
              color="secondary"
              disabled={taskAssignLoading}
            >
              {taskAssignLoading ? 'Resolving...' : 'Resolve'}
            </Button>
          ) : (
            <Button
              onClick={() => handleAction(actionDialog === 'approve')}
              color={actionDialog === 'approve' ? 'primary' : 'error'}
              variant="contained"
            >
              Confirm
            </Button>
          )}
        </DialogActions>
      </Dialog>

      <Dialog open={processVariableEditorOpen} onClose={closeProcessVariableEditor} fullWidth maxWidth="sm">
        <DialogTitle>{processVariableName ? 'Edit Process Variable' : 'Set Process Variable'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField
              label="Variable name"
              value={processVariableName}
              onChange={(event) => setProcessVariableName(event.target.value)}
              fullWidth
              autoFocus
            />
            <TextField
              label="Value"
              value={processVariableValue}
              onChange={(event) => setProcessVariableValue(event.target.value)}
              fullWidth
              multiline
              minRows={6}
              helperText="JSON values are parsed automatically. Plain text is stored as-is."
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeProcessVariableEditor} disabled={processVariableSaving}>Cancel</Button>
          <Button onClick={() => void handleSaveProcessVariable()} variant="contained" disabled={processVariableSaving}>
            {processVariableSaving ? 'Saving...' : 'Save variable'}
          </Button>
        </DialogActions>
      </Dialog>

      {previewNode && (
        <React.Suspense
          fallback={(
            <Box
              sx={{
                position: 'fixed',
                inset: 0,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                bgcolor: 'rgba(0,0,0,0.35)',
                zIndex: (theme) => theme.zIndex.modal + 1,
              }}
            >
              <CircularProgress />
            </Box>
          )}
        >
          <DocumentPreview
            open={Boolean(previewNode)}
            onClose={closeDocumentPreview}
            node={previewNode}
            initialCommentsOpen={previewCommentsOpen}
            initialCommentDraftText={previewCommentDraftText}
          />
        </React.Suspense>
      )}
    </Box>
  );
};

export default TasksPage;
