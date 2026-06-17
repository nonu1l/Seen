import { useState, useEffect, useCallback, useRef } from 'react';
import { api } from '../api/client';
import type { ConversationMessageDTO, ConversationCardDTO, AiStreamEventDTO, ConversationStateDTO } from '../api/types';

const ACTIVE_ASSISTANT_ID = -900719925474000;

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
      const assistantId = activeRun.assistantMessageId ?? ACTIVE_ASSISTANT_ID;
      const assistantContent = activeRun.assistantContent || activeRun.error || '';
      if (assistantContent && !nextMessages.some(msg => msg.id === assistantId)) {
        nextMessages.push({
          id: assistantId,
          role: 'assistant',
          content: assistantContent,
          createdAt: activeRun.updatedAt ?? activeRun.startedAt ?? new Date().toISOString(),
        });
      }
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
    const tempAssistantId = tempUserId - 1;
    let currentAssistantId = tempAssistantId;
    let assistantVisible = false;

    setMessages(prev => [...prev, {
      id: tempUserId, role: 'user', content: text, createdAt: now,
    }]);
    setRunStatuses([]);
    setRecoveringRun(false);
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
      const errorText = '抱歉，请求失败，请重试。';
      if (assistantVisible) {
        setMessages(prev => prev.map(msg => msg.id === currentAssistantId
          ? { ...msg, content: msg.content || errorText }
          : msg));
      } else {
        setMessages(prev => [...prev, {
          id: tempAssistantId, role: 'assistant', content: errorText, createdAt: now,
        }]);
      }
    } finally {
      setLoading(false);
      setRunStatuses([]);
      setRecoveringRun(false);
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
    setRecoveringRun(false);
    setLoading(false);
  }, []);

  return {
    isOpen, setIsOpen,
    sessionId, messages, cards, loading, runStatuses,
    send, saveCard, undoCard, reset,
  };
}
