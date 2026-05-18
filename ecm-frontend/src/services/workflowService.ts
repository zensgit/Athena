import api from './api';

export const WORKFLOW_UNEXPECTED_RESPONSE_MESSAGE =
  'Workflow endpoint returned an unexpected response. Backend route may be missing or the request may have received an HTML fallback.';

export interface ProcessDefinition {
  id: string;
  key: string;
  name: string;
  version: number;
}

export interface Task {
  id: string;
  name: string;
  assignee: string;
  owner?: string;
  delegationState?: string;
  description: string;
  createTime: string;
  status?: string;
  completedAt?: string;
  dueDate?: string;
  processInstanceId?: string;
  processDefinitionId?: string;
  taskDefinitionKey?: string;
  processDefinitionKey?: string;
  processDefinitionName?: string;
  processDefinitionVersion?: number;
  businessKey?: string;
  startedBy?: string;
  claimable?: boolean;
}

export type TaskInboxScope =
  | 'my'
  | 'claimable'
  | 'unassigned'
  | 'all'
  | 'completed'
  | 'involved';

export interface TaskInboxQuery {
  scope?: TaskInboxScope;
  query?: string;
  businessKey?: string;
  assignee?: string;
  processId?: string;
  owner?: string;
  candidateUser?: string;
  candidateGroup?: string;
}

export interface ProcessInstance {
  id: string;
  definitionKey: string;
  businessKey: string;
  ended: boolean;
}

export interface WorkflowProcessStartItem {
  id: string;
}

export interface WorkflowProcessStartRequest {
  processDefinitionId?: string;
  processDefinitionKey?: string;
  businessKey?: string;
  variables?: Record<string, any>;
  items?: WorkflowProcessStartItem[];
}

export interface WorkflowHistoryItem {
  id: string;
  businessKey: string;
  processDefinitionKey?: string;
  processDefinitionName?: string;
  startedBy?: string;
  startTime: string;
  endTime?: string;
  ended?: boolean;
  approvers?: string[];
  startComment?: string;
  decision?: string;
  decisionLabel?: string;
  reviewedBy?: string;
  reviewedAt?: string;
  comment?: string;
}

export interface WorkflowSubmissionSummary {
  approvers: string[];
  startComment?: string;
  startFormSubmittedBy?: string;
  startFormSubmittedAt?: string;
  decision?: string;
  decisionLabel?: string;
  reviewedBy?: string;
  reviewedAt?: string;
  comment?: string;
}

export interface WorkflowProcessDetail {
  id: string;
  processDefinitionId: string;
  processDefinitionKey?: string;
  processDefinitionName?: string;
  processDefinitionVersion?: number;
  businessKey?: string;
  startedBy?: string;
  startTime?: string;
  endTime?: string;
  suspended: boolean;
  ended: boolean;
  variables?: Record<string, any>;
  submissionSummary?: WorkflowSubmissionSummary;
}

export interface WorkflowVariableItem {
  name: string;
  type: string;
  value: any;
  scope: string;
}

export interface WorkflowBusinessItem {
  id: string;
  name: string;
  nodeType: string;
  path: string;
  businessKey?: string;
  source?: string;
}

export interface WorkflowProcessTask {
  id: string;
  name: string;
  assignee?: string;
  owner?: string;
  description?: string;
  createTime: string;
  dueDate?: string;
  taskDefinitionKey?: string;
  delegationState?: string;
}

export interface WorkflowProcessActivity {
  id: string;
  activityId?: string;
  activityName?: string;
  activityType?: string;
  executionId?: string;
  taskId?: string;
  assignee?: string;
  startTime?: string;
  endTime?: string;
  durationInMillis?: number;
}

export interface WorkflowHistoricTaskItem {
  id: string;
  name?: string;
  assignee?: string;
  owner?: string;
  description?: string;
  taskDefinitionKey?: string;
  startTime?: string;
  endTime?: string;
  durationInMillis?: number;
  deleteReason?: string;
}

export interface WorkflowTaskCandidate {
  userId?: string;
  groupId?: string;
  type?: string;
}

