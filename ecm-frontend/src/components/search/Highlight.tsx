import React from 'react';
import { Typography } from '@mui/material';

interface HighlightProps {
  text?: string;
  highlights?: string[];
}

const Highlight: React.FC<HighlightProps> = ({ text, highlights }) => {
  if (highlights && highlights.length > 0) {
    return (
      <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}
        dangerouslySetInnerHTML={{ __html: highlights[0] }}
      />
    );
  }

  if (text) {
    return (
      <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
        {text}
      </Typography>
    );
  }

  return null;
};

export default Highlight;
