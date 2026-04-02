import { LockInfo } from 'types';

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

export const getLockInfoChipLabel = (lockInfo: LockInfo | null): string | null => {
  if (!lockInfo || lockInfo.status === 'NO_LOCK') {
    return null;
  }
  switch (lockInfo.status) {
    case 'LOCK_OWNER':
      return lockInfo.lockLifetime === 'EPHEMERAL' ? 'Locked by you (temp)' : 'Locked by you';
    case 'LOCKED_BY_OTHER':
      return lockInfo.lockedBy ? `Locked by ${lockInfo.lockedBy}` : 'Locked by another user';
    case 'LOCK_EXPIRED':
      return 'Lock expired';
    default:
      return 'Lock';
  }
};

export const getLockInfoAlertMessage = (lockInfo: LockInfo | null): string | null => {
  if (!lockInfo || lockInfo.status === 'NO_LOCK') {
    return null;
  }
  const remaining = formatDuration(lockInfo.remainingSeconds);
  switch (lockInfo.status) {
    case 'LOCK_OWNER':
      return lockInfo.lockLifetime === 'EPHEMERAL' && remaining
        ? `You hold a temporary lock. Expires in ${remaining}.`
        : 'You hold the active lock for this document.';
    case 'LOCKED_BY_OTHER':
      return lockInfo.lockedBy && remaining
        ? `Locked by ${lockInfo.lockedBy}. Expected to expire in ${remaining}.`
        : lockInfo.lockedBy
          ? `Locked by ${lockInfo.lockedBy}.`
          : 'Locked by another user.';
    case 'LOCK_EXPIRED':
      return lockInfo.lockedBy
        ? `The previous lock from ${lockInfo.lockedBy} has expired and is pending refresh.`
        : 'A previous lock has expired and is pending refresh.';
    default:
      return null;
  }
};

export const getLockInfoAlertSeverity = (lockInfo: LockInfo | null): 'info' | 'warning' | 'success' | null => {
  if (!lockInfo || lockInfo.status === 'NO_LOCK') {
    return null;
  }
  switch (lockInfo.status) {
    case 'LOCK_OWNER':
      return 'success';
    case 'LOCKED_BY_OTHER':
      return 'warning';
    case 'LOCK_EXPIRED':
      return 'info';
    default:
      return null;
  }
};
