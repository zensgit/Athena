import React from 'react';
import { render, screen } from '@testing-library/react';
import AppErrorBoundary, { buildCacheBustReloadUrl, buildRecoveryLoginUrl } from './AppErrorBoundary';

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

  it('shows chunk-load hint when dynamic import failure is emitted', async () => {
    const errorSpy = jest.spyOn(console, 'error').mockImplementation(() => undefined);
    render(
      <AppErrorBoundary>
        <div>safe-child</div>
      </AppErrorBoundary>
    );

    const rejectionEvent = new Event('unhandledrejection');
    Object.defineProperty(rejectionEvent, 'reason', {
      value: new Error('Loading chunk 42 failed.'),
      configurable: true,
    });
    window.dispatchEvent(rejectionEvent);

    expect(await screen.findByText('Athena ECM')).toBeTruthy();
    expect(screen.getByText(/application files may be outdated after an update/i)).toBeTruthy();
    errorSpy.mockRestore();
  });

  it('builds cache-busting reload urls for chunk-load recovery', () => {
    const output = buildCacheBustReloadUrl('http://localhost:3000/login?foo=bar');
    expect(output).toContain('foo=bar');
    expect(output).toMatch(/_ecm_reload=\d+/);
  });

  it('builds cache-busting login recovery urls', () => {
    const nowSpy = jest.spyOn(Date, 'now').mockReturnValue(1700000000000);
    const output = buildRecoveryLoginUrl('http://localhost:3000/browse/root?x=1', 'app_recovery');
    expect(output).toBe('http://localhost:3000/login?reason=app_recovery&_ecm_reload=1700000000000');
    nowSpy.mockRestore();
  });

  it('ignores non-fatal ResizeObserver window errors', async () => {
    const errorSpy = jest.spyOn(console, 'error').mockImplementation(() => undefined);
    const warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => undefined);
    render(
      <AppErrorBoundary>
        <div>safe-child</div>
      </AppErrorBoundary>
    );

    window.dispatchEvent(new ErrorEvent('error', { message: 'ResizeObserver loop limit exceeded' }));

    expect(screen.getByText('safe-child')).toBeTruthy();
    expect(screen.queryByText('Athena ECM')).toBeNull();
    expect(warnSpy).toHaveBeenCalled();
    errorSpy.mockRestore();
    warnSpy.mockRestore();
  });

  it('ignores abort-like unhandled rejections', async () => {
    const errorSpy = jest.spyOn(console, 'error').mockImplementation(() => undefined);
    const warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => undefined);
    render(
      <AppErrorBoundary>
        <div>safe-child</div>
      </AppErrorBoundary>
    );

    const rejectionEvent = new Event('unhandledrejection');
    Object.defineProperty(rejectionEvent, 'reason', {
      value: { name: 'AbortError', message: 'The operation was aborted', code: 'ERR_CANCELED' },
      configurable: true,
    });
    window.dispatchEvent(rejectionEvent);

    expect(screen.getByText('safe-child')).toBeTruthy();
    expect(screen.queryByText('Athena ECM')).toBeNull();
    expect(warnSpy).toHaveBeenCalled();
    errorSpy.mockRestore();
    warnSpy.mockRestore();
  });
});
