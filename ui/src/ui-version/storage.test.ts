import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  THEME_KEY,
  UI_VERSION_KEY,
  applyToDocument,
  fromServerValue,
  newViewEnabled,
  readTheme,
  readUiVersion,
  resolveTheme,
  toServerValue,
  writeTheme,
  writeUiVersion,
} from "./storage";

describe("ui version storage", () => {
  beforeEach(() => localStorage.clear());

  it("defaults to the classic view when nothing is stored", () => {
    expect(readUiVersion()).toBe("classic");
  });

  it("ignores a stored value it does not recognise", () => {
    localStorage.setItem(UI_VERSION_KEY, "future");
    expect(readUiVersion()).toBe("classic");
  });

  it("round-trips the chosen view", () => {
    writeUiVersion("new");
    expect(localStorage.getItem(UI_VERSION_KEY)).toBe("new");
    expect(readUiVersion()).toBe("new");
  });

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

describe("server value mapping", () => {
  it("maps the API enum to the client value and back", () => {
    expect(fromServerValue("NEW")).toBe("new");
    expect(fromServerValue("CLASSIC")).toBe("classic");
    expect(toServerValue("new")).toBe("NEW");
    expect(toServerValue("classic")).toBe("CLASSIC");
  });

  it("returns undefined for a missing or unknown server value", () => {
    expect(fromServerValue(undefined)).toBeUndefined();
    expect(fromServerValue("FUTURE")).toBeUndefined();
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

  it("is on in development", () => {
    vi.stubEnv("DEV", true);
    vi.stubEnv("VITE_NEW_VIEW", "");
    expect(newViewEnabled()).toBe(true);
  });

  it("is off in a production build unless VITE_NEW_VIEW is 'on'", () => {
    vi.stubEnv("DEV", false);
    vi.stubEnv("VITE_NEW_VIEW", "");
    expect(newViewEnabled()).toBe(false);
    vi.stubEnv("VITE_NEW_VIEW", "on");
    expect(newViewEnabled()).toBe(true);
  });

  it("can be switched off explicitly, even in development", () => {
    vi.stubEnv("DEV", true);
    vi.stubEnv("VITE_NEW_VIEW", "off");
    expect(newViewEnabled()).toBe(false);
  });
});
