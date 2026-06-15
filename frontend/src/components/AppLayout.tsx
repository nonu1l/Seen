import { useState, useCallback } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';

export interface AppLayoutContext {
  registerReset: (fn: (() => void) | null) => void;
}

/**
 * 公共布局 — 顶部 header + 子路由内容。
 * /ai 路由时 header 扩展显示 "assistant" + 按钮。
 */
export default function AppLayout() {
  const location = useLocation();
  const navigate = useNavigate();
  const isAi = location.pathname === '/ai';
  const isSettings = location.pathname === '/settings';

  const [onReset, setOnReset] = useState<(() => void) | null>(null);

  const registerReset = useCallback((fn: (() => void) | null) => {
    setOnReset(() => fn);
  }, []);

  const handleBack = useCallback(() => {
    if (location.key === 'default') navigate('/');
    else navigate(-1);
  }, [location.key, navigate]);

  const context: AppLayoutContext = { registerReset };

  return (
    <div className="min-h-dvh flex flex-col px-3 pb-5 sm:pb-20 sm:px-8">
      <div className="mx-auto w-full max-w-7xl flex flex-col flex-1 min-h-0">

        <div className="app-header -mx-3 px-3 sm:-mx-8 sm:px-8 flex-shrink-0">
          <div className="app-header-row">
            <h1 className="text-2xl font-bold tracking-tight sm:text-3xl" style={{ color: 'var(--text-primary)' }}>
              seen<span style={{ color: 'var(--accent)' }}>.</span>
              {isAi && <span style={{ color: 'var(--text-secondary)', fontWeight: 500 }}> assistant</span>}
              {isSettings && <span style={{ color: 'var(--text-secondary)', fontWeight: 500 }}> settings</span>}
            </h1>
            {isSettings ? (
              <button type="button" onClick={handleBack}
                className="btn-icon" title="返回" aria-label="返回">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
                  <path d="M19 12H5" /><path d="M12 19l-7-7 7-7" />
                </svg>
              </button>
            ) : (
              <div className="flex items-center gap-1">
                <button type="button" onClick={() => navigate('/settings')}
                  className="btn-icon" title="设置" aria-label="设置">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round">
                    <path d="M12 15.5A3.5 3.5 0 1 0 12 8a3.5 3.5 0 0 0 0 7.5z" />
                    <path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 0 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6V21a2 2 0 0 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1A2 2 0 0 1 4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.6-1H3a2 2 0 0 1 0-4h.1a1.7 1.7 0 0 0 1.6-1.1 1.7 1.7 0 0 0-.3-1.9l-.1-.1A2 2 0 0 1 7.1 4.2l.1.1a1.7 1.7 0 0 0 1.9.3 1.7 1.7 0 0 0 1-1.6V3a2 2 0 0 1 4 0v.1a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1A2 2 0 0 1 19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1H21a2 2 0 0 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z" />
                  </svg>
                </button>
                {isAi && (
                  <>
                    <button type="button" onClick={() => navigate('/')}
                      className="btn-icon" title="退出" aria-label="退出">
                      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
                        <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
                      </svg>
                    </button>
                    <button type="button" onClick={() => onReset?.()}
                      className="btn-icon" title="重置" aria-label="重置">
                      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
                        <polyline points="1 4 1 10 7 10" /><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10" />
                      </svg>
                    </button>
                  </>
                )}
              </div>
            )}
          </div>
        </div>

        <Outlet context={context} />
      </div>
    </div>
  );
}
