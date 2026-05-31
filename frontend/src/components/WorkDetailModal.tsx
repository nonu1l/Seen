import { useEffect, useLayoutEffect, useState } from 'react';
import { api } from '../api/client';
import type { Status, WorkDetail } from '../api/types';
import { Cover } from './Cover';
import { StarRating } from './StarRating';

interface Props { id: number; platform: string; onClose: (changed: boolean) => void; }

const STATUSES: { s: Status; label: string; dot: string }[] = [
  { s: 'wish', label: '想看', dot: 'dot-amber' },
  { s: 'doing', label: '在看', dot: 'dot-green' },
  { s: 'collect', label: '看过', dot: 'dot-blue' },
  { s: 'on_hold', label: '搁置', dot: 'dot-gray' },
  { s: 'dropped', label: '抛弃', dot: 'dot-gray' },
];

export function WorkDetailModal({ id, platform, onClose }: Props) {
  const [d, setD] = useState<WorkDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [rating, setRating] = useState<number | null>(null);
  const [review, setReview] = useState('');
  const [saving, setSaving] = useState(false);
  const [changed, setChanged] = useState(false);
  const [cnNames, setCnNames] = useState<Record<number, string>>({});
  const [cnLoading, setCnLoading] = useState<Set<number>>(new Set());
  const [actorNames, setActorNames] = useState<Record<number, string>>({});
  const [plotExpanded, setPlotExpanded] = useState(false);

  useEffect(() => { document.body.style.overflow = 'hidden'; return () => { document.body.style.overflow = ''; }; }, []);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(changed); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [changed, onClose]);

  const refresh = async () => {
    setError(null);
    setCnNames({});
    setCnLoading(new Set());
    setActorNames({});
    setPlotExpanded(false);
    try { const r = await api.getDetail(String(id), platform); setD(r); setRating(r.myRating); setReview(r.myReview ?? ''); }
    catch (e: any) { setError(e?.message || '加载失败'); }
    finally { setLoading(false); }
  };
  useEffect(() => { refresh(); }, [id, platform]); // eslint-disable-line

  // 批量获取角色中文名
  useLayoutEffect(() => {
    if (!d?.cast?.length) return;
    const chars = d.cast.filter(c => c.id != null);
    if (!chars.length) return;
    const ids = new Set(chars.map(c => c.id!));
    setCnLoading(ids);
    api.getCharacterNames(chars.map(c => c.id!)).then(map => {
      const next: Record<number, string> = {};
      const nextLoading = new Set<number>();
      chars.forEach(c => {
        const cn = map[c.id!];
        if (cn) next[c.id!] = cn;
      });
      setCnNames(prev => ({ ...prev, ...next }));
      setCnLoading(nextLoading);
    }).catch(() => {
      setCnLoading(new Set());
    });
  }, [d?.cast, id]);

  // 批量获取演员中文名
  useLayoutEffect(() => {
    if (!d?.cast?.length) return;
    const actors = d.cast.filter(c => c.actorId != null);
    if (!actors.length) return;
    const ids = [...new Set(actors.map(c => c.actorId!))];
    api.getActorNames(ids).then(map => {
      setActorNames(prev => ({ ...prev, ...map }));
    }).catch(() => {});
  }, [d?.cast, id]);

  const mark = async (status: Status) => {
    if (!d) return;
    try {
      const updated = await api.mark({ id: String(d.id ?? id), platform: d.platform ?? platform, status,
        meta: d.id == null ? { id, platform, nameCn: d.nameCn, nameOrig: d.nameOrig, coverUrl: d.coverUrl, year: d.year, tags: d.tags, plot: d.plot, score: d.score, source: 'bangumi' } : undefined });
      setChanged(true);
      // 仅更新标记相关状态，避免 refresh() 导致角色信息重新获取
      setD(prev => prev ? { ...prev, status, id: prev.id ?? updated.id } : prev);
      setRating(updated.myRating);
    } catch (e: any) { setError(e?.message || '标记失败'); }
  };

  // FIXME: 保留
  // const rewatch = async () => { if (!d?.id) return; try { await api.rewatch(d.id); setChanged(true); await refresh(); } catch (e: any) { setError(e?.message || '操作失败'); } };
  const saveReview = async () => {
    if (!d?.id) return; setSaving(true);
    try { await api.updateReview(d.id, rating, review.trim() || null); setChanged(true); }
    catch (e: any) { setError(e?.message || '保存失败'); }
    finally { setSaving(false); }
  };
  const unmark = async () => {
    if (!d?.id) return;
    if (!window.confirm('确定要删除该记录吗？将同时删除作品信息和所有标记记录。')) return;
    try { await api.unmark(d.id); setChanged(true); onClose(true); }
    catch (e: any) { setError(e?.message || '操作失败'); }
  };

