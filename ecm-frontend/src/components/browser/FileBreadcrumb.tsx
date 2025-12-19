import React from 'react';
import { Breadcrumbs, Link, Typography } from '@mui/material';
import { NavigateNext, Home } from '@mui/icons-material';

interface FileBreadcrumbProps {
  path: string;
  onNavigate: (path: string) => void;
}

const FileBreadcrumb: React.FC<FileBreadcrumbProps> = ({ path, onNavigate }) => {
  const navParts = (() => {
    const parts = path.split('/').filter(Boolean);
    if (parts.length > 1 && parts[0].toLowerCase() === 'root') {
      while (parts.length > 1 && parts[1].toLowerCase() === 'root') {
        parts.splice(1, 1);
      }
    }
    return parts;
  })();
  const hasRootPrefix = navParts.length > 0 && navParts[0].toLowerCase() === 'root';
  const pathParts = hasRootPrefix ? navParts.slice(1) : navParts;
  const navOffset = hasRootPrefix ? 1 : 0;

  const handleClick = (index: number) => {
    const navIndex = index + navOffset;
    const targetPath = '/' + navParts.slice(0, navIndex + 1).join('/');
    onNavigate(targetPath);
  };

  return (
    <Breadcrumbs
      separator={<NavigateNext fontSize="small" />}
      aria-label="breadcrumb"
      sx={{ py: 1 }}
    >
      <Link
        underline="hover"
        sx={{ display: 'flex', alignItems: 'center', cursor: 'pointer' }}
        color="inherit"
        onClick={() => onNavigate('/')}
      >
        <Home sx={{ mr: 0.5 }} fontSize="small" />
        Root
      </Link>
      {pathParts.map((part, index) => {
        const isLast = index === pathParts.length - 1;
        return isLast ? (
          <Typography key={index} color="text.primary">
            {part}
          </Typography>
        ) : (
          <Link
            key={index}
            underline="hover"
            color="inherit"
            sx={{ cursor: 'pointer' }}
            onClick={() => handleClick(index)}
          >
            {part}
          </Link>
        );
      })}
    </Breadcrumbs>
  );
};

export default FileBreadcrumb;
