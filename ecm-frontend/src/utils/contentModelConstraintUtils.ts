import { ConstraintDefinition, ConstraintType } from 'services/contentModelService';

export interface ConstraintFormState {
  pattern: string;
  values: string;
  min: string;
  max: string;
}

const parseOptionalNumber = (value: string): number | undefined => {
  const trimmed = value.trim();
  if (!trimmed) {
    return undefined;
  }
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : undefined;
};

export const buildConstraintParameters = (
  constraintType: ConstraintType,
  form: ConstraintFormState
): Record<string, unknown> => {
  switch (constraintType) {
    case 'REGEX':
      return { pattern: form.pattern.trim() };
    case 'LIST':
      return {
        values: form.values
          .split(/[,\n]/)
          .map((value) => value.trim())
          .filter(Boolean),
      };
    case 'RANGE':
    case 'LENGTH':
      return {
        ...(parseOptionalNumber(form.min) !== undefined ? { min: parseOptionalNumber(form.min) } : {}),
        ...(parseOptionalNumber(form.max) !== undefined ? { max: parseOptionalNumber(form.max) } : {}),
      };
    default:
      return {};
  }
};

export const getConstraintValidationMessage = (
  constraintType: ConstraintType,
  form: ConstraintFormState
): string | null => {
  if (constraintType === 'REGEX' && !form.pattern.trim()) {
    return 'Regex pattern is required';
  }
  if (constraintType === 'LIST') {
    const values = buildConstraintParameters('LIST', form).values as string[];
    if (!values.length) {
      return 'At least one list value is required';
    }
    return null;
  }
  if (constraintType === 'RANGE' || constraintType === 'LENGTH') {
    const min = parseOptionalNumber(form.min);
    const max = parseOptionalNumber(form.max);
    if (min === undefined && max === undefined) {
      return 'Enter at least one boundary';
    }
    if (min !== undefined && max !== undefined && min > max) {
      return 'Minimum cannot be greater than maximum';
    }
  }
  return null;
};

export const formatConstraintLabel = (constraint: ConstraintDefinition): string => {
  switch (constraint.constraintType) {
    case 'REGEX':
      return constraint.parameters?.pattern
        ? `REGEX (${String(constraint.parameters.pattern)})`
        : 'REGEX';
    case 'LIST': {
      const values = Array.isArray(constraint.parameters?.values)
        ? (constraint.parameters.values as unknown[])
        : [];
      return values.length ? `LIST (${values.join(', ')})` : 'LIST';
    }
    case 'RANGE':
    case 'LENGTH': {
      const min = constraint.parameters?.min;
      const max = constraint.parameters?.max;
      if (min !== undefined && max !== undefined) {
        return `${constraint.constraintType} (${min}..${max})`;
      }
      if (min !== undefined) {
        return `${constraint.constraintType} (min ${min})`;
      }
      if (max !== undefined) {
        return `${constraint.constraintType} (max ${max})`;
      }
      return constraint.constraintType;
    }
    default:
      return constraint.constraintType;
  }
};
