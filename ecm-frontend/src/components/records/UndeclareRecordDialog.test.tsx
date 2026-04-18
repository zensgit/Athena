import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { toast } from 'react-toastify';
import UndeclareRecordDialog from './UndeclareRecordDialog';
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
    undeclareRecord: jest.fn(),
  },
}));

const mockedRecordsManagementService = recordsManagementService as jest.Mocked<typeof recordsManagementService>;
const toastSuccessMock = toast.success as jest.Mock;
const toastErrorMock = toast.error as jest.Mock;

describe('UndeclareRecordDialog', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('undeclares a record with a required reason', async () => {
    const onClose = jest.fn();
    const onUndeclared = jest.fn();
    mockedRecordsManagementService.undeclareRecord.mockResolvedValueOnce(undefined as any);

    render(
      <UndeclareRecordDialog
        open
        nodeId="node-1"
        nodeName="Policy.pdf"
        onClose={onClose}
        onUndeclared={onUndeclared}
      />
    );

    fireEvent.click(screen.getByRole('button', { name: 'Undeclare Record' }));
    expect(await screen.findByText('Reason is required')).toBeTruthy();

    fireEvent.change(screen.getByRole('textbox', { name: /Reason/i }), {
      target: { value: '  governance update  ' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Undeclare Record' }));

    await waitFor(() => {
      expect(mockedRecordsManagementService.undeclareRecord).toHaveBeenCalledWith('node-1', {
        reason: 'governance update',
      });
    });
    expect(onUndeclared).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
    expect(toastSuccessMock).toHaveBeenCalledWith('Document undeclared as record');
    expect(toastErrorMock).not.toHaveBeenCalled();
  });
});
