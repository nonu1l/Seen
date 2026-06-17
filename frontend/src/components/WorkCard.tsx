import type { Status, WorkListItemDTO, WorkSearchResultDTO } from '../api/types';
import { Cover } from './Cover';
import { QuickMarkMenu } from './QuickMarkMenu';
import { STATUS_META } from '../utils/statusMeta';

interface Props {
  data: WorkListItemDTO | WorkSearchResultDTO;
  unmarked?: boolean;
  onOpen: () => void;
  onQuickMark: (status: Status) => void;
  index?: number;
}

export function WorkCard({ data, unmarked, onOpen, onQuickMark, index = 0 }: Props) {
  const status: Status | null = unmarked ? null : ((data as WorkListItemDTO).status ?? null);
  const score = data.score != null && data.score > 0 ? data.score.toFixed(1) : null;

  return (
    <div
      className="anim-in group cursor-pointer"
      style={{ animationDelay: `${index * 0.04}s` }}
      onClick={onOpen}
      role="button" tabIndex={0}
      onKeyDown={e => { if (e.key === 'Enter') onOpen(); }}
    >
      {/* Cover */}
      <div className="relative aspect-[2/3] rounded-[6px] overflow-hidden cover-gradient-bottom"
        style={{ border: '1px solid rgba(255,255,255,0.08)' }}>
        <Cover src={data.coverUrl} alt={data.nameCn} />
        {score && <span className="cover-score">{score}</span>}
        {data.platform && <span className="cover-platform">{data.platform}</span>}
      </div>

      {/* Info */}
      <div className="mt-2">
        <h3 className="truncate text-[13px] font-medium leading-snug tracking-[-0.01em] text-[color:var(--text-primary)] group-hover:text-[color:var(--accent)] transition-colors duration-200">
          {data.nameCn}
        </h3>
        <div className="flex items-baseline justify-between mt-0.5">
          <span className="text-[11px] text-[color:var(--text-muted)] flex-shrink-0 tracking-[0.01em]">
            {data.year ?? ''}
          </span>
          <span onClick={e => e.stopPropagation()} className="flex-shrink-0">
            <QuickMarkMenu
              current={status}
              onSelect={onQuickMark}
              trigger={
                status ? (
                  <span
                    className="text-[11px] leading-snug cursor-pointer select-none transition-all duration-150 hover:opacity-80"
                    style={{ color: STATUS_META[status].color }}
                  >
                    {STATUS_META[status].label}
                  </span>
                ) : (
                  <span className="unmarked-dots cursor-pointer select-none">
                    <span className="inline-block w-[3px] h-[3px] rounded-full bg-current align-middle" />
                    <span className="inline-block w-[3px] h-[3px] rounded-full bg-current align-middle ml-[3px]" />
                    <span className="inline-block w-[3px] h-[3px] rounded-full bg-current align-middle ml-[3px]" />
                  </span>
                )
              }
            />
          </span>
        </div>
      </div>
    </div>
  );
}
