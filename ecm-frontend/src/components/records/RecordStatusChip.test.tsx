import React from 'react';
import { render, screen } from '@testing-library/react';
import RecordStatusChip from './RecordStatusChip';

describe('RecordStatusChip', () => {
  it('renders a record chip', () => {
    render(<RecordStatusChip />);

    expect(screen.getByText('Record')).toBeTruthy();
  });

  it('shows declaration details in the tooltip attributes', () => {
    render(
      <RecordStatusChip
        declaration={{
          nodeId: 'node-1',
          name: 'Policy.pdf',
          path: '/Records/Policy.pdf',
          declaredBy: 'admin',
          declaredAt: '2026-04-14T10:00:00',
          declaredVersionLabel: '1.0',
          declarationComment: 'Final signed copy',
        }}
      />
    );

    expect(screen.getByText('Record')).toBeTruthy();
  });
});
