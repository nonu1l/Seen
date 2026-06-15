import { useEffect, useMemo, useState } from 'react';
import { api } from '../api/client';
import { useToast } from '../components/ToastProvider';
import type {
  AiProviderSettingRequest,
  SettingsResponse,
  SettingsTestResult,
} from '../api/types';

type TestKey = 'ai' | 'search' | 'bangumi';

interface EditableAiConfig {
  aiEnabled: boolean;
  tokenUsageEnabled: boolean;
  baseUrl: string;
  model: string;
  temperature: number;
  apiKey: string;
  clearApiKey: boolean;
  apiKeySet: boolean;
}

interface SourceValues {
  searchProvider: 'serper' | 'ddg';
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
  baseUrl: '',
  model: '',
  temperature: 0,
  apiKey: '',
  clearApiKey: false,
  apiKeySet: false,
};

const SECRET_MASK = '************';

function secretDisplayValue(value: string, visible: boolean) {
  return visible || value.trim() === '' ? value : SECRET_MASK;
}

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
    searchProvider: settings?.sources.searchProvider ?? 'ddg',
    serperApiKey: settings?.sources.serperApiKey ?? '',
    serperApiKeySet: settings?.sources.serperApiKeySet ?? false,
    bangumiProxy: settings?.sources.bangumiProxy ?? '',
    detailCastEnabled: settings?.sources.detailCastEnabled ?? true,
  };
}

function sameAiConfig(a: EditableAiConfig, b: EditableAiConfig) {
  return a.aiEnabled === b.aiEnabled
    && a.tokenUsageEnabled === b.tokenUsageEnabled
    && a.baseUrl === b.baseUrl
    && a.model === b.model
    && String(a.temperature) === String(b.temperature)
    && isSecretUnchanged(a.apiKey, b.apiKey)
    && a.clearApiKey === b.clearApiKey;
}

function sameSources(a: SourceValues, b: SourceValues) {
  return a.searchProvider === b.searchProvider
    && isSecretUnchanged(a.serperApiKey, b.serperApiKey)
    && a.bangumiProxy === b.bangumiProxy
    && a.detailCastEnabled === b.detailCastEnabled;
}

