import { Node } from 'types';

export const getFileLockTooltip = (node: Node): string | null => {
  if (!node.locked) {
    return null;
  }
  return node.lockedBy ? `Locked by ${node.lockedBy}` : 'Locked';
};

const normalizeUsername = (username?: string | null): string | null => {
  const normalized = username?.trim().toLowerCase();
  return normalized || null;
};

export const isLockedByAnotherUser = (node: Node, currentUsername?: string | null): boolean => {
  if (!node.locked) {
    return false;
  }
  const owner = normalizeUsername(node.lockedBy);
  const current = normalizeUsername(currentUsername);
  if (!owner) {
    return true;
  }
  return owner !== current;
};

export const getFileLockActionReason = (
  node: Node,
  actionLabel: string,
  currentUsername?: string | null,
): string | null => {
  if (!isLockedByAnotherUser(node, currentUsername)) {
    return null;
  }
  if (node.lockedBy) {
    return `Cannot ${actionLabel} while locked by ${node.lockedBy}`;
  }
  return `Cannot ${actionLabel} while this item is locked`;
};
