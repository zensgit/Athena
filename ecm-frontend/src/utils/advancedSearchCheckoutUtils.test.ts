import { getSearchResultCheckoutChip } from './advancedSearchCheckoutUtils';

describe('advancedSearchCheckoutUtils', () => {
  it('returns null for available results', () => {
    expect(getSearchResultCheckoutChip(false, 'alice')).toBeNull();
    expect(getSearchResultCheckoutChip(undefined, undefined)).toBeNull();
  });

  it('returns owner-aware chip for checked out results', () => {
    expect(getSearchResultCheckoutChip(true, 'alice')).toEqual({
      label: 'Checked out by alice',
      tooltip: 'Checked out by alice',
      color: 'warning',
    });
  });

  it('returns generic chip when checkout owner missing', () => {
    expect(getSearchResultCheckoutChip(true, '')).toEqual({
      label: 'Checked out',
      tooltip: 'Checked out',
      color: 'info',
    });
  });
});