function renderDetailValue(value: unknown) {
  if (value == null) return '';
  if (Array.isArray(value)) return value.join('、');
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

function SecretVisibilityIcon({ visible }: { visible: boolean }) {
  return visible ? (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M3 3l18 18" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
      <path d="M10.6 10.7a2 2 0 0 0 2.7 2.7" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
      <path d="M7.1 7.5C5.4 8.6 4 10.2 3 12c2 3.5 5.2 5.5 9 5.5 1.3 0 2.6-.3 3.7-.8" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M10.2 6.7c.6-.1 1.2-.2 1.8-.2 3.8 0 7 2 9 5.5-.5.9-1.2 1.8-2 2.5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  ) : (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M3 12c2-3.5 5.2-5.5 9-5.5s7 2 9 5.5c-2 3.5-5.2 5.5-9 5.5S5 15.5 3 12z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
      <circle cx="12" cy="12" r="2.7" stroke="currentColor" strokeWidth="1.8" />
    </svg>
  );
}

export default function SettingsPage() {
  const toast = useToast();
  const [activeGroup, setActiveGroup] = useState<'ai' | 'sources'>('ai');
  const [aiDraft, setAiDraft] = useState<EditableAiConfig>(EMPTY_AI_CONFIG);
  const [aiInitial, setAiInitial] = useState<EditableAiConfig>(EMPTY_AI_CONFIG);
  const [sourceValues, setSourceValues] = useState<SourceValues>(() => toSourceValues(null));
  const [sourceInitial, setSourceInitial] = useState<SourceValues>(() => toSourceValues(null));
  const [showAiSecret, setShowAiSecret] = useState(false);
  const [showSerperSecret, setShowSerperSecret] = useState(false);
  const [loading, setLoading] = useState(true);
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

  const renderTestResult = (key: TestKey) => {
    const result = testResults[key];
    if (!result) return null;
    const detailEntries = Object.entries(result.details ?? {});
    return (
      <div className={`settings-test-result ${result.ok ? 'is-ok' : 'is-error'}`}>
        <span>{result.message}</span>
        {typeof result.elapsedMs === 'number' && <span>{result.elapsedMs}ms</span>}
        {detailEntries.length > 0 && (
          <div className="settings-test-details">
            {detailEntries.map(([detailKey, detailValue]) => (
              <span key={detailKey}>{detailKey}: {renderDetailValue(detailValue)}</span>
            ))}
          </div>
        )}
      </div>
    );
  };

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
          <div className="settings-row">
            <div className="settings-row-meta">
              <div className="settings-row-title"><span>AI 助手</span></div>
            </div>
            <div className="settings-row-control">
              <button type="button" className={`settings-toggle ${aiDraft.aiEnabled ? 'is-on' : ''}`} aria-pressed={aiDraft.aiEnabled} onClick={() => setAiConfig('aiEnabled', !aiDraft.aiEnabled)}>
                <span />
              </button>
            </div>
          </div>

          <div className="settings-row">
            <div className="settings-row-meta">
              <div className="settings-row-title"><span>记录 token 使用明细</span></div>
            </div>
            <div className="settings-row-control">
              <button type="button" className={`settings-toggle ${aiDraft.tokenUsageEnabled ? 'is-on' : ''}`} aria-pressed={aiDraft.tokenUsageEnabled} onClick={() => setAiConfig('tokenUsageEnabled', !aiDraft.tokenUsageEnabled)}>
                <span />
              </button>
            </div>
          </div>

          <div className="settings-row">
            <div className="settings-row-meta">
              <div className="settings-row-title"><span>API Base URL</span></div>
              <p>填写完整 API 前缀，例如 https://api.deepseek.com/v1 或 https://open.bigmodel.cn/api/paas/v4。</p>
            </div>
            <div className="settings-row-control">
              <input className="settings-input" value={aiDraft.baseUrl} onChange={event => setAiConfig('baseUrl', event.target.value)} />
            </div>
          </div>

          <div className="settings-row">
            <div className="settings-row-meta">
              <div className="settings-row-title"><span>模型名称</span></div>
            </div>
            <div className="settings-row-control">
              <input className="settings-input" value={aiDraft.model} onChange={event => setAiConfig('model', event.target.value)} />
            </div>
          </div>

          <div className="settings-row">
            <div className="settings-row-meta">
              <div className="settings-row-title"><span>API Key</span></div>
            </div>
            <div className="settings-row-control">
              <div className="settings-secret-wrap">
                <input
                  className="settings-input"
                  type="text"
                  value={secretDisplayValue(aiDraft.apiKey, showAiSecret)}
                  placeholder="API Key"
                  readOnly={!showAiSecret && aiDraft.apiKey.trim() !== ''}
                  onChange={event => {
                    if (!showAiSecret) setShowAiSecret(true);
                    setAiConfig('apiKey', event.target.value);
                  }}
                />
                <button
                  type="button"
                  className="btn-icon settings-secret-button"
                  title={showAiSecret ? '隐藏' : '显示'}
                  aria-label={showAiSecret ? '隐藏' : '显示'}
                  onClick={() => setShowAiSecret(prev => !prev)}
                >
                  <SecretVisibilityIcon visible={showAiSecret} />
                </button>
              </div>
            </div>
          </div>

          {aiDraft.apiKeySet && (
            <div className="settings-row">
              <div className="settings-row-meta">
                <div className="settings-row-title"><span>清空 API Key</span></div>
              </div>
              <div className="settings-row-control">
                <button
                  type="button"
                  className={`settings-toggle ${aiDraft.clearApiKey ? 'is-on' : ''}`}
                  aria-pressed={aiDraft.clearApiKey}
                  onClick={() => setAiConfig('clearApiKey', !aiDraft.clearApiKey)}
                >
                  <span />
                </button>
              </div>
            </div>
          )}

          <div className="settings-row">
            <div className="settings-row-meta">
              <div className="settings-row-title"><span>Temperature</span></div>
            </div>
            <div className="settings-row-control">
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
            </div>
          </div>
        </div>
      </div>

      <div className="settings-results">{renderTestResult('ai')}</div>
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
        <div className="settings-row">
          <div className="settings-row-meta">
            <div className="settings-row-title"><span>搜索源</span></div>
          </div>
          <div className="settings-row-control">
            <div className="settings-segmented">
              <button type="button" className={sourceValues.searchProvider === 'serper' ? 'is-active' : ''} onClick={() => setSource('searchProvider', 'serper')}>Serper</button>
              <button type="button" className={sourceValues.searchProvider === 'ddg' ? 'is-active' : ''} onClick={() => setSource('searchProvider', 'ddg')}>DuckDuckGo</button>
            </div>
          </div>
        </div>

        <div className="settings-row">
          <div className="settings-row-meta">
            <div className="settings-row-title"><span>Serper API Key</span></div>
          </div>
          <div className="settings-row-control">
            <div className="settings-secret-wrap">
              <input
                className="settings-input"
                type="text"
                value={secretDisplayValue(sourceValues.serperApiKey, showSerperSecret)}
                placeholder="Serper API Key"
                readOnly={!showSerperSecret && sourceValues.serperApiKey.trim() !== ''}
                onChange={event => {
                  if (!showSerperSecret) setShowSerperSecret(true);
                  setSource('serperApiKey', event.target.value);
                }}
              />
              <button
                type="button"
                className="btn-icon settings-secret-button"
                title={showSerperSecret ? '隐藏' : '显示'}
                aria-label={showSerperSecret ? '隐藏' : '显示'}
                onClick={() => setShowSerperSecret(prev => !prev)}
              >
                <SecretVisibilityIcon visible={showSerperSecret} />
              </button>
            </div>
          </div>
        </div>

        <div className="settings-row">
          <div className="settings-row-meta">
            <div className="settings-row-title"><span>Bangumi 代理地址</span></div>
          </div>
          <div className="settings-row-control">
            <input className="settings-input" value={sourceValues.bangumiProxy} placeholder="https://api.bgm.tv/v0" onChange={event => setSource('bangumiProxy', event.target.value)} />
          </div>
        </div>

        <div className="settings-row">
          <div className="settings-row-meta">
            <div className="settings-row-title"><span>展示角色 / 演员信息</span></div>
          </div>
          <div className="settings-row-control">
            <button type="button" className={`settings-toggle ${sourceValues.detailCastEnabled ? 'is-on' : ''}`} aria-pressed={sourceValues.detailCastEnabled} onClick={() => setSource('detailCastEnabled', !sourceValues.detailCastEnabled)}>
              <span />
            </button>
          </div>
        </div>
      </div>

      <div className="settings-results">
        {renderTestResult('search')}
        {renderTestResult('bangumi')}
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
