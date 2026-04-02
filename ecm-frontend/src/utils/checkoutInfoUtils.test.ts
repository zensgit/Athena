import { CheckoutInfo } from 'types';
import {
  getCheckoutInfoAlertMessage,
  getCheckoutInfoAlertSeverity,
  getCheckoutInfoChipLabel,
} from './checkoutInfoUtils';

describe('checkoutInfoUtils', () => {
  it('formats owner checkout details', () => {
    const checkoutInfo: CheckoutInfo = {
      status: 'CHECKED_OUT_BY_YOU',
      checkoutAgeSeconds: 900,
      canCheckout: false,
      canCheckIn: true,
      canCancelCheckout: true,
      canKeepCheckedOut: true,
      requiresNewVersionFile: true,
    };

    expect(getCheckoutInfoChipLabel(checkoutInfo)).toBe('Checked out by you');
    expect(getCheckoutInfoAlertSeverity(checkoutInfo)).toBe('success');
    expect(getCheckoutInfoAlertMessage(checkoutInfo)).toBe('You hold the active checkout. Checked out 15m ago.');
  });

  it('formats foreign checkout details', () => {
    const checkoutInfo: CheckoutInfo = {
      status: 'CHECKED_OUT_BY_OTHER',
      checkoutUser: 'bob',
      checkoutAgeSeconds: 3660,
      canCheckout: false,
      canCheckIn: false,
      canCancelCheckout: false,
      canKeepCheckedOut: false,
      requiresNewVersionFile: true,
    };

    expect(getCheckoutInfoChipLabel(checkoutInfo)).toBe('Checked out by bob');
    expect(getCheckoutInfoAlertSeverity(checkoutInfo)).toBe('warning');
    expect(getCheckoutInfoAlertMessage(checkoutInfo)).toBe('Checked out by bob 1h 1m ago.');
  });

  it('surfaces blockers for available but not actionable documents', () => {
    const checkoutInfo: CheckoutInfo = {
      status: 'AVAILABLE',
      canCheckout: false,
      canCheckIn: false,
      canCancelCheckout: false,
      canKeepCheckedOut: false,
      requiresNewVersionFile: true,
      blockingReason: 'Cannot check out while locked by bob.',
    };

    expect(getCheckoutInfoChipLabel(checkoutInfo)).toBeNull();
    expect(getCheckoutInfoAlertSeverity(checkoutInfo)).toBe('info');
    expect(getCheckoutInfoAlertMessage(checkoutInfo)).toBe('Cannot check out while locked by bob.');
  });
});
