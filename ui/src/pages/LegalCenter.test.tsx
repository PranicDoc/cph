import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, within, waitFor, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import React from "react";

import LegalCenter from "./LegalCenter";
import type { PolicyVersionResponse } from "../hooks/admin/useLegalDocuments";
import {
  triggerIntersection,
  resetIntersectionObserverMock,
} from "../test/intersectionObserverMock";

const { mockLogout } = vi.hoisted(() => ({ mockLogout: vi.fn() }));
const { mockNavigate } = vi.hoisted(() => ({ mockNavigate: vi.fn() }));

vi.mock("../lib/axios", () => ({
  default: { get: vi.fn(), post: vi.fn(), patch: vi.fn() },
}));

vi.mock("sonner", () => ({
  toast: { error: vi.fn(), success: vi.fn() },
}));

vi.mock("../context/AuthContext", () => ({
  useAuth: () => ({
    logout: mockLogout,
    isAuthenticated: true,
    isLoading: false,
    user: { role: "HEALER" },
  }),
}));

vi.mock("react-router-dom", async (importOriginal) => {
  const actual = await importOriginal<typeof import("react-router-dom")>();
  return { ...actual, useNavigate: () => mockNavigate };
});

import api from "../lib/axios";

const mockedGet = api.get as unknown as ReturnType<typeof vi.fn>;
const mockedPost = api.post as unknown as ReturnType<typeof vi.fn>;
const mockedPatch = api.patch as unknown as ReturnType<typeof vi.fn>;

let originalSendBeacon: Navigator["sendBeacon"] | undefined;

const termsDoc: PolicyVersionResponse = {
  id: "doc-terms",
  consentType: "TERMS_AND_CONDITIONS",
  policyVersion: 1,
  mandatory: true,
  effectiveDate: "2026-01-01T00:00:00Z",
  content: "CONTENT_TERMS — please read the terms carefully.",
  publisherId: "pub-1",
  publisherName: "Admin User",
};

const privacyDoc: PolicyVersionResponse = {
  id: "doc-privacy",
  consentType: "PRIVACY_POLICY",
  policyVersion: 2,
  mandatory: true,
  effectiveDate: "2026-01-01T00:00:00Z",
  content: "CONTENT_PRIVACY — how we handle your data.",
  publisherId: "pub-1",
  publisherName: "Admin User",
};

const twoDocFixture = [termsDoc, privacyDoc];

// Queue of successive responses `GET /api/v1/consents/outstanding` should
// return, in call order. Once only one entry remains it's returned for
// every subsequent call (models the settled post-refetch state).
let outstandingQueue: PolicyVersionResponse[][] = [];

function nextOutstanding(): PolicyVersionResponse[] {
  if (outstandingQueue.length > 1) {
    return outstandingQueue.shift() as PolicyVersionResponse[];
  }
  return outstandingQueue[0] ?? [];
}

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return {
    queryClient,
    Wrapper: ({ children }: { children: React.ReactNode }) =>
      React.createElement(
        QueryClientProvider,
        { client: queryClient },
        React.createElement(MemoryRouter, null, children)
      ),
  };
}

async function renderLegalCenter() {
  const { Wrapper, queryClient } = createWrapper();
  const utils = render(React.createElement(LegalCenter), { wrapper: Wrapper });
  await waitFor(() =>
    expect(screen.queryByText("Loading...")).not.toBeInTheDocument()
  );
  return { ...utils, queryClient };
}

