import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';

type ToastVariant = 'success' | 'error' | 'info';

interface ToastOptions {
  variant?: ToastVariant;
  duration?: number;
  actionLabel?: string;
  onAction?: () => void;
}

interface ToastItem {
  id: number;
  message: string;
  variant: ToastVariant;
  actionLabel?: string;
  onAction?: () => void;
}

interface ToastContextValue {
  showToast: (message: string, options?: ToastOptions) => number;
  success: (message: string, options?: Omit<ToastOptions, 'variant'>) => number;
  error: (message: string, options?: Omit<ToastOptions, 'variant'>) => number;
  info: (message: string, options?: Omit<ToastOptions, 'variant'>) => number;
  dismissToast: (id: number) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const nextIdRef = useRef(1);
  const timersRef = useRef<Map<number, ReturnType<typeof setTimeout>>>(new Map());

  const dismissToast = useCallback((id: number) => {
    const timer = timersRef.current.get(id);
    if (timer) {
      clearTimeout(timer);
      timersRef.current.delete(id);
    }
    setToasts(prev => prev.filter(toast => toast.id !== id));
  }, []);

  const showToast = useCallback((message: string, options: ToastOptions = {}) => {
    const id = nextIdRef.current++;
    const toast: ToastItem = {
      id,
      message,
      variant: options.variant ?? 'info',
      actionLabel: options.actionLabel,
      onAction: options.onAction,
    };
    const duration = options.duration ?? 2600;

    setToasts(prev => [...prev, toast].slice(-4));
    if (duration > 0) {
      const timer = setTimeout(() => dismissToast(id), duration);
      timersRef.current.set(id, timer);
    }
    return id;
  }, [dismissToast]);

  const value = useMemo<ToastContextValue>(() => ({
    showToast,
    success: (message, options) => showToast(message, { ...options, variant: 'success' }),
    error: (message, options) => showToast(message, { ...options, variant: 'error' }),
    info: (message, options) => showToast(message, { ...options, variant: 'info' }),
    dismissToast,
  }), [dismissToast, showToast]);

  useEffect(() => () => {
    timersRef.current.forEach(timer => clearTimeout(timer));
    timersRef.current.clear();
  }, []);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="toast-viewport" role="region" aria-label="消息提示">
        {toasts.map(toast => (
          <div key={toast.id} className={`toast-item is-${toast.variant}`} role={toast.variant === 'error' ? 'alert' : 'status'}>
            <span className="toast-message">{toast.message}</span>
            {toast.actionLabel && (
              <button
                type="button"
                className="toast-action"
                onClick={() => {
                  toast.onAction?.();
                  dismissToast(toast.id);
                }}
              >
                {toast.actionLabel}
              </button>
            )}
            <button type="button" className="toast-close" aria-label="关闭提示" onClick={() => dismissToast(toast.id)}>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path d="M6 6l12 12M18 6L6 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
              </svg>
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used within ToastProvider');
  }
  return context;
}
