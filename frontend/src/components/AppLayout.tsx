import { useState, useCallback } from 'react';
import { ArrowLeft, RotateCcw, Settings, X } from 'lucide-react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useConfirm } from './ConfirmProvider';

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
  const confirm = useConfirm();
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

  /** 确认后清空 AI 对话历史，避免用户误触重置按钮。 */
  const handleReset = useCallback(async () => {
    if (!onReset) return;
    const confirmed = await confirm({
      title: '重置 AI 对话？',
      message: '这会清空当前 AI 对话消息、卡片和运行状态。',
      confirmLabel: '重置对话',
      variant: 'danger',
    });
    if (confirmed) onReset();
  }, [confirm, onReset]);

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
                <ArrowLeft size={18} strokeWidth={2} />
              </button>
            ) : (
              <div className="flex items-center gap-1">
                <button type="button" onClick={() => navigate('/settings')}
                  className="btn-icon" title="设置" aria-label="设置">
                  <Settings size={18} strokeWidth={1.8} />
                </button>
                {isAi && (
                  <>
                    <button type="button" onClick={() => navigate('/')}
                      className="btn-icon" title="退出" aria-label="退出">
                      <X size={18} strokeWidth={2} />
                    </button>
                    <button type="button" onClick={handleReset}
                      className="btn-icon" title="重置" aria-label="重置">
                      <RotateCcw size={18} strokeWidth={2} />
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