describe("LegalCenter", () => {
  beforeEach(() => {
    resetIntersectionObserverMock();
    vi.clearAllMocks();
    mockLogout.mockReset();
    mockNavigate.mockReset();
    outstandingQueue = [twoDocFixture];

    mockedGet.mockImplementation((url: string) => {
      if (url === "/api/v1/consents/outstanding") {
        return Promise.resolve({ data: nextOutstanding() });
      }
      return Promise.resolve({ data: {} });
    });
    mockedPost.mockImplementation((url: string) => {
      if (url === "/api/v1/consents") {
        return Promise.resolve({ data: { id: "consent-1" } });
      }
      if (url === "/api/v1/consents/reading-sessions") {
        return Promise.resolve({ data: { id: "session-1" } });
      }
      return Promise.resolve({ data: {} });
    });
    mockedPatch.mockResolvedValue({ data: {} });

    originalSendBeacon = navigator.sendBeacon;
    navigator.sendBeacon = vi.fn();
  });

  afterEach(() => {
    navigator.sendBeacon = originalSendBeacon as Navigator["sendBeacon"];
  });

  it("renders_a_tab_for_every_outstanding_document_with_human_readable_labels", async () => {
    await renderLegalCenter();

    const tablist = screen.getByRole("tablist");
    const tabs = within(tablist).getAllByRole("tab");
    expect(tabs).toHaveLength(2);
    expect(within(tablist).getByText("Terms and Conditions")).toBeInTheDocument();
    expect(within(tablist).getByText("Privacy Policy")).toBeInTheDocument();
  });

  it("shows_the_same_subtitle_regardless_of_how_many_documents_are_outstanding", async () => {
    const subtitle =
      "Please review and accept the following documents to continue using your account.";

    outstandingQueue = [twoDocFixture];
    const { unmount } = await renderLegalCenter();
    expect(screen.getByText(subtitle)).toBeInTheDocument();
    unmount();

    outstandingQueue = [[termsDoc]];
    await renderLegalCenter();
    expect(screen.getByText(subtitle)).toBeInTheDocument();
  });

  it("posts_the_acceptance_for_the_document_the_practitioner_agreed_to", async () => {
    const user = userEvent.setup();
    await renderLegalCenter();

    act(() => {
      triggerIntersection(true);
    });

    const agreeButton = screen.getByRole("button", { name: /i agree/i });
    await user.click(agreeButton);
    await act(async () => {
      await Promise.resolve();
    });

    const consentCalls = mockedPost.mock.calls.filter(
      ([url]) => url === "/api/v1/consents"
    );
    expect(consentCalls).toHaveLength(1);
    expect(consentCalls[0][1]).toEqual({
      consentType: "TERMS_AND_CONDITIONS",
      policyVersion: 1,
    });
  });

  it("does_not_accept_anything_before_the_reader_gate_is_satisfied", async () => {
    await renderLegalCenter();

    const agreeButton = screen.getByRole("button", { name: /i agree/i });
    expect(agreeButton).toBeDisabled();

    const consentCalls = mockedPost.mock.calls.filter(
      ([url]) => url === "/api/v1/consents"
    );
    expect(consentCalls).toHaveLength(0);
  });

  it("keeps_the_reader_visible_after_accepting_a_non_default_tab", async () => {
    const user = userEvent.setup();
    // First GET (initial mount) returns both docs; every GET after the
    // accept-triggered invalidation returns only the terms document —
    // the regression guard for LegalCenter.tsx's remount key.
    outstandingQueue = [twoDocFixture, [termsDoc]];

    await renderLegalCenter();

    const privacyTab = screen.getByTestId("tab-PRIVACY_POLICY");
    await user.click(privacyTab);
    await act(async () => {
      await Promise.resolve();
    });

    act(() => {
      triggerIntersection(true);
    });

    const agreeButton = screen.getByRole("button", { name: /i agree/i });
    await user.click(agreeButton);
    await act(async () => {
      await Promise.resolve();
    });

    const consentCalls = mockedPost.mock.calls.filter(
      ([url]) => url === "/api/v1/consents"
    );
    expect(consentCalls[0][1]).toEqual({
      consentType: "PRIVACY_POLICY",
      policyVersion: 2,
    });

    await waitFor(() =>
      expect(screen.queryByTestId("tab-PRIVACY_POLICY")).not.toBeInTheDocument()
    );

    expect(screen.getByText(/CONTENT_TERMS/)).toBeInTheDocument();
    expect(screen.queryByText("No documents to display.")).not.toBeInTheDocument();
  });

  it("shows_retry_and_logout_when_the_outstanding_check_fails", async () => {
    const user = userEvent.setup();
    mockedGet.mockImplementationOnce((url: string) => {
      if (url === "/api/v1/consents/outstanding") {
        return Promise.reject({ response: { data: {} } });
      }
      return Promise.resolve({ data: {} });
    });

    await renderLegalCenter();

    expect(
      screen.getByText("We Couldn't Check Your Account Status")
    ).toBeInTheDocument();
    expect(screen.getByTestId("legal-center-retry")).toBeInTheDocument();
    expect(screen.getByTestId("legal-center-error-logout")).toBeInTheDocument();

    await user.click(screen.getByTestId("legal-center-retry"));
    await act(async () => {
      await Promise.resolve();
    });

    const outstandingCalls = mockedGet.mock.calls.filter(
      ([url]) => url === "/api/v1/consents/outstanding"
    );
    expect(outstandingCalls.length).toBeGreaterThanOrEqual(2);
  });

  // This test exercises LegalCenter STANDALONE, outside ProtectedRoute, so
  // it verifies only the page's defense-in-depth fallback (Task 2 state 3).
  // It is NOT the proof of D-05 — in the integrated app the guard redirects
  // before this page ever renders, and D-05 is proven by Plan 34-03's
  // redirects_away_from_the_legal_center_once_nothing_is_outstanding. Do
  // not later "promote" this test to stand in for that one.
  it("falls_back_to_dashboard_navigation_when_rendered_with_nothing_outstanding", async () => {
    outstandingQueue = [[]];

    await renderLegalCenter();

    await waitFor(() =>
      expect(mockNavigate).toHaveBeenCalledWith("/", { replace: true })
    );
  });
});