export interface WorkflowInvolvedActor {
  userId?: string;
  groupId?: string;
  displayName?: string;
  roles: string[];
}

export interface WorkflowTaskDetail {
  id: string;
  name: string;
  description?: string;
  assignee?: string;
  owner?: string;
  delegationState?: string;
  createTime: string;
  dueDate?: string;
  processInstanceId: string;
  processDefinitionId: string;
  taskDefinitionKey?: string;
  processDefinitionKey?: string;
  processDefinitionName?: string;
  processDefinitionVersion?: number;
  businessKey?: string;
  processVariables?: Record<string, any>;
  processDefinitionSuspended?: boolean;
  submissionSummary?: WorkflowSubmissionSummary;
}

export interface WorkflowDefinitionDetail {
  id: string;
  key: string;
  name: string;
  description?: string;
  version: number;
  category?: string;
  deploymentId: string;
  tenantId?: string;
  suspended: boolean;
  hasStartFormKey: boolean;
  hasGraphicalNotation: boolean;
  modelResourceName?: string;
  diagramResourceName?: string;
  bpmnXmlAvailable: boolean;
  diagramAvailable: boolean;
  resourceNames: string[];
}

export interface WorkflowDefinitionModel {
  definitionId: string;
  resourceName?: string;
  xml?: string;
}

export type WorkflowProcessListStatus = 'ACTIVE' | 'COMPLETED' | 'ALL';

export interface WorkflowProcessBrowserItem {
  id: string;
  processDefinitionId?: string;
  processDefinitionKey?: string;
  processDefinitionName?: string;
  processDefinitionVersion?: number;
  businessKey?: string;
  startedBy?: string;
  startTime?: string;
  endTime?: string;
  ended: boolean;
  submissionSummary?: WorkflowSubmissionSummary;
}

export interface WorkflowProcessBrowserListResponse {
  items: WorkflowProcessBrowserItem[];
  paging: {
    skipCount: number;
    maxItems: number;
    totalItems: number;
    hasMoreItems: boolean;
  };
}

export interface WorkflowFormOption {
  label: string;
  value: string;
}

export interface WorkflowFormModelElement {
  id: string;
  name: string;
  title: string;
  type: string;
  required: boolean;
  readOnly: boolean;
  repeated: boolean;
  placeholder?: string;
  defaultValue?: any;
  options: WorkflowFormOption[];
  scope: 'start' | 'task' | string;
}

export type WorkflowTaskTransitionState =
  | 'completed'
  | 'claimed'
  | 'unclaimed'
  | 'assigned'
  | 'delegated'
  | 'resolved';

export interface WorkflowTaskTransitionRequest {
  state: WorkflowTaskTransitionState;
  assignee?: string;
  values?: Record<string, any>;
}

const TASK_SCOPE_TO_API_SCOPE: Record<TaskInboxScope, string> = {
  my: 'ASSIGNED',
  claimable: 'CLAIMABLE',
  unassigned: 'SHARED',
  all: 'ALL_ACTIVE',
  completed: 'COMPLETED',
  involved: 'INVOLVED',
};

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

const isFiniteNumber = (value: unknown): value is number =>
  typeof value === 'number' && Number.isFinite(value);

const isOptionalNullableString = (
  value: unknown
): value is string | null | undefined =>
  value === undefined || value === null || typeof value === 'string';

const isOptionalNullableNumber = (
  value: unknown
): value is number | null | undefined =>
  value === undefined || value === null || isFiniteNumber(value);

const isStringArray = (value: unknown): value is string[] =>
  Array.isArray(value) && value.every((item) => typeof item === 'string');

function assertWorkflowResponse(condition: unknown): asserts condition {
  if (!condition) {
    throw new Error(WORKFLOW_UNEXPECTED_RESPONSE_MESSAGE);
  }
}

const assertWorkflowArray = <T>(
  value: unknown,
  itemGuard: (item: unknown) => T
): T[] => {
  assertWorkflowResponse(Array.isArray(value));
  return value.map(itemGuard);
};

