import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import authReducer, { setSession } from 'store/slices/authSlice';
import nodeReducer from 'store/slices/nodeSlice';
import uiReducer from 'store/slices/uiSlice';
import MainLayout from 'components/layout/MainLayout';

jest.mock('components/browser/FolderTree', () => {
  return function FolderTreeMock() {
    return <div data-testid="folder-tree" />;
  };
});

const renderLayoutWithRoles = (roles: string[]) => {
  const store = configureStore({
    reducer: {
      auth: authReducer,
      node: nodeReducer,
      ui: uiReducer,
    },
  });

  store.dispatch(
    setSession({
      user: {
        id: 'test-user',
        username: 'tester',
        email: 'tester@example.com',
        roles,
      },
      token: 'test-token',
      isAuthenticated: true,
    })
  );

  render(
    <Provider store={store}>
      <MemoryRouter
        initialEntries={['/browse/root']}
        future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
      >
        <Routes>
          <Route
            path="/browse/:nodeId"
            element={
              <MainLayout>
                <div>content</div>
              </MainLayout>
            }
          />
        </Routes>
      </MemoryRouter>
    </Provider>
  );
};

test('account menu shows Tags/Categories for admin/editor roles', async () => {
  renderLayoutWithRoles(['ROLE_ADMIN']);

  const uploadButton = screen.getByRole('button', { name: 'Upload', exact: true });
  expect((uploadButton as HTMLButtonElement).disabled).toBe(false);
  const newFolderButton = screen.getByRole('button', { name: 'New Folder', exact: true });
  expect((newFolderButton as HTMLButtonElement).disabled).toBe(false);

  fireEvent.click(screen.getByRole('button', { name: 'Account menu' }));
  expect(await screen.findByRole('menuitem', { name: 'Tags' })).toBeTruthy();
  expect(screen.getByRole('menuitem', { name: 'Categories' })).toBeTruthy();
  expect(screen.getByRole('menuitem', { name: 'Admin Dashboard' })).toBeTruthy();
  expect(screen.getByRole('menuitem', { name: 'Mail Automation' })).toBeTruthy();
  expect(screen.getByRole('menuitem', { name: 'Content Types' })).toBeTruthy();
  expect(screen.getByRole('menuitem', { name: 'Permission Templates' })).toBeTruthy();
  expect(screen.getByRole('menuitem', { name: 'Webhooks' })).toBeTruthy();
  expect(screen.getByRole('menuitem', { name: 'System Status' })).toBeTruthy();
});

test('account menu hides Tags/Categories for viewer role', async () => {
  renderLayoutWithRoles(['ROLE_VIEWER']);

  const uploadButton = screen.getByRole('button', { name: 'Upload', exact: true });
  expect((uploadButton as HTMLButtonElement).disabled).toBe(true);
  const newFolderButton = screen.getByRole('button', { name: 'New Folder', exact: true });
  expect((newFolderButton as HTMLButtonElement).disabled).toBe(true);

  fireEvent.click(screen.getByRole('button', { name: 'Account menu' }));
  expect(await screen.findByRole('menuitem', { name: 'Tasks' })).toBeTruthy();
  expect(screen.queryByRole('menuitem', { name: 'Tags' })).toBeNull();
  expect(screen.queryByRole('menuitem', { name: 'Categories' })).toBeNull();
  expect(screen.queryByRole('menuitem', { name: 'Admin Dashboard' })).toBeNull();
  expect(screen.queryByRole('menuitem', { name: 'Mail Automation' })).toBeNull();
  expect(screen.queryByRole('menuitem', { name: 'Content Types' })).toBeNull();
  expect(screen.queryByRole('menuitem', { name: 'Permission Templates' })).toBeNull();
  expect(screen.queryByRole('menuitem', { name: 'Webhooks' })).toBeNull();
  expect(screen.queryByRole('menuitem', { name: 'System Status' })).toBeNull();
});
