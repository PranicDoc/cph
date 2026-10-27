import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import { PendingReportsTable } from "./PendingReportsTable";

vi.mock("../lib/axios", () => ({
  default: { get: vi.fn() },
}));
import api from "../lib/axios";

const mGet = api.get as unknown as ReturnType<typeof vi.fn>;

function page(content: unknown[], overrides = {}) {
  return { data: { content, page: 0, size: 10, totalElements: content.length, totalPages: 1, last: true, ...overrides } };
}

const ROWS = [
  { caseId: "c1", patientFirstName: "Asha", patientLastName: "Rao", closureDate: "2026-10-01" },
  { caseId: "c2", patientFirstName: "Ravi", patientLastName: "Kumar", closureDate: null },
];

function renderTable() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <PendingReportsTable healerId="h1" />
    </QueryClientProvider>
  );
}

describe("PendingReportsTable", () => {
  beforeEach(() => {
    mGet.mockReset();
    mGet.mockResolvedValue(page(ROWS));
  });

  it("lists the patients whose report is still missing", async () => {
    renderTable();
    expect(await screen.findByText("Asha Rao")).toBeInTheDocument();
    expect(screen.getByText("Ravi Kumar")).toBeInTheDocument();
    expect(mGet).toHaveBeenCalledWith("/api/v1/dashboard/pending-reports/page", {
      params: { q: undefined, page: 0, size: 10 },
    });
  });

  it("searches by name from the first page", async () => {
    renderTable();
    await screen.findByText("Asha Rao");
    await userEvent.type(screen.getByLabelText("Search pending reports by patient name"), "ravi");
    await waitFor(() =>
      expect(mGet).toHaveBeenLastCalledWith("/api/v1/dashboard/pending-reports/page", {
        params: { q: "ravi", page: 0, size: 10 },
      })
    );
  });

  it("pages forward", async () => {
    mGet.mockResolvedValue(page(ROWS, { totalPages: 2, last: false }));
    renderTable();
    await screen.findByText("Asha Rao");
    await userEvent.click(screen.getByLabelText("Next page"));
    await waitFor(() =>
      expect(mGet).toHaveBeenLastCalledWith("/api/v1/dashboard/pending-reports/page", {
        params: { q: undefined, page: 1, size: 10 },
      })
    );
  });
});
