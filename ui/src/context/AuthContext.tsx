import React, {
  createContext,
  useContext,
  useState,
  useEffect,
  ReactNode,
} from "react";
import api from "../lib/axios";
import type { UserInfo } from "../components/UserProfile";

interface AuthContextType {
  isAuthenticated: boolean;
  isLoading: boolean;
  user: UserInfo | null;
  setUser: (user: UserInfo | null) => void;
  login: (userData: UserInfo) => void;
  logout: () => void;
  ensureValidSession: () => Promise<void>;
  fetchWithAuth: (url: string, options?: RequestInit) => Promise<Response>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

const ACTIVITY_TIMEOUT = 15 * 60 * 1000;
const REFRESH_INTERVAL = 50 * 60 * 1000;
const USER_STORAGE_KEY = "user_profile_data";

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  const [user, setUserState] = useState<UserInfo | null>(() => {
    const stored = localStorage.getItem(USER_STORAGE_KEY);
    return stored ? JSON.parse(stored) : null;
  });

  const [lastActivity, setLastActivity] = useState(Date.now());

  let isRefreshing = false;
  let queue: any[] = [];
  let lastRefreshTime = 0;

  const setUser = (newUser: UserInfo | null) => {
    setUserState(newUser);
    if (newUser) {
      localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(newUser));
    } else {
      localStorage.removeItem(USER_STORAGE_KEY);
    }
  };

  const refreshToken = async () => {
    const now = Date.now();
    if (isRefreshing || now - lastRefreshTime < 60 * 1000) return;

    try {
      isRefreshing = true;
      lastRefreshTime = now;

      const res = await api.post("/api/v1/auth/refresh");
      if (res.status !== 200) throw new Error("Refresh failed");

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
    queue.forEach((p) => (error ? p.reject(error) : p.resolve()));
    queue = [];
  };

  useEffect(() => {
    const init = async () => {
      try {
        const res = await api.get("/api/v1/auth/me", {
          withCredentials: true,
        });

        if (res.status === 200) {
          setIsAuthenticated(true);
        } else {
          setIsAuthenticated(false);
          setUser(null);
        }
      } catch {
        setIsAuthenticated(false);
        setUser(null);
      } finally {
        setIsLoading(false);
      }
    };

    init();
  }, []);

  useEffect(() => {
    let debounce: any;

    const handleActivity = () => {
      const now = Date.now();
      const wasInactive = now - lastActivity > ACTIVITY_TIMEOUT;

      setLastActivity(now);

      if (wasInactive) {
        clearTimeout(debounce);
        debounce = setTimeout(() => {
          refreshToken().catch(() => {});
        }, 1000);
      }
    };

    window.addEventListener("click", handleActivity);
    window.addEventListener("keydown", handleActivity);
    window.addEventListener("mousemove", handleActivity);

    return () => {
      window.removeEventListener("click", handleActivity);
      window.removeEventListener("keydown", handleActivity);
      window.removeEventListener("mousemove", handleActivity);
    };
  }, [lastActivity]);

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

  const ensureValidSession = async () => {
    try {
      await refreshToken();
    } catch {
      logout();
      throw new Error("Session expired");
    }
  };

  const fetchWithAuth = async (
    url: string,
    options: RequestInit = {}
  ): Promise<Response> => {
    let res = await fetch(url, {
      ...options,
      credentials: "include",
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
            credentials: "include",
          });
          resolve(retry);
        },
        reject,
      });
    });
  };

  const login = (userData: UserInfo) => {
    setIsAuthenticated(true);
    setUser(userData);
  };

  const logout = async () => {
    const res = await api.post("/api/v1/auth/logout");
    setIsAuthenticated(false);
    setUser(null);
  };

  return (
    <AuthContext.Provider
      value={{
        isAuthenticated,
        isLoading,
        user,
        setUser,
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

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return context;
}
