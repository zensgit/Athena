import React from 'react';
import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { BrowserRouter } from 'react-router-dom';
import { store } from './store';
import App from './App';

test('renders Athena ECM app without crashing', () => {
  render(
    <Provider store={store}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </Provider>
  );
  // Basic smoke test - just checking if it mounts
  // In a real scenario, we'd look for specific text like "Athena ECM"
  // but that might be behind a login screen.
  expect(document.body).toBeInTheDocument();
});