{/* FIXME: 保留 const rwDisabled = !d?.id || d.status === 'wish' || d.status === 'dropped' || (d.watchedCount ?? 0) === 0; */}
  const pLabel = d?.platform || platform;

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center pt-[5vh] pb-[5vh] bg-black/75 overflow-y-auto"
         onClick={() => onClose(changed)}>
      <div className="record-panel mobile-fullscreen w-full max-w-3xl my-auto sm:m-4 anim-in"
           onClick={e => e.stopPropagation()} style={{ maxHeight: '90vh', overflowY: 'auto' }}>

        {/* Header */}
        <div className="flex items-center justify-between px-4 py-3" style={{borderBottom:'1px solid var(--border)'}}>
          <div className="flex items-center gap-3 min-w-0">
            <h2 className="text-lg font-semibold text-[color:var(--text-primary)] truncate">{d?.nameCn || '加载中...'}</h2>
            {d && pLabel && <span className="badge badge-outline flex-shrink-0">{pLabel}</span>}
          </div>
          <button onClick={() => onClose(changed)} className="btn-icon" aria-label="关闭">
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <div className="p-4 space-y-4">
          {loading && <div className="flex justify-center py-8 text-sm text-[color:var(--text-muted)]">加载中...</div>}
          {error && <div className="rounded-lg border border-red-500/20 bg-red-500/5 px-4 py-3 text-sm text-red-400">{error}</div>}

          {d && (<>
            {/* Cover + Info */}
            <div className="flex gap-3">
              <div className="flex-shrink-0 self-start w-[88px] sm:w-28 aspect-[2/3] rounded-md overflow-hidden" style={{background:'var(--bg-card)'}}>
                <Cover src={d.coverUrl} alt={d.nameCn} />
              </div>
              <div className="flex-1 min-w-0 space-y-1">
                {d.nameOrig && d.nameOrig !== d.nameCn && (
                  <p className="text-[13px] text-[color:var(--text-secondary)]">{d.nameOrig}</p>
                )}
                <div className="flex flex-wrap items-center gap-1.5 text-[12px]">
                  {d.year && <span className="badge badge-muted">{d.year}</span>}
                  {d.tags?.slice(0, 4).map(t => <span key={t} className="badge badge-muted">{t}</span>)}
                  {d.regions?.length! > 0 && <span className="badge badge-muted">{d.regions!.join(' · ')}</span>}
                  {d.seasonsCount != null && <span className="badge badge-muted">{d.seasonsCount} 季</span>}
                  {d.episodes != null && <span className="badge badge-muted">{d.episodes} 集</span>}
                  {d.runtime != null && <span className="badge badge-muted">{d.runtime} 分钟</span>}
                  {d.score != null && d.score > 0 && <span className="badge badge-accent">{d.score.toFixed(1)}</span>}
                </div>
                <a href={d.imdbId ? `https://www.imdb.com/title/${d.imdbId}/` : `https://www.imdb.com/find/?q=${encodeURIComponent(d.nameOrig || d.nameCn)}`}
                  target="_blank" rel="noopener noreferrer"
                  className="inline-block text-[11px] mt-1 underline underline-offset-2 transition-opacity hover:opacity-70"
                  style={{ color: 'var(--text-muted)' }}>
                  在 IMDb 查看
                </a>
                {d.plot && (
                  <div className="hidden sm:block text-[13px] text-[color:var(--text-secondary)] leading-relaxed">
                    <div className={!plotExpanded ? 'line-clamp-3' : ''}>{d.plot}</div>
                    <button onClick={() => setPlotExpanded(!plotExpanded)}
                      className="text-[12px] text-[color:var(--accent)]">
                      {plotExpanded ? '收起' : '展开'}
                    </button>
                  </div>
                )}
              </div>
            </div>
            {d.plot && (
              <div className="sm:hidden text-[13px] text-[color:var(--text-secondary)] leading-relaxed">
                <div className={!plotExpanded ? 'line-clamp-3' : ''}>{d.plot}</div>
                <button onClick={() => setPlotExpanded(!plotExpanded)}
                  className="text-[12px] text-[color:var(--accent)]">
                  {plotExpanded ? '收起' : '展开'}
                </button>
              </div>
            )}


            {/* Cast */}
            {d.cast && d.cast.length > 0 && (
              <div>
                <p className="mb-2 text-[11px] font-medium uppercase tracking-wider text-[color:var(--text-muted)]">角色介绍</p>
                <div className="flex gap-2 overflow-x-auto pb-1 cast-scroll">
                  {d.cast.map((c, i) => {
                    const loading = c.id != null && cnLoading.has(c.id);
                    if (loading) {
                      return (
                        <div key={i} className="flex-shrink-0 w-[68px] text-center">
                          <div className="w-[68px] h-[92px] skeleton rounded-lg mb-1" />
                          <div className="skeleton h-3 w-[56px] mx-auto mb-0.5" />
                          <div className="skeleton h-2.5 w-[44px] mx-auto" />
                        </div>
                      );
                    }
                    return (
                      <div key={i} className="flex-shrink-0 w-[68px] text-center">
                        <div className="w-[68px] h-[92px] rounded-lg overflow-hidden mb-1 cover-placeholder [&_img]:object-top">
                          <Cover src={c.profile} alt={c.name} />
                        </div>
                        <p className="text-[11px] truncate font-medium text-[color:var(--text-primary)]"
                          title={c.id != null && cnNames[c.id] ? cnNames[c.id] : c.name}>
                          {c.id != null && cnNames[c.id] ? cnNames[c.id] : c.name}
                        </p>
                        {c.character && (
                          <p className="text-[10px] truncate text-[color:var(--text-muted)]"
                            title={c.actorId != null && actorNames[c.actorId] ? actorNames[c.actorId] : c.character}>
                            {c.actorId != null && actorNames[c.actorId] ? actorNames[c.actorId] : c.character}
                          </p>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>
            )}

            {/* FIXME: 保留
              <button onClick={rewatch} disabled={rwDisabled}
                className="flex items-center gap-1.5 rounded-lg border px-3 py-1.5 text-[12px] font-medium transition-colors"
                style={{ borderColor:'var(--border)', color: rwDisabled ? 'var(--text-muted)' : 'var(--text-secondary)', opacity: rwDisabled ? 0.35 : 1, cursor: rwDisabled ? 'not-allowed' : 'pointer' }}>
                <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                </svg>
                多刷{(d.watchedCount ?? 0) > 1 ? ` ×${d.watchedCount}` : ''}
              </button>
              */}


            {/* Mark section */}
            <div className="space-y-3 pt-3" style={{borderTop:'1px solid var(--border)'}}>
              <p className="text-[11px] font-medium uppercase tracking-wider text-[color:var(--text-muted)]">标记</p>

              <div className="grid grid-cols-5 gap-1.5">
                {STATUSES.map(({ s, label, dot: dd }) => {
                  const active = d.status === s;
                  return (
                    <button key={s} onClick={() => mark(s)}
                      className="flex items-center justify-center gap-1 rounded-lg border px-1.5 py-1 text-[11px] font-medium transition-all"
                      style={{
                        borderColor: active ? 'var(--border-active)' : 'var(--border)',
                        color: active ? 'var(--amber)' : 'var(--text-secondary)',
                        background: active ? 'var(--amber-dim)' : 'transparent',
                      }}>
                      <span className={`dot ${dd}`} />{label}
                    </button>
                  );
                })}
              </div>
{/* FIXME: 保留
              <button onClick={rewatch} disabled={rwDisabled}
                className="flex items-center gap-1.5 rounded-lg border px-3 py-1.5 text-[12px] font-medium transition-colors"
                style={{ borderColor:'var(--border)', color: rwDisabled ? 'var(--text-muted)' : 'var(--text-secondary)', opacity: rwDisabled ? 0.35 : 1, cursor: rwDisabled ? 'not-allowed' : 'pointer' }}>
                <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                </svg>
                多刷{(d.watchedCount ?? 0) > 1 ? ` ×${d.watchedCount}` : ''}
              </button>
              */}

              {d.id != null && (<>
                <div>
                  <p className="mb-1.5 text-[11px] font-medium uppercase tracking-wider text-[color:var(--text-muted)]">评分</p>
                  <div className="flex items-center gap-3">
                    <StarRating value={rating} onChange={setRating} size={24} />
                    <span className="text-[13px] text-[color:var(--text-muted)]">{rating != null ? `${rating} / 10` : '未评分'}</span>
                  </div>
                </div>
                <div>
                  <p className="mb-1.5 text-[11px] font-medium uppercase tracking-wider text-[color:var(--text-muted)]">评价</p>
                  <textarea value={review} onChange={e => setReview(e.target.value)} rows={2}
                    className="dark-textarea" placeholder="写下你的感想..." />
                </div>
                <div className="flex items-center justify-end gap-2">
                  <button onClick={unmark} className="btn-ghost" style={{height:32,fontSize:12,padding:'0 12px'}}>删除记录</button>
                  <button onClick={saveReview} disabled={saving} className="btn-primary" style={{height:32,fontSize:12,padding:'0 14px'}}>
                    {saving ? '保存中...' : '保存'}
                  </button>
                </div>
              </>)}
            </div>
          </>)}
        </div>
      </div>
    </div>
  );
}
