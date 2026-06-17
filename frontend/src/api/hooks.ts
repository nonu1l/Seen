import { useState, useEffect, useRef, useCallback } from 'react';
import { api } from './client';
import type { WorkListItemDTO, SearchDTO } from './types';

/**
 * 首页数据 hook：
 *  - 搜索框为空 → 返回已标记作品列表（api.listWorks）
 *  - 搜索框非空 → debounce 搜索本地 + Bangumi（api.searchAll）
 *
 * query 变化自动触发重新查询。
 */
export function useHomeList() {
  const [query, setQuery] = useState('');
  const [marked, setMarked] = useState<WorkListItemDTO[]>([]);
  const [search, setSearch] = useState<SearchDTO | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout>>();
  const lastQueryRef = useRef<string>('');

  const refreshMarked = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const list = await api.listWorks();
      setMarked(list);
    } catch (e: any) {
      setError(e?.message || 'load failed');
    } finally {
      setLoading(false);
    }
  }, []);

  /** 用当前 query 立即重新搜索（标记后保持搜索状态用） */
  const refreshSearch = useCallback(async () => {
    const q = lastQueryRef.current;
    if (!q) return;
    setLoading(true);
    setError(null);
    try {
      const r = await api.searchAll(q);
      setSearch(r);
    } catch (e: any) {
      setError(e?.message || 'search failed');
    } finally {
      setLoading(false);
    }
  }, []);

  // Initial load + when query is cleared
  useEffect(() => {
    const q = query.trim();
    lastQueryRef.current = q;

    if (!q) {
      setSearch(null);
      refreshMarked();
      return;
    }

    /** 输入防抖处理 */
    if (debounceRef.current) clearTimeout(debounceRef.current);
    setLoading(true);
    setError(null);

    debounceRef.current = setTimeout(async () => {
      try {
        const r = await api.searchAll(q);
        setSearch(r);
      } catch (e: any) {
        setError(e?.message || 'search failed');
      } finally {
        setLoading(false);
      }
    }, 250);

    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [query, refreshMarked]);

  return {
    query,
    setQuery,
    marked,
    search,
    loading,
    error,
    refresh: refreshMarked,
    refreshSearch
  };
}
