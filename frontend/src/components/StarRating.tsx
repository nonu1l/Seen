import { useState } from 'react';

interface Props {
  value: number | null;
  onChange?: (v: number | null) => void;
  readOnly?: boolean;
  size?: number;
  allowClear?: boolean;
  step?: 0.5 | 1;
}

/**
 * 10 分制星空评分。
 * 5 颗星，每颗 2 分；默认 1 分步进，可按需启用 0.5 分步进。
 * value 范围 0-10，null 表示未评分。
 */
export function StarRating({ value, onChange, readOnly = false, size = 24, allowClear = true, step = 1 }: Props) {
  const [hover, setHover] = useState<number | null>(null);
  const display = hover !== null ? hover : (value ?? 0);

  const fromPointer = (starIndex: number, x: number, width: number) => {
    const starStart = (starIndex - 1) * 2;
    const ratio = Math.min(1, Math.max(0, x / width));
    const raw = starStart + ratio * 2;
    return Math.min(10, Math.max(step, Math.ceil(raw / step) * step));
  };

  const click = (next: number) => {
    if (readOnly || !onChange) return;
    onChange(allowClear && value === next ? null : next);
  };

  return (
    <div className="inline-flex gap-0.5" onMouseLeave={() => setHover(null)}
         role={readOnly ? 'img' : 'group'} aria-label={value != null ? `${value} 分` : '未评分'}>
      {[1, 2, 3, 4, 5].map(i => {
        const starStart = (i - 1) * 2;
        const fillRatio = Math.min(1, Math.max(0, (display - starStart) / 2));
        const active = fillRatio > 0;

        return (
          <button key={i} type="button" disabled={readOnly}
            className={`relative inline-flex items-center justify-center transition-colors ${readOnly ? 'cursor-default' : 'cursor-pointer hover:scale-105'}`}
            style={{ width: size, height: size, border: 0, background: 'transparent', padding: 0,
              color: active ? 'var(--amber)' : 'rgba(255,255,255,0.12)' }}
            onClick={e => {
              if (readOnly) return;
              const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
              click(fromPointer(i, e.clientX - rect.left, rect.width));
            }}
            onMouseMove={e => {
              if (readOnly) return;
              const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
              setHover(fromPointer(i, e.clientX - rect.left, rect.width));
            }}>
            <svg width={size} height={size} viewBox="0 0 20 20" fill="none"
                 stroke="currentColor" strokeWidth={1.2} className="block">
              <path d="M10 1.5l2.6 5.3 5.9.9-4.3 4.2 1 5.9L10 15l-5.3 2.8 1-5.9L1.5 7.7l5.9-.9L10 1.5z"
                    strokeLinecap="round" strokeLinejoin="round" />
            </svg>
            {active && (
              <svg width={size} height={size} viewBox="0 0 20 20" fill="currentColor" className="absolute inset-0"
                   style={{ clipPath: `inset(0 ${100 - fillRatio * 100}% 0 0)`, color: 'var(--amber)' }}>
                <path d="M10 1.5l2.6 5.3 5.9.9-4.3 4.2 1 5.9L10 15l-5.3 2.8 1-5.9L1.5 7.7l5.9-.9L10 1.5z"
                      strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            )}
          </button>
        );
      })}
    </div>
  );
}
