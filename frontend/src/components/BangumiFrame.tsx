import { useEffect, useRef } from 'react';

interface Props {
  subjectId: number | string;
  open: boolean;
  onClose: () => void;
}

export function BangumiFrame({ subjectId, open, onClose }: Props) {
  const overlayRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    if (open) {
      document.body.style.overflow = 'hidden';
      window.addEventListener('keydown', onKey);
    }
    return () => {
      document.body.style.overflow = '';
      window.removeEventListener('keydown', onKey);
    };
  }, [open, onClose]);

  if (!open) return null;

  const proxyUrl = `/api/proxy/bangumi?url=${encodeURIComponent(`https://bgm.tv/subject/${subjectId}`)}`;

  return (
    <div
      ref={overlayRef}
      className="fixed inset-0 z-50 flex items-start justify-center bg-black/75 overflow-y-auto"
      onClick={onClose}
    >
      <div
        className="my-8 anim-in w-full max-w-sm rounded-lg overflow-hidden"
        style={{ background: 'var(--bg-panel)', border: '1px solid var(--border)', height: '85vh' }}
        onClick={e => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-4 py-2" style={{ borderBottom: '1px solid var(--border)' }}>
          <span className="text-sm font-medium" style={{ color: 'var(--text-secondary)' }}>Bangumi</span>
          <button onClick={onClose} className="btn-icon" aria-label="关闭">
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
        <iframe
          src={proxyUrl}
          className="w-full border-0"
          style={{ height: 'calc(100% - 41px)' }}
          title="Bangumi"
        />
      </div>
    </div>
  );
}
