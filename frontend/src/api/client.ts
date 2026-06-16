import type {
  WorkListItem,
  SearchResponse,
  WorkDetail,
  MarkRequest,
  DictResponse,
  ConversationState,
  AiChatResponse,
  ConversationCardVO,
  SettingsResponse,
  UpdateSettingsRequest,
  SettingsTestResult,
  AiProviderSetting,
  AiProviderSettingRequest,
  AiMemoryResponse,
} from './types';

const BASE = '/api';

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${url}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options
  });
  if (!res.ok) {
    throw new Error(await errorMessage(res));
  }
  if (res.status === 204) return undefined as T;
  return res.json();
}

async function errorMessage(res: Response) {
  const fallback = `API error: ${res.status} ${res.statusText}`;
  try {
    const text = await res.text();
    if (!text) return fallback;
    const contentType = res.headers.get('content-type') ?? '';
    if (contentType.includes('application/json')) {
      const body = JSON.parse(text) as { error?: unknown; message?: unknown };
      if (typeof body.message === 'string' && body.message.trim()) return body.message;
      if (typeof body.error === 'string' && body.error.trim()) return body.error;
    }
    return text.length > 240 ? text.slice(0, 240) + '...' : text;
  } catch {
    return fallback;
  }
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

  // ── Settings ──

  getSettings: () =>
    request<SettingsResponse>('/settings'),

  updateSettings: (req: UpdateSettingsRequest) =>
    request<SettingsResponse>('/settings', { method: 'PUT', body: JSON.stringify(req) }),

  updateAiProfile: (req: AiProviderSettingRequest) =>
    request<AiProviderSetting>('/settings/ai-profile', { method: 'PUT', body: JSON.stringify(req) }),

  getAiMemory: () =>
    request<AiMemoryResponse>('/admin/ai-memory'),

  rebuildAiMemory: () =>
    request<AiMemoryResponse>('/admin/ai-memory/rebuild', { method: 'POST' }),

  testAiProfile: (req: Record<string, unknown>) =>
    request<SettingsTestResult>('/settings/ai-profile/test', { method: 'POST', body: JSON.stringify(req) }),

  testSearchSettings: (req: Record<string, unknown>) =>
    request<SettingsTestResult>('/settings/test-search', { method: 'POST', body: JSON.stringify(req) }),

  testBangumiSettings: (req: Record<string, unknown>) =>
    request<SettingsTestResult>('/settings/test-bangumi', { method: 'POST', body: JSON.stringify(req) }),
};
