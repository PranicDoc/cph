import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { DeletedPatientsPanel } from "./DeletedPatientsPanel";
import api from "../../lib/axios";

vi.mock("../../lib/axios", () => ({ default: { get: vi.fn(), post: vi.fn() } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const trashed = {
  id: "p-1",
  patientCode: "PT-000007",
  firstName: "Asha",
  lastName: "Rao",
  healerId: "h-1",
  healerName: "Dr Healer",
  deletedAt: "2026-11-01T10:00:00Z",
  purgeAt: "2026-12-16T10:00:00Z",
  caseCount: 2,
};

function renderPanel() {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <DeletedPatientsPanel />
    </QueryClientProvider>
  );
}

describe("DeletedPatientsPanel", () => {
  beforeEach(() => vi.clearAllMocks());

  it("lists deleted patients with their code, healer and cases", async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [trashed] });
    renderPanel();

    expect(await screen.findByText("PT-000007")).toBeInTheDocument();
    expect(screen.getByText("Asha Rao")).toBeInTheDocument();
    expect(screen.getByText("Dr Healer")).toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument();
  });

  it("restores only after the admin confirms", async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [trashed] });
    vi.mocked(api.post).mockResolvedValue({ data: {} });
    renderPanel();

    await userEvent.click(await screen.findByRole("button", { name: "Restore" }));
    expect(api.post).not.toHaveBeenCalled();
    await userEvent.click(await screen.findByRole("button", { name: "Restore", hidden: false }).then(() => screen.getAllByRole("button", { name: "Restore" }).at(-1)!));

    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/patients/p-1/restore"));
  });

  it("says so when nothing is deleted", async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [] });
    renderPanel();
    expect(await screen.findByText("No deleted patients.")).toBeInTheDocument();
  });
});
