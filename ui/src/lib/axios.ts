import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';

const api = axios.create({
  baseURL: '/cph',
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
});

/** Fired on window when the refresh token is rejected and the user must sign in again. */
export const SESSION_EXPIRED_EVENT = 'auth:session-expired';

// The access token lasts 1 hour server-side; refresh well before that.
export const REFRESH_AFTER_MS = 45 * 60 * 1000;

// Auth endpoints must never trigger a refresh-and-retry (a 401 there is a real answer, and
// retrying /refresh through itself would loop).
const NO_REFRESH_URLS = [
  '/api/v1/auth/login',
  '/api/v1/auth/refresh',
  '/api/v1/auth/logout',
  '/api/v1/auth/register',
  '/api/v1/auth/forgot-password',
  '/api/v1/auth/reset-password',
];

let refreshInFlight: Promise<void> | null = null;
let lastRefreshAt = 0;

/**
 * Exchange the refresh-token cookie for a new access token. Concurrent callers share one
 * request. Resolves when the new cookies are set; rejects (and announces session expiry)
 * only when the server rejects the refresh token itself.
 */
export function refreshSession(): Promise<void> {
  if (refreshInFlight) return refreshInFlight;

  refreshInFlight = api
    .post('/api/v1/auth/refresh', undefined, { skipAuthRefresh: true })
    .then(() => {
      lastRefreshAt = Date.now();
    })
    .catch((error: AxiosError) => {
      const status = error.response?.status;
      // 400/401/403: the refresh token is gone or expired (the server answers 400 for a missing
      // or invalid one). Anything else (network blip, 5xx) is transient, so keep the session
      // and let the next call retry.
      if (status === 400 || status === 401 || status === 403) {
        window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
      }
      throw error;
    })
    .finally(() => {
      refreshInFlight = null;
    });

  return refreshInFlight;
}

/** True when the last successful refresh is older than REFRESH_AFTER_MS (or never happened). */
export function isRefreshDue(now: number = Date.now()): boolean {
  return now - lastRefreshAt >= REFRESH_AFTER_MS;
}

/** Test hook: reset module state. */
export function __resetRefreshStateForTests(): void {
  refreshInFlight = null;
  lastRefreshAt = 0;
}

declare module 'axios' {
  interface AxiosRequestConfig {
    /** Do not refresh-and-retry this request on a 401. */
    skipAuthRefresh?: boolean;
    _retried?: boolean;
  }
}

function isNoRefreshUrl(url: string | undefined): boolean {
  if (!url) return false;
  return NO_REFRESH_URLS.some((u) => url.includes(u));
}

api.interceptors.request.use(
  (config) => config,
  (error) => Promise.reject(error)
);

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const config = error.config as InternalAxiosRequestConfig | undefined;

    if (
      error.response?.status === 401 &&
      config &&
      !config._retried &&
      !config.skipAuthRefresh &&
      !isNoRefreshUrl(config.url)
    ) {
      config._retried = true;
      try {
        await refreshSession();
      } catch {
        return Promise.reject(error);
      }
      return api(config);
    }

    console.error('API Error:', error.response?.data || error.message);
    return Promise.reject(error);
  }
);

export default api;
