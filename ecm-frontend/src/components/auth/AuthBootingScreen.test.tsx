import React from 'react';
import { act, fireEvent, render, screen } from '@testing-library/react';
import AuthBootingScreen from './AuthBootingScreen';

beforeEach(() => {
  jest.useFakeTimers();
});

afterEach(() => {
  jest.useRealTimers();
});

test('shows startup loading state before watchdog timeout', () => {
  render(<AuthBootingScreen watchdogMs={8_000} />);

  expect(screen.getByText(/initializing sign-in/i)).toBeTruthy();
  expect(screen.queryByTestId('auth-booting-watchdog-alert')).toBeNull();
});

test('shows watchdog banner and emits callback once after timeout', () => {
  const onWatchdogTriggered = jest.fn();

  render(<AuthBootingScreen watchdogMs={5_000} onWatchdogTriggered={onWatchdogTriggered} />);

  act(() => {
    jest.advanceTimersByTime(5_000);
  });

  expect(screen.getByTestId('auth-booting-watchdog-alert')).toBeTruthy();
  expect(onWatchdogTriggered).toHaveBeenCalledTimes(1);

  act(() => {
    jest.advanceTimersByTime(20_000);
  });
  expect(onWatchdogTriggered).toHaveBeenCalledTimes(1);
});

test('runs recovery actions from watchdog controls', () => {
  const onReload = jest.fn();
  const onContinueToLogin = jest.fn();

  render(
    <AuthBootingScreen
      watchdogMs={1_000}
      onReload={onReload}
      onContinueToLogin={onContinueToLogin}
    />
  );

  act(() => {
    jest.advanceTimersByTime(1_000);
  });

  fireEvent.click(screen.getByTestId('auth-booting-watchdog-reload'));
  fireEvent.click(screen.getByTestId('auth-booting-watchdog-continue-login'));

  expect(onReload).toHaveBeenCalledTimes(1);
  expect(onContinueToLogin).toHaveBeenCalledTimes(1);
});
