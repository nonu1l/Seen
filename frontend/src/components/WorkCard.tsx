import type { Status, WorkListItem, WorkSearchResult } from '../api/types';
import { Cover } from './Cover';
import { QuickMarkMenu } from './QuickMarkMenu';

interface Props {
  data: WorkListItem | WorkSearchResult;
  unmarked?: boolean;
  onOpen: () => void;
  onQuickMark: (status: Status) => void;
  index?: number;
}

const LABEL: Record<Status, string> = { wish: '想看', doing: '在看', collect: '看过', on_hold: '搁置', dropped: '抛弃' };

export function WorkCard({ data, unmarked, onOpen, onQuickMark, index = 0 }: Props) {
  const status: Status | null = unmarked ? null : ((data as WorkListItem).status ?? null);
  const rewatched = !unmarked && (data as WorkListItem).rewatched;

  return (
    <div
      className="anim-in group flex gap-3 p-3 rounded-lg cursor-pointer transition-colors hover:bg-[var(--bg-card-hover)]"
      style={{
        background: 'var(--bg-card)',
        border: '1px solid ' + (status ? 'var(--border-active)' : 'var(--border)'),
        animationDelay: `${index * 0.04}s`,
      }}
      onClick={onOpen}
      role="button" tabIndex={0}
      onKeyDown={e => { if (e.key === 'Enter') onOpen(); }}
    >
      <div className="flex-shrink-0 self-start w-[72px] sm:w-[76px] aspect-[2/3] rounded-md overflow-hidden" style={{background:'var(--bg-card)'}}>
        <Cover src={data.coverUrl} alt={data.nameCn} />
      </div>

      <div className="min-w-0 flex-1 flex flex-col gap-1">
        <div className="flex items-baseline gap-2 min-w-0">
          <h3 className="truncate text-[14px] font-semibold leading-snug text-[color:var(--text-primary)]">
            {data.nameCn}
          </h3>
          {data.year && <span className="text-[11px] text-[color:var(--text-muted)] flex-shrink-0">{data.year}</span>}
        </div>

        <div className="flex flex-wrap items-center gap-1">
          {data.platform && <span className="text-[11px] text-[color:var(--text-muted)]">{data.platform}</span>}
          {data.score != null && data.score > 0 && <span className="badge badge-accent">{data.score.toFixed(1)}</span>}
          {data.tags?.slice(0, 2).map(t => <span key={t} className="badge badge-muted">{t}</span>)}
        </div>

        {data.plot && (
          <p className="line-clamp-2 text-[12px] text-[color:var(--text-secondary)] leading-relaxed">{data.plot}</p>
        )}

        <div className="mt-auto flex items-center justify-between gap-2 pt-1">
          <div className="flex items-center gap-1.5">
            {status && (
              <span className={`status-badge ${status === 'wish' ? 'badge-amber' : status === 'doing' ? 'badge-green' : status === 'collect' ? 'badge-blue' : 'badge-gray'}`}>
                <span className={`dot ${status === 'wish' ? 'dot-amber' : status === 'doing' ? 'dot-green' : status === 'collect' ? 'dot-blue' : 'dot-gray'}`} />
                {LABEL[status]}
              </span>
            )}
            {rewatched && <span className="rewatch-pill">多刷</span>}
          </div>

          <div onClick={e => e.stopPropagation()}>
            <QuickMarkMenu current={status} onSelect={onQuickMark} />
          </div>
        </div>
      </div>
    </div>
  );
}
