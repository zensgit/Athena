import {
  getAdvancedSearchCancelCheckoutReason,
  getAdvancedSearchCheckInActionReason,
  getAdvancedSearchCheckoutActionReason,
} from './advancedSearchActionUtils';

describe('advancedSearchActionUtils', () => {
  it('blocks check out when a foreign lock is active', () => {
    expect(
      getAdvancedSearchCheckoutActionReason({ checkedOut: false, locked: true, lockedBy: 'bob' }, 'alice'),
    ).toBe('Cannot check out while locked by bob');
  });

  it('allows check out when lock belongs to current user', () => {
    expect(
      getAdvancedSearchCheckoutActionReason({ checkedOut: false, locked: true, lockedBy: 'alice' }, 'alice'),
    ).toBeNull();
  });

  it('blocks cancel checkout for foreign owner when not admin', () => {
    expect(
      getAdvancedSearchCancelCheckoutReason({ checkedOut: true, checkoutUser: 'bob' }, 'alice', false),
    ).toBe('Only bob or an admin can cancel checkout');
  });

  it('allows admin to cancel foreign checkout', () => {
    expect(
      getAdvancedSearchCancelCheckoutReason({ checkedOut: true, checkoutUser: 'bob' }, 'alice', true),
    ).toBeNull();
  });

  it('allows checkout owner to check in', () => {
    expect(
      getAdvancedSearchCheckInActionReason({ checkedOut: true, checkoutUser: 'alice' }, 'alice', false),
    ).toBeNull();
  });

  it('allows admin to check in foreign checkout', () => {
    expect(
      getAdvancedSearchCheckInActionReason({ checkedOut: true, checkoutUser: 'bob' }, 'alice', true),
    ).toBeNull();
  });

  it('blocks non-admin check in for foreign checkout', () => {
    expect(
      getAdvancedSearchCheckInActionReason({ checkedOut: true, checkoutUser: 'bob' }, 'alice', false),
    ).toBe('Only bob or an admin can check in');
  });
});
