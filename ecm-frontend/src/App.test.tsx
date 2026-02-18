import React from 'react';
import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { store } from './store';
import App from './App';

jest.mock('./components/search/SearchDialog', () => ({
  __esModule: true,
  default: () => <div data-testid="search-dialog" />,
}));

test('renders Athena ECM app without crashing', async () => {
  window.history.pushState({}, 'Login', '/login');

  render(
    <Provider store={store}>
      <App />
    </Provider>
  );
  expect(await screen.findByRole('heading', { name: 'Athena ECM' })).toBeTruthy();
  expect(screen.getByRole('button', { name: 'Sign in with Keycloak' })).toBeTruthy();
});

test('renders unauthorized page copy', async () => {
  window.history.pushState({}, 'Unauthorized', '/unauthorized');

  render(
    <Provider store={store}>
      <App />
    </Provider>
  );

  expect(await screen.findByRole('heading', { name: 'Unauthorized' })).toBeTruthy();
  expect(screen.getByText('You do not have permission to access this page.')).toBeTruthy();
});

test('unknown route falls back to login instead of blank page', async () => {
  window.history.pushState({}, 'Unknown', '/definitely-not-a-real-route');

  render(
    <Provider store={store}>
      <App />
    </Provider>
  );

  expect(await screen.findByRole('heading', { name: 'Athena ECM' })).toBeTruthy();
  expect(screen.getByRole('button', { name: 'Sign in with Keycloak' })).toBeTruthy();
});
