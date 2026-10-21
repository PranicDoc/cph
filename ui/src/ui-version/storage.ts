/**
 * Which web UI the user sees ("classic" or the redesigned "new" view) and which
 * theme the new view uses. The choice lives on the account (see UiVersionContext);
 * local storage is only a cache so the very first paint does not wait for the network.
 */

export type UiVersion = "classic" | "new";
export type ThemeChoice = "dark" | "light" | "system";
export type ResolvedTheme = "dark" | "light";

export const UI_VERSION_KEY = "hl.ui.version";
export const THEME_KEY = "hl.ui.theme";

const THEMES: ThemeChoice[] = ["dark", "light", "system"];

/**
 * The new view is on in development and in builds made with VITE_NEW_VIEW=on.
 * VITE_NEW_VIEW=off is the kill switch: it wins over everything.
 */
export function newViewEnabled(): boolean {
  const flag = import.meta.env.VITE_NEW_VIEW;
  if (flag === "off") return false;
  return flag === "on" || import.meta.env.DEV === true;
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
    // Storage can be blocked (private window, cleared site data); the choice still works for this visit.
  }
}

export function readUiVersion(): UiVersion {
  return read(UI_VERSION_KEY) === "new" ? "new" : "classic";
}

export function writeUiVersion(value: UiVersion): void {
  write(UI_VERSION_KEY, value);
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

/** The API stores the choice as the enum CLASSIC | NEW. */
export function fromServerValue(value: string | undefined | null): UiVersion | undefined {
  if (value === "NEW") return "new";
  if (value === "CLASSIC") return "classic";
  return undefined;
}

export function toServerValue(value: UiVersion): "CLASSIC" | "NEW" {
  return value === "new" ? "NEW" : "CLASSIC";
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
