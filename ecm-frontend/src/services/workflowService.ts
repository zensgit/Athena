import api from './api';

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

export type TaskInboxScope = 'my' | 'claimable' | 'unassigned' | 'all' | 'completed' | 'involved';

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

export type WorkflowTaskTransitionState = 'completed' | 'claimed' | 'unclaimed' | 'assigned' | 'delegated' | 'resolved';

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

const workflowService = {
  getDefinitions: async () => {
    return api.get<ProcessDefinition[]>('/workflows/definitions');
  },

  startApproval: async (documentId: string, approvers: string[], comment: string) => {
    return api.post<ProcessInstance>(`/workflows/document/${documentId}/approval`, {
      approvers,
      comment,
    });
  },

  submitStartForm: async (documentId: string, values: Record<string, any>) => {
    return api.post<ProcessInstance>(`/workflows/document/${documentId}/approval/form-submit`, {
      values,
    });
  },

  startProcess: async (request: WorkflowProcessStartRequest) => {
    return api.post<ProcessInstance>('/workflows/processes', request);
  },

  getTaskInbox: async (query: TaskInboxQuery = {}) => {
    return api.get<Task[]>('/workflows/tasks/inbox', {
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
    });
  },

  getMyTasks: async () => {
    return workflowService.getTaskInbox({ scope: 'my' });
  },

  getTaskDetail: async (taskId: string) => {
    return api.get<WorkflowTaskDetail>(`/workflows/tasks/${taskId}`);
  },

  getProcessDetail: async (processId: string) => {
    return api.get<WorkflowProcessDetail>(`/workflows/processes/${processId}`);
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
    return api.get<WorkflowProcessBrowserListResponse>('/workflows/processes/browser', {
      params: {
        status,
        businessKey: businessKey || undefined,
        startedBy: startedBy || undefined,
        definitionKey: definitionKey || undefined,
        query: query || undefined,
        skipCount,
        maxItems,
      },
    });
  },

  getProcessTasks: async (processId: string) => {
    return api.get<WorkflowProcessTask[]>(`/workflows/processes/${processId}/tasks`);
  },

  getProcessTaskHistory: async (
    processId: string,
    params?: {
      query?: string;
      assignee?: string;
      taskDefinitionKey?: string;
    }
  ) => {
    return api.get<WorkflowHistoricTaskItem[]>(`/workflows/processes/${processId}/task-history`, {
      params: {
        query: params?.query || undefined,
        assignee: params?.assignee || undefined,
        taskDefinitionKey: params?.taskDefinitionKey || undefined,
      },
    });
  },

  getProcessActivities: async (
    processId: string,
    params?: {
      query?: string;
      assignee?: string;
      activityType?: string;
    }
  ) => {
    return api.get<WorkflowProcessActivity[]>(`/workflows/processes/${processId}/activities`, {
      params: {
        query: params?.query || undefined,
        assignee: params?.assignee || undefined,
        activityType: params?.activityType || undefined,
      },
    });
  },

  getProcessVariables: async (processId: string) => {
    return api.get<WorkflowVariableItem[]>(`/workflows/processes/${processId}/variables`);
  },

  setProcessVariable: async (processId: string, variableName: string, value: any) => {
    await api.put(`/workflows/processes/${processId}/variables/${encodeURIComponent(variableName)}`, { value });
  },

  deleteProcessVariable: async (processId: string, variableName: string) => {
    await api.delete(`/workflows/processes/${processId}/variables/${encodeURIComponent(variableName)}`);
  },

  getTaskVariables: async (taskId: string) => {
    return api.get<WorkflowVariableItem[]>(`/workflows/tasks/${taskId}/variables`);
  },

  getProcessItems: async (processId: string) => {
    return api.get<WorkflowBusinessItem[]>(`/workflows/processes/${processId}/items`);
  },

  getTaskItems: async (taskId: string) => {
    return api.get<WorkflowBusinessItem[]>(`/workflows/tasks/${taskId}/items`);
  },

  getTaskCandidates: async (taskId: string) => {
    return api.get<WorkflowTaskCandidate[]>(`/workflows/tasks/${taskId}/candidates`);
  },

  getTaskInvolvedActors: async (taskId: string) => {
    return api.get<WorkflowInvolvedActor[]>(`/workflows/tasks/${taskId}/involved`);
  },

  getProcessInvolvedActors: async (processId: string) => {
    return api.get<WorkflowInvolvedActor[]>(`/workflows/processes/${processId}/involved`);
  },

  getDefinitionDetail: async (definitionId: string) => {
    return api.get<WorkflowDefinitionDetail>(`/workflows/definitions/${definitionId}`);
  },

  getDefinitionModel: async (definitionId: string) => {
    return api.get<WorkflowDefinitionModel>(`/workflows/definitions/${definitionId}/model`);
  },

  getDefinitionDiagram: async (definitionId: string) => {
    return api.getBlob(`/workflows/definitions/${definitionId}/diagram`);
  },

  getStartFormModel: async (definitionId: string) => {
    return api.get<WorkflowFormModelElement[]>(`/workflows/definitions/${definitionId}/start-form-model`);
  },

  getProcessDiagram: async (processId: string) => {
    return api.getBlob(`/workflows/processes/${processId}/diagram`);
  },

  getTaskFormModel: async (taskId: string) => {
    return api.get<WorkflowFormModelElement[]>(`/workflows/tasks/${taskId}/task-form-model`);
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

  transitionTask: async (taskId: string, request: WorkflowTaskTransitionRequest) => {
    await api.put(`/workflows/tasks/${taskId}`, request);
  },

  deleteProcess: async (processId: string) => {
    await api.delete(`/workflows/processes/${processId}`);
  },

  cancelProcess: async (processId: string, reason?: string) => {
    await api.post(`/workflows/processes/${processId}/cancel`, { reason });
  },

  getDocumentHistory: async (documentId: string) => {
    return api.get<WorkflowHistoryItem[]>(`/workflows/document/${documentId}/history`);
  },
};

export default workflowService;
