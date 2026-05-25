import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { toast } from 'react-toastify';
import EditSmartFolderDialog from './EditSmartFolderDialog';
import nodeService from 'services/nodeService';

jest.mock('react-toastify', () => ({
  toast: {
    success: jest.fn(),
    error: jest.fn(),
    info: jest.fn(),
  },
}));

jest.mock('services/nodeService', () => ({
  __esModule: true,
  default: {
    updateFolder: jest.fn(),
  },
}));

const mockedService = nodeService as jest.Mocked<typeof nodeService>;
const toastSuccess = toast.success as jest.Mock;
const toastError = toast.error as jest.Mock;

const smartFolder = {
  id: 'folder-1',
  name: 'Contracts',
  nodeType: 'FOLDER',
  path: '/Sites/legal/Contracts',
  smart: true,
  queryCriteria: { query: 'type:contract', pathPrefix: '/Sites/legal' },
} as any;

const renderDialog = (overrides: Partial<React.ComponentProps<typeof EditSmartFolderDialog>> = {}) => {
  const onClose = jest.fn();
  const onUpdated = jest.fn();
  render(
    <EditSmartFolderDialog open onClose={onClose} onUpdated={onUpdated} folder={smartFolder} {...overrides} />,
  );
  return { onClose, onUpdated };
};

describe('EditSmartFolderDialog', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('prefills the query and path prefix from the folder criteria', () => {
    renderDialog();
    expect((screen.getByTestId('edit-smart-folder-query') as HTMLInputElement).value).toBe('type:contract');
    expect((screen.getByTestId('edit-smart-folder-pathprefix') as HTMLInputElement).value).toBe('/Sites/legal');
    // Lock the RHF form-name `searchQuery` (→ payload key `query`) for parity with CreateFolderDialog;
    // renaming the field would silently break that contract while the payload mapping still passed.
    expect((screen.getByTestId('edit-smart-folder-query') as HTMLInputElement).name).toBe('searchQuery');
  });

  it('disables submit when the query is cleared', () => {
    renderDialog();
    fireEvent.change(screen.getByTestId('edit-smart-folder-query'), { target: { value: '   ' } });
    expect((screen.getByTestId('edit-smart-folder-submit') as HTMLButtonElement).disabled).toBe(true);
  });

  it('PUTs isSmart:true + queryCriteria, toasts, and closes on success', async () => {
    mockedService.updateFolder.mockResolvedValueOnce({ ...smartFolder } as any);
    const { onClose, onUpdated } = renderDialog();

    fireEvent.change(screen.getByTestId('edit-smart-folder-query'), { target: { value: 'type:invoice' } });
    fireEvent.click(screen.getByTestId('edit-smart-folder-submit'));

    await waitFor(() => {
      expect(mockedService.updateFolder).toHaveBeenCalledWith('folder-1', {
        isSmart: true,
        queryCriteria: { query: 'type:invoice', pathPrefix: '/Sites/legal' },
      });
    });
    expect(toastSuccess).toHaveBeenCalledWith('Smart folder updated');
    expect(onUpdated).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it('omits an empty path prefix from the payload', async () => {
    mockedService.updateFolder.mockResolvedValueOnce({ ...smartFolder } as any);
    renderDialog({ folder: { ...smartFolder, queryCriteria: { query: 'type:contract' } } as any });

    fireEvent.click(screen.getByTestId('edit-smart-folder-submit'));

    await waitFor(() => {
      expect(mockedService.updateFolder).toHaveBeenCalledWith('folder-1', {
        isSmart: true,
        queryCriteria: { query: 'type:contract' },
      });
    });
  });

  it('stays open and does NOT double-toast on a server error (interceptor already toasts)', async () => {
    mockedService.updateFolder.mockRejectedValueOnce(new Error('Folder is locked by alice'));
    const { onClose } = renderDialog();

    fireEvent.click(screen.getByTestId('edit-smart-folder-submit'));

    await waitFor(() => {
      expect(mockedService.updateFolder).toHaveBeenCalled();
    });
    expect(onClose).not.toHaveBeenCalled();
    // The api response interceptor surfaces error.response.data.message; the dialog must not
    // re-toast, so no dialog-level error toast is emitted here.
    expect(toastError).not.toHaveBeenCalled();
    // Submit is usable again for retry.
    expect((screen.getByTestId('edit-smart-folder-submit') as HTMLButtonElement).disabled).toBe(false);
  });
});
