import React from 'react';

type AppErrorBoundaryProps = {
  children: React.ReactNode;
};

type AppErrorBoundaryState = {
  hasError: boolean;
  message: string;
};

class AppErrorBoundary extends React.Component<AppErrorBoundaryProps, AppErrorBoundaryState> {
  state: AppErrorBoundaryState = {
    hasError: false,
    message: '',
  };

  static getDerivedStateFromError(error: unknown): AppErrorBoundaryState {
    return {
      hasError: true,
      message: error instanceof Error ? error.message : 'Unexpected application error',
    };
  }

  componentDidCatch(error: unknown, errorInfo: React.ErrorInfo) {
    console.error('AppErrorBoundary caught render failure', { error, componentStack: errorInfo.componentStack });
  }

  private handleReload = () => {
    window.location.reload();
  };

  private handleGoToLogin = () => {
    try {
      window.localStorage.removeItem('ecm_e2e_force_render_error');
    } catch {
      // Ignore storage cleanup errors.
    }
    window.location.assign('/login');
  };

  render() {
    if (!this.state.hasError) {
      return this.props.children;
    }

    return (
      <div
        style={{
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          backgroundColor: '#f5f5f5',
          padding: '24px',
        }}
      >
        <div
          style={{
            maxWidth: '520px',
            width: '100%',
            backgroundColor: '#fff',
            border: '1px solid #ddd',
            borderRadius: '8px',
            padding: '24px',
            boxShadow: '0 4px 16px rgba(0, 0, 0, 0.08)',
          }}
        >
          <h1 style={{ marginTop: 0, marginBottom: '12px', fontSize: '28px' }}>Athena ECM</h1>
          <p style={{ marginTop: 0, marginBottom: '12px', color: '#333' }}>
            The page encountered an unexpected error. You can refresh and try again.
          </p>
          {process.env.NODE_ENV !== 'production' && (
            <p style={{ marginTop: 0, marginBottom: '16px', color: '#a40000' }}>
              Details: {this.state.message || 'unknown error'}
            </p>
          )}
          <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
            <button type="button" onClick={this.handleReload}>
              Reload
            </button>
            <button type="button" onClick={this.handleGoToLogin}>
              Back to Login
            </button>
          </div>
        </div>
      </div>
    );
  }
}

export default AppErrorBoundary;
