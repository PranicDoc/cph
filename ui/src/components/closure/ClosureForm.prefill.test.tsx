import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import ClosureForm from "./ClosureForm";
import { renderWithDataRouter } from "../../test/renderWithDataRouter";

vi.mock("@/lib/axios", () => ({
  default: { get: vi.fn(), post: vi.fn() },
}));
vi.mock("../../ui-version", () => ({
  useUiVersion: () => ({ isNewView: true }),
}));
vi.mock("@/hooks/useReferenceData", () => ({
  useReferenceData: () => ({
    colors: [
      { id: "g", code: "G", name: "Green", properties: ["Cleansing"], category: "CLEANSING" },
      { id: "v", code: "V", name: "Violet", properties: ["Regenerating"], category: "ENERGISING" },
    ],
    loading: false,
    error: null,
  }),
}));
vi.mock("@/hooks/admin/useMasterData", () => ({
  useProtocols: () => ({ data: [], isLoading: false }),
}));
vi.mock("sonner", () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import api from "@/lib/axios";

const mGet = api.get as unknown as ReturnType<typeof vi.fn>;

const sel = (color: string, properties: string[] = []) => ({ color, properties });

function renderForm({
  saved = [],
  prefill,
}: {
  saved?: unknown[];
  prefill: unknown[] | "error";
}) {
  mGet.mockImplementation((url: string) => {
    if (url.endsWith("/chakras")) return Promise.resolve({ data: { data: ["Solar Plexus", "Spleen"] } });
    if (url.endsWith("/prefill")) {
      return prefill === "error"
        ? Promise.reject(new Error("boom"))
        : Promise.resolve({ data: { chakraSelections: prefill } });
    }
    return Promise.resolve({ data: { chakraSelections: saved } });
  });
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

/** Expands a chakra's accordion and returns its energising / cleansing panels. */
async function panelsFor(chakra: string) {
  const header = await screen.findByRole("button", { name: new RegExp(chakra) });
  if (header.getAttribute("aria-expanded") !== "true") await userEvent.setup().click(header);
  return {
    energising: await screen.findByRole("group", { name: "Energising colors" }),
    cleansing: await screen.findByRole("group", { name: "Cleansing colors" }),
  };
}

describe("ClosureForm protocol pre-fill", () => {
  beforeEach(() => vi.clearAllMocks());

  it("pre-fills a chakra with no saved selection from the protocol pre-fill", async () => {
    renderForm({
      prefill: [
        // Violet is energising-only in the catalog but the protocol also uses it for cleansing.
        { chakra: "Solar Plexus", energisingColors: [sel("Violet")], cleansingColors: [sel("Green"), sel("Violet")] },
        { chakra: "Spleen", energisingColors: [], cleansingColors: [] },
      ],
    });

    const { energising, cleansing } = await panelsFor("Solar Plexus");
    expect(within(energising).getByRole("button", { name: /Violet/ })).toHaveAttribute("aria-pressed", "true");
    expect(within(cleansing).getByRole("button", { name: /Green/ })).toHaveAttribute("aria-pressed", "true");
    expect(within(cleansing).getByRole("button", { name: /Violet/ })).toHaveAttribute("aria-pressed", "true");
  });

  it("keeps a saved selection over the protocol pre-fill", async () => {
    renderForm({
      saved: [{ chakra: "Solar Plexus", energisingColors: [], cleansingColors: [sel("Green", ["Cleansing"])] }],
      prefill: [{ chakra: "Solar Plexus", energisingColors: [sel("Violet")], cleansingColors: [] }],
    });

    const { energising, cleansing } = await panelsFor("Solar Plexus");
    expect(within(cleansing).getByRole("button", { name: /Green/ })).toHaveAttribute("aria-pressed", "true");
    expect(within(energising).getByRole("button", { name: /Violet/ })).toHaveAttribute("aria-pressed", "false");
  });

  it("still opens for manual entry when the pre-fill fails to load", async () => {
    renderForm({ prefill: "error" });

    const { energising } = await panelsFor("Solar Plexus");
    expect(within(energising).getByRole("button", { name: /Violet/ })).toHaveAttribute("aria-pressed", "false");
  });
});
