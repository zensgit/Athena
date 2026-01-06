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
  render(
    <Provider store={store}>
      <App />
    </Provider>
  );
  expect(await screen.findByTestId('search-dialog')).toBeTruthy();
});
