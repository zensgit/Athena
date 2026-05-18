import api from './api';
import workflowService, {
  ProcessDefinition,
  ProcessInstance,
  Task,
  WorkflowFormModelElement,
  WorkflowProcessBrowserListResponse,
  WORKFLOW_UNEXPECTED_RESPONSE_MESSAGE,
} from './workflowService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    put: jest.fn(),
    delete: jest.fn(),
    getBlob: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const definition: ProcessDefinition = {
  id: 'documentApproval:1:definition-1',
  key: 'documentApproval',
  name: 'Document Approval',
  version: 1,
};

const processInstance: ProcessInstance = {
  id: 'process-1',
  definitionKey: 'documentApproval',
  businessKey: 'node-1',
  ended: false,
};

const inboxTask: Task = {
  id: 'task-1',
  name: 'Review document',
  assignee: 'alice',
  description: 'Review document',
  createTime: '2026-05-18T01:00:00Z',
  processInstanceId: 'process-1',
  processDefinitionId: 'documentApproval:1:definition-1',
  processDefinitionKey: 'documentApproval',
  processDefinitionName: 'Document Approval',
  processDefinitionVersion: 1,
  businessKey: 'node-1',
  startedBy: 'bob',
  claimable: false,
  status: 'ACTIVE',
};

const processBrowserPage: WorkflowProcessBrowserListResponse = {
  items: [
    {
      id: 'process-1',
      processDefinitionId: 'documentApproval:1:definition-1',
      processDefinitionKey: 'documentApproval',
      processDefinitionName: 'Document Approval',
      processDefinitionVersion: 1,
      businessKey: 'node-1',
      startedBy: 'bob',
      startTime: '2026-05-18T01:00:00Z',
      ended: false,
      submissionSummary: {
        approvers: ['alice'],
        startComment: 'Please review',
      },
    },
  ],
  paging: {
    skipCount: 0,
    maxItems: 20,
    totalItems: 1,
    hasMoreItems: false,
  },
};

const startFormElement: WorkflowFormModelElement = {
  id: 'comment',
  name: 'comment',
  title: 'Comment',
  type: 'text',
  required: true,
  readOnly: false,
  repeated: false,
  placeholder: 'Add a comment',
  options: [],
  scope: 'start',
};

describe('workflowService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns guarded workflow definitions and preserves the endpoint', async () => {
    mockedApi.get.mockResolvedValueOnce([definition]);

    await expect(workflowService.getDefinitions()).resolves.toEqual([
      definition,
    ]);

    expect(mockedApi.get).toHaveBeenCalledWith('/workflows/definitions');
  });

  it('returns guarded start approval responses and preserves the payload', async () => {
    mockedApi.post.mockResolvedValueOnce(processInstance);

    await expect(
      workflowService.startApproval('node-1', ['alice', 'bob'], 'Please review')
    ).resolves.toEqual(processInstance);

    expect(mockedApi.post).toHaveBeenCalledWith(
      '/workflows/document/node-1/approval',
      {
        approvers: ['alice', 'bob'],
        comment: 'Please review',
      }
    );
  });

  it('preserves task inbox query normalization and returns guarded tasks', async () => {
    mockedApi.get.mockResolvedValueOnce([inboxTask]);

    await expect(
      workflowService.getTaskInbox({
        scope: 'claimable',
        query: ' review ',
        businessKey: ' node-1 ',
        assignee: ' alice ',
        processId: ' process-1 ',
        owner: ' bob ',
        candidateUser: ' carol ',
        candidateGroup: ' reviewers ',
      })
    ).resolves.toEqual([inboxTask]);

    expect(mockedApi.get).toHaveBeenCalledWith('/workflows/tasks/inbox', {
      params: {
        scope: 'CLAIMABLE',
        query: 'review',
        businessKey: 'node-1',
        assignee: 'alice',
        processId: 'process-1',
        owner: 'bob',
        candidateUser: 'carol',
        candidateGroup: 'reviewers',
      },
    });
  });

  it('preserves process browser params and returns guarded pages', async () => {
    mockedApi.get.mockResolvedValueOnce(processBrowserPage);

    await expect(
      workflowService.listProcesses(
        'COMPLETED',
        'node-1',
        'alice',
        'documentApproval',
        'approve',
        10,
        25
      )
    ).resolves.toEqual(processBrowserPage);

    expect(mockedApi.get).toHaveBeenCalledWith('/workflows/processes/browser', {
      params: {
        status: 'COMPLETED',
        businessKey: 'node-1',
        startedBy: 'alice',
        definitionKey: 'documentApproval',
        query: 'approve',
        skipCount: 10,
        maxItems: 25,
      },
    });
  });

  it('returns guarded form models', async () => {
    mockedApi.get.mockResolvedValueOnce([startFormElement]);

    await expect(
      workflowService.getStartFormModel('definition-1')
    ).resolves.toEqual([startFormElement]);

    expect(mockedApi.get).toHaveBeenCalledWith(
      '/workflows/definitions/definition-1/start-form-model'
    );
  });

  it('keeps diagram blob methods unmodified', async () => {
    const blob = new Blob(['diagram'], { type: 'image/png' });
    mockedApi.getBlob.mockResolvedValueOnce(blob).mockResolvedValueOnce(blob);

    await expect(
      workflowService.getDefinitionDiagram('definition-1')
    ).resolves.toBe(blob);
    await expect(workflowService.getProcessDiagram('process-1')).resolves.toBe(
      blob
    );

    expect(mockedApi.getBlob).toHaveBeenNthCalledWith(
      1,
      '/workflows/definitions/definition-1/diagram'
    );
    expect(mockedApi.getBlob).toHaveBeenNthCalledWith(
      2,
      '/workflows/processes/process-1/diagram'
    );
  });

  it('rejects HTML fallback for JSON endpoints', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(workflowService.getDefinitions()).rejects.toThrow(
      WORKFLOW_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('rejects malformed process browser pages', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...processBrowserPage,
      paging: {
        ...processBrowserPage.paging,
        totalItems: '1',
      },
    });

    await expect(workflowService.listProcesses()).rejects.toThrow(
      WORKFLOW_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('rejects malformed form model options', async () => {
    mockedApi.get.mockResolvedValueOnce([
      {
        ...startFormElement,
        options: [{ label: 'Approve', value: 1 }],
      },
    ]);

    await expect(
      workflowService.getStartFormModel('definition-1')
    ).rejects.toThrow(WORKFLOW_UNEXPECTED_RESPONSE_MESSAGE);
  });
});
