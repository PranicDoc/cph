import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Routes, Route } from "react-router-dom";

import ProtectedRoute from "./ProtectedRoute";

vi.mock("../lib/axios", () => ({
  default: { get: vi.fn() },
}));

const { mockUseAuth } = vi.hoisted(() => ({ mockUseAuth: vi.fn() }));

vi.mock("../context/AuthContext", () => ({
  useAuth: () => mockUseAuth(),
}));

import api from "../lib/axios";

const mockedGet = api.get as unknown as ReturnType<typeof vi.fn>;

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return {
    Wrapper: ({ children }: { children: React.ReactNode }) =>
      React.createElement(QueryClientProvider, { client: queryClient }, children),
  };
}

function renderAt(startPath: string) {
  const { Wrapper } = createWrapper();
  return render(
    React.createElement(
      Wrapper,
      null,
      React.createElement(
        MemoryRouter,
        { initialEntries: [startPath] },
        React.createElement(
          Routes,
          null,
          React.createElement(Route, {
            element: React.createElement(ProtectedRoute),
            children: [
              React.createElement(Route, {
                key: "root",
                path: "/",
                element: React.createElement("div", null, "DASHBOARD"),
              }),
              React.createElement(Route, {
                key: "patients",
                path: "/patients",
                element: React.createElement("div", null, "PATIENTS"),
              }),
              React.createElement(Route, {
                key: "deep",
                path: "/cases/abc/summary",
                element: React.createElement("div", null, "DEEP"),
              }),
              React.createElement(Route, {
                key: "legal-center",
                path: "/legal-center",
                element: React.createElement("div", null, "LEGAL_CENTER"),
              }),
            ],
          }),
          React.createElement(Route, {
            path: "/login",
            element: React.createElement("div", null, "LOGIN"),
          })
        )
      )
    )
  );
}

describe("ProtectedRoute", () => {
  beforeEach(() => {
    mockedGet.mockReset();
    mockUseAuth.mockReset();
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: { role: "HEALER" },
    });
  });

  it("redirects_a_healer_with_outstanding_consent_away_from_a_protected_route", async () => {
    mockedGet.mockResolvedValue({
      data: [{ id: "doc-1", consentType: "TERMS_AND_CONDITIONS", policyVersion: 1 }],
    });

    renderAt("/patients");

    expect(await screen.findByText("LEGAL_CENTER")).toBeInTheDocument();
    expect(screen.queryByText("PATIENTS")).not.toBeInTheDocument();
  });

  it("redirects_from_a_deep_bookmarked_url_too", async () => {
    mockedGet.mockResolvedValue({
      data: [{ id: "doc-1", consentType: "TERMS_AND_CONDITIONS", policyVersion: 1 }],
    });

    renderAt("/cases/abc/summary");

    expect(await screen.findByText("LEGAL_CENTER")).toBeInTheDocument();
    expect(screen.queryByText("DEEP")).not.toBeInTheDocument();
  });

  it("lets_a_healer_with_no_outstanding_consent_through", async () => {
    mockedGet.mockResolvedValue({ data: [] });

    renderAt("/patients");

    expect(await screen.findByText("PATIENTS")).toBeInTheDocument();
  });

  it("does_not_gate_admin_accounts", async () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: { role: "ADMIN" },
    });
    mockedGet.mockResolvedValue({
      data: [{ id: "doc-1", consentType: "TERMS_AND_CONDITIONS", policyVersion: 1 }],
    });

    renderAt("/");

    expect(await screen.findByText("DASHBOARD")).toBeInTheDocument();
    expect(mockedGet).not.toHaveBeenCalledWith("/api/v1/consents/outstanding");
  });

  it("does_not_gate_supadm_accounts", async () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: { role: "SUPADM" },
    });
    mockedGet.mockResolvedValue({
      data: [{ id: "doc-1", consentType: "TERMS_AND_CONDITIONS", policyVersion: 1 }],
    });

    renderAt("/");

    expect(await screen.findByText("DASHBOARD")).toBeInTheDocument();
    expect(mockedGet).not.toHaveBeenCalledWith("/api/v1/consents/outstanding");
  });

  it("redirects_away_from_the_legal_center_once_nothing_is_outstanding", async () => {
    mockedGet.mockResolvedValue({ data: [] });

    renderAt("/legal-center");

    expect(await screen.findByText("DASHBOARD")).toBeInTheDocument();
  });

  it("keeps_the_gate_closed_when_the_outstanding_check_fails", async () => {
    mockedGet.mockRejectedValue(new Error("network error"));

    renderAt("/patients");

    expect(await screen.findByText("LEGAL_CENTER")).toBeInTheDocument();
    expect(screen.queryByText("PATIENTS")).not.toBeInTheDocument();
  });

  it("does_not_render_a_protected_route_while_the_consent_check_is_in_flight", async () => {
    mockedGet.mockReturnValue(new Promise(() => {})); // never settles

    renderAt("/patients");

    await waitFor(() => expect(screen.getByText("Loading...")).toBeInTheDocument());
    expect(screen.queryByText("PATIENTS")).not.toBeInTheDocument();
  });

  it("sends_an_unauthenticated_visitor_to_login", async () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: false,
      isLoading: false,
      user: null,
    });

    renderAt("/patients");

    expect(await screen.findByText("LOGIN")).toBeInTheDocument();
  });
});
