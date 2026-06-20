import { useState, useEffect, useCallback, useRef } from 'react';
import { api, ApiError } from '../api/client';
import type { ConversationMessageDTO, ConversationCardDTO, AiStreamEventDTO, ConversationStateDTO } from '../api/types';

export function useAiMode() {
  const [isOpen, setIsOpen] = useState(false);
  const [sessionId, setSessionId] = useState<number | null>(null);
  const [messages, setMessages] = useState<ConversationMessageDTO[]>([]);
  const [cards, setCards] = useState<ConversationCardDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [runStatuses, setRunStatuses] = useState<string[]>([]);
  const [recoveringRun, setRecoveringRun] = useState(false);
  const restoredRef = useRef(false);

  const applyConversationState = useCallback((state: ConversationStateDTO) => {
    const activeRun = state.activeRun;
    const nextMessages = [...state.messages];
    setSessionId(state.sessionId);
    setCards(state.cards);

    if (activeRun?.active) {
      setMessages(nextMessages);
      setRunStatuses(activeRun.statuses ?? []);
      setLoading(true);
      setRecoveringRun(true);
      return;
    }

    setMessages(nextMessages);
    setRunStatuses([]);
    setLoading(false);
    setRecoveringRun(false);
  }, []);

  const restoreState = useCallback(async () => {
    try {
      const state = await api.getConversationState();
      if (state.messages.length > 0 || state.cards.length > 0 || state.activeRun?.active) {
        applyConversationState(state);
        return true;
      }
    } catch (e) {
      console.warn('Failed to restore conversation state:', e);
    }
    return false;
  }, [applyConversationState]);

  useEffect(() => {
    if (restoredRef.current) return;
    restoredRef.current = true;
    restoreState().then(hasHistory => {
      if (hasHistory) setIsOpen(true);
    });
  }, [restoreState]);

  useEffect(() => {
    if (!recoveringRun) return;
    let cancelled = false;
    const timer = window.setInterval(async () => {
      try {
        const state = await api.getConversationState();
        if (!cancelled) {
          applyConversationState(state);
        }
      } catch (e) {
        console.warn('Failed to refresh active AI run:', e);
      }
    }, 1000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [recoveringRun, applyConversationState]);

  // ── 发送消息 ──

  const send = useCallback(async (input: string) => {
    if (!input.trim() || loading) return;
    const now = new Date().toISOString();
    const text = input.trim();
    const tempUserId = -Date.now();
    let assistantMessageId: number | null = null;
    let restoredRunState = false;

    setMessages(prev => [...prev, {
      id: tempUserId, role: 'user', content: text, createdAt: now,
    }]);
    setRunStatuses([]);
    setRecoveringRun(false);
    setLoading(true);

    try {
      const handleStreamEvent = (event: AiStreamEventDTO) => {
        switch (event.type) {
          case 'user_saved':
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
          case 'assistant_saved': {
            const nextAssistantId = event.messageId ?? assistantMessageId ?? tempUserId - 1;
            const oldAssistantId = assistantMessageId;
            assistantMessageId = nextAssistantId;
            setMessages(prev => {
              const exists = oldAssistantId != null && prev.some(msg => msg.id === oldAssistantId);
              if (exists) {
                return prev.map(msg => msg.id === oldAssistantId
                ? {
                  ...msg,
                  id: nextAssistantId,
                  content: event.content ?? msg.content,
                  contentBlocks: event.contentBlocks ?? msg.contentBlocks,
                  createdAt: event.createdAt ?? msg.createdAt,
                }
                : msg);
              }
              return [...prev, {
                id: nextAssistantId,
                role: 'assistant',
                content: event.content ?? '',
                contentBlocks: event.contentBlocks ?? null,
                createdAt: event.createdAt ?? new Date().toISOString(),
              }];
            });
            break;
          }
          case 'cards':
            if (event.cards && event.cards.length > 0) {
              setCards(prev => [...prev, ...event.cards!]);
            }
            break;
          case 'error':
            if (event.content && assistantMessageId == null) {
              assistantMessageId = tempUserId - 1;
              setMessages(prev => [...prev, {
                id: assistantMessageId!,
                role: 'assistant',
                content: event.content!,
                contentBlocks: null,
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
      if (e instanceof ApiError && e.status === 409) {
        setMessages(prev => prev.filter(msg => msg.id !== tempUserId));
        restoredRunState = await restoreState();
        if (!restoredRunState) {
          setRunStatuses(['已有 AI 任务正在运行']);
          setLoading(true);
          setRecoveringRun(true);
          restoredRunState = true;
        }
        return;
      }
      const errorText = '抱歉，请求失败，请重试。';
      if (assistantMessageId != null) {
        setMessages(prev => prev.map(msg => msg.id === assistantMessageId
          ? { ...msg, content: msg.content || errorText }
          : msg));
      } else {
        setMessages(prev => [...prev, {
          id: tempUserId - 1, role: 'assistant', content: errorText, contentBlocks: null, createdAt: now,
        }]);
      }
    } finally {
      if (!restoredRunState) {
        setLoading(false);
        setRunStatuses([]);
        setRecoveringRun(false);
      }
    }
  }, [loading, restoreState]);

  const stop = useCallback(async () => {
    try {
      const state = await api.stopConversation();
      applyConversationState(state);
    } catch (e) {
      console.error('Stop AI run failed:', e);
      await restoreState();
    }
  }, [applyConversationState, restoreState]);

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
    setRecoveringRun(false);
    setLoading(false);
  }, []);

  return {
    isOpen, setIsOpen,
    sessionId, messages, cards, loading, runStatuses,
    send, stop, saveCard, undoCard, reset,
  };
}
