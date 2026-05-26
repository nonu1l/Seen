import type { Status } from '../api/types';

export const STATUS_META: Record<Status, { label: string; badge: string; dot: string }> = {
  wish:    { label: '想看', badge: 'badge-amber', dot: 'dot-amber' },
  doing:   { label: '在看', badge: 'badge-green', dot: 'dot-green' },
  collect: { label: '看过', badge: 'badge-blue',  dot: 'dot-blue'  },
  on_hold: { label: '搁置', badge: 'badge-gray',  dot: 'dot-gray'  },
  dropped: { label: '抛弃', badge: 'badge-gray',  dot: 'dot-gray'  }
};

export function StatusBadge({ status }: { status: Status | null | undefined }) {
  if (!status) return null;
  const m = STATUS_META[status];
  return <span className={`badge ${m.badge}`}>{m.label}</span>;
}
