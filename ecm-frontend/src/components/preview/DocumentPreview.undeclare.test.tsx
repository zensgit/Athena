import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import DocumentPreview from './DocumentPreview';
import apiService from 'services/api';
import nodeService from 'services/nodeService';
import recordsManagementService from 'services/recordsManagementService';
import { useAppDispatch, useAppSelector } from 'store';

const mockNavigate = jest.fn();

jest.mock('react-router-dom', () => ({
  __esModule: true,
  ...jest.requireActual('react-router-dom'),
  useNavigate: () => mockNavigate,
}));

jest.mock('store', () => ({
  __esModule: true,
  useAppDispatch: jest.fn(),
  useAppSelector: jest.fn(),
}));

jest.mock('services/api', () => ({
  __esModule: true,
  default: {
    getBlob: jest.fn(),
  },
}));

jest.mock('services/nodeService', () => ({
  __esModule: true,
  default: {
    getNode: jest.fn(),
    getLockInfo: jest.fn(),
    getCheckoutInfo: jest.fn(),
    getNodeRelationCheckoutGraph: jest.fn(),
    getNodeRenditionRelationSummary: jest.fn(),
    getNodeRenditionDefinitions: jest.fn(),
    getPdfAnnotations: jest.fn(),
    downloadDocument: jest.fn(),
  },
}));

jest.mock('services/recordsManagementService', () => ({
  __esModule: true,
  default: {
    getRecord: jest.fn(),
  },
}));

jest.mock('components/dialogs/CheckoutGraphDialog', () => () => null);
jest.mock('components/dialogs/RenditionDefinitionDialog', () => () => null);
jest.mock('components/records/DeclareRecordDialog', () => () => null);
jest.mock('components/records/UndeclareRecordDialog', () => {
  return function MockUndeclareRecordDialog(props: {
    open: boolean;
    onClose: () => void;
    onUndeclared?: () => void;
  }) {
    if (!props.open) {
      return null;
    }
    return (
      <button
        type="button"
        onClick={() => {
          props.onUndeclared?.();
          props.onClose();
        }}
      >
        Confirm undeclare
      </button>
    );
  };
});

jest.mock('../comments/CommentSection', () => () => <div>Comments</div>);
jest.mock('./PdfPreview', () => () => <div>Pdf Preview</div>);

const mockedUseAppSelector = useAppSelector as jest.Mock;
const mockedUseAppDispatch = useAppDispatch as jest.Mock;
const mockedApiService = apiService as jest.Mocked<typeof apiService>;
const mockedNodeService = nodeService as jest.Mocked<typeof nodeService>;
const mockedRecordsManagementService = recordsManagementService as jest.Mocked<typeof recordsManagementService>;

describe('DocumentPreview undeclare flow', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedUseAppDispatch.mockReturnValue(jest.fn());
    mockedUseAppSelector.mockImplementation((selector: (state: any) => unknown) =>
      selector({
        auth: {
          user: {
            roles: ['ROLE_ADMIN'],
            username: 'admin',
          },
        },
      })
    );
    mockedApiService.getBlob.mockResolvedValue(new Blob(['pdf'], { type: 'application/pdf' }) as any);
    mockedNodeService.getNode
      .mockResolvedValueOnce({
        id: 'node-1',
        name: 'Policy.pdf',
        nodeType: 'DOCUMENT',
        aspects: ['rm:record'],
        properties: {},
        created: '2026-04-14T10:00:00',
        modified: '2026-04-14T10:00:00',
        creator: 'admin',
        modifier: 'admin',
        path: '/Documents/Policy.pdf',
        contentType: 'application/pdf',
      } as any)
      .mockResolvedValueOnce({
        id: 'node-1',
        name: 'Policy.pdf',
        nodeType: 'DOCUMENT',
        aspects: [],
        properties: {},
        created: '2026-04-14T10:00:00',
        modified: '2026-04-14T10:00:00',
        creator: 'admin',
        modifier: 'admin',
        path: '/Documents/Policy.pdf',
        contentType: 'application/pdf',
      } as any);
    mockedNodeService.getLockInfo.mockResolvedValue(null as any);
    mockedNodeService.getCheckoutInfo.mockResolvedValue(null as any);
    mockedNodeService.getNodeRelationCheckoutGraph.mockResolvedValue(null as any);
    mockedNodeService.getNodeRenditionRelationSummary.mockResolvedValue(null as any);
    mockedNodeService.getNodeRenditionDefinitions.mockResolvedValue([] as any);
    mockedNodeService.getPdfAnnotations.mockResolvedValue({
      annotations: [],
      updatedBy: null,
      updatedAt: null,
    } as any);
    mockedNodeService.downloadDocument.mockResolvedValue(undefined as any);
    mockedRecordsManagementService.getRecord.mockResolvedValue({
      nodeId: 'node-1',
      name: 'Policy.pdf',
      path: '/Documents/Policy.pdf',
      declaredBy: 'admin',
      declaredAt: '2026-04-14T10:00:00',
    } as any);
    Object.defineProperty(window.URL, 'createObjectURL', {
      value: jest.fn(() => 'blob:preview'),
      configurable: true,
      writable: true,
    });
    Object.defineProperty(window.URL, 'revokeObjectURL', {
      value: jest.fn(),
      configurable: true,
      writable: true,
    });
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('shows undeclare action for admin declared records and clears the record state after success', async () => {
    render(
      <DocumentPreview
        open
        onClose={jest.fn()}
        node={{
          id: 'node-1',
          name: 'Policy.pdf',
          nodeType: 'DOCUMENT',
          aspects: ['rm:record'],
          properties: {},
          created: '2026-04-14T10:00:00',
          modified: '2026-04-14T10:00:00',
          creator: 'admin',
          modifier: 'admin',
          path: '/Documents/Policy.pdf',
          contentType: 'application/pdf',
        } as any}
      />
    );

    expect(await screen.findByText((content) => content.includes('Declared as a record by admin'))).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'More actions' }));
    expect(await screen.findByText('Undeclare Record...')).toBeTruthy();

    fireEvent.click(screen.getByText('Undeclare Record...'));
    fireEvent.click(await screen.findByRole('button', { name: 'Confirm undeclare' }));

    await waitFor(() => {
      expect(screen.queryByText((content) => content.includes('Declared as a record by admin'))).toBeNull();
    });
  });
});
