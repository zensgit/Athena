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
  description: string;
  createTime: string;
  processInstanceId: string;
}

export interface ProcessInstance {
  id: string;
  definitionKey: string;
  businessKey: string;
  ended: boolean;
}

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

  getMyTasks: async () => {
    return api.get<Task[]>('/workflows/tasks/my');
  },

  completeTask: async (taskId: string, variables: Record<string, any>) => {
    await api.post(`/workflows/tasks/${taskId}/complete`, variables);
  },

  getDocumentHistory: async (documentId: string) => {
    return api.get<any[]>(`/workflows/document/${documentId}/history`);
  },
};

export default workflowService;
