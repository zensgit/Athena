type CheckoutChipColor = 'default' | 'warning' | 'info';

export const getSearchResultCheckoutChip = (
  checkedOut?: boolean,
  checkoutUser?: string,
): { label: string; tooltip: string; color: CheckoutChipColor } | null => {
  if (!checkedOut) {
    return null;
  }
  if (checkoutUser && checkoutUser.trim().length > 0) {
    return {
      label: `Checked out by ${checkoutUser.trim()}`,
      tooltip: `Checked out by ${checkoutUser.trim()}`,
      color: 'warning',
    };
  }
  return {
    label: 'Checked out',
    tooltip: 'Checked out',
    color: 'info',
  };
};
