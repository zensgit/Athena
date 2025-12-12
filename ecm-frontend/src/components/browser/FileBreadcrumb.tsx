import React from 'react';
import { Breadcrumbs, Link, Typography } from '@mui/material';
import { NavigateNext, Home } from '@mui/icons-material';

interface FileBreadcrumbProps {
  path: string;
  onNavigate: (path: string) => void;
}

const FileBreadcrumb: React.FC<FileBreadcrumbProps> = ({ path, onNavigate }) => {
  const pathParts = path.split('/').filter(Boolean);

  const handleClick = (index: number) => {
    const targetPath = '/' + pathParts.slice(0, index + 1).join('/');
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
