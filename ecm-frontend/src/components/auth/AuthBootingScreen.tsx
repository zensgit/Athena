import React from 'react';

type AuthBootingScreenProps = {
  watchdogMs?: number;
  onWatchdogTriggered?: () => void;
  onReload?: () => void;
  onContinueToLogin?: () => void;
};

const normalizeWatchdogMs = (value: number | undefined): number => {
  if (!Number.isFinite(value)) {
    return 12_000;
  }
  const ms = Number(value);
  if (ms <= 0) {
    return 12_000;
  }
  return Math.floor(ms);
};

const AuthBootingScreen: React.FC<AuthBootingScreenProps> = ({
  watchdogMs,
  onWatchdogTriggered,
  onReload,
  onContinueToLogin,
}) => {
  const effectiveWatchdogMs = normalizeWatchdogMs(watchdogMs);
  const [watchdogTriggered, setWatchdogTriggered] = React.useState(false);
  const didEmitWatchdogRef = React.useRef(false);

  React.useEffect(() => {
    setWatchdogTriggered(false);
    didEmitWatchdogRef.current = false;

    const timeoutHandle = window.setTimeout(() => {
      setWatchdogTriggered(true);
      if (!didEmitWatchdogRef.current) {
        didEmitWatchdogRef.current = true;
        onWatchdogTriggered?.();
      }
    }, effectiveWatchdogMs);

    return () => {
      window.clearTimeout(timeoutHandle);
    };
  }, [effectiveWatchdogMs, onWatchdogTriggered]);

  const handleReload = () => {
    if (onReload) {
      onReload();
      return;
    }
    window.location.reload();
  };

  const handleContinueToLogin = () => {
    onContinueToLogin?.();
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: '12px',
        backgroundColor: '#f5f5f5',
        color: '#333',
        fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
        padding: '24px',
      }}
      data-testid="auth-booting-screen"
    >
      <div
        style={{
          width: '34px',
          height: '34px',
          borderRadius: '50%',
          border: '4px solid #d9d9d9',
          borderTopColor: '#1976d2',
          animation: 'ecm-auth-spin 1s linear infinite',
        }}
      />
      <div>Initializing sign-in...</div>
      {watchdogTriggered && (
        <div
          style={{
            marginTop: '8px',
            maxWidth: '420px',
            width: '100%',
            borderRadius: '8px',
            border: '1px solid #ffcc80',
            backgroundColor: '#fff8e1',
            color: '#5d4037',
            padding: '14px',
            display: 'flex',
            flexDirection: 'column',
            gap: '10px',
          }}
          data-testid="auth-booting-watchdog-alert"
        >
          <div>Sign-in initialization is taking longer than expected.</div>
          <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
            <button
              type="button"
              onClick={handleReload}
              data-testid="auth-booting-watchdog-reload"
              style={{
                background: '#1976d2',
                border: 'none',
                color: '#fff',
                borderRadius: '4px',
                padding: '8px 12px',
                cursor: 'pointer',
              }}
            >
              Reload
            </button>
            <button
              type="button"
              onClick={handleContinueToLogin}
              data-testid="auth-booting-watchdog-continue-login"
              style={{
                background: '#fff',
                border: '1px solid #1976d2',
                color: '#1976d2',
                borderRadius: '4px',
                padding: '8px 12px',
                cursor: 'pointer',
              }}
            >
              Continue to login
            </button>
          </div>
        </div>
      )}
      <style>
        {`
          @keyframes ecm-auth-spin {
            from { transform: rotate(0deg); }
            to { transform: rotate(360deg); }
          }
        `}
      </style>
    </div>
  );
};

export default AuthBootingScreen;
