import { describe, it, expect, beforeEach, vi } from 'vitest';
import type { AxiosAdapter, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import api, {
  refreshSession,
  isRefreshDue,
  SESSION_EXPIRED_EVENT,
  REFRESH_AFTER_MS,
  __resetRefreshStateForTests,
} from './axios';

type Handler = (cfg: InternalAxiosRequestConfig) => { status: number; data?: unknown };

function respond(cfg: InternalAxiosRequestConfig, status: number, data: unknown = {}) {
  const res = { data, status, statusText: '', headers: {}, config: cfg } as AxiosResponse;
  if (status >= 200 && status < 300) return Promise.resolve(res);
  return Promise.reject(Object.assign(new Error(`HTTP ${status}`), {
    isAxiosError: true, config: cfg, response: res,
  }));
}

function install(handler: Handler) {
  const calls: string[] = [];
  const adapter: AxiosAdapter = (cfg) => {
    calls.push(`${cfg.method?.toUpperCase()} ${cfg.url}`);
    const { status, data } = handler(cfg);
    return respond(cfg, status, data);
  };
  api.defaults.adapter = adapter;
  return calls;
}

describe('axios auth refresh', () => {
  beforeEach(() => {
    __resetRefreshStateForTests();
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  it('refreshes once and retries the original request after a 401', async () => {
    let tokenFresh = false;
    const calls = install((cfg) => {
      if (cfg.url === '/api/v1/auth/refresh') { tokenFresh = true; return { status: 200 }; }
      return tokenFresh ? { status: 200, data: 'saved' } : { status: 401 };
    });

    const res = await api.post('/api/v1/cases/1/protocol-understanding', { a: 1 });

    expect(res.data).toBe('saved');
    expect(calls).toEqual([
      'POST /api/v1/cases/1/protocol-understanding',
      'POST /api/v1/auth/refresh',
      'POST /api/v1/cases/1/protocol-understanding',
    ]);
  });

  it('shares one refresh across concurrent 401s', async () => {
    let tokenFresh = false;
    const calls = install((cfg) => {
      if (cfg.url === '/api/v1/auth/refresh') { tokenFresh = true; return { status: 200 }; }
      return tokenFresh ? { status: 200 } : { status: 401 };
    });

    await Promise.all([api.get('/a'), api.get('/b'), api.get('/c')]);

    expect(calls.filter((c) => c.includes('auth/refresh'))).toHaveLength(1);
  });

  it('does not loop when the retry is still 401', async () => {
    const calls = install((cfg) =>
      cfg.url === '/api/v1/auth/refresh' ? { status: 200 } : { status: 401 });

    await expect(api.get('/a')).rejects.toMatchObject({ response: { status: 401 } });
    expect(calls).toEqual(['GET /a', 'POST /api/v1/auth/refresh', 'GET /a']);
  });

  it('announces session expiry and rejects when the refresh token is rejected', async () => {
    install((cfg) => (cfg.url === '/api/v1/auth/refresh' ? { status: 401 } : { status: 401 }));
    const onExpired = vi.fn();
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired);

    await expect(api.get('/a')).rejects.toMatchObject({ response: { status: 401 } });
    expect(onExpired).toHaveBeenCalledTimes(1);
    window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired);
  });

  it('keeps the session on a transient refresh failure', async () => {
    install((cfg) => (cfg.url === '/api/v1/auth/refresh' ? { status: 503 } : { status: 401 }));
    const onExpired = vi.fn();
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired);

    await expect(api.get('/a')).rejects.toBeDefined();
    expect(onExpired).not.toHaveBeenCalled();
    window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired);
  });

  it('does not refresh for a failed login', async () => {
    const calls = install(() => ({ status: 401 }));

    await expect(api.post('/api/v1/auth/login', {})).rejects.toBeDefined();
    expect(calls).toEqual(['POST /api/v1/auth/login']);
  });

  it('reports a refresh as due until one succeeds, then not due until 45 minutes later', async () => {
    install(() => ({ status: 200 }));
    expect(isRefreshDue()).toBe(true);

    await refreshSession();
    expect(isRefreshDue()).toBe(false);
    expect(isRefreshDue(Date.now() + REFRESH_AFTER_MS + 1000)).toBe(true);
  });
});
