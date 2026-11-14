import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import Patients from "./Patients";
import api from "../lib/axios";

vi.mock("../lib/axios", () => ({
  default: { get: vi.fn(), patch: vi.fn(), post: vi.fn() },
}));

const patient = (id: string, status: string) => ({
  id,
  healingCaseId: `case-${id}`,
  firstName: `Patient ${id}`,
  lastName: "",
  fullName: `Patient ${id}`,
  dob: "1990-01-01",
  gender: "F",
  contact: "999",
  status,
});

vi.mock("../context/PatientContext", () => ({
  usePatient: () => ({ selectPatient: vi.fn() }),
  usePatientList: () => ({
    patients: [patient("a", "CLOSED"), patient("b", "CLOSED"), patient("c", "CLOSED"), patient("d", "ACTIVE")],
    isLoading: false,
  }),
}));
vi.mock("../ui-version", () => ({ useUiVersion: () => ({ isNewView: false }) }));
vi.mock("../context/AuthContext", () => ({ useAuth: () => ({ user: { id: "healer-1" } }) }));
vi.mock("../hooks/useCreditAccount", () => ({
  useCreditAccount: () => ({ data: { free_balance: 3, paid_balance: 0 } }),
}));

describe("Patients: submit for review", () => {
  beforeEach(() => vi.clearAllMocks());

  it("offers Submit only on cases with a generated PDF that are not approved", async () => {
    vi.mocked(api.get).mockResolvedValue({
      data: [
        { caseId: "case-a", status: "NOT_SUBMITTED", eligible: true, latestRevisionNo: null },
        { caseId: "case-b", status: "NOT_SUBMITTED", eligible: false, latestRevisionNo: null },
        { caseId: "case-c", status: "APPROVED", eligible: true, latestRevisionNo: 1 },
      ],
    });
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <Patients />
        </MemoryRouter>
      </QueryClientProvider>
    );

    // Only Patient a qualifies (b has no PDF, c is approved, d is not closed); the page may render
    // the same row in more than one layout, so check who owns every button rather than counting.
    const found = await screen.findAllByRole("button", { name: /submit for review/i }, { timeout: 5000 });
    for (const button of found) {
      expect(button.closest("tr,[role=button]")?.textContent).toContain("Patient a");
    }
    expect(api.get).toHaveBeenCalledWith("/api/v1/reviews/case-statuses", {
      params: { caseIds: "case-a,case-b,case-c" },
    });
  });

  it("shows each case's review status next to its actions", async () => {
    vi.mocked(api.get).mockResolvedValue({
      data: [
        { caseId: "case-a", status: "CHANGES_REQUESTED", eligible: true, latestRevisionNo: 1 },
        { caseId: "case-b", status: "NOT_SUBMITTED", eligible: false, latestRevisionNo: null },
        { caseId: "case-c", status: "APPROVED", eligible: true, latestRevisionNo: 1 },
      ],
    });
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <Patients />
        </MemoryRouter>
      </QueryClientProvider>
    );

    expect((await screen.findAllByText("Changes requested", {}, { timeout: 5000 })).length).toBeGreaterThan(0);
    expect(screen.getAllByText("Approved").length).toBeGreaterThan(0);
    // Patient b has no PDF yet: nothing to say about review.
    expect(screen.queryByText("Not submitted")).not.toBeInTheDocument();
  });
});
