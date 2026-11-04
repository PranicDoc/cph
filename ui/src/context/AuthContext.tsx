import React, {
  createContext,
  useContext,
  useState,
  useEffect,
  ReactNode,
} from "react";
import api, {
  SESSION_EXPIRED_EVENT,
  isRefreshDue,
  refreshSession,
} from "../lib/axios";
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

const USER_STORAGE_KEY = "user_profile_data";
// How often to check whether a proactive refresh is due. The refresh itself is gated on
// isRefreshDue() (token older than 45 min), so this only needs to be well under that.
const REFRESH_CHECK_INTERVAL = 60 * 1000;

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  const [user, setUserState] = useState<UserInfo | null>(() => {
    const stored = localStorage.getItem(USER_STORAGE_KEY);
    return stored ? JSON.parse(stored) : null;
  });

  const setUser = (newUser: UserInfo | null) => {
    setUserState(newUser);
    if (newUser) {
      localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(newUser));
    } else {
      localStorage.removeItem(USER_STORAGE_KEY);
    }
  };

  useEffect(() => {
    const init = async () => {
      try {
        // A 401 here is refreshed and retried by the axios interceptor, so a returning user
        // with an expired access token but a live refresh token stays signed in.
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

  // The refresh token was rejected: the session is really over, so drop local state and let
  // the route guards send the user to sign in. No logout call, the cookies are already dead.
  useEffect(() => {
    const onExpired = () => {
      setIsAuthenticated(false);
      setUser(null);
    };
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired);
  }, []);

  // Proactive refresh, independent of user activity: keeps the access token fresh while the
  // tab is open (a healer filling a long form makes no API calls), and catches up straight
  // away when a backgrounded tab (whose timers are throttled) becomes visible again.
  useEffect(() => {
    if (!isAuthenticated) return;

    const refreshIfDue = () => {
      if (isRefreshDue()) refreshSession().catch(() => {});
    };

    refreshIfDue();
    const interval = setInterval(refreshIfDue, REFRESH_CHECK_INTERVAL);
    const onVisible = () => {
      if (document.visibilityState === "visible") refreshIfDue();
    };
    document.addEventListener("visibilitychange", onVisible);
    window.addEventListener("online", refreshIfDue);

    return () => {
      clearInterval(interval);
      document.removeEventListener("visibilitychange", onVisible);
      window.removeEventListener("online", refreshIfDue);
    };
  }, [isAuthenticated]);

  const ensureValidSession = async () => {
    try {
      await refreshSession();
    } catch {
      throw new Error("Session expired");
    }
  };

  // The axios client already refreshes and retries on 401; this is the same behaviour for
  // callers that need a raw fetch Response.
  const fetchWithAuth = async (
    url: string,
    options: RequestInit = {}
  ): Promise<Response> => {
    const res = await fetch(url, { ...options, credentials: "include" });
    if (res.status !== 401) return res;

    await refreshSession();
    return fetch(url, { ...options, credentials: "include" });
  };

  const login = (userData: UserInfo) => {
    setIsAuthenticated(true);
    setUser(userData);
  };

  const logout = async () => {
    try {
      await api.post("/api/v1/auth/logout");
    } finally {
      setIsAuthenticated(false);
      setUser(null);
    }
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
