import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import MasterDataPage from "./MasterDataPage";
import api from "../../lib/axios";

vi.mock("../../lib/axios", () => ({ default: { get: vi.fn(), post: vi.fn() } }));
vi.mock("../../context/AuthContext", () => ({
  useAuth: () => ({ user: { id: "healer-1", role: "HEALER" }, isAuthenticated: true }),
}));

const { systemColor, query, mutation } = vi.hoisted(() => ({
  systemColor: { id: "c1", name: "Green", code: "GREEN", colorHex: "#00ff00", properties: [], healerId: null },
  query: (data: unknown) => ({ data, isLoading: false, isError: false }),
  mutation: () => ({ mutate: () => {}, mutateAsync: async () => {}, isPending: false }),
}));

vi.mock("../../hooks/admin/useMasterData", () => ({
  usePranaColors: () => query([systemColor]),
  useChakras: () => query([]),
  useProtocolMappings: () => query([]),
  useProtocolsPage: () => query({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 50, last: true }),
  useProtocolImportJobs: () => query([]),
  useChakraColorGroupsForProtocol: () => query([]),
  useCreateColor: mutation,
  useUpdateColor: mutation,
  useDeleteColor: mutation,
  useCreateChakra: mutation,
  useUpdateChakra: mutation,
  useDeleteChakra: mutation,
  useCreateMapping: mutation,
  useDeleteMapping: mutation,
  useCreateProtocol: mutation,
  useUpdateProtocol: mutation,
  useDeleteProtocol: mutation,
  useSubmitProtocolImport: mutation,
  useAddProtocolChakraColors: mutation,
  useUpdateProtocolChakraColor: mutation,
  useDeleteProtocolChakraColor: mutation,
  useWipeMasterData: mutation,
}));

const grants = { MASTER_DATA_COLORS: ["READ", "UPDATE", "DELETE", "ELEVATE"] };
let elevations: Record<string, string> = {};

function renderPage() {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MasterDataPage />
    </QueryClientProvider>
  );
}

const pencils = () => document.querySelectorAll(".lucide-pencil").length;
const bins = () => document.querySelectorAll(".lucide-trash-2, .lucide-trash2").length;

describe("Master Data: unlock shared entries", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    elevations = {};
    (api.get as any).mockImplementation(() => Promise.resolve({ data: { grants, elevations } }));
  });

  it("locks a system row until shared entries are unlocked, then allows editing but not deleting", async () => {
    const user = userEvent.setup();
    (api.post as any).mockImplementation(() => {
      elevations = { MASTER_DATA_COLORS: new Date(Date.now() + 10 * 60_000).toISOString() };
      return Promise.resolve({ data: {} });
    });
    renderPage();

    await user.click(await screen.findByRole("button", { name: /unlock shared entries/i }));
    expect(pencils()).toBe(0);

    await user.type(screen.getByLabelText(/password/i), "secret");
    await user.click(screen.getByRole("button", { name: /^unlock$/i }));

    expect(api.post).toHaveBeenCalledWith("/api/v1/masterdata/colors/elevation", { password: "secret" });
    expect(await screen.findByText(/unlocked · 10 min left/i)).toBeInTheDocument();
    await waitFor(() => expect(pencils()).toBeGreaterThan(0));
    expect(bins()).toBe(0);
  });

  it("shows why a wrong password did not unlock", async () => {
    const user = userEvent.setup();
    (api.post as any).mockRejectedValue({ response: { status: 400, data: { detail: "Password is incorrect" } } });
    renderPage();

    await user.click(await screen.findByRole("button", { name: /unlock shared entries/i }));
    await user.type(screen.getByLabelText(/password/i), "wrong");
    await user.click(screen.getByRole("button", { name: /^unlock$/i }));

    expect(await screen.findByText("Password is incorrect")).toBeInTheDocument();
    expect(pencils()).toBe(0);
  });

  it("offers no unlock without the ELEVATE permission", async () => {
    (api.get as any).mockResolvedValue({ data: { grants: { MASTER_DATA_COLORS: ["READ", "UPDATE"] } } });
    renderPage();

    expect(await screen.findAllByText("Green")).not.toHaveLength(0);
    expect(screen.queryByRole("button", { name: /unlock shared entries/i })).not.toBeInTheDocument();
  });
});
