import React from 'react';
import { Chip, Tooltip } from '@mui/material';
import { SxProps, Theme } from '@mui/material/styles';
import { RecordDeclaration } from 'types';

interface RecordStatusChipProps {
  declaration?: RecordDeclaration | null;
  size?: 'small' | 'medium';
  sx?: SxProps<Theme>;
  variant?: 'filled' | 'outlined';
}

const formatDeclaredAt = (value?: string | null) => {
  if (!value) {
    return null;
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  return parsed.toLocaleString();
};

const buildTooltip = (declaration?: RecordDeclaration | null) => {
  if (!declaration) {
    return 'Declared as a record';
  }
  const lines = ['Declared as a record'];
  if (declaration.declaredBy) {
    lines.push(`By ${declaration.declaredBy}`);
  }
  const declaredAtLabel = formatDeclaredAt(declaration.declaredAt);
  if (declaredAtLabel) {
    lines.push(`At ${declaredAtLabel}`);
  }
  if (declaration.declaredVersionLabel) {
    lines.push(`Version ${declaration.declaredVersionLabel}`);
  }
  if (declaration.declarationComment) {
    lines.push(`Comment: ${declaration.declarationComment}`);
  }
  if (declaration.recordCategoryPath) {
    lines.push(`Category: ${declaration.recordCategoryPath}`);
  }
  return lines.join('\n');
};

const RecordStatusChip: React.FC<RecordStatusChipProps> = ({
  declaration,
  size = 'small',
  sx,
  variant = 'outlined',
}) => {
  const chip = (
    <Chip
      size={size}
      color="warning"
      variant={variant}
      label="Record"
      sx={sx}
    />
  );

  return (
    <Tooltip
      title={buildTooltip(declaration)}
      arrow
      slotProps={{ tooltip: { sx: { whiteSpace: 'pre-line' } } }}
    >
      {chip}
    </Tooltip>
  );
};

export default RecordStatusChip;
