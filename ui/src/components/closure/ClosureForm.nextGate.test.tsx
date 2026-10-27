import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";

import ClosureForm from "./ClosureForm";
import { renderWithDataRouter } from "../../test/renderWithDataRouter";

vi.mock("@/lib/axios", () => ({
  default: { get: vi.fn(), post: vi.fn() },
}));
vi.mock("../../ui-version", () => ({
  useUiVersion: () => ({ isNewView: true }),
}));
vi.mock("@/hooks/useReferenceData", () => ({
  useReferenceData: () => ({ colors: [], loading: false, error: null }),
}));
vi.mock("@/hooks/admin/useMasterData", () => ({
  useProtocols: () => ({ data: [], isLoading: false }),
}));
vi.mock("sonner", () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import api from "@/lib/axios";

const mGet = api.get as unknown as ReturnType<typeof vi.fn>;

function renderForm() {
  const noop = vi.fn();
  return renderWithDataRouter([
    {
      path: "/",
      element: (
        <ClosureForm
          comments=""
          setComments={noop}
          isHealingContinued={false}
          setIsHealingContinued={noop}
          selectedProtocols={[]}
          setSelectedProtocols={noop}
          closeMutation={{ mutate: noop, isPending: false } as never}
          onClose={noop}
          healingCaseId="case-1"
        />
      ),
    },
  ]);
}

describe("ClosureForm Next button", () => {
  beforeEach(() => {
    mGet.mockReset();
  });

  it("stays disabled until the protocol-understanding call has completed", async () => {
    let release: (v: unknown) => void = () => {};
    const pending = new Promise((resolve) => {
      release = resolve;
    });
    mGet.mockImplementation((url: string) => {
      if (url.endsWith("/chakras")) return Promise.resolve({ data: { data: ["Solar Plexus"] } });
      if (url.endsWith("/prefill")) return Promise.resolve({ data: { chakraSelections: [] } });
      return pending;
    });
    renderForm();

    const loading = await screen.findByRole("button", { name: "Loading..." });
    expect(loading).toBeDisabled();

    release({ data: { chakraSelections: [] } });

    await waitFor(() => expect(screen.getByRole("button", { name: "Next" })).toBeEnabled());
  });

  it("stays disabled when the protocol-understanding call fails, so an empty table is never saved", async () => {
    mGet.mockImplementation((url: string) => {
      if (url.endsWith("/chakras")) return Promise.resolve({ data: { data: ["Solar Plexus"] } });
      if (url.endsWith("/prefill")) return Promise.resolve({ data: { chakraSelections: [] } });
      return Promise.reject(new Error("boom"));
    });
    renderForm();

    await waitFor(() => expect(screen.getByRole("button", { name: "Next" })).toBeInTheDocument());
    expect(screen.getByRole("button", { name: "Next" })).toBeDisabled();
  });
});
