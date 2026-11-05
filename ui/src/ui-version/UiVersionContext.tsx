import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import {
  applyToDocument,
  newViewEnabled,
  readTheme,
  resolveTheme,
  writeTheme,
  type ResolvedTheme,
  type ThemeChoice,
  type UiVersion,
} from "./storage";

interface UiVersionContextValue {
  uiVersion: UiVersion;
  isNewView: boolean;
  theme: ThemeChoice;
  resolvedTheme: ResolvedTheme;
  setTheme: (next: ThemeChoice) => void;
}

const UiVersionContext = createContext<UiVersionContextValue | undefined>(undefined);

const DARK_QUERY = "(prefers-color-scheme: dark)";

function systemPrefersDark(): boolean {
  return typeof window !== "undefined" && typeof window.matchMedia === "function"
    ? window.matchMedia(DARK_QUERY).matches
    : true;
}

/**
 * Everyone gets the new view, whatever an account or browser saved earlier; only the kill switch
 * brings back classic. The attributes set on <html> are what the new-view styles key off.
 */
export function UiVersionProvider({ children }: { children: ReactNode }) {
  const uiVersion: UiVersion = newViewEnabled() ? "new" : "classic";
  const [theme, setThemeState] = useState<ThemeChoice>(readTheme);
  const [prefersDark, setPrefersDark] = useState<boolean>(systemPrefersDark);

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

  const setTheme = useCallback((next: ThemeChoice) => {
    setThemeState(next);
    writeTheme(next);
  }, []);

  const value = useMemo<UiVersionContextValue>(
    () => ({
      uiVersion,
      isNewView: uiVersion === "new",
      theme,
      resolvedTheme,
      setTheme,
    }),
    [uiVersion, theme, resolvedTheme, setTheme]
  );

  return <UiVersionContext.Provider value={value}>{children}</UiVersionContext.Provider>;
}

/** Used when no provider is mounted (isolated component tests): the classic view. */
const CLASSIC_ONLY: UiVersionContextValue = {
  uiVersion: "classic",
  isNewView: false,
  theme: "dark",
  resolvedTheme: "dark",
  setTheme: () => {},
};

export function useUiVersion(): UiVersionContextValue {
  return useContext(UiVersionContext) ?? CLASSIC_ONLY;
}
