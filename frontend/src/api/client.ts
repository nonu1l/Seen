import type { WorkListItem, SearchResponse, WorkDetail, MarkRequest, DictResponse, ConversationState, AiChatResponse, ConversationCardVO } from './types';

const BASE = '/api';

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${url}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options
  });
  if (!res.ok) throw new Error(`API error: ${res.status} ${res.statusText}`);
  if (res.status === 204) return undefined as T;
  return res.json();
}

export const api = {
  listWorks: () =>
    request<WorkListItem[]>('/works/list', { method: 'POST' }),

  searchAll: (q: string) =>
    request<SearchResponse>('/works/search', { method: 'POST', body: JSON.stringify({ q }) }),

  getDetail: (id: string, platform: string) =>
    request<WorkDetail>('/works/details', { method: 'POST', body: JSON.stringify({ id, platform }) }),

  mark: (req: MarkRequest) =>
    request<WorkListItem>('/works/mark', { method: 'POST', body: JSON.stringify(req) }),

  unmark: (workId: number) =>
    request<{ ok: boolean }>('/works/unmark', { method: 'POST', body: JSON.stringify({ workId }) }),

  updateReview: (workId: number, rating: number | null, review: string | null) =>
    request<WorkListItem>('/works/update-review', { method: 'POST', body: JSON.stringify({ workId, rating, review }) }),

  getDict: () =>
    request<DictResponse>('/works/dict'),

  getCharacterNames: (ids: number[]) =>
    request<Record<number, string>>('/works/character-names', { method: 'POST', body: JSON.stringify({ ids }) }),

  getActorNames: (ids: number[]) =>
    request<Record<number, string>>('/works/actor-names', { method: 'POST', body: JSON.stringify({ ids }) }),

  // ── Conversation ──

  getConversationState: () =>
    request<ConversationState>('/conversation/state'),

  sendMessage: (userInput: string) =>
    request<AiChatResponse>('/conversation/send', { method: 'POST', body: JSON.stringify({ userInput }) }),

  saveCard: (cardId: number, rating?: number | null, review?: string | null, status?: string | null) =>
    request<ConversationCardVO>(`/conversation/cards/${cardId}/save`, {
      method: 'POST',
      body: JSON.stringify({ rating, review, status }),
    }),

  undoCard: (cardId: number) =>
    request<ConversationCardVO>(`/conversation/cards/${cardId}/undo`, { method: 'POST' }),

  resetConversation: () =>
    request<{ ok: boolean }>('/conversation/reset', { method: 'POST' }),

  getAppConfig: () =>
    request<{ aiEnabled: boolean }>('/app-config'),
};
