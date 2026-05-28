import { useState, useCallback } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';

export interface AppLayoutContext {
  setOnReset: (fn: (() => void) | null) => void;
}

/**
 * 公共布局 — 顶部 header + 子路由内容。
 * /ai 路由时 header 扩展显示 "assistant" + 按钮。
 */
export default function AppLayout() {
  const location = useLocation();
  const navigate = useNavigate();
  const isAi = location.pathname === '/ai';

  const [onReset, setOnReset] = useState<(() => void) | null>(null);

  const handleSetReset = useCallback((fn: (() => void) | null) => {
    setOnReset(fn);
  }, []);

  const context: AppLayoutContext = { setOnReset: handleSetReset };

  return (
    <div className="min-h-dvh flex flex-col px-3 pb-5 sm:pb-20 sm:px-8">
      <div className="mx-auto w-full max-w-4xl flex flex-col flex-1 min-h-0">

        <div className="app-header -mx-3 px-3 sm:-mx-8 sm:px-8 flex-shrink-0">
          <div className="app-header-row">
            <h1 className="text-2xl font-bold tracking-tight sm:text-3xl" style={{ color: 'var(--text-primary)' }}>
              seen<span style={{ color: 'var(--accent)' }}>.</span>
              {isAi && <span style={{ color: 'var(--text-secondary)', fontWeight: 500 }}> assistant</span>}
            </h1>
            {isAi && (
              <div className="flex items-center gap-1">
                <button type="button" onClick={() => navigate('/')}
                  className="rounded-md px-2 py-1 transition-colors hover:opacity-80"
                  style={{ color: 'var(--text-secondary)' }} title="退出">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
                    <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
                  </svg>
                </button>
                <button type="button" onClick={() => onReset?.()}
                  className="rounded-md px-2 py-1 transition-colors hover:opacity-80"
                  style={{ color: 'var(--text-secondary)' }} title="重置">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
                    <polyline points="1 4 1 10 7 10" /><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10" />
                  </svg>
                </button>
              </div>
            )}
          </div>
        </div>

        <Outlet context={context} />
      </div>
    </div>
  );
}
