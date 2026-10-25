import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

vi.mock("../lib/axios", () => ({
  default: { get: vi.fn(), put: vi.fn() },
}));

const { mockUseAuth, mockToastError } = vi.hoisted(() => ({
  mockUseAuth: vi.fn(),
  mockToastError: vi.fn(),
}));

vi.mock("../context/AuthContext", () => ({ useAuth: () => mockUseAuth() }));
vi.mock("sonner", () => ({ toast: { error: mockToastError, success: vi.fn() } }));

import api from "../lib/axios";
import { THEME_KEY, UI_VERSION_KEY } from "./storage";
import { UiVersionProvider, useUiVersion } from "./UiVersionContext";

const get = api.get as unknown as ReturnType<typeof vi.fn>;
const put = api.put as unknown as ReturnType<typeof vi.fn>;

function Probe() {
  const v = useUiVersion();
  return (
    <div>
      <span data-testid="version">{v.uiVersion}</span>
      <span data-testid="theme">{v.theme}</span>
      <span data-testid="resolved">{v.resolvedTheme}</span>
      <span data-testid="can-switch">{String(v.canSwitch)}</span>
      <button onClick={() => v.setUiVersion("new")}>go new</button>
      <button onClick={() => v.setUiVersion("classic")}>go classic</button>
      <button onClick={() => v.setTheme("light")}>light</button>
      <button onClick={() => v.setTheme("system")}>system</button>
    </div>
  );
}

function renderProvider() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <UiVersionProvider>
        <Probe />
      </UiVersionProvider>
    </QueryClientProvider>
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
    document.documentElement.removeAttribute("data-ui");
    document.documentElement.removeAttribute("data-theme");
    mockUseAuth.mockReturnValue({ isAuthenticated: true });
    get.mockResolvedValue({ data: { uiVersion: "CLASSIC" } });
    put.mockResolvedValue({ data: {} });
    mockToastError.mockClear();
    mockMatchMedia(true);
    vi.stubEnv("DEV", true);
    vi.stubEnv("VITE_NEW_VIEW", "");
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.clearAllMocks();
  });

  it("starts on the classic view with no theme attribute", async () => {
    renderProvider();
    expect(screen.getByTestId("version")).toHaveTextContent("classic");
    expect(document.documentElement.dataset.ui).toBe("classic");
    expect(document.documentElement.dataset.theme).toBeUndefined();
  });

  it("restores the new view and the dark default theme from the cache", async () => {
    localStorage.setItem(UI_VERSION_KEY, "new");
    get.mockResolvedValue({ data: { uiVersion: "NEW" } });
    renderProvider();
    expect(screen.getByTestId("version")).toHaveTextContent("new");
    expect(document.documentElement.dataset.ui).toBe("new");
    expect(document.documentElement.dataset.theme).toBe("dark");
  });

  it("saves the choice on the account and caches it", async () => {
    renderProvider();
    await act(async () => screen.getByText("go new").click());
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/auth/ui-version", { uiVersion: "NEW" }));
    expect(screen.getByTestId("version")).toHaveTextContent("new");
    expect(localStorage.getItem(UI_VERSION_KEY)).toBe("new");
    expect(document.documentElement.dataset.ui).toBe("new");
  });

  it("goes back to the previous view and tells the user when saving fails", async () => {
    put.mockRejectedValue(new Error("network"));
    renderProvider();
    await act(async () => screen.getByText("go new").click());
    await waitFor(() => expect(mockToastError).toHaveBeenCalled());
    expect(screen.getByTestId("version")).toHaveTextContent("classic");
    expect(localStorage.getItem(UI_VERSION_KEY)).toBe("classic");
    expect(document.documentElement.dataset.ui).toBe("classic");
  });

  it("follows the account when it says NEW but this browser has not chosen yet", async () => {
    get.mockResolvedValue({ data: { uiVersion: "NEW" } });
    renderProvider();
    await waitFor(() => expect(screen.getByTestId("version")).toHaveTextContent("new"));
    expect(localStorage.getItem(UI_VERSION_KEY)).toBe("new");
  });

  it("follows the account when it says CLASSIC but this browser had the new view", async () => {
    localStorage.setItem(UI_VERSION_KEY, "new");
    get.mockResolvedValue({ data: { uiVersion: "CLASSIC" } });
    renderProvider();
    // The view switches on render and the theme attribute is cleared just after: wait for both.
    await waitFor(() => {
      expect(screen.getByTestId("version")).toHaveTextContent("classic");
      expect(document.documentElement.dataset.theme).toBeUndefined();
    });
  });

  it("does not ask the account anything while signed out", async () => {
    mockUseAuth.mockReturnValue({ isAuthenticated: false });
    renderProvider();
    await act(async () => screen.getByText("go new").click());
    expect(get).not.toHaveBeenCalled();
    expect(put).not.toHaveBeenCalled();
  });

  it("is forced to classic, and cannot switch, when the kill switch is on", async () => {
    vi.stubEnv("VITE_NEW_VIEW", "off");
    localStorage.setItem(UI_VERSION_KEY, "new");
    get.mockResolvedValue({ data: { uiVersion: "NEW" } });
    renderProvider();
    expect(screen.getByTestId("version")).toHaveTextContent("classic");
    expect(screen.getByTestId("can-switch")).toHaveTextContent("false");
    await act(async () => screen.getByText("go new").click());
    expect(screen.getByTestId("version")).toHaveTextContent("classic");
    expect(put).not.toHaveBeenCalled();
    expect(get).not.toHaveBeenCalled();
  });

  it("applies and remembers a chosen theme", async () => {
    localStorage.setItem(UI_VERSION_KEY, "new");
    // The account must agree, or its CLASSIC answer can switch views (and clear the theme) mid-test.
    get.mockResolvedValue({ data: { uiVersion: "NEW" } });
    renderProvider();
    await act(async () => screen.getByText("light").click());
    expect(document.documentElement.dataset.theme).toBe("light");
    expect(localStorage.getItem(THEME_KEY)).toBe("light");
  });

  it("follows the system theme when 'system' is chosen", async () => {
    mockMatchMedia(false);
    localStorage.setItem(UI_VERSION_KEY, "new");
    get.mockResolvedValue({ data: { uiVersion: "NEW" } });
    renderProvider();
    await act(async () => screen.getByText("system").click());
    expect(screen.getByTestId("resolved")).toHaveTextContent("light");
    expect(document.documentElement.dataset.theme).toBe("light");
  });
});
