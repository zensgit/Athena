import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { Provider } from 'react-redux';
import { ThemeProvider, createTheme, CssBaseline } from '@mui/material';
import { ToastContainer } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';

import { store } from './store';
import Login from './components/auth/Login';
import PrivateRoute from './components/auth/PrivateRoute';
import MainLayout from './components/layout/MainLayout';
import FileBrowser from './pages/FileBrowser';
import SearchResults from './pages/SearchResults';
import AdminDashboard from './pages/AdminDashboard';
import MailAutomationPage from './pages/MailAutomationPage';
import ContentTypesPage from './pages/ContentTypesPage';
import WebhookSubscriptionsPage from './pages/WebhookSubscriptionsPage';
import EditorPage from './pages/EditorPage';
import TasksPage from './pages/TasksPage';
import AdvancedSearchPage from './pages/AdvancedSearchPage';
import SystemStatusPage from './pages/SystemStatusPage';
import TrashPage from './pages/TrashPage';
import RulesPage from './pages/RulesPage';
import SettingsPage from './pages/SettingsPage';
import CorrespondentsPage from './pages/CorrespondentsPage';
import FavoritesPage from './pages/FavoritesPage';
import SavedSearchesPage from './pages/SavedSearchesPage';
import VersionHistoryDialog from './components/dialogs/VersionHistoryDialog';
import PermissionsDialog from './components/dialogs/PermissionsDialog';
import PropertiesDialog from './components/dialogs/PropertiesDialog';

const SearchDialog = React.lazy(() => import('./components/search/SearchDialog'));

const theme = createTheme({
  palette: {
    mode: 'light',
    primary: {
      main: '#1976d2',
    },
    secondary: {
      main: '#dc004e',
    },
  },
  typography: {
    fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
  },
  components: {
    MuiButton: {
      styleOverrides: {
        root: {
          textTransform: 'none',
        },
      },
    },
  },
});

const App: React.FC = () => {
  return (
    <Provider store={store}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <Router future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
          <Routes>
            <Route path="/login" element={<Login />} />
            <Route
              path="/"
              element={
                <PrivateRoute>
                  <MainLayout>
                    <Navigate to="/browse/root" replace />
                  </MainLayout>
                </PrivateRoute>
              }
            />
            <Route
              path="/browse/:nodeId"
              element={
                <PrivateRoute>
                  <MainLayout>
                    <FileBrowser />
                  </MainLayout>
                </PrivateRoute>
              }
            />
            <Route
              path="/search"
              element={
                <PrivateRoute>
                  <MainLayout>
                    <AdvancedSearchPage />
                  </MainLayout>
                </PrivateRoute>
              }
            />
            <Route
              path="/search-results"
              element={
                <PrivateRoute>
                  <MainLayout>
                    <SearchResults />
                  </MainLayout>
                </PrivateRoute>
              }
            />
            <Route
              path="/editor/:documentId"
              element={
                <PrivateRoute>
                  {/* Editor takes full screen, no MainLayout */}
                  <EditorPage />
                </PrivateRoute>
              }
            />
            <Route
              path="/tasks"
              element={
                <PrivateRoute>
                  <MainLayout>
                    <TasksPage />
                  </MainLayout>
                </PrivateRoute>
              }
            />
            <Route
              path="/status"
              element={
                <PrivateRoute requiredRoles={['ROLE_ADMIN']}>
                  <MainLayout>
                    <SystemStatusPage />
                  </MainLayout>
                </PrivateRoute>
              }
            />
            <Route
              path="/correspondents"
              element={
                <PrivateRoute requiredRoles={['ROLE_ADMIN', 'ROLE_EDITOR']}>
                  <MainLayout>
                    <CorrespondentsPage />
                  </MainLayout>
                </PrivateRoute>
              }
            />
            <Route
              path="/trash"
              element={
                <PrivateRoute>
                  <MainLayout>
                    <TrashPage />
                  </MainLayout>
                </PrivateRoute>
              }
            />
            <Route
              path="/favorites"
              element={
                <PrivateRoute>
                  <MainLayout>
                    <FavoritesPage />
                  </MainLayout>
                </PrivateRoute>
              }
            />
            <Route
              path="/saved-searches"
              element={
                <PrivateRoute>
                  <MainLayout>
                    <SavedSearchesPage />
                  </MainLayout>
                </PrivateRoute>
              }
            />
            <Route
              path="/admin"
              element={
                <PrivateRoute requiredRoles={['ROLE_ADMIN']}>
                  <MainLayout>
                    <AdminDashboard />
                  </MainLayout>
                </PrivateRoute>
              }
            />
            <Route
              path="/admin/mail"
              element={
                <PrivateRoute requiredRoles={['ROLE_ADMIN']}>
                  <MainLayout>
                    <MailAutomationPage />
                  </MainLayout>
                </PrivateRoute>
              }
            />
            <Route
              path="/admin/content-types"
              element={
                <PrivateRoute requiredRoles={['ROLE_ADMIN']}>
                  <MainLayout>
                    <ContentTypesPage />
                  </MainLayout>
                </PrivateRoute>
              }
            />
            <Route
              path="/admin/webhooks"
              element={
                <PrivateRoute requiredRoles={['ROLE_ADMIN']}>
                  <MainLayout>
                    <WebhookSubscriptionsPage />
                  </MainLayout>
                </PrivateRoute>
              }
            />
            <Route
              path="/rules"
              element={
                <PrivateRoute requiredRoles={['ROLE_ADMIN', 'ROLE_EDITOR']}>
                  <MainLayout>
                    <RulesPage />
                  </MainLayout>
                </PrivateRoute>
              }
            />
            <Route
              path="/unauthorized"
              element={
                <div>
                  <h1>Unauthorized</h1>
                  <p>You do not have permission to access this page.</p>
                </div>
              }
            />
            <Route
              path="/settings"
              element={
                <PrivateRoute>
                  <MainLayout>
                    <SettingsPage />
                  </MainLayout>
                </PrivateRoute>
              }
            />
          </Routes>

          {/* Global Dialogs - Must be inside Router for useNavigate */}
          <React.Suspense fallback={null}>
            <SearchDialog />
          </React.Suspense>
          <VersionHistoryDialog />
          <PermissionsDialog />
          <PropertiesDialog />
        </Router>

        <ToastContainer
          position="bottom-right"
          autoClose={5000}
          hideProgressBar={false}
          newestOnTop={false}
          closeOnClick
          rtl={false}
          pauseOnFocusLoss
          draggable
          pauseOnHover
        />
      </ThemeProvider>
    </Provider>
  );
};

export default App;
