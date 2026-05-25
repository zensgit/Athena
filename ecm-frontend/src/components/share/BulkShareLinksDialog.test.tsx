import React from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { toast } from 'react-toastify';
import BulkShareLinksDialog from './BulkShareLinksDialog';
import shareLinkService from 'services/shareLinkService';

jest.mock('react-toastify', () => ({
  toast: { success: jest.fn(), error: jest.fn(), warn: jest.fn() },
}));

jest.mock('services/shareLinkService', () => ({
  __esModule: true,
  default: { bulkCreateLinks: jest.fn() },
}));

const mockedService = shareLinkService as jest.Mocked<typeof shareLinkService>;
const toastSuccess = toast.success as jest.Mock;
const toastWarn = toast.warn as jest.Mock;
const toastError = toast.error as jest.Mock;

const link = (nodeId: string) => ({
  id: `s-${nodeId}`, token: `t-${nodeId}`, nodeId, nodeName: `${nodeId}.pdf`,
  createdBy: 'alice', createdAt: '2026-05-25T00:00:00', expiryDate: null, maxAccessCount: null,
  accessCount: 0, active: true, name: null, permissionLevel: 'VIEW', lastAccessedAt: null,
  passwordProtected: false, hasIpRestrictions: false, isValid: true,
});
const wrap = (rows: any[]) => ({ bulkShareLinkCreateResults: { rows } });

const renderDialog = (overrides: Partial<React.ComponentProps<typeof BulkShareLinksDialog>> = {}) => {
  const onClose = jest.fn();
  const onCreated = jest.fn();
  render(
    <BulkShareLinksDialog open onClose={onClose} onCreated={onCreated} documentIds={['node-1', 'node-2']} {...overrides} />,
  );
  return { onClose, onCreated };
};

describe('BulkShareLinksDialog', () => {
  beforeEach(() => jest.clearAllMocks());

  it('disables submit when there are no selected documents', () => {
    renderDialog({ documentIds: [] });
    expect((screen.getByTestId('bulk-share-submit') as HTMLButtonElement).disabled).toBe(true);
  });

  it('creates links, toasts success, and closes when all rows are CREATED', async () => {
    mockedService.bulkCreateLinks.mockResolvedValueOnce(wrap([
      { nodeId: 'node-1', status: 'CREATED', shareLink: link('node-1'), errorCategory: null, message: null },
      { nodeId: 'node-2', status: 'CREATED', shareLink: link('node-2'), errorCategory: null, message: null },
    ]) as any);

    const { onClose, onCreated } = renderDialog();
    fireEvent.click(screen.getByTestId('bulk-share-submit'));

    await waitFor(() => {
      expect(mockedService.bulkCreateLinks).toHaveBeenCalledWith(expect.objectContaining({
        nodeIds: ['node-1', 'node-2'],
        permissionLevel: 'VIEW',
      }));
    });
    expect(toastSuccess).toHaveBeenCalledWith(expect.stringContaining('2'));
    expect(onCreated).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it('stays open on partial failure and renders failed rows grouped by errorCategory', async () => {
    mockedService.bulkCreateLinks.mockResolvedValueOnce(wrap([
      { nodeId: 'node-1', status: 'CREATED', shareLink: link('node-1'), errorCategory: null, message: null },
      { nodeId: 'node-2', status: 'FAILED', shareLink: null, errorCategory: 'NO_PERMISSION', message: 'No permission to share the target node.' },
    ]) as any);

    const { onClose, onCreated } = renderDialog();
    fireEvent.click(screen.getByTestId('bulk-share-submit'));

    await waitFor(() => {
      expect(toastWarn).toHaveBeenCalledWith(expect.stringContaining('1 failed'));
    });
    expect(onClose).not.toHaveBeenCalled();
    expect(onCreated).toHaveBeenCalled(); // created > 0
    const failed = screen.getByTestId('bulk-share-failed-rows');
    expect(within(failed).getByText('node-2')).toBeTruthy();
    expect(screen.getByTestId('bulk-share-failed-no_permission')).toBeTruthy();
  });

  it('shows an error toast and stays open when the service rejects', async () => {
    mockedService.bulkCreateLinks.mockRejectedValueOnce(new Error('Bulk share-link creation failed'));
    const { onClose } = renderDialog();

    fireEvent.click(screen.getByTestId('bulk-share-submit'));

    await waitFor(() => {
      expect(toastError).toHaveBeenCalledWith(expect.stringContaining('Bulk share-link'));
    });
    expect(onClose).not.toHaveBeenCalled();
  });
});
