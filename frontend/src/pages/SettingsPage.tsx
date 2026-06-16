import { useEffect, useMemo, useState } from 'react';
import { api } from '../api/client';
import { useToast } from '../components/ToastProvider';
import { SecretInput } from '../components/settings/SecretInput';
import { SettingsRow } from '../components/settings/SettingsRow';
import { TestResult } from '../components/settings/TestResult';
import { ToggleRow } from '../components/settings/ToggleRow';
import type {
  AdminOverviewResponse,
  AiMemoryResponse,
  AiProviderSettingRequest,
  SettingsResponse,
  SettingsTestResult,
} from '../api/types';

type TestKey = 'ai' | 'search' | 'bangumi';
type SettingsGroup = 'ai' | 'sources' | 'extra';

interface EditableAiConfig {
  aiEnabled: boolean;
  tokenUsageEnabled: boolean;
  memoryEnabled: boolean;
  baseUrl: string;
  model: string;
  temperature: number;
  apiKey: string;
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
  { key: 'extra' as const, label: '额外配置' },
];

const EMPTY_AI_CONFIG: EditableAiConfig = {
  aiEnabled: true,
  tokenUsageEnabled: true,
  memoryEnabled: true,
  baseUrl: '',
  model: '',
  temperature: 0,
  apiKey: '',
};

function isSecretUnchanged(current: string, initial: string) {
  return current === initial;
}

function shouldSubmitSecret(current: string, initial: string) {
  const normalized = current.trim();
  return normalized !== '' && normalized !== initial;
}

function shouldSubmitAiSecret(current: string, initial: string) {
  return current !== initial;
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
    && isSecretUnchanged(a.apiKey, b.apiKey);
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

function formatBytes(value: number) {
  if (!Number.isFinite(value) || value <= 0) return '0 B';
  const units = ['B', 'KB', 'MB'];
  let size = value;
  let unitIndex = 0;
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024;
    unitIndex += 1;
  }
  return unitIndex === 0 ? `${Math.round(size)} ${units[unitIndex]}` : `${size.toFixed(1)} ${units[unitIndex]}`;
}

function sameSources(a: SourceValues, b: SourceValues) {
  return a.searchProvider === b.searchProvider
    && isSecretUnchanged(a.serperApiKey, b.serperApiKey)
    && a.bangumiProxy === b.bangumiProxy
    && a.detailCastEnabled === b.detailCastEnabled;
}

