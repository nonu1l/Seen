import { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { useHomeList } from '../api/hooks';
import { setBangumiProxy } from '../api/proxy';
import type { Status, WorkListItemDTO, WorkSearchResultDTO } from '../api/types';
import { WorkCard } from '../components/WorkCard';
import { WorkDetailModal } from '../components/WorkDetailModal';
import { RobotLogo } from '../components/RobotLogo';
import { STATUS_FILTERS } from '../utils/statusMeta';

interface OpenedWork { id: number; platform: string; }

export default function HomePage() {
  const { query, setQuery, marked, search, loading, error, refresh, refreshSearch } = useHomeList();
  const [opened, setOpened] = useState<OpenedWork | null>(null);
  const [filter, setFilter] = useState<Status | 'all'>('all');
  const [aiEnabled, setAiEnabled] = useState(true);
  const showingSearch = query.trim().length > 0;

  /** IME 组合状态：中文输入法拼音期间不触发搜索 */
  const composingRef = useRef(false);
  const [displayQuery, setDisplayQuery] = useState('');

  useEffect(() => {
    api.getAppConfig().then(c => {
      setAiEnabled(c.aiEnabled);
      setBangumiProxy(c.bangumiProxy);
    }).catch(() => {});
  }, []);

  /** 同步 displayQuery ← query（外部重置或 hooks 变化时） */
  useEffect(() => { setDisplayQuery(query); }, [query]);

  const merged = useMemo(() => {
    if (!showingSearch || !search) return { marked, unmarked: [] as WorkSearchResultDTO[] };
    const keys = new Set(search.local.map(w => `${w.platform}:${w.id}`));
    return { marked: search.local, unmarked: search.works.filter(w => !keys.has(`${w.platform}:${w.id}`)) };
  }, [showingSearch, search, marked]);

  const filteredMarked = useMemo(() => {
    if (!showingSearch && filter !== 'all') return merged.marked.filter(w => (w as WorkListItemDTO).status === filter);
    return merged.marked;
  }, [merged.marked, filter, showingSearch]);

  const counts = useMemo(() => {
    const c: Record<string, number> = {};
    for (const w of merged.marked) { const s = (w as WorkListItemDTO).status; if (s) c[s] = (c[s] || 0) + 1; }
    return c;
  }, [merged.marked]);

  const handleMark = async (item: WorkListItemDTO | WorkSearchResultDTO, status: Status, unmarked: boolean) => {
    try {
      await api.mark({ id: String(item.id), platform: item.platform, status, meta: unmarked ? (item as WorkSearchResultDTO) : undefined });
      showingSearch ? await refreshSearch() : await refresh();
    } catch (e) { console.error(e); }
  };

  const handleClose = async (changed: boolean) => {
    setOpened(null);
    if (changed) showingSearch ? await refreshSearch() : await refresh();
  };

  const isEmpty = !loading && !error && merged.marked.length === 0 && merged.unmarked.length === 0;

  return (
    <>
      {/* 搜索框 — sticky */}
      <div className="sticky top-0 z-10 -mx-3 px-3 sm:-mx-8 sm:px-8 pt-4 pb-3" style={{ background: 'var(--bg)' }}>
        <div className="relative">
          <svg className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2" style={{ color: 'var(--text-muted)' }}
               fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z" />
          </svg>
          <input type="text" value={displayQuery}
                 onChange={e => {
                   setDisplayQuery(e.target.value);
                   if (!composingRef.current) {
                     setQuery(e.target.value);
                   }
                 }}
                 onCompositionStart={() => { composingRef.current = true; }}
                 onCompositionEnd={e => {
                   composingRef.current = false;
                   setQuery(e.currentTarget.value);
                 }}
                 placeholder="搜索片名..."
                 className="search-input" />
          {displayQuery && (
            <button type="button" onClick={() => { setQuery(''); setDisplayQuery(''); }}
                    className="absolute right-4 top-1/2 -translate-y-1/2 transition-colors hover:opacity-70" style={{ color: 'var(--text-muted)' }} aria-label="清除">
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          )}
        </div>
      </div>

      {/* Hint */}
      {!showingSearch && merged.marked.length > 0 && (
        <p className="mb-5 mt-3 text-[12px]" style={{ color: 'var(--text-muted)' }}>输入片名开始搜索</p>
      )}

      {/* Filter tabs — only when not searching */}
      {!showingSearch && merged.marked.length > 0 && (
        <div className="flex items-center gap-1.5 mb-4 overflow-x-auto scrollbar-none">
          {STATUS_FILTERS.map(f => (
            <button
              key={f.key}
              onClick={() => setFilter(f.key)}
              className="flex-shrink-0 px-3 py-1.5 rounded-full text-[12px] font-medium transition-all duration-200"
              style={{
                background: filter === f.key ? 'var(--accent-dim)' : 'transparent',
                color: filter === f.key ? 'var(--accent)' : 'var(--text-muted)',
                border: `1px solid ${filter === f.key ? 'rgba(200,147,59,0.25)' : 'var(--border)'}`,
              }}
            >
              {f.label}{f.key === 'all' ? ` (${merged.marked.length})` : counts[f.key] ? ` (${counts[f.key]})` : ''}
            </button>
          ))}
        </div>
      )}

      {showingSearch && (
        <p className="mb-5 mt-3 text-[12px]" style={{ color: 'var(--text-muted)' }}>本地 + Bangumi 搜索</p>
      )}

      {/* Loading */}
      {loading && (
        <div className="flex justify-center py-16">
          <span className="inline-flex items-center gap-1.5">
            <span className="h-2 w-2 dot-1 rounded-full" style={{ background: 'var(--accent)' }} />
            <span className="h-2 w-2 dot-2 rounded-full" style={{ background: 'var(--accent)' }} />
            <span className="h-2 w-2 dot-3 rounded-full" style={{ background: 'var(--accent)' }} />
          </span>
        </div>
      )}

      {error && <div className="mb-6 rounded-lg border border-red-500/20 bg-red-500/5 px-4 py-3 text-sm text-red-400">{error}</div>}

      {/* Empty */}
      {isEmpty && (
        <div className="flex flex-col items-center gap-3 py-24 text-center">
          <p className="text-sm" style={{ color: 'var(--text-muted)' }}>
            {showingSearch ? '没有找到匹配的作品' : '还没有标记作品'}
          </p>
          <p className="max-w-xs text-[13px] leading-relaxed" style={{ color: 'var(--text-secondary)' }}>
            {showingSearch ? '试试其他关键词' : '在上方搜索框输入片名，找到作品后即可标记'}
          </p>
        </div>
      )}

      {/* Marked */}
      {filteredMarked.length > 0 && (
        <section className="mb-8">
          {showingSearch && <p className="mb-3 text-[12px] font-medium" style={{ color: 'var(--text-muted)' }}>标记 · {filteredMarked.length}</p>}
          <div className="grid gap-4 grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 2xl:grid-cols-7">
            {filteredMarked.map((w, i) => (
              <WorkCard key={`local-${w.id}`} data={w} index={i}
                onOpen={() => setOpened({ id: w.id, platform: w.platform })}
                onQuickMark={s => handleMark(w, s, false)} />
            ))}
          </div>
        </section>
      )}

      {/* Bangumi results */}
      {showingSearch && merged.unmarked.length > 0 && (
        <section>
          <p className="mb-3 text-[12px] font-medium" style={{ color: 'var(--text-muted)' }}>Bangumi · {merged.unmarked.length}</p>
          <div className="grid gap-4 grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 2xl:grid-cols-7">
            {merged.unmarked.map((w, i) => (
              <WorkCard key={`bgm-${w.platform}-${w.id}`} data={w} unmarked index={merged.marked.length + i}
                onOpen={() => setOpened({ id: w.id, platform: w.platform })}
                onQuickMark={s => handleMark(w, s, true)} />
            ))}
          </div>
        </section>
      )}

      {opened && <WorkDetailModal id={opened.id} platform={opened.platform} onClose={handleClose} />}

      {/* AI 机器人入口 */}
      {!opened && aiEnabled && (
        <Link to="/ai" className="robot-fab-link">
          <RobotLogo />
        </Link>
      )}
    </>
  );
}
