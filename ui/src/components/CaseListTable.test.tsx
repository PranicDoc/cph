import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";

import { CaseListTable } from "./CaseListTable";

vi.mock("../lib/axios", () => ({
  default: { get: vi.fn() },
}));
import api from "../lib/axios";

const mGet = api.get as unknown as ReturnType<typeof vi.fn>;

function page(content: unknown[], overrides = {}) {
  return { data: { content, page: 0, size: 5, totalElements: content.length, totalPages: 1, last: true, ...overrides } };
}

const ROWS = [
  { caseId: "c1", patientFirstName: "Asha", patientLastName: "Rao", status: "ACTIVE", lastSessionDate: "2026-10-01" },
  { caseId: "c2", patientFirstName: "Ravi", patientLastName: "Kumar", status: "DRAFT", lastSessionDate: null },
];

function Where() {
  return <div data-testid="where">{useLocation().pathname}</div>;
}

function renderTable() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={["/"]}>
        <Routes>
          <Route path="/" element={<CaseListTable healerId="h1" />} />
          <Route path="*" element={<Where />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("CaseListTable", () => {
  beforeEach(() => {
    mGet.mockReset();
    mGet.mockResolvedValue(page(ROWS));
  });

  it("lists patient names with their status", async () => {
    renderTable();
    expect(await screen.findByText("Asha Rao")).toBeInTheDocument();
    expect(screen.getByText("Ravi Kumar")).toBeInTheDocument();
    expect(screen.getByText("Active")).toBeInTheDocument();
    expect(screen.getByText("Draft")).toBeInTheDocument();
  });

  it("opens the case when its row is clicked", async () => {
    renderTable();
    await userEvent.click(await screen.findByText("Ravi Kumar"));
    expect(await screen.findByTestId("where")).toHaveTextContent("/session/c2");
  });

  it("searches by name from the first page", async () => {
    renderTable();
    await screen.findByText("Asha Rao");
    await userEvent.type(screen.getByLabelText("Search cases by patient name"), "ravi");
    await waitFor(() =>
      expect(mGet).toHaveBeenLastCalledWith("/api/v1/dashboard/cases", {
        params: { q: "ravi", page: 0, size: 5 },
      })
    );
  });

  it("pages forward and back", async () => {
    mGet.mockResolvedValue(page(ROWS, { totalPages: 2, last: false }));
    renderTable();
    await screen.findByText("Asha Rao");
    await userEvent.click(screen.getByLabelText("Next page"));
    await waitFor(() =>
      expect(mGet).toHaveBeenLastCalledWith("/api/v1/dashboard/cases", {
        params: { q: undefined, page: 1, size: 5 },
      })
    );
  });
});
