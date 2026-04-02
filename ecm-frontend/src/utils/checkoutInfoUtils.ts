import { CheckoutInfo } from 'types';

const formatDuration = (seconds?: number | null): string | null => {
  if (seconds === null || seconds === undefined) {
    return null;
  }
  if (seconds < 60) {
    return `${seconds}s`;
  }
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) {
    return `${minutes}m`;
  }
  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;
  return remainingMinutes > 0 ? `${hours}h ${remainingMinutes}m` : `${hours}h`;
};

export const getCheckoutInfoChipLabel = (checkoutInfo: CheckoutInfo | null): string | null => {
  if (!checkoutInfo || checkoutInfo.status === 'AVAILABLE') {
    return null;
  }
  if (checkoutInfo.status === 'CHECKED_OUT_BY_YOU') {
    return 'Checked out by you';
  }
  return checkoutInfo.checkoutUser ? `Checked out by ${checkoutInfo.checkoutUser}` : 'Checked out';
};

export const getCheckoutInfoAlertMessage = (checkoutInfo: CheckoutInfo | null): string | null => {
  if (!checkoutInfo) {
    return null;
  }
  const age = formatDuration(checkoutInfo.checkoutAgeSeconds);
  switch (checkoutInfo.status) {
    case 'CHECKED_OUT_BY_YOU':
      return age
        ? `You hold the active checkout. Checked out ${age} ago.`
        : 'You hold the active checkout for this document.';
    case 'CHECKED_OUT_BY_OTHER':
      return checkoutInfo.checkoutUser && age
        ? `Checked out by ${checkoutInfo.checkoutUser} ${age} ago.`
        : checkoutInfo.checkoutUser
          ? `Checked out by ${checkoutInfo.checkoutUser}.`
          : 'Checked out by another user.';
    case 'AVAILABLE':
      return checkoutInfo.blockingReason || null;
    default:
      return null;
  }
};

export const getCheckoutInfoAlertSeverity = (checkoutInfo: CheckoutInfo | null): 'info' | 'warning' | 'success' | null => {
  if (!checkoutInfo) {
    return null;
  }
  switch (checkoutInfo.status) {
    case 'CHECKED_OUT_BY_YOU':
      return 'success';
    case 'CHECKED_OUT_BY_OTHER':
      return 'warning';
    case 'AVAILABLE':
      return checkoutInfo.blockingReason ? 'info' : null;
    default:
      return null;
  }
};
