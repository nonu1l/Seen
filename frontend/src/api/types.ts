export type Status = 'wish' | 'doing' | 'collect' | 'on_hold' | 'dropped';

export interface SubjectType {
  code: number;
  label: string;
}

export interface RecordStatus {
  code: string;
  label: string;
  sortOrder: number;
}

export interface DictResponse {
  subjectTypes: SubjectType[];
  recordStatuses: RecordStatus[];
  bangumiProxy: string;
}

export interface CastMember {
  id: number | null;
  name: string;
  character: string | null;
  profile: string | null;
  actorId: number | null;
}

export interface WorkSearchResult {
  id: number;
  platform: string;
  nameCn: string;
  nameOrig: string | null;
  coverUrl: string | null;
  year: string | null;
  tags: string[] | null;
  plot: string | null;
  score: number | null;
  source?: 'local' | 'bangumi';
}

export interface WorkListItem {
  id: number;
  platform: string;
  nameCn: string;
  nameOrig: string | null;
  coverUrl: string | null;
  year: string | null;
  tags: string[] | null;
  plot: string | null;
  score: number | null;
  status: Status | null;
  myRating: number | null;
  myReview: string | null;
  rewatched: boolean;
  recordsCount: number | null;
  latestRecordAt: string | null;
}

export interface SearchResponse {
  local: WorkListItem[];
  works: WorkSearchResult[];
}

export interface WorkDetail {
  id: number | null;
  platform: string;
  nameCn: string;
  nameOrig: string | null;
  coverUrl: string | null;
  year: string | null;
  tags: string[] | null;
  plot: string | null;
  score: number | null;
  regions: string[] | null;
  episodes: number | null;
  seasonsCount: number | null;
  runtime: number | null;
  cast: CastMember[] | null;
  imdbId: string | null;
  status: Status | null;
  myRating: number | null;
  myReview: string | null;
  rewatched: boolean;
  watchedCount: number | null;
}

export interface MarkRequest {
  id: string;
  platform: string;
  status: Status;
  meta?: WorkSearchResult;
}

// ── Conversation ──

export interface ConversationMessageVO {
  id: number;
  role: 'user' | 'assistant';
  content: string;
  createdAt: string;
}

export interface ConversationCardVO {
  id: number;
  messageId: number;
  subjectId: number;
  nameCn: string;
  coverUrl: string | null;
  year: string | null;
  platform: string | null;
  tags: string[] | null;
  plot: string | null;
  rating: number | null;
  score: number | null;
  review: string | null;
  status: Status | null;
  cardState: CardState;
  /** 以下为 AI 新增记录时的历史对比 */
  previousRating: number | null;
  previousReview: string | null;
  previousStatus: Status | null;
}

export type CardState = 'PENDING' | 'SAVED' | 'EXCLUDED' | 'EDITABLE' | 'CONFLICT' | 'UNMARKED' | 'RESTORED';

export interface AiChatResponse {
  messageId: number;
  replyText: string;
  cards: ConversationCardVO[];
}

export interface ConversationState {
  sessionId: number;
  messages: ConversationMessageVO[];
  cards: ConversationCardVO[];
}

// ── Settings ──

export interface SettingsResponse {
  aiEnabled: boolean;
  tokenUsageEnabled: boolean;
  aiProfile: AiProviderSetting;
  sources: SourceSettings;
}

export interface AiProviderSetting {
  baseUrl: string;
  model: string;
  temperature: number;
  apiKeySet: boolean;
  apiKey: string;
}

export interface AiProviderSettingRequest {
  baseUrl: string;
  model: string;
  temperature: number;
  apiKey?: string;
  clearApiKey?: boolean;
}

export interface SourceSettings {
  searchProvider: 'serper' | 'ddg';
  serperApiKeySet: boolean;
  serperApiKey: string;
  bangumiProxy: string;
  detailCastEnabled: boolean;
}

export interface UpdateSettingsRequest {
  settings: Record<string, string | number | boolean>;
}

export interface SettingsTestResult {
  ok: boolean;
  message: string;
  elapsedMs?: number;
  details?: Record<string, unknown>;
}
