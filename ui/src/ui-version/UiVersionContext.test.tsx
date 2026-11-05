import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, screen } from "@testing-library/react";

import { THEME_KEY } from "./storage";
import { UiVersionProvider, useUiVersion } from "./UiVersionContext";

function Probe() {
  const v = useUiVersion();
  return (
    <div>
      <span data-testid="version">{v.uiVersion}</span>
      <span data-testid="theme">{v.theme}</span>
      <span data-testid="resolved">{v.resolvedTheme}</span>
      <button onClick={() => v.setTheme("light")}>light</button>
      <button onClick={() => v.setTheme("system")}>system</button>
    </div>
  );
}

function renderProvider() {
  return render(
    <UiVersionProvider>
      <Probe />
    </UiVersionProvider>
  );
}

function mockMatchMedia(prefersDark: boolean) {
  window.matchMedia = ((query: string) => ({
    matches: query.includes("dark") ? prefersDark : false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  })) as unknown as typeof window.matchMedia;
}

describe("UiVersionProvider", () => {
  beforeEach(() => {
    localStorage.clear();
    mockMatchMedia(true);
    vi.stubEnv("VITE_NEW_VIEW", "");
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    delete document.documentElement.dataset.ui;
    delete document.documentElement.dataset.theme;
  });

  it("starts everyone on the new view with the dark theme", () => {
    renderProvider();
    expect(screen.getByTestId("version")).toHaveTextContent("new");
    expect(document.documentElement.dataset.ui).toBe("new");
    expect(document.documentElement.dataset.theme).toBe("dark");
  });

  it("ignores classic saved in this browser by the old view switch", () => {
    localStorage.setItem("hl.ui.version", "classic");
    renderProvider();
    expect(screen.getByTestId("version")).toHaveTextContent("new");
    expect(document.documentElement.dataset.ui).toBe("new");
  });

  it("shows classic with no theme attribute when the kill switch is on", () => {
    vi.stubEnv("VITE_NEW_VIEW", "off");
    renderProvider();
    expect(screen.getByTestId("version")).toHaveTextContent("classic");
    expect(document.documentElement.dataset.ui).toBe("classic");
    expect(document.documentElement.dataset.theme).toBeUndefined();
  });

  it("applies and remembers a chosen theme", async () => {
    renderProvider();
    await act(async () => screen.getByText("light").click());
    expect(document.documentElement.dataset.theme).toBe("light");
    expect(localStorage.getItem(THEME_KEY)).toBe("light");
  });

  it("follows the system theme when 'system' is chosen", async () => {
    mockMatchMedia(false);
    renderProvider();
    await act(async () => screen.getByText("system").click());
    expect(screen.getByTestId("resolved")).toHaveTextContent("light");
    expect(document.documentElement.dataset.theme).toBe("light");
  });
});
