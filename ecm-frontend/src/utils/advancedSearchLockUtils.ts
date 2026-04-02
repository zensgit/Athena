type LockChipColor = 'default' | 'warning' | 'info';

export const getSearchResultLockChip = (
  locked?: boolean,
  lockedBy?: string,
): { label: string; tooltip: string; color: LockChipColor } | null => {
  if (!locked) {
    return null;
  }
  if (lockedBy && lockedBy.trim().length > 0) {
    return {
      label: `Locked by ${lockedBy.trim()}`,
      tooltip: `Locked by ${lockedBy.trim()}`,
      color: 'warning',
    };
  }
  return {
    label: 'Locked',
    tooltip: 'Locked',
    color: 'info',
  };
};