const assertSubmissionSummary = (
  value: unknown
): WorkflowSubmissionSummary | undefined => {
  if (value === undefined || value === null) {
    return value as undefined;
  }
  assertWorkflowResponse(isRecord(value));
  assertWorkflowResponse(isStringArray(value.approvers));
  assertWorkflowResponse(isOptionalNullableString(value.startComment));
  assertWorkflowResponse(isOptionalNullableString(value.startFormSubmittedBy));
  assertWorkflowResponse(isOptionalNullableString(value.startFormSubmittedAt));
  assertWorkflowResponse(isOptionalNullableString(value.decision));
  assertWorkflowResponse(isOptionalNullableString(value.decisionLabel));
  assertWorkflowResponse(isOptionalNullableString(value.reviewedBy));
  assertWorkflowResponse(isOptionalNullableString(value.reviewedAt));
  assertWorkflowResponse(isOptionalNullableString(value.comment));
  return value as unknown as WorkflowSubmissionSummary;
};

const assertProcessDefinition = (value: unknown): ProcessDefinition => {
  assertWorkflowResponse(isRecord(value));
  assertWorkflowResponse(typeof value.id === 'string');
  assertWorkflowResponse(typeof value.key === 'string');
  assertWorkflowResponse(typeof value.name === 'string');
  assertWorkflowResponse(isFiniteNumber(value.version));
  return value as unknown as ProcessDefinition;
};

const assertProcessInstance = (value: unknown): ProcessInstance => {
  assertWorkflowResponse(isRecord(value));
  assertWorkflowResponse(typeof value.id === 'string');
  assertWorkflowResponse(isOptionalNullableString(value.definitionKey));
  assertWorkflowResponse(isOptionalNullableString(value.businessKey));
  assertWorkflowResponse(typeof value.ended === 'boolean');
  return value as unknown as ProcessInstance;
};

const assertTask = (value: unknown): Task => {
  assertWorkflowResponse(isRecord(value));
  assertWorkflowResponse(typeof value.id === 'string');
  assertWorkflowResponse(isOptionalNullableString(value.name));
  assertWorkflowResponse(isOptionalNullableString(value.assignee));
  assertWorkflowResponse(isOptionalNullableString(value.description));
  assertWorkflowResponse(isOptionalNullableString(value.createTime));
  assertWorkflowResponse(isOptionalNullableString(value.status));
  assertWorkflowResponse(isOptionalNullableString(value.completedAt));
  assertWorkflowResponse(isOptionalNullableString(value.dueDate));
  assertWorkflowResponse(isOptionalNullableString(value.processInstanceId));
  assertWorkflowResponse(isOptionalNullableString(value.processDefinitionId));
  assertWorkflowResponse(isOptionalNullableString(value.taskDefinitionKey));
  assertWorkflowResponse(isOptionalNullableString(value.processDefinitionKey));
  assertWorkflowResponse(isOptionalNullableString(value.processDefinitionName));
  assertWorkflowResponse(
    isOptionalNullableNumber(value.processDefinitionVersion)
  );
  assertWorkflowResponse(isOptionalNullableString(value.businessKey));
  assertWorkflowResponse(isOptionalNullableString(value.startedBy));
  assertWorkflowResponse(
    value.claimable === undefined || typeof value.claimable === 'boolean'
  );
  return value as unknown as Task;
};

const assertTaskDetail = (value: unknown): WorkflowTaskDetail => {
  assertWorkflowResponse(isRecord(value));
  assertWorkflowResponse(typeof value.id === 'string');
  assertWorkflowResponse(isOptionalNullableString(value.name));
  assertWorkflowResponse(isOptionalNullableString(value.processInstanceId));
  assertWorkflowResponse(isOptionalNullableString(value.processDefinitionId));
  assertWorkflowResponse(
    value.processVariables === undefined ||
      value.processVariables === null ||
      isRecord(value.processVariables)
  );
  assertWorkflowResponse(
    value.processDefinitionSuspended === undefined ||
      typeof value.processDefinitionSuspended === 'boolean'
  );
  assertSubmissionSummary(value.submissionSummary);
  return value as unknown as WorkflowTaskDetail;
};

