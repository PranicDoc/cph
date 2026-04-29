import React, {
  createContext,
  useContext,
  useState,
  useEffect,
  ReactNode,
} from 'react';
import api from '../lib/axios';

interface AuthContextType {
  isAuthenticated: boolean;
  isLoading: boolean;
  login: () => void;
  logout: () => void;
  ensureValidSession: () => Promise<void>;
  fetchWithAuth: (url: string, options?: RequestInit) => Promise<Response>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

// ---- CONFIG ----
const ACTIVITY_TIMEOUT = 15 * 60 * 1000; // 15 min
const REFRESH_INTERVAL = 50 * 60 * 1000; // ~50 min (since TTL = 1hr)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [lastActivity, setLastActivity] = useState(Date.now());

  let isRefreshing = false;
  let queue: any[] = [];
  let lastRefreshTime = 0;

  // ---- REFRESH ----
  const refreshToken = async () => {
    const now = Date.now();

    // prevent spam
    if (isRefreshing || now - lastRefreshTime < 60 * 1000) return;

    try {
      isRefreshing = true;
      lastRefreshTime = now;

      const res = await api.post('/api/v1/auth/refresh');

      if (res.status !== 200) throw new Error('Refresh failed');

      setIsAuthenticated(true);
      processQueue(null);
    } catch (err) {
      processQueue(err);
      logout();
      throw err;
    } finally {
      isRefreshing = false;
    }
  };

  const processQueue = (error: any) => {
    queue.forEach((p) =>
      error ? p.reject(error) : p.resolve()
    );
    queue = [];
  };

  // ---- INITIAL AUTH CHECK ----
  useEffect(() => {
    const init = async () => {
      try {
        const res = await api.get('/api/v1/auth/me', {
          withCredentials: true,
        });

        setIsAuthenticated(res.status === 200);
      } catch {
        setIsAuthenticated(false);
      } finally {
        setIsLoading(false);
      }
    };

    init();
  }, []);

  // ---- ACTIVITY TRACKING + EVENT REFRESH ----
  useEffect(() => {
    let debounce: any;

    const handleActivity = () => {
      const now = Date.now();
      const wasInactive = now - lastActivity > ACTIVITY_TIMEOUT;

      setLastActivity(now);

      // user came back after long time → try refresh once
      if (wasInactive) {
        clearTimeout(debounce);
        debounce = setTimeout(() => {
          refreshToken().catch(() => {});
        }, 1000);
      }
    };

    window.addEventListener('click', handleActivity);
    window.addEventListener('keydown', handleActivity);
    window.addEventListener('mousemove', handleActivity);

    return () => {
      window.removeEventListener('click', handleActivity);
      window.removeEventListener('keydown', handleActivity);
      window.removeEventListener('mousemove', handleActivity);
    };
  }, [lastActivity]);

  // ---- PROACTIVE REFRESH (APPROXIMATION) ----
  useEffect(() => {
    const interval = setInterval(() => {
      const now = Date.now();
      const isActive = now - lastActivity < ACTIVITY_TIMEOUT;

      if (isActive) {
        refreshToken().catch(() => {});
      }
    }, REFRESH_INTERVAL);

    return () => clearInterval(interval);
  }, [lastActivity]);

  // ---- PRE-FLIGHT (for forms, payments etc.) ----
  const ensureValidSession = async () => {
    try {
      await refreshToken();
    } catch {
      logout();
      throw new Error('Session expired');
    }
  };

  // ---- REACTIVE FETCH ----
  const fetchWithAuth = async (
    url: string,
    options: RequestInit = {}
  ): Promise<Response> => {
    let res = await fetch(url, {
      ...options,
      credentials: 'include',
    });

    if (res.status !== 401) return res;

    if (!isRefreshing) {
      await refreshToken();
    }

    return new Promise((resolve, reject) => {
      queue.push({
        resolve: async () => {
          const retry = await fetch(url, {
            ...options,
            credentials: 'include',
          });
          resolve(retry);
        },
        reject,
      });
    });
  };

  // ---- AUTH ACTIONS ----
  const login = () => setIsAuthenticated(true);

  const logout = () => {
    setIsAuthenticated(false);
    window.location.href = '/login';
  };

  return (
    <AuthContext.Provider
      value={{
        isAuthenticated,
        isLoading,
        login,
        logout,
        ensureValidSession,
        fetchWithAuth,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

// ---- HOOK ----
export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
}