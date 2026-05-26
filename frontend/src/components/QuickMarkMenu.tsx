import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import type { Status } from '../api/types';

interface Props {
  current: Status | null;
  onSelect: (s: Status) => void;
}

const ITEMS: { s: Status; label: string; dot: string }[] = [
  { s: 'wish', label: '想看', dot: 'dot-amber' },
  { s: 'doing', label: '在看', dot: 'dot-green' },
  { s: 'collect', label: '看过', dot: 'dot-blue' },
  { s: 'on_hold', label: '搁置', dot: 'dot-gray' },
  { s: 'dropped', label: '抛弃', dot: 'dot-gray' },
];

export function QuickMarkMenu({ current, onSelect }: Props) {
  const [open, setOpen] = useState(false);
  const [closing, setClosing] = useState(false);
  const [pos, setPos] = useState({ top: 0, right: 0 });
  const btnRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const closeTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const calcPos = () => {
    if (!btnRef.current) return;
    const r = btnRef.current.getBoundingClientRect();
    setPos({ top: r.bottom + 6, right: window.innerWidth - r.right });
  };

  const close = () => setOpen(false);

  const closeAnimated = () => {
    setClosing(true);
    closeTimerRef.current = setTimeout(() => { setOpen(false); setClosing(false); }, 150);
  };

  useEffect(() => () => { if (closeTimerRef.current) clearTimeout(closeTimerRef.current); }, []);

  useEffect(() => {
    if (!open) return;
    calcPos();
    const onDoc = (e: MouseEvent) => {
      const t = e.target as Node;
      if (btnRef.current?.contains(t) || menuRef.current?.contains(t)) return;
      close();
    };
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') close(); };
    document.addEventListener('mousedown', onDoc);
    document.addEventListener('keydown', onKey);
    window.addEventListener('scroll', closeAnimated, true);
    return () => {
      document.removeEventListener('mousedown', onDoc);
      document.removeEventListener('keydown', onKey);
      window.removeEventListener('scroll', closeAnimated, true);
    };
  }, [open]);

  return (
    <div className="inline-block">
      <button
        ref={btnRef}
        type="button"
        onClick={e => { e.stopPropagation(); setOpen(v => !v); }}
        className="flex items-center gap-1 rounded-lg border px-3 py-1.5 text-[12px] font-medium transition-colors hover:text-[color:var(--text-primary)]"
        style={{ borderColor: 'var(--border)', color: 'var(--text-secondary)' }}
      >
        标记
        <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M6 9l6 6 6-6" />
        </svg>
      </button>

      {open && createPortal(
        <div
          ref={menuRef}
          className={`fixed z-[9999] rounded-lg border py-1 ${closing ? 'anim-out' : 'anim-in'}`}
          style={{
            background: 'var(--bg-panel)',
            borderColor: 'var(--border)',
            minWidth: 112,
            top: pos.top,
            right: pos.right,
          }}
          onClick={e => e.stopPropagation()}
          role="menu"
        >
          {ITEMS.map(({ s, label, dot: dd }) => (
            <button
              key={s}
              type="button"
              role="menuitem"
              className="flex items-center gap-2 w-full px-3 py-2 text-[13px] font-medium transition-colors hover:bg-white/5"
              style={{ color: current === s ? 'var(--accent)' : 'var(--text-secondary)' }}
              onClick={() => { setOpen(false); onSelect(s); }}
            >
              <span className={`dot ${dd}`} />
              {label}
              {current === s && (
                <svg className="ml-auto h-3.5 w-3.5" style={{ color: 'var(--accent)' }} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                </svg>
              )}
            </button>
          ))}
        </div>,
        document.body
      )}
    </div>
  );
}
