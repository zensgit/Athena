import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { toast } from 'react-toastify';
import CreateFolderDialog from './CreateFolderDialog';
import authService from 'services/authService';
import { createFolder } from 'store/slices/nodeSlice';
import { setCreateFolderDialogOpen } from 'store/slices/uiSlice';

const mockDispatch = jest.fn();

const mockState = {
  ui: { createFolderDialogOpen: true },
  node: {
    currentNode: {
      id: 'parent-1',
      path: '/Sites/Finance',
    },
  },
  auth: {
    user: {
      id: 'user-1',
      username: 'alice',
      email: 'alice@example.com',
      roles: ['ROLE_EDITOR'],
    },
  },
};

jest.mock('react-toastify', () => ({
  toast: {
    error: jest.fn(),
    success: jest.fn(),
  },
}));

jest.mock('store', () => ({
  useAppDispatch: () => mockDispatch,
  useAppSelector: (selector: any) => selector(mockState),
}));

jest.mock('services/authService', () => ({
  __esModule: true,
  default: {
    getCurrentUser: jest.fn(),
  },
}));

jest.mock('store/slices/nodeSlice', () => ({
  createFolder: jest.fn(),
}));

jest.mock('store/slices/uiSlice', () => ({
  setCreateFolderDialogOpen: jest.fn((open: boolean) => ({
    type: 'ui/setCreateFolderDialogOpen',
    payload: open,
  })),
}));

const mockedCreateFolder = createFolder as jest.MockedFunction<typeof createFolder>;
const mockedSetCreateFolderDialogOpen = setCreateFolderDialogOpen as jest.MockedFunction<typeof setCreateFolderDialogOpen>;
const toastSuccessMock = toast.success as jest.Mock;
const toastErrorMock = toast.error as jest.Mock;
const mockedAuthService = authService as jest.Mocked<typeof authService>;

describe('CreateFolderDialog smart authoring', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedAuthService.getCurrentUser.mockReturnValue(mockState.auth.user as any);
    mockedCreateFolder.mockReturnValue({
      type: 'node/createFolder',
    } as any);
    mockDispatch.mockReturnValue({
      unwrap: () => Promise.resolve({ id: 'folder-1' }),
    });
  });

  it('creates a smart folder through the generic create dialog', async () => {
    render(<CreateFolderDialog />);

    fireEvent.change(screen.getByLabelText('Folder Name'), {
      target: { value: 'Smart Invoices' },
    });
    fireEvent.change(screen.getByLabelText('Description (optional)'), {
      target: { value: 'Query-backed folder' },
    });

    fireEvent.click(screen.getByRole('checkbox', { name: 'Create as Smart Folder' }));

    const pathPrefixInput = await screen.findByLabelText('Path Prefix (optional)');
    expect((pathPrefixInput as HTMLInputElement).value).toBe('/Sites/Finance');

    fireEvent.change(screen.getByLabelText('Search Query'), {
      target: { value: 'invoice' },
    });

    fireEvent.click(screen.getByRole('button', { name: 'Create' }));

    await waitFor(() => {
      expect(mockedCreateFolder).toHaveBeenCalledWith({
        parentId: 'parent-1',
        name: 'Smart Invoices',
        description: 'Query-backed folder',
        isSmart: true,
        queryCriteria: {
          query: 'invoice',
          pathPrefix: '/Sites/Finance',
        },
      });
    });

    expect(toastSuccessMock).toHaveBeenCalledWith('Smart folder created successfully');
    expect(mockedSetCreateFolderDialogOpen).toHaveBeenCalledWith(false);
  });

  it('rejects smart-folder submit when query is missing', async () => {
    render(<CreateFolderDialog />);

    fireEvent.change(screen.getByLabelText('Folder Name'), {
      target: { value: 'Smart Invoices' },
    });
    fireEvent.click(screen.getByRole('checkbox', { name: 'Create as Smart Folder' }));
    fireEvent.click(screen.getByRole('button', { name: 'Create' }));

    await waitFor(() => {
      expect(screen.getByText('Search query is required for smart folders')).toBeTruthy();
    });
    expect(mockedCreateFolder).not.toHaveBeenCalled();
    expect(toastErrorMock).not.toHaveBeenCalled();
  });
});
