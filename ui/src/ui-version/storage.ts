/**
 * Which web UI is shown and which theme it uses. Everyone gets the new view; "classic" only
 * remains for the VITE_NEW_VIEW=off kill switch. The theme is remembered in this browser.
 */

export type UiVersion = "classic" | "new";
export type ThemeChoice = "dark" | "light" | "system";
export type ResolvedTheme = "dark" | "light";

export const THEME_KEY = "hl.ui.theme";

const THEMES: ThemeChoice[] = ["dark", "light", "system"];

/** The new view is always on unless the build sets the kill switch, VITE_NEW_VIEW=off. */
export function newViewEnabled(): boolean {
  return import.meta.env.VITE_NEW_VIEW !== "off";
}

function read(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function write(key: string, value: string): void {
  try {
    localStorage.setItem(key, value);
  } catch {
    // Storage can be blocked (private window, cleared site data); the theme still works for this visit.
  }
}

export function readTheme(): ThemeChoice {
  const stored = read(THEME_KEY);
  return THEMES.includes(stored as ThemeChoice) ? (stored as ThemeChoice) : "dark";
}

export function writeTheme(value: ThemeChoice): void {
  write(THEME_KEY, value);
}

export function resolveTheme(choice: ThemeChoice, systemPrefersDark: boolean): ResolvedTheme {
  if (choice === "system") return systemPrefersDark ? "dark" : "light";
  return choice;
}

/** Dialogs and menus render on <body>, so the attributes live on <html>, not on a wrapper. */
export function applyToDocument(root: HTMLElement, uiVersion: UiVersion, theme: ResolvedTheme): void {
  root.dataset.ui = uiVersion;
  if (uiVersion === "new") {
    root.dataset.theme = theme;
  } else {
    delete root.dataset.theme;
  }
}
