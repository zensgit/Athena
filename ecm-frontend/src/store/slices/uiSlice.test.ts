import uiReducer, { setSidebarOpen, toggleSidebar } from 'store/slices/uiSlice';

test('toggleSidebar persists to localStorage', () => {
  window.localStorage.removeItem('athena.ecm.sidebarOpen');

  const initial = uiReducer(undefined, { type: 'ui/init' });
  expect(initial.sidebarOpen).toBe(true);

  const next = uiReducer(initial, toggleSidebar());
  expect(window.localStorage.getItem('athena.ecm.sidebarOpen')).toBe(String(next.sidebarOpen));
});

test('setSidebarOpen persists to localStorage', () => {
  window.localStorage.removeItem('athena.ecm.sidebarOpen');

  const initial = uiReducer(undefined, { type: 'ui/init' });
  const next = uiReducer(initial, setSidebarOpen(false));

  expect(next.sidebarOpen).toBe(false);
  expect(window.localStorage.getItem('athena.ecm.sidebarOpen')).toBe('false');
});

