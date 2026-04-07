import React from 'react';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import authReducer, { setSession } from 'store/slices/authSlice';
import nodeReducer from 'store/slices/nodeSlice';
import uiReducer from 'store/slices/uiSlice';
import MainLayout from 'components/layout/MainLayout';
import { TENANT_STORAGE_KEY } from 'utils/tenantContext';

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

  return render(
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
          <Route path="/admin/tenants" element={<div>tenant admin route</div>} />
        </Routes>
      </MemoryRouter>
    </Provider>
  );
};

afterEach(() => {
  window.localStorage.removeItem(TENANT_STORAGE_KEY);
});

test('tenant badge defaults to default when no active tenant is stored', () => {
  renderLayoutWithRoles(['ROLE_ADMIN']);

  const badge = screen.getByLabelText('Active tenant default');
  expect(badge).toBeTruthy();
});

test('tenant badge reflects the stored active tenant domain', () => {
  window.localStorage.setItem(TENANT_STORAGE_KEY, 'acme');

  renderLayoutWithRoles(['ROLE_ADMIN']);

  const badge = screen.getByLabelText('Active tenant acme');
  expect(badge).toBeTruthy();
});

test('admins can click the tenant badge to navigate to /admin/tenants', () => {
  renderLayoutWithRoles(['ROLE_ADMIN']);

  const badge = screen.getByLabelText('Active tenant default');
  fireEvent.click(badge);

  expect(screen.getByText('tenant admin route')).toBeTruthy();
});

test('non-admin users see the tenant badge but it is not clickable', () => {
  renderLayoutWithRoles(['ROLE_VIEWER']);

  const badge = screen.getByLabelText('Active tenant default');
  fireEvent.click(badge);

  // Non-admin click should be a no-op — still on the browse content view.
  expect(screen.getByText('content')).toBeTruthy();
  expect(screen.queryByText('tenant admin route')).toBeNull();
});

test('tenant badge updates when athena:tenant-changed event fires', () => {
  renderLayoutWithRoles(['ROLE_ADMIN']);

  expect(screen.getByLabelText('Active tenant default')).toBeTruthy();

  act(() => {
    window.localStorage.setItem(TENANT_STORAGE_KEY, 'beta');
    window.dispatchEvent(new CustomEvent('athena:tenant-changed'));
  });

  expect(screen.getByLabelText('Active tenant beta')).toBeTruthy();
});
