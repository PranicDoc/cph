import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  THEME_KEY,
  applyToDocument,
  newViewEnabled,
  readTheme,
  resolveTheme,
  writeTheme,
} from "./storage";

describe("ui version storage", () => {
  beforeEach(() => localStorage.clear());

  it("defaults the theme to dark and ignores unknown values", () => {
    expect(readTheme()).toBe("dark");
    localStorage.setItem(THEME_KEY, "sepia");
    expect(readTheme()).toBe("dark");
  });

  it("round-trips the theme choice", () => {
    writeTheme("system");
    expect(readTheme()).toBe("system");
  });

  it("does not throw when storage is unavailable", () => {
    const spy = vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    expect(readUiVersion()).toBe("classic");
    expect(readTheme()).toBe("dark");
    spy.mockRestore();
  });
});

describe("resolveTheme", () => {
  it("returns an explicit choice as it is", () => {
    expect(resolveTheme("light", true)).toBe("light");
    expect(resolveTheme("dark", false)).toBe("dark");
  });

  it("follows the system preference for 'system'", () => {
    expect(resolveTheme("system", true)).toBe("dark");
    expect(resolveTheme("system", false)).toBe("light");
  });
});

describe("applyToDocument", () => {
  it("sets data-ui and data-theme for the new view", () => {
    const el = document.createElement("html");
    applyToDocument(el, "new", "dark");
    expect(el.dataset.ui).toBe("new");
    expect(el.dataset.theme).toBe("dark");
  });

  it("leaves no theme attribute on the classic view", () => {
    const el = document.createElement("html");
    el.dataset.theme = "dark";
    applyToDocument(el, "classic", "dark");
    expect(el.dataset.ui).toBe("classic");
    expect(el.dataset.theme).toBeUndefined();
  });
});

describe("newViewEnabled", () => {
  afterEach(() => vi.unstubAllEnvs());

  it("is on in a production build with no flag set", () => {
    vi.stubEnv("DEV", false);
    vi.stubEnv("VITE_NEW_VIEW", "");
    expect(newViewEnabled()).toBe(true);
  });

  it("is off only when the kill switch is set", () => {
    vi.stubEnv("VITE_NEW_VIEW", "off");
    expect(newViewEnabled()).toBe(false);
  });
});
