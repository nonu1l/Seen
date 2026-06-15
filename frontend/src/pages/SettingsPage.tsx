import { useEffect, useMemo, useState } from 'react';
import { api } from '../api/client';
import type { SettingItem, SettingsResponse, SettingsTestResult, SettingType } from '../api/types';

type PrimitiveValue = string | number | boolean;
type Values = Record<string, PrimitiveValue>;
type TestKey = 'ai' | 'search' | 'bangumi';

interface FieldOption {
  value: string;
  label: string;
}

interface FieldDef {
  key: string;
  label: string;
  type: SettingType;
  group: 'ai' | 'sources';
  defaultValue: PrimitiveValue;
  sensitive?: boolean;
  placeholder?: string;
  help?: string;
  min?: number;
  max?: number;
  step?: number;
  options?: FieldOption[];
}

const MASK_PLACEHOLDER = '已保存，留空不修改';

const FIELD_DEFS: FieldDef[] = [
  {
    key: 'seen.ai.enabled',
    label: 'AI 助手',
    type: 'boolean',
    group: 'ai',
    defaultValue: true,
    help: '关闭后前端会隐藏 AI 入口，后端会拒绝新的 AI 会话。',
  },
  {
    key: 'spring.ai.openai.base-url',
    label: 'API Base URL',
    type: 'string',
    group: 'ai',
    defaultValue: '',
    placeholder: 'https://api.deepseek.com',
    help: 'OpenAI 兼容服务地址，需要支持 /chat/completions。',
  },
  {
    key: 'spring.ai.openai.api-key',
    label: 'API Key',
    type: 'password',
    group: 'ai',
    defaultValue: '',
    sensitive: true,
    help: '留空保存时不会覆盖已有密钥。',
  },
  {
    key: 'spring.ai.openai.chat.options.model',
    label: '模型名称',
    type: 'string',
    group: 'ai',
    defaultValue: '',
    placeholder: 'deepseek-chat',
  },
  {
    key: 'spring.ai.openai.chat.options.temperature',
    label: 'Temperature',
    type: 'number',
    group: 'ai',
    defaultValue: 0.7,
    min: 0,
    max: 2,
    step: 0.1,
    help: '范围 0 到 2，数值越高回复越发散。',
  },
  {
    key: 'seen.ai.token-usage-enabled',
    label: '记录 token 使用明细',
    type: 'boolean',
    group: 'ai',
    defaultValue: true,
  },
  {
    key: 'seen.search.provider',
    label: '搜索源',
    type: 'select',
    group: 'sources',
    defaultValue: 'ddg',
    options: [
      { value: 'serper', label: 'Serper' },
      { value: 'ddg', label: 'DuckDuckGo' },
    ],
  },
  {
    key: 'seen.search.serper-api-key',
    label: 'Serper API Key',
    type: 'password',
    group: 'sources',
    defaultValue: '',
    sensitive: true,
    help: '选择 Serper 时需要 API Key；留空保存不会覆盖已有密钥。',
  },
  {
    key: 'seen.bangumi-proxy',
    label: 'Bangumi 代理地址',
    type: 'string',
    group: 'sources',
    defaultValue: '',
    placeholder: 'https://api.bgm.tv/v0',
    help: '为空时后端回退到默认 Bangumi API。',
  },
  {
    key: 'seen.detail.cast-enabled',
    label: '展示角色 / 演员信息',
    type: 'boolean',
    group: 'sources',
    defaultValue: true,
  },
];

const GROUPS = [
  { key: 'ai' as const, label: 'AI 助手' },
  { key: 'sources' as const, label: '搜索与数据源' },
];

function flattenSettings(response: SettingsResponse | null): Record<string, SettingItem> {
  const map: Record<string, SettingItem> = {};
  for (const group of response?.groups ?? []) {
    for (const item of group.settings) map[item.key] = item;
  }
  return map;
}

function normalizeValue(def: FieldDef, item?: SettingItem): PrimitiveValue {
  if (def.sensitive) return '';
  const raw = item?.value ?? def.defaultValue;
  if (def.type === 'boolean') {
    if (typeof raw === 'boolean') return raw;
    if (typeof raw === 'string') return raw.toLowerCase() === 'true';
    return Boolean(raw);
  }
  if (def.type === 'number') {
    const n = typeof raw === 'number' ? raw : Number(raw);
    return Number.isFinite(n) ? n : Number(def.defaultValue);
  }
  return String(raw ?? '');
}

function buildValues(response: SettingsResponse | null): Values {
  const itemMap = flattenSettings(response);
  return FIELD_DEFS.reduce<Values>((acc, def) => {
    acc[def.key] = normalizeValue(def, itemMap[def.key]);
    return acc;
  }, {});
}

