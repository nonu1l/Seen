import { useEffect, useRef } from 'react';
import { useOutletContext } from 'react-router-dom';
import ReactMarkdown from 'react-markdown';
import { Masonry } from '@tuturuuu/masonry';
import remarkGfm from 'remark-gfm';
import { useAiMode } from '../hooks/useAiMode';
import type { AppLayoutContext } from '../components/AppLayout';
import { AiCard } from '../components/AiCard';
import { AiInput } from '../components/AiInput';

export default function AiPage() {
  const { registerReset } = useOutletContext<AppLayoutContext>();
  const ai = useAiMode();

  const bottomRef = useRef<HTMLDivElement>(null);

  // 向 AppLayout 注册 reset 回调
  useEffect(() => {
    registerReset(ai.reset);
    return () => registerReset(null);
  }, [ai.reset, registerReset]);

  // 自动滚动到底部（等 DOM 布局稳定后再滚）
  useEffect(() => {
    const el = bottomRef.current;
    if (!el) return;
    const timer = setTimeout(() => {
      el.scrollIntoView({ behavior: 'smooth', block: 'end' });
    }, 200);
    return () => clearTimeout(timer);
  }, [ai.messages, ai.runStatuses]);

  return (
    <div className="flex flex-col flex-1 min-h-0">

      {/* 动画内容区 — 消息 + 输入 */}
      <div className="ai-content">

        <div className="flex-1 overflow-y-auto py-4 pb-24 space-y-4" style={{ scrollBehavior: 'smooth' }}>
          {ai.messages.map(msg => {
            const msgCards = msg.role === 'assistant'
              ? ai.cards.filter(c => c.messageId === msg.id)
              : [];
            const isUser = msg.role === 'user';
            return (
              <div key={msg.id}>
                <div className={`text-[13px] leading-relaxed ${isUser ? 'text-right' : ''}`}>
                  <span
                    className={`inline-block max-w-[85%] rounded-lg px-3 py-2 text-left ${isUser ? '' : 'ai-prose'}`}
                    style={{
                      background: isUser ? 'var(--accent)' : 'var(--bg-card)',
                      color: isUser ? 'var(--on-accent)' : 'var(--text-primary)',
                    }}
                  >
                    {isUser
                      ? msg.content
                      : <ReactMarkdown remarkPlugins={[remarkGfm]}>{msg.content}</ReactMarkdown>}
                  </span>
                </div>
                {msgCards.length > 0 && (
                  <div className="mt-2">
                    <Masonry columns={3} gap={8} breakpoints={{ 0: 1, 768: 2, 1024: 3 }} strategy="count">
                      {msgCards.map(card => (
                        <AiCard key={card.id} card={card} onSave={ai.saveCard} onUndo={ai.undoCard} />
                      ))}
                    </Masonry>
                  </div>
                )}
              </div>
            );
          })}

          {ai.loading && (
            <div className="text-[13px] space-y-2">
              {ai.runStatuses.length > 0 && (
                <div className="inline-flex max-w-[85%] flex-col gap-1 rounded-lg px-3 py-2"
                  style={{ background: 'var(--bg-card)', color: 'var(--text-secondary)' }}>
                  {ai.runStatuses.map((status, index) => (
                    <span key={`${status}-${index}`} className="text-xs">{status}</span>
                  ))}
                </div>
              )}
              <span className="inline-block rounded-lg" style={{ background: 'var(--bg-card)' }}>
                <span className="ai-typing">
                  <span /><span /><span />
                </span>
              </span>
            </div>
          )}
          <div ref={bottomRef} className="h-20" />
        </div>

      </div>

      {/* 悬浮输入框 — 固定在视口底部，磨砂玻璃 */}
      <div className="fixed bottom-0 left-0 right-0 z-20 px-3 pb-3 pt-2"
        style={{
          background: 'var(--dock-bg)',
          backdropFilter: 'blur(12px)',
          WebkitBackdropFilter: 'blur(12px)',
        }}>
        <div className="mx-auto w-full max-w-7xl">
          <AiInput onSend={ai.send} onStop={ai.stop} loading={ai.loading} />
        </div>
      </div>
    </div>
  );
}
