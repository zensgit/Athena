import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { toast } from 'react-toastify';
import SavedSearchesPage from './SavedSearchesPage';
import savedSearchService from 'services/savedSearchService';
import { setSearchOpen, setSearchPrefill } from 'store/slices/uiSlice';

const mockNavigate = jest.fn();
const mockDispatch = jest.fn();

jest.mock('react-toastify', () => ({
  toast: {
    error: jest.fn(),
    success: jest.fn(),
    info: jest.fn(),
  },
}));

jest.mock('store', () => ({
  useAppDispatch: () => mockDispatch,
}));

jest.mock('@mui/x-data-grid', () => ({
  __esModule: true,
  GridColDef: {},
  DataGrid: ({ rows, columns }: any) => (
    <div data-testid="mock-data-grid">
      {rows.map((row: any) => (
        <div key={row.id}>
          {columns.map((column: any) => (
            <div key={column.field}>
              {column.renderCell
                ? column.renderCell({ row, value: row[column.field] })
                : <span>{String(row[column.field] ?? '')}</span>}
            </div>
          ))}
        </div>
      ))}
    </div>
  ),
}));

jest.mock('react-router-dom', () => {
  const actual = jest.requireActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

jest.mock('services/savedSearchService', () => ({
  __esModule: true,
  default: {
    list: jest.fn(),
    delete: jest.fn(),
    update: jest.fn(),
    save: jest.fn(),
    setPinned: jest.fn(),
    execute: jest.fn(),
    createSmartFolder: jest.fn(),
  },
}));

const mockedSavedSearchService = savedSearchService as jest.Mocked<typeof savedSearchService>;
const toastSuccessMock = toast.success as jest.Mock;

describe('SavedSearchesPage smart folder action', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedSavedSearchService.list.mockResolvedValue([
      {
        id: 'saved-1',
        userId: 'alice',
        name: 'Invoices',
        queryParams: {
          query: 'invoice',
        },
        createdAt: '2026-04-14T00:00:00Z',
      },
    ]);
    mockedSavedSearchService.createSmartFolder.mockResolvedValue({
      id: 'folder-1',
      name: 'Invoices',
      path: '/Invoices',
      smart: true,
    });
  });

  it('creates a smart folder from a saved search and navigates to it', async () => {
    render(<SavedSearchesPage />);

    expect(await screen.findByText('Invoices')).toBeTruthy();

    fireEvent.click(screen.getByLabelText('Create smart folder from saved search Invoices'));

    const folderNameInput = await screen.findByLabelText('Folder Name');
    fireEvent.change(folderNameInput, { target: { value: 'Invoices Smart' } });
    fireEvent.change(screen.getByLabelText('Description (optional)'), {
      target: { value: 'folder from search' },
    });

    fireEvent.click(screen.getByRole('button', { name: 'Create Smart Folder' }));

    await waitFor(() => {
      expect(mockedSavedSearchService.createSmartFolder).toHaveBeenCalledWith('saved-1', {
        name: 'Invoices Smart',
        description: 'folder from search',
      });
    });

    expect(toastSuccessMock).toHaveBeenCalledWith('Smart folder created');
    expect(mockNavigate).toHaveBeenCalledWith('/browse/folder-1');
  });

  it('loads record filters into advanced search prefill', async () => {
    mockedSavedSearchService.list.mockResolvedValueOnce([
      {
        id: 'saved-2',
        userId: 'alice',
        name: 'Declared Finance Records',
        queryParams: {
          query: 'finance',
          filters: {
            recordOnly: true,
            recordCategoryPaths: ['/Records Management/Finance'],
          },
        },
        createdAt: '2026-04-14T00:00:00Z',
      },
    ]);

    render(<SavedSearchesPage />);

    expect(await screen.findByText('Declared Finance Records')).toBeTruthy();

    fireEvent.click(screen.getByLabelText('Load saved search Declared Finance Records'));

    expect(mockDispatch).toHaveBeenCalledWith(
      expect.objectContaining({
        type: setSearchPrefill.type,
        payload: expect.objectContaining({
          name: 'finance',
          recordOnly: true,
          recordCategoryPaths: ['/Records Management/Finance'],
        }),
      })
    );
    expect(mockDispatch).toHaveBeenCalledWith(setSearchOpen(true));
    expect(toastSuccessMock).toHaveBeenCalledWith('Loaded saved search into Advanced Search');
  });
});
