import { Node } from 'types';

const normalizeUsername = (username?: string | null): string | null => {
  const normalized = username?.trim().toLowerCase();
  return normalized || null;
};

export const getFileCheckoutTooltip = (node: Node): string | null => {
  if (!node.checkedOut) {
    return null;
  }
  return node.checkoutUser ? `Checked out by ${node.checkoutUser}` : 'Checked out';
};

export const isCheckedOutByAnotherUser = (node: Node, currentUsername?: string | null): boolean => {
  if (!node.checkedOut) {
    return false;
  }
  const owner = normalizeUsername(node.checkoutUser);
  const current = normalizeUsername(currentUsername);
  if (!owner) {
    return true;
  }
  return owner !== current;
};

export const getFileCheckoutActionReason = (
  node: Node,
  actionLabel: string,
  currentUsername?: string | null,
): string | null => {
  if (!isCheckedOutByAnotherUser(node, currentUsername)) {
    return null;
  }
  if (node.checkoutUser) {
    return `Cannot ${actionLabel} while checked out by ${node.checkoutUser}`;
  }
  return `Cannot ${actionLabel} while this item is checked out`;
};

export const getCancelCheckoutActionReason = (
  node: Node,
  currentUsername?: string | null,
  isAdmin = false,
): string | null => {
  if (!node.checkedOut) {
    return null;
  }
  if (isAdmin) {
    return null;
  }
  if (isCheckedOutByAnotherUser(node, currentUsername)) {
    return node.checkoutUser
      ? `Only ${node.checkoutUser} or an admin can cancel checkout`
      : 'Only the checkout owner or an admin can cancel checkout';
  }
  return null;
};

export const getCheckInActionReason = (
  node: Node,
  currentUsername?: string | null,
  isAdmin = false,
): string | null => {
  if (!node.checkedOut) {
    return 'Only checked-out documents can be checked in';
  }
  if (isAdmin) {
    return null;
  }
  if (isCheckedOutByAnotherUser(node, currentUsername)) {
    return node.checkoutUser
      ? `Only ${node.checkoutUser} or an admin can check in`
      : 'Only the checkout owner or an admin can check in';
  }
  return null;
};
