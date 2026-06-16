import { useEffect, useMemo, useState } from 'react';
import { api } from '../api/client';
import { useToast } from '../components/ToastProvider';
import { SecretInput } from '../components/settings/SecretInput';
import { SettingsRow } from '../components/settings/SettingsRow';
import { TestResult } from '../components/settings/TestResult';
import { ToggleRow } from '../components/settings/ToggleRow';
import type {
  AiMemoryResponse,
  AiProviderSettingRequest,
  SettingsResponse,
  SettingsTestResult,
} from '../api/types';

type TestKey = 'ai' | 'search' | 'bangumi';

interface EditableAiConfig {
  aiEnabled: boolean;
  tokenUsageEnabled: boolean;
  memoryEnabled: boolean;
  baseUrl: string;
  model: string;
  temperature: number;
  apiKey: string;
  clearApiKey: boolean;
  apiKeySet: boolean;
}

interface SourceValues {
  searchProvider: 'auto' | 'serper' | 'ddg';
  serperApiKey: string;
  serperApiKeySet: boolean;
  bangumiProxy: string;
  detailCastEnabled: boolean;
}

const GROUPS = [
  { key: 'ai' as const, label: 'AI 助手' },
  { key: 'sources' as const, label: '搜索与数据源' },
];

const EMPTY_AI_CONFIG: EditableAiConfig = {
  aiEnabled: true,
  tokenUsageEnabled: true,
  memoryEnabled: true,
  baseUrl: '',
  model: '',
  temperature: 0,
  apiKey: '',
  clearApiKey: false,
  apiKeySet: false,
};

function isSecretUnchanged(current: string, initial: string) {
  return current === initial;
}

function shouldSubmitSecret(current: string, initial: string) {
  const normalized = current.trim();
  return normalized !== '' && normalized !== initial;
}

function toAiConfigDraft(settings: SettingsResponse | null): EditableAiConfig {
  const profile = settings?.aiProfile ?? null;
  if (!profile) return { ...EMPTY_AI_CONFIG };
  return {
    aiEnabled: settings?.aiEnabled ?? true,
    tokenUsageEnabled: settings?.tokenUsageEnabled ?? true,
    memoryEnabled: settings?.aiMemory?.enabled ?? true,
    baseUrl: profile.baseUrl,
    model: profile.model ?? '',
    temperature: Number.isFinite(profile.temperature) ? profile.temperature : 0,
    apiKey: profile.apiKey ?? '',
    clearApiKey: false,
    apiKeySet: profile.apiKeySet,
  };
}

function toSourceValues(settings: SettingsResponse | null): SourceValues {
  return {
    searchProvider: settings?.sources.searchProvider ?? 'auto',
    serperApiKey: settings?.sources.serperApiKey ?? '',
    serperApiKeySet: settings?.sources.serperApiKeySet ?? false,
    bangumiProxy: settings?.sources.bangumiProxy ?? '',
    detailCastEnabled: settings?.sources.detailCastEnabled ?? true,
  };
}

function sameAiConfig(a: EditableAiConfig, b: EditableAiConfig) {
  return a.aiEnabled === b.aiEnabled
    && a.tokenUsageEnabled === b.tokenUsageEnabled
    && a.memoryEnabled === b.memoryEnabled
    && a.baseUrl === b.baseUrl
    && a.model === b.model
    && String(a.temperature) === String(b.temperature)
    && isSecretUnchanged(a.apiKey, b.apiKey)
    && a.clearApiKey === b.clearApiKey;
}

/** 将长期记忆 JSON 字段压平成可展示的短文本列表。 */
function parseMemoryItems(value: string | null) {
  if (!value?.trim()) return [];
  try {
    return normalizeMemoryItems(JSON.parse(value)).filter(Boolean).slice(0, 12);
  } catch {
    return [value.trim()].filter(Boolean);
  }
}

/** 从数组、对象或字符串中提取长期记忆条目，兼容模型输出的小幅格式波动。 */
function normalizeMemoryItems(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value.flatMap(item => normalizeMemoryItems(item));
  }
  if (typeof value === 'string') {
    return value.trim() ? [value.trim()] : [];
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return [String(value)];
  }
  if (value && typeof value === 'object') {
    return Object.values(value as Record<string, unknown>).flatMap(item => normalizeMemoryItems(item));
  }
  return [];
}

