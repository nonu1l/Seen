import type { Status } from '../api/types';

export const STATUS_META: Record<Status, { label: string; dot: string; color: string }> = {
  wish: { label: '想看', dot: 'dot-amber', color: 'var(--amber)' },
  doing: { label: '在看', dot: 'dot-green', color: 'var(--green)' },
  collect: { label: '看过', dot: 'dot-blue', color: 'var(--blue)' },
  on_hold: { label: '搁置', dot: 'dot-gray', color: 'var(--gray-text)' },
  dropped: { label: '抛弃', dot: 'dot-gray', color: 'var(--gray-text)' },
};

export const STATUS_OPTIONS = [
  { status: 'wish', ...STATUS_META.wish },
  { status: 'doing', ...STATUS_META.doing },
  { status: 'collect', ...STATUS_META.collect },
  { status: 'on_hold', ...STATUS_META.on_hold },
  { status: 'dropped', ...STATUS_META.dropped },
] satisfies Array<{ status: Status; label: string; dot: string; color: string }>;

export const STATUS_FILTERS = [
  { key: 'all', label: '全部' },
  { key: 'collect', label: STATUS_META.collect.label },
  { key: 'doing', label: STATUS_META.doing.label },
  { key: 'wish', label: STATUS_META.wish.label },
  { key: 'on_hold', label: STATUS_META.on_hold.label },
  { key: 'dropped', label: STATUS_META.dropped.label },
] satisfies Array<{ key: Status | 'all'; label: string }>;

export function statusLabel(status: string | null | undefined) {
  return status && status in STATUS_META ? STATUS_META[status as Status].label : status ?? '';
}