const assertProcessDetail = (value: unknown): WorkflowProcessDetail => {
  assertWorkflowResponse(isRecord(value));
  assertWorkflowResponse(typeof value.id === 'string');
  assertWorkflowResponse(isOptionalNullableString(value.processDefinitionId));
  assertWorkflowResponse(typeof value.suspended === 'boolean');
  assertWorkflowResponse(typeof value.ended === 'boolean');
  assertWorkflowResponse(
    value.variables === undefined ||
      value.variables === null ||
      isRecord(value.variables)
  );
  assertSubmissionSummary(value.submissionSummary);
  return value as unknown as WorkflowProcessDetail;
};

const assertProcessBrowserItem = (
  value: unknown
): WorkflowProcessBrowserItem => {
  assertWorkflowResponse(isRecord(value));
  assertWorkflowResponse(typeof value.id === 'string');
  assertWorkflowResponse(typeof value.ended === 'boolean');
  assertWorkflowResponse(isOptionalNullableString(value.processDefinitionId));
  assertWorkflowResponse(
    isOptionalNullableNumber(value.processDefinitionVersion)
  );
  assertSubmissionSummary(value.submissionSummary);
  return value as unknown as WorkflowProcessBrowserItem;
};

const assertProcessBrowserList = (
  value: unknown
): WorkflowProcessBrowserListResponse => {
  assertWorkflowResponse(isRecord(value));
  assertWorkflowArray(value.items, assertProcessBrowserItem);
  assertWorkflowResponse(isRecord(value.paging));
  assertWorkflowResponse(isFiniteNumber(value.paging.skipCount));
  assertWorkflowResponse(isFiniteNumber(value.paging.maxItems));
  assertWorkflowResponse(isFiniteNumber(value.paging.totalItems));
  assertWorkflowResponse(typeof value.paging.hasMoreItems === 'boolean');
  return value as unknown as WorkflowProcessBrowserListResponse;
};

const assertProcessTask = (value: unknown): WorkflowProcessTask => {
  assertWorkflowResponse(isRecord(value));
  assertWorkflowResponse(typeof value.id === 'string');
  assertWorkflowResponse(isOptionalNullableString(value.name));
  assertWorkflowResponse(isOptionalNullableString(value.createTime));
  return value as unknown as WorkflowProcessTask;
};

const assertHistoricTask = (value: unknown): WorkflowHistoricTaskItem => {
  assertWorkflowResponse(isRecord(value));
  assertWorkflowResponse(typeof value.id === 'string');
  assertWorkflowResponse(isOptionalNullableString(value.name));
  assertWorkflowResponse(isOptionalNullableNumber(value.durationInMillis));
  return value as unknown as WorkflowHistoricTaskItem;
};

const assertProcessActivity = (value: unknown): WorkflowProcessActivity => {
  assertWorkflowResponse(isRecord(value));
  assertWorkflowResponse(typeof value.id === 'string');
  assertWorkflowResponse(isOptionalNullableString(value.activityId));
  assertWorkflowResponse(isOptionalNullableNumber(value.durationInMillis));
  return value as unknown as WorkflowProcessActivity;
};

const assertVariableItem = (value: unknown): WorkflowVariableItem => {
  assertWorkflowResponse(isRecord(value));
  assertWorkflowResponse(typeof value.name === 'string');
  assertWorkflowResponse(typeof value.type === 'string');
  assertWorkflowResponse(typeof value.scope === 'string');
  return value as unknown as WorkflowVariableItem;
};

const assertBusinessItem = (value: unknown): WorkflowBusinessItem => {
  assertWorkflowResponse(isRecord(value));
  assertWorkflowResponse(typeof value.id === 'string');
  assertWorkflowResponse(typeof value.name === 'string');
  assertWorkflowResponse(typeof value.nodeType === 'string');
  assertWorkflowResponse(typeof value.path === 'string');
  return value as unknown as WorkflowBusinessItem;
};

