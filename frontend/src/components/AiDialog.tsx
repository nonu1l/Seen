import { useRef, useEffect } from 'react';
import { AiCard } from './AiCard';
import { AiInput } from './AiInput';
import type { ConversationMessageVO, ConversationCardVO } from '../api/types';

interface AiModeState {
  isOpen: boolean;
  setIsOpen: (v: boolean) => void;
  messages: ConversationMessageVO[];
  cards: ConversationCardVO[];
  loading: boolean;
  send: (input: string) => void;
  saveCard: (id: number, rating: number | null, review: string | null, status: string | null) => void;
  undoCard: (id: number) => void;
  reset: () => void;
}

export function AiDialog({ ai }: { ai: AiModeState }) {
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [ai.messages, ai.cards]);

  if (!ai.isOpen) return null;

  return (
    <>
      {/* 移动端遮罩 */}
      <div
        className="fixed inset-0 z-40 bg-black/30 sm:hidden"
        onClick={() => ai.setIsOpen(false)}
      />

      <div
        className="fixed z-50 flex flex-col overflow-hidden shadow-2xl
                   max-sm:inset-x-0 max-sm:bottom-0 max-sm:top-1/3 max-sm:rounded-t-xl
                   sm:bottom-6 sm:right-6 sm:w-[420px] sm:max-h-[70vh] sm:rounded-xl"
        style={{ background: 'var(--bg-page, #0f0f0f)', border: '1px solid var(--border)' }}
      >
        {/* Header */}
        <div
          className="flex flex-shrink-0 items-center justify-between px-4 py-2.5"
          style={{ borderBottom: '1px solid var(--border)' }}
        >
          <h2 className="text-lg font-bold tracking-tight" style={{ color: 'var(--text-primary)' }}>
            seen<span style={{ color: 'var(--accent)' }}>.</span>{' '}
            <span style={{ color: 'var(--text-primary)' }}>assistant</span>
          </h2>
          <div className="flex items-center gap-1">
            <button type="button" onClick={() => ai.setIsOpen(false)}
              className="rounded-md px-1.5 py-1 transition-colors hover:opacity-80"
              style={{ color: 'var(--text-muted)' }} title="收起">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
                <polyline points="6 9 12 15 18 9" />
              </svg>
            </button>
            <button type="button" onClick={() => ai.reset()}
              className="rounded-md px-1.5 py-1 transition-colors hover:opacity-80"
              style={{ color: 'var(--text-muted)' }} title="重置">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
                <polyline points="1 4 1 10 7 10" /><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10" />
              </svg>
            </button>
          </div>
        </div>

        {/* Scrollable content */}
        <div ref={scrollRef} className="flex-1 overflow-y-auto px-4 py-3 space-y-4" style={{ scrollBehavior: 'smooth' }}>
          {ai.messages.map(msg => {
            const msgCards = msg.role === 'assistant'
              ? ai.cards.filter(c => c.messageId === msg.id)
              : [];
            return (
              <div key={msg.id}>
                <div className={`text-[13px] leading-relaxed ${msg.role === 'user' ? 'text-right' : ''}`}>
                  <span
                    className={`inline-block max-w-[85%] rounded-lg px-3 py-2 text-left`}
                    style={{
                      background: msg.role === 'user' ? 'var(--accent)' : 'var(--bg-card)',
                      color: msg.role === 'user' ? '#fff' : 'var(--text-primary)',
                    }}
                  >
                    {msg.content}
                  </span>
                </div>
                {msgCards.length > 0 && (
                  <div className="mt-2 space-y-2">
                    {msgCards.map(card => (
                      <AiCard key={card.id} card={card} onSave={ai.saveCard} onUndo={ai.undoCard} />
                    ))}
                  </div>
                )}
              </div>
            );
          })}
        </div>

        {/* 底部：输入 */}
        <div className="flex-shrink-0">
          <AiInput onSend={ai.send} loading={ai.loading} />
        </div>
      </div>
    </>
  );
}
