import { useState, useEffect, useCallback, useRef } from 'react';
import { api } from '../api/client';
import type { ConversationMessageVO, ConversationCardVO, AiStreamEvent } from '../api/types';

export function useAiMode() {
  const [isOpen, setIsOpen] = useState(false);
  const [sessionId, setSessionId] = useState<number | null>(null);
  const [messages, setMessages] = useState<ConversationMessageVO[]>([]);
  const [cards, setCards] = useState<ConversationCardVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [runStatuses, setRunStatuses] = useState<string[]>([]);
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
    const text = input.trim();
    const tempUserId = -Date.now();
    const tempAssistantId = tempUserId - 1;
    let currentAssistantId = tempAssistantId;
    let assistantVisible = false;
    let userSaved = false;

    setMessages(prev => [...prev, {
      id: tempUserId, role: 'user', content: text, createdAt: now,
    }]);
    setRunStatuses([]);
    setLoading(true);

    try {
      const ensureAssistant = (createdAt?: string | null) => {
        if (assistantVisible) return;
        assistantVisible = true;
        setMessages(prev => [...prev, {
          id: currentAssistantId,
          role: 'assistant',
          content: '',
          createdAt: createdAt ?? new Date().toISOString(),
        }]);
      };

      const handleStreamEvent = (event: AiStreamEvent) => {
        switch (event.type) {
          case 'user_saved':
            userSaved = true;
            if (event.messageId != null) {
              setMessages(prev => prev.map(msg => msg.id === tempUserId
                ? { ...msg, id: event.messageId!, createdAt: event.createdAt ?? msg.createdAt }
                : msg));
            }
            break;
          case 'status':
            if (event.content) {
              setRunStatuses(prev => prev[prev.length - 1] === event.content
                ? prev
                : [...prev.slice(-5), event.content!]);
            }
            break;
          case 'delta':
            if (event.content) {
              ensureAssistant(event.createdAt);
              setMessages(prev => prev.map(msg => msg.id === currentAssistantId
                ? { ...msg, content: msg.content + event.content }
                : msg));
            }
            break;
          case 'assistant_saved': {
            const oldAssistantId = currentAssistantId;
            const nextAssistantId = event.messageId ?? oldAssistantId;
            currentAssistantId = nextAssistantId;
            if (assistantVisible) {
              setMessages(prev => prev.map(msg => msg.id === oldAssistantId
                ? {
                  ...msg,
                  id: nextAssistantId,
                  content: event.content ?? msg.content,
                  createdAt: event.createdAt ?? msg.createdAt,
                }
                : msg));
            } else {
              assistantVisible = true;
              setMessages(prev => [...prev, {
                id: nextAssistantId,
                role: 'assistant',
                content: event.content ?? '',
                createdAt: event.createdAt ?? new Date().toISOString(),
              }]);
            }
            break;
          }
          case 'cards':
            if (event.cards && event.cards.length > 0) {
              setCards(prev => [...prev, ...event.cards!]);
            }
            break;
          case 'error':
            if (event.content && !assistantVisible) {
              assistantVisible = true;
              setMessages(prev => [...prev, {
                id: currentAssistantId,
                role: 'assistant',
                content: event.content!,
                createdAt: new Date().toISOString(),
              }]);
            }
            break;
          default:
            break;
        }
      };

      await api.sendMessageStream(text, { onEvent: handleStreamEvent });
    } catch (e) {
      console.error('Send message failed:', e);
      if (!userSaved) {
        try {
          const res = await api.sendMessage(text);
          setMessages(prev => [...prev, {
            id: res.messageId, role: 'assistant', content: res.replyText, createdAt: now,
          }]);
          if (res.cards.length > 0) {
            setCards(prev => [...prev, ...res.cards]);
          }
        } catch {
          setMessages(prev => [...prev, {
            id: tempAssistantId, role: 'assistant', content: '抱歉，请求失败，请重试。', createdAt: now,
          }]);
        }
      } else {
        setMessages(prev => [...prev, {
          id: tempAssistantId, role: 'assistant', content: '抱歉，请求失败，请重试。', createdAt: now,
        }]);
      }
    } finally {
      setLoading(false);
      setRunStatuses([]);
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
    setRunStatuses([]);
  }, []);

  return {
    isOpen, setIsOpen,
    sessionId, messages, cards, loading, runStatuses,
    send, saveCard, undoCard, reset,
  };
}
