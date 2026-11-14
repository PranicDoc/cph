import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";

import ClassicDashboard from "./Dashboard";
import NewDashboard from "../ui-new/pages/NewDashboard";
import api from "../lib/axios";

vi.mock("../lib/axios", () => ({ default: { get: vi.fn() } }));
vi.mock("../ui-version", () => ({
  // The classic page is what we want here; the new view is imported directly below.
  versioned: (Classic: unknown) => Classic,
}));
vi.mock("../context/AuthContext", () => ({ useAuth: () => ({ user: { id: "h1", fullName: "Asha Rao" } }) }));
vi.mock("../hooks/useDashboardSummary", () => ({
  useDashboardSummary: () => ({
    data: { activePatients: 4, sessionsThisMonth: 9, creditsRemaining: 7, pdfsGeneratedThisMonth: 2, creditServiceUnavailable: false },
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  }),
}));

// The charts and tables are not under test; keep them out of the way.
const { stub } = vi.hoisted(() => ({ stub: (name: string) => ({ [name]: () => null }) }));
vi.mock("../components/DateRangeFilter", () => stub("DateRangeFilter"));
vi.mock("../components/SessionTrendChart", () => stub("SessionTrendChart"));
vi.mock("../components/PatientGrowthChart", () => stub("PatientGrowthChart"));
vi.mock("../components/TopSymptomsChart", () => stub("TopSymptomsChart"));
vi.mock("../components/TopProtocolsChart", () => stub("TopProtocolsChart"));
vi.mock("../components/CreditUsageChart", () => stub("CreditUsageChart"));
vi.mock("../components/PendingReportsTable", () => stub("PendingReportsTable"));
vi.mock("../components/CaseListTable", () => stub("CaseListTable"));
vi.mock("../components/FollowupTable", () => stub("FollowupTable"));
vi.mock("../ui-new/dashboard/RankedCard", () => ({ TopProtocolsList: () => null, TopSymptomsList: () => null }));

function wrap(ui: ReactNode) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{ui}</MemoryRouter>
    </QueryClientProvider>
  );
}

describe("dashboard approved cases tile", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.get).mockImplementation((url: string) =>
      url === "/api/v1/reviews/my-approved-count"
        ? Promise.resolve({ data: { count: 5 } })
        : Promise.reject(new Error(`unexpected GET ${url}`))
    );
  });

  it("dashboardShowsApprovedCount (classic)", async () => {
    wrap(<ClassicDashboard />);
    expect(await screen.findByText("Approved cases")).toBeInTheDocument();
    expect(await screen.findByTestId("approved-cases-value")).toHaveTextContent("5");
  });

  it("dashboardShowsApprovedCount (new view)", async () => {
    wrap(<NewDashboard />);
    expect(await screen.findByText("Approved cases")).toBeInTheDocument();
    expect(await screen.findByTestId("approved-cases-value")).toHaveTextContent("5");
  });

  it("leaves the tile out when the healer may not use case review", async () => {
    vi.mocked(api.get).mockRejectedValue({ response: { status: 403 } });
    wrap(<ClassicDashboard />);
    expect(await screen.findByText("Active Patients")).toBeInTheDocument();
    expect(screen.queryByText("Approved cases")).not.toBeInTheDocument();
  });
});
