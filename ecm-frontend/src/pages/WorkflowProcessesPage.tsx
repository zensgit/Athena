import React, { Suspense, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  InputAdornment,
  List,
  ListItem,
  ListItemText,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { ChatBubbleOutline, FolderOpen, Search, Visibility } from '@mui/icons-material';
import { format } from 'date-fns';
import { useNavigate } from 'react-router-dom';
import { useAppSelector } from 'store';
import authService from 'services/authService';
import { toast } from 'react-toastify';
import nodeService from 'services/nodeService';
import workflowService, {
  ProcessDefinition,
  WorkflowBusinessItem,
  WorkflowFormModelElement,
  WorkflowHistoricTaskItem,
  WorkflowDefinitionDetail,
  WorkflowDefinitionModel,
  WorkflowHistoryItem,
  WorkflowInvolvedActor,
  WorkflowProcessActivity,
  WorkflowProcessBrowserItem,
  WorkflowProcessDetail,
  WorkflowProcessListStatus,
  WorkflowProcessTask,
  WorkflowSubmissionSummary,
  WorkflowVariableItem,
} from 'services/workflowService';
import { Node } from 'types';

const DocumentPreview = React.lazy(() => import('components/preview/DocumentPreview'));

const PROCESS_PAGE_SIZE = 8;
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

const WorkflowProcessesPage: React.FC = () => {
  const navigate = useNavigate();
  const currentUser = useAppSelector((state) => state.auth.user) ?? authService.getCurrentUser();
  const [status, setStatus] = useState<WorkflowProcessListStatus>('ACTIVE');
  const [searchInput, setSearchInput] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [startedByInput, setStartedByInput] = useState('');
  const [startedByQuery, setStartedByQuery] = useState('');
  const [definitionKeyInput, setDefinitionKeyInput] = useState('');
  const [definitionKeyQuery, setDefinitionKeyQuery] = useState('');
  const [page, setPage] = useState(0);
  const [items, setItems] = useState<WorkflowProcessBrowserItem[]>([]);
  const [total, setTotal] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(false);
  const [selectedProcessId, setSelectedProcessId] = useState<string | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
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
  const [processActivities, setProcessActivities] = useState<WorkflowProcessActivity[]>([]);
  const [activityQuickScope, setActivityQuickScope] = useState<WorkflowActivityQuickScope>('ALL');
  const [activitySearchInput, setActivitySearchInput] = useState('');
  const [activitySearchQuery, setActivitySearchQuery] = useState('');
  const [activityAssigneeInput, setActivityAssigneeInput] = useState('');
  const [activityAssigneeQuery, setActivityAssigneeQuery] = useState('');
  const [activityTypeInput, setActivityTypeInput] = useState('');
  const [activityTypeQuery, setActivityTypeQuery] = useState('');
  const [processInvolvedActors, setProcessInvolvedActors] = useState<WorkflowInvolvedActor[]>([]);
  const [processVariables, setProcessVariables] = useState<WorkflowVariableItem[]>([]);
  const [processVariableSearchInput, setProcessVariableSearchInput] = useState('');
  const [processVariableSearchQuery, setProcessVariableSearchQuery] = useState('');
  const [processItems, setProcessItems] = useState<WorkflowBusinessItem[]>([]);
  const [definitionDetail, setDefinitionDetail] = useState<WorkflowDefinitionDetail | null>(null);
  const [definitionModel, setDefinitionModel] = useState<WorkflowDefinitionModel | null>(null);
  const [definitionDiagramUrl, setDefinitionDiagramUrl] = useState<string | null>(null);
  const [definitionDiagramError, setDefinitionDiagramError] = useState<string | null>(null);
  const [history, setHistory] = useState<WorkflowHistoryItem[]>([]);
  const [historySearchInput, setHistorySearchInput] = useState('');
  const [historySearchQuery, setHistorySearchQuery] = useState('');
  const [historyQuickScope, setHistoryQuickScope] = useState<WorkflowHistoryQuickScope>('ALL');
  const [processVariableQuickScope, setProcessVariableQuickScope] = useState<WorkflowVariableQuickScope>('ALL');
  const [showModelXml, setShowModelXml] = useState(false);
  const [previewNode, setPreviewNode] = useState<Node | null>(null);
  const [previewCommentsOpen, setPreviewCommentsOpen] = useState(false);
  const [previewLoadingItemId, setPreviewLoadingItemId] = useState<string | null>(null);
  const [refreshToken, setRefreshToken] = useState(0);
  const [detailRefreshToken, setDetailRefreshToken] = useState(0);
  const [startDialogOpen, setStartDialogOpen] = useState(false);
  const [startDefinitions, setStartDefinitions] = useState<ProcessDefinition[]>([]);
  const [startDefinitionsLoading, setStartDefinitionsLoading] = useState(false);
  const [startDefinitionId, setStartDefinitionId] = useState('');
  const [startFormModel, setStartFormModel] = useState<WorkflowFormModelElement[]>([]);
  const [startBusinessKey, setStartBusinessKey] = useState('');
  const [startVariablesText, setStartVariablesText] = useState('{}');
  const [startItemsText, setStartItemsText] = useState('');
  const [startSubmitting, setStartSubmitting] = useState(false);
  const [variableEditorOpen, setVariableEditorOpen] = useState(false);
  const [variableEditorName, setVariableEditorName] = useState('');
  const [variableEditorValue, setVariableEditorValue] = useState('');
  const [variableEditorSaving, setVariableEditorSaving] = useState(false);
  const [variableDeletingName, setVariableDeletingName] = useState<string | null>(null);

  const selectedProcess = useMemo(
    () => items.find((item) => item.id === selectedProcessId) || null,
    [items, selectedProcessId]
  );

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

  const filteredProcessActivities = useMemo(() => {
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
  }, [activityAssigneeQuery, activityQuickScope, activitySearchQuery, activityTypeQuery, processActivities]);

  const processActivityScopeCounts = useMemo(() => ({
    ALL: processActivities.length,
    RUNNING: processActivities.filter((activity) => matchesWorkflowActivityQuickScope(activity, 'RUNNING')).length,
    ENDED: processActivities.filter((activity) => matchesWorkflowActivityQuickScope(activity, 'ENDED')).length,
    HUMAN: processActivities.filter((activity) => matchesWorkflowActivityQuickScope(activity, 'HUMAN')).length,
    SYSTEM: processActivities.filter((activity) => matchesWorkflowActivityQuickScope(activity, 'SYSTEM')).length,
  }), [processActivities]);

  const activityAssigneeSuggestions = useMemo(() => {
    const suggestions = Array.from(new Set(
      processActivities
        .map((activity) => activity.assignee?.trim())
        .filter((value): value is string => Boolean(value))
    ));
    return suggestions.slice(0, 6);
  }, [processActivities]);

  const activityTypeSuggestions = useMemo(() => {
    const suggestions = Array.from(new Set(
      processActivities
        .map((activity) => activity.activityType?.trim())
        .filter((value): value is string => Boolean(value))
    ));
    return suggestions.slice(0, 6);
  }, [processActivities]);

  const filteredHistory = useMemo(() => {
    const query = historySearchQuery.trim().toLowerCase();
    return history.filter((entry) => {
      if (!matchesWorkflowHistoryQuickScope(entry, historyQuickScope)) {
        return false;
      }
      if (!query) {
        return true;
      }

      return (
      [
        entry.businessKey,
        entry.processDefinitionKey,
        entry.processDefinitionName,
        entry.startedBy,
        entry.decision,
        entry.decisionLabel,
        entry.startComment,
        entry.comment,
        entry.reviewedBy,
      ]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(query))
      );
    });
  }, [history, historyQuickScope, historySearchQuery]);

  const historyScopeCounts = useMemo(() => ({
    ALL: history.length,
    RUNNING: history.filter((entry) => matchesWorkflowHistoryQuickScope(entry, 'RUNNING')).length,
    ENDED: history.filter((entry) => matchesWorkflowHistoryQuickScope(entry, 'ENDED')).length,
    APPROVED: history.filter((entry) => matchesWorkflowHistoryQuickScope(entry, 'APPROVED')).length,
    REJECTED: history.filter((entry) => matchesWorkflowHistoryQuickScope(entry, 'REJECTED')).length,
    COMMENTED: history.filter((entry) => matchesWorkflowHistoryQuickScope(entry, 'COMMENTED')).length,
    REVIEWED: history.filter((entry) => matchesWorkflowHistoryQuickScope(entry, 'REVIEWED')).length,
  }), [history]);

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

  const processVariableScopeCounts = useMemo(() => ({
    ALL: processVariables.length,
    STRING: processVariables.filter((variable) => matchesWorkflowVariableQuickScope(variable, 'STRING')).length,
    NUMBER: processVariables.filter((variable) => matchesWorkflowVariableQuickScope(variable, 'NUMBER')).length,
    BOOLEAN: processVariables.filter((variable) => matchesWorkflowVariableQuickScope(variable, 'BOOLEAN')).length,
    STRUCTURED: processVariables.filter((variable) => matchesWorkflowVariableQuickScope(variable, 'STRUCTURED')).length,
    OTHER: processVariables.filter((variable) => matchesWorkflowVariableQuickScope(variable, 'OTHER')).length,
  }), [processVariables]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setSearchQuery(searchInput.trim());
      setPage(0);
    }, 250);
    return () => window.clearTimeout(timer);
  }, [searchInput]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setStartedByQuery(startedByInput.trim());
      setPage(0);
    }, 250);
    return () => window.clearTimeout(timer);
  }, [startedByInput]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDefinitionKeyQuery(definitionKeyInput.trim());
      setPage(0);
    }, 250);
    return () => window.clearTimeout(timer);
  }, [definitionKeyInput]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setTaskHistorySearchQuery(taskHistorySearchInput.trim());
    }, 250);
    return () => window.clearTimeout(timer);
  }, [taskHistorySearchInput]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setTaskHistoryAssigneeQuery(taskHistoryAssigneeInput.trim());
    }, 250);
    return () => window.clearTimeout(timer);
  }, [taskHistoryAssigneeInput]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setTaskHistoryDefinitionQuery(taskHistoryDefinitionInput.trim());
    }, 250);
    return () => window.clearTimeout(timer);
  }, [taskHistoryDefinitionInput]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setActivitySearchQuery(activitySearchInput.trim());
    }, 250);
    return () => window.clearTimeout(timer);
  }, [activitySearchInput]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setActivityAssigneeQuery(activityAssigneeInput.trim());
    }, 250);
    return () => window.clearTimeout(timer);
  }, [activityAssigneeInput]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setActivityTypeQuery(activityTypeInput.trim());
    }, 250);
    return () => window.clearTimeout(timer);
  }, [activityTypeInput]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setHistorySearchQuery(historySearchInput.trim());
    }, 250);
    return () => window.clearTimeout(timer);
  }, [historySearchInput]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setProcessVariableSearchQuery(processVariableSearchInput.trim());
    }, 250);
    return () => window.clearTimeout(timer);
  }, [processVariableSearchInput]);

  useEffect(() => {
    return () => {
      if (definitionDiagramUrl) {
        window.URL.revokeObjectURL(definitionDiagramUrl);
      }
    };
  }, [definitionDiagramUrl]);

  useEffect(() => {
    if (!startDialogOpen) {
      return;
    }

    let cancelled = false;
    setStartDefinitionsLoading(true);
    workflowService.getDefinitions()
      .then((definitions) => {
        if (cancelled) {
          return;
        }
        setStartDefinitions(definitions || []);
        setStartDefinitionId((current) => current || definitions?.[0]?.id || '');
      })
      .catch(() => {
        if (!cancelled) {
          setStartDefinitions([]);
          toast.error('Failed to load workflow definitions');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setStartDefinitionsLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [startDialogOpen]);

  useEffect(() => {
    if (!startDialogOpen || !startDefinitionId) {
      setStartFormModel([]);
      return;
    }

    let cancelled = false;
    workflowService.getStartFormModel(startDefinitionId)
      .then((fields) => {
        if (!cancelled) {
          setStartFormModel(fields || []);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setStartFormModel([]);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [startDefinitionId, startDialogOpen]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);

    workflowService.listProcesses(
      status,
      undefined,
      startedByQuery || undefined,
      definitionKeyQuery || undefined,
      searchQuery || undefined,
      page * PROCESS_PAGE_SIZE,
      PROCESS_PAGE_SIZE
    )
      .then((response) => {
        if (cancelled) {
          return;
        }
        const nextItems = response.items || [];
        setItems(nextItems);
        setTotal(response.paging?.totalItems || 0);
        setHasMore(Boolean(response.paging?.hasMoreItems));
        setSelectedProcessId((current) => {
          if (current && nextItems.some((item) => item.id === current)) {
            return current;
          }
          return nextItems[0]?.id || null;
        });
      })
      .catch(() => {
        if (!cancelled) {
          setItems([]);
          setTotal(0);
          setHasMore(false);
          setSelectedProcessId(null);
          toast.error('Failed to load process browser');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [definitionKeyQuery, page, refreshToken, searchQuery, startedByQuery, status]);

  useEffect(() => {
    if (!selectedProcessId) {
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
      setDefinitionDiagramError(null);
      setHistory([]);
      return;
    }

    let cancelled = false;
    setDetailLoading(true);

    const loadDetail = async () => {
      try {
        const detail = await workflowService.getProcessDetail(selectedProcessId);
        if (cancelled) {
          return;
        }
        setProcessDetail(detail);

        const [
          tasksResult,
          taskHistoryResult,
          activitiesResult,
          involvedActorsResult,
          variablesResult,
          itemsResult,
          definitionResult,
          modelResult,
          historyResult,
        ] = await Promise.allSettled([
          workflowService.getProcessTasks(detail.id),
          workflowService.getProcessTaskHistory(detail.id, {
            query: taskHistorySearchQuery || undefined,
            assignee: taskHistoryAssigneeQuery || undefined,
            taskDefinitionKey: taskHistoryDefinitionQuery || undefined,
          }),
          workflowService.getProcessActivities(detail.id, {
            query: activitySearchQuery || undefined,
            assignee: activityAssigneeQuery || undefined,
            activityType: activityTypeQuery || undefined,
          }),
          workflowService.getProcessInvolvedActors(detail.id),
          workflowService.getProcessVariables(detail.id),
          workflowService.getProcessItems(detail.id),
          workflowService.getDefinitionDetail(detail.processDefinitionId),
          workflowService.getDefinitionModel(detail.processDefinitionId),
          detail.businessKey ? workflowService.getDocumentHistory(detail.businessKey) : Promise.resolve([] as WorkflowHistoryItem[]),
        ]);

        if (cancelled) {
          return;
        }

        setProcessTasks(tasksResult.status === 'fulfilled' ? tasksResult.value || [] : []);
        setProcessTaskHistory(taskHistoryResult.status === 'fulfilled' ? taskHistoryResult.value || [] : []);
        setProcessActivities(activitiesResult.status === 'fulfilled' ? activitiesResult.value || [] : []);
        setProcessInvolvedActors(involvedActorsResult.status === 'fulfilled' ? involvedActorsResult.value || [] : []);
        setProcessVariables(variablesResult.status === 'fulfilled' ? variablesResult.value || [] : []);
        setProcessItems(itemsResult.status === 'fulfilled' ? itemsResult.value || [] : []);
        setDefinitionDetail(definitionResult.status === 'fulfilled' ? definitionResult.value : null);
        setDefinitionModel(modelResult.status === 'fulfilled' ? modelResult.value : null);
        setHistory(historyResult.status === 'fulfilled' ? historyResult.value || [] : []);

        if (definitionResult.status === 'fulfilled' && definitionResult.value.diagramAvailable) {
          try {
            const diagramBlob = await workflowService.getProcessDiagram(detail.id)
              .catch(() => workflowService.getDefinitionDiagram(detail.processDefinitionId));
            if (cancelled) {
              return;
            }
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
          toast.error('Failed to load process details');
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
          setDefinitionDiagramError(null);
          setHistory([]);
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
  }, [
    activityAssigneeQuery,
    activitySearchQuery,
    activityTypeQuery,
    detailRefreshToken,
    selectedProcessId,
    taskHistoryAssigneeQuery,
    taskHistoryDefinitionQuery,
    taskHistorySearchQuery,
  ]);

  const openPeopleDirectoryProfile = (username?: string | null) => {
    if (!username) {
      return;
    }
    navigate(`/people-directory?username=${encodeURIComponent(username)}`);
  };

  const renderPersonValue = (username?: string | null, fallback = 'Unknown') => {
    if (!username) {
      return fallback;
    }
    return (
      <Button
        size="small"
        variant="text"
        sx={{ minWidth: 0, px: 0, textTransform: 'none' }}
        onClick={() => openPeopleDirectoryProfile(username)}
      >
        {username}
      </Button>
    );
  };

  const renderSubmissionSummary = (summary?: WorkflowSubmissionSummary | null) => {
    if (!summary) {
      return (
        <Typography color="text.secondary">
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
          {summary.approvers?.map((approver) => (
            <Chip key={approver} size="small" variant="outlined" label={`@${approver}`} onClick={() => openPeopleDirectoryProfile(approver)} />
          ))}
        </Box>
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
            <strong>Reviewed by:</strong> {renderPersonValue(summary.reviewedBy)}
            {summary.reviewedAt ? ` · ${formatTimestamp(summary.reviewedAt)}` : ''}
          </Typography>
        )}
        {summary.comment && (
          <Typography variant="body2">
            <strong>Review note:</strong> {summary.comment}
          </Typography>
        )}
      </Stack>
    );
  };

  const closeDocumentPreview = () => {
    setPreviewNode(null);
    setPreviewCommentsOpen(false);
  };

  const openBusinessItem = async (item: WorkflowBusinessItem, discuss = false) => {
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
      setPreviewCommentsOpen(discuss);
    } catch {
      toast.error('Failed to open workflow item');
    } finally {
      setPreviewLoadingItemId((current) => (current === item.id ? null : current));
    }
  };

  const renderBusinessItems = (title: string, businessItems: WorkflowBusinessItem[]) => (
    <Box>
      <Box display="flex" alignItems="center" gap={1} mb={1}>
        <Typography variant="subtitle2">{title}</Typography>
        <Chip size="small" variant="outlined" label={`${businessItems.length} item${businessItems.length === 1 ? '' : 's'}`} />
      </Box>
      {businessItems.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          No workflow business items are attached to this process.
        </Typography>
      ) : (
        <Stack spacing={1}>
          {businessItems.map((item) => {
            const normalizedType = item.nodeType?.trim().toUpperCase();
            const isFolder = normalizedType === 'FOLDER';
            const isLoading = previewLoadingItemId === item.id;
            return (
              <Paper key={item.id} variant="outlined" sx={{ p: 1.25 }}>
                <Stack spacing={1}>
                  <Box display="flex" justifyContent="space-between" gap={1}>
                    <Box minWidth={0}>
                      <Typography variant="body2" fontWeight={600} noWrap title={item.name}>
                        {item.name}
                      </Typography>
                      <Typography variant="caption" color="text.secondary" sx={{ wordBreak: 'break-word' }}>
                        {item.nodeType} · {item.path}
                      </Typography>
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
                          onClick={() => void openBusinessItem(item, true)}
                          disabled={isLoading}
                        >
                          Discuss
                        </Button>
                      </>
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

  const renderProcessActivities = () => {
    if (processActivities.length === 0) {
      return (
        <Typography color="text.secondary">
          No process activities are available for this workflow instance.
        </Typography>
      );
    }

    if (filteredProcessActivities.length === 0) {
      return (
        <Typography color="text.secondary">
          {(activitySearchQuery || activityAssigneeQuery || activityTypeQuery || activityQuickScope !== 'ALL')
            ? 'No process activities match the current filters.'
            : 'No process activities match the current scope.'}
        </Typography>
      );
    }

    return (
      <List disablePadding>
        {filteredProcessActivities.map((activity, index) => (
          <React.Fragment key={activity.id}>
            {index > 0 && <Divider />}
            <ListItem disableGutters sx={{ py: 1.25 }}>
              <ListItemText
                primary={activity.activityName || activity.activityId || activity.activityType || activity.id}
                secondary={
                  <Stack spacing={0.5} sx={{ mt: 0.5 }}>
                    <Typography component="span" variant="caption" color="text.secondary">
                      Type: {activity.activityType || 'N/A'}
                      {activity.taskId ? ` · Task ${activity.taskId}` : ''}
                    </Typography>
                    <Typography component="span" variant="caption" color="text.secondary">
                      Start: {formatTimestamp(activity.startTime)}
                      {activity.endTime ? ` · End: ${formatTimestamp(activity.endTime)}` : ' · Running'}
                    </Typography>
                    {(activity.assignee || activity.durationInMillis !== undefined) && (
                      <Typography component="span" variant="caption" color="text.secondary">
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

  const renderInvolvedActors = () => {
    if (processInvolvedActors.length === 0) {
      return (
        <Typography color="text.secondary">
          No involved people or groups are available for this workflow instance.
        </Typography>
      );
    }

    return (
      <List disablePadding>
        {processInvolvedActors.map((actor, index) => {
          const label = actor.userId || actor.groupId || actor.displayName || 'Unknown';
          const isUser = Boolean(actor.userId);
          return (
            <React.Fragment key={`${actor.userId || actor.groupId || 'actor'}-${index}`}>
              {index > 0 && <Divider />}
              <ListItem disableGutters sx={{ py: 1.25 }}>
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

  const openStartProcessDialog = () => {
    setStartDialogOpen(true);
    setStartBusinessKey('');
    setStartVariablesText('{}');
    setStartItemsText('');
  };

  const closeStartProcessDialog = () => {
    if (startSubmitting) {
      return;
    }
    setStartDialogOpen(false);
  };

  const handleStartProcess = async () => {
    if (!startDefinitionId) {
      toast.error('Workflow definition is required');
      return;
    }

    let parsedVariables: Record<string, any> = {};
    try {
      parsedVariables = startVariablesText.trim() ? JSON.parse(startVariablesText) : {};
    } catch {
      toast.error('Variables must be valid JSON');
      return;
    }

    if (parsedVariables === null || Array.isArray(parsedVariables) || typeof parsedVariables !== 'object') {
      toast.error('Variables JSON must be an object');
      return;
    }

    const items = startItemsText
      .split(/[\s,]+/)
      .map((value) => value.trim())
      .filter(Boolean)
      .map((id) => ({ id }));

    try {
      setStartSubmitting(true);
      const instance = await workflowService.startProcess({
        processDefinitionId: startDefinitionId,
        businessKey: startBusinessKey.trim() || undefined,
        variables: parsedVariables,
        items,
      });
      toast.success('Workflow process started');
      setStartDialogOpen(false);
      setStatus('ACTIVE');
      setPage(0);
      setSelectedProcessId(instance.id);
      setRefreshToken((current) => current + 1);
    } catch {
      toast.error('Failed to start workflow process');
    } finally {
      setStartSubmitting(false);
    }
  };

  const openVariableEditor = (variable?: WorkflowVariableItem) => {
    setVariableEditorName(variable?.name || '');
    setVariableEditorValue(
      variable
        ? typeof variable.value === 'string'
          ? variable.value
          : JSON.stringify(variable.value, null, 2)
        : ''
    );
    setVariableEditorOpen(true);
  };

  const closeVariableEditor = () => {
    if (variableEditorSaving) {
      return;
    }
    setVariableEditorOpen(false);
    setVariableEditorName('');
    setVariableEditorValue('');
  };

  const handleSaveProcessVariable = async () => {
    if (!selectedProcessId) {
      return;
    }
    const variableName = variableEditorName.trim();
    if (!variableName) {
      toast.error('Variable name is required');
      return;
    }

    let parsedValue: any = variableEditorValue;
    const normalizedValue = variableEditorValue.trim();
    if (!normalizedValue) {
      parsedValue = '';
    } else {
      try {
        parsedValue = JSON.parse(normalizedValue);
      } catch {
        parsedValue = variableEditorValue;
      }
    }

    try {
      setVariableEditorSaving(true);
      await workflowService.setProcessVariable(selectedProcessId, variableName, parsedValue);
      toast.success(`Process variable ${variableName} saved`);
      setVariableEditorOpen(false);
      setVariableEditorName('');
      setVariableEditorValue('');
      setDetailRefreshToken((current) => current + 1);
      setRefreshToken((current) => current + 1);
    } catch {
      toast.error('Failed to save process variable');
    } finally {
      setVariableEditorSaving(false);
    }
  };

  const handleDeleteProcessVariable = async (variableName: string) => {
    if (!selectedProcessId) {
      return;
    }

    try {
      setVariableDeletingName(variableName);
      await workflowService.deleteProcessVariable(selectedProcessId, variableName);
      toast.success(`Process variable ${variableName} deleted`);
      setDetailRefreshToken((current) => current + 1);
      setRefreshToken((current) => current + 1);
    } catch {
      toast.error('Failed to delete process variable');
    } finally {
      setVariableDeletingName((current) => (current === variableName ? null : current));
    }
  };

  const renderProcessTaskHistory = () => {
    if (filteredProcessTaskHistory.length === 0) {
      return (
        <Typography color="text.secondary">
          {(taskHistorySearchQuery || taskHistoryAssigneeQuery || taskHistoryDefinitionQuery || taskHistoryQuickScope !== 'ALL')
            ? 'No completed process tasks match the current filters.'
            : 'No completed process tasks are recorded yet.'}
        </Typography>
      );
    }

    return (
      <List disablePadding>
        {filteredProcessTaskHistory.map((task, index) => (
          <React.Fragment key={task.id}>
            {index > 0 && <Divider />}
            <ListItem disableGutters sx={{ py: 1.25 }}>
              <ListItemText
                primary={task.name || task.taskDefinitionKey || task.id}
                secondary={
                  <Stack spacing={0.5} sx={{ mt: 0.5 }}>
                    <Typography component="span" variant="caption" color="text.secondary">
                      Assignee: {task.assignee || 'Unassigned'}
                      {task.owner ? ` · Owner: ${task.owner}` : ''}
                    </Typography>
                    <Typography component="span" variant="caption" color="text.secondary">
                      Start: {formatTimestamp(task.startTime)}
                      {task.endTime ? ` · End: ${formatTimestamp(task.endTime)}` : ''}
                    </Typography>
                    {(task.durationInMillis !== undefined || task.deleteReason) && (
                      <Typography component="span" variant="caption" color="text.secondary">
                        {task.durationInMillis !== undefined ? `Duration ${task.durationInMillis} ms` : 'Duration N/A'}
                        {task.deleteReason ? ` · ${task.deleteReason}` : ''}
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

  return (
    <Box p={3}>
      <Box display="flex" justifyContent="space-between" alignItems="flex-start" gap={2} flexWrap="wrap" mb={2}>
        <Box>
          <Typography variant="h4" gutterBottom>
            Workflow Processes
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Browse active and completed workflow processes with diagram, history, variables, and direct document collaboration entry points.
          </Typography>
        </Box>
        <Button variant="contained" onClick={openStartProcessDialog}>
          Start Process
        </Button>
      </Box>

      <Grid container spacing={2}>
        <Grid item xs={12} md={4.5}>
          <Paper sx={{ p: 2 }}>
            <Box display="flex" alignItems="center" justifyContent="space-between" gap={1} mb={1.5}>
              <Box>
                <Typography variant="h6">Process Browser</Typography>
                <Typography variant="caption" color="text.secondary">
                  Search by definition, business key, starter, or decision and switch between active and completed instances.
                </Typography>
              </Box>
              <Chip size="small" variant="outlined" label={`${total} process${total === 1 ? '' : 'es'}`} />
            </Box>

            <Stack spacing={1.25} sx={{ mb: 1.5 }}>
              <TextField
                select
                size="small"
                label="Status"
                value={status}
                onChange={(event) => {
                  setStatus(event.target.value as WorkflowProcessListStatus);
                  setPage(0);
                }}
              >
                <MenuItem value="ACTIVE">Active</MenuItem>
                <MenuItem value="COMPLETED">Completed</MenuItem>
                <MenuItem value="ALL">All</MenuItem>
              </TextField>
              <TextField
                size="small"
                label="Search"
                value={searchInput}
                onChange={(event) => setSearchInput(event.target.value)}
                placeholder="Definition, business key, starter..."
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <Search fontSize="small" />
                    </InputAdornment>
                  ),
                }}
              />
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.25}>
                <TextField
                  size="small"
                  label="Started by"
                  value={startedByInput}
                  onChange={(event) => setStartedByInput(event.target.value)}
                  fullWidth
                />
                <TextField
                  size="small"
                  label="Definition key"
                  value={definitionKeyInput}
                  onChange={(event) => setDefinitionKeyInput(event.target.value)}
                  fullWidth
                />
              </Stack>
            </Stack>

            {loading ? (
              <Typography color="text.secondary">Loading process browser...</Typography>
            ) : items.length === 0 ? (
              <Alert severity="info">No processes match the current browser filters.</Alert>
            ) : (
              <List disablePadding>
                {items.map((item, index) => (
                  <React.Fragment key={item.id}>
                    {index > 0 && <Divider />}
                    <ListItem
                      disableGutters
                      sx={{
                        py: 1.25,
                        cursor: 'pointer',
                        borderRadius: 1,
                        px: 1,
                        backgroundColor: selectedProcessId === item.id ? 'action.selected' : 'transparent',
                      }}
                      onClick={() => setSelectedProcessId(item.id)}
                    >
                      <ListItemText
                        primary={item.processDefinitionName || item.processDefinitionKey || item.id}
                        secondary={
                          <Stack spacing={0.5} sx={{ mt: 0.5 }}>
                            <Typography component="span" variant="caption" color="text.secondary">
                              Business key: {item.businessKey || 'N/A'}
                            </Typography>
                            <Typography component="span" variant="caption" color="text.secondary">
                              Definition: {item.processDefinitionKey || 'N/A'}
                            </Typography>
                            <Typography component="span" variant="caption" color="text.secondary">
                              Started by: {item.startedBy || 'Unknown'} · {formatTimestamp(item.startTime)}
                            </Typography>
                            <Typography component="span" variant="caption" color="text.secondary">
                              Ended: {item.endTime ? formatTimestamp(item.endTime) : 'Running'}
                            </Typography>
                          </Stack>
                        }
                      />
                      <Stack spacing={0.75} alignItems="flex-end">
                        <Chip size="small" color={item.ended ? 'default' : 'success'} label={item.ended ? 'Completed' : 'Active'} />
                        <Button
                          size="small"
                          variant="text"
                          onClick={(event) => {
                            event.stopPropagation();
                            if (item.businessKey) {
                              navigate('/tasks');
                              toast.info('Opened task workbench. Use the business key from this process to narrow the inbox.');
                            } else {
                              navigate('/tasks');
                            }
                          }}
                        >
                          Open tasks
                        </Button>
                      </Stack>
                    </ListItem>
                  </React.Fragment>
                ))}
              </List>
            )}

            <Box display="flex" justifyContent="space-between" alignItems="center" mt={1.5}>
              <Typography variant="caption" color="text.secondary">
                Page {page + 1} · Showing {items.length} of {total}
              </Typography>
              <Stack direction="row" spacing={1}>
                <Button
                  size="small"
                  variant="outlined"
                  disabled={page === 0}
                  onClick={() => setPage((current) => Math.max(0, current - 1))}
                >
                  Prev
                </Button>
                <Button
                  size="small"
                  variant="outlined"
                  disabled={!hasMore}
                  onClick={() => setPage((current) => current + 1)}
                >
                  Next
                </Button>
              </Stack>
            </Box>
          </Paper>
        </Grid>

        <Grid item xs={12} md={7.5}>
          <Stack spacing={2}>
            <Paper sx={{ p: 2 }}>
              <Typography variant="h6" gutterBottom>
                Process Summary
              </Typography>
              {detailLoading ? (
                <Typography color="text.secondary">Loading process summary...</Typography>
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
                    <strong>Business key:</strong> {processDetail.businessKey || 'N/A'}
                  </Typography>
                  <Typography variant="body2">
                    <strong>Started by:</strong> {renderPersonValue(processDetail.startedBy)}
                  </Typography>
                  <Typography variant="body2">
                    <strong>Started:</strong> {formatTimestamp(processDetail.startTime)}
                  </Typography>
                  <Typography variant="body2">
                    <strong>Ended:</strong> {formatTimestamp(processDetail.endTime)}
                  </Typography>
                </Stack>
              ) : (
                <Typography color="text.secondary">
                  Select a process to inspect details.
                </Typography>
              )}
            </Paper>

            <Paper sx={{ p: 2 }}>
              <Typography variant="h6" gutterBottom>
                Submission Summary
              </Typography>
              {detailLoading ? (
                <Typography color="text.secondary">Loading submission summary...</Typography>
              ) : (
                renderSubmissionSummary(processDetail?.submissionSummary || selectedProcess?.submissionSummary)
              )}
            </Paper>

            <Paper sx={{ p: 2 }}>
              <Typography variant="h6" gutterBottom>
                Involved People
              </Typography>
              {detailLoading ? (
                <Typography color="text.secondary">Loading involved actors...</Typography>
              ) : (
                renderInvolvedActors()
              )}
            </Paper>

            <Paper sx={{ p: 2 }}>
              <Typography variant="h6" gutterBottom>
                Current Process Tasks
              </Typography>
              {detailLoading ? (
                <Typography color="text.secondary">Loading process tasks...</Typography>
              ) : processTasks.length > 0 ? (
                <List disablePadding>
                  {processTasks.map((task, index) => (
                    <React.Fragment key={task.id}>
                      {index > 0 && <Divider />}
                      <ListItem disableGutters sx={{ py: 1.25 }}>
                        <ListItemText
                          primary={task.name}
                          secondary={
                            <Stack spacing={0.5} sx={{ mt: 0.5 }}>
                              <Typography component="span" variant="caption" color="text.secondary">
                                Assignee: {task.assignee || 'Unassigned'}
                              </Typography>
                              <Typography component="span" variant="caption" color="text.secondary">
                                Created: {formatTimestamp(task.createTime)}
                              </Typography>
                              <Typography component="span" variant="caption" color="text.secondary">
                                Due: {formatTimestamp(task.dueDate)}
                              </Typography>
                            </Stack>
                          }
                        />
                      </ListItem>
                    </React.Fragment>
                  ))}
                </List>
              ) : (
                <Typography color="text.secondary">
                  No process tasks are currently active.
                </Typography>
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
                <Typography color="text.secondary">Loading process task history...</Typography>
              ) : (
                renderProcessTaskHistory()
              )}
            </Paper>

            <Paper sx={{ p: 2 }}>
              <Box display="flex" justifyContent="space-between" alignItems="center" gap={1} mb={1.5} flexWrap="wrap">
                <Typography variant="h6">Process Activity Timeline</Typography>
                <Chip size="small" variant="outlined" label={`${filteredProcessActivities.length}/${processActivities.length || 0}`} />
              </Box>
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1.5 }}>
                Server text, assignee, and type filters combine with local scopes. Counts reflect visible activities.
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
                  <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ mb: 1.5 }}>
                    <TextField
                      size="small"
                      label="Filter activities"
                      value={activitySearchInput}
                      onChange={(event) => setActivitySearchInput(event.target.value)}
                      placeholder="Name, type, assignee, task..."
                      fullWidth
                    />
                    <TextField
                      size="small"
                      label="Assignee"
                      value={activityAssigneeInput}
                      onChange={(event) => setActivityAssigneeInput(event.target.value)}
                      placeholder="alice"
                      fullWidth
                    />
                    <TextField
                      size="small"
                      label="Activity type"
                      value={activityTypeInput}
                      onChange={(event) => setActivityTypeInput(event.target.value)}
                      placeholder="userTask"
                      fullWidth
                    />
                  </Stack>
                  {(activityAssigneeSuggestions.length > 0 || activityTypeSuggestions.length > 0) && (
                    <Stack spacing={1} sx={{ mb: 1.5 }}>
                      {activityAssigneeSuggestions.length > 0 && (
                        <Box>
                          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.75 }}>
                            Quick assignees
                          </Typography>
                          <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                            {activityAssigneeSuggestions.map((assignee) => (
                              <Chip
                                key={`activity-assignee-${assignee}`}
                                size="small"
                                variant={activityAssigneeQuery === assignee ? 'filled' : 'outlined'}
                                color={activityAssigneeQuery === assignee ? 'primary' : 'default'}
                                label={assignee}
                                onClick={() => setActivityAssigneeInput(assignee)}
                              />
                            ))}
                            {activityAssigneeQuery && (
                              <Chip
                                size="small"
                                variant="outlined"
                                label="Clear assignee"
                                onClick={() => setActivityAssigneeInput('')}
                              />
                            )}
                          </Stack>
                        </Box>
                      )}
                      {activityTypeSuggestions.length > 0 && (
                        <Box>
                          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.75 }}>
                            Quick activity types
                          </Typography>
                          <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                            {activityTypeSuggestions.map((activityType) => (
                              <Chip
                                key={`activity-type-${activityType}`}
                                size="small"
                                variant={activityTypeQuery === activityType ? 'filled' : 'outlined'}
                                color={activityTypeQuery === activityType ? 'primary' : 'default'}
                                label={activityType}
                                onClick={() => setActivityTypeInput(activityType)}
                              />
                            ))}
                            {activityTypeQuery && (
                              <Chip
                                size="small"
                                variant="outlined"
                                label="Clear type"
                                onClick={() => setActivityTypeInput('')}
                              />
                            )}
                          </Stack>
                        </Box>
                      )}
                    </Stack>
                  )}
                </>
              )}
              {detailLoading ? (
                <Typography color="text.secondary">Loading process activities...</Typography>
              ) : (
                renderProcessActivities()
              )}
            </Paper>

            <Paper sx={{ p: 2 }}>
              {renderBusinessItems('Process Business Items', processItems)}
            </Paper>

            <Paper sx={{ p: 2 }}>
              <Box display="flex" justifyContent="space-between" alignItems="center" gap={1} mb={1.5}>
                <Typography variant="h6">Process Variables</Typography>
                {canEditProcessVariables && (
                  <Button size="small" variant="outlined" onClick={() => openVariableEditor()}>
                    Set variable
                  </Button>
                )}
              </Box>
              {detailLoading ? (
                <Typography color="text.secondary">Loading process variables...</Typography>
              ) : processVariables.length > 0 ? (
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
                  {filteredProcessVariables.length > 0 ? (
                    <Stack spacing={1}>
                      {filteredProcessVariables.map((variable) => (
                        <Paper key={`${variable.scope}-${variable.name}`} variant="outlined" sx={{ p: 1.25 }}>
                          <Box display="flex" justifyContent="space-between" alignItems="flex-start" gap={1}>
                            <Box minWidth={0}>
                              <Typography variant="subtitle2">
                                {variable.name}
                              </Typography>
                              <Typography variant="body2" color="text.secondary">
                                {variable.type} · {variable.scope}
                              </Typography>
                              <Typography variant="body2" sx={{ wordBreak: 'break-word' }}>
                                {formatWorkflowValue(variable.value)}
                              </Typography>
                            </Box>
                            {canEditProcessVariables && (
                              <Stack direction="row" spacing={0.75}>
                                <Button size="small" variant="text" onClick={() => openVariableEditor(variable)}>
                                  Edit
                                </Button>
                                <Button
                                  size="small"
                                  color="error"
                                  disabled={variableDeletingName === variable.name}
                                  onClick={() => void handleDeleteProcessVariable(variable.name)}
                                >
                                  {variableDeletingName === variable.name ? 'Deleting...' : 'Delete'}
                                </Button>
                              </Stack>
                            )}
                          </Box>
                        </Paper>
                      ))}
                    </Stack>
                  ) : (
                    <Typography color="text.secondary">
                      No process variables match the current filter.
                    </Typography>
                  )}
                </>
              ) : (
                <Stack spacing={1}>
                  <Typography color="text.secondary">
                    No process variables are available.
                  </Typography>
                  {canEditProcessVariables && (
                    <Button size="small" variant="outlined" onClick={() => openVariableEditor()} sx={{ alignSelf: 'flex-start' }}>
                      Create first variable
                    </Button>
                  )}
                </Stack>
              )}
            </Paper>

            <Paper sx={{ p: 2 }}>
              <Box display="flex" justifyContent="space-between" alignItems="center" gap={1} mb={1.5}>
                <Typography variant="h6">Workflow Model</Typography>
                {definitionModel?.xml && (
                  <Button size="small" variant="outlined" onClick={() => setShowModelXml((current) => !current)}>
                    {showModelXml ? 'Hide XML' : 'Show XML'}
                  </Button>
                )}
              </Box>
              {detailLoading ? (
                <Typography color="text.secondary">Loading model metadata...</Typography>
              ) : definitionDetail ? (
                <Stack spacing={1}>
                  <Typography variant="body2">
                    <strong>Name:</strong> {definitionDetail.name}
                  </Typography>
                  <Typography variant="body2">
                    <strong>Key:</strong> {definitionDetail.key}
                  </Typography>
                  <Typography variant="body2">
                    <strong>Deployment:</strong> {definitionDetail.deploymentId}
                  </Typography>
                  {definitionDiagramUrl ? (
                    <Box
                      component="img"
                      src={definitionDiagramUrl}
                      alt={`${definitionDetail.name} diagram`}
                      sx={{ width: '100%', borderRadius: 1, border: '1px solid', borderColor: 'divider' }}
                    />
                  ) : definitionDiagramError ? (
                    <Alert severity="info">{definitionDiagramError}</Alert>
                  ) : null}
                  {showModelXml && definitionModel?.xml && (
                    <Box
                      component="pre"
                      sx={{
                        m: 0,
                        p: 1.5,
                        overflow: 'auto',
                        borderRadius: 1,
                        bgcolor: 'grey.100',
                        fontSize: 12,
                      }}
                    >
                      {definitionModel.xml}
                    </Box>
                  )}
                </Stack>
              ) : (
                <Typography color="text.secondary">
                  No workflow model metadata is available for the selected process.
                </Typography>
              )}
            </Paper>

            <Paper sx={{ p: 2 }}>
              <Box display="flex" justifyContent="space-between" alignItems="center" gap={1} mb={1.5}>
                <Typography variant="h6">Workflow History</Typography>
                <Chip size="small" variant="outlined" label={`${filteredHistory.length}/${history.length || 0}`} />
              </Box>
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1.5 }}>
                Quick scopes and local filters are combined. Counts reflect visible workflow history entries.
              </Typography>
              <TextField
                label="Filter history"
                value={historySearchInput}
                onChange={(event) => setHistorySearchInput(event.target.value)}
                placeholder="Business key, started by, decision, comment..."
                helperText="Filters are matched locally against workflow history fields."
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <Search fontSize="small" />
                    </InputAdornment>
                  ),
                }}
                fullWidth
                size="small"
                sx={{ mb: 1.5 }}
              />
              {history.length > 0 && (
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
                      variant={historyQuickScope === scope ? 'filled' : 'outlined'}
                      color={historyQuickScope === scope ? 'primary' : 'default'}
                      label={`${label} ${historyScopeCounts[scope]}`}
                      onClick={() => setHistoryQuickScope(scope)}
                    />
                  ))}
                </Stack>
              )}
              {detailLoading ? (
                <Typography color="text.secondary">Loading workflow history...</Typography>
              ) : history.length === 0 ? (
                <Typography color="text.secondary">
                  No workflow history entries are linked to this business key.
                </Typography>
              ) : filteredHistory.length > 0 ? (
                <Stack spacing={1}>
                  {filteredHistory.map((entry) => (
                    <Box key={entry.id} sx={{ borderBottom: '1px solid', borderColor: 'divider', pb: 1 }}>
                      <Typography variant="subtitle2">
                        {entry.processDefinitionName || entry.processDefinitionKey || entry.id}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {entry.startedBy || 'Unknown'} · {formatTimestamp(entry.startTime)}
                        {entry.endTime ? ` -> ${formatTimestamp(entry.endTime)}` : ''}
                      </Typography>
                      {(entry.startComment || entry.comment) && (
                        <Typography variant="body2">
                          {[entry.startComment, entry.comment].filter(Boolean).join(' / ')}
                        </Typography>
                      )}
                    </Box>
                  ))}
                </Stack>
              ) : (
                <Typography color="text.secondary">
                  No workflow history entries match the current filter.
                </Typography>
              )}
            </Paper>
          </Stack>
        </Grid>
      </Grid>

      <Dialog open={startDialogOpen} onClose={closeStartProcessDialog} maxWidth="sm" fullWidth>
        <DialogTitle>Start Workflow Process</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              select
              label="Definition"
              value={startDefinitionId}
              onChange={(event) => setStartDefinitionId(event.target.value)}
              disabled={startDefinitionsLoading}
              helperText="Pick the workflow definition to start."
              fullWidth
            >
              {startDefinitions.map((definition) => (
                <MenuItem key={definition.id} value={definition.id}>
                  {definition.name || definition.key} ({definition.key})
                </MenuItem>
              ))}
            </TextField>
            {startFormModel.length > 0 && (
              <Box display="flex" gap={1} flexWrap="wrap">
                {startFormModel.map((field) => (
                  <Chip
                    key={field.id}
                    size="small"
                    variant="outlined"
                    label={`${field.title}${field.required ? ' *' : ''}`}
                  />
                ))}
              </Box>
            )}
            <TextField
              label="Business key"
              value={startBusinessKey}
              onChange={(event) => setStartBusinessKey(event.target.value)}
              placeholder="Optional unique business reference"
              fullWidth
            />
            <TextField
              label="Variables JSON"
              value={startVariablesText}
              onChange={(event) => setStartVariablesText(event.target.value)}
              placeholder='{"priority":"high"}'
              multiline
              minRows={5}
              fullWidth
            />
            <TextField
              label="Attached item IDs"
              value={startItemsText}
              onChange={(event) => setStartItemsText(event.target.value)}
              placeholder="UUIDs separated by comma, space, or newline"
              multiline
              minRows={3}
              helperText="These repository items will be attached to the new process context."
              fullWidth
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeStartProcessDialog} disabled={startSubmitting}>
            Cancel
          </Button>
          <Button onClick={() => void handleStartProcess()} variant="contained" disabled={startSubmitting}>
            {startSubmitting ? 'Starting...' : 'Start'}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={variableEditorOpen} onClose={closeVariableEditor} maxWidth="sm" fullWidth>
        <DialogTitle>{variableEditorName ? 'Edit Process Variable' : 'Set Process Variable'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Variable name"
              value={variableEditorName}
              onChange={(event) => setVariableEditorName(event.target.value)}
              fullWidth
              autoFocus
            />
            <TextField
              label="Value"
              value={variableEditorValue}
              onChange={(event) => setVariableEditorValue(event.target.value)}
              multiline
              minRows={6}
              helperText="JSON values are parsed automatically. Plain text is stored as-is."
              fullWidth
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeVariableEditor} disabled={variableEditorSaving}>Cancel</Button>
          <Button onClick={() => void handleSaveProcessVariable()} variant="contained" disabled={variableEditorSaving}>
            {variableEditorSaving ? 'Saving...' : 'Save variable'}
          </Button>
        </DialogActions>
      </Dialog>

      <Suspense fallback={null}>
        {previewNode && (
          <DocumentPreview
            node={previewNode}
            open={Boolean(previewNode)}
            onClose={closeDocumentPreview}
            initialCommentsOpen={previewCommentsOpen}
          />
        )}
      </Suspense>
    </Box>
  );
};

export default WorkflowProcessesPage;
