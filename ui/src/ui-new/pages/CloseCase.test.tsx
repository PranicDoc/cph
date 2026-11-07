import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import CloseCase from "./CloseCase";
import { renderWithDataRouter } from "../../test/renderWithDataRouter";

vi.mock("@/lib/axios", () => ({
  default: { get: vi.fn(), post: vi.fn() },
}));
vi.mock("../../ui-version", () => ({
  useUiVersion: () => ({ isNewView: true }),
}));
vi.mock("@/hooks/useReferenceData", () => ({
  useReferenceData: () => ({
    colors: [{ id: "g", code: "G", name: "Green", properties: [], category: "CLEANSING" }],
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
const mPost = api.post as unknown as ReturnType<typeof vi.fn>;

const patient = { id: "p1", firstName: "Asha", lastName: "Rao", healingCaseId: "c1" };

function renderPage() {
  mGet.mockImplementation((url: string) => {
    if (url.endsWith("/chakras")) return Promise.resolve({ data: { data: ["Solar Plexus", "Spleen"] } });
    if (url.endsWith("/protocol-understanding")) {
      return Promise.resolve({
        data: {
          chakraSelections: [
            {
              chakra: "Solar Plexus",
              energisingColors: [],
              cleansingColors: [{ color: "Green", properties: [] }],
              reason: "Anger held here",
            },
          ],
        },
      });
    }
    return Promise.resolve({ data: {} });
  });
  mPost.mockResolvedValue({ data: {} });
  return renderWithDataRouter(
    [
      { path: "/session/c1", element: <p>Session page</p> },
      { path: "/cases/:healingCaseId/close", element: <CloseCase /> },
    ],
    { initialEntries: ["/session/c1", { pathname: "/cases/c1/close", state: { patient } } as never] }
  );
}

describe("CloseCase page", () => {
  beforeEach(() => vi.clearAllMocks());

  it("lists the scanned chakras with their saved colours and reason", async () => {
    renderPage();

    expect(await screen.findByRole("button", { name: /Spleen/ })).toBeInTheDocument();
    const cleansing = await screen.findByRole("group", { name: "Cleansing colors" });
    expect(within(cleansing).getByRole("button", { name: /Green/ })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByLabelText("Why this chakra? (optional)")).toHaveValue("Anger held here");
  });

  it("limits the reason to 300 characters and shows the count", async () => {
    renderPage();

    const reason = await screen.findByLabelText("Why this chakra? (optional)");
    expect(reason).toHaveAttribute("maxLength", "300");
    expect(screen.getByText("15/300")).toBeInTheDocument();
  });

  it("saves the reasons, then closes the case without the understanding step and goes back", async () => {
    const user = userEvent.setup();
    const { router } = renderPage();

    const reason = await screen.findByLabelText("Why this chakra? (optional)");
    await user.clear(reason);
    await user.type(reason, "Fear");
    await waitFor(() => expect(screen.getByRole("button", { name: "Save" })).toBeEnabled());
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(mPost).toHaveBeenCalledWith("/api/v1/cases/c1/protocol-understanding", {
      chakraSelections: [
        {
          chakra: "Solar Plexus",
          energisingColors: [],
          cleansingColors: [{ color: "Green", properties: [] }],
          reason: "Fear",
        },
        { chakra: "Spleen", energisingColors: [], cleansingColors: [], reason: undefined },
      ],
    });

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Is healing continued?")).toBeInTheDocument();
    expect(within(dialog).queryByRole("button", { name: "Back" })).not.toBeInTheDocument();

    await user.click(within(dialog).getByRole("button", { name: "Close Case" }));

    await waitFor(() => expect(router.state.location.pathname).toBe("/session/c1"));
    expect(mPost).toHaveBeenCalledWith("/api/v1/cases/c1/close", expect.anything());
  });
});
