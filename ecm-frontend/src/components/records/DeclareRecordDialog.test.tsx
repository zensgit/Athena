import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { toast } from 'react-toastify';
import DeclareRecordDialog from './DeclareRecordDialog';
import recordsManagementService from 'services/recordsManagementService';

jest.mock('react-toastify', () => ({
  toast: {
    success: jest.fn(),
    error: jest.fn(),
  },
}));

jest.mock('services/recordsManagementService', () => ({
  __esModule: true,
  default: {
    declareRecord: jest.fn(),
  },
}));

const mockedRecordsManagementService = recordsManagementService as jest.Mocked<typeof recordsManagementService>;
const toastSuccessMock = toast.success as jest.Mock;
const toastErrorMock = toast.error as jest.Mock;

describe('DeclareRecordDialog', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('declares a record with an optional comment', async () => {
    const onClose = jest.fn();
    const onDeclared = jest.fn();
    mockedRecordsManagementService.declareRecord.mockResolvedValueOnce({
      nodeId: 'node-1',
      name: 'Policy.pdf',
      path: '/Records/Policy.pdf',
    } as any);

    render(
      <DeclareRecordDialog
        open
        nodeId="node-1"
        nodeName="Policy.pdf"
        onClose={onClose}
        onDeclared={onDeclared}
      />
    );

    fireEvent.change(screen.getByLabelText('Declaration Comment (optional)'), {
      target: { value: 'Signed by compliance' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Declare Record' }));

    await waitFor(() => {
      expect(mockedRecordsManagementService.declareRecord).toHaveBeenCalledWith('node-1', {
        comment: 'Signed by compliance',
      });
    });
    expect(onDeclared).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
    expect(toastSuccessMock).toHaveBeenCalledWith('Document declared as record');
    expect(toastErrorMock).not.toHaveBeenCalled();
  });
});
