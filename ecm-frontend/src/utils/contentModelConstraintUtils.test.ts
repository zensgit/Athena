import {
  buildConstraintParameters,
  formatConstraintLabel,
  getConstraintValidationMessage,
} from './contentModelConstraintUtils';

describe('contentModelConstraintUtils', () => {
  it('builds regex parameters', () => {
    expect(buildConstraintParameters('REGEX', {
      pattern: '^cm:.*$',
      values: '',
      min: '',
      max: '',
    })).toEqual({ pattern: '^cm:.*$' });
  });

  it('splits list values by comma and newline', () => {
    expect(buildConstraintParameters('LIST', {
      pattern: '',
      values: 'a, b\nc',
      min: '',
      max: '',
    })).toEqual({ values: ['a', 'b', 'c'] });
  });

  it('builds numeric range parameters', () => {
    expect(buildConstraintParameters('RANGE', {
      pattern: '',
      values: '',
      min: '1',
      max: '9',
    })).toEqual({ min: 1, max: 9 });
  });

  it('validates empty list and invalid bounds', () => {
    expect(getConstraintValidationMessage('LIST', {
      pattern: '',
      values: '   ',
      min: '',
      max: '',
    })).toBe('At least one list value is required');

    expect(getConstraintValidationMessage('LENGTH', {
      pattern: '',
      values: '',
      min: '10',
      max: '2',
    })).toBe('Minimum cannot be greater than maximum');
  });

  it('formats constraint labels', () => {
    expect(formatConstraintLabel({
      id: '1',
      constraintType: 'REGEX',
      parameters: { pattern: '^cm:.*$' },
    })).toBe('REGEX (^cm:.*$)');

    expect(formatConstraintLabel({
      id: '2',
      constraintType: 'LENGTH',
      parameters: { min: 1, max: 255 },
    })).toBe('LENGTH (1..255)');
  });
});