const assertTaskCandidate = (value: unknown): WorkflowTaskCandidate => {
  assertWorkflowResponse(isRecord(value));
  assertWorkflowResponse(isOptionalNullableString(value.userId));
  assertWorkflowResponse(isOptionalNullableString(value.groupId));
  assertWorkflowResponse(isOptionalNullableString(value.type));
  assertWorkflowResponse(
    value.userId !== undefined ||
      value.groupId !== undefined ||
      value.type !== undefined
  );
  return value as unknown as WorkflowTaskCandidate;
};

const assertInvolvedActor = (value: unknown): WorkflowInvolvedActor => {
  assertWorkflowResponse(isRecord(value));
  assertWorkflowResponse(isOptionalNullableString(value.userId));
  assertWorkflowResponse(isOptionalNullableString(value.groupId));
  assertWorkflowResponse(isOptionalNullableString(value.displayName));
  assertWorkflowResponse(isStringArray(value.roles));
  return value as unknown as WorkflowInvolvedActor;
};

const assertDefinitionDetail = (value: unknown): WorkflowDefinitionDetail => {
  assertWorkflowResponse(isRecord(value));
  assertWorkflowResponse(typeof value.id === 'string');
  assertWorkflowResponse(typeof value.key === 'string');
  assertWorkflowResponse(typeof value.name === 'string');
  assertWorkflowResponse(isFiniteNumber(value.version));
  assertWorkflowResponse(typeof value.suspended === 'boolean');
  assertWorkflowResponse(typeof value.hasStartFormKey === 'boolean');
  assertWorkflowResponse(typeof value.hasGraphicalNotation === 'boolean');
  assertWorkflowResponse(typeof value.bpmnXmlAvailable === 'boolean');
  assertWorkflowResponse(typeof value.diagramAvailable === 'boolean');
  assertWorkflowResponse(isStringArray(value.resourceNames));
  return value as unknown as WorkflowDefinitionDetail;
};

const assertDefinitionModel = (value: unknown): WorkflowDefinitionModel => {
  assertWorkflowResponse(isRecord(value));
  assertWorkflowResponse(typeof value.definitionId === 'string');
  assertWorkflowResponse(isOptionalNullableString(value.resourceName));
  assertWorkflowResponse(isOptionalNullableString(value.xml));
  return value as unknown as WorkflowDefinitionModel;
};

const assertFormModelOption = (value: unknown): WorkflowFormOption => {
  assertWorkflowResponse(isRecord(value));
  assertWorkflowResponse(typeof value.label === 'string');
  assertWorkflowResponse(typeof value.value === 'string');
  return value as unknown as WorkflowFormOption;
};

const assertFormModelElement = (value: unknown): WorkflowFormModelElement => {
  assertWorkflowResponse(isRecord(value));
  assertWorkflowResponse(typeof value.id === 'string');
  assertWorkflowResponse(typeof value.name === 'string');
  assertWorkflowResponse(typeof value.title === 'string');
  assertWorkflowResponse(typeof value.type === 'string');
  assertWorkflowResponse(typeof value.required === 'boolean');
  assertWorkflowResponse(typeof value.readOnly === 'boolean');
  assertWorkflowResponse(typeof value.repeated === 'boolean');
  assertWorkflowArray(value.options, assertFormModelOption);
  assertWorkflowResponse(typeof value.scope === 'string');
  return value as unknown as WorkflowFormModelElement;
};

const assertHistoryItem = (value: unknown): WorkflowHistoryItem => {
  assertWorkflowResponse(isRecord(value));
  assertWorkflowResponse(typeof value.id === 'string');
  assertWorkflowResponse(isOptionalNullableString(value.businessKey));
  assertWorkflowResponse(isOptionalNullableString(value.startTime));
  assertWorkflowResponse(isOptionalNullableString(value.endTime));
  assertWorkflowResponse(
    value.approvers === undefined ||
      value.approvers === null ||
      isStringArray(value.approvers)
  );
  assertWorkflowResponse(
    value.ended === undefined || typeof value.ended === 'boolean'
  );
  return value as unknown as WorkflowHistoryItem;
};