/** 格式化后端返回的长期记忆更新时间，解析失败时保留原始文本。 */
function formatMemoryTime(value: string | null) {
  if (!value) return '未知';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('zh-CN', { hour12: false });
}

function sameSources(a: SourceValues, b: SourceValues) {
  return a.searchProvider === b.searchProvider
    && isSecretUnchanged(a.serperApiKey, b.serperApiKey)
    && a.bangumiProxy === b.bangumiProxy
    && a.detailCastEnabled === b.detailCastEnabled;
}

export default function SettingsPage() {
  const toast = useToast();
  const [activeGroup, setActiveGroup] = useState<'ai' | 'sources'>('ai');
  const [aiDraft, setAiDraft] = useState<EditableAiConfig>(EMPTY_AI_CONFIG);
  const [aiInitial, setAiInitial] = useState<EditableAiConfig>(EMPTY_AI_CONFIG);
  const [sourceValues, setSourceValues] = useState<SourceValues>(() => toSourceValues(null));
  const [sourceInitial, setSourceInitial] = useState<SourceValues>(() => toSourceValues(null));
  const [aiMemory, setAiMemory] = useState<AiMemoryResponse | null>(null);
  const [showAiSecret, setShowAiSecret] = useState(false);
  const [showSerperSecret, setShowSerperSecret] = useState(false);
  const [loading, setLoading] = useState(true);
  const [memoryLoading, setMemoryLoading] = useState(true);
  const [memoryRebuilding, setMemoryRebuilding] = useState(false);
  const [memoryDetailsOpen, setMemoryDetailsOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testLoading, setTestLoading] = useState<TestKey | null>(null);
  const [testResults, setTestResults] = useState<Record<TestKey, SettingsTestResult | null>>({
    ai: null,
    search: null,
    bangumi: null,
  });

  const aiDirty = useMemo(() => !sameAiConfig(aiDraft, aiInitial), [aiDraft, aiInitial]);
  const sourceDirty = useMemo(() => !sameSources(sourceValues, sourceInitial), [sourceValues, sourceInitial]);
  const dirty = activeGroup === 'ai' ? aiDirty : sourceDirty;

  const applySettings = (next: SettingsResponse) => {
    const nextSources = toSourceValues(next);
    setSourceValues(nextSources);
    setSourceInitial(nextSources);

    const nextDraft = toAiConfigDraft(next);
    setAiDraft(nextDraft);
    setAiInitial(nextDraft);
    setShowAiSecret(false);
    setShowSerperSecret(false);
  };

  const reload = async () => {
    const next = await api.getSettings();
    applySettings(next);
    return next;
  };

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setMemoryLoading(true);
    api.getSettings()
      .then(next => {
        if (!cancelled) {
          applySettings(next);
        }
      })
      .catch(err => {
        if (!cancelled) {
          toast.error(err instanceof Error ? err.message : '读取设置失败', {
            actionLabel: '重新加载',
            duration: 0,
            onAction: () => window.location.reload(),
          });
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    api.getAiMemory()
      .then(next => {
        if (!cancelled) {
          setAiMemory(next);
        }
      })
      .catch(err => {
        if (!cancelled) {
          toast.error(err instanceof Error ? err.message : '读取长期记忆失败');
        }
      })
      .finally(() => {
        if (!cancelled) setMemoryLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [toast]);

  useEffect(() => {
    if (!aiDirty && !sourceDirty) return;
    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  }, [aiDirty, sourceDirty]);

  const setAiConfig = <K extends keyof EditableAiConfig>(key: K, value: EditableAiConfig[K]) => {
    setAiDraft(prev => ({ ...prev, [key]: value }));
  };

  const setSource = <K extends keyof SourceValues>(key: K, value: SourceValues[K]) => {
    setSourceValues(prev => ({ ...prev, [key]: value }));
  };

  const buildAiConfigRequest = (): AiProviderSettingRequest => {
    const req: AiProviderSettingRequest = {
      baseUrl: aiDraft.baseUrl.trim(),
      model: aiDraft.model.trim(),
      temperature: Number.isFinite(aiDraft.temperature) ? aiDraft.temperature : 0,
      clearApiKey: aiDraft.clearApiKey,
    };
    if (shouldSubmitSecret(aiDraft.apiKey, aiInitial.apiKey)) req.apiKey = aiDraft.apiKey.trim();
    return req;
  };

  const saveAiConfig = async () => {
    setSaving(true);
    try {
      await api.updateAiProfile(buildAiConfigRequest());
      await api.updateSettings({
        settings: {
          'ai.enabled': aiDraft.aiEnabled,
          'ai.token-usage.enabled': aiDraft.tokenUsageEnabled,
          'ai.memory.enabled': aiDraft.memoryEnabled,
        },
      });
      await reload();
      toast.success('设置已生效');
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '保存 AI 设置失败');
    } finally {
      setSaving(false);
    }
  };

  const rebuildMemory = async () => {
    if (!aiDraft.memoryEnabled) return;
    setMemoryRebuilding(true);
    try {
      const next = await api.rebuildAiMemory();
      setAiMemory(next);
      toast.success(next.exists ? '长期记忆已重建' : '长期记忆已重建，暂无可用画像');
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '重建长期记忆失败');
    } finally {
      setMemoryRebuilding(false);
    }
  };

  const saveSources = async () => {
    setSaving(true);
    try {
      const payload: Record<string, string | number | boolean> = {
        'search.provider': sourceValues.searchProvider,
        'source.bangumi-proxy': sourceValues.bangumiProxy,
        'detail.cast-enabled': sourceValues.detailCastEnabled,
      };
      if (shouldSubmitSecret(sourceValues.serperApiKey, sourceInitial.serperApiKey)) {
        payload['search.serper-api-key'] = sourceValues.serperApiKey.trim();
      }
      const next = await api.updateSettings({ settings: payload });
      applySettings(next);
      toast.success('设置已生效');
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '保存设置失败');
    } finally {
      setSaving(false);
    }
  };

  const runTest = async (key: TestKey) => {
    setTestLoading(key);
    setTestResults(prev => ({ ...prev, [key]: null }));
    try {
      const result = key === 'ai'
        ? await api.testAiProfile({
          baseUrl: aiDraft.baseUrl,
          apiKey: aiDraft.apiKey,
          model: aiDraft.model,
          temperature: aiDraft.temperature,
          clearApiKey: aiDraft.clearApiKey,
        })
        : key === 'search'
          ? await api.testSearchSettings({
            provider: sourceValues.searchProvider,
            serperApiKey: sourceValues.serperApiKey,
            bangumiProxy: sourceValues.bangumiProxy,
            query: '孤独摇滚',
          })
          : await api.testBangumiSettings({
            bangumiProxy: sourceValues.bangumiProxy,
          });
      setTestResults(prev => ({ ...prev, [key]: result }));
    } catch (err) {
      setTestResults(prev => ({
        ...prev,
        [key]: {
          ok: false,
          message: err instanceof Error ? err.message : '测试失败',
        },
      }));
    } finally {
      setTestLoading(null);
    }
  };

  const renderMemoryItemGroup = (title: string, json: string | null) => {
    const items = parseMemoryItems(json);
    return (
      <div className="settings-memory-detail-section">
        <h5>{title}</h5>
        {items.length > 0 ? (
          <div className="settings-memory-tags">
            {items.map((item, index) => <span key={`${title}-${index}`} className="settings-memory-tag">{item}</span>)}
          </div>
        ) : (
          <p>暂无</p>
        )}
      </div>
    );
  };

  const renderMemoryPreview = () => {
    if (memoryLoading && !aiMemory) {
      return <div className="settings-memory-empty">读取长期记忆中...</div>;
    }
    if (!aiMemory?.exists) {
      return <div className="settings-memory-empty">还没有生成长期记忆</div>;
    }
    return (
      <div className="settings-memory-preview">
        <div className="settings-memory-meta">
          <span className="settings-memory-pill">版本 v{aiMemory.version ?? '-'}</span>
          <span className="settings-memory-pill">更新 {formatMemoryTime(aiMemory.updatedAt)}</span>
          {memoryLoading && <span className="settings-memory-pill">刷新中...</span>}
          {!aiDraft.memoryEnabled && <span className="settings-memory-pill is-disabled">当前未启用</span>}
        </div>
        <p className="settings-memory-summary">{aiMemory.summary || '暂无摘要'}</p>
        <button
          type="button"
          className="btn-ghost settings-memory-detail-toggle"
          onClick={() => setMemoryDetailsOpen(open => !open)}
        >
          {memoryDetailsOpen ? '收起偏好细节' : '查看偏好细节'}
        </button>
        {memoryDetailsOpen && (
          <div className="settings-memory-details">
            {renderMemoryItemGroup('喜欢', aiMemory.likesJson)}
            {renderMemoryItemGroup('不喜欢', aiMemory.dislikesJson)}
            {renderMemoryItemGroup('近期变化', aiMemory.recentShiftJson)}
            {renderMemoryItemGroup('推荐规则', aiMemory.recommendationRulesJson)}
          </div>
        )}
      </div>
    );
  };

  const renderMemorySettings = () => (
    <div className="settings-subsection">
      <div className="settings-subsection-head">
        <div>
          <h4>长期记忆</h4>
          <p>从本地标记、评分和影评生成可重建的长期偏好画像。</p>
        </div>
        <button
          type="button"
          className="btn-ghost"
          disabled={!aiDraft.memoryEnabled || memoryRebuilding}
          title={!aiDraft.memoryEnabled ? '开启长期记忆后可重建' : undefined}
          onClick={rebuildMemory}
        >
          {memoryRebuilding ? '重建中...' : '重建长期记忆'}
        </button>
      </div>

      <ToggleRow
        title="AI 长期记忆"
        description="开启后自动更新偏好画像，并用于推荐与分析；当前请求和明确条件始终优先。"
        checked={aiDraft.memoryEnabled}
        onChange={value => setAiConfig('memoryEnabled', value)}
      />

      {renderMemoryPreview()}
    </div>
  );

  const renderAiSettings = () => (
    <>
      <div className="settings-panel-head">
        <div>
          <h3>AI 接入配置</h3>
          <p>填写 OpenAI-compatible API 前缀、模型名称和 API Key。保存后立即生效，无需重启。</p>
        </div>
        <div className="settings-panel-buttons">
          <button type="button" className="btn-ghost" disabled={testLoading !== null} onClick={() => runTest('ai')}>
            {testLoading === 'ai' ? '测试中...' : '测试 AI'}
          </button>
          <button type="button" className="btn-primary" disabled={!aiDirty || saving} onClick={saveAiConfig}>
            {saving ? '保存中...' : '保存'}
          </button>
        </div>
      </div>

      <div className="settings-ai-editor">
        <div className="settings-form">
          <ToggleRow title="AI 助手" checked={aiDraft.aiEnabled} onChange={value => setAiConfig('aiEnabled', value)} />

          <ToggleRow title="记录 token 使用明细" checked={aiDraft.tokenUsageEnabled} onChange={value => setAiConfig('tokenUsageEnabled', value)} />

          <SettingsRow title="API Base URL" description="填写完整 API 前缀，例如 https://api.deepseek.com/v1 或 https://open.bigmodel.cn/api/paas/v4。">
            <input className="settings-input" value={aiDraft.baseUrl} onChange={event => setAiConfig('baseUrl', event.target.value)} />
          </SettingsRow>

          <SettingsRow title="模型名称">
            <input className="settings-input" value={aiDraft.model} onChange={event => setAiConfig('model', event.target.value)} />
          </SettingsRow>

          <SettingsRow title="API Key">
            <SecretInput
              value={aiDraft.apiKey}
              visible={showAiSecret}
              placeholder="API Key"
              onVisibilityChange={setShowAiSecret}
              onChange={value => setAiConfig('apiKey', value)}
            />
          </SettingsRow>

          {aiDraft.apiKeySet && (
            <ToggleRow title="清空 API Key" checked={aiDraft.clearApiKey} onChange={value => setAiConfig('clearApiKey', value)} />
          )}

          <SettingsRow title="Temperature">
            <div className="settings-number">
              <input
                className="settings-input"
                type="number"
                min={0}
                max={2}
                step={0.1}
                value={String(aiDraft.temperature)}
                onChange={event => setAiConfig('temperature', Number(event.target.value))}
              />
              <input
                className="settings-range"
                type="range"
                min={0}
                max={2}
                step={0.1}
                value={String(aiDraft.temperature)}
                onChange={event => setAiConfig('temperature', Number(event.target.value))}
              />
            </div>
          </SettingsRow>
        </div>

        {renderMemorySettings()}
      </div>

      <div className="settings-results"><TestResult result={testResults.ai} /></div>
    </>
  );

  const renderSourceSettings = () => (
    <>
      <div className="settings-panel-head">
        <div>
          <h3>搜索与数据源</h3>
          <p>搜索服务、Bangumi 代理和详情数据控制。</p>
        </div>
        <div className="settings-panel-buttons">
          <button type="button" className="btn-ghost" disabled={testLoading !== null} onClick={() => runTest('search')}>
            {testLoading === 'search' ? '测试中...' : '测试搜索'}
          </button>
          <button type="button" className="btn-ghost" disabled={testLoading !== null} onClick={() => runTest('bangumi')}>
            {testLoading === 'bangumi' ? '测试中...' : '测试 Bangumi'}
          </button>
          <button type="button" className="btn-primary" disabled={!sourceDirty || saving} onClick={saveSources}>
            {saving ? '保存中...' : '保存'}
          </button>
        </div>
      </div>

      <div className="settings-form">
        <SettingsRow title="搜索源">
          <div className="settings-segmented">
            <button type="button" className={sourceValues.searchProvider === 'auto' ? 'is-active' : ''} onClick={() => setSource('searchProvider', 'auto')}>智能选择</button>
            <button type="button" className={sourceValues.searchProvider === 'serper' ? 'is-active' : ''} onClick={() => setSource('searchProvider', 'serper')}>Serper</button>
            <button type="button" className={sourceValues.searchProvider === 'ddg' ? 'is-active' : ''} onClick={() => setSource('searchProvider', 'ddg')}>DuckDuckGo</button>
          </div>
        </SettingsRow>

        <SettingsRow title="Serper API Key">
          <SecretInput
            value={sourceValues.serperApiKey}
            visible={showSerperSecret}
            placeholder="Serper API Key"
            onVisibilityChange={setShowSerperSecret}
            onChange={value => setSource('serperApiKey', value)}
          />
        </SettingsRow>

        <SettingsRow title="Bangumi 代理地址">
          <input className="settings-input" value={sourceValues.bangumiProxy} placeholder="https://api.bgm.tv/v0" onChange={event => setSource('bangumiProxy', event.target.value)} />
        </SettingsRow>

        <ToggleRow title="展示角色 / 演员信息" checked={sourceValues.detailCastEnabled} onChange={value => setSource('detailCastEnabled', value)} />
      </div>

      <div className="settings-results">
        <TestResult result={testResults.search} />
        <TestResult result={testResults.bangumi} />
      </div>
    </>
  );

  return (
    <div className="settings-shell">
      <div className="settings-topbar">
        <div>
          <h2>{GROUPS.find(group => group.key === activeGroup)?.label}</h2>
        </div>
        <div className="settings-actions">
          {dirty && <span className="settings-dirty">未保存</span>}
        </div>
      </div>

      <div className="settings-layout">
        <nav className="settings-nav" aria-label="设置分组">
          {GROUPS.map(group => (
            <button
              key={group.key}
              type="button"
              className={activeGroup === group.key ? 'is-active' : ''}
              onClick={() => setActiveGroup(group.key)}
            >
              {group.label}
            </button>
          ))}
        </nav>

        <section className="settings-panel">
          {loading ? (
            <div className="settings-loading">
              <span className="h-2 w-2 dot-1 rounded-full" style={{ background: 'var(--accent)' }} />
              <span className="h-2 w-2 dot-2 rounded-full" style={{ background: 'var(--accent)' }} />
              <span className="h-2 w-2 dot-3 rounded-full" style={{ background: 'var(--accent)' }} />
            </div>
          ) : activeGroup === 'ai' ? renderAiSettings() : renderSourceSettings()}
        </section>
      </div>
    </div>
  );
}
