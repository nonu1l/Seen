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

export interface AppConfigDTO {
  aiEnabled: boolean;
  bangumiProxy: string;
}

export interface CastMember {
  id: number | null;
  name: string;
  character: string | null;
  profile: string | null;
  actorId: number | null;
}

export interface BangumiSubjectSummaryDTO {
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

export interface WorkListItemResponse {
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
  recordsCount: number | null;
  latestRecordAt: string | null;
}

export interface WorkSearchResponse {
  local: WorkListItemResponse[];
  works: BangumiSubjectSummaryDTO[];
}

export interface WorkDetailResponse {
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
  watchedCount: number | null;
}

export interface MarkRequest {
  id: string;
  platform: string;
  status: Status;
  meta?: BangumiSubjectSummaryDTO;
}

// ── Conversation ──

export interface ConversationMessageDTO {
  id: number;
  role: 'user' | 'assistant';
  content: string;
  createdAt: string;
}

export interface ConversationCardDTO {
  id: number;
  messageId: number;
  requestId: string | null;
  subjectId: number;
  actionType: CardActionType | null;
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
  createdAt: string | null;
  updatedAt: string | null;
}

export type CardState = 'PENDING' | 'SAVED' | 'EDITABLE' | 'UNMARKED' | 'RESTORED';
export type CardActionType = 'PRESENT' | 'MARK' | 'UPDATE' | 'UNMARK' | 'MANUAL_SAVE';

export type AiStreamEventType = 'user_saved' | 'status' | 'assistant_saved' | 'cards' | 'done' | 'error';

export interface AiStreamEventDTO {
  type: AiStreamEventType;
  messageId: number | null;
  content: string | null;
  createdAt: string | null;
  cards: ConversationCardDTO[] | null;
}

export interface ConversationRunStateDTO {
  active: boolean;
  userMessageId: number | null;
  assistantMessageId: number | null;
  assistantContent: string;
  statuses: string[];
  startedAt: string | null;
  updatedAt: string | null;
  error: string | null;
}

export interface ConversationStateDTO {
  sessionId: number;
  messages: ConversationMessageDTO[];
  cards: ConversationCardDTO[];
  activeRun: ConversationRunStateDTO;
}

// ── Settings ──

export interface SettingsDTO {
  aiEnabled: boolean;
  tokenUsageEnabled: boolean;
  aiMemory: AiMemorySettings;
  aiProfile: AiProviderSettingDTO;
  sources: SourceSettings;
}

export interface AiMemorySettings {
  enabled: boolean;
  autoUpdateEnabled?: boolean;
}

export interface AiMemoryDTO {
  exists: boolean;
  version: number | null;
  summary: string | null;
  likesJson: string | null;
  dislikesJson: string | null;
  recentShiftJson: string | null;
  recommendationRulesJson: string | null;
  sourceHash: string | null;
  updatedAt: string | null;
}

export interface AdminOverviewDTO {
  totalTokens: number;
  cacheBytes: number;
}

export interface AiProviderSettingDTO {
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
}

export interface SourceSettings {
  searchProvider: 'serper' | 'tavily';
  serperApiKeySet: boolean;
  serperApiKey: string;
  tavilyApiKeySet: boolean;
  tavilyApiKey: string;
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