const workflowService = {
  getDefinitions: async () => {
    return assertWorkflowArray(
      await api.get<unknown>('/workflows/definitions'),
      assertProcessDefinition
    );
  },

  startApproval: async (
    documentId: string,
    approvers: string[],
    comment: string
  ) => {
    return assertProcessInstance(
      await api.post<unknown>(`/workflows/document/${documentId}/approval`, {
        approvers,
        comment,
      })
    );
  },

  submitStartForm: async (documentId: string, values: Record<string, any>) => {
    return assertProcessInstance(
      await api.post<unknown>(
        `/workflows/document/${documentId}/approval/form-submit`,
        {
          values,
        }
      )
    );
  },

  startProcess: async (request: WorkflowProcessStartRequest) => {
    return assertProcessInstance(
      await api.post<unknown>('/workflows/processes', request)
    );
  },

  getTaskInbox: async (query: TaskInboxQuery = {}) => {
    return assertWorkflowArray(
      await api.get<unknown>('/workflows/tasks/inbox', {
        params: {
          scope: TASK_SCOPE_TO_API_SCOPE[query.scope || 'my'],
          query: query.query?.trim() || undefined,
          businessKey: query.businessKey?.trim() || undefined,
          assignee: query.assignee?.trim() || undefined,
          processId: query.processId?.trim() || undefined,
          owner: query.owner?.trim() || undefined,
          candidateUser: query.candidateUser?.trim() || undefined,
          candidateGroup: query.candidateGroup?.trim() || undefined,
        },
      }),
      assertTask
    );
  },

  getMyTasks: async () => {
    return workflowService.getTaskInbox({ scope: 'my' });
  },

  getTaskDetail: async (taskId: string) => {
    return assertTaskDetail(
      await api.get<unknown>(`/workflows/tasks/${taskId}`)
    );
  },

  getProcessDetail: async (processId: string) => {
    return assertProcessDetail(
      await api.get<unknown>(`/workflows/processes/${processId}`)
    );
  },

  listProcesses: async (
    status: WorkflowProcessListStatus = 'ACTIVE',
    businessKey?: string,
    startedBy?: string,
    definitionKey?: string,
    query?: string,
    skipCount = 0,
    maxItems = 20
  ) => {
    return assertProcessBrowserList(
      await api.get<unknown>('/workflows/processes/browser', {
        params: {
          status,
          businessKey: businessKey || undefined,
          startedBy: startedBy || undefined,
          definitionKey: definitionKey || undefined,
          query: query || undefined,
          skipCount,
          maxItems,
        },
      })
    );
  },

  getProcessTasks: async (processId: string) => {
    return assertWorkflowArray(
      await api.get<unknown>(`/workflows/processes/${processId}/tasks`),
      assertProcessTask
    );
  },

  getProcessTaskHistory: async (
    processId: string,
    params?: {
      query?: string;
      assignee?: string;
      taskDefinitionKey?: string;
    }
  ) => {
    return assertWorkflowArray(
      await api.get<unknown>(`/workflows/processes/${processId}/task-history`, {
        params: {
          query: params?.query || undefined,
          assignee: params?.assignee || undefined,
          taskDefinitionKey: params?.taskDefinitionKey || undefined,
        },
      }),
      assertHistoricTask
    );
  },

  getProcessActivities: async (
    processId: string,
    params?: {
      query?: string;
      assignee?: string;
      activityType?: string;
    }
  ) => {
    return assertWorkflowArray(
      await api.get<unknown>(`/workflows/processes/${processId}/activities`, {
        params: {
          query: params?.query || undefined,
          assignee: params?.assignee || undefined,
          activityType: params?.activityType || undefined,
        },
      }),
      assertProcessActivity
    );
  },

  getProcessVariables: async (processId: string) => {
    return assertWorkflowArray(
      await api.get<unknown>(`/workflows/processes/${processId}/variables`),
      assertVariableItem
    );
  },

  setProcessVariable: async (
    processId: string,
    variableName: string,
    value: any
  ) => {
    await api.put(
      `/workflows/processes/${processId}/variables/${encodeURIComponent(
        variableName
      )}`,
      { value }
    );
  },

  deleteProcessVariable: async (processId: string, variableName: string) => {
    await api.delete(
      `/workflows/processes/${processId}/variables/${encodeURIComponent(
        variableName
      )}`
    );
  },

  getTaskVariables: async (taskId: string) => {
    return assertWorkflowArray(
      await api.get<unknown>(`/workflows/tasks/${taskId}/variables`),
      assertVariableItem
    );
  },

  getProcessItems: async (processId: string) => {
    return assertWorkflowArray(
      await api.get<unknown>(`/workflows/processes/${processId}/items`),
      assertBusinessItem
    );
  },

  getTaskItems: async (taskId: string) => {
    return assertWorkflowArray(
      await api.get<unknown>(`/workflows/tasks/${taskId}/items`),
      assertBusinessItem
    );
  },

  getTaskCandidates: async (taskId: string) => {
    return assertWorkflowArray(
      await api.get<unknown>(`/workflows/tasks/${taskId}/candidates`),
      assertTaskCandidate
    );
  },

  getTaskInvolvedActors: async (taskId: string) => {
    return assertWorkflowArray(
      await api.get<unknown>(`/workflows/tasks/${taskId}/involved`),
      assertInvolvedActor
    );
  },

  getProcessInvolvedActors: async (processId: string) => {
    return assertWorkflowArray(
      await api.get<unknown>(`/workflows/processes/${processId}/involved`),
      assertInvolvedActor
    );
  },

  getDefinitionDetail: async (definitionId: string) => {
    return assertDefinitionDetail(
      await api.get<unknown>(`/workflows/definitions/${definitionId}`)
    );
  },

  getDefinitionModel: async (definitionId: string) => {
    return assertDefinitionModel(
      await api.get<unknown>(`/workflows/definitions/${definitionId}/model`)
    );
  },

  getDefinitionDiagram: async (definitionId: string) => {
    return api.getBlob(`/workflows/definitions/${definitionId}/diagram`);
  },

  getStartFormModel: async (definitionId: string) => {
    return assertWorkflowArray(
      await api.get<unknown>(
        `/workflows/definitions/${definitionId}/start-form-model`
      ),
      assertFormModelElement
    );
  },

  getProcessDiagram: async (processId: string) => {
    return api.getBlob(`/workflows/processes/${processId}/diagram`);
  },

  getTaskFormModel: async (taskId: string) => {
    return assertWorkflowArray(
      await api.get<unknown>(`/workflows/tasks/${taskId}/task-form-model`),
      assertFormModelElement
    );
  },

  completeTask: async (taskId: string, variables: Record<string, any>) => {
    await api.post(`/workflows/tasks/${taskId}/complete`, variables);
  },

  submitTaskForm: async (taskId: string, values: Record<string, any>) => {
    await api.post(`/workflows/tasks/${taskId}/task-form-submit`, { values });
  },

  claimTask: async (taskId: string) => {
    await api.post(`/workflows/tasks/${taskId}/claim`);
  },

  unclaimTask: async (taskId: string) => {
    await api.post(`/workflows/tasks/${taskId}/unclaim`);
  },

  assignTask: async (taskId: string, assignee: string) => {
    await api.post(`/workflows/tasks/${taskId}/assign`, { assignee });
  },

  transitionTask: async (
    taskId: string,
    request: WorkflowTaskTransitionRequest
  ) => {
    await api.put(`/workflows/tasks/${taskId}`, request);
  },

  deleteProcess: async (processId: string) => {
    await api.delete(`/workflows/processes/${processId}`);
  },

  cancelProcess: async (processId: string, reason?: string) => {
    await api.post(`/workflows/processes/${processId}/cancel`, { reason });
  },

  getDocumentHistory: async (documentId: string) => {
    return assertWorkflowArray(
      await api.get<unknown>(`/workflows/document/${documentId}/history`),
      assertHistoryItem
    );
  },
};

export default workflowService;
