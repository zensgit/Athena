type SearchResultActionState = {
  checkedOut?: boolean;
  checkoutUser?: string;
  locked?: boolean;
  lockedBy?: string;
};

const normalizeUsername = (username?: string | null): string | null => {
  const normalized = username?.trim().toLowerCase();
  return normalized || null;
};

export const getAdvancedSearchCheckoutActionReason = (
  result: SearchResultActionState,
  currentUsername?: string | null,
): string | null => {
  if (result.checkedOut) {
    return null;
  }
  if (!result.locked) {
    return null;
  }
  const lockOwner = normalizeUsername(result.lockedBy);
  const current = normalizeUsername(currentUsername);
  if (lockOwner && lockOwner === current) {
    return null;
  }
  if (result.lockedBy) {
    return `Cannot check out while locked by ${result.lockedBy}`;
  }
  return 'Cannot check out while this item is locked';
};

export const getAdvancedSearchCancelCheckoutReason = (
  result: SearchResultActionState,
  currentUsername?: string | null,
  isAdmin = false,
): string | null => {
  if (!result.checkedOut) {
    return null;
  }
  if (isAdmin) {
    return null;
  }
  const owner = normalizeUsername(result.checkoutUser);
  const current = normalizeUsername(currentUsername);
  if (owner && owner === current) {
    return null;
  }
  if (result.checkoutUser) {
    return `Only ${result.checkoutUser} or an admin can cancel checkout`;
  }
  return 'Only the checkout owner or an admin can cancel checkout';
};

export const getAdvancedSearchCheckInActionReason = (
  result: SearchResultActionState,
  currentUsername?: string | null,
  isAdmin = false,
): string | null => {
  if (!result.checkedOut) {
    return 'Only checked-out documents can be checked in';
  }
  if (isAdmin) {
    return null;
  }
  const owner = normalizeUsername(result.checkoutUser);
  const current = normalizeUsername(currentUsername);
  if (owner && owner === current) {
    return null;
  }
  if (result.checkoutUser) {
    return `Only ${result.checkoutUser} or an admin can check in`;
  }
  return 'Only the checkout owner or an admin can check in';
};
