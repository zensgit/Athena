import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAppSelector } from '@/store';

interface PrivateRouteProps {
  children: React.ReactNode;
  requiredRoles?: string[];
}

const PrivateRoute: React.FC<PrivateRouteProps> = ({ children, requiredRoles }) => {
  const location = useLocation();
  const { isAuthenticated, user } = useAppSelector((state) => state.auth);

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (requiredRoles && user) {
    const hasRequiredRole = requiredRoles.some((role) => user.roles.includes(role));
    if (!hasRequiredRole) {
      return <Navigate to="/unauthorized" replace />;
    }
  }

  return <>{children}</>;
};

export default PrivateRoute;