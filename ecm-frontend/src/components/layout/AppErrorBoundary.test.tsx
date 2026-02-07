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

  it('renders fallback UI when window error is emitted', async () => {
    const errorSpy = jest.spyOn(console, 'error').mockImplementation(() => undefined);
    render(
      <AppErrorBoundary>
        <div>safe-child</div>
      </AppErrorBoundary>
    );

    window.dispatchEvent(new ErrorEvent('error', { error: new Error('runtime-window-error'), message: 'runtime-window-error' }));

    expect(await screen.findByText('Athena ECM')).toBeTruthy();
    expect(screen.getByText(/runtime-window-error/i)).toBeTruthy();
    errorSpy.mockRestore();
  });

  it('renders fallback UI when unhandled rejection is emitted', async () => {
    const errorSpy = jest.spyOn(console, 'error').mockImplementation(() => undefined);
    render(
      <AppErrorBoundary>
        <div>safe-child</div>
      </AppErrorBoundary>
    );

    const rejectionEvent = new Event('unhandledrejection');
    Object.defineProperty(rejectionEvent, 'reason', {
      value: new Error('runtime-unhandled-rejection'),
      configurable: true,
    });
    window.dispatchEvent(rejectionEvent);

    expect(await screen.findByText('Athena ECM')).toBeTruthy();
    expect(screen.getByText(/runtime-unhandled-rejection/i)).toBeTruthy();
    errorSpy.mockRestore();
  });
});
