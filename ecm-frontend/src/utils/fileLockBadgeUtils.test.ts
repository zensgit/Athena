import { Node } from 'types';
import {
  getFileLockActionReason,
  getFileLockTooltip,
  isLockedByAnotherUser,
} from './fileLockBadgeUtils';

const baseNode: Node = {
  id: '1',
  name: 'contract.pdf',
  nodeType: 'DOCUMENT',
  properties: {},
  aspects: [],
  created: '2026-03-26T00:00:00Z',
  modified: '2026-03-26T00:00:00Z',
  creator: 'alice',
  modifier: 'alice',
  path: '/contract.pdf',
};

describe('fileLockBadgeUtils', () => {
  it('returns null for unlocked nodes', () => {
    expect(getFileLockTooltip(baseNode)).toBeNull();
  });

  it('returns owner tooltip for locked nodes', () => {
    expect(getFileLockTooltip({ ...baseNode, locked: true, lockedBy: 'bob' })).toBe('Locked by bob');
  });

  it('returns generic tooltip when owner missing', () => {
    expect(getFileLockTooltip({ ...baseNode, locked: true })).toBe('Locked');
  });

  it('detects nodes locked by another user', () => {
    expect(isLockedByAnotherUser({ ...baseNode, locked: true, lockedBy: 'bob' }, 'alice')).toBe(true);
  });

  it('does not flag nodes locked by the current user', () => {
    expect(isLockedByAnotherUser({ ...baseNode, locked: true, lockedBy: 'alice' }, 'alice')).toBe(false);
    expect(isLockedByAnotherUser({ ...baseNode, locked: true, lockedBy: 'Alice' }, 'alice')).toBe(false);
  });

  it('treats lock owner gaps as guarded for write actions', () => {
    expect(isLockedByAnotherUser({ ...baseNode, locked: true }, 'alice')).toBe(true);
  });

  it('builds an action-level reason with lock owner details', () => {
    expect(getFileLockActionReason({ ...baseNode, locked: true, lockedBy: 'bob' }, 'delete', 'alice')).toBe(
      'Cannot delete while locked by bob',
    );
  });

  it('returns null action reason for the lock owner', () => {
    expect(getFileLockActionReason({ ...baseNode, locked: true, lockedBy: 'alice' }, 'move', 'alice')).toBeNull();
  });
});
