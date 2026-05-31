let _proxy = '';

export function setBangumiProxy(url: string) {
  _proxy = url || '';
  if (_proxy) localStorage.setItem('bgm_proxy', _proxy);
}

export function getBangumiProxy(): string {
  if (!_proxy) _proxy = localStorage.getItem('bgm_proxy') || '';
  return _proxy;
}

export function rewriteBangumiUrl(src: string | null | undefined): string | undefined {
  if (!src) return undefined;
  const proxy = getBangumiProxy();
  if (proxy && src.includes('lain.bgm.tv')) {
    return src.replace('https://lain.bgm.tv', proxy.endsWith('/') ? proxy.slice(0, -1) : proxy);
  }
  return src;
}
