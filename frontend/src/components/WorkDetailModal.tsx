import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { api } from '../api/client';
import type { Status, WorkDetailDTO } from '../api/types';
import { Cover } from './Cover';
import { StarRating } from './StarRating';
import { STATUS_OPTIONS } from '../utils/statusMeta';

interface Props { id: number; platform: string; onClose: (changed: boolean) => void; }

export function WorkDetailModal({ id, platform, onClose }: Props) {
  const [d, setD] = useState<WorkDetailDTO | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [rating, setRating] = useState<number | null>(null);
  const [review, setReview] = useState('');
  const [savedRating, setSavedRating] = useState<number | null>(null);
  const [savedReview, setSavedReview] = useState('');
  const [saving, setSaving] = useState(false);
  const [changed, setChanged] = useState(false);
  const changedRef = useRef(false);
  const saveQueueRef = useRef<Promise<void>>(Promise.resolve());
  const [cnNames, setCnNames] = useState<Record<number, string>>({});
  const [cnLoading, setCnLoading] = useState<Set<number>>(new Set());
  const [actorNames, setActorNames] = useState<Record<number, string>>({});
  const [plotExpanded, setPlotExpanded] = useState(false);

  useEffect(() => { document.body.style.overflow = 'hidden'; return () => { document.body.style.overflow = ''; }; }, []);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(changedRef.current || changed); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [changed, onClose]);

  const refresh = async () => {
    setError(null);
    setCnNames({});
    setCnLoading(new Set());
    setActorNames({});
    setPlotExpanded(false);
    try {
      const r = await api.getDetail(String(id), platform);
      const nextReview = r.myReview ?? '';
      setD(r);
      setRating(r.myRating);
      setReview(nextReview);
      setSavedRating(r.myRating);
      setSavedReview(nextReview);
    }
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
      changedRef.current = true;
      setChanged(true);
      // 仅更新标记相关状态，避免 refresh() 导致角色信息重新获取
      setD(prev => prev ? { ...prev, status, id: prev.id ?? updated.id } : prev);
      setRating(updated.myRating);
      setSavedRating(updated.myRating);
    } catch (e: any) { setError(e?.message || '标记失败'); }
  };

  const saveProgress = (nextRating: number | null, nextReview: string) => {
    if (!d?.id) return;
    const workId = d.id;
    const normalizedReview = nextReview.trim();
    setSaving(true);
    setError(null);
    changedRef.current = true;
    setChanged(true);

    const job = saveQueueRef.current.catch(() => undefined).then(async () => {
      const updated = await api.updateReview(workId, nextRating, normalizedReview || null);
      setSavedRating(updated.myRating);
      setSavedReview(updated.myReview ?? '');
    });
    saveQueueRef.current = job;
    job.catch((e: any) => setError(e?.message || '保存失败'))
      .finally(() => {
        if (saveQueueRef.current === job) {
          setSaving(false);
        }
      });
  };

  const handleRatingChange = (nextRating: number | null) => {
    setRating(nextRating);
    if (nextRating !== savedRating || review.trim() !== savedReview.trim()) {
      saveProgress(nextRating, review);
    }
  };

  const handleReviewBlur = () => {
    if (rating !== savedRating || review.trim() !== savedReview.trim()) {
      saveProgress(rating, review);
    }
  };

  const unmark = async () => {
    if (!d?.id) return;
    if (!window.confirm('确定要删除该记录吗？将同时删除作品信息和所有标记记录。')) return;
    try { await api.unmark(d.id); changedRef.current = true; setChanged(true); onClose(true); }
    catch (e: any) { setError(e?.message || '操作失败'); }
  };

  const pLabel = d?.platform || platform;

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center pt-[5vh] pb-[5vh] bg-black/75 overflow-y-auto"
         onClick={() => onClose(changedRef.current || changed)}>
      <div className="record-panel mobile-fullscreen w-full max-w-3xl my-auto sm:m-4 anim-in"
           onClick={e => e.stopPropagation()} style={{ maxHeight: '90vh', overflowY: 'auto' }}>

        {/* Header */}
        <div className="flex items-center justify-between px-4 py-3" style={{borderBottom:'1px solid var(--border)'}}>
          <div className="flex items-center gap-3 min-w-0">
            <h2 className="text-lg font-semibold text-[color:var(--text-primary)] truncate">{d?.nameCn || '加载中...'}</h2>
            {d && pLabel && <span className="badge badge-outline flex-shrink-0">{pLabel}</span>}
          </div>
          <div className="flex items-center gap-1">
            {d?.id != null && (
              <button
                onMouseDown={e => e.preventDefault()}
                onClick={unmark}
                className="inline-flex h-[30px] w-[30px] flex-shrink-0 items-center justify-center rounded-md text-[color:var(--text-muted)] transition-colors hover:bg-white/[0.04] hover:text-[color:var(--text-primary)]"
                aria-label="删除记录"
                title="删除记录">
                <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M6 7h12M9 7V5.5A1.5 1.5 0 0110.5 4h3A1.5 1.5 0 0115 5.5V7m-7 0l.7 12A2 2 0 0010.7 21h2.6a2 2 0 002-1.9L16 7M10 11v6M14 11v6" />
                </svg>
              </button>
            )}
            <button onClick={() => onClose(changedRef.current || changed)} className="btn-icon" aria-label="关闭">
              <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
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

            {/* Mark section */}
            <div className="space-y-3 pt-3" style={{borderTop:'1px solid var(--border)'}}>
              <p className="text-[11px] font-medium uppercase tracking-wider text-[color:var(--text-muted)]">标记</p>

              <div className="grid grid-cols-5 gap-1.5">
                {STATUS_OPTIONS.map(({ status: s, label, dot: dd }) => {
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
              {d.id != null && (<>
                <div>
                  <p className="mb-1.5 text-[11px] font-medium uppercase tracking-wider text-[color:var(--text-muted)]">评分</p>
                  <div className="flex items-center gap-3">
                    <StarRating value={rating} onChange={handleRatingChange} size={24} />
                    <span className="text-[13px] text-[color:var(--text-muted)]">{rating != null ? `${rating} / 10` : '未评分'}</span>
                    {saving && <span className="text-[12px] text-[color:var(--text-muted)]">保存中...</span>}
                  </div>
                </div>
                <div>
                  <p className="mb-1.5 text-[11px] font-medium uppercase tracking-wider text-[color:var(--text-muted)]">评价</p>
                  <textarea value={review} onChange={e => setReview(e.target.value)} onBlur={handleReviewBlur} rows={2}
                    className="dark-textarea" placeholder="写下你的感想..." />
                </div>
              </>)}
            </div>
          </>)}
        </div>
      </div>
    </div>
  );
}
