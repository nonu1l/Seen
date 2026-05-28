import { useEffect, useRef } from 'react';
import { useOutletContext } from 'react-router-dom';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { useAiMode } from '../hooks/useAiMode';
import type { AppLayoutContext } from '../components/AppLayout';
import { AiCard } from '../components/AiCard';
import { AiInput } from '../components/AiInput';

export default function AiPage() {
  const { setOnReset } = useOutletContext<AppLayoutContext>();
  const ai = useAiMode();

  const bottomRef = useRef<HTMLDivElement>(null);

  // 向 AppLayout 注册 reset 回调
  useEffect(() => {
    setOnReset(() => ai.reset);
    return () => setOnReset(null);
  }, [ai.reset, setOnReset]);

  // 自动滚动到底部（等 DOM 布局稳定后再滚）
  useEffect(() => {
    const el = bottomRef.current;
    if (!el) return;
    // 先用 instant 滚到底，再 smooth 微调，避免动画期间位置不准
    el.scrollIntoView({ behavior: 'instant', block: 'end' });
    requestAnimationFrame(() => {
      el.scrollIntoView({ behavior: 'smooth', block: 'end' });
    });
  }, [ai.messages, ai.loading]);

  return (
    <div className="flex flex-col flex-1 min-h-0">

      {/* 动画内容区 — 消息 + 输入 */}
      <div className="ai-content">

        <div className="flex-1 overflow-y-auto py-4 space-y-4" style={{ scrollBehavior: 'smooth' }}>
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
                      color: isUser ? '#fff' : 'var(--text-primary)',
                    }}
                  >
                    {isUser
                      ? msg.content
                      : <ReactMarkdown remarkPlugins={[remarkGfm]}>{msg.content}</ReactMarkdown>}
                  </span>
                </div>
                {msgCards.length > 0 && (
                  <div className="mt-2 grid gap-2 grid-cols-1 lg:grid-cols-2">
                    {msgCards.map(card => (
                      <AiCard key={card.id} card={card} onSave={ai.saveCard} onUndo={ai.undoCard} />
                    ))}
                  </div>
                )}
              </div>
            );
          })}

          {ai.loading && (
            <div className="text-[13px]">
              <span className="inline-block rounded-lg" style={{ background: 'var(--bg-card)' }}>
                <span className="ai-typing">
                  <span /><span /><span />
                </span>
              </span>
            </div>
          )}
          <div ref={bottomRef} />
        </div>

        <div className="flex-shrink-0">
          <AiInput onSend={ai.send} loading={ai.loading} />
        </div>
      </div>
    </div>
  );
}
