import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import type { AxiosAdapter, AxiosResponse, InternalAxiosRequestConfig } from "axios";

// The theme has no bearing on sign-in state. Everything else is real: AuthProvider, the axios
// refresh interceptor, useMyPermissions and the healer shell. Only the network is faked.
vi.mock("../ui-version", () => ({
  useUiVersion: () => ({ isNewView: true, theme: "dark", resolvedTheme: "dark", setTheme: vi.fn() }),
}));

import api, { __resetRefreshStateForTests } from "../lib/axios";
import { AuthProvider } from "./AuthContext";
import HealerShell from "../ui-new/shell/HealerShell";

const SAVED_USER_KEY = "user_profile_data";
const NETWORK_DOWN = 0;

const PROFILE = {
  id: "01a1ac13-ebf4-7200-810b-6012f47e06d6",
  fullName: "Asha Rao",
  email: "asha@example.com",
  phone: "9999999999",
  role: "HEALER",
};
const PERMISSIONS = { grants: { PATIENT: ["READ", "WRITE"], PDF: ["READ"] } };

/** Successive statuses per endpoint. The last one repeats; endpoints not listed answer 200. */
type Script = Partial<Record<"me" | "refresh" | "profile" | "permissions", number[]>>;

const ENDPOINTS = {
  "/api/v1/auth/me": { key: "me", body: { id: PROFILE.id } },
  "/api/v1/auth/refresh": { key: "refresh", body: { success: true } },
  "/api/v1/auth/profile": { key: "profile", body: PROFILE },
  "/api/v1/auth/me/permissions": { key: "permissions", body: PERMISSIONS },
} as const;

function fakeServer(script: Script) {
  const calls: string[] = [];
  const adapter: AxiosAdapter = (cfg: InternalAxiosRequestConfig) => {
    calls.push(cfg.url ?? "");
    const route = ENDPOINTS[cfg.url as keyof typeof ENDPOINTS];
    const statuses = route ? script[route.key] : undefined;
    const status = statuses ? (statuses.length > 1 ? statuses.shift()! : statuses[0]) : 200;

    if (status === NETWORK_DOWN) {
      return Promise.reject(
        Object.assign(new Error("Network Error"), { isAxiosError: true, config: cfg })
      );
    }
    const response = {
      data: route?.body ?? [],
      status,
      statusText: "",
      headers: {},
      config: cfg,
    } as AxiosResponse;
    if (status < 400) return Promise.resolve(response);
    return Promise.reject(
      Object.assign(new Error(`HTTP ${status}`), { isAxiosError: true, config: cfg, response })
    );
  };
  api.defaults.adapter = adapter;
  return calls;
}

function openApp() {
  // Same query defaults as main.tsx: a test with friendlier defaults would hide retry bugs.
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        refetchOnWindowFocus: false,
        refetchOnReconnect: false,
        retry: false,
        staleTime: 1000 * 60 * 5,
      },
    },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/"]}>
        <AuthProvider>
          <Routes>
            <Route element={<HealerShell />}>
              <Route path="*" element={<div>PAGE</div>} />
            </Route>
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

const saveUser = () => localStorage.setItem(SAVED_USER_KEY, JSON.stringify(PROFILE));
const savedUser = () => localStorage.getItem(SAVED_USER_KEY);

async function expectFullHealerNav() {
  const timeout = { timeout: 5000 };
  expect((await screen.findAllByRole("link", { name: "Patients" }, timeout)).length).toBeGreaterThan(0);
  expect((await screen.findAllByRole("link", { name: "PDFs" }, timeout)).length).toBeGreaterThan(0);
  // The profile icon holds the only Log out option, so losing it leaves the healer stuck.
  expect((await screen.findAllByRole("button", { name: "AR" }, timeout)).length).toBeGreaterThan(0);
}

describe("signed-in session keeps its navigation", () => {
  beforeEach(() => {
    localStorage.clear();
    __resetRefreshStateForTests();
    vi.spyOn(console, "error").mockImplementation(() => {});
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it("shows the full navigation for a healthy session with a saved user", async () => {
    saveUser();
    fakeServer({});
    openApp();
    await expectFullHealerNav();
  });

  // Production, 5 Nov 2026 (iPhone): cookies still valid, saved user gone, and the app showed
  // only Dashboard and Profile with no profile icon. me/permissions was never requested.
  it("restores the user when the session is valid but no user is saved", async () => {
    fakeServer({});
    openApp();
    await expectFullHealerNav();
  });

  it("restores the user after refreshing an expired access token", async () => {
    fakeServer({ me: [401, 200] });
    openApp();
    await expectFullHealerNav();
  });

  it("keeps trying to restore the user when the first profile request fails", async () => {
    fakeServer({ profile: [500, 200] });
    openApp();
    await expectFullHealerNav();
  });

  // A patient link's cookie also gets a 200 from /auth/me, but it may not read a healer's profile.
  it("does not keep asking for a profile the session may not read", async () => {
    const calls = fakeServer({ profile: [403] });
    openApp();

    await waitFor(() => expect(screen.queryByText("PAGE")).toBeInTheDocument());
    await new Promise((resolve) => setTimeout(resolve, 3500));

    expect(calls.filter((url) => url === "/api/v1/auth/profile")).toHaveLength(1);
    expect(savedUser()).toBeNull();
    expect(screen.queryByRole("link", { name: "Patients" })).not.toBeInTheDocument();
  });

  it("keeps the saved user when the access token expired and the refresh hits an outage", async () => {
    saveUser();
    fakeServer({ me: [401], refresh: [503] });
    openApp();

    await waitFor(() => expect(screen.queryByText("PAGE")).toBeInTheDocument());
    await new Promise((resolve) => setTimeout(resolve, 50));

    expect(savedUser()).not.toBeNull();
  });

  it.each([
    ["the server is restarting", 502],
    ["the network drops", NETWORK_DOWN],
  ])("keeps the saved user when /auth/me fails because %s", async (_cause, status) => {
    saveUser();
    fakeServer({ me: [status] });
    openApp();

    // Let the failed request settle before checking that the user survived it.
    await waitFor(() => expect(screen.queryByText("PAGE")).toBeInTheDocument());
    await new Promise((resolve) => setTimeout(resolve, 50));

    expect(savedUser()).not.toBeNull();
  });

  it("shows the tabs after a single failed permissions request", async () => {
    saveUser();
    fakeServer({ permissions: [500, 200] });
    openApp();
    await expectFullHealerNav();
  });

  // /auth/refresh answers 400 for a missing or expired refresh token (seen in production).
  // The session is over, so the user must be signed out rather than left on a dead screen.
  it("signs out mid-session when the server rejects the refresh token with 400", async () => {
    saveUser();
    fakeServer({ refresh: [400] });
    openApp();
    await waitFor(() => expect(savedUser()).toBeNull());
  });

  // Guard: the fixes above must not keep someone signed in once the session has really ended.
  it("signs out when both the access token and the refresh token are rejected", async () => {
    saveUser();
    fakeServer({ me: [401], refresh: [401] });
    openApp();

    await waitFor(() => expect(savedUser()).toBeNull());
    expect(screen.queryByRole("link", { name: "Patients" })).not.toBeInTheDocument();
  });
});
