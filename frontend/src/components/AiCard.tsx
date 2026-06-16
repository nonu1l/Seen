import { useState } from 'react';
import type { ConversationCardVO, Status } from '../api/types';
import { Cover } from './Cover';
import { STATUS_OPTIONS, statusLabel } from '../utils/statusMeta';

interface Props {
  card: ConversationCardVO;
  onSave: (id: number, rating: number | null, review: string | null, status: string | null) => void;
  onUndo: (id: number) => void;
}

export function AiCard({ card, onSave, onUndo }: Props) {
  const saved = card.cardState === 'SAVED';
  const editable = card.cardState === 'EDITABLE';
  const conflict = card.cardState === 'CONFLICT';
  const unmarked = card.cardState === 'UNMARKED';
  const restored = card.cardState === 'RESTORED';

  const [rating, setRating] = useState<number | null>(() => card.rating ? Math.round(card.rating / 2) : null);
  const [status, setStatus] = useState<Status | null>(card.status ?? null);
  const [review, setReview] = useState(card.review ?? '');
  const [saving, setSaving] = useState(false);
  const [plotExpanded, setPlotExpanded] = useState(false);

  const handleSave = async () => {
    setSaving(true);
    try {
      const tenScale = rating != null ? rating * 2 : null;
      await onSave(card.id, tenScale, review || null, status);
    } catch {
      // ignore
    } finally {
      setSaving(false);
    }
  };

  const readOnly = saved && !editable;

  // ── 冲突 UI ──
  if (conflict) {
    return (
      <div className="flex flex-col gap-2 rounded-lg p-3" style={{
        background: 'var(--bg-card)', border: '1px solid #f59e0b',
      }}>
        <div className="flex items-center gap-1.5 text-[12px] font-medium" style={{ color: '#f59e0b' }}>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}><path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" /><line x1="12" y1="9" x2="12" y2="13" /><line x1="12" y1="17" x2="12.01" y2="17" /></svg>
          与本地记录冲突
        </div>
        <div className="flex gap-3">
          <div className="flex-shrink-0 self-start overflow-hidden rounded-md" style={{ width: 64, aspectRatio: '2/3', background: 'var(--bg-card)' }}>
            <Cover src={card.coverUrl} alt={card.nameCn} />
          </div>
          <div className="flex-1 text-[12px]" style={{ color: 'var(--text-secondary)' }}>
            <p className="font-semibold" style={{ color: 'var(--text-primary)' }}>{card.nameCn}</p>
            <p className="mt-0.5">本次输入：{status ? statusLabel(status) : '未指定'} · {rating ? rating + '星' : '未评分'}</p>
          </div>
        </div>
        <div className="flex justify-end gap-2">
          <button type="button" onClick={() => onUndo(card.id)}
            className="rounded-md px-3 py-1 text-[12px]" style={{ color: 'var(--text-muted)', border: '1px solid var(--border)' }}>
            放弃本次
          </button>
          <button type="button" onClick={handleSave} disabled={saving}
            className="rounded-md px-3 py-1 text-[12px] font-medium text-white" style={{ background: 'var(--accent)' }}>
            {saving ? '...' : '覆盖'}
          </button>
        </div>
      </div>
    );
  }

  // ── 已取消 / 已撤回卡片 ──
  if (unmarked || restored) {
    return (
      <div className="flex flex-col gap-2 rounded-lg p-3" style={{
        background: 'var(--bg-card)', border: '1px solid var(--border)', opacity: 0.7,
      }}>
        <div className="flex gap-3">
          <div className="flex-shrink-0 self-start overflow-hidden rounded-md" style={{ width: 64, aspectRatio: '2/3', background: 'var(--bg-card)' }}>
            <Cover src={card.coverUrl} alt={card.nameCn} />
          </div>
          <div className="flex-1 min-w-0 text-[12px]" style={{ color: 'var(--text-secondary)' }}>
            <p className="font-semibold truncate" style={{ color: 'var(--text-primary)' }}>{card.nameCn}</p>
            <p className="mt-0.5">
              已取消标记
              {card.status && <> · {statusLabel(card.status)}</>}
              {card.rating != null && <> · {card.rating}分</>}
              {card.review && <> · 「{card.review}」</>}
            </p>
          </div>
        </div>
        <div className="flex justify-end">
          {unmarked ? (
            <button type="button" onClick={() => onSave(card.id, card.rating, card.review, card.status)}
              className="rounded-md px-3 py-1 text-[12px] transition-colors hover:opacity-80"
              style={{ color: 'var(--accent)', border: '1px solid var(--accent)', background: 'transparent' }}>
              撤回
            </button>
          ) : (
            <span className="text-[11px]" style={{ color: 'var(--text-muted)' }}>已撤回</span>
          )}
        </div>
      </div>
    );
  }

  // ── 普通 / 已保存 / 可编辑卡片（PENDING / EDITABLE / SAVED）──
  return (
    <div className="flex flex-col gap-2 rounded-lg p-3" style={{
      background: 'var(--bg-card)', border: '1px solid var(--border)',
    }}>
      {/* ── 卡片头：片名 + 年份 ── */}
      <div className="flex items-baseline gap-2" style={{ borderBottom: '1px solid var(--border)', paddingBottom: 8 }}>
        <h4 className="truncate text-[14px] font-semibold" style={{ color: 'var(--text-primary)' }}>{card.nameCn}</h4>
        {card.year && <span className="flex-shrink-0 text-[11px]" style={{ color: 'var(--text-muted)' }}>{card.year}</span>}
      </div>

      {/* ── 上区：封面 + 元数据 ── */}
      <div className="flex gap-3">
        <div className="flex-shrink-0 self-start overflow-hidden rounded-md relative aspect-[2/3] cover-gradient-bottom"
          style={{ width: 88, border: '1px solid rgba(255,255,255,0.08)' }}>
          <Cover src={card.coverUrl} alt={card.nameCn} />
          {card.platform && <span className="cover-platform" style={{ fontSize: 10, padding: '0 5px', bottom: 6, right: 6 }}>{card.platform}</span>}
        </div>

        <div className="flex min-w-0 flex-1 flex-col gap-1">
          {card.tags && card.tags.length > 0 && (
            <div className="flex flex-wrap gap-1">
              {card.tags.slice(0, 4).map(t => <span key={t} className="badge badge-muted">{t}</span>)}
            </div>
          )}
          {card.plot && (
            <div>
              <p className={`text-[12px] leading-relaxed ${plotExpanded ? '' : 'line-clamp-3'}`}
                style={{ color: 'var(--text-secondary)', whiteSpace: 'pre-line' }}>
                {card.plot}
              </p>
              <button onClick={() => setPlotExpanded(!plotExpanded)}
                className="text-[11px] hover:opacity-70"
                style={{ color: 'var(--accent)', background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}>
                {plotExpanded ? '收起' : '展开'}
              </button>
            </div>
          )}
          <div className="flex items-center justify-between text-[12px]" style={{ color: 'var(--text-muted)' }}>
            {card.score != null && (
              <span>Bangumi 评分：<span style={{ color: 'var(--amber)', fontWeight: 600 }}>★ {card.score.toFixed(1)}</span></span>
            )}
            <a href={`https://bangumi.tv/subject/${card.subjectId}`}
              target="_blank" rel="noopener noreferrer"
              className="underline underline-offset-2 transition-opacity hover:opacity-70 flex-shrink-0"
              style={{ color: 'var(--text-muted)' }}>
              Bangumi &gt;
            </a>
          </div>
          {hasHistory(card) && (
            <div className="rounded-md px-2 py-1.5 text-[11px] leading-relaxed"
              style={{ background: 'rgba(245,158,11,0.06)', border: '1px solid rgba(245,158,11,0.15)' }}>
              <span style={{ color: 'var(--text-muted)' }}>之前：</span>
              <span style={{ color: 'var(--text-secondary)' }}>
                {formatHistory(card)}
              </span>
            </div>
          )}
        </div>
      </div>

      {/* ── 下区：我的标记 ── */}
      <div className="flex flex-col gap-2 pt-2" style={{ borderTop: '1px solid var(--border)' }}>
        {/* 我的评分 */}
        <div className="flex items-center gap-2">
          <span className="text-[11px] font-medium text-[color:var(--text-muted)] flex-shrink-0">评分</span>
          <span className="inline-flex gap-0.5" role="group">
          {[1, 2, 3, 4, 5].map(i => (
            <button key={i} type="button" disabled={readOnly}
              className={readOnly ? '' : 'cursor-pointer hover:scale-110 bg-transparent p-0 transition-colors'}
              style={{
                width: 22, height: 22, border: 0, background: 'transparent',
                color: rating != null && i <= rating ? 'var(--amber)' : 'rgba(255,255,255,0.12)',
              }}
              onClick={() => !readOnly && setRating(prev => (prev === i ? null : i))}>
              <svg width={22} height={22} viewBox="0 0 20 20" fill={rating != null && i <= rating ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth={1.2}>
                <path d="M10 1.5l2.6 5.3 5.9.9-4.3 4.2 1 5.9L10 15l-5.3 2.8 1-5.9L1.5 7.7l5.9-.9L10 1.5z" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </button>
          ))}
        </span>
        </div>

        {/* 状态 */}
        <div className="flex items-center gap-2">
          <span className="text-[11px] font-medium text-[color:var(--text-muted)] flex-shrink-0">标记</span>
          <span className="flex flex-wrap gap-1.5">
          {STATUS_OPTIONS.map(({ status: s, label, dot: dd }) => {
            const active = status === s;
            return (
              <button key={s} type="button" disabled={readOnly}
                onClick={() => !readOnly && setStatus(prev => (prev === s ? null : s))}
                className="flex items-center justify-center gap-1 rounded-lg border px-1.5 py-1 text-[11px] font-medium transition-all"
                style={{
                  borderColor: active ? 'var(--border-active)' : 'var(--border)',
                  color: active ? 'var(--amber)' : 'var(--text-secondary)',
                  background: active ? 'var(--amber-dim)' : 'transparent',
                  opacity: readOnly ? 0.7 : 1,
                }}>
                <span className={`dot ${dd}`} />{label}
              </button>
            );
          })}
        </span>
        </div>

        {/* 影评 */}
        <textarea value={review} onChange={e => !readOnly && setReview(e.target.value)}
          placeholder="添加影评..." rows={2} readOnly={readOnly}
          className="w-full resize-none rounded-md px-2.5 py-1.5 text-[12px] outline-none"
          style={{
            background: 'var(--bg-card)', color: 'var(--text-primary)',
            border: '1px solid var(--border)', opacity: readOnly ? 0.7 : 1,
          }}
          onInput={e => { const el = e.currentTarget; el.style.height = 'auto'; el.style.height = el.scrollHeight + 'px'; }}
        />

        {/* 按钮 */}
        <div className="flex justify-end gap-2">
          {saved && (
            <button type="button" onClick={() => onUndo(card.id)}
              className="rounded-md px-3 py-1 text-[12px] transition-colors hover:opacity-80"
              style={{ color: 'var(--text-muted)', border: '1px solid var(--border)' }}>
              撤销保存
            </button>
          )}
          {(editable || !saved) && (
            <button type="button" onClick={handleSave} disabled={saving}
              className="rounded-md px-4 py-1 text-[12px] font-medium transition-colors disabled:opacity-60"
              style={{ background: 'var(--accent)', color: '#fff' }}>
              {saving ? '...' : '保存'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

function hasHistory(card: ConversationCardVO) {
  return card.previousStatus != null || card.previousRating != null || (card.previousReview != null && card.previousReview.length > 0);
}

function formatHistory(card: ConversationCardVO) {
  const parts: string[] = [];
  if (card.previousStatus != null) {
    parts.push(statusLabel(card.previousStatus));
  }
  if (card.previousRating != null) {
    parts.push(card.previousRating + '分');
  }
  if (card.previousReview) {
    parts.push('"' + card.previousReview + '"');
  }
  return parts.join(' · ') || '已记录';
}
