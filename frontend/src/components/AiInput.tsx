import { useState, useRef } from 'react';
import { ArrowUp, LoaderCircle } from 'lucide-react';

interface Props {
  onSend: (text: string) => void;
  loading: boolean;
}

export function AiInput({ onSend, loading }: Props) {
  const [value, setValue] = useState('');
  const inputRef = useRef<HTMLTextAreaElement>(null);

  const handleSend = () => {
    if (!value.trim() || loading) return;
    onSend(value);
    setValue('');
    // 发送后重置高度
    if (inputRef.current) inputRef.current.style.height = 'auto';
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleInput = () => {
    const el = inputRef.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = Math.min(el.scrollHeight, 120) + 'px';
  };

  return (
    <div className="flex items-end gap-2 border-t px-4 py-3" style={{ borderColor: 'var(--border)' }}>
      <textarea
        ref={inputRef}
        value={value}
        onChange={e => setValue(e.target.value)}
        onKeyDown={handleKeyDown}
        onInput={handleInput}
        placeholder="描述你看过的作品..."
        rows={1}
        disabled={loading}
        className="ai-textarea flex-1 resize-none rounded-lg px-3 py-2 text-sm transition-colors disabled:opacity-50"
        style={{
          background: 'var(--bg-card)',
          color: 'var(--text-primary)',
          border: '1px solid var(--border)',
        }}
      />
      <button
        type="button"
        onClick={handleSend}
        disabled={loading || !value.trim()}
        className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg transition-colors disabled:opacity-30"
        style={{ background: 'var(--accent)', color: '#fff' }}
      >
        {loading ? (
          <LoaderCircle className="h-4 w-4 animate-spin" strokeWidth={2.5} />
        ) : (
          <ArrowUp size={18} strokeWidth={2.5} />
        )}
      </button>
    </div>
  );
}
