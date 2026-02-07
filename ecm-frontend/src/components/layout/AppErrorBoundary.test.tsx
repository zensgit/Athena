import React from 'react';
import { render, screen } from '@testing-library/react';
import AppErrorBoundary from './AppErrorBoundary';

const ThrowingComponent: React.FC = () => {
  throw new Error('boundary-test-error');
};

describe('AppErrorBoundary', () => {
  it('renders fallback UI when child throws', () => {
    const errorSpy = jest.spyOn(console, 'error').mockImplementation(() => undefined);
    render(
      <AppErrorBoundary>
        <ThrowingComponent />
      </AppErrorBoundary>
    );

    expect(screen.getByText('Athena ECM')).toBeTruthy();
    expect(screen.getByText(/unexpected error/i)).toBeTruthy();
    expect(screen.getByRole('button', { name: /reload/i })).toBeTruthy();
    expect(screen.getByRole('button', { name: /back to login/i })).toBeTruthy();
    expect(screen.getByText(/boundary-test-error/i)).toBeTruthy();

    errorSpy.mockRestore();
  });
});
