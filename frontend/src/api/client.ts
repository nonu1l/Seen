import type {
  WorkListItemDTO,
  SearchDTO,
  WorkDetailDTO,
  MarkRequest,
  DictResponse,
  AppConfigDTO,
  ConversationStateDTO,
  AiChatDTO,
  AiStreamEventDTO,
  ConversationCardDTO,
  SettingsDTO,
  UpdateSettingsRequest,
  SettingsTestResult,
  AiProviderSettingDTO,
  AiProviderSettingRequest,
  AiMemoryDTO,
  AdminOverviewDTO,
} from './types';

const BASE = '/api';

export interface AiStreamHandlers {
  onEvent?: (event: AiStreamEventDTO) => void;
}

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

async function readSseStream(res: Response, handlers?: AiStreamHandlers) {
  if (!res.body) throw new Error('浏览器不支持流式响应');
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    buffer = consumeSseBuffer(buffer, handlers);
  }
  buffer += decoder.decode();
  consumeSseBuffer(buffer, handlers, true);
}

function consumeSseBuffer(buffer: string, handlers?: AiStreamHandlers, flush = false) {
  let normalized = buffer.replace(/\r\n/g, '\n');
  let boundary = normalized.indexOf('\n\n');
  while (boundary >= 0) {
    const block = normalized.slice(0, boundary);
    normalized = normalized.slice(boundary + 2);
    handleSseBlock(block, handlers);
    boundary = normalized.indexOf('\n\n');
  }
  if (flush && normalized.trim()) {
    handleSseBlock(normalized, handlers);
    return '';
  }
  return normalized;
}

function handleSseBlock(block: string, handlers?: AiStreamHandlers) {
  const data = block.split('\n')
    .filter(line => line.startsWith('data:'))
    .map(line => line.slice(5).trimStart())
    .join('\n');
  if (!data.trim()) return;
  const event = JSON.parse(data) as AiStreamEventDTO;
  handlers?.onEvent?.(event);
}

export const api = {
  listWorks: () =>
    request<WorkListItemDTO[]>('/works/list', { method: 'POST' }),

  searchAll: (q: string) =>
    request<SearchDTO>('/works/search', { method: 'POST', body: JSON.stringify({ q }) }),

  getDetail: (id: string, platform: string) =>
    request<WorkDetailDTO>('/works/details', { method: 'POST', body: JSON.stringify({ id, platform }) }),

  mark: (req: MarkRequest) =>
    request<WorkListItemDTO>('/works/mark', { method: 'POST', body: JSON.stringify(req) }),

  unmark: (workId: number) =>
    request<{ ok: boolean }>('/works/unmark', { method: 'POST', body: JSON.stringify({ workId }) }),

  updateReview: (workId: number, rating: number | null, review: string | null) =>
    request<WorkListItemDTO>('/works/update-review', { method: 'POST', body: JSON.stringify({ workId, rating, review }) }),

  getDict: () =>
    request<DictResponse>('/works/dict'),

  getCharacterNames: (ids: number[]) =>
    request<Record<number, string>>('/works/character-names', { method: 'POST', body: JSON.stringify({ ids }) }),

  getActorNames: (ids: number[]) =>
    request<Record<number, string>>('/works/actor-names', { method: 'POST', body: JSON.stringify({ ids }) }),

  // ── Conversation ──

  getConversationState: () =>
    request<ConversationStateDTO>('/conversation/state'),

  sendMessage: (userInput: string) =>
    request<AiChatDTO>('/conversation/send', { method: 'POST', body: JSON.stringify({ userInput }) }),

  sendMessageStream: async (userInput: string, handlers?: AiStreamHandlers) => {
    const res = await fetch(`${BASE}/conversation/send-stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userInput }),
    });
    if (!res.ok) {
      throw new Error(await errorMessage(res));
    }
    await readSseStream(res, handlers);
  },

  saveCard: (cardId: number, rating?: number | null, review?: string | null, status?: string | null) =>
    request<ConversationCardDTO>(`/conversation/cards/${cardId}/save`, {
      method: 'POST',
      body: JSON.stringify({ rating, review, status }),
    }),

  undoCard: (cardId: number) =>
    request<ConversationCardDTO>(`/conversation/cards/${cardId}/undo`, { method: 'POST' }),

  resetConversation: () =>
    request<{ ok: boolean }>('/conversation/reset', { method: 'POST' }),

  getAppConfig: () =>
    request<AppConfigDTO>('/app-config'),

  // ── Settings ──

  getSettings: () =>
    request<SettingsDTO>('/settings'),

  updateSettings: (req: UpdateSettingsRequest) =>
    request<SettingsDTO>('/settings', { method: 'PUT', body: JSON.stringify(req) }),

  updateAiProfile: (req: AiProviderSettingRequest) =>
    request<AiProviderSettingDTO>('/settings/ai-profile', { method: 'PUT', body: JSON.stringify(req) }),

  getAiMemory: () =>
    request<AiMemoryDTO>('/admin/ai-memory'),

  rebuildAiMemory: () =>
    request<AiMemoryDTO>('/admin/ai-memory/rebuild', { method: 'POST' }),

  getAdminOverview: () =>
    request<AdminOverviewDTO>('/admin/overview'),

  clearRequestCache: () =>
    request<AdminOverviewDTO>('/admin/request-cache/clear', { method: 'POST' }),

  resetTokenUsage: () =>
    request<AdminOverviewDTO>('/admin/token-usage/reset', { method: 'POST' }),

  testAiProfile: (req: Record<string, unknown>) =>
    request<SettingsTestResult>('/settings/ai-profile/test', { method: 'POST', body: JSON.stringify(req) }),

  testSearchSettings: (req: Record<string, unknown>) =>
    request<SettingsTestResult>('/settings/test-search', { method: 'POST', body: JSON.stringify(req) }),

  testBangumiSettings: (req: Record<string, unknown>) =>
    request<SettingsTestResult>('/settings/test-bangumi', { method: 'POST', body: JSON.stringify(req) }),
};
