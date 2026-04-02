import { LockInfo } from 'types';
import { getLockInfoAlertMessage, getLockInfoAlertSeverity, getLockInfoChipLabel } from './lockInfoUtils';

describe('lockInfoUtils', () => {
  it('formats owner ephemeral lock details', () => {
    const lockInfo: LockInfo = {
      status: 'LOCK_OWNER',
      lockLifetime: 'EPHEMERAL',
      remainingSeconds: 900,
      canUnlock: true,
    };

    expect(getLockInfoChipLabel(lockInfo)).toBe('Locked by you (temp)');
    expect(getLockInfoAlertSeverity(lockInfo)).toBe('success');
    expect(getLockInfoAlertMessage(lockInfo)).toBe('You hold a temporary lock. Expires in 15m.');
  });

  it('formats foreign lock details', () => {
    const lockInfo: LockInfo = {
      status: 'LOCKED_BY_OTHER',
      lockedBy: 'bob',
      remainingSeconds: 3660,
      canUnlock: false,
    };

    expect(getLockInfoChipLabel(lockInfo)).toBe('Locked by bob');
    expect(getLockInfoAlertSeverity(lockInfo)).toBe('warning');
    expect(getLockInfoAlertMessage(lockInfo)).toBe('Locked by bob. Expected to expire in 1h 1m.');
  });

  it('formats expired lock details', () => {
    const lockInfo: LockInfo = {
      status: 'LOCK_EXPIRED',
      lockedBy: 'alice',
      remainingSeconds: 0,
      canUnlock: false,
    };

    expect(getLockInfoChipLabel(lockInfo)).toBe('Lock expired');
    expect(getLockInfoAlertSeverity(lockInfo)).toBe('info');
    expect(getLockInfoAlertMessage(lockInfo)).toBe('The previous lock from alice has expired and is pending refresh.');
  });
});