function valuesEqual(a: PrimitiveValue, b: PrimitiveValue) {
  return String(a) === String(b);
}

function renderDetailValue(value: unknown) {
  if (value == null) return '';
  if (Array.isArray(value)) return value.join('、');
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

export default function SettingsPage() {
  const [response, setResponse] = useState<SettingsResponse | null>(null);
  const [initialValues, setInitialValues] = useState<Values>(() => buildValues(null));
  const [values, setValues] = useState<Values>(() => buildValues(null));
  const [activeGroup, setActiveGroup] = useState<'ai' | 'sources'>('ai');
  const [visibleSecrets, setVisibleSecrets] = useState<Record<string, boolean>>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const [testLoading, setTestLoading] = useState<TestKey | null>(null);
  const [testResults, setTestResults] = useState<Record<TestKey, SettingsTestResult | null>>({
    ai: null,
    search: null,
    bangumi: null,
  });

  const itemMap = useMemo(() => flattenSettings(response), [response]);
  const dirty = useMemo(() => FIELD_DEFS.some(def => {
    const value = values[def.key];
    if (def.sensitive) return String(value ?? '').trim().length > 0;
    return !valuesEqual(value, initialValues[def.key]);
  }), [initialValues, values]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    api.getSettings()
      .then(next => {
        if (cancelled) return;
        const nextValues = buildValues(next);
        setResponse(next);
        setInitialValues(nextValues);
        setValues(nextValues);
        setError(null);
      })
      .catch(err => {
        if (cancelled) return;
        setError(err instanceof Error ? err.message : '读取设置失败');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!dirty) return;
    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  }, [dirty]);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 2600);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const setValue = (key: string, value: PrimitiveValue) => {
    setValues(prev => ({ ...prev, [key]: value }));
    setError(null);
    setToast(null);
  };

  const getString = (key: string) => String(values[key] ?? '');
  const getNumber = (key: string) => {
    const value = values[key];
    const n = typeof value === 'number' ? value : Number(value);
    return Number.isFinite(n) ? n : 0;
  };

  const handleSave = async () => {
    const settings: Record<string, PrimitiveValue> = {};
    for (const def of FIELD_DEFS) {
      const value = values[def.key];
      if (def.sensitive) {
        const secret = String(value ?? '').trim();
        if (secret) settings[def.key] = secret;
        continue;
      }
      if (!valuesEqual(value, initialValues[def.key])) settings[def.key] = value;
    }
    if (Object.keys(settings).length === 0) return;

    setSaving(true);
    setError(null);
    try {
      const next = await api.updateSettings({ settings });
      const nextValues = buildValues(next);
      setResponse(next);
      setInitialValues(nextValues);
      setValues(nextValues);
      setVisibleSecrets({});
      setToast('设置已生效');
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存设置失败');
    } finally {
      setSaving(false);
    }
  };

  const runTest = async (key: TestKey) => {
    setTestLoading(key);
    setTestResults(prev => ({ ...prev, [key]: null }));
    try {
      const result = key === 'ai'
        ? await api.testAiSettings({
          baseUrl: getString('spring.ai.openai.base-url'),
          apiKey: getString('spring.ai.openai.api-key'),
          model: getString('spring.ai.openai.chat.options.model'),
          temperature: getNumber('spring.ai.openai.chat.options.temperature'),
        })
        : key === 'search'
          ? await api.testSearchSettings({
            provider: getString('seen.search.provider'),
            serperApiKey: getString('seen.search.serper-api-key'),
            bangumiProxy: getString('seen.bangumi-proxy'),
            query: '孤独摇滚',
          })
          : await api.testBangumiSettings({
            bangumiProxy: getString('seen.bangumi-proxy'),
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

  const renderField = (def: FieldDef) => {
    const item = itemMap[def.key];
    const value = values[def.key];

    return (
      <div className="settings-row" key={def.key}>
        <div className="settings-row-meta">
          <div className="settings-row-title">
            <span>{item?.label ?? def.label}</span>
          </div>
          {def.help && <p>{def.help}</p>}
        </div>
        <div className="settings-row-control">
          {def.type === 'boolean' && (
            <button
              type="button"
              className={`settings-toggle ${value ? 'is-on' : ''}`}
              onClick={() => setValue(def.key, !value)}
              aria-pressed={Boolean(value)}
            >
              <span />
            </button>
          )}

          {def.type === 'select' && (
            <div className="settings-segmented">
              {(def.options ?? []).map(option => (
                <button
                  type="button"
                  key={option.value}
                  className={value === option.value ? 'is-active' : ''}
                  onClick={() => setValue(def.key, option.value)}
                >
                  {option.label}
                </button>
              ))}
            </div>
          )}

          {def.type === 'number' && (
            <div className="settings-number">
              <input
                className="settings-input"
                type="number"
                min={def.min}
                max={def.max}
                step={def.step}
                value={String(value ?? '')}
                onChange={event => setValue(def.key, Number(event.target.value))}
              />
              <input
                className="settings-range"
                type="range"
                min={def.min}
                max={def.max}
                step={def.step}
                value={String(value ?? '')}
                onChange={event => setValue(def.key, Number(event.target.value))}
              />
            </div>
          )}

          {(def.type === 'string' || def.type === 'password') && (
            <div className="settings-secret-wrap">
              <input
                className="settings-input"
                type={def.type === 'password' && !visibleSecrets[def.key] ? 'password' : 'text'}
                value={String(value ?? '')}
                placeholder={def.sensitive ? MASK_PLACEHOLDER : def.placeholder}
                onChange={event => setValue(def.key, event.target.value)}
              />
              {def.type === 'password' && (
                <button
                  type="button"
                  className="btn-icon settings-secret-button"
                  title={visibleSecrets[def.key] ? '隐藏' : '显示'}
                  aria-label={visibleSecrets[def.key] ? '隐藏' : '显示'}
                  onClick={() => setVisibleSecrets(prev => ({ ...prev, [def.key]: !prev[def.key] }))}
                >
                  {visibleSecrets[def.key] ? (
                    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round">
                      <path d="M3 3l18 18" />
                      <path d="M10.6 10.6a2 2 0 0 0 2.8 2.8" />
                      <path d="M9.9 4.2A10.7 10.7 0 0 1 12 4c5 0 8.7 4 10 8a12.8 12.8 0 0 1-3 4.7" />
                      <path d="M6.6 6.6A12.9 12.9 0 0 0 2 12c1.3 4 5 8 10 8a10.8 10.8 0 0 0 4.2-.9" />
                    </svg>
                  ) : (
                    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round">
                      <path d="M2 12s3.7-7 10-7 10 7 10 7-3.7 7-10 7S2 12 2 12z" />
                      <circle cx="12" cy="12" r="3" />
                    </svg>
                  )}
                </button>
              )}
            </div>
          )}
        </div>
      </div>
    );
  };

  const activeDefs = FIELD_DEFS.filter(def => def.group === activeGroup);

  return (
    <div className="settings-shell">
      <div className="settings-topbar">
        <div>
          <h2>{GROUPS.find(group => group.key === activeGroup)?.label}</h2>
        </div>
        <div className="settings-actions">
          {dirty && <span className="settings-dirty">未保存</span>}
          <button type="button" className="btn-primary" disabled={!dirty || saving || loading} onClick={handleSave}>
            {saving ? '保存中...' : '保存'}
          </button>
        </div>
      </div>

      {toast && <div className="settings-toast">{toast}</div>}
      {error && (
        <div className="settings-error">
          <span>{error}</span>
          <button type="button" className="btn-ghost" onClick={() => window.location.reload()}>重新加载</button>
        </div>
      )}

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
          ) : (
            <>
              <div className="settings-panel-head">
                <div>
                  <h3>{GROUPS.find(group => group.key === activeGroup)?.label}</h3>
                  <p>{activeGroup === 'ai' ? 'AI 服务连接和对话运行参数。' : '搜索服务、Bangumi 代理和详情数据控制。'}</p>
                </div>
                {activeGroup === 'ai' ? (
                  <button type="button" className="btn-ghost" disabled={testLoading !== null} onClick={() => runTest('ai')}>
                    {testLoading === 'ai' ? '测试中...' : '测试 AI'}
                  </button>
                ) : (
                  <div className="settings-panel-buttons">
                    <button type="button" className="btn-ghost" disabled={testLoading !== null} onClick={() => runTest('search')}>
                      {testLoading === 'search' ? '测试中...' : '测试搜索'}
                    </button>
                    <button type="button" className="btn-ghost" disabled={testLoading !== null} onClick={() => runTest('bangumi')}>
                      {testLoading === 'bangumi' ? '测试中...' : '测试 Bangumi'}
                    </button>
                  </div>
                )}
              </div>

              <div className="settings-form">
                {activeDefs.map(renderField)}
              </div>

              <div className="settings-results">
                {activeGroup === 'ai' ? renderTestResult('ai') : (
                  <>
                    {renderTestResult('search')}
                    {renderTestResult('bangumi')}
                  </>
                )}
              </div>
            </>
          )}
        </section>
      </div>
    </div>
  );
}
