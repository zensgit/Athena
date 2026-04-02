import { Node } from 'types';
import {
  getCancelCheckoutActionReason,
  getCheckInActionReason,
  getFileCheckoutActionReason,
  getFileCheckoutTooltip,
  isCheckedOutByAnotherUser,
} from './fileCheckoutBadgeUtils';

const baseNode: Node = {
  id: '1',
  name: 'contract.docx',
  nodeType: 'DOCUMENT',
  properties: {},
  aspects: [],
  created: '2026-03-26T00:00:00Z',
  modified: '2026-03-26T00:00:00Z',
  creator: 'alice',
  modifier: 'alice',
  path: '/contract.docx',
};

describe('fileCheckoutBadgeUtils', () => {
  it('returns null for non-checked-out nodes', () => {
    expect(getFileCheckoutTooltip(baseNode)).toBeNull();
  });

  it('returns owner tooltip for checked-out nodes', () => {
    expect(getFileCheckoutTooltip({ ...baseNode, checkedOut: true, checkoutUser: 'bob' })).toBe('Checked out by bob');
  });

  it('detects nodes checked out by another user', () => {
    expect(isCheckedOutByAnotherUser({ ...baseNode, checkedOut: true, checkoutUser: 'bob' }, 'alice')).toBe(true);
  });

  it('does not flag nodes checked out by the current user', () => {
    expect(isCheckedOutByAnotherUser({ ...baseNode, checkedOut: true, checkoutUser: 'alice' }, 'alice')).toBe(false);
    expect(isCheckedOutByAnotherUser({ ...baseNode, checkedOut: true, checkoutUser: 'Alice' }, 'alice')).toBe(false);
  });

  it('builds an action-level reason with checkout owner details', () => {
    expect(getFileCheckoutActionReason({ ...baseNode, checkedOut: true, checkoutUser: 'bob' }, 'delete this item', 'alice')).toBe(
      'Cannot delete this item while checked out by bob',
    );
  });

  it('allows admin to cancel another user checkout', () => {
    expect(getCancelCheckoutActionReason({ ...baseNode, checkedOut: true, checkoutUser: 'bob' }, 'alice', true)).toBeNull();
  });

  it('blocks non-admin cancel checkout for another user', () => {
    expect(getCancelCheckoutActionReason({ ...baseNode, checkedOut: true, checkoutUser: 'bob' }, 'alice', false)).toBe(
      'Only bob or an admin can cancel checkout',
    );
  });

  it('allows checkout owner to check in', () => {
    expect(getCheckInActionReason({ ...baseNode, checkedOut: true, checkoutUser: 'alice' }, 'alice', false)).toBeNull();
  });

  it('allows admin to check in another user checkout', () => {
    expect(getCheckInActionReason({ ...baseNode, checkedOut: true, checkoutUser: 'bob' }, 'alice', true)).toBeNull();
  });

  it('blocks non-admin check in for another user checkout', () => {
    expect(getCheckInActionReason({ ...baseNode, checkedOut: true, checkoutUser: 'bob' }, 'alice', false)).toBe(
      'Only bob or an admin can check in',
    );
  });
});
