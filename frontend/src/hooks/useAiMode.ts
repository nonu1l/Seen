import { useState, useEffect, useCallback, useRef } from 'react';
import { api } from '../api/client';
import type { ConversationMessageVO, ConversationCardVO } from '../api/types';

export function useAiMode() {
  const [isOpen, setIsOpen] = useState(false);
  const [sessionId, setSessionId] = useState<number | null>(null);
  const [messages, setMessages] = useState<ConversationMessageVO[]>([]);
  const [cards, setCards] = useState<ConversationCardVO[]>([]);
  const [loading, setLoading] = useState(false);
  const restoredRef = useRef(false);

  const restoreState = useCallback(async () => {
    try {
      const state = await api.getConversationState();
      if (state.messages.length > 0 || state.cards.length > 0) {
        setSessionId(state.sessionId);
        setMessages(state.messages);
        setCards(state.cards);
        return true;
      }
    } catch (e) {
      console.warn('Failed to restore conversation state:', e);
    }
    return false;
  }, []);

  useEffect(() => {
    if (restoredRef.current) return;
    restoredRef.current = true;
    restoreState().then(hasHistory => {
      if (hasHistory) setIsOpen(true);
    });
  }, [restoreState]);

  // ── 发送消息 ──

  const send = useCallback(async (input: string) => {
    if (!input.trim() || loading) return;
    const now = new Date().toISOString();

    setMessages(prev => [...prev, {
      id: Date.now(), role: 'user', content: input.trim(), createdAt: now,
    }]);
    setLoading(true);

    try {
      const res = await api.sendMessage(input.trim());

      setMessages(prev => [...prev, {
        id: res.messageId, role: 'assistant', content: res.replyText, createdAt: now,
      }]);

      if (res.cards.length > 0) {
        setCards(prev => [...prev, ...res.cards]);
      }
    } catch (e) {
      console.error('Send message failed:', e);
      setMessages(prev => [...prev, {
        id: Date.now() + 1, role: 'assistant', content: '抱歉，请求失败，请重试。', createdAt: now,
      }]);
    } finally {
      setLoading(false);
    }
  }, [loading]);

  // ── 保存 / 撤销 ──

  const saveCard = useCallback(async (cardId: number, rating?: number | null, review?: string | null, status?: string | null) => {
    try {
      const result = await api.saveCard(cardId, rating ?? undefined, review ?? undefined, status ?? undefined);
      setCards(prev => prev.map(c => c.id === cardId ? result : c));
    } catch (e) {
      console.error('Save card failed:', e);
    }
  }, []);

  const undoCard = useCallback(async (cardId: number) => {
    try {
      const result = await api.undoCard(cardId);
      setCards(prev => prev.map(c => c.id === cardId ? result : c));
    } catch (e) {
      console.error('Undo card failed:', e);
    }
  }, []);

  const reset = useCallback(async () => {
    try { await api.resetConversation(); } catch { /* ignore */ }
    setMessages([]);
    setCards([]);
  }, []);

  return {
    isOpen, setIsOpen,
    sessionId, messages, cards, loading,
    send, saveCard, undoCard, reset,
  };
}
