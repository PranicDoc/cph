import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import api from "../lib/axios";
import { extractDetail } from "../lib/utils";
import { useAuth } from "../context/AuthContext";
import {
  applyToDocument,
  fromServerValue,
  newViewEnabled,
  readTheme,
  readUiVersion,
  resolveTheme,
  toServerValue,
  writeTheme,
  writeUiVersion,
  type ResolvedTheme,
  type ThemeChoice,
  type UiVersion,
} from "./storage";

interface UiVersionContextValue {
  uiVersion: UiVersion;
  isNewView: boolean;
  /** False when the new view is switched off for this build (kill switch or not yet released). */
  canSwitch: boolean;
  setUiVersion: (next: UiVersion) => Promise<void>;
  theme: ThemeChoice;
  resolvedTheme: ResolvedTheme;
  setTheme: (next: ThemeChoice) => void;
}

const UiVersionContext = createContext<UiVersionContextValue | undefined>(undefined);

const PROFILE_KEY = ["healer-profile"];
const DARK_QUERY = "(prefers-color-scheme: dark)";

function systemPrefersDark(): boolean {
  return typeof window !== "undefined" && typeof window.matchMedia === "function"
    ? window.matchMedia(DARK_QUERY).matches
    : true;
}

/**
 * Holds the user's UI choice. The account is the source of truth (it follows the user to any
 * device); local storage only makes the first paint instant. The attributes it sets on <html>
 * are what the new-view styles key off, so classic visuals never change.
 */
export function UiVersionProvider({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth();
  const queryClient = useQueryClient();
  const enabled = newViewEnabled();

  const [uiVersion, setUiVersionState] = useState<UiVersion>(() => (enabled ? readUiVersion() : "classic"));
  const [theme, setThemeState] = useState<ThemeChoice>(readTheme);
  const [prefersDark, setPrefersDark] = useState<boolean>(systemPrefersDark);
  const current = useRef(uiVersion);
  current.current = uiVersion;
  // Once the user picks a view in this visit, a slower profile response must not undo it.
  const chosenThisVisit = useRef(false);

  const resolvedTheme = resolveTheme(theme, prefersDark);

  useEffect(() => {
    applyToDocument(document.documentElement, uiVersion, resolvedTheme);
  }, [uiVersion, resolvedTheme]);

  useEffect(() => {
    if (typeof window.matchMedia !== "function") return;
    const media = window.matchMedia(DARK_QUERY);
    const onChange = (e: MediaQueryListEvent) => setPrefersDark(e.matches);
    media.addEventListener("change", onChange);
    return () => media.removeEventListener("change", onChange);
  }, []);

  // The account wins over whatever this browser remembered.
  const profile = useQuery<{ uiVersion?: string }>({
    queryKey: PROFILE_KEY,
    queryFn: () => api.get("/api/v1/auth/profile").then((r) => r.data),
    enabled: enabled && isAuthenticated,
  });
  const serverValue = fromServerValue(profile.data?.uiVersion);
  useEffect(() => {
    if (!enabled || !serverValue || chosenThisVisit.current || serverValue === current.current) return;
    setUiVersionState(serverValue);
    writeUiVersion(serverValue);
  }, [enabled, serverValue]);

  const patchProfileCache = useCallback(
    (value: UiVersion) =>
      queryClient.setQueryData(PROFILE_KEY, (old: Record<string, unknown> | undefined) =>
        old ? { ...old, uiVersion: toServerValue(value) } : old
      ),
    [queryClient]
  );

  const setUiVersion = useCallback(
    async (next: UiVersion) => {
      if (!enabled) return;
      const previous = current.current;
      if (next === previous) return;
      chosenThisVisit.current = true;
      setUiVersionState(next);
      writeUiVersion(next);
      if (!isAuthenticated) return;
      // A profile fetch still in flight would bring back the old value and undo this choice.
      await queryClient.cancelQueries({ queryKey: PROFILE_KEY });
      patchProfileCache(next);
      try {
        await api.put("/api/v1/auth/ui-version", { uiVersion: toServerValue(next) });
      } catch (err) {
        setUiVersionState(previous);
        writeUiVersion(previous);
        patchProfileCache(previous);
        toast.error(extractDetail(err) || "Could not save your choice. Please try again.", { duration: 5000 });
      }
    },
    [enabled, isAuthenticated, patchProfileCache, queryClient]
  );

  const setTheme = useCallback((next: ThemeChoice) => {
    setThemeState(next);
    writeTheme(next);
  }, []);

  const value = useMemo<UiVersionContextValue>(
    () => ({
      uiVersion,
      isNewView: uiVersion === "new",
      canSwitch: enabled,
      setUiVersion,
      theme,
      resolvedTheme,
      setTheme,
    }),
    [uiVersion, enabled, setUiVersion, theme, resolvedTheme, setTheme]
  );

  return <UiVersionContext.Provider value={value}>{children}</UiVersionContext.Provider>;
}

/** Used when no provider is mounted (isolated component tests): the classic view, nothing to switch. */
const CLASSIC_ONLY: UiVersionContextValue = {
  uiVersion: "classic",
  isNewView: false,
  canSwitch: false,
  setUiVersion: async () => {},
  theme: "dark",
  resolvedTheme: "dark",
  setTheme: () => {},
};

export function useUiVersion(): UiVersionContextValue {
  return useContext(UiVersionContext) ?? CLASSIC_ONLY;
}
