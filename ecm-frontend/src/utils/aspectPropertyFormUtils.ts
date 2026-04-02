import { AspectDefinition, PropertyDefinition } from 'services/contentModelService';

const NUMERIC_TYPES = new Set(['INT', 'LONG', 'FLOAT', 'DOUBLE']);

const propertyKey = (property: PropertyDefinition): string => property.qualifiedName || property.name;

export const buildAspectInitialPropertyValues = (
  aspect?: AspectDefinition | null
): Record<string, string> => {
  if (!aspect) {
    return {};
  }

  return aspect.properties.reduce<Record<string, string>>((values, property) => {
    values[propertyKey(property)] = property.defaultValue ?? '';
    return values;
  }, {});
};

export const getAspectPropertyListOptions = (property: PropertyDefinition): string[] => {
  const listConstraint = property.constraints.find((constraint) => constraint.constraintType === 'LIST');
  const values = listConstraint?.parameters?.values;
  return Array.isArray(values)
    ? values.map((value) => String(value).trim()).filter(Boolean)
    : [];
};

const normalizeSingleValue = (property: PropertyDefinition, value: string): unknown => {
  const trimmed = value.trim();
  if (!trimmed) {
    return undefined;
  }

  if (property.dataType === 'BOOLEAN') {
    return trimmed === 'true';
  }

  if (NUMERIC_TYPES.has(property.dataType)) {
    const parsed = Number(trimmed);
    return Number.isFinite(parsed) ? parsed : trimmed;
  }

  return trimmed;
};

export const buildAspectPropertyPayload = (
  aspect: AspectDefinition | null | undefined,
  values: Record<string, string>
): Record<string, unknown> => {
  if (!aspect) {
    return {};
  }

  return aspect.properties.reduce<Record<string, unknown>>((payload, property) => {
    const rawValue = values[propertyKey(property)] ?? '';
    if (property.multiValued) {
      const multiValues = rawValue
        .split(/[,\n]/)
        .map((value) => value.trim())
        .filter(Boolean);
      if (multiValues.length > 0) {
        payload[propertyKey(property)] = multiValues;
      }
      return payload;
    }

    const normalized = normalizeSingleValue(property, rawValue);
    if (normalized !== undefined) {
      payload[propertyKey(property)] = normalized;
    }
    return payload;
  }, {});
};