export default function SettingsPage() {
  const toast = useToast();
  const [activeGroup, setActiveGroup] = useState<SettingsGroup>('ai');
  const [aiDraft, setAiDraft] = useState<EditableAiConfig>(EMPTY_AI_CONFIG);
  const [aiInitial, setAiInitial] = useState<EditableAiConfig>(EMPTY_AI_CONFIG);
  const [sourceValues, setSourceValues] = useState<SourceValues>(() => toSourceValues(null));
  const [sourceInitial, setSourceInitial] = useState<SourceValues>(() => toSourceValues(null));
  const [aiMemory, setAiMemory] = useState<AiMemoryResponse | null>(null);
  const [adminOverview, setAdminOverview] = useState<AdminOverviewResponse | null>(null);
  const [showAiSecret, setShowAiSecret] = useState(false);
  const [showSerperSecret, setShowSerperSecret] = useState(false);
  const [loading, setLoading] = useState(true);
  const [memoryLoading, setMemoryLoading] = useState(true);
  const [memoryRebuilding, setMemoryRebuilding] = useState(false);
  const [memoryDetailsOpen, setMemoryDetailsOpen] = useState(false);
  const [adminOverviewLoading, setAdminOverviewLoading] = useState(false);
  const [adminOverviewRequested, setAdminOverviewRequested] = useState(false);
  const [adminActionLoading, setAdminActionLoading] = useState<'cache' | 'token' | null>(null);
  const [saving, setSaving] = useState(false);
  const [testLoading, setTestLoading] = useState<TestKey | null>(null);
  const [testResults, setTestResults] = useState<Record<TestKey, SettingsTestResult | null>>({
    ai: null,
    search: null,
    bangumi: null,
  });

  const aiDirty = useMemo(() => !sameAiConfig(aiDraft, aiInitial), [aiDraft, aiInitial]);
  const extraDirty = useMemo(() => aiDraft.tokenUsageEnabled !== aiInitial.tokenUsageEnabled, [aiDraft, aiInitial]);
  const sourceDirty = useMemo(() => !sameSources(sourceValues, sourceInitial), [sourceValues, sourceInitial]);
  const dirty = activeGroup === 'ai' ? aiDirty : activeGroup === 'sources' ? sourceDirty : extraDirty;

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

  const reloadAdminOverview = async () => {
    setAdminOverviewLoading(true);
    try {
      const next = await api.getAdminOverview();
      setAdminOverview(next);
      return next;
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '读取额外配置汇总失败');
      throw err;
    } finally {
      setAdminOverviewLoading(false);
    }
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
    if (activeGroup !== 'extra') return;
    if (adminOverview !== null || adminOverviewLoading || adminOverviewRequested) return;
    setAdminOverviewRequested(true);
    reloadAdminOverview().catch(() => {});
  }, [activeGroup, adminOverview, adminOverviewLoading, adminOverviewRequested]);

  useEffect(() => {
    if (!aiDirty && !sourceDirty && !extraDirty) return;
    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  }, [aiDirty, sourceDirty, extraDirty]);

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
    };
    if (shouldSubmitAiSecret(aiDraft.apiKey, aiInitial.apiKey)) req.apiKey = aiDraft.apiKey.trim();
    return req;
  };

  const saveAiConfig = async () => {
    setSaving(true);
    try {
      await api.updateAiProfile(buildAiConfigRequest());
      await api.updateSettings({
        settings: {
          'ai.enabled': aiDraft.aiEnabled,
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

  const saveExtraConfig = async () => {
    setSaving(true);
    try {
      const next = await api.updateSettings({
        settings: {
          'ai.token-usage.enabled': aiDraft.tokenUsageEnabled,
        },
      });
      applySettings(next);
      toast.success('设置已生效');
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '保存额外配置失败');
    } finally {
      setSaving(false);
    }
  };

  const clearRequestCache = async () => {
    if (!window.confirm('确定要清空当前请求缓存吗？')) return;
    setAdminActionLoading('cache');
    try {
      const next = await api.clearRequestCache();
      setAdminOverview(next);
      toast.success('请求缓存已清空');
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '清空请求缓存失败');
    } finally {
      setAdminActionLoading(null);
    }
  };

  const resetTokenUsage = async () => {
    if (!window.confirm('确定要重置 Token 计算吗？这会删除全部 Token 使用明细。')) return;
    setAdminActionLoading('token');
    try {
      const next = await api.resetTokenUsage();
      setAdminOverview(next);
      toast.success('Token 计算已重置');
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '重置 Token 计算失败');
    } finally {
      setAdminActionLoading(null);
    }
  };

  const openAdminPage = (path: string) => {
    window.open(path, '_blank');
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
      <SettingsRow
        title="AI 长期记忆"
        description="开启后自动更新偏好画像，并用于推荐与分析；当前请求和明确条件始终优先。"
      >
        <div className="settings-memory-control">
          <button
            type="button"
            className="btn-ghost"
            disabled={!aiDraft.memoryEnabled || memoryRebuilding}
            title={!aiDraft.memoryEnabled ? '开启长期记忆后可重建' : undefined}
            onClick={rebuildMemory}
          >
            {memoryRebuilding ? '重建中...' : '重建长期记忆'}
          </button>
          <button
            type="button"
            className={`settings-toggle ${aiDraft.memoryEnabled ? 'is-on' : ''}`}
            aria-pressed={aiDraft.memoryEnabled}
            onClick={() => setAiConfig('memoryEnabled', !aiDraft.memoryEnabled)}
          >
            <span />
          </button>
        </div>
      </SettingsRow>

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

  const renderExtraSettings = () => (
    <>
      <div className="settings-panel-head">
        <div>
          <h3>额外配置</h3>
          <p>查看 Token 与请求缓存总量，管理调试明细和本地运行数据。</p>
        </div>
        <div className="settings-panel-buttons">
          <button type="button" className="btn-ghost" onClick={() => openAdminPage('/admin/token-usage')}>
            查看 Token 明细
          </button>
          <button type="button" className="btn-ghost" onClick={() => openAdminPage('/admin/request-cache')}>
            查看缓存明细
          </button>
          <button type="button" className="btn-primary" disabled={!extraDirty || saving} onClick={saveExtraConfig}>
            {saving ? '保存中...' : '保存'}
          </button>
        </div>
      </div>

      <div className="settings-extra">
        <div className="settings-overview-grid">
          <div className="settings-overview-metric">
            <span>Token 消耗总量</span>
            <strong>{adminOverviewLoading && !adminOverview ? '读取中...' : (adminOverview?.totalTokens ?? 0).toLocaleString()}</strong>
          </div>
          <div className="settings-overview-metric">
            <span>缓存使用总量</span>
            <strong>{adminOverviewLoading && !adminOverview ? '读取中...' : formatBytes(adminOverview?.cacheBytes ?? 0)}</strong>
          </div>
        </div>

        <div className="settings-form">
          <ToggleRow
            title="记录 token 使用明细"
            description="开启后记录每次 AI 调用的 token 用量，并可在后台明细页查看。"
            checked={aiDraft.tokenUsageEnabled}
            onChange={value => setAiConfig('tokenUsageEnabled', value)}
          />

          <SettingsRow title="缓存维护" description="清空当前进程内 HTTP 请求缓存，下一次请求会重新访问数据源。">
            <button
              type="button"
              className="btn-ghost"
              disabled={adminActionLoading !== null}
              onClick={clearRequestCache}
            >
              {adminActionLoading === 'cache' ? '清空中...' : '清空缓存'}
            </button>
          </SettingsRow>

          <SettingsRow title="Token 计算" description="删除全部 token 使用明细，并将统计总量重置为 0。">
            <button
              type="button"
              className="btn-ghost"
              disabled={adminActionLoading !== null}
              onClick={resetTokenUsage}
            >
              {adminActionLoading === 'token' ? '重置中...' : '重置 Token 计算'}
            </button>
          </SettingsRow>
        </div>
      </div>
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
          ) : activeGroup === 'ai' ? renderAiSettings() : activeGroup === 'sources' ? renderSourceSettings() : renderExtraSettings()}
        </section>
      </div>
    </div>
  );
}
