import { getSearchResultLockChip } from './advancedSearchLockUtils';

describe('advancedSearchLockUtils', () => {
  it('returns null for unlocked results', () => {
    expect(getSearchResultLockChip(false, 'alice')).toBeNull();
    expect(getSearchResultLockChip(undefined, undefined)).toBeNull();
  });

  it('returns owner-aware chip for locked results', () => {
    expect(getSearchResultLockChip(true, 'bob')).toEqual({
      label: 'Locked by bob',
      tooltip: 'Locked by bob',
      color: 'warning',
    });
  });

  it('returns generic chip when lock owner missing', () => {
    expect(getSearchResultLockChip(true, '')).toEqual({
      label: 'Locked',
      tooltip: 'Locked',
      color: 'info',
    });
  });
});
