import React, {
  createContext,
  useContext,
  useState,
  useEffect,
  useRef,
  ReactNode,
} from "react";
import { isAxiosError } from "axios";
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
const PROFILE_RETRY_INTERVAL = 3 * 1000;

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

  // A 403 means this session may not have a profile at all (a patient link's cookie also passes
  // /auth/me), so it is final. Any other failure can be retried.
  const profileDenied = useRef(false);
  const loadProfile = async () => {
    try {
      const res = await api.get("/api/v1/auth/profile");
      setUser(res.data);
    } catch (error) {
      if (isAxiosError(error) && error.response?.status === 403) profileDenied.current = true;
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
          // The cookies outlive the saved user, so a valid session can arrive without one.
          // Load it before the app opens: the permissions, tabs and profile menu all need it.
          if (!user?.id) await loadProfile();
          setIsAuthenticated(true);
        } else {
          setIsAuthenticated(false);
        }
      } catch (error) {
        // Not signed in right now, but only a rejected session may forget the user: an outage
        // or network drop says nothing about the session. A rejected refresh token clears the
        // user through SESSION_EXPIRED_EVENT below.
        setIsAuthenticated(false);
        if (isAxiosError(error) && error.response?.status === 403) setUser(null);
      } finally {
        setIsLoading(false);
      }
    };

    init();
  }, []);

  // Signed in without a user means the profile request above failed. Keep trying, so a brief
  // outage doesn't leave the healer without tabs or a way to log out until they reload.
  useEffect(() => {
    if (!isAuthenticated || user?.id) return;
    const interval = setInterval(() => {
      if (profileDenied.current) clearInterval(interval);
      else loadProfile();
    }, PROFILE_RETRY_INTERVAL);
    return () => clearInterval(interval);
  }, [isAuthenticated, user?.id]);

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
