import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { AlertTriangle, X } from 'lucide-react';
import type { ReactNode } from 'react';

type ConfirmVariant = 'default' | 'danger';

interface ConfirmOptions {
  title: string;
  message?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  variant?: ConfirmVariant;
}

interface ConfirmRequest extends Required<ConfirmOptions> {
  resolve: (confirmed: boolean) => void;
}

interface ConfirmContextValue {
  confirm: (options: ConfirmOptions) => Promise<boolean>;
}

const ConfirmContext = createContext<ConfirmContextValue | null>(null);

/** 提供全局应用内确认框，替代浏览器原生 confirm 弹窗。 */
export function ConfirmProvider({ children }: { children: ReactNode }) {
  const [request, setRequest] = useState<ConfirmRequest | null>(null);
  const cancelButtonRef = useRef<HTMLButtonElement>(null);

  const close = useCallback((confirmed: boolean) => {
    setRequest(current => {
      current?.resolve(confirmed);
      return null;
    });
  }, []);

  /** 打开确认框，并在用户确认或取消后返回布尔结果。 */
  const confirm = useCallback((options: ConfirmOptions) => new Promise<boolean>(resolve => {
    setRequest({
      title: options.title,
      message: options.message ?? '',
      confirmLabel: options.confirmLabel ?? '确认',
      cancelLabel: options.cancelLabel ?? '取消',
      variant: options.variant ?? 'default',
      resolve,
    });
  }), []);

  useEffect(() => {
    if (!request) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    cancelButtonRef.current?.focus();
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') close(false);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener('keydown', onKeyDown);
    };
  }, [close, request]);

  const value = useMemo<ConfirmContextValue>(() => ({ confirm }), [confirm]);

  return (
    <ConfirmContext.Provider value={value}>
      {children}
      {request && (
        <div className="confirm-backdrop" onMouseDown={() => close(false)}>
          <div
            className={`confirm-dialog is-${request.variant}`}
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="confirm-title"
            aria-describedby={request.message ? 'confirm-message' : undefined}
            onMouseDown={event => event.stopPropagation()}
          >
            <div className="confirm-head">
              <span className="confirm-icon" aria-hidden="true">
                <AlertTriangle size={18} strokeWidth={2} />
              </span>
              <button type="button" className="confirm-close" aria-label="关闭确认框" onClick={() => close(false)}>
                <X size={16} strokeWidth={2} />
              </button>
            </div>
            <div className="confirm-body">
              <h3 id="confirm-title">{request.title}</h3>
              {request.message && <p id="confirm-message">{request.message}</p>}
            </div>
            <div className="confirm-actions">
              <button type="button" className="btn-ghost" ref={cancelButtonRef} onClick={() => close(false)}>
                {request.cancelLabel}
              </button>
              <button type="button" className="btn-primary confirm-primary" onClick={() => close(true)}>
                {request.confirmLabel}
              </button>
            </div>
          </div>
        </div>
      )}
    </ConfirmContext.Provider>
  );
}

/** 获取全局确认框调用函数。 */
export function useConfirm() {
  const context = useContext(ConfirmContext);
  if (!context) {
    throw new Error('useConfirm must be used within ConfirmProvider');
  }
  return context.confirm;
}
